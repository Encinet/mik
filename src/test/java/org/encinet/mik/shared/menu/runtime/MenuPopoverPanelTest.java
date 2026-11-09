package org.encinet.mik.shared.menu.runtime;

import java.util.concurrent.atomic.*;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuPopoverPanelTest {
    private static MenuScene host(String screen) {
        return MenuScene.screen(screen).content(Ui.button(Component.text("Identity")).key("identity").primary((_,_)->{}).build()).build();
    }

    private static MenuScene compose(MenuPopoverPanel panel, MenuScene scene) {
        return panel.compose(scene, UiSceneTestSupport.frame(scene));
    }

    @Test void parentRefreshesAndOlderLoadingSnapshotsRetainTheSameOwnedContent() {
        var closed = new AtomicInteger();
        var changed = new AtomicInteger();
        var callback = new AtomicReference<Runnable>();
        var panel = new MenuPopoverPanel("main",UiKey.named("identity"),Component.text("Close"),_ -> changed.incrementAndGet());
        panel.initialize(redraw -> {
            callback.set(redraw);
            return new MenuPanel() {
                @Override public MenuScene render() { return MenuScene.screen("identity-content").content(Ui.text(Component.text("Progress")).build()).build(); }
                @Override public void close() { closed.incrementAndGet(); redraw.run(); }
            };
        });
        var first = compose(panel, host("main"));
        var next = compose(panel, host("main"));
        assertNotNull(next.popover());
        assertNotNull(compose(panel, first).popover(),"retained older snapshots still belong to the same scope");
        callback.get().run();
        assertEquals(1,changed.get());
        assertEquals(0,closed.get());
        var elsewhere = compose(panel, host("settings"));
        assertNull(elsewhere.popover());
        assertEquals(1,closed.get());
        callback.get().run();
        panel.close();
        assertEquals(1,changed.get(),"late callbacks and cleanup cannot redraw a disposed selection");
        assertEquals(1,closed.get());
    }

    @Test void aNewDeclaredCompanionSupersedesTheManagedSelectionWithoutBeingOverwritten() {
        var closed = new AtomicBoolean();
        var panel = new MenuPopoverPanel("main",UiKey.named("identity"),Component.text("Close"),_ -> {});
        panel.initialize(_ -> new MenuPanel() {
            @Override public MenuScene render() { return host("content"); }
            @Override public void close() { closed.set(true); }
        });
        compose(panel, host("main"));
        var replacement = MenuPopover.attached(UiKey.named("identity"), Component.text("Details"), Ui.text(Component.text("Confirm")).build(), Component.text("Close")).exclusive();
        assertSame(replacement,compose(panel, host("main").toBuilder().popover(replacement).build()).popover());
        assertTrue(closed.get());
        assertFalse(panel.active());
    }

    @Test void removingTheSourceClosesContentBeforeRenderingAnObsoleteSelection() {
        var renders = new AtomicInteger();
        var closed = new AtomicInteger();
        var panel = new MenuPopoverPanel("main", UiKey.named("identity"), Component.text("Close"), _ -> {});
        panel.initialize(_ -> new MenuPanel() {
            @Override public MenuScene render() { renders.incrementAndGet(); return host("content"); }
            @Override public void close() { closed.incrementAndGet(); }
        });
        compose(panel, host("main"));
        var otherTarget = MenuScene.screen("main").content(Ui.button(Component.text("Other device")).key("other").build()).build();
        assertNull(compose(panel, otherTarget).popover());
        assertFalse(panel.active());
        assertEquals(1, renders.get(), "Old device data must not be read after its source disappears");
        assertEquals(1, closed.get());
    }

    @Test void theHostOwnsOneHeaderWithLocalToolsAndEveryCloseCarriesItsScope() {
        var panel=new MenuPopoverPanel("main",UiKey.named("identity"),Component.text("Close this popover"),_->{});
        panel.initialize(_->new MenuPanel() {
            public MenuScene render() { return MenuScene.screen("content",Component.text("Identity")).content(Ui.text(Component.text("Progress")).key("progress").build()).build(); }
            public UiNode headerActions() { return Ui.button(UiIcons.REFRESH,Component.text("Refresh identity")).key("refresh").build(); }
        });
        var first=compose(panel, host("main")).popover();
        var next=compose(panel, host("main")).popover();
        assertTrue(first.sameScope(next));
        var replacement=MenuPopover.attached(UiKey.named("identity"),Component.text("Another"),Ui.column().build(),Component.text("Close"));
        assertFalse(first.sameScope(replacement));
        var scene=MenuScene.screen("popup").content(first.contentTree()).build();
        assertEquals(1,UiSceneContent.elements(scene.content()).values().stream().filter(node->node.key().value().equals("close")).count());
        assertNotNull(org.encinet.mik.shared.menu.ui.UiSceneTestSupport.entry(scene,"refresh"));
        var captured=new AtomicReference<MenuPopover>();
        var handle=(FloatingMenuHandle)java.lang.reflect.Proxy.newProxyInstance(FloatingMenuHandle.class.getClassLoader(),new Class<?>[]{FloatingMenuHandle.class},
                (_,method,args)-> { assertEquals("closePopover",method.getName()); captured.set((MenuPopover)args[0]); return null; });
        org.encinet.mik.shared.menu.ui.UiSceneTestSupport.entry(scene,"close").trigger(FloatingMenuInteraction.PRIMARY).execute(null,handle,FloatingMenuInteraction.PRIMARY);
        assertSame(first,captured.get(),"the host must not issue an unscoped close for a retained snapshot");
    }
}
