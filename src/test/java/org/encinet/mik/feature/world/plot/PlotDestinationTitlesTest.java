package org.encinet.mik.feature.world.plot;

import static org.junit.jupiter.api.Assertions.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.shared.i18n.Message;
import org.junit.jupiter.api.Test;

class PlotDestinationTitlesTest {
    @Test void formatsMiniMessageAndCountsVisibleUnicodeTextSeparatelyFromSource() {
        assertEquals(Component.text("Welcome", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                PlotDestinationTitles.render("<gold><bold>Welcome</bold></gold>"));
        assertEquals("Welcome", PlainTextComponentSerializer.plainText().serialize(
                PlotDestinationTitles.render("<gradient:red:gold>Welcome</gradient>")));
        assertDoesNotThrow(() -> PlotDestinationTitles.validate("<gold>" + "😀".repeat(32) + "</gold>", "😀".repeat(64)));
        assertEquals(Message.PLOT_DESTINATION_ERROR_TITLE, assertThrows(PlotProblem.class,
                () -> PlotDestinationTitles.validate("😀".repeat(33), "")).message());
        assertEquals(Message.PLOT_DESTINATION_ERROR_SUBTITLE, assertThrows(PlotProblem.class,
                () -> PlotDestinationTitles.validate("", "😀".repeat(65))).message());
        assertThrows(PlotProblem.class, () -> PlotDestinationTitles.validate("<gold>".repeat(100) + "A", ""));
    }

    @Test void emptyFieldsAreOptionalButControlsMultipleLinesAndFormattingOnlyAreRejected() {
        assertDoesNotThrow(() -> PlotDestinationTitles.validate("", ""));
        assertDoesNotThrow(() -> PlotDestinationTitles.validate("", "<aqua>Only a subtitle</aqua>"));
        for (String invalid : new String[]{"A\nB", "A\rB", "A\u2028B", "A\u0000B", "<red></red>", "<bold> </bold>"})
            assertThrows(PlotProblem.class, () -> PlotDestinationTitles.validate(invalid, ""), invalid);
    }
}
