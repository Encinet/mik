package org.encinet.mik.module.menu.runtime;

import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuInteraction;
import org.encinet.mik.module.menu.FloatingMenuPoint;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockMenuTranslatorTest {
    @Test
    void spatialViewControlsAreNotMeaninglessNativeFormButtons() {
        var builder = FloatingMenuDefinition.screen("workbench");
        builder.control("rotate", Component.text("Rotate model")).spatialOnly().primary((viewer, handle) -> { });
        builder.control("preview", Component.text("World preview")).primary((viewer, handle) -> { });
        var menu = new BedrockMenuTranslator().translate(builder.build(), LABELS);
        assertEquals(1, menu.options().size());
        assertEquals("preview", menu.options().getFirst().elementId());
        assertFalse(menu.content().contains("Rotate model"));
    }

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

    @Test
    void permissionTableButtonsKeepOperationNamesAndSourcesWithoutSpatialColumnHeadings() {
        var builder = FloatingMenuDefinition.screen("plot-permissions");
        builder.information("heading", Component.text("Test plot")).region("heading");
        builder.information("operation-heading", Component.text("Operation column"))
                .region("operation-heading").spatialOnly();
        builder.information("result-heading", Component.text("Status column"))
                .region("result-heading").spatialOnly();
        builder.information("section", Component.text("Storage access")).region("section");
        builder.control("subject", Component.text("Invited visitors ▾"))
                .region("subject").primary((viewer, handle) -> { });
        for (String action : java.util.List.of("container", "entity_storage")) {
            builder.information(action + "-label", Component.text(action))
                    .region("permission-" + action + "-group-heading");
            builder.control("visitor." + action, Component.text("Allowed\nParent plot"))
                    .region("permission-" + action).primary((viewer, handle) -> { });
        }
        var menu = new BedrockMenuTranslator().translate(builder.build(), LABELS);
        assertEquals("§fTest plot", menu.title());
        assertTrue(menu.content().contains("Storage access"));
        assertFalse(menu.content().contains("Operation column"));
        assertFalse(menu.content().contains("Status column"));
        assertFalse(menu.content().contains("container"));
        assertEquals(3, menu.options().size());
        assertTrue(menu.options().get(1).label().contains("container\n"));
        assertTrue(menu.options().get(2).label().contains("entity_storage\n"));
        assertTrue(menu.options().get(1).label().contains("Allowed\nParent plot"));
        assertEquals("visitor.container", menu.options().get(1).elementId());
        assertEquals("visitor.entity_storage", menu.options().get(2).elementId());
    }

    @Test
    void pairedSectionHeadingsStayAttachedToTheirNativeFormChoices() {
        FloatingMenuDefinition.Builder builder = FloatingMenuDefinition.screen("plot-access");
        builder.information("heading", Component.text("Access and members")).region("heading");
        builder.information("visitor-group-heading", Component.text("Invited visitors"))
                .region("visitor-group-heading");
        builder.information("newcomer-group-heading", Component.text("Newcomer defaults"))
                .region("newcomer-group-heading");
        builder.information("member-group-heading", Component.text("Member defaults"))
                .region("member-group-heading");
        builder.control("visitor.interact", Component.text("Block interaction\ndefault"))
                .region("visitor").primary((player, handle) -> { });
        builder.control("newcomer.interact", Component.text("Block interaction\ndisabled"))
                .region("newcomer").primary((player, handle) -> { });
        builder.control("member.interact", Component.text("Block interaction\nenabled"))
                .region("member").primary((player, handle) -> { });

        BedrockMenuTranslator.Menu menu =
                new BedrockMenuTranslator().translate(builder.build(), LABELS);

        assertEquals("§fAccess and members", menu.title());
        assertFalse(menu.content().contains("Invited visitors"));
        assertFalse(menu.content().contains("Newcomer defaults"));
        assertFalse(menu.content().contains("Member defaults"));
        assertTrue(menu.options().get(0).label().contains("Invited visitors\n"));
        assertTrue(menu.options().get(1).label().contains("Newcomer defaults\n"));
        assertTrue(menu.options().get(2).label().contains("Member defaults\n"));
    }
}
