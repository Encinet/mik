package org.encinet.mik.module.afk.viewing;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Optional plugin boundary. Implementations supply only physical, supported
 * display shapes and must distinguish unknown playback from known playing state.
 * Called on the server thread; failures invalidate viewing suppression.
 */
public interface PhysicalScreenSource {
    List<PhysicalScreen> snapshot(Instant now)
            throws ReflectiveOperationException;

    boolean supportsViewer(UUID playerId) throws ReflectiveOperationException;
}
