package org.encinet.mik.module.plot;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotSelectionCommandsTest {
    private final UUID world = UUID.randomUUID();
    private final PlotPosition current = new PlotPosition(world, -1, 65, 3);

    @Test
    void onlyExactWorldEditPositionCommandsAreClaimed() {
        assertTrue(PlotSelectionCommands.matches(" //POS1 "));
        assertTrue(PlotSelectionCommands.matches("//pos2 -8,64,3"));
        for (String input : List.of("/pos1", "//pos10", "//hpos1", "//set stone", ""))
            assertFalse(PlotSelectionCommands.matches(input));
        assertEquals(new PlotSelectionCommands.Mark(true, current),
                PlotSelectionCommands.parse("//pos1", current));
    }

    @Test
    void parsesSignedCoordinatesAndRejectsPartialOverflowOrExtraArguments() {
        assertEquals(new PlotSelectionCommands.Mark(false, new PlotPosition(world, -8, 64, 12)),
                PlotSelectionCommands.parse("//pos2 -8,64,+12", current));
        for (String input : List.of("//pos1 1,2", "//pos2 1,2,", "//pos1 2147483648,64,0",
                "//pos1 1,64,3 extra", "//pos1 ~,64,0", "//pos1 a,64,0", "//set stone"))
            assertThrows(PlotProblem.class, () -> PlotSelectionCommands.parse(input, current), input);
    }

    @Test
    void actionbarOnlyShowsCommandCompletionAndAlignedDimensions() {
        assertEquals("//pos1 · //pos2", PlotSelectionCommands.status(new PlotSelectionState.Points(null, null)));
        assertEquals("//pos1 ✓ · //pos2", PlotSelectionCommands.status(new PlotSelectionState.Points(current, null)));
        assertEquals("//pos1 · //pos2 ✓", PlotSelectionCommands.status(new PlotSelectionState.Points(null, current)));
        assertEquals("//pos1 ✓ · //pos2 ✓ · 8×4×4", PlotSelectionCommands.status(
                new PlotSelectionState.Points(current, new PlotPosition(world, 3, 67, 3))));
        assertFalse(PlotSelectionCommands.status(new PlotSelectionState.Points(current,
                new PlotPosition(UUID.randomUUID(), 3, 67, 3))).contains("×"));
    }
}
