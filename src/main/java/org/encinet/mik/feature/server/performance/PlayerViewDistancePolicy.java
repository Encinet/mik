package org.encinet.mik.feature.server.performance;

/**
 * Resolves per-player distance limits against the render distance advertised by the client.
 */
final class PlayerViewDistancePolicy {

    private static final int MINIMUM_DISTANCE = 2;
    private static final int MAXIMUM_DISTANCE = 32;

    private PlayerViewDistancePolicy() {
    }

    /**
     * Paper's automatic chunk sender keeps one boundary chunk beyond the client's render setting.
     * Preserve that boundary when installing an explicit per-player distance.
     */
    static int clientChunkLimit(int clientViewDistance) {
        return (int) Math.clamp(
                (long) clientViewDistance + 1L,
                MINIMUM_DISTANCE,
                MAXIMUM_DISTANCE);
    }

    static int sendDistance(int requestedSendDistance, int clientViewDistance) {
        return Math.min(normalize(requestedSendDistance), clientChunkLimit(clientViewDistance));
    }

    private static int normalize(int distance) {
        return Math.clamp(distance, MINIMUM_DISTANCE, MAXIMUM_DISTANCE);
    }
}
