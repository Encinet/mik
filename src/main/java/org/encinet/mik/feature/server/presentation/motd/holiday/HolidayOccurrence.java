package org.encinet.mik.feature.server.presentation.motd.holiday;

public record HolidayOccurrence(String name) {
    public HolidayOccurrence {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Holiday name must not be blank");
        }
    }
}
