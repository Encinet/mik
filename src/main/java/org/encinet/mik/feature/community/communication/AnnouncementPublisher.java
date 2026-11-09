package org.encinet.mik.feature.community.communication;

/** Receives a newly added announcement; timestamp is Unix time in seconds. */
@FunctionalInterface
public interface AnnouncementPublisher {
    void publish(long timestamp, String content);
}
