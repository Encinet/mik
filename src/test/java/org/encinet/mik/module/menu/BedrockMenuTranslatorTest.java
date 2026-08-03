package org.encinet.mik.module.menu;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockMenuTranslatorTest {

    private static final BedrockMenuTranslator.ActionLabels LABELS =
            new BedrockMenuTranslator.ActionLabels(
                    "Primary", "Secondary", "Shortcut", "Up", "Down");

    @Test
    void semanticNodesBecomeNativeContentAndButtons() {
        FloatingMenuDefinition.Builder builder = FloatingMenuDefinition.builder();
        builder.information("heading", Component.text("Settings")).region("heading");
        builder.textDecoration("ambient", new FloatingMenuPoint(0, 0, 0),
                Component.text("Current state"));
        builder.information("detail", Component.text("Details"));
        builder.control("toggle", Component.text("Toggle"))
                .selected(true)
                .primary((player, handle) -> { })
                .secondary((player, handle) -> { });
        builder.control("locked", Component.text("Locked"))
                .primary((player, handle) -> { })
                .disabled(Component.text("Unavailable"));
        builder.on(FloatingMenuInteraction.SCROLL_DOWN,
                (player, handle, interaction) -> { });

        BedrockMenuTranslator.Menu menu =
                new BedrockMenuTranslator().translate(builder.build(), LABELS);

        assertEquals("§fSettings", menu.title());
        assertFalse(menu.content().contains("Settings"));
        assertTrue(menu.content().contains("Current state"));
        assertTrue(menu.content().contains("Details"));
        assertEquals(3, menu.options().size());

        BedrockMenuTranslator.Option toggle = menu.options().get(0);
        assertEquals("toggle", toggle.elementId());
        assertTrue(toggle.label().contains("✓"));
        assertEquals(java.util.List.of(FloatingMenuInteraction.PRIMARY,
                FloatingMenuInteraction.SECONDARY), toggle.interactions());

        BedrockMenuTranslator.Option locked = menu.options().get(1);
        assertFalse(locked.enabled());
        assertEquals("§cUnavailable", locked.disabledReason());
        assertTrue(locked.label().contains("Unavailable"));

        BedrockMenuTranslator.Option global = menu.options().get(2);
        assertNull(global.elementId());
        assertEquals("§fDown", global.label());
        assertEquals(java.util.List.of(FloatingMenuInteraction.SCROLL_DOWN),
                global.interactions());
    }

    @Test
    void untitledMenuInfersAUsefulNativeTitleWithoutInventingCopy() {
        FloatingMenuDefinition.Builder builder = FloatingMenuDefinition.builder();
        builder.information("status", Component.text("Now playing\nTrack name"));

        BedrockMenuTranslator.Menu menu =
                new BedrockMenuTranslator().translate(builder.build(), LABELS);

        assertEquals("§fNow playing", menu.title());
        assertTrue(menu.content().contains("Track name"));
        assertTrue(menu.options().isEmpty());
    }
}
