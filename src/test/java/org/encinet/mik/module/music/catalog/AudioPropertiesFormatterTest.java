package org.encinet.mik.module.music.catalog;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AudioPropertiesFormatterTest {

    @Test
    void formatsNumericPropertiesOnlyAtThePresentationBoundary() {
        assertEquals("512 B", AudioPropertiesFormatter.fileSize(512L));
        assertEquals("1.5 KB", AudioPropertiesFormatter.fileSize(1536L));
        assertEquals("44.1 kHz", AudioPropertiesFormatter.sampleRate(44_100));
        assertEquals("3:05", AudioPropertiesFormatter.duration(Duration.ofSeconds(185)));
        assertEquals("1:01:01", AudioPropertiesFormatter.duration(Duration.ofSeconds(3661)));
        assertEquals("0:01", AudioPropertiesFormatter.duration(Duration.ofMillis(500)));
    }

    @Test
    void omitsUnavailableOrInvalidValues() {
        assertNull(AudioPropertiesFormatter.fileSize(null));
        assertNull(AudioPropertiesFormatter.sampleRate(null));
        assertNull(AudioPropertiesFormatter.duration(null));
        assertNull(AudioPropertiesFormatter.duration(Duration.ZERO));
    }
}
