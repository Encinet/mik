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
                  Component blocked, Component move) { }
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
        default void move(UUID owner) { }
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
        boolean closed;
        UiTransform transform = UiTransform.IDENTITY;
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
    private int trayPage;
    private MenuScene rootScene;
    private boolean rootMinimized;
    private UUID minimizedFocus;
    private UUID moving;
    private MenuWindowSpec.Position rootPosition = MenuWindowSpec.Position.CENTER;
    private UiTransform rootTransform = UiTransform.IDENTITY;
    private int suspendCount;
    private ClosePlan closing;
    private Set<UUID> presented = Set.of();
    private final List<Suspension> suspensions = new ArrayList<>();

    MenuWorkspaceSession(UUID root, Host host) { this.root = root; this.focus = root; this.host = host; }
    UUID focused() { return focus; }
    boolean paused() { return suspendCount > 0; }
    boolean empty() { return windows.isEmpty(); }
    boolean minimized() { return rootMinimized; }
    MenuWindowPolicy policy(UUID id) {
        if (!root.equals(id)) return window(id).spec.policy();
        return rootScene == null ? MenuWindowPolicy.STANDARD : rootScene.windowPolicy();
    }
    boolean movable(UUID id) { return !rootMinimized && contains(id) && allowed(id) && !hidden(id) && policy(id).movable(); }
    UUID page(UUID id) { return contains(id) ? pageId(id) : null; }
    UiTransform transform(UUID id) { return root.equals(id) ? rootTransform : window(id).transform; }
    boolean hasMovedWindows() { return !rootTransform.equals(UiTransform.IDENTITY)
            || windows.values().stream().anyMatch(value -> !value.transform.equals(UiTransform.IDENTITY)); }
    void transform(UUID id, UiTransform value, boolean publish) {
        if (!contains(id)) return;
        if (root.equals(id)) rootTransform = Objects.requireNonNull(value); else window(id).transform = Objects.requireNonNull(value);
        if (publish) changed();
    }
    void move(UUID id) { if (movable(id)) host.move(id); }
    void moving(UUID id) { moving(id,true); }
    void moving(UUID id, boolean publish) { moving = id; if (publish) changed(); }
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
        if (rootMinimized && !root.equals(id)) return true;
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
        if (rootMinimized) return Set.copyOf(result);
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
        if (rootMinimized || hidden(owner)) {
            if (closing != null && owner.equals(closing.waitingFor)) restore(owner);
            else throw new IllegalStateException("Restore the owner before opening another window");
        }
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
        if (rootMinimized) { restore(id); return; }
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
    String sourceScope(UUID id) {
        if (!root.equals(id)) return window(id).scope().value();
        return MAIN.value();
    }

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
            rootScene = authored;
            if (!policy(root).minimizable()) rootMinimized = false;
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
            var viewport = authored.viewport();
            if (rootMinimized) {
                presented = Set.of(root);
                return authored.toBuilder().withoutTitle().withoutDecorations().decorationScope(null).popover(null)
                        .content(Ui.group(tray(labels,Set.of())).key(MAIN).transform(UiTransform.IDENTITY.translated(0,-64,0)).build()).build();
            }
            // Ownership and movement operate on a logical group, never on an enclosing visual plane.
            UiNode rootContent = MenuWindowPresentation.workspace(authored.titleVisible() ? authored.title() : Component.empty(),authored.content(),tools(root,labels));
            nodes.add(Ui.group(rootContent).key(MAIN).transform(rootTransform).build());
            viewports.add(viewport); positions.add(rootPosition);
            int layer = 1;
            for (UUID id : List.copyOf(order)) {
                var value = windows.get(id);
                if (value == null || hidden(id) || !reading.contains(id)) continue;
                value.scene = validate(value.page().scene());
                nodes.add(Ui.group(MenuWindowPresentation.floating(value.scene.title(),
                                Ui.column().key("page-"+value.page().id()).child(value.scene.content()).build(),tools(id,labels),
                                focus.equals(id),() -> activate(id)))
                        .key(value.scope()).transform(value.transform.translated(0,0,32 * layer++)).build());
                viewports.add(value.scene.viewport());
                positions.add(value.position);
            }
            boolean showTray = !windows.isEmpty();
            if (showTray) {
                nodes.add(Ui.group(tray(labels,reading))
                        .key("window-switcher").transform(UiTransform.IDENTITY.translated(0,-160,32 * layer)).build());
                viewports.add(authored.viewport()); positions.add(MenuWindowSpec.Position.BOTTOM_LEFT);
            }
            var stack = Ui.layout(new MenuWindowLayout(viewports, positions,showTray)).key("menu-workspace");
            nodes.forEach(stack::child);
            presented = reading;
            return authored.toBuilder().withoutTitle().decorationScope(MAIN).viewport(viewport).content(stack.build()).build();
        } finally { composing = false; }
    }

    /** Stable cards retain instance identity; the tray never renders miniature interactive content. */
    private UiNode tray(Labels labels, Set<UUID> reading) {
        var cards = new ArrayList<UiNode>();
        if (rootMinimized) cards.add(card(root,rootScene.title(),true,reading,labels));
        if (!rootMinimized) for (var value : windows.values()) cards.add(card(value.id,value.scene == null ? Component.empty() : value.scene.title(),hidden(value.id),reading,labels));
        var strip = Ui.pagedFlow(1).key("window-tray").gaps(UiLength.ui(6),UiLength.ui(6))
                .items(cards).page(trayPage,page -> { trayPage = page; changed(); }).build();
        return Ui.surface(strip).key("tray-surface").style(UiTheme.FLOATING_WINDOW).build();
    }

    private UiNode card(UUID id, Component title, boolean minimized, Set<UUID> reading, Labels labels) {
        var activate = Ui.button(title.equals(Component.empty()) ? labels.restore() : title)
                .key("restore-"+id).textWidth(100).maxLines(1).selected(!rootMinimized && focus.equals(id))
                .primary((_,_) -> restore(id));
        if (minimized || !reading.contains(id)) activate.tooltip(labels.restore());
        if (!activatable(id)) activate.disabled(labels.blocked());
        var card = Ui.row().key("card-"+id).gap(UiLength.ui(4)).child(activate.build());
        if (minimized) card.child(Ui.button(UiIcons.CROSS,labels.close()).key("close-"+id)
                .primary((_,_) -> requestClose(id,FloatingMenuCloseReason.USER)).build());
        return card.build();
    }

    private UiNode tools(UUID id, Labels labels) {
        var value = windows.get(id);
        var policy = policy(id);
        var tools = Ui.layout(UiLayouts.wrap(UiLength.ui(4),UiLength.ui(4))).key("window-tools");
        if (value != null && value.pages.size() > 1)
            tools.child(Ui.button(UiIcons.CHEVRON_LEFT,labels.back()).key("back").primary((_,_) -> back(id)).build());
        if (policy.minimizable()) {
            var minimize = Ui.button(UiIcons.REMOVE, labels.minimize()).key("minimize")
                    .primary((_, _) -> minimize(id));
            if (!canMinimize(id)) minimize.disabled(labels.blocked());
            tools.child(minimize.build());
        }
        if (policy.movable()) {
            tools.child(Ui.button(UiIcons.MOVE,labels.move()).key("move").selected(id.equals(moving)).primary((_,_) -> move(id)).build());
        }
        var extra = value == null ? null : value.page().content().headerActions();
        if (extra != null) tools.child(extra);
        // The workspace retains feature-owned navigation and dismissal; detached windows own their chrome.
        if (value != null) tools.child(Ui.button(UiIcons.CROSS, labels.close()).key("close")
                .primary((_, _) -> requestClose(id, FloatingMenuCloseReason.USER)).build());
        return tools.build();
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

    void minimize(UUID id) {
        if (!contains(id) || !allowed(id) || !canMinimize(id)) return;
        if (root.equals(id)) {
            if (rootMinimized) return;
            minimizedFocus = focus; rootMinimized = true; focus = root;
            host.focusChanged(); changed();
            host.lifecycle(rootScene,host.root(),FloatingMenuState.ACTIVE,FloatingMenuState.SUSPENDED,null);
            return;
        }
        var value = window(id);
        value.minimized = true;
        if (descendant(focus, id)) { focus = value.owner; restoreFocus(); host.focusChanged(); }
        changed();
    }
    private boolean canMinimize(UUID id) {
        return policy(id).minimizable() && windows.values().stream().noneMatch(child -> descendant(child.id, id)
                && child.spec.modality() != MenuWindowSpec.Modality.NONE);
    }
    void restore(UUID id) {
        if (!contains(id)) return;
        boolean wasMinimized = rootMinimized;
        rootMinimized = false;
        activate(wasMinimized && root.equals(id) && minimizedFocus != null && contains(minimizedFocus) ? minimizedFocus : id);
        changed();
        if (wasMinimized) {
            host.lifecycle(rootScene,host.root(),FloatingMenuState.SUSPENDED,FloatingMenuState.OPENING,null);
            host.lifecycle(rootScene,host.root(),FloatingMenuState.OPENING,FloatingMenuState.ACTIVE,null);
        }
    }
    void position(UUID id, MenuWindowSpec.Position value) {
        if (!movable(id)) return;
        Objects.requireNonNull(value);
        if (root.equals(id)) { rootPosition = value; rootTransform = UiTransform.IDENTITY; }
        else { window(id).position = value; window(id).transform = UiTransform.IDENTITY; }
        changed();
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
                review(id,windows.get(id).spec.policy().closeRequested());
                return;
            }
            if (target.equals(root) && !approved.contains(root)) {
                review(root,policy(root).closeRequested());
                return;
            }
            closing = null;
            if (target.equals(root)) {
                try { close(reason); }
                finally { host.rootClosed(reason); }
            }
            else forceClose(target, reason);
        }
        private void review(UUID id, Consumer<MenuCloseRequest> handler) {
            waitingFor = id;
            var decision = new MenuCloseRequest() {
                    private boolean resolved;
                    public FloatingMenuCloseReason reason() { return reason; }
                    public boolean active() { return !resolved && closing == ClosePlan.this && contains(id); }
                    public void approve() { host.execute(() -> { if (!active()) return; resolved = true; approved.add(id); waitingFor = null; next(); }); }
                    public void cancel() { host.execute(() -> { if (!active()) return; resolved = true; ClosePlan.this.cancel(); }); }
            };
            try { handler.accept(decision); }
            catch (RuntimeException error) { closing = null; host.failure(error); }
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
        @Override public void restore() { host.execute(() -> { if (live()) MenuWorkspaceSession.this.restore(value.id); }); }
        @Override public void position(MenuWindowSpec.Position position) { host.execute(() -> { if (live()) MenuWorkspaceSession.this.position(value.id,position); }); }
        @Override public void move() { host.execute(() -> { if (live()) MenuWorkspaceSession.this.move(value.id); }); }
        @Override public void complete(T result) { host.execute(() -> { if (live()) { value.outcome = MenuWindowResult.completed(type.cast(result)); requestClose(value.id, FloatingMenuCloseReason.USER); } }); }
        @Override public BooleanSupplier current() { UUID page = live() ? value.page().id() : null; return () -> live() && value.page().id().equals(page); }
    }
}
