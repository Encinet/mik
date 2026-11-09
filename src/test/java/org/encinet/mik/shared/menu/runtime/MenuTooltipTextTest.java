package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.encinet.mik.shared.menu.ui.UiSize;
import org.encinet.mik.shared.menu.ui.UiTooltip;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MenuTooltipTextTest {
    @Test void longAndMultilingualHelpFitsAReadOnlyBoxWithoutChangingTheTextScale() {
        for (String content : new String[] {"Useful help", "很长的名字😀 ".repeat(1000), "A long translated sentence ".repeat(100), "line\n".repeat(1000)}) {
            var full = Component.text(content, NamedTextColor.AQUA);
            for (double scale : new double[] {.3, .7, 1, 1.8}) {
                var available = new UiSize(4, 2.5);
                var text = MenuTooltipText.fit(UiTooltip.of(full), available, scale);
                assertNotNull(text);
                assertTrue(text.layout().size().width() * scale <= available.width());
                assertTrue(text.layout().size().height() * scale <= available.height());
                if (content.equals("Useful help")) assertEquals(full, text.content());
                else assertNotEquals(full, text.content());
            }
        }
        assertNull(MenuTooltipText.fit(UiTooltip.of(Component.text("Help")), new UiSize(.001, .001), 1));
    }
}
