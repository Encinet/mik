package org.encinet.mik.shared.menu.ui;

import java.lang.reflect.Proxy;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class UiNumberFieldTest {
    @Test void trackAndCompactInputShareLiveWheelBoundsWhileOnlyInputOpensEditing() {
        var value = new AtomicInteger(95);
        var edits = new AtomicInteger();
        var range = UiRange.live(0, 100, value::get).step(10);
        var scene = MenuScene.screen("numeric").content(Ui.numberField(Component.text("Level"), range,
                        (_, _, next) -> value.set((int) Math.round(next)))
                .key("level").edit((_, _) -> edits.incrementAndGet()).build()).build();
        var handle = (FloatingMenuHandle) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{FloatingMenuHandle.class},
                (_, method, _) -> method.getName().equals("horizontalFraction") ? OptionalDouble.of(.37) : null);
        act(scene, "track", FloatingMenuInteraction.PRIMARY, handle);
        assertEquals(37, value.get());
        act(scene, "level", FloatingMenuInteraction.SCROLL_UP, handle);
        assertEquals(47, value.get());
        value.set(95);
        act(scene, "track", FloatingMenuInteraction.SCROLL_UP, handle);
        assertEquals(100, value.get());
        value.set(0);
        act(scene, "level", FloatingMenuInteraction.SCROLL_DOWN, handle);
        assertEquals(0, value.get());
        act(scene, "level", FloatingMenuInteraction.PRIMARY, handle);
        assertEquals(1, edits.get());
        assertEquals(0, value.get(), "opening precise editing does not change the value");
    }

    @Test void disablingCoversBothTargetsWithoutDuplicateEndpointLabels() {
        var reason = Component.text("No permission");
        var scene = MenuScene.screen("numeric").content(Ui.numberField(Component.text("Level"), UiRange.fixed(0, 100, 50).withLabels(),
                (_, _, _) -> fail("Disabled control changed")).key("level").edit((_, _) -> fail("Disabled control edited"))
                .disabled(reason).build()).build();
        for (var key : new String[]{"track", "level"}) {
            var input = element(SceneGeometryTestSupport.measure(scene, 1), key).node().input();
            assertFalse(input.enabled());
            assertEquals(reason, input.disabledReason().orElseThrow());
        }
        var track = (UiContent.Range) element(SceneGeometryTestSupport.measure(scene, 1), "track").node().content();
        assertFalse(track.value().labels());
    }

    @Test void narrowHostsStackWholeControlsWhileLargeHostsKeepValuesBesideTracks() {
        for (double scale : new double[]{.7, 1, 1.8}) for (int width : new int[]{100, 180, 320, 600}) {
            var scene = MenuScene.screen("numeric").viewport(new UiSize(width, 1024))
                    .content(Ui.numberField(Component.text("Level"), UiRange.fixed(0, 100, 50).format(n -> Component.text((int) n + "%")),
                            (_, _, _) -> {}).key("value").build()).build();
            var frame = SceneGeometryTestSupport.measure(scene, scale);
            assertDisjoint(frame);
            assertTrue(frame.size().width() <= width + 1e-6);
            assertTrue(frame.size().height() < 120, "stacking must not stretch a slider to fill the host height");
            var track = element(frame, "track"); var input = element(frame, "value");
            if (width >= 320) assertEquals(track.y() + track.size().height() / 2, input.y() + input.size().height() / 2, 1e-6);
            if (width == 100) assertTrue(input.y() >= track.y() + track.size().height());
        }
    }

    @Test void distinctValueKeysAnchorPreciseEditorsWhenSeveralFieldsShareAForm() {
        var fields = Ui.form();
        for (String key : new String[]{"volume", "range"}) fields.field(key, Component.text(key),
                Ui.numberField(Component.text(key), UiRange.fixed(0, 100, 50), (_,_,_) -> {})
                        .key(key).edit((_,_) -> {}).build());
        var scene = MenuScene.screen("numeric").content(fields.build())
                .popover(MenuPopover.attached(UiKey.named("range"), Component.text("Range"),
                        Ui.text(Component.text("Precise input")).key("editor").build(), Component.text("Close"))).build();
        var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.measure(scene, 1.8));
        assertDisjoint(frame);
        assertNotNull(entry(scene, "editor"));
        assertEquals(1, elements(scene).values().stream().filter(element -> element.key().value().equals("range")).count());
    }

    private static void act(MenuScene scene, String key, FloatingMenuInteraction interaction, FloatingMenuHandle handle) {
        entry(scene, key).trigger(interaction).execute(null, handle, interaction);
    }
}
