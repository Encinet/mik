package org.encinet.mik.module.performance;

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

    static EffectiveDistances performanceDistances(
            int requestedViewDistance,
            int requestedSimulationDistance,
            int clientViewDistance
    ) {
        int viewDistance = Math.min(
                normalize(requestedViewDistance),
                clientChunkLimit(clientViewDistance));
        int simulationDistance = Math.min(normalize(requestedSimulationDistance), viewDistance);
        return new EffectiveDistances(viewDistance, simulationDistance);
    }

    static int sendDistance(int requestedSendDistance, int clientViewDistance) {
        return Math.min(normalize(requestedSendDistance), clientChunkLimit(clientViewDistance));
    }

    static DistanceChanges changes(
            int currentViewDistance,
            int currentSimulationDistance,
            int targetViewDistance,
            int targetSimulationDistance
    ) {
        return new DistanceChanges(
                currentViewDistance != targetViewDistance,
                currentSimulationDistance != targetSimulationDistance);
    }

    private static int normalize(int distance) {
        return Math.clamp(distance, MINIMUM_DISTANCE, MAXIMUM_DISTANCE);
    }

    record EffectiveDistances(int viewDistance, int simulationDistance) {
    }

    record DistanceChanges(boolean viewDistanceChanged, boolean simulationDistanceChanged) {
    }
}
