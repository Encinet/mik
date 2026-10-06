package org.encinet.mik.module.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingMenuDefinitionTest {
    @Test
    void explicitFramingDecorationsAreValidatedAndImmutable() {
        var builder = FloatingMenuDefinition.screen("wall").framingDecorations("surface");
        assertThrows(IllegalArgumentException.class, builder::build);
        builder.textDecoration("surface", FloatingMenuPoint.ORIGIN, Component.text("Surface"));
        var definition = builder.build();
        assertTrue(definition.framingDecorations().contains("surface"));
        assertEquals(definition.framingDecorations(), definition.withLifecycle(FloatingMenuLifecycle.NONE).framingDecorations());
        assertEquals(definition.framingDecorations(), FloatingMenuDefinition.builder()
                .textDecoration("surface", FloatingMenuPoint.ORIGIN, Component.text("Surface"))
                .framingDecorations("surface").build().identifiedBy("owned").framingDecorations());
        assertThrows(UnsupportedOperationException.class, () -> definition.framingDecorations().add("body"));
        builder.framingDecorations();
        assertTrue(builder.build().framingDecorations().isEmpty());
        assertEquals(1, definition.framingDecorations().size());
    }


    @Test
    void buildsDefinitionWithStableDefaults() {
        FloatingMenuDefinition.Builder builder = FloatingMenuDefinition.builder(Component.text("Root"));
        builder.item("stone", new TestItemStack(), Component.text("Stone"));
        FloatingMenuDefinition definition = builder.build();

        assertEquals(FloatingMenuAnimation.DEFAULT, definition.animation());
        assertEquals(1, definition.entries().size());
        assertNotNull(definition.entries().get("stone"));
    }

    @Test
    void applicationScreenFactoryOwnsIdentityAndSharedAppearance() {
        FloatingMenuDefinition definition = FloatingMenuDefinition.screen(
                        "settings", Component.text("Settings"))
                .build();

        assertEquals("settings", definition.screenId());
        assertEquals(FloatingMenuAppearance.SPATIAL, definition.appearance());
        assertTrue(definition.titleVisible());
    }

    @Test
    void sharedPaginationOwnsNodesAndScrollActions() {
        AtomicInteger selectedPage = new AtomicInteger(-1);
        FloatingMenuDefinition definition = FloatingMenuDefinition.screen("browser")
                .pagination("navigation", new FloatingMenuPage(1, 8, 3),
                        selectedPage::set)
                .build();

        assertEquals("navigation", definition.entries().get("page:previous").region());
        assertEquals("navigation", definition.entries().get("page").region());
        assertEquals("navigation", definition.entries().get("page:next").region());
        definition.triggers().get(FloatingMenuInteraction.SCROLL_UP)
                .execute(null, null, FloatingMenuInteraction.SCROLL_UP);
        assertEquals(0, selectedPage.get());
        definition.triggers().get(FloatingMenuInteraction.SCROLL_DOWN)
                .execute(null, null, FloatingMenuInteraction.SCROLL_DOWN);
        assertEquals(2, selectedPage.get());
    }

    @Test
    void validatesLayoutAndAnimationContracts() {
        FloatingMenuDefinition.Builder builder = FloatingMenuDefinition.builder(Component.text("spatial"));
        builder.item("same", new TestItemStack(), Component.text("Same"));
        assertThrows(IllegalArgumentException.class,
                () -> builder.item("same", new TestItemStack(), Component.text("Duplicate")));
        assertThrows(IllegalArgumentException.class,
                () -> new FloatingMenuAnimation(0, 5, 4, 0.1, 0.1, 0.01, 0.1,
                        FloatingMenuEasing.LINEAR, FloatingMenuEasing.LINEAR));
    }

    @Test
    void easingFunctionsKeepLifecycleEndpointsStable() {
        for (FloatingMenuEasing easing : FloatingMenuEasing.values()) {
            assertEquals(0.0, easing.apply(0.0), 0.0001);
            assertEquals(1.0, easing.apply(1.0), 0.0001);
        }
        assertEquals(0.5, FloatingMenuEasing.LINEAR.apply(0.5), 0.0001);
    }

    @Test
    void globalSemanticTriggersAreDeclaredWithoutInteractionSwitches() {
        FloatingMenuDefinition.Builder builder = FloatingMenuDefinition.builder(Component.text("menu"));
        builder.onScroll((player, menu, interaction) -> { },
                (player, menu, interaction) -> { });

        FloatingMenuDefinition definition = builder.build();
        assertNotNull(definition.triggers().get(FloatingMenuInteraction.SCROLL_UP));
        assertNotNull(definition.triggers().get(FloatingMenuInteraction.SCROLL_DOWN));
    }

    @Test
    void hotkeyIsAFirstClassNodeInteraction() {
        AtomicInteger activations = new AtomicInteger();
        FloatingMenuDefinition.Builder builder = FloatingMenuDefinition.builder();
        builder.control("shortcut", Component.text("Shortcut"))
                .hotkey((player, menu) -> activations.incrementAndGet());

        FloatingMenuDefinition.Entry shortcut = builder.build().entries().get("shortcut");
        assertEquals(List.of(
                        FloatingMenuInteraction.PRIMARY,
                        FloatingMenuInteraction.SECONDARY,
                        FloatingMenuInteraction.HOTKEY,
                        FloatingMenuInteraction.SCROLL_UP,
                        FloatingMenuInteraction.SCROLL_DOWN),
                List.of(FloatingMenuInteraction.values()));
        shortcut.trigger(FloatingMenuInteraction.HOTKEY)
                .execute(null, null, FloatingMenuInteraction.HOTKEY);
        assertEquals(1, activations.get());
    }

    @Test
    void nodeFactoriesPreserveSemanticRolesWithoutReadingItemMetadata() {
        FloatingMenuDefinition.Builder builder = FloatingMenuDefinition.builder();
        builder.information("status", Component.text("Ready"));
        builder.control("play", Component.text("Play")).primary((player, menu) -> { });
        builder.navigation("back", Component.text("Back"));
        builder.item("disc", new TestItemStack(), Component.text("Disc"));

        FloatingMenuDefinition definition = builder.build();
        FloatingMenuDefinition.Entry status = definition.entries().get("status");
        assertEquals(FloatingMenuNodeRole.INFORMATION, status.role());
        assertEquals(FloatingMenuElementStyle.TEXT, status.style());
        assertFalse(status.interactive());
        assertNull(status.item());

        FloatingMenuDefinition.Entry control = definition.entries().get("play");
        assertEquals(FloatingMenuNodeRole.CONTROL, control.role());
        assertTrue(control.interactive());
        assertNotNull(control.trigger(FloatingMenuInteraction.PRIMARY));
        assertEquals(FloatingMenuDecoration.Alignment.CENTER, control.alignment());

        assertEquals(FloatingMenuNodeRole.NAVIGATION,
                definition.entries().get("back").role());
        FloatingMenuDefinition.Entry item = definition.entries().get("disc");
        assertEquals(FloatingMenuNodeRole.ITEM, item.role());
        assertEquals(Component.text("Disc"), item.label());
    }

    @Test
    void passiveInformationAndInteractiveControlsCannotBeConfused() {
        FloatingMenuDefinition.Builder builder = FloatingMenuDefinition.builder();
        FloatingMenuDefinition.NodeBuilder information =
                builder.information("status", Component.text("Ready"));
        FloatingMenuDefinition.NodeBuilder control =
                builder.control("action", Component.text("Action"));

        assertThrows(IllegalStateException.class,
                () -> information.primary((player, menu) -> { }));
        assertThrows(IllegalStateException.class, control::passive);
        assertThrows(IllegalStateException.class, () -> information.selected(true));
    }

    @Test
    void persistentSelectionIsSemanticNodeState() {
        FloatingMenuDefinition.Builder builder = FloatingMenuDefinition.builder();
        builder.control("off", Component.text("Off"))
                .selected(true)
                .primary((player, menu) -> { });
        builder.control("three-seconds", Component.text("3 seconds"))
                .selected(false)
                .primary((player, menu) -> { });

        FloatingMenuDefinition definition = builder.build();
        assertTrue(definition.entries().get("off").selected());
        assertFalse(definition.entries().get("three-seconds").selected());
        assertNotEquals(
                definition.appearance().elementBackground(FloatingMenuElementState.NORMAL),
                definition.appearance().elementBackground(FloatingMenuElementState.SELECTED));
    }

    @Test
    void nodeTextAlignmentIsPartOfTheSemanticDefinition() {
        FloatingMenuDefinition.Builder builder = FloatingMenuDefinition.builder();
        builder.information("profile", Component.text("one\ntwo"))
                .alignment(FloatingMenuDecoration.Alignment.RIGHT);

        assertEquals(FloatingMenuDecoration.Alignment.RIGHT,
                builder.build().entries().get("profile").alignment());
    }

    @Test
    void essentialRingControlsCanRemainAccessibleWhenCardsAreOccluded() {
        FloatingMenuDefinition.Builder builder = FloatingMenuDefinition.screen("ring")
                .aroundViewer();
        builder.information("reader", Component.text("Current notice"))
                .keepAccessible();
        builder.control("side-card", Component.text("Another notice"))
                .primary((player, menu) -> { });

        FloatingMenuDefinition definition = builder.build();
        assertTrue(definition.entries().get("reader").keepAccessible());
        assertFalse(definition.entries().get("side-card").keepAccessible());
    }

    @Test
    void framingAndTextWidthAreDeclarativeScenePolicies() {
        FloatingMenuDefinition.Builder builder = FloatingMenuDefinition.builder()
                .framing(FloatingMenuFraming.PANORAMIC);
        builder.control("track", Component.text("Song\nArtist"))
                .textWidth(FloatingMenuTextWidth.WIDE);

        FloatingMenuDefinition definition = builder.build();
        assertEquals(FloatingMenuFraming.PANORAMIC, definition.framing());
        assertEquals(FloatingMenuTextWidth.WIDE,
                definition.entries().get("track").textWidth());
    }

    @Test
    void decorationsAreSeparateFromInteractiveEntries() {
        FloatingMenuDefinition.Builder builder = FloatingMenuDefinition.builder(Component.text("scene"));
        builder.textDecoration("context", FloatingMenuPose.oriented(
                        new FloatingMenuPoint(-1, 0, 0.2), -12.0, 3.0),
                Component.text("ambient information"));
        builder.item("action", new TestItemStack(), Component.text("Action"));

        FloatingMenuDefinition definition = builder.build();
        assertEquals(1, definition.entries().size());
        assertEquals(1, definition.decorations().size());
        assertNotNull(definition.decorations().get("context"));
        FloatingMenuPlacement.Local placement = (FloatingMenuPlacement.Local)
                definition.decorations().get("context").placement();
        assertEquals(-12.0, placement.pose().yawDegrees());
        assertEquals(3.0, placement.pose().pitchDegrees());
        assertThrows(IllegalArgumentException.class,
                () -> builder.item("context", new TestItemStack(), Component.text("Duplicate")));
    }

    @Test
    void untitledScenesAndTextSurfacesAreExplicitPolicies() {
        FloatingMenuDefinition definition = FloatingMenuDefinition.builder()
                .appearance(FloatingMenuAppearance.SPATIAL)
                .build();

        assertFalse(definition.titleVisible());
        assertEquals(Component.empty(), definition.title());
        assertTrue((definition.appearance().elementBackground(
                FloatingMenuElementState.NORMAL) >>> 24) >= 0xA0);
        assertTrue((definition.appearance().titleBackground() >>> 24) >= 0xA0);
        assertNotEquals(FloatingMenuAppearance.TRANSPARENT,
                definition.appearance().elementBackground(FloatingMenuElementState.HOVERED));
        assertEquals(FloatingMenuAppearance.TRANSPARENT,
                FloatingMenuAppearance.NO_BACKGROUNDS.elementBackground(
                        FloatingMenuElementState.HOVERED));
    }

    @Test
    void liveRefreshCanSuppressUnchangedEntityUpdatesWithARevision() {
        FloatingMenuDefinition definition = FloatingMenuDefinition.builder()
                .refreshWhenChanged(5, ignored -> "revision",
                        (player, handle) -> { })
                .build();

        assertTrue(definition.refresh().enabled());
        assertEquals(5, definition.refresh().intervalTicks());
        assertEquals("revision", definition.refresh().revision(null));
        assertThrows(IllegalArgumentException.class, () ->
                FloatingMenuRefresh.every(0, (player, handle) -> { }));
    }

    @Test
    void worldDecorationsOwnAnImmutableAbsoluteLocation() {
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, args) ->
                        switch (method.getName()) {
                            case "equals" -> proxy == args[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "getName" -> "test";
                            default -> TestItemStack.defaultValue(method.getReturnType());
                        });
        Location source = new Location(world, 4.5, 65.2, -3.5);
        FloatingMenuDefinition definition = FloatingMenuDefinition.builder()
                .worldItemDecoration("world-disc", source, 20.0, 90.0,
                        new TestItemStack(), 0.8F, FloatingMenuDecoration.Motion.SPIN)
                .build();
        source.setX(99.0);

        FloatingMenuPlacement.World placement = (FloatingMenuPlacement.World)
                definition.decorations().get("world-disc").placement();
        assertEquals(4.5, placement.location().getX());
        Location copy = placement.location();
        copy.setY(0.0);
        assertEquals(65.2, placement.location().getY());
        assertEquals(90.0, placement.pitchDegrees());
    }

    /** Avoids bootstrapping Paper registries just to exercise the immutable builder. */
    private static final class TestItemStack extends ItemStack {
        private TestItemStack() { super(); }

        @Override public TestItemStack clone() { return this; }
        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) return null;
            if (type == boolean.class) return false;
            if (type == byte.class) return (byte) 0;
            if (type == short.class) return (short) 0;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == float.class) return 0F;
            if (type == double.class) return 0D;
            if (type == char.class) return '\0';
            throw new AssertionError(type);
        }
    }
}
