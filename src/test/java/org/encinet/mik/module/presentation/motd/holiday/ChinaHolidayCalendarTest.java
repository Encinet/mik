package org.encinet.mik.module.presentation.motd.holiday;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

class ChinaHolidayCalendarTest {

    @Test
    void parsesOnlyOffDaysWithUsableNamesAndDates() {
        String json = """
                {
                  "days": [
                    {"name":"春节","date":"2026-02-17","isOffDay":true},
                    {"name":"春节补班","date":"2026-02-14","isOffDay":false},
                    {"name":"","date":"2026-05-01","isOffDay":true},
                    {"name":"端午","isOffDay":true}
                  ]
                }
                """;

        var events = HolidayCnParser.parseYearResponse(json);

        assertEquals(1, events.size());
        assertEquals(LocalDate.of(2026, 2, 17), events.getFirst().start());
        assertEquals(LocalDate.of(2026, 2, 18), events.getFirst().endExclusive());
        assertEquals("春节", events.getFirst().summary());
    }
}
