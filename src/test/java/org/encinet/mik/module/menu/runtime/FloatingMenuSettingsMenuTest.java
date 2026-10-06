package org.encinet.mik.module.menu.runtime;

import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuElementStyle;
import org.encinet.mik.module.menu.FloatingMenuInteraction;
import org.encinet.mik.module.menu.FloatingMenuLayout;
import org.encinet.mik.module.menu.FloatingMenuPreferences;
import org.encinet.mik.module.menu.FloatingMenuScale;
import org.encinet.mik.module.menu.FloatingMenuTextScale;
import org.encinet.mik.module.menu.FloatingMenuSpatialFrame;
import org.encinet.mik.module.menu.FloatingMenuFieldOfView;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingMenuSettingsMenuTest {
    private final AtomicReference<FloatingMenuScale> selectedLayout = new AtomicReference<>();
    private final AtomicReference<FloatingMenuTextScale> selectedText = new AtomicReference<>();
    private boolean reset;

    @Test
    void settingsExposeBothFullChoiceSetsWithoutPagingOrAmbiguousGlobalScrolling() {
        var definition = definition(FloatingMenuPreferences.DEFAULT, true);
        assertEquals(FloatingMenuSpatialFrame.FRONT_ARC, definition.spatialFrame());
        assertEquals(7, definition.entries().values().stream().filter(entry -> entry.region().equals("layout")).count());
        assertEquals(9, definition.entries().values().stream().filter(entry -> entry.region().equals("text")).count());
        assertTrue(definition.triggers().isEmpty());
        assertTrue(definition.entries().values().stream().allMatch(entry -> entry.style() == FloatingMenuElementStyle.TEXT));
        assertEquals(2, definition.entries().values().stream().filter(FloatingMenuDefinition.Entry::selected).count());
        assertFalse(definition.entries().containsKey("native-hint"));
        assertTrue(definition.entries().containsKey("preview"));
        assertTrue(definition.entries().containsKey("field-of-view"));
    }

    @Test
    void directChoicesAndWheelInputOnlyChangeThePointedAtDimension() {
        var definition = definition(FloatingMenuPreferences.DEFAULT, true);
        invoke(definition, "scale:minimum", FloatingMenuInteraction.PRIMARY);
        assertEquals(FloatingMenuScale.MINIMUM, selectedLayout.get());
        assertNull(selectedText.get());
        selectedLayout.set(null);
        invoke(definition, "text-scale:180", FloatingMenuInteraction.PRIMARY);
        assertEquals(FloatingMenuTextScale.MAXIMUM, selectedText.get());
        assertNull(selectedLayout.get());
        selectedText.set(null);
        invoke(definition, "scale:normal", FloatingMenuInteraction.SCROLL_UP);
        assertEquals(FloatingMenuScale.LARGE, selectedLayout.get());
        assertNull(selectedText.get());
        selectedLayout.set(null);
        invoke(definition, "text-scale:100", FloatingMenuInteraction.SCROLL_DOWN);
        assertEquals(FloatingMenuTextScale.SMALL, selectedText.get());
        assertNull(selectedLayout.get());
        invoke(definition, "reset-scale", FloatingMenuInteraction.PRIMARY);
        assertTrue(reset);
    }

    @Test
    void wheelInputStopsAtEitherLimit() {
        var maximum = definition(new FloatingMenuPreferences(FloatingMenuScale.MAXIMUM, FloatingMenuTextScale.MAXIMUM), true);
        invoke(maximum, "scale:maximum", FloatingMenuInteraction.SCROLL_UP);
        invoke(maximum, "text-scale:180", FloatingMenuInteraction.SCROLL_UP);
        assertEquals(FloatingMenuScale.MAXIMUM, selectedLayout.get());
        assertEquals(FloatingMenuTextScale.MAXIMUM, selectedText.get());
        var minimum = definition(new FloatingMenuPreferences(FloatingMenuScale.MINIMUM, FloatingMenuTextScale.MINIMUM), true);
        invoke(minimum, "scale:minimum", FloatingMenuInteraction.SCROLL_DOWN);
        invoke(minimum, "text-scale:70", FloatingMenuInteraction.SCROLL_DOWN);
        assertEquals(FloatingMenuScale.MINIMUM, selectedLayout.get());
        assertEquals(FloatingMenuTextScale.MINIMUM, selectedText.get());
    }

    @Test
    void everyCombinationKeepsMenuChoicesLeftAndTextChoicesRight() {
        for (FloatingMenuScale layout : FloatingMenuScale.values()) {
            for (FloatingMenuTextScale text : FloatingMenuTextScale.values()) {
                var preferences = new FloatingMenuPreferences(layout, text);
                var definition = definition(preferences, true);
                List<FloatingMenuLayout.Node> nodes = new ArrayList<>();
                for (var entry : definition.entries().values()) {
                    var measurement = FloatingMenuNodeSizing.measure(entry, preferences.typographyFactor());
                    nodes.add(new FloatingMenuLayout.Node(entry.id(), entry.style(), entry.role(), entry.region(), measurement.footprint()));
                }
                for (int index = 0; index < nodes.size(); index++) {
                    var node = nodes.get(index);
                    var pose = definition.layout().pose(new FloatingMenuLayout.Context(index, nodes));
                    if (node.region().equals("layout")) assertTrue(pose.right() < 0);
                    if (node.region().equals("text")) assertTrue(pose.right() > 0);
                    assertTrue(Double.isFinite(pose.up()));
                    assertTrue(Math.abs(pose.yawDegrees()) < 80);
                }
            }
        }
    }

    @Test
    void nativeFormsKeepGroupsClearAndChoicesDirectWithoutWheelActionDialogs() {
        var definition = definition(FloatingMenuPreferences.DEFAULT, false);
        assertTrue(definition.entries().containsKey("native-hint"));
        assertFalse(definition.entries().containsKey("field-of-view"));
        var translated = new BedrockMenuTranslator().translate(definition,
                new BedrockMenuTranslator.ActionLabels("Primary", "Secondary", "Shortcut", "Up", "Down"));
        assertEquals(18, translated.options().size());
        for (var option : translated.options()) {
            assertEquals(List.of(FloatingMenuInteraction.PRIMARY), option.interactions());
            if (option.elementId().startsWith("scale:")) assertTrue(option.label().contains(Message.INTERFACE_SCALE_LAYOUT_TITLE.key()));
            if (option.elementId().startsWith("text-scale:")) assertTrue(option.label().contains(Message.INTERFACE_SCALE_TEXT_TITLE.key()));
        }
        assertTrue(translated.content().contains(Message.INTERFACE_SCALE_NATIVE_HINT.key()));
    }

    @Test
    void fovControlsOfferPresetsExactInputClampedStepsAndAFreshReturn() {
        var selected = new AtomicReference<FloatingMenuFieldOfView>();
        var custom = new AtomicReference<Boolean>(false);
        var returned = new AtomicReference<Boolean>(false);
        var definition = FloatingMenuSettingsMenu.fieldOfViewDefinition(new FloatingMenuFieldOfView(73), Message::key,
                (player, value) -> selected.set(value), player -> custom.set(true), player -> returned.set(true));
        assertEquals(9, definition.entries().values().stream().filter(entry -> entry.region().equals("presets")).count());
        assertEquals(0, definition.entries().values().stream().filter(FloatingMenuDefinition.Entry::selected).count());
        invoke(definition, "fov:30", FloatingMenuInteraction.PRIMARY);
        assertEquals(30, selected.get().degrees());
        invoke(definition, "fov-increase", FloatingMenuInteraction.PRIMARY);
        assertEquals(78, selected.get().degrees());
        invoke(definition, "fov-decrease", FloatingMenuInteraction.PRIMARY);
        assertEquals(68, selected.get().degrees());
        definition.triggers().get(FloatingMenuInteraction.SCROLL_UP).execute(null, null, FloatingMenuInteraction.SCROLL_UP);
        assertEquals(78, selected.get().degrees());
        invoke(definition, "fov-custom", FloatingMenuInteraction.PRIMARY);
        assertTrue(custom.get());
        invoke(definition, "back", FloatingMenuInteraction.PRIMARY);
        assertTrue(returned.get());
    }

    private FloatingMenuDefinition definition(FloatingMenuPreferences preferences, boolean spatial) {
        return FloatingMenuSettingsMenu.definition(preferences, spatial, Message::key,
                (player, selected) -> selectedLayout.set(selected),
                (player, selected) -> selectedText.set(selected), player -> { }, player -> reset = true);
    }

    private void invoke(FloatingMenuDefinition definition, String id, FloatingMenuInteraction interaction) {
        definition.entries().get(id).triggers().get(interaction).execute(null, null, interaction);
    }
}
