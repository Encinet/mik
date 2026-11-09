package org.encinet.mik.shared.menu.runtime;

import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuWindowedPresentationTest {
    @Test void leavingAWindowClosesItsCompanionBeforeRenderingObsoleteObjectData() {
        var renders = new AtomicInteger();
        var closed = new AtomicInteger();
        try (var panel = new MenuPopoverPanel("catalogue", UiKey.item(0), Component.text("Close"), _ -> {})) {
            panel.initialize(_ -> new MenuPanel() {
                public MenuScene render() { renders.incrementAndGet(); return MenuScene.screen("details").content(Ui.text(Component.text("Object zero")).build()).build(); }
                public void close() { closed.incrementAndGet(); }
            });
            var world = new FloatingMenuTestWorld();
            var presentation = new MenuPresentation();
            var eye = new Location(world.world(), 0, 64, 0);
            var first = presentation.prepare(null, eye, new Vector(0, 0, 1), scene(0), 1, 1,
                    FloatingMenuFieldOfView.DEFAULT, UiPalette.DEFAULT, panel::compose);
            assertNotNull(first.geometry().popover());
            assertEquals(1, renders.get());
            var next = presentation.prepare(null, eye, new Vector(0, 0, 1), scene(1), 1, 1,
                    FloatingMenuFieldOfView.DEFAULT, UiPalette.DEFAULT, panel::compose);
            assertNull(next.geometry().popover());
            assertFalse(panel.active());
            assertEquals(1, renders.get());
            assertEquals(1, closed.get());
            assertTrue(next.geometry().definition().entries().keySet().stream().noneMatch(path -> path.contains("/0/")));
        }
    }

    private static MenuScene scene(int page) {
        return MenuScene.screen("catalogue").viewport(new UiSize(320, 160)).content(Ui.windowedList(10000, UiLength.ui(32),
                index -> Ui.button(Component.text("Object " + index)).key(UiKey.item(index)).singleLine().primary((_, _) -> {}).build())
                .header(Ui.text(Component.text("Objects")).build()).page(page, _ -> {}).build()).build();
    }
}
