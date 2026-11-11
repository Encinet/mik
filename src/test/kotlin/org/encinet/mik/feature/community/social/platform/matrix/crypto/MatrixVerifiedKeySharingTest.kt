package org.encinet.mik.feature.community.social.platform.matrix.crypto

import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.clientserverapi.client.UserApiClient
import de.connect2x.trixnity.client.store.KeyStore
import de.connect2x.trixnity.client.store.KeyVerificationState
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.EventContent
import de.connect2x.trixnity.core.model.events.ToDeviceEventContent
import de.connect2x.trixnity.core.model.events.m.DummyEventContent
import de.connect2x.trixnity.core.model.events.m.ForwardedRoomKeyEventContent
import de.connect2x.trixnity.core.model.events.m.RoomKeyBundleEventContent
import de.connect2x.trixnity.core.model.events.m.RoomKeyEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.events.m.room.HistoryVisibilityEventContent.HistoryVisibility
import de.connect2x.trixnity.core.model.keys.DeviceKeys
import de.connect2x.trixnity.core.model.keys.EncryptionAlgorithm
import de.connect2x.trixnity.core.model.keys.ExportedSessionKeyValue
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.core.model.keys.KeyValue
import de.connect2x.trixnity.core.model.keys.Keys
import de.connect2x.trixnity.core.model.keys.SessionKeyValue
import de.connect2x.trixnity.core.model.keys.Signed
import de.connect2x.trixnity.core.model.keys.SignedDeviceKeys
import de.connect2x.trixnity.crypto.key.DeviceTrustLevel
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService
import de.connect2x.trixnity.crypto.olm.OlmStore
import de.connect2x.trixnity.crypto.olm.StoredOutboundMegolmSession
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class MatrixVerifiedKeySharingTest {
    @Test
    fun `all key sharing paths require verified devices while Olm verification remains available`() = runBlocking {
        val fixture = Fixture()
        val levels = listOf(DeviceTrustLevel.CrossSigned(true), DeviceTrustLevel.Valid(true),
            DeviceTrustLevel.CrossSigned(false), DeviceTrustLevel.Valid(false), DeviceTrustLevel.NotCrossSigned,
            DeviceTrustLevel.Blocked, DeviceTrustLevel.Invalid("invalid"), DeviceTrustLevel.Unknown)
        levels.forEachIndexed { index, level -> fixture.device("D$index", level) }
        fixture.device("D8", DeviceTrustLevel.NotCrossSigned)
        fixture.verificationState("D8", true)
        fixture.device("D9", DeviceTrustLevel.CrossSigned(true))
        fixture.verificationState("D9", false)
        val recipients = fixture.recipients()
        val trusted = setOf(OWN to "BOT", PEER to "D0", PEER to "D1", PEER to "D8")
        val forwarded = ForwardedRoomKeyEventContent(ROOM, KeyValue.Curve25519KeyValue("original"), "session",
            ExportedSessionKeyValue("secret"), KeyValue.Ed25519KeyValue("signing"), emptyList(), EncryptionAlgorithm.Megolm)
        for (content in listOf(roomKey(), forwarded, mock(RoomKeyBundleEventContent::class.java))) {
            assertEquals(trusted, fixture.policy.encryptOlm(content, recipients).keys)
            assertEquals(trusted, fixture.encryptedFor)
            assertTrue(fixture.policy.encryptOlm(content, PEER, "D0").isSuccess)
            assertTrue(fixture.policy.encryptOlm(content, PEER, "D2").isFailure)
        }
        fixture.device("D8", DeviceTrustLevel.NotCrossSigned, "replacement")
        assertTrue(fixture.policy.encryptOlm(roomKey(), PEER, "D8").isFailure)
        assertEquals(recipients, fixture.policy.encryptOlm(mock(DummyEventContent::class.java), recipients).keys)
        assertEquals(recipients, fixture.encryptedFor)
    }

    @Test
    fun `trust and key changes retire sessions but unchanged verified recipients reuse them`() = runBlocking {
        val fixture = Fixture()
        fixture.device("BOB", DeviceTrustLevel.CrossSigned(true))
        fixture.policy.encryptRoom(ROOM) { "first" }
        assertEquals(1, fixture.rotations)
        fixture.policy.encryptRoom(ROOM) { "second" }
        assertEquals(1, fixture.rotations)
        fixture.trust[PEER to "BOB"] = DeviceTrustLevel.Blocked
        fixture.policy.encryptRoom(ROOM) { "revoked" }
        assertEquals(2, fixture.rotations)
        fixture.trust[PEER to "BOB"] = DeviceTrustLevel.CrossSigned(true)
        fixture.policy.encryptRoom(ROOM) { "verified again" }
        assertEquals(3, fixture.rotations)
        fixture.device("BOB", DeviceTrustLevel.CrossSigned(true), "replacement")
        fixture.policy.encryptRoom(ROOM) { "new device keys" }
        assertEquals(4, fixture.rotations)
        fixture.peerMembership = Membership.INVITE
        fixture.policy.encryptRoom(ROOM) { "joined history excludes invitees" }
        assertEquals(5, fixture.rotations)
        fixture.history = HistoryVisibility.INVITED
        fixture.policy.encryptRoom(ROOM) { "invited history allows invitees" }
        assertEquals(6, fixture.rotations)
        fixture.peerMembership = Membership.KNOCK
        fixture.policy.encryptRoom(ROOM) { "invited history excludes knocks" }
        assertEquals(7, fixture.rotations)
        fixture.history = HistoryVisibility.SHARED
        fixture.policy.encryptRoom(ROOM) { "shared history uses the SDK recipient policy" }
        assertEquals(8, fixture.rotations)
    }

    @Test
    fun `trust lost during encryption suppresses keys and discards the room ciphertext`() = runBlocking {
        val fixture = Fixture()
        fixture.device("BOB", DeviceTrustLevel.CrossSigned(true))
        fixture.onEncrypt = { fixture.trust[PEER to "BOB"] = DeviceTrustLevel.Blocked }
        assertTrue(fixture.policy.encryptOlm(roomKey(), setOf(PEER to "BOB")).isEmpty())
        fixture.trust[PEER to "BOB"] = DeviceTrustLevel.CrossSigned(true)
        var attempts = 0
        val result = fixture.policy.encryptRoom(ROOM) {
            attempts++
            if (attempts == 1) fixture.trust[PEER to "BOB"] = DeviceTrustLevel.Blocked
            attempts
        }
        assertEquals(2, result)
        assertEquals(2, fixture.rotations)

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                fixture.policy.encryptRoom(ROOM) {
                    fixture.trust[PEER to "BOB"] = if (fixture.trust[PEER to "BOB"] == DeviceTrustLevel.Blocked)
                        DeviceTrustLevel.CrossSigned(true) else DeviceTrustLevel.Blocked
                    "unstable"
                }
            }
        }
        Unit
    }

    private class Fixture {
        val trust = mutableMapOf<Pair<UserId, String>, DeviceTrustLevel>()
        private val keys = mutableMapOf<UserId, MutableMap<String, SignedDeviceKeys>>()
        var rotations = 0
        var peerMembership = Membership.JOIN
        var history = HistoryVisibility.JOINED
        var encryptedFor = emptySet<Pair<UserId, String>>()
        var onEncrypt: () -> Unit = {}
        private val keyStore = mock(KeyStore::class.java)
        private val store = object : OlmStore by mock(OlmStore::class.java) {
            override suspend fun getDeviceKeys(userId: UserId) = keys[userId]?.toMap()
            override suspend fun getTrustLevel(userId: UserId, deviceId: String) = trust[userId to deviceId]
            override suspend fun getMembers(roomId: RoomId, memberships: Set<Membership>) = keys.keys
                .filter { if (it == OWN) Membership.JOIN in memberships else peerMembership in memberships }.toSet()
            override suspend fun getHistoryVisibility(roomId: RoomId) = history
            override suspend fun updateOutboundMegolmSession(roomId: RoomId, updater: (StoredOutboundMegolmSession?) -> StoredOutboundMegolmSession?) {
                rotations++
            }
        }
        private val delegate = object : OlmEncryptionService by mock(OlmEncryptionService::class.java) {
            override suspend fun encryptOlm(content: EventContent, recipients: Set<Pair<UserId, String>>): Map<Pair<UserId, String>, Result<OlmEncryptedToDeviceEventContent>> {
                encryptedFor = recipients
                onEncrypt()
                return recipients.associateWith { Result.success(mock(OlmEncryptedToDeviceEventContent::class.java)) }
            }
        }
        private val api = object : MatrixClientServerApiClient by mock(MatrixClientServerApiClient::class.java) {
            override val user = object : UserApiClient by mock(UserApiClient::class.java) {
                override suspend fun sendToDeviceUnsafe(type: String, events: Map<UserId, Map<String, ToDeviceEventContent>>, transactionId: String) = Result.success(Unit)
            }
        }
        val policy = MatrixVerifiedKeySharing(delegate, store,
            UserInfo(OWN, "BOT", Key.Ed25519Key("BOT", "signing"), Key.Curve25519Key("BOT", "identity")), api, keyStore)

        init {
            keys[OWN] = mutableMapOf("BOT" to signedKeys(OWN, "BOT", "own"))
            trust[OWN to "BOT"] = DeviceTrustLevel.Valid(false)
        }

        fun device(id: String, level: DeviceTrustLevel, identity: String = id) {
            keys.getOrPut(PEER) { mutableMapOf() }[id] = signedKeys(PEER, id, identity)
            trust[PEER to id] = level
        }

        fun recipients() = keys.flatMap { (user, devices) -> devices.keys.map { user to it } }.toSet()

        suspend fun verificationState(device: String, verified: Boolean) {
            val key = checkNotNull(keys[PEER]?.get(device)).signed.keys.keys.filterIsInstance<Key.Ed25519Key>().single()
            `when`(keyStore.getKeyVerificationState(key)).thenReturn(if (verified)
                KeyVerificationState.Verified(key.value.value) else KeyVerificationState.Blocked(key.value.value))
        }

        private fun signedKeys(user: UserId, device: String, identity: String): SignedDeviceKeys =
            Signed(DeviceKeys(user, device, setOf(EncryptionAlgorithm.Olm),
                Keys(setOf(Key.Ed25519Key(device, "signing-$identity"), Key.Curve25519Key(device, "identity-$identity")))))
    }

    companion object {
        private val ROOM = RoomId("!room:example.org")
        private val OWN = UserId("@bot:example.org")
        private val PEER = UserId("@peer:example.org")
        private fun roomKey() = RoomKeyEventContent(ROOM, "session", SessionKeyValue("secret"), EncryptionAlgorithm.Megolm)
    }
}
