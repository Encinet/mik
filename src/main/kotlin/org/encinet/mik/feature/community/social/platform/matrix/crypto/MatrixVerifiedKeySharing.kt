package org.encinet.mik.feature.community.social.platform.matrix.crypto

import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.client.store.KeyStore
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.EventContent
import de.connect2x.trixnity.core.model.events.UnknownEventContent
import de.connect2x.trixnity.core.model.events.block.EventContentBlocks
import de.connect2x.trixnity.core.model.events.m.ForwardedRoomKeyEventContent
import de.connect2x.trixnity.core.model.events.m.RoomKeyBundleEventContent
import de.connect2x.trixnity.core.model.events.m.RoomKeyEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import de.connect2x.trixnity.core.model.keys.Keys
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService
import de.connect2x.trixnity.crypto.olm.OlmEncryptionServiceImpl
import de.connect2x.trixnity.crypto.olm.OlmStore
import de.connect2x.trixnity.crypto.olm.membershipsAllowedToReceiveKey
import de.connect2x.trixnity.utils.KeyedMutex
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.dsl.module

internal fun matrixVerifiedKeySharingModule() = module {
    single { MatrixVerifiedKeySharing(get<OlmEncryptionServiceImpl>(), get(), get(), get(), get()) }
    single<OlmEncryptionService> { get<MatrixVerifiedKeySharing>() }
}

/** Restricts every SDK room-key sharing path, while keeping verification and incoming Olm unchanged. */
internal class MatrixVerifiedKeySharing(
    private val delegate: OlmEncryptionService,
    private val store: OlmStore,
    private val userInfo: UserInfo,
    private val api: MatrixClientServerApiClient,
    private val keyStore: KeyStore,
) : OlmEncryptionService by delegate {
    private data class Recipient(val userId: UserId, val deviceId: String, val keys: Keys)

    private val roomLocks = KeyedMutex<RoomId>()
    private val sessionRecipients = ConcurrentHashMap<RoomId, Set<Recipient>>()

    /** First use also discards a persisted session that may predate this policy. */
    suspend fun <T> encryptRoom(roomId: RoomId, encrypt: suspend () -> T): T = roomLocks.withLock(roomId) {
        repeat(3) {
            val recipients = trustedRoomRecipients(roomId)
            if (sessionRecipients[roomId] != recipients) store.updateOutboundMegolmSession(roomId) { null }
            val encrypted = encrypt()
            if (trustedRoomRecipients(roomId) == recipients) {
                sessionRecipients[roomId] = recipients
                return@withLock encrypted
            }
            sessionRecipients.remove(roomId)
        }
        throw IllegalStateException("Matrix recipient trust changed during encryption; retry the message")
    }

    override suspend fun encryptOlm(
        content: EventContent, recipientUserId: UserId, recipientDeviceId: String,
    ): Result<OlmEncryptedToDeviceEventContent> {
        if (!content.carriesRoomKeys()) return delegate.encryptOlm(content, recipientUserId, recipientDeviceId)
        return encryptOlm(content, setOf(recipientUserId to recipientDeviceId))[recipientUserId to recipientDeviceId]
            ?: Result.failure(IllegalStateException("Matrix room keys require a verified recipient device"))
    }

    override suspend fun encryptOlm(
        content: EventContent, recipients: Set<Pair<UserId, String>>,
    ): Map<Pair<UserId, String>, Result<OlmEncryptedToDeviceEventContent>> {
        if (!content.carriesRoomKeys()) return delegate.encryptOlm(content, recipients)
        val allowed = recipients.mapNotNull { (user, device) -> trustedRecipient(user, device)?.let { (user to device) to it } }.toMap()
        val encrypted = delegate.encryptOlm(content, allowed.keys)
        val checked = encrypted.filter { (recipient, _) ->
            trustedRecipient(recipient.first, recipient.second) == allowed[recipient]
        }
        notifyWithheld(content, recipients - checked.keys)
        return checked
    }

    private fun EventContent.carriesRoomKeys() = this is RoomKeyEventContent ||
        this is ForwardedRoomKeyEventContent || this is RoomKeyBundleEventContent

    private suspend fun trustedRecipient(userId: UserId, deviceId: String): Recipient? {
        val keys = store.getDeviceKeys(userId)?.get(deviceId)?.signed?.keys ?: return null
        val trusted = userId == userInfo.userId && deviceId == userInfo.deviceId ||
            keyStore.deviceVerified(store.getTrustLevel(userId, deviceId), keys)
        return if (trusted) Recipient(userId, deviceId, keys) else null
    }

    private suspend fun trustedRoomRecipients(roomId: RoomId): Set<Recipient> = buildSet {
        for (user in store.getMembers(roomId, store.getHistoryVisibility(roomId).membershipsAllowedToReceiveKey)) {
            for (device in store.getDeviceKeys(user).orEmpty().keys) trustedRecipient(user, device)?.let { add(it) }
        }
    }

    @OptIn(MSC4354::class)
    private suspend fun notifyWithheld(content: EventContent, recipients: Set<Pair<UserId, String>>) {
        if (recipients.isEmpty()) return
        val (room, session, senderKey) = when (content) {
            is RoomKeyEventContent -> Triple(content.roomId, content.sessionId, userInfo.identityPublicKey.value.value)
            is ForwardedRoomKeyEventContent -> Triple(content.roomId, content.sessionId, content.senderKey.value)
            else -> return
        }
        val withheld = UnknownEventContent(buildJsonObject {
            put("algorithm", "m.megolm.v1.aes-sha2")
            put("code", "m.unverified")
            put("reason", "Device not verified by this sender")
            put("room_id", room.full)
            put("session_id", session)
            put("sender_key", senderKey)
        }, EventContentBlocks(), "m.room_key.withheld")
        api.user.sendToDeviceUnsafe("m.room_key.withheld", recipients.groupBy { it.first }
            .mapValues { (_, devices) -> devices.associate { it.second to withheld } }).getOrThrow()
    }
}
