package org.encinet.mik.feature.community.social.platform.matrix.crypto

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import de.connect2x.trixnity.client.CryptoDriverModule
import de.connect2x.trixnity.client.MediaStoreModule
import de.connect2x.trixnity.client.RepositoriesModule
import de.connect2x.trixnity.client.create
import de.connect2x.trixnity.client.cryptodriver.vodozemac.vodozemac
import de.connect2x.trixnity.client.key
import de.connect2x.trixnity.client.media.inMemory
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.room.encrypt
import de.connect2x.trixnity.client.roomEventEncryptionServices
import de.connect2x.trixnity.client.store.AccountStore
import de.connect2x.trixnity.client.store.OlmCryptoStore
import de.connect2x.trixnity.client.store.StoreTransactionManager
import de.connect2x.trixnity.client.store.repository.exposed.exposed
import de.connect2x.trixnity.clientserverapi.client.MatrixClientAuthProviderData
import de.connect2x.trixnity.clientserverapi.client.SyncEvents
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.clientserverapi.client.classic
import de.connect2x.trixnity.clientserverapi.client.useApi
import de.connect2x.trixnity.clientserverapi.model.sync.SyncResponseSerializer
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.serialization.events.MessageEventSerializer
import de.connect2x.trixnity.crypto.key.DeviceTrustLevel
import de.connect2x.trixnity.crypto.key.EventTrustLevel
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.Url
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.future.future
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.encinet.mik.feature.community.social.platform.matrix.MatrixPlatformConfig
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import de.connect2x.trixnity.client.MatrixClient as SdkClient

/** The SDK owns crypto state; the existing transport remains the only /sync consumer. */
class TrixnityEncryption(
    private val config: MatrixPlatformConfig,
    private val root: Path,
    private val recoveryKey: String,
    private val logger: Logger,
) : MatrixEncryption {
    private val closed = AtomicBoolean()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncState = MutableStateFlow(SyncState.STOPPED)
    private val engine = Java.create()
    @Volatile private var client: SdkClient? = null
    @Volatile private var storage: MatrixCryptoStorage? = null
    @Volatile private var token: String? = null
    private var lastReceived: String? = null
    @Volatile private var deviceVerification: MatrixDeviceVerification? = null

    private val httpConfiguration: HttpClientConfig<*>.() -> Unit = {
        followRedirects = false
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 20_000
        }
        install(MatrixSdkHttp) {
            maximumBytes = config.syncMaxResponseBytes()
        }
    }

    override fun initialize(userId: String) = blocking {
        if (client != null) return@blocking
        withTimeout(90.seconds) {
            val authentication = MatrixClientAuthProviderData.classic(Url(config.homeserverUri().toString()), config.accessToken())
            val device = authentication.useApi(httpClientEngine = engine, httpClientConfig = httpConfiguration) {
                it.authentication.whoAmI().getOrThrow()
            }
            check(device.userId.full == userId) { "Matrix token identity changed during startup" }
            val deviceId = checkNotNull(device.deviceId) { "Matrix encryption requires a device access token" }
            val store = MatrixCryptoStorage.open(root, config.homeserverUri().toString(), userId, deviceId)
            storage = store
            try {
                val advertised = authentication.useApi(httpClientEngine = engine, httpClientConfig = httpConfiguration) {
                    val keys = it.key.getKeys(mapOf(device.userId to setOf(deviceId))).getOrThrow()
                    check(keys.failures.isNullOrEmpty()) { "Could not establish the Matrix device's existing identity" }
                    keys.deviceKeys?.get(device.userId)?.get(deviceId)?.signed?.keys
                }
                if (!store.initialized()) {
                    check(!Files.exists(Path.of(store.database().toString() + ".mv.db"))) {
                        "Unclaimed Matrix encryption database; restore its ownership marker or use a fresh bot device"
                    }
                    check(advertised == null) {
                        "This Matrix device already has encryption keys; restore its MIK key store or use a fresh bot device token"
                    }
                    store.markInitialized()
                } else {
                    check(Files.exists(Path.of(store.database().toString() + ".mv.db"))) {
                        "Matrix encryption database is missing; restore it or use a fresh bot device token"
                    }
                }
                val database = R2dbcDatabase.connect(
                    url = "r2dbc:h2:file:///${store.database()};CIPHER=AES;DB_CLOSE_ON_EXIT=FALSE",
                    user = "sa", password = "${store.password()} ${store.password()}",
                )
                val sdk = SdkClient.create(
                    repositoriesModule = RepositoriesModule.exposed(database),
                    mediaStoreModule = MediaStoreModule.inMemory(),
                    cryptoDriverModule = CryptoDriverModule.vodozemac(),
                    authProviderData = authentication,
                    coroutineContext = scope.coroutineContext,
                ) {
                    name = "mik-matrix-encryption"
                    httpClientEngine = engine
                    httpClientConfig = httpConfiguration
                    autoJoinUpgradedRooms = false
                    storeTimelineEventContentUnencrypted = false
                    matrixClientServerApiClientFactory = MatrixSdkFactory(syncState, advertised)
                    if (config.onlyVerifiedUsers()) modulesFactories += ::matrixVerifiedKeySharingModule
                }.getOrThrow()
                client = sdk
                check(sdk.userId.full == userId && sdk.deviceId == deviceId) { "Matrix key store belongs to another device" }
                check(advertised == null || advertised.keys.containsAll(setOf(sdk.identityKey, sdk.signingKey))) {
                    "Matrix device identity does not match its existing keys; restore the key store or use a fresh device token"
                }
                sdk.started.first { it }
                if (!config.onlyVerifiedUsers()) {
                    // Disabling the policy must retire sessions that excluded unverified devices.
                    val crypto = sdk.di.get<OlmCryptoStore>()
                    sdk.di.get<StoreTransactionManager>().writeTransaction {
                        config.rooms().observedRoomIds().forEach { room ->
                            crypto.updateOutboundMegolmSession(RoomId(room)) { null }
                        }
                    }
                }
                token = sdk.di.get<AccountStore>().getAccount()?.syncBatchToken
                check(!closed.get()) { "Matrix encryption is closed" }
                logger.info("Matrix encryption device: $userId / $deviceId; Ed25519 ${sdk.signingKey.value.value}")
                val verification = MatrixDeviceVerification(sdk, logger)
                deviceVerification = verification
                verification.start(scope, recoveryKey)
            } catch (failure: Throwable) {
                withContext(NonCancellable) {
                    try {
                        client?.closeSuspending()
                    } finally {
                        client = null
                        store.close()
                        storage = null
                    }
                }
                throw failure
            }
        }
    }

    override fun cursor(): Optional<String> = Optional.ofNullable(token)

    override fun receiveSync(response: JsonObject, fullState: Boolean) = blocking {
        val sdk = requireClient()
        val joined = if (fullState) sdk.room.getAll().first().filterValues { it.first()?.membership == Membership.JOIN }.keys
            .map { it.full }.toSet() else emptySet()
        val prepared = cryptoSync(response, joined, fullState).toString()
        val fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(prepared.toByteArray()))
        if (lastReceived != fingerprint) {
            syncState.value = SyncState.RUNNING
            sdk.api.sync.emit(SyncEvents(sdk.api.json.decodeFromString(
                SyncResponseSerializer(sdk.api.json, sdk.api.eventContentSerializerMappings), prepared)))
            lastReceived = fingerprint
            deviceVerification?.synced()
        }
    }

    override fun commit(nextBatch: String) = blocking {
        val sdk = requireClient()
        sdk.di.get<StoreTransactionManager>().writeTransaction {
            sdk.di.get<AccountStore>().updateAccount { it?.copy(syncBatchToken = nextBatch) }
        }
        token = nextBatch
    }

    override fun resetCursor() = blocking {
        val sdk = requireClient()
        sdk.di.get<StoreTransactionManager>().writeTransaction {
            sdk.di.get<AccountStore>().updateAccount { it?.copy(syncBatchToken = null) }
        }
        token = null
        lastReceived = null
    }

    override fun disconnected() {
        syncState.value = SyncState.STOPPED
    }

    override fun encrypt(roomId: String, content: JsonObject): CompletableFuture<JsonObject> = scope.future {
        withTimeout(90.seconds) {
            val sdk = requireClient()
            val serializer = MessageEventSerializer(sdk.api.eventContentSerializerMappings.message)
            val template = buildJsonObject {
                put("event_id", "$" + "outgoing")
                put("room_id", roomId)
                put("sender", sdk.userId.full)
                put("origin_server_ts", 0)
                put("type", "m.room.message")
                put("content", sdk.api.json.parseToJsonElement(content.toString()))
            }
            val event = sdk.api.json.decodeFromJsonElement(serializer, template)
            val room = RoomId(roomId)
            val encrypt: suspend () -> MessageEventContent = {
                checkNotNull(sdk.roomEventEncryptionServices.encrypt(event.content, room)) {
                    "Matrix room encryption is not available"
                }.getOrThrow()
            }
            val encrypted = if (config.onlyVerifiedUsers()) sdk.di.get<MatrixVerifiedKeySharing>().encryptRoom(room, encrypt)
                else encrypt()
            check(encrypted is EncryptedMessageEventContent) { "Matrix SDK refused to encrypt the room message" }
            val encoded = sdk.api.json.encodeToJsonElement(serializer, MessageEvent<MessageEventContent>(
                encrypted, event.id, event.sender, event.roomId, event.originTimestamp,
            ))
            JsonParser.parseString(encoded.jsonObject.getValue("content").toString()).asJsonObject
        }
    }

    override fun decrypt(roomId: String, eventId: String): CompletableFuture<JsonObject> = scope.future {
        withTimeout(2.minutes) {
            val sdk = requireClient()
            val room = RoomId(roomId)
            val id = EventId(eventId)
            val timeline = sdk.room.getTimelineEvent(room, id) {
                fetchTimeout = Duration.ZERO
                decryptionTimeout = 2.minutes
                allowReplaceContent = false
            }.filterNotNull().first { it.content != null }
            val content = checkNotNull(timeline.content).getOrThrow()
            val trust = sdk.key.getTrustLevel(room, id).first { it != EventTrustLevel.Unknown }
            val sourceTrusted = when (trust) {
                is EventTrustLevel.Creator -> true
                is EventTrustLevel.Shared -> trust.sharingTrustLevels.isNotEmpty() && trust.sharingTrustLevels.all { (sender, level) ->
                    sender.userId == sdk.userId && when (level) {
                        is DeviceTrustLevel.Valid -> level.verified
                        is DeviceTrustLevel.CrossSigned -> level.verified
                        else -> false
                    }
                }
                else -> false
            }
            check(sourceTrusted && (trust.senderTrustLevel is DeviceTrustLevel.Valid || trust.senderTrustLevel is DeviceTrustLevel.CrossSigned)) {
                "Matrix encrypted message has no authenticated sender device"
            }
            check(content is MessageEventContent) { "Matrix decrypted event is not a message" }
            val original = timeline.event
            val encoded = sdk.api.json.encodeToJsonElement(MessageEventSerializer(sdk.api.eventContentSerializerMappings.message),
                MessageEvent(content, original.id, original.sender, original.roomId, original.originTimestamp))
            JsonParser.parseString(encoded.toString()).asJsonObject
        }
    }

    override fun manage(action: String, arguments: List<String>): CompletableFuture<MatrixCryptoResult> = scope.future {
        withTimeout(30.seconds) {
            requireClient()
            checkNotNull(deviceVerification).manage(action, arguments)
        }
    }

    private fun requireClient(): SdkClient {
        check(!closed.get()) { "Matrix encryption is closed" }
        return checkNotNull(client) { "Matrix encryption has not initialized" }
    }

    private fun <T> blocking(block: suspend () -> T): T {
        val future = scope.future { block() }
        try {
            return future.get()
        } catch (interrupted: InterruptedException) {
            future.cancel(true)
            throw interrupted
        } catch (failure: ExecutionException) {
            val cause = failure.cause
            if (cause is RuntimeException) throw cause
            throw IllegalStateException("Matrix encryption operation failed: ${cause?.javaClass?.simpleName}", cause)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        syncState.value = SyncState.STOPPED
        scope.cancel()
        engine.close()
        Thread.ofVirtual().name("mik-matrix-crypto-close").start {
            runBlocking {
                try {
                    scope.coroutineContext.job.join()
                    client?.closeSuspending()
                } finally {
                    storage?.close()
                }
            }
        }
    }
}
