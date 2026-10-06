package org.encinet.mik.module.communication;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnnouncementSlideTest {
    private static final long START = 1_000_000_000L;

    @Test
    void slideClampsTimeAndSettlesExactlyAtTheTarget() {
        var slide = AnnouncementSlide.rest(0).move(1, START);
        assertEquals(0, slide.position(START - 1));
        assertEquals(0, slide.position(START));
        assertEquals(0.875, slide.position(START + AnnouncementSlide.DURATION_NANOS / 2));
        assertTrue(slide.moving(START));
        assertEquals(1, slide.position(START + AnnouncementSlide.DURATION_NANOS));
        assertFalse(slide.moving(START + AnnouncementSlide.DURATION_NANOS));
        assertEquals(1, slide.position(START + AnnouncementSlide.DURATION_NANOS * 2));
    }

    @Test
    void rapidInputsAccumulateTargetsWithoutJumpingTheRenderedPosition() {
        var slide = AnnouncementSlide.rest(0).move(1, START);
        for (int target = 2; target < 20; target++) {
            long now = START + target * 10_000_000L;
            double current = slide.position(now);
            slide = slide.move(target, now);
            assertEquals(current, slide.position(now));
            assertEquals(target, slide.target());
        }
    }

    @Test
    void reverseInputStartsFromTheCurrentInterpolatedPosition() {
        var forward = AnnouncementSlide.rest(0).move(2, START);
        long now = START + AnnouncementSlide.DURATION_NANOS / 2;
        var backward = forward.move(0, now);
        assertEquals(forward.position(now), backward.position(now));
        assertTrue(backward.position(now + 50_000_000L) < backward.position(now));
        assertEquals(0, backward.position(now + AnnouncementSlide.DURATION_NANOS));
    }

    @Test
    void sameTargetDoesNotRestartTheAnimation() {
        var slide = AnnouncementSlide.rest(3).move(4, START);
        assertSame(slide, slide.move(4, START + 50_000_000L));
        assertFalse(AnnouncementSlide.rest(3).moving(START));
        assertThrows(IllegalArgumentException.class, () -> AnnouncementSlide.rest(-1));
        assertThrows(IllegalArgumentException.class, () -> new AnnouncementSlide(Double.NaN, 0, START));
    }
}
