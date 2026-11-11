package org.encinet.mik.feature.community.social.platform.matrix.crypto

import de.connect2x.trixnity.client.key
import de.connect2x.trixnity.client.key.KeySecretService
import de.connect2x.trixnity.client.key.KeyTrustService
import de.connect2x.trixnity.client.store.GlobalAccountDataStore
import de.connect2x.trixnity.client.store.KeyStore
import de.connect2x.trixnity.client.store.StoreTransactionManager
import de.connect2x.trixnity.clientserverapi.client.getAccountData
import de.connect2x.trixnity.core.ErrorResponse
import de.connect2x.trixnity.core.MatrixServerException
import de.connect2x.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import de.connect2x.trixnity.core.model.events.m.secretstorage.DefaultSecretKeyEventContent
import de.connect2x.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import de.connect2x.trixnity.crypto.SecretType
import de.connect2x.trixnity.crypto.key.UserTrustLevel
import de.connect2x.trixnity.crypto.key.checkRecoveryKey
import de.connect2x.trixnity.crypto.key.decodeRecoveryKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import de.connect2x.trixnity.client.MatrixClient as SdkClient

/** Retrieves account secrets independently of the /sync cursor; the SDK owns all cryptographic checks. */
internal class MatrixAccountRecovery(private val sdk: SdkClient) {
    class Failure(val stage: String, val failure: Exception) : Exception("Matrix recovery failed at $stage", failure)

    suspend fun restore(recoveryKey: String) {
        var stage = "read default secret-storage key"
        try {
            val defaultKey = sdk.api.user.getAccountData<DefaultSecretKeyEventContent>(sdk.userId).getOrThrow()
            stage = "read secret-storage key configuration"
            val keyId = defaultKey.key
            val info = sdk.api.user.getAccountData<SecretKeyEventContent>(sdk.userId, keyId).getOrThrow()
            require(info is SecretKeyEventContent.AesHmacSha2Key) { "Unsupported secret-storage algorithm" }
            stage = "validate the recovery key"
            val decoded = decodeRecoveryKey(recoveryKey)
            checkRecoveryKey(decoded, info).getOrThrow()

            stage = "retrieve encrypted account secrets"
            val secrets = coroutineScope {
                listOf(SecretType.M_CROSS_SIGNING_MASTER, SecretType.M_CROSS_SIGNING_SELF_SIGNING,
                    SecretType.M_CROSS_SIGNING_USER_SIGNING, SecretType.M_MEGOLM_BACKUP_V1).map { type ->
                    async {
                        sdk.api.user.getAccountData(type.id, sdk.userId).fold(
                            onSuccess = { GlobalAccountDataEvent(it) },
                            onFailure = {
                                if (it is MatrixServerException && it.errorResponse is ErrorResponse.NotFound) null
                                else throw it
                            },
                        )
                    }
                }.awaitAll().filterNotNull()
            }
            val accountData = sdk.di.get<GlobalAccountDataStore>()
            sdk.di.get<StoreTransactionManager>().writeTransaction {
                accountData.save(GlobalAccountDataEvent(defaultKey))
                accountData.save(GlobalAccountDataEvent(info, keyId))
                secrets.forEach { accountData.save(it) }
            }
            stage = "decrypt account secrets"
            sdk.di.get<KeySecretService>().decryptOrCreateMissingSecrets(decoded, keyId, info)
            stage = "verify the account master key"
            if (!masterKeyVerified()) {
                sdk.di.get<KeyTrustService>().checkOwnAdvertisedMasterKeyAndVerifySelf(decoded, keyId, info).getOrThrow()
            }
            stage = "sign our device with the account self-signing key"
            check(signOwnDeviceIfPossible()) { "Missing verified master key or self-signing secret" }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            throw Failure(stage, failure)
        }
    }

    suspend fun signOwnDeviceIfPossible(): Boolean {
        if (!masterKeyVerified() || SecretType.M_CROSS_SIGNING_SELF_SIGNING !in sdk.di.get<KeyStore>().getSecrets()) return false
        sdk.di.get<KeyTrustService>().trustAndSignKeys(setOf(sdk.signingKey), sdk.userId)
        return true
    }

    private suspend fun masterKeyVerified(): Boolean = when (val trust = sdk.key.getTrustLevel(sdk.userId).first()) {
        is UserTrustLevel.CrossSigned -> trust.verified
        is UserTrustLevel.NotAllDevicesCrossSigned -> trust.verified
        else -> false
    }
}
