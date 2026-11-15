package org.encinet.mik.shared.menu.runtime;

import java.util.*;
import java.util.function.*;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.encinet.mik.shared.util.ShutdownSequence;

/** Window ownership, focus and close plans. All mutations are confined to the host's menu thread. */
final class MenuWorkspaceSession implements AutoCloseable {
    static final int MAX_WINDOWS = 8;
    static final int MAX_READING_WINDOWS = 3;
    static final UiKey MAIN = UiKey.named("menu-main");
    record Labels(Component back, Component close, Component minimize, Component restore,
                  Component expand, Component collapse, Component windows, Component blocked, Component position) { }
    interface Host {
        void requireThread(String operation);
        void execute(Runnable action);
        FloatingMenuHandle root();
        void changed();
        void focusChanged();
        boolean cancelInput(UUID owner);
        void lifecycle(MenuScene scene, FloatingMenuHandle handle, FloatingMenuState before,
                       FloatingMenuState after, FloatingMenuCloseReason reason);
        void rootClosed(FloatingMenuCloseReason reason);
        void failure(RuntimeException error);
        MenuInputLease suspend(UUID window);
        void popover(UUID owner, UiKey source, Function<Runnable, MenuPanel> content);
        void popover(UUID owner, MenuPopover content);
        boolean closePopover(UUID owner, MenuPopover expected);
    }
    private record Page(UUID id, MenuPanel content, MenuScene override, MenuScene suspended) {
        Page(MenuPanel content) { this(UUID.randomUUID(), content, null, null); }
        Page(UUID id,MenuPanel content,MenuScene override) { this(id,content,override,null); }
        MenuScene scene() { return override == null ? content.render() : override; }
    }
    private static final class Window {
        final UUID id = UUID.randomUUID();
        final UUID owner;
        final MenuWindowSpec spec;
        final UUID ownerPage;
        final Class<?> resultType;
        final Consumer<MenuWindowResult<?>> result;
        final Deque<Page> pages = new ArrayDeque<>();
        MenuWindowHandle<?> handle;
        MenuWindowSpec.Position position;
        MenuWindowResult<?> outcome;
        MenuScene scene;
        boolean minimized;
        boolean expanded;
        boolean closed;
        Object refreshRevision;
        Window(UUID owner, UUID ownerPage, MenuWindowSpec spec, Class<?> type, Consumer<MenuWindowResult<?>> result) {
            this.owner = owner; this.ownerPage = ownerPage; this.spec = spec; this.resultType = type;
            this.result = result; this.position = spec.position();
        }
        UiKey scope() { return UiKey.named("window-" + id); }
        Page page() { return pages.getFirst(); }
    }
    private final UUID root;
    private final Host host;
    private final LinkedHashMap<UUID, Window> windows = new LinkedHashMap<>();
    private final List<UUID> order = new ArrayList<>();
    private UUID focus;
    private UUID rootPage = UUID.randomUUID();
    private String rootScreen;
    private boolean closed;
    private boolean composing;
    private boolean listOpen;
    private int suspendCount;
    private ClosePlan closing;
    private Set<UUID> presented = Set.of();
    private final List<Suspension> suspensions = new ArrayList<>();

    MenuWorkspaceSession(UUID root, Host host) { this.root = root; this.focus = root; this.host = host; }
    UUID focused() { return focus; }
    boolean paused() { return suspendCount > 0; }
    boolean empty() { return windows.isEmpty(); }
    boolean contains(UUID id) { return !closed && (root.equals(id) || windows.containsKey(id)); }
    private UUID pageId(UUID id) { return root.equals(id) ? rootPage : window(id).page().id(); }
    private Window window(UUID id) {
        var value = windows.get(id);
        if (value == null || value.closed) throw new IllegalStateException("Window is no longer open");
        return value;
    }
    private boolean descendant(UUID id, UUID ancestor) {
        for (UUID cursor = id; cursor != null;) {
            if (cursor.equals(ancestor)) return true;
            var value = windows.get(cursor); cursor = value == null ? null : value.owner;
        }
        return false;
    }
    boolean allowed(UUID id) {
        if (closed || paused() || !contains(id)) return false;
        if (closing != null && descendant(id,closing.target) && closing.initial.contains(id)) return false;
        for (var entry : windows.values()) {
            if (closing != null && closing.approved.contains(entry.id)) continue;
            if (hidden(entry.id) || entry.spec.modality() == MenuWindowSpec.Modality.NONE) continue;
            boolean affects = entry.spec.modality() == MenuWindowSpec.Modality.WORKSPACE || descendant(id, entry.owner);
            if (affects && !descendant(id, entry.id)) return false;
        }
        return true;
    }
    private boolean hidden(UUID id) {
        for (UUID cursor = id; !cursor.equals(root);) {
            var value = windows.get(cursor);
            if (value == null || value.minimized) return true;
            cursor = value.owner;
        }
        return false;
    }
    private void changed() { if (!closed && !composing) host.changed(); }

    private Set<UUID> readingWindows() {
        var result = new LinkedHashSet<UUID>(); result.add(root);
        if (!focus.equals(root) && windows.containsKey(focus)) {
            result.add(focus);
            var owner = window(focus).owner;
            if (!owner.equals(root)) result.add(owner);
        }
        for (UUID id : List.copyOf(order).reversed()) {
            if (result.size() >= MAX_READING_WINDOWS) break;
            var value = windows.get(id);
            if (!hidden(id) && value.owner.equals(root) && allowed(id)) result.add(id);
        }
        return Set.copyOf(result);
    }

    private boolean activatable(UUID id) {
        return allowed(id) || windows.keySet().stream().anyMatch(candidate -> descendant(candidate,id) && allowed(candidate));
    }

    <T> MenuWindowHandle<T> open(UUID owner, MenuWindowSpec spec, Class<T> type,
                                Function<MenuWindowContext<T>, MenuPanel> create, Consumer<MenuWindowResult<T>> result) {
        host.requireThread("openWindow");
        if (closed || !contains(owner)) throw new IllegalStateException("Cannot open a window from a closed owner");
        Objects.requireNonNull(spec); Objects.requireNonNull(type); Objects.requireNonNull(create); Objects.requireNonNull(result);
        for (var existing : windows.values()) if (existing.owner.equals(owner) && existing.spec.key().equals(spec.key())) {
            if (!existing.resultType.equals(type)) throw new IllegalArgumentException("Window key has a different result type");
            activate(existing.id);
            return new Handle<>(existing, type);
        }
        if (!allowed(owner) && (closing == null || !owner.equals(closing.waitingFor)))
            throw new IllegalStateException("The owner is blocked by another task");
        if (windows.size() + 1 >= MAX_WINDOWS) throw new UiOverflowException("Workspace window capacity reached");
        var value = new Window(owner, pageId(owner), spec, type, received -> result.accept(received.completed()
                ? MenuWindowResult.completed(type.cast(received.value())) : MenuWindowResult.cancelled(received.reason())));
        var handle = new Handle<>(value, type); value.handle = handle;
        var panel = Objects.requireNonNull(create.apply(new MenuWindowContext<>(handle, handle::refresh)));
        value.pages.push(new Page(panel));
        UUID previous = focus;
        windows.put(value.id, value); order.add(value.id); focus = value.id;
        try {
            value.scene = validate(panel.render());
            changed();
            host.focusChanged();
            panel.attach(handle);
            if (windows.get(value.id) == value) {
                host.lifecycle(value.scene, handle, FloatingMenuState.CLOSED, FloatingMenuState.OPENING, null);
                if (windows.get(value.id) == value) host.lifecycle(value.scene,handle,FloatingMenuState.OPENING,FloatingMenuState.ACTIVE,null);
            }
        } catch (RuntimeException | Error failure) {
            windows.remove(value.id); order.remove(value.id); focus = previous;
            value.closed = true;
            try { panel.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            changed();
            throw failure;
        }
        return handle;
    }

    void activate(UUID id) {
        host.requireThread("activateWindow");
        if (!contains(id)) return;
        if (!root.equals(id)) {
            for (UUID cursor = id; !cursor.equals(root); cursor = window(cursor).owner) window(cursor).minimized = false;
        }
        UUID target = id;
        if (!allowed(id)) for (UUID candidate : List.copyOf(order))
            if (descendant(candidate, id) && allowed(candidate)) target = candidate;
        if (!allowed(target)) return;
        if (focus.equals(target)) return;
        focus = target;
        var group = order.stream().filter(candidate -> descendant(candidate, id)).toList();
        order.removeAll(group); order.addAll(group);
        host.focusChanged(); changed();
    }

    UUID ownerOf(String path) {
        if (path == null) return focus;
        for (var value : windows.values()) if (segment(path, value.scope().value())) return value.id;
        return root;
    }
    static boolean segment(String path, String key) {
        return path.equals(key) || path.startsWith(key + "/") || path.contains("/" + key + "/") || path.endsWith("/" + key);
    }
    FloatingMenuHandle handle(UUID id) { return root.equals(id) ? host.root() : window(id).handle; }
    MenuScene scene(UUID id) { return root.equals(id) ? null : window(id).scene; }
    String sourceScope(UUID id) { return root.equals(id) ? MAIN.value() : window(id).scope().value(); }

    MenuInputLease suspend(UUID id) {
        host.requireThread("suspendWindow");
        if (!allowed(id)) throw new IllegalStateException("Cannot suspend a closed or blocked window");
        var lease = new Suspension(id,pageId(id)); suspensions.add(lease);
        suspendCount++; host.focusChanged(); changed(); return lease;
    }
    private final class Suspension implements MenuInputLease {
        private final UUID owner, page;
        private boolean open = true;
        Suspension(UUID owner,UUID page) { this.owner = owner; this.page = page; }
        public boolean active() { return open && contains(owner) && pageId(owner).equals(page); }
        public boolean complete() { boolean valid = active(); close(); return valid; }
        public void close() {
            host.execute(() -> { if (!open) return; open = false; suspendCount--; suspensions.remove(this); if (!closed) changed(); });
        }
    }
    private void releaseExpiredSuspensions() {
        for (var lease : List.copyOf(suspensions)) if (!lease.active()) lease.close();
    }

    MenuScene compose(MenuScene authored, Labels labels) {
        host.requireThread("composeWindows");
        if (closed) return authored;
        composing = true;
        try {
            if (!Objects.equals(rootScreen, authored.screenId())) {
                if (rootScreen != null) expirePage(root, rootPage);
                rootScreen = authored.screenId(); rootPage = UUID.randomUUID();
            }
            releaseExpiredSuspensions();
            if (!windows.isEmpty() && authored.spatialFrame() == FloatingMenuSpatialFrame.AROUND_VIEWER)
                throw new IllegalArgumentException("Independent windows require a front workspace");
            var nodes = new ArrayList<UiNode>();
            var reading = readingWindows();
            var viewports = new ArrayList<UiSize>();
            var positions = new ArrayList<MenuWindowSpec.Position>();
            nodes.add(Ui.column().key(MAIN).child(authored.content()).build());
            viewports.add(authored.viewport()); positions.add(MenuWindowSpec.Position.CENTER);
            int layer = 1;
            for (UUID id : List.copyOf(order)) {
                var value = windows.get(id);
                if (value == null || hidden(id) || !reading.contains(id)) continue;
                value.scene = validate(value.page().scene());
                UiNode tools = tools(value, labels);
                UiNode header = UiTemplates.titleBar(Ui.button(value.scene.title()).key("title")
                        .selected(focus.equals(id)).primary((_,_) -> activate(id)).build(), tools);
                UiNode body = Ui.layout(UiLayouts.headerBody(UiLength.ui(8))).key("window-body")
                        .child(UiKey.named("header"), header)
                        .child(UiKey.named("body"), Ui.column().key("page-" + value.page().id()).child(value.scene.content()).build()).build();
                nodes.add(Ui.group(Ui.surface(body).key("surface").style(UiTheme.WINDOW)
                                .facing(UiSurfaceFacing.FORWARD).build()).key(value.scope())
                        .transform(UiTransform.IDENTITY.translated(0, 0, 32 * layer++)).build());
                viewports.add(value.expanded ? authored.viewport() : value.scene.viewport());
                positions.add(value.position);
            }
            if (!windows.isEmpty() && allowed(root)) {
                var list = Ui.column().key("window-list").gap(UiLength.ui(4));
                for (var value : windows.values()) {
                    var button = Ui.button(value.scene == null ? labels.restore() : value.scene.title()).key("restore-" + value.id)
                            .selected(focus.equals(value.id)).primary((_, _) -> activate(value.id));
                    if (!activatable(value.id) && !hidden(value.id)) button.disabled(labels.blocked());
                    list.child(button.build());
                }
                var bar = Ui.row().key("windows-bar").child(Ui.button(UiIcons.COPY, labels.windows())
                        .key("list").selected(listOpen).primary((_, _) -> { listOpen = !listOpen; changed(); }).build());
                if (listOpen) bar.child(list.build());
                nodes.add(Ui.group(Ui.surface(bar.build()).key("list-surface").style(UiTheme.TOOLBAR).build())
                        .key("window-switcher").transform(UiTransform.IDENTITY.translated(0,0,32 * layer)).build());
                viewports.add(authored.viewport()); positions.add(MenuWindowSpec.Position.BOTTOM_LEFT);
            }
            var stack = Ui.layout(new MenuWindowLayout(viewports, positions)).key("menu-workspace");
            nodes.forEach(stack::child);
            presented = reading;
            return authored.toBuilder().content(stack.build()).build();
        } finally { composing = false; }
    }

    private UiNode tools(Window value, Labels labels) {
        var tools = Ui.row().key("window-tools").gap(UiLength.ui(4));
        if (value.pages.size() > 1) tools.child(Ui.button(UiIcons.CHEVRON_LEFT, labels.back()).key("back")
                .primary((_, _) -> back(value.id)).build());
        if (value.spec.minimizable()) {
            var minimize = Ui.button(UiIcons.REMOVE, labels.minimize()).key("minimize")
                    .primary((_, _) -> minimize(value.id));
            if (!canMinimize(value)) minimize.disabled(labels.blocked());
            tools.child(minimize.build());
        }
        tools.child(Ui.button(UiIcons.MORE,labels.position()).key("position").primary((_,_) -> {
            value.position = MenuWindowSpec.Position.values()[(value.position.ordinal() + 1) % MenuWindowSpec.Position.values().length]; changed();
        }).build());
        tools.child(Ui.button(UiIcons.BOX, value.expanded ? labels.collapse() : labels.expand()).key("expand")
                .primary((_, _) -> { value.expanded = !value.expanded; changed(); }).build());
        var extra = value.page().content().headerActions();
        if (extra != null) tools.child(extra);
        return tools.child(Ui.button(UiIcons.CROSS, labels.close()).key("close")
                .primary((_, _) -> requestClose(value.id, FloatingMenuCloseReason.USER)).build()).build();
    }

    void tick(org.bukkit.entity.Player player, long tick) {
        releaseExpiredSuspensions();
        for (var value : List.copyOf(windows.values())) {
            if (value.closed || value.scene == null || hidden(value.id) || !presented.contains(value.id)) continue;
            var refresh = value.scene.refresh();
            if (!refresh.enabled() || tick % refresh.intervalTicks() != 0) continue;
            Object revision = refresh.revision(player);
            if (refresh.revision() != null && Objects.equals(revision, value.refreshRevision)) continue;
            value.refreshRevision = revision;
            try { refresh.action().execute(player, value.handle); }
            catch (RuntimeException error) { host.failure(error); }
        }
    }

    private void minimize(UUID id) {
        var value = window(id);
        if (!canMinimize(value)) return;
        value.minimized = true;
        if (descendant(focus, id)) { focus = value.owner; restoreFocus(); host.focusChanged(); }
        changed();
    }
    private boolean canMinimize(Window value) {
        return value.spec.minimizable() && windows.values().stream().noneMatch(child -> descendant(child.id, value.id)
                && child.spec.modality() != MenuWindowSpec.Modality.NONE);
    }
    private void restoreFocus() {
        if (allowed(focus) && !hidden(focus)) return;
        focus = root;
        for (UUID id : order) if (!hidden(id) && allowed(id)) focus = id;
    }
    /** Window content shares the workspace's spatial frame and resource pipeline. */
    private static MenuScene validate(MenuScene scene) {
        Objects.requireNonNull(scene);
        if (!scene.decorations().isEmpty() || scene.frameObserver() != FloatingMenuFrameObserver.NONE)
            throw new IllegalArgumentException("Window content must use UI nodes; standalone decorations require a root scene");
        if (scene.popover() != null)
            throw new IllegalArgumentException("Open a window's attached menu through its handle");
        if (scene.spatialFrame() == FloatingMenuSpatialFrame.AROUND_VIEWER)
            throw new IllegalArgumentException("A window shares its workspace's front spatial frame");
        return scene;
    }

    private void back(UUID id) {
        var value = window(id);
        if (value.pages.size() <= 1) { requestClose(id, FloatingMenuCloseReason.BACK); return; }
        var oldScene = value.scene;
        var old = value.pages.pop();
        try {
            value.scene = validate(value.page().scene());
            changed();
        } catch (RuntimeException | Error failure) {
            value.pages.push(old); value.scene = oldScene;
            throw failure;
        }
        var shutdown = new ShutdownSequence();
        shutdown.attempt("page windows", () -> expirePage(id, old.id()));
        shutdown.attempt("page suspensions", this::releaseExpiredSuspensions);
        shutdown.attempt("page content", old.content()::close);
        shutdown.attempt("page lifecycle", () -> host.lifecycle(oldScene, value.handle,
                FloatingMenuState.ACTIVE, FloatingMenuState.CLOSED, FloatingMenuCloseReason.BACK));
        if (contains(id)) {
            if (focus.equals(id)) host.focusChanged();
            shutdown.attempt("resumed page attach", () -> value.page().content().attach(value.handle));
            if (contains(id)) shutdown.attempt("resumed page lifecycle", () -> host.lifecycle(value.scene,
                    value.handle, FloatingMenuState.SUSPENDED, FloatingMenuState.ACTIVE, null));
        }
        shutdown.finish("window page");
    }
    private void expirePage(UUID owner, UUID page) {
        for (var value : List.copyOf(windows.values())) if (value.owner.equals(owner)
                && value.spec.lifetime() == MenuWindowSpec.Lifetime.PAGE && value.ownerPage.equals(page))
            forceClose(value.id, FloatingMenuCloseReason.REPLACED);
    }

    void requestClose(UUID id, FloatingMenuCloseReason reason) {
        host.requireThread("closeWindow");
        if (closed || !contains(id)) return;
        if (closing != null) {
            if (!closing.initial.contains(id) && descendant(id,closing.target)) closing.cancel();
            return;
        }
        closing = new ClosePlan(id, reason); closing.next();
    }
    private final class ClosePlan {
        final UUID target; final FloatingMenuCloseReason reason;
        final Set<UUID> approved = new HashSet<>();
        final Set<UUID> initial = new HashSet<>();
        UUID waitingFor;
        ClosePlan(UUID target, FloatingMenuCloseReason reason) {
            this.target = target; this.reason = reason; initial.add(root); initial.addAll(order);
        }
        void cancel() {
            if (closing != this) return;
            closing = null;
            var targetWindow = windows.get(target);
            if (targetWindow != null) targetWindow.outcome = null;
            for (UUID id : List.copyOf(order).reversed()) if (!initial.contains(id) && contains(id) && descendant(id,target))
                forceClose(id,FloatingMenuCloseReason.BACK);
        }
        void next() {
            if (closing != this || !contains(target)) return;
            for (UUID id : List.copyOf(order).reversed()) {
                if (!descendant(id, target) || approved.contains(id)) continue;
                var value = windows.get(id); waitingFor = id;
                var decision = new MenuCloseRequest() {
                    private boolean resolved;
                    public FloatingMenuCloseReason reason() { return reason; }
                    public boolean active() { return !resolved && closing == ClosePlan.this && contains(id); }
                    public void approve() { host.execute(() -> { if (!active()) return; resolved = true; approved.add(id); waitingFor = null; next(); }); }
                    public void cancel() { host.execute(() -> { if (!active()) return; resolved = true; ClosePlan.this.cancel(); }); }
                };
                try { value.spec.closeRequested().accept(decision); }
                catch (RuntimeException error) { closing = null; host.failure(error); }
                return;
            }
            closing = null;
            if (target.equals(root)) {
                try { close(reason); }
                finally { host.rootClosed(reason); }
            }
            else forceClose(target, reason);
        }
    }
    private void forceClose(UUID id, FloatingMenuCloseReason reason) {
        var selected = order.stream().filter(candidate -> descendant(candidate, id)).toList().reversed();
        boolean focusLost = descendant(focus, id);
        UUID fallback = windows.containsKey(id) ? window(id).owner : root;
        var removed = new ArrayList<Window>();
        for (UUID candidate : selected) {
            var value = windows.remove(candidate); order.remove(candidate);
            if (value != null) { value.closed = true; removed.add(value); }
        }
        var shutdown = new ShutdownSequence();
        if (focusLost) { focus = fallback; restoreFocus(); shutdown.attempt("window focus",host::focusChanged); }
        shutdown.attempt("window suspensions",this::releaseExpiredSuspensions);
        for (var value : removed) {
            boolean currentPage = true;
            while (!value.pages.isEmpty()) {
                var page = value.pages.pop(); var snapshot = currentPage ? value.scene : page.suspended();
                var before = currentPage ? FloatingMenuState.ACTIVE : FloatingMenuState.SUSPENDED;
                currentPage = false;
                shutdown.attempt("window content", page.content()::close);
                if (snapshot != null) shutdown.attempt("window lifecycle", () -> host.lifecycle(snapshot,value.handle,before,FloatingMenuState.CLOSED,reason));
            }
            if (contains(value.owner) && pageId(value.owner).equals(value.ownerPage))
                shutdown.attempt("window result", () -> value.result.accept(value.outcome == null ? MenuWindowResult.cancelled(reason) : value.outcome));
        }
        shutdown.attempt("window presentation",this::changed);
        shutdown.finish("window group");
    }
    @Override public void close() { close(FloatingMenuCloseReason.REPLACED); }
    void close(FloatingMenuCloseReason reason) {
        if (closed) return;
        closed = true;
        closing = null;
        var shutdown = new ShutdownSequence();
        shutdown.attempt("workspace suspensions",this::releaseExpiredSuspensions);
        for (UUID id : List.copyOf(order).reversed()) if (windows.containsKey(id))
            shutdown.attempt("window " + id, () -> forceClose(id, reason));
        shutdown.finish("workspace windows");
    }

    private final class Handle<T> implements MenuWindowHandle<T> {
        private final Window value;
        private final Class<T> type;
        Handle(Window value, Class<T> type) { this.value = value; this.type = type; }
        private boolean live() { return !closed && windows.get(value.id) == value; }
        @Override public UUID id() { return value.id; }
        @Override public UUID playerId() { return host.root().playerId(); }
        @Override public FloatingMenuState state() { return !live() ? FloatingMenuState.CLOSED : hidden(value.id) || paused() ? FloatingMenuState.SUSPENDED : FloatingMenuState.ACTIVE; }
        @Override public int depth() { return live() ? value.pages.size() - 1 : -1; }
        @Override public OptionalDouble horizontalFraction() { return host.root().horizontalFraction(); }
        @Override public void refresh() { host.execute(() -> { if (live()) changed(); }); }
        @Override public void reanchor() { if (live()) host.root().reanchor(); }
        @Override public void update(MenuScene scene) {
            validate(scene);
            host.execute(() -> {
                if (!live()) return;
                var old = value.pages.pop(); var oldScene = value.scene;
                value.pages.push(new Page(old.id(), old.content(), scene)); value.scene = scene;
                try { changed(); }
                catch (RuntimeException error) { value.pages.pop(); value.pages.push(old); value.scene = oldScene; throw error; }
            });
        }
        @Override public FloatingMenuHandle openChild(MenuScene scene) {
            host.requireThread("navigateWindow");
            validate(scene);
            if (!live() || !allowed(value.id)) throw new IllegalStateException("Window is closed or blocked");
            var previousScene = value.scene;
            var parent = value.pages.pop();
            value.pages.push(new Page(parent.id(), parent.content(), parent.override(), previousScene));
            value.pages.push(new Page(() -> scene)); value.scene = scene;
            try { changed(); }
            catch (RuntimeException | Error failure) {
                value.pages.pop(); value.pages.pop(); value.pages.push(parent); value.scene = previousScene;
                throw failure;
            }
            expirePage(value.id, parent.id());
            releaseExpiredSuspensions();
            if (focus.equals(value.id)) host.focusChanged();
            host.lifecycle(previousScene, this, FloatingMenuState.ACTIVE, FloatingMenuState.SUSPENDED, null);
            if (live()) host.lifecycle(scene, this, FloatingMenuState.CLOSED, FloatingMenuState.OPENING, null);
            if (live()) host.lifecycle(scene, this, FloatingMenuState.OPENING, FloatingMenuState.ACTIVE, null);
            return this;
        }
        @Override public <R> MenuWindowHandle<R> openWindow(MenuWindowSpec spec, Class<R> resultType,
                Function<MenuWindowContext<R>, MenuPanel> content, Consumer<MenuWindowResult<R>> result) { return open(value.id, spec, resultType, content, result); }
        @Override public void openPopover(UiKey source, Function<Runnable, MenuPanel> content) {
            host.requireThread("openPopover"); if (live()) host.popover(value.id, source, content);
        }
        @Override public void openPopover(MenuPopover content) {
            host.requireThread("openPopover"); if (live()) host.popover(value.id, content);
        }
        @Override public void closePopover() { host.execute(() -> { if (live()) host.closePopover(value.id, null); }); }
        @Override public void closePopover(MenuPopover expected) { host.execute(() -> { if (live()) host.closePopover(value.id, expected); }); }
        @Override public MenuInputLease suspend() { return host.suspend(value.id); }
        @Override public void back() { host.execute(() -> {
            if (!live() || host.cancelInput(value.id) || host.closePopover(value.id,null)) return;
            MenuWorkspaceSession.this.back(value.id);
        }); }
        @Override public void close() { host.execute(() -> { if (live()) requestClose(value.id, FloatingMenuCloseReason.USER); }); }
        @Override public void feedback(Component message, FloatingMenuFeedbackKind kind) { if (live() && focused()) host.root().feedback(message, kind); }
        @Override public boolean focused() { return live() && focus.equals(value.id); }
        @Override public void activate() { host.execute(() -> { if (live()) MenuWorkspaceSession.this.activate(value.id); }); }
        @Override public void minimize() { host.execute(() -> { if (live()) MenuWorkspaceSession.this.minimize(value.id); }); }
        @Override public void restore() { activate(); }
        @Override public void expanded(boolean expanded) { host.execute(() -> { if (live()) { value.expanded = expanded; changed(); } }); }
        @Override public void position(MenuWindowSpec.Position position) { host.execute(() -> { if (live()) { value.position = Objects.requireNonNull(position); changed(); } }); }
        @Override public void complete(T result) { host.execute(() -> { if (live()) { value.outcome = MenuWindowResult.completed(type.cast(result)); requestClose(value.id, FloatingMenuCloseReason.USER); } }); }
        @Override public BooleanSupplier current() { UUID page = live() ? value.page().id() : null; return () -> live() && value.page().id().equals(page); }
    }
}
