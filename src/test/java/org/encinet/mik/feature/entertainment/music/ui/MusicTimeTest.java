package org.encinet.mik.feature.entertainment.music.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MusicTimeTest {
    @Test void preciseSeekAcceptsSecondsMinutesAndHoursAndFormatsTheSamePosition() {
        assertEquals(83000, MusicTime.parse("83"));
        assertEquals(83000, MusicTime.parse(" 1:23 "));
        assertEquals(3723000, MusicTime.parse("1:02:03"));
        assertEquals("1:23", MusicTime.format(83000));
        assertEquals("1:02:03", MusicTime.format(3723000));
        assertEquals("0:00", MusicTime.format(-1));
    }
    @Test void malformedNegativeAndOverflowingTimesCannotReachPlayback() {
        for (var invalid : new String[]{"", ":30", "1:", "-1", "1:60", "1:60:00", "1:2:3:4", "1.5", "one"})
            assertThrows(IllegalArgumentException.class, () -> MusicTime.parse(invalid), invalid);
        assertThrows(ArithmeticException.class, () -> MusicTime.parse("9223372036854775807"));
    }
}
