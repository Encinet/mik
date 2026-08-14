package org.encinet.mik.module.ban;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BanMessageRendererTest {

    @Test
    void banScreenUsesSeparatedVisualSections() {
        Component screen = BanMessageRenderer.banScreen(
                "米客", "你已被服务器封禁", "原因", "破坏建筑",
                "解封时间", "2026年8月20日 20:00", "请联系管理员申诉");

        assertEquals("米客\n"
                        + "━━━━━━━━━━━━━━━━━━━━\n\n"
                        + "你已被服务器封禁\n\n"
                        + "原因\n"
                        + "破坏建筑\n\n"
                        + "解封时间\n"
                        + "2026年8月20日 20:00\n\n"
                        + "请联系管理员申诉\n"
                        + "━━━━━━━━━━━━━━━━━━━━",
                PlainTextComponentSerializer.plainText().serialize(screen));
        Set<TextColor> colors = new HashSet<>();
        collectColors(screen, colors);
        assertTrue(colors.contains(TextColor.color(0xF6C453)));
        assertTrue(colors.contains(TextColor.color(0xFF5C7A)));
        assertTrue(colors.contains(TextColor.color(0xFF9A62)));
        assertTrue(colors.contains(TextColor.color(0xA7B0C0)));
        assertTrue(colors.contains(TextColor.color(0xFFD166)));
        assertTrue(colors.contains(TextColor.color(0x69D2E7)));
    }

    private void collectColors(Component component, Set<TextColor> colors) {
        if (component.color() != null) {
            colors.add(component.color());
        }
        component.children().forEach(child -> collectColors(child, colors));
    }
}
