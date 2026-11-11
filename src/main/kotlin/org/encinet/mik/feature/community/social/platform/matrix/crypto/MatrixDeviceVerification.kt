package org.encinet.mik.feature.community.social.platform.matrix.crypto

import de.connect2x.trixnity.client.key
import de.connect2x.trixnity.client.key.KeyBackupService
import de.connect2x.trixnity.client.key.MasterKeyInvalidException
import de.connect2x.trixnity.client.store.KeyStore
import de.connect2x.trixnity.client.store.StoreTransactionManager
import de.connect2x.trixnity.client.verification
import de.connect2x.trixnity.client.verification.ActiveSasVerificationMethod
import de.connect2x.trixnity.client.verification.ActiveSasVerificationState
import de.connect2x.trixnity.client.verification.ActiveVerificationState
import de.connect2x.trixnity.client.verification.VerificationService
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.MatrixServerException
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.core.model.events.m.key.verification.VerificationMethod
import de.connect2x.trixnity.crypto.key.DeviceTrustLevel
import de.connect2x.trixnity.crypto.key.RecoveryKeyInvalidException
import java.util.logging.Logger
import java.util.Optional
import org.encinet.mik.feature.community.social.platform.matrix.crypto.MatrixCryptoResult.Verification
import org.encinet.mik.feature.community.social.platform.matrix.crypto.MatrixCryptoResult.Phase
import org.encinet.mik.feature.community.social.platform.matrix.crypto.MatrixCryptoResult.Trust
import org.encinet.mik.feature.community.social.platform.matrix.crypto.MatrixCryptoException.Reason
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.koin.core.qualifier.named
import de.connect2x.trixnity.client.MatrixClient as SdkClient

/** Human verification and account recovery; the SDK validates all protocol transitions. */
internal class MatrixDeviceVerification(private val sdk: SdkClient, private val logger: Logger) {
    private val firstSync = CompletableDeferred<Unit>()
    private val refresh = Mutex()
    private val recovery = MatrixAccountRecovery(sdk)

    fun synced() { firstSync.complete(Unit) }

    fun start(scope: CoroutineScope, recoveryKey: String) {
        scope.launch {
            firstSync.await()
            try {
                withTimeout(2.minutes) {
                    refreshOwnKeys()
                    if (recovery.signOwnDeviceIfPossible()) refreshOwnKeys()
                }
            } catch (cancelled: CancellationException) {
                if (cancelled !is TimeoutCancellationException) throw cancelled
                logger.warning("Matrix device signature refresh timed out")
            } catch (failure: Exception) {
                logger.warning("Matrix device signature refresh failed: ${safeFailure(failure)}")
            }
            if (recoveryKey.isNotBlank()) restore(recoveryKey)
        }
        scope.launch {
            sdk.verification.activeDeviceVerification.filterNotNull()
                .mapNotNull { active ->
                    val terminal = active.state.first { it is ActiveVerificationState.Done || it is ActiveVerificationState.Cancel }
                    active.takeIf { terminal is ActiveVerificationState.Done && active.theirUserId == sdk.userId }
                }
                .collectLatest { active -> reconcileSelfVerification(active.transactionId) }
        }
    }

    private suspend fun refreshOwnKeys(): DeviceTrustLevel = refresh.withLock {
        sdk.api.sync.currentSyncState.first { it == SyncState.RUNNING }
        val keys = sdk.di.get<KeyStore>()
        sdk.di.get<StoreTransactionManager>().writeTransaction {
            keys.updateOutdatedKeys { it + sdk.userId }
        }
        // The SDK waits for its key query, validates signatures, and recalculates the trust chain.
        sdk.key.getTrustLevel(sdk.userId, sdk.deviceId).first()
    }

    private suspend fun reconcileSelfVerification(transactionId: String?) {
        try {
            withTimeout(2.minutes) {
                var pause = 1.seconds
                var signed = false
                while (true) {
                    val trust = refreshOwnKeys()
                    if (trust is DeviceTrustLevel.CrossSigned && trust.verified) break
                    if (!signed) signed = recovery.signOwnDeviceIfPossible()
                    // The other client can upload its signature after sending verification.done.
                    delay(pause)
                    pause = (pause * 2).coerceAtMost(10.seconds)
                }
            }
            logger.info("Matrix device verification completed: ${sdk.deviceId} (transaction $transactionId)")
        } catch (timeout: TimeoutCancellationException) {
            logger.warning("Matrix SAS completed but our device cross-signature is not verified (transaction $transactionId)")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            logger.warning("Matrix device verification refresh failed: ${safeFailure(failure)} (transaction $transactionId)")
        }
    }

    private suspend fun restore(recoveryKey: String) {
        var stage = "recover account secrets"
        try {
            withTimeout(2.minutes) {
                recovery.restore(recoveryKey)
                stage = "refresh the device cross-signature"
                refreshOwnKeys()
                sdk.key.getTrustLevel(sdk.userId, sdk.deviceId).first { it is DeviceTrustLevel.CrossSigned && it.verified }
                logger.info("Matrix encryption recovery and device verification completed")
            }
        } catch (timeout: TimeoutCancellationException) {
            logger.warning("Matrix encryption recovery timed out at: $stage")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val recoveryFailure = failure as? MatrixAccountRecovery.Failure
            logger.warning("Matrix encryption recovery failed at ${recoveryFailure?.stage ?: stage}: ${safeFailure(recoveryFailure?.failure ?: failure)}")
        }
    }

    private fun safeFailure(failure: Exception): String = when (failure) {
        is RecoveryKeyInvalidException -> "recovery key format or secret-storage key does not match"
        is MasterKeyInvalidException -> "account master key is missing or does not match"
        is MatrixServerException -> "HTTP ${failure.statusCode.value} (${failure.errorResponse.javaClass.simpleName})"
        // SDK exception messages may contain secret-derived values; log only safe categories.
        else -> failure.javaClass.simpleName
    }

    suspend fun manage(action: String, arguments: List<String>): MatrixCryptoResult {
        if (action == "status") {
            expect(arguments.isEmpty() || arguments.size == 2, Reason.USAGE)
            val active = sdk.verification.activeDeviceVerification.value
            val comparison = ((active?.state?.value as? ActiveVerificationState.Start)?.method as? ActiveSasVerificationMethod)
                ?.state?.value as? ActiveSasVerificationState.ComparisonByUser
            val self = when (sdk.verification.getSelfVerificationMethods().first()) {
                is VerificationService.SelfVerificationMethods.AlreadyCrossSigned -> Verification.VERIFIED
                is VerificationService.SelfVerificationMethods.CrossSigningEnabled -> Verification.UNVERIFIED
                is VerificationService.SelfVerificationMethods.NoCrossSigningEnabled -> Verification.NO_CROSS_SIGNING
                is VerificationService.SelfVerificationMethods.PreconditionsNotMet -> Verification.INITIALIZING
            }
            val target = if (arguments.isEmpty()) null else {
                val userId = UserId(arguments[0])
                val trust = sdk.key.getTrustLevel(userId, arguments[1]).first()
                val keys = sdk.di.get<KeyStore>()
                MatrixCryptoResult.Target(arguments[0], arguments[1], trustState(trust),
                    keys.deviceVerified(trust, keys.getDeviceKeys(userId).first()?.get(arguments[1])?.value?.signed?.keys))
            }
            val transaction = active?.let {
                MatrixCryptoResult.Transaction(it.transactionId, it.theirUserId.full, it.theirDeviceId.orEmpty(),
                    if (comparison != null) Phase.COMPARISON else phase(it.state.value),
                    comparison?.decimal.orEmpty(), comparison?.emojis?.map { emoji -> emoji.second }.orEmpty())
            }
            return MatrixCryptoResult.Status(sdk.userId.full, sdk.deviceId, sdk.signingKey.value.value,
                self, Optional.ofNullable(sdk.di.get<KeyBackupService>(named<KeyBackupService>()).version.value?.version),
                Optional.ofNullable(transaction), Optional.ofNullable(target))
        }
        if (action == "verify") {
            expect(arguments.size == 2, Reason.USAGE)
            val active = sdk.verification.createDeviceVerificationRequest(UserId(arguments[0]), setOf(arguments[1])).getOrThrow()
            return MatrixCryptoResult.Requested(active.transactionId)
        }
        expect(action in setOf("accept", "confirm", "cancel"), Reason.USAGE)
        val active = sdk.verification.activeDeviceVerification.value ?: throw MatrixCryptoException(Reason.NO_ACTIVE)
        expect(arguments.size == 1 && arguments[0] == active.transactionId, Reason.TRANSACTION_REQUIRED)
        when (action) {
            "accept" -> when (val state = active.state.value) {
                is ActiveVerificationState.TheirRequest -> state.ready()
                is ActiveVerificationState.Ready -> state.start(VerificationMethod.Sas)
                is ActiveVerificationState.Start -> {
                    val sas = (state.method as? ActiveSasVerificationMethod)?.state?.value
                    if (sas !is ActiveSasVerificationState.TheirSasStart) throw MatrixCryptoException(Reason.NOT_ACCEPTING)
                    sas.accept()
                }
                else -> throw MatrixCryptoException(Reason.NOT_ACCEPTING)
            }
            "confirm" -> {
                val state = ((active.state.value as? ActiveVerificationState.Start)?.method as? ActiveSasVerificationMethod)?.state?.value
                if (state !is ActiveSasVerificationState.ComparisonByUser) throw MatrixCryptoException(Reason.COMPARISON_NOT_READY)
                state.match()
            }
            "cancel" -> active.cancel()
        }
        return MatrixCryptoResult.Updated()
    }

    private fun expect(condition: Boolean, reason: Reason) {
        if (!condition) throw MatrixCryptoException(reason)
    }

    private fun trustState(trust: DeviceTrustLevel): Trust = when (trust) {
        is DeviceTrustLevel.Valid -> Trust.VALID
        is DeviceTrustLevel.CrossSigned -> Trust.CROSS_SIGNED
        is DeviceTrustLevel.NotCrossSigned -> Trust.NOT_CROSS_SIGNED
        is DeviceTrustLevel.Blocked -> Trust.BLOCKED
        is DeviceTrustLevel.Invalid -> Trust.INVALID
        is DeviceTrustLevel.Unknown -> Trust.UNKNOWN
    }

    private fun phase(state: ActiveVerificationState): Phase = when (state) {
        is ActiveVerificationState.OwnRequest, is ActiveVerificationState.TheirRequest -> Phase.REQUESTED
        is ActiveVerificationState.Ready -> Phase.READY
        is ActiveVerificationState.Start -> Phase.STARTED
        is ActiveVerificationState.WaitForDone -> Phase.WAITING
        is ActiveVerificationState.Done -> Phase.DONE
        is ActiveVerificationState.Cancel -> Phase.CANCELLED
        is ActiveVerificationState.AcceptedByOtherDevice, is ActiveVerificationState.Undefined -> Phase.UNKNOWN
    }
}
