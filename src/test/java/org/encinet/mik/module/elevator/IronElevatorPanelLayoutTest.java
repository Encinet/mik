package org.encinet.mik.module.elevator;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IronElevatorPanelLayoutTest {

    @Test
    void fitsTwentyFloorsOnTwoWideTwoHighWallsWithoutLoweringTheButtons() {
        var layout = IronElevatorPanelLayout.fit(-0.5, 1.5, -1, 1, 20);
        assertNotNull(layout);
        assertFalse(layout.portrait());
        assertEquals(5, layout.columns());
        assertEquals(4, layout.rows());
        assertEquals(0.3, layout.horizontalGap());
        assertEquals(0.18, layout.verticalGap());
        assertFalse(layout.paginated());
        assertEquals(0.5, layout.centerSideways());
        assertTrue(layout.centerAbove() - layout.height() / 2 >= 0);
    }

    @Test
    void balancesActualWidthAndHeightInsteadOfFollowingTallWalls() {
        var layout = IronElevatorPanelLayout.fit(-0.5, 1.5, -1, 5, 20);
        assertNotNull(layout);
        assertEquals(4, layout.columns());
        assertEquals(5, layout.rows());
        assertFalse(layout.paginated());
        assertTrue(layout.width() / layout.height() < 1.5);
        var fewFloors = IronElevatorPanelLayout.fit(-0.5, 1.5, -1, 5, 7);
        assertEquals(2, fewFloors.columns());
        assertEquals(4, fewFloors.rows());
        assertTrue(fewFloors.height() / fewFloors.width() < 1.5);
    }

    @Test
    void usesHorizontalRowsWhenVerticalSpaceIsLimited() {
        var layout = IronElevatorPanelLayout.fit(-2, 2, 0, 0.6, 12);
        assertNotNull(layout);
        assertFalse(layout.portrait());
        assertEquals(6, layout.columns());
        assertEquals(2, layout.rows());
        assertEquals(0.3, layout.horizontalGap());
        assertEquals(0.18, layout.verticalGap());
        assertFalse(layout.paginated());
        assertFalse(layout.pageCounter());
    }

    @Test
    void doesNotLiftOrStretchTheLayoutWhenMoreWallHeightBecomesAvailable() {
        for (int levels : List.of(3, 7, 20, 60, 448)) {
            var threeHigh = IronElevatorPanelLayout.fit(-0.5, 1.5, -1, 2, levels);
            var sixHigh = IronElevatorPanelLayout.fit(-0.5, 1.5, -1, 5, levels);
            assertEquals(threeHigh, sixHigh);
            assertTrue(sixHigh.centerAbove() - sixHigh.height() / 2 >= 0);
            assertTrue(sixHigh.centerAbove() + sixHigh.height() / 2 <= 1.6);
        }
    }

    @Test
    void centersOnTheProjectedPlatformRatherThanTheWallMidpoint() {
        var layout = IronElevatorPanelLayout.fit(-2.5, 2.5, -1, 2, 7, 0.75);
        assertEquals(0.75, layout.centerSideways());
        var clamped = IronElevatorPanelLayout.fit(-0.5, 0.5, -1, 2, 7, 4);
        assertTrue(clamped.centerSideways() + clamped.width() / 2 + 0.04 <= 0.5);
    }

    @Test
    void minimizesPagesWithinTheReachableBandBeforeOptimizingShape() {
        var layout = IronElevatorPanelLayout.fit(-0.5, 1.5, -1, 5, 60);
        assertTrue(layout.paginated());
        assertEquals(30, layout.pageSize());
        assertEquals(2, (60 + layout.pageSize() - 1) / layout.pageSize());
        assertTrue(layout.centerAbove() - layout.height() / 2 >= 0);
        assertTrue(layout.centerAbove() + layout.height() / 2 <= 1.6);
    }

    @Test
    void usesAllReachableRowsAndColumnsBeforePaginating() {
        for (int width = 1; width <= 7; width++) {
            for (int height = 2; height <= 6; height++) {
                int columns = (int) Math.floor((width - 0.24 - 0.27) / 0.3 + 1.0E-9) + 1;
                int rows = (int) Math.floor((Math.min(height - 1, 1.6) - 0.24 - 0.16)
                        / 0.18 + 1.0E-9) + 1;
                int capacity = columns * rows;
                var full = IronElevatorPanelLayout.fit(-0.5, width - 0.5, -1, height - 1, capacity);
                assertFalse(full.paginated(), width + "x" + height);
                assertEquals(capacity, full.pageSize());
                assertEquals(0, full.footerWidth());
                var overflow = IronElevatorPanelLayout.fit(-0.5, width - 0.5, -1, height - 1, capacity + 1);
                assertTrue(overflow.paginated(), width + "x" + height);
            }
        }
    }

    @Test
    void centersIncompleteRowsAndSmallFloorCounts() {
        var layout = IronElevatorPanelLayout.fit(-1.5, 1.5, -1, 1, 21);
        assertEquals(layout.centerSideways(), layout.floor(layout.columns(), layout.columns() + 1).sideways());
        assertEquals(layout.centerSideways(), layout.floor(0, 1).sideways());
        assertEquals(layout.centerSideways() - layout.horizontalGap() / 2, layout.floor(0, 2).sideways());
        assertEquals(layout.centerSideways() + layout.horizontalGap() / 2, layout.floor(1, 2).sideways());
        var small = IronElevatorPanelLayout.fit(-1.5, 1.5, -1, 1, 3);
        assertEquals(3, small.columns());
        assertEquals(1, small.rows());
        assertEquals(small.centerSideways(), small.floor(1, 3).sideways());
    }

    @Test
    void paginatesNarrowWallsWithoutCrowdingTheFooterOrMovingTheFrame() {
        var layout = IronElevatorPanelLayout.fit(-0.5, 0.5, -1, 1, 60);
        assertTrue(layout.paginated());
        assertFalse(layout.pageCounter());
        assertTrue(layout.navigation(-1).sideways() + layout.halfWidth()
                < layout.navigation(1).sideways() - layout.halfWidth());
        assertEquals(layout.floor(0, layout.pageSize()).above(), layout.floor(0, 1).above());
        int visited = 0;
        for (int start = 0; start < 60; start += layout.pageSize()) {
            int pageItems = Math.min(layout.pageSize(), 60 - start);
            for (int slot = 0; slot < pageItems; slot++) {
                assertNotNull(layout.floor(slot, pageItems));
                visited++;
            }
        }
        assertEquals(60, visited);
    }

    @Test
    void keepsButtonsFootersAndProtectionInsideTheWallAndOneBlockAboveTheFloor() {
        for (int width = 1; width <= 7; width++) {
            for (int height = 2; height <= 6; height++) {
                for (int levels : List.of(3, 7, 20, 21, 60, 448)) {
                    var layout = IronElevatorPanelLayout.fit(-0.5, width - 0.5, -1, height - 1, levels);
                    assertNotNull(layout);
                    assertTrue(layout.centerSideways() - layout.width() / 2 - 0.04 >= -0.5);
                    assertTrue(layout.centerSideways() + layout.width() / 2 + 0.04 <= width - 0.5);
                    assertTrue(layout.centerAbove() - layout.height() / 2 - 0.04 >= 0);
                    assertTrue(layout.centerAbove() + layout.height() / 2 + 0.04 <= Math.min(height - 1, 1.6));
                    List<IronElevatorPanelLayout.Position> positions = new ArrayList<>();
                    for (int slot = 0; slot < layout.pageSize(); slot++)
                        positions.add(layout.floor(slot, layout.pageSize()));
                    if (layout.paginated()) {
                        positions.add(layout.navigation(-1));
                        positions.add(layout.navigation(1));
                    }
                    for (int first = 0; first < positions.size(); first++) {
                        var position = positions.get(first);
                        assertTrue(layout.contains(position.sideways(), position.above()));
                        assertTrue(position.above() - layout.halfHeight() >= 0);
                        for (int second = first + 1; second < positions.size(); second++) {
                            var other = positions.get(second);
                            assertTrue(Math.abs(position.sideways() - other.sideways()) > layout.halfWidth() * 2
                                    || Math.abs(position.above() - other.above()) > layout.halfHeight() * 2);
                        }
                    }
                    assertFalse(layout.contains(layout.centerSideways() + layout.width() / 2 + 0.05,
                            layout.centerAbove()));
                }
            }
        }
    }

    @Test
    void rejectsInvalidCountsAndWallsTooSmallToFitButtons() {
        assertThrows(IllegalArgumentException.class, () -> IronElevatorPanelLayout.fit(-1, 1, -1, 1, 0));
        assertNull(IronElevatorPanelLayout.fit(0, 0.4, 0, 2, 3));
        assertNull(IronElevatorPanelLayout.fit(0, 2, 0, 0.3, 3));
        assertNull(IronElevatorPanelLayout.fit(0, 2, -1, -0.1, 3));
        var layout = IronElevatorPanelLayout.fit(-1, 1, -1, 1, 20);
        assertThrows(IllegalArgumentException.class, () -> layout.floor(-1, 3));
        assertThrows(IllegalArgumentException.class, () -> layout.floor(3, 3));
        assertThrows(IllegalArgumentException.class, () -> layout.floor(0, layout.pageSize() + 1));
    }
}
