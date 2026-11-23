package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.runtime.window.MenuWorkspaceSession;

import java.util.*;
import java.util.function.*;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.UiKey;

/** Feature tests use the real ownership and composition runtime without allocating network entities. */
public final class MenuWorkspaceTestSupport implements AutoCloseable {
    private static final Component LABEL = Component.text("Window");
    private static final MenuWorkspaceSession.Labels LABELS = new MenuWorkspaceSession.Labels(LABEL,LABEL,LABEL,LABEL,LABEL,LABEL);
    private final UUID id = UUID.randomUUID();
    private final UUID player;
    private final Root root = new Root();
    private final MenuWorkspaceSession manager;
    private final Map<UUID,FloatingMenuHandle> known = new LinkedHashMap<>();
    private MenuScene authored;
    private MenuScene composed;
    private MenuPanel popover;
    private MenuPopover declared;
    private UUID popoverOwner;
    private boolean closed;

    public MenuWorkspaceTestSupport(UUID player, MenuScene scene) {
        this.player = player; authored = scene;
        manager = new MenuWorkspaceSession(id,new MenuWorkspaceSession.Host() {
            public void requireThread(String operation) { }
            public void execute(Runnable action) { action.run(); }
            public FloatingMenuHandle root() { return root; }
            public void changed() { if (!closed) composed = manager.compose(authored,LABELS); }
            public void focusChanged() { }
            public boolean cancelInput(UUID owner) { return false; }
            public void lifecycle(MenuScene scene, FloatingMenuHandle handle, FloatingMenuState before, FloatingMenuState after, FloatingMenuCloseReason reason) {
                known.put(handle.id(),handle);
            }
            public void rootClosed(FloatingMenuCloseReason reason) { closed = true; }
            public void failure(RuntimeException error) { throw error; }
            public MenuInputLease suspend(UUID owner) { return manager.suspend(owner); }
            public void popover(UUID owner, UiKey source, Function<Runnable,MenuPanel> content) {
                closePopover(owner,null); popoverOwner = owner; declared = null;
                popover = content.apply(() -> {}); popover.attach(manager.handle(owner));
            }
            public void popover(UUID owner, MenuPopover content) { closePopover(owner,null); popoverOwner = owner; declared = content; }
            public boolean closePopover(UUID owner, MenuPopover expected) {
                boolean present = popover != null || declared != null;
                if (popover != null) { var previous = popover; popover = null; previous.close(); }
                declared = null; return present;
            }
        });
        composed = manager.compose(authored,LABELS);
    }
    public FloatingMenuHandle root() { return root; }
    public FloatingMenuHandle current() { return manager.handle(manager.focused()); }
    public MenuScene rootScene() { return authored; }
    public MenuScene scene() { var local = manager.scene(manager.focused()); return local == null ? authored : local; }
    public MenuScene composed() { return composed; }
    public boolean paused() { return manager.paused(); }
    public List<MenuWindowHandle<?>> windows() { return known.values().stream().filter(handle -> handle instanceof MenuWindowHandle<?> && handle.state() != FloatingMenuState.CLOSED)
            .<MenuWindowHandle<?>>map(handle -> (MenuWindowHandle<?>) handle).toList(); }
    public MenuScene popup() { return declared != null ? MenuScene.screen("popup",declared.title()).content(declared.content()).build() : popover.render(); }
    public FloatingMenuHandle popupOwner() { return manager.handle(popoverOwner); }
    public void refresh() { composed = manager.compose(authored,LABELS); }
    @Override public void close() { if (closed) return; manager.close(); closed = true; if (popover != null) { popover.close(); popover = null; } }

    private final class Root implements FloatingMenuHandle {
        public UUID id() { return id; }
        public UUID playerId() { return player; }
        public FloatingMenuState state() { return closed ? FloatingMenuState.CLOSED : manager.paused() || manager.minimized() ? FloatingMenuState.SUSPENDED : FloatingMenuState.ACTIVE; }
        public boolean focused() { return !closed && !manager.minimized() && manager.focused().equals(id); }
        public void activate() { manager.restore(id); }
        public void minimize() { manager.minimize(id); }
        public void restore() { manager.restore(id); }
        public void position(MenuWindowSpec.Position value) { manager.position(id,value); }
        public void move() { manager.move(id); }
        public int depth() { return 0; }
        public void refresh() { MenuWorkspaceTestSupport.this.refresh(); }
        public void reanchor() { }
        public void update(MenuScene scene) { authored = scene; refresh(); }
        public FloatingMenuHandle openChild(MenuScene scene) { update(scene); return this; }
        public <T> MenuWindowHandle<T> openWindow(MenuWindowSpec spec,Class<T> type,Function<MenuWindowContext<T>,MenuPanel> content,Consumer<MenuWindowResult<T>> result) {
            return manager.open(id,spec,type,content,result);
        }
        public void openPopover(UiKey source,Function<Runnable,MenuPanel> content) {
            if (popover != null) popover.close(); declared = null; popoverOwner = id;
            popover = content.apply(() -> {}); popover.attach(this);
        }
        public void openPopover(MenuPopover definition) { if (popover != null) { popover.close(); popover = null; } declared = definition; popoverOwner = id; }
        public void closePopover() { if (popover != null) { popover.close(); popover = null; } declared = null; }
        public void closePopover(MenuPopover expected) { if (declared != null && expected.sameScope(declared)) closePopover(); }
        public MenuInputLease suspend() { return manager.suspend(id); }
        public void back() { if (!manager.focused().equals(id)) current().back(); else close(); }
        public void close() { if (!closed) manager.requestClose(id,FloatingMenuCloseReason.USER); }
        public void feedback(Component message,FloatingMenuFeedbackKind kind) { }
    }
}
