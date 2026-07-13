package org.encinet.mik.module.presentation.motd.holiday;

import java.time.LocalDate;

record ChinaHolidayEvent(LocalDate start, LocalDate endExclusive, String summary) {
    boolean includes(LocalDate date) {
        return !date.isBefore(start) && date.isBefore(endExclusive);
    }
}
