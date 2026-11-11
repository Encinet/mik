package org.encinet.mik.feature.community.social.platform.matrix.crypto

import de.connect2x.trixnity.client.key
import de.connect2x.trixnity.client.key.KeyBackupService
import de.connect2x.trixnity.client.verification
import de.connect2x.trixnity.client.verification.ActiveSasVerificationMethod
import de.connect2x.trixnity.client.verification.ActiveSasVerificationState
import de.connect2x.trixnity.client.verification.ActiveVerificationState
import de.connect2x.trixnity.client.verification.SelfVerificationMethod
import de.connect2x.trixnity.client.verification.VerificationService
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.key.verification.VerificationMethod
import de.connect2x.trixnity.crypto.key.DeviceTrustLevel
import java.util.logging.Logger
import java.util.Optional
import org.encinet.mik.feature.community.social.platform.matrix.crypto.MatrixCryptoResult.Verification
import org.encinet.mik.feature.community.social.platform.matrix.crypto.MatrixCryptoResult.Phase
import org.encinet.mik.feature.community.social.platform.matrix.crypto.MatrixCryptoResult.Trust
import org.encinet.mik.feature.community.social.platform.matrix.crypto.MatrixCryptoException.Reason
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.koin.core.qualifier.named
import de.connect2x.trixnity.client.MatrixClient as SdkClient

/** Human verification and account recovery; the SDK validates all protocol transitions. */
internal class MatrixDeviceVerification(private val sdk: SdkClient, private val logger: Logger) {
    suspend fun restore(recoveryKey: String) {
        try {
            withTimeout(2.minutes) {
                val methods = sdk.verification.getSelfVerificationMethods()
                    .first { it !is VerificationService.SelfVerificationMethods.PreconditionsNotMet }
                when (methods) {
                    is VerificationService.SelfVerificationMethods.AlreadyCrossSigned -> Unit
                    is VerificationService.SelfVerificationMethods.CrossSigningEnabled ->
                        checkNotNull(methods.methods.filterIsInstance<SelfVerificationMethod.AesHmacSha2RecoveryKey>().firstOrNull()) {
                            "Matrix account has no recovery-key verification method"
                        }.verify(recoveryKey).getOrThrow()
                    else -> error("Set up Matrix account recovery in a trusted client before configuring encryption.recovery-key")
                }
                sdk.key.getTrustLevel(sdk.userId, sdk.deviceId).first { it is DeviceTrustLevel.CrossSigned && it.verified }
                logger.info("Matrix encryption recovery and device verification completed")
            }
        } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
            logger.warning("Matrix encryption recovery timed out; check account recovery and server connectivity")
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            logger.warning("Matrix encryption recovery failed; check encryption.recovery-key and account recovery settings")
        }
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
                val trust = sdk.key.getTrustLevel(UserId(arguments[0]), arguments[1]).first()
                MatrixCryptoResult.Target(arguments[0], arguments[1], trustState(trust), when (trust) {
                    is DeviceTrustLevel.Valid -> trust.verified
                    is DeviceTrustLevel.CrossSigned -> trust.verified
                    else -> false
                })
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
