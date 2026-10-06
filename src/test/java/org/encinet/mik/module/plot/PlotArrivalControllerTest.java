package org.encinet.mik.module.plot;

import org.encinet.mik.module.i18n.Message;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlotArrivalControllerTest {

    @Test
    void formattedTitlesUseVisibleCodePointsForTheLengthLimit() {
        String title = "<gold>" + "😀".repeat(32) + "</gold>";
        assertDoesNotThrow(() -> PlotArrivalController.validateText(title, 32));
        assertEquals(Message.PLOT_ERROR_ARRIVAL_TEXT,
                assertThrows(PlotProblem.class,
                        () -> PlotArrivalController.validateText(title + "a", 32)).message());
    }

    @Test
    void titlesStayOnOneLineAndCannotConsistOnlyOfFormatting() {
        for (String invalid : new String[]{"first\nsecond", "first\u2028second",
                "first\u2029second", "<red></red>"}) {
            assertEquals(Message.PLOT_ERROR_ARRIVAL_TEXT,
                    assertThrows(PlotProblem.class,
                            () -> PlotArrivalController.validateText(invalid, 32)).message());
        }
    }
}
