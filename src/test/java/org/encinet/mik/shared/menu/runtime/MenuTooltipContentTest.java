package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.ui.Ui;
import org.encinet.mik.shared.menu.ui.UiSize;
import org.junit.jupiter.api.Test;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MenuTooltipContentTest {
    @Test void repeatingTheCaptionEvenWithAnotherColourDoesNotCreateHelp() {
        var caption = Component.text("3:45", NamedTextColor.AQUA);
        var scene = MenuScene.screen("tip").content(Ui.text(caption).key("value")
                .tooltip(Component.text("3:45", NamedTextColor.WHITE)).build()).build();
        assertNull(MenuTooltipContent.resolve(entry(scene, "value"), caption, _ -> false));
    }

    @Test void shortenedNamesAndUsefulOperationalHelpAreKept() {
        Component full = Component.text("A much longer song name");
        var scene = MenuScene.screen("tip").viewport(new UiSize(80,55)).content(Ui.column()
                .child(Ui.button(full).summaryLines(1).key("song").build())
                .child(Ui.button(Component.text("Volume")).key("volume").tooltip(Component.text("Use the wheel to adjust.")).build()).build()).build();
        var measured = scene.measured(SceneGeometryTestSupport.naturalFrame(scene,1));
        assertEquals(full,entry(scene,"song").label());
        assertNotEquals(full,entry(measured,"song").label());
        assertEquals(full, MenuTooltipContent.resolve(entry(measured, "song"), entry(measured, "song").label(), _ -> false).text());
        assertEquals(Component.text("Use the wheel to adjust."), MenuTooltipContent.resolve(entry(scene, "volume"), entry(scene, "volume").label(), _ -> false).text());
    }

    @Test void disabledReasonsTakePriorityButDoNotRepeatAnInlineExplanation() {
        Component reason = Component.text("Enter a name first.");
        var scene = MenuScene.screen("tip").content(Ui.button(Component.text("Apply")).key("apply")
                .tooltip(Component.text("Save changes.")).disabled(reason).build()).build();
        assertEquals(reason, MenuTooltipContent.resolve(entry(scene, "apply"), entry(scene, "apply").label(), _ -> false).text());
        assertNull(MenuTooltipContent.resolve(entry(scene, "apply"), entry(scene, "apply").label(), visible -> MenuTooltipContent.same(visible, reason)));
        var full = Component.text("A long destination name that cannot fit on one line");
        var source = MenuScene.screen("disabled").viewport(new UiSize(120,21)).content(Ui.button(full).key("apply")
                .singleLine().disabled(reason).build()).build();
        var measured = source.measured(SceneGeometryTestSupport.naturalFrame(source,1));
        var apply = entry(measured,"apply");
        assertEquals(full.append(Component.newline()).append(reason),MenuTooltipContent.resolve(apply,apply.label(),_ -> false).text());
        assertEquals(full,MenuTooltipContent.resolve(apply,apply.label(),_ -> true).text());
    }
    @Test void aFrozenControlWithNoExplanationStillExposesItsTruncatedContentWithoutAnEmptyTooltip() {
        Component full = Component.text("A long selected song name");
        var source = MenuScene.screen("tip").viewport(new UiSize(80,55)).content(Ui.column()
                .child(Ui.button(full).key("frozen").summaryLines(1).disabled(Component.empty()).build())
                .child(Ui.button(Component.text("☑")).key("marker").disabled(Component.empty()).build()).build()).build();
        var scene = source.measured(SceneGeometryTestSupport.naturalFrame(source,1));
        assertFalse(entry(scene, "frozen").enabled());
        assertNull(entry(scene, "frozen").trigger(org.encinet.mik.shared.menu.FloatingMenuInteraction.PRIMARY));
        assertEquals(full, MenuTooltipContent.resolve(entry(scene, "frozen"), entry(scene, "frozen").label(), _ -> false).text());
        assertNull(MenuTooltipContent.resolve(entry(scene, "marker"), entry(scene, "marker").label(), _ -> false));
    }

    @Test void widthTruncationUsesTheRenderedCaptionAndKeepsExplicitHelp() {
        String raw = "A song name that does not fit in this input";
        var full = Component.text(raw);
        var help = Component.text("Use the wheel to adjust.");
        var scene = MenuScene.screen("tip").content(Ui.column()
                .child(Ui.textField(raw).key("name").build())
                .child(Ui.button(full).key("volume").singleLine().tooltip(help).build()).build()).build();
        var frame = SceneGeometryTestSupport.measure(scene, 1);
        var preview = element(frame, "preview");
        assertNotEquals(full, preview.node().content().label());
        assertEquals(full, MenuTooltipContent.resolve(entry(scene, "preview"), preview.node().content().label(), _ -> false).text());
        assertNull(MenuTooltipContent.resolve(entry(scene, "preview"), full, _ -> false));
        assertEquals(help, MenuTooltipContent.resolve(entry(scene, "volume"), Component.text("A song…"), _ -> false).text());
    }

    @Test void completeLabelsKeepExplicitHelpAndSummariesRetainBothTheFullTextAndHelp() {
        Component full = Component.text("Long field value with information");
        Component help = Component.text("Click to inspect the track.");
        var scene = MenuScene.screen("tip").content(Ui.column()
                .child(Ui.text(Component.text("FLAC")).summaryLines(1).key("short").build())
                .child(Ui.button(full).tooltip(help).summaryLines(1).key("before").build())
                .child(Ui.button(full).summaryLines(1).tooltip(help).key("after").build()).build()).build();
        assertNull(entry(scene, "short").tooltip());
        assertEquals(help, entry(scene, "before").tooltip().text());
        assertEquals(help, entry(scene, "after").tooltip().text());
        var small = scene.toBuilder().viewport(new UiSize(80,80)).build();
        var measured = small.measured(SceneGeometryTestSupport.naturalFrame(small,1));
        var completeHelp = full.append(Component.newline()).append(help);
        assertEquals(completeHelp,entry(measured,"before").tooltip().text());
        assertEquals(completeHelp,entry(measured,"after").tooltip().text());
    }
}
