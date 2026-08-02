package org.encinet.mik.module.space;

/**
 * Public Bukkit service for inspecting, reloading, and extending the spatial network.
 * The returned network is immutable and safe to inspect anywhere. Mutating operations
 * ({@link #reload()} and {@link #register(SpaceLink)}) must run on the server thread.
 */
public interface NonEuclideanSpaceService {

    SpaceNetwork snapshot();

    SpaceReloadResult reload();

    SpaceRegistration register(SpaceLink link);
}
