package org.encinet.mik.feature.community.social.platform.matrix.crypto

import de.connect2x.trixnity.clientserverapi.client.MatrixClientAuthProvider
import de.connect2x.trixnity.clientserverapi.client.KeyApiClient
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClientFactory
import de.connect2x.trixnity.clientserverapi.client.SyncApiClient
import de.connect2x.trixnity.clientserverapi.client.SyncBatchTokenStore
import de.connect2x.trixnity.clientserverapi.client.SyncEvents
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.Presence
import de.connect2x.trixnity.core.model.keys.KeyAlgorithm
import de.connect2x.trixnity.core.model.keys.Keys
import de.connect2x.trixnity.core.model.keys.SignedDeviceKeys
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.utils.RetryFlowDelayConfig
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json

/** Presents the host's sync state consistently to every SDK service without opening another poller. */
internal class MatrixSdkFactory(private val state: StateFlow<SyncState>, private val advertisedKeys: Keys?) : MatrixClientServerApiClientFactory {
    override fun create(
        authProvider: MatrixClientAuthProvider,
        eventContentSerializerMappings: EventContentSerializerMappings,
        json: Json,
        syncBatchTokenStore: SyncBatchTokenStore,
        syncErrorDelayConfig: RetryFlowDelayConfig,
        coroutineContext: CoroutineContext,
        asUserId: UserId?,
        asDeviceId: String?,
        httpClientEngine: HttpClientEngine?,
        httpClientConfig: (HttpClientConfig<*>.() -> Unit)?,
    ): MatrixClientServerApiClient {
        val api = super.create(authProvider, eventContentSerializerMappings, json, syncBatchTokenStore,
            syncErrorDelayConfig, coroutineContext, asUserId, asDeviceId, httpClientEngine, httpClientConfig)
        return object : MatrixClientServerApiClient by api {
            override val key = object : KeyApiClient by api.key {
                override suspend fun setKeys(deviceKeys: SignedDeviceKeys?, oneTimeKeys: Keys?, fallbackKeys: Keys?): Result<Map<KeyAlgorithm, Int>> {
                    if (deviceKeys != null && advertisedKeys != null && deviceKeys.signed.keys != advertisedKeys) {
                        return Result.failure(IllegalStateException(
                            "Matrix device identity does not match its existing keys; restore the key store or use a fresh device token"))
                    }
                    return api.key.setKeys(deviceKeys, oneTimeKeys, fallbackKeys)
                }
            }
            override val sync = object : SyncApiClient by api.sync {
                override val currentSyncState = state

                override suspend fun start(filter: String?, setPresence: Presence?, timeout: Duration) {
                    error("MIK owns the Matrix sync loop")
                }

                override suspend fun <T> startOnce(filter: String?, setPresence: Presence?, timeout: Duration,
                    runOnce: suspend (SyncEvents) -> T): Result<T> = error("MIK owns Matrix sync requests")
            }
        }
    }
}
