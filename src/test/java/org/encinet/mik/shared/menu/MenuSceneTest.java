package org.encinet.mik.shared.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.encinet.mik.shared.menu.ui.UiRole;
import org.encinet.mik.shared.menu.ui.Ui;
import org.encinet.mik.shared.menu.ui.UiSize;
import org.encinet.mik.shared.menu.ui.UiTemplates;

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

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.entry;

class MenuSceneTest {
    @Test void framingAResolvedSceneReindexesItsControlsAndRetainsItsActionsAndPolicies() {
        var activations = new AtomicInteger();
        var revisions = new AtomicInteger();
        var transitions = new AtomicInteger();
        var builder = MenuScene.screen("feature", Component.text("Feature"))
                .content(UiTemplates.window("body", UiTemplates.list("choices"))).stableAnchor()
                .lifecycle((_, _, _, _, _) -> transitions.incrementAndGet())
                .refreshWhenChanged(20, _ -> revisions.get(), (_, _) -> revisions.incrementAndGet());
        builder.control("toggle", Component.text("Enabled")).region("choices").selected(true).keepAccessible()
                .tooltip(Component.text("More information")).primary((_, _) -> activations.incrementAndGet());
        builder.control("locked", Component.text("Locked")).region("choices").disabled(Component.text("Unavailable"));
        var original = builder.build();
        var framed = original.toBuilder().screen("host").withoutTitle().viewport(new UiSize(960, 2048))
                .content(UiTemplates.split(Ui.text(Component.text("Navigation")).key("rail").build(), original.content())).build();

        assertTrue(original.titleVisible());
        assertFalse(framed.titleVisible());
        assertNotEquals(entry(original, "toggle").id(), entry(framed, "toggle").id());
        var toggle = entry(framed, "toggle");
        assertTrue(toggle.selected());
        assertTrue(toggle.keepAccessible());
        assertEquals(entry(original, "toggle").tooltip(), toggle.tooltip());
        assertEquals(entry(original, "locked").disabledReason(), entry(framed, "locked").disabledReason());
        toggle.trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
        framed.refresh().action().execute(null, null);
        framed.lifecycle().changed(null, null, FloatingMenuState.OPENING, FloatingMenuState.ACTIVE, null);
        assertEquals(1, activations.get());
        assertEquals(1, revisions.get());
        assertEquals(1, transitions.get());
        assertEquals(original.anchorMode(), framed.anchorMode());
    }

    @Test void choiceSemanticsDoNotChangeWhenSelectionIsClearedAndReadOnlyHelpOwnsNoAction() {
        var builder = MenuScene.builder();
        builder.control("unselected", Component.text("Choice")).selected(false);
        builder.information("help", Component.text("About")).tooltip(Component.text("Details"));
        var scene = builder.build();
        assertEquals(UiRole.CHOICE, entry(scene, "unselected").role());
        assertFalse(entry(scene, "unselected").selected());
        assertEquals(UiRole.TEXT, entry(scene, "help").role());
        assertEquals(org.encinet.mik.shared.menu.ui.UiPointerBehavior.INSPECT, entry(scene, "help").pointerBehavior());
        assertFalse(entry(scene, "help").interactive());
        assertTrue(entry(scene, "help").triggers().isEmpty());
    }
    @Test
    void explicitFramingDecorationsAreValidatedAndImmutable() {
        var builder = MenuScene.screen("wall").framingDecorations("surface");
        assertThrows(IllegalArgumentException.class, builder::build);
        builder.textDecoration("surface", FloatingMenuPoint.ORIGIN, Component.text("Surface"));
        var definition = builder.build();
        assertTrue(definition.framingDecorations().contains("surface"));
        assertEquals(definition.framingDecorations(), definition.withLifecycle(FloatingMenuLifecycle.NONE).framingDecorations());
        assertEquals(definition.framingDecorations(), MenuScene.builder()
                .textDecoration("surface", FloatingMenuPoint.ORIGIN, Component.text("Surface"))
                .framingDecorations("surface").build().identifiedBy("owned").framingDecorations());
        assertThrows(UnsupportedOperationException.class, () -> definition.framingDecorations().add("body"));
        builder.framingDecorations();
        assertTrue(builder.build().framingDecorations().isEmpty());
        assertEquals(1, definition.framingDecorations().size());
    }


    @Test
    void buildsDefinitionWithStableDefaults() {
        MenuScene.Builder builder = MenuScene.builder(Component.text("Root"));
        builder.item("stone", new TestItemStack(), Component.text("Stone"));
        MenuScene definition = builder.build();

        assertEquals(FloatingMenuAnimation.DEFAULT, definition.animation());
        assertEquals(1, definition.entries().size());
        assertNotNull(entry(definition, "stone"));
    }

    @Test
    void applicationScreenFactoryOwnsIdentityAndSharedAppearance() {
        MenuScene definition = MenuScene.screen(
                        "settings", Component.text("Settings"))
                .build();

        assertEquals("settings", definition.screenId());
        assertEquals(FloatingMenuAppearance.DEFAULT, definition.appearance());
        assertTrue(definition.titleVisible());
    }

    @Test
    void sharedPaginationOwnsNodesAndScrollActions() {
        AtomicInteger selectedPage = new AtomicInteger(-1);
        MenuScene definition = MenuScene.screen("browser")
                .pagination("navigation", new FloatingMenuPage(1, 8, 3),
                        selectedPage::set)
                .build();

        assertNotNull(entry(definition, "page:previous"));
        assertNotNull(entry(definition, "page"));
        assertNotNull(entry(definition, "page:next"));
        definition.triggers().get(FloatingMenuInteraction.SCROLL_UP)
                .execute(null, null, FloatingMenuInteraction.SCROLL_UP);
        assertEquals(0, selectedPage.get());
        definition.triggers().get(FloatingMenuInteraction.SCROLL_DOWN)
                .execute(null, null, FloatingMenuInteraction.SCROLL_DOWN);
        assertEquals(2, selectedPage.get());
    }

    @Test
    void wheelDirectionsStayCapturedAtFirstLastEmptyAndSinglePagesWithoutChangingThePage() {
        for (FloatingMenuPage page : List.of(new FloatingMenuPage(0, 0, 6), new FloatingMenuPage(0, 1, 6),
                new FloatingMenuPage(0, 13, 6), new FloatingMenuPage(2, 13, 6))) {
            AtomicInteger selected = new AtomicInteger(-1);
            MenuScene definition = MenuScene.screen("browser").pagination("navigation", page, selected::set).build();
            for (FloatingMenuInteraction direction : List.of(FloatingMenuInteraction.SCROLL_UP, FloatingMenuInteraction.SCROLL_DOWN)) {
                selected.set(-1);
                var action = definition.triggers().get(direction);
                assertNotNull(action, "both directions must consume wheel input even at an endpoint");
                action.execute(null, null, direction);
                int expected = direction == FloatingMenuInteraction.SCROLL_UP
                        ? (page.hasPrevious() ? page.previous().index() : -1) : (page.hasNext() ? page.next().index() : -1);
                assertEquals(expected, selected.get());
            }
            assertEquals(page.hasPrevious(), entry(definition, "page:previous") != null);
            assertEquals(page.hasNext(), entry(definition, "page:next") != null);
        }
    }

    @Test
    void validatesLayoutAndAnimationContracts() {
        MenuScene.Builder builder = MenuScene.builder(Component.text("spatial"));
        builder.item("same", new TestItemStack(), Component.text("Same"));
        assertThrows(IllegalArgumentException.class,
                () -> builder.item("same", new TestItemStack(), Component.text("Duplicate")));
        assertThrows(IllegalArgumentException.class,
                () -> new FloatingMenuAnimation(0, 5, 4, 0.12, 0.1, 0.1, 0.01, 0.1,
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
        MenuScene.Builder builder = MenuScene.builder(Component.text("menu"));
        builder.onScroll((_, _, _) -> { },
                (_, _, _) -> { });

        MenuScene definition = builder.build();
        assertNotNull(definition.triggers().get(FloatingMenuInteraction.SCROLL_UP));
        assertNotNull(definition.triggers().get(FloatingMenuInteraction.SCROLL_DOWN));
    }

    @Test
    void hotkeyIsAFirstClassNodeInteraction() {
        AtomicInteger activations = new AtomicInteger();
        MenuScene.Builder builder = MenuScene.builder();
        builder.control("shortcut", Component.text("Shortcut"))
                .hotkey((_, _) -> activations.incrementAndGet());

        MenuScene.Entry shortcut = entry(builder.build(), "shortcut");
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
        MenuScene.Builder builder = MenuScene.builder();
        builder.information("status", Component.text("Ready"));
        builder.control("play", Component.text("Play")).primary((_, _) -> { });
        builder.navigation("back", Component.text("Back"));
        builder.item("disc", new TestItemStack(), Component.text("Disc"));

        MenuScene definition = builder.build();
        MenuScene.Entry status = entry(definition, "status");
        assertEquals(UiRole.TEXT, status.role());
        assertEquals(FloatingMenuElementStyle.TEXT, status.style());
        assertFalse(status.interactive());
        assertNull(status.item());

        MenuScene.Entry control = entry(definition, "play");
        assertEquals(UiRole.ACTION, control.role());
        assertTrue(control.interactive());
        assertNotNull(control.trigger(FloatingMenuInteraction.PRIMARY));
        assertEquals(FloatingMenuDecoration.Alignment.CENTER, control.alignment());

        assertEquals(UiRole.NAVIGATION,
                entry(definition, "back").role());
        MenuScene.Entry item = entry(definition, "disc");
        assertEquals(UiRole.ACTION, item.role());
        assertEquals(Component.text("Disc"), item.label());
    }

    @Test
    void passiveInformationAndInteractiveControlsCannotBeConfused() {
        MenuScene.Builder builder = MenuScene.builder();
        MenuScene.NodeBuilder information =
                builder.information("status", Component.text("Ready"));
        MenuScene.NodeBuilder control =
                builder.control("action", Component.text("Action"));

        assertThrows(IllegalStateException.class,
                () -> information.primary((_, _) -> { }));
        assertThrows(IllegalStateException.class, control::passive);
        assertThrows(IllegalStateException.class, () -> information.selected(true));
    }

    @Test
    void persistentSelectionIsSemanticNodeState() {
        MenuScene.Builder builder = MenuScene.builder();
        builder.control("off", Component.text("Off"))
                .selected(true)
                .primary((_, _) -> { });
        builder.control("three-seconds", Component.text("3 seconds"))
                .selected(false)
                .primary((_, _) -> { });

        MenuScene definition = builder.build();
        assertTrue(entry(definition, "off").selected());
        assertFalse(entry(definition, "three-seconds").selected());
        assertNotEquals(
                definition.appearance().elementBackground(FloatingMenuElementState.NORMAL),
                definition.appearance().elementBackground(FloatingMenuElementState.SELECTED));
    }

    @Test
    void nodeTextAlignmentIsPartOfTheSemanticDefinition() {
        MenuScene.Builder builder = MenuScene.builder();
        builder.information("profile", Component.text("one\ntwo"))
                .alignment(FloatingMenuDecoration.Alignment.RIGHT);

        assertEquals(FloatingMenuDecoration.Alignment.RIGHT,
                entry(builder.build(), "profile").alignment());
    }

    @Test
    void essentialRingControlsCanRemainAccessibleWhenCardsAreOccluded() {
        MenuScene.Builder builder = MenuScene.screen("ring")
                .aroundViewer();
        builder.information("reader", Component.text("Current notice"))
                .keepAccessible();
        builder.control("side-card", Component.text("Another notice"))
                .primary((_, _) -> { });

        MenuScene definition = builder.build();
        assertTrue(entry(definition, "reader").keepAccessible());
        assertFalse(entry(definition, "side-card").keepAccessible());
    }

    @Test
    void framingAndTextWidthAreDeclarativeScenePolicies() {
        MenuScene.Builder builder = MenuScene.builder()
                .framing(FloatingMenuFraming.PANORAMIC);
        builder.control("track", Component.text("Song\nArtist"))
                .textWidth(FloatingMenuTextWidth.WIDE);

        MenuScene definition = builder.build();
        assertEquals(FloatingMenuFraming.PANORAMIC, definition.framing());
        assertEquals(FloatingMenuTextWidth.WIDE.resolve(128),
                UiSceneContent.elements(definition.content()).values().stream()
                        .filter(node -> node.key().value().equals("track")).findFirst().orElseThrow().textWidthPixels());
    }

    @Test
    void decorationsAreSeparateFromInteractiveEntries() {
        MenuScene.Builder builder = MenuScene.builder(Component.text("scene"));
        builder.textDecoration("context", FloatingMenuPose.oriented(
                        new FloatingMenuPoint(-1, 0, 0.2), -12.0, 3.0),
                Component.text("ambient information"));
        builder.item("action", new TestItemStack(), Component.text("Action"));

        MenuScene definition = builder.build();
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
        MenuScene definition = MenuScene.builder()
                .appearance(FloatingMenuAppearance.DEFAULT)
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
        MenuScene definition = MenuScene.builder()
                .refreshWhenChanged(5, _ -> "revision",
                        (_, _) -> { })
                .build();

        assertTrue(definition.refresh().enabled());
        assertEquals(5, definition.refresh().intervalTicks());
        assertEquals("revision", definition.refresh().revision(null));
        assertThrows(IllegalArgumentException.class, () ->
                FloatingMenuRefresh.every(0, (_, _) -> { }));
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
        MenuScene definition = MenuScene.builder()
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
