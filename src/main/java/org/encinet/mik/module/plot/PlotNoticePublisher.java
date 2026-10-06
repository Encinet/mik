package org.encinet.mik.module.plot;

import java.util.UUID;

/** Sends a plot notice to the linked social accounts of one player. */
@FunctionalInterface
public interface PlotNoticePublisher {
    void publish(UUID playerId, String playerName, String text);
}
