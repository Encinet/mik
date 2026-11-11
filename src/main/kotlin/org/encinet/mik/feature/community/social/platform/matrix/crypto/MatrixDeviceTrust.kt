package org.encinet.mik.feature.community.social.platform.matrix.crypto

import de.connect2x.trixnity.client.store.KeyStore
import de.connect2x.trixnity.client.store.KeyVerificationState
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.core.model.keys.Keys
import de.connect2x.trixnity.crypto.key.DeviceTrustLevel

/** Uses the SDK's exact-key verification records as well as its cross-signing trust chain. */
internal suspend fun KeyStore.deviceVerified(level: DeviceTrustLevel?, keys: Keys?): Boolean {
    if (level !is DeviceTrustLevel.Valid && level !is DeviceTrustLevel.CrossSigned && level != DeviceTrustLevel.NotCrossSigned) return false
    val signingKey = keys?.keys?.filterIsInstance<Key.Ed25519Key>()?.singleOrNull() ?: return false
    return when (getKeyVerificationState(signingKey)) {
        is KeyVerificationState.Blocked -> false
        is KeyVerificationState.Verified -> true
        else -> when (level) {
            is DeviceTrustLevel.Valid -> level.verified
            is DeviceTrustLevel.CrossSigned -> level.verified
            else -> false
        }
    }
}
