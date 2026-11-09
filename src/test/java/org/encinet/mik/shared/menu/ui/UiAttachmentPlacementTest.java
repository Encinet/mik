package org.encinet.mik.shared.menu.ui;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class UiAttachmentPlacementTest {
    @Test void aNeighbouringPanelFlipsTheHintToTheFreeSideOfThePointer() {
        var obstacle = new UiAttachmentPlacement.Rect(104, 84, 96, 76);
        var pointer = new UiLayout.Position(100, 80);
        var placed = UiAttachmentPlacement.place(pointer, new UiSize(60, 30), new UiSize(200, 160), 4, List.of(obstacle)).orElseThrow();
        assertEquals(0, placed.intersectionArea(obstacle));
        assertTrue(placed.x() + placed.width() < pointer.x());
        assertFalse(placed.contains(pointer));
    }

    @Test void nearbyEdgesOfferAFreePositionWhenEveryPointerQuadrantIsBlocked() {
        var obstacles = List.of(new UiAttachmentPlacement.Rect(154, 104, 40, 20), new UiAttachmentPlacement.Rect(106, 104, 40, 20),
                new UiAttachmentPlacement.Rect(154, 76, 40, 20), new UiAttachmentPlacement.Rect(106, 76, 40, 20));
        var pointer = new UiLayout.Position(150, 100);
        var placed = UiAttachmentPlacement.place(pointer, new UiSize(40, 20), new UiSize(300, 200), 4, obstacles).orElseThrow();
        assertTrue(obstacles.stream().allMatch(rect -> rect.intersectionArea(placed) == 0));
        assertTrue(placed.x() >= 0 && placed.y() >= 0 && placed.x() + placed.width() <= 300 && placed.y() + placed.height() <= 200);
        assertFalse(placed.contains(pointer));
        var reordered = new ArrayList<>(obstacles);
        Collections.reverse(reordered);
        assertEquals(placed, UiAttachmentPlacement.place(pointer, new UiSize(40, 20), new UiSize(300, 200), 4, reordered).orElseThrow());
    }

    @Test void denseContentStillGetsANearbyHintWithoutCoveringThePointer() {
        var pointer = new UiLayout.Position(150, 100);
        var placed = UiAttachmentPlacement.place(pointer, new UiSize(40, 20), new UiSize(300, 200), 4,
                List.of(new UiAttachmentPlacement.Rect(0, 0, 300, 200))).orElseThrow();
        assertEquals(new UiAttachmentPlacement.Rect(154, 104, 40, 20), placed);
    }

    @Test void overlaysUseThePointerAndFlipAtViewportEdgesWithoutNeedingAnEmptyLayoutSlot() {
        assertEquals(new UiAttachmentPlacement.Rect(54, 44, 40, 20),
                UiAttachmentPlacement.place(new UiLayout.Position(50, 40), new UiSize(40, 20), new UiSize(100, 80), 4).orElseThrow());
        assertEquals(new UiAttachmentPlacement.Rect(51, 55, 40, 20),
                UiAttachmentPlacement.place(new UiLayout.Position(95, 79), new UiSize(40, 20), new UiSize(100, 80), 4).orElseThrow());
        assertTrue(UiAttachmentPlacement.place(new UiLayout.Position(50, 40),
                new UiSize(100, 80), new UiSize(100, 80), 4).isEmpty());
    }

    @Test void invalidOrUnboundedGeometryDoesNotReachTheRenderer() {
        assertThrows(IllegalArgumentException.class, () -> new UiAttachmentPlacement.Rect(Double.NaN, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new UiAttachmentPlacement.Rect(0, 0, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> UiAttachmentPlacement.place(
                new UiLayout.Position(0, 0), new UiSize(10, 10), new UiSize(100, 100), -1));
        assertThrows(IllegalArgumentException.class, () -> UiAttachmentPlacement.place(
                new UiLayout.Position(-1, 0), new UiSize(10, 10), new UiSize(100, 100), 4));
    }
}
