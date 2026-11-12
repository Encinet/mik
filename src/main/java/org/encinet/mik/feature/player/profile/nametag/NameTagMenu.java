package org.encinet.mik.feature.player.profile.nametag;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.player.profile.PlayerNameTag;
import org.encinet.mik.feature.player.profile.PlayerNameTagRenderer;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.i18n.RichArg;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.encinet.mik.shared.role.RolePermissions;
import org.encinet.mik.shared.util.NameMetaRenderer;
import org.encinet.mik.shared.util.PlayerNames;
import org.encinet.mik.shared.util.ShutdownSequence;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;


/** Main-thread workspace; sent chat changes one preview, explicit confirmation persists it. */
final class NameTagMenu implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final LanguageService text;
    private final PlayerNameTagRenderer nameTags;
    private final NameTagStore store;
    private final NameTagDirectory directory;
    private List<NameTagStyle> styles = List.of();
    private boolean loadingStyles;
    private boolean stylesLoaded;
    private boolean stylesFailed;
    private long refreshStylesAt;
    private final Consumer<UUID> changed;
    private final Function<Player, Component> baseName;
    private final Set<Workspace> workspaces = new HashSet<>();
    private volatile boolean closed;

    private static final class Workspace {
        final UUID playerId;
        final Runnable redraw;
        FloatingMenuHandle handle;
        int page;
        NameTagEdit edit;
        MenuInputLease input;
        NameTagField inputField;
        Object inputToken;
        Component notice;

        Workspace(UUID playerId, Runnable redraw) {
            this.playerId = playerId;
            this.redraw = redraw;
        }
    }

    NameTagMenu(JavaPlugin plugin, LanguageService text, PlayerNameTagRenderer nameTags, NameTagStore store, NameTagDirectory directory, Consumer<UUID> changed, Function<Player, Component> baseName) {
        this.plugin = plugin;
        this.text = text;
        this.nameTags = nameTags;
        this.store = store;
        this.directory = directory;
        this.changed = changed;
        this.baseName = baseName;
    }

    void enable() { Bukkit.getPluginManager().registerEvents(this, plugin); }

    void open(Player viewer) {
        if (closed) return;
        Workspace workspace = workspaces.stream().filter(candidate -> candidate.playerId.equals(viewer.getUniqueId())
                && candidate.redraw == null).findFirst().orElseGet(() -> workspace(viewer, null));
        refreshStyles(false);
        workspace.handle = FloatingMenus.present(viewer, scene(viewer, workspace));
    }

    MenuPanel panel(Player viewer, Runnable redraw) {
        Workspace workspace = workspace(viewer, redraw);
        refreshStyles(false);
        return new MenuPanel() {
            @Override public MenuScene render() { return scene(viewer, workspace); }
            @Override public void attach(FloatingMenuHandle handle) { workspace.handle = handle; }
            @Override public void close() { forget(workspace); }
        };
    }

    private Workspace workspace(Player viewer, Runnable redraw) {
        Workspace workspace = new Workspace(viewer.getUniqueId(), redraw);
        workspaces.add(workspace);
        return workspace;
    }

    private void show(Player viewer, Workspace workspace) {
        if (closed || !workspaces.contains(workspace)) return;
        if (workspace.redraw != null) { workspace.redraw.run(); return; }
        MenuScene scene = scene(viewer, workspace);
        if (workspace.handle == null) workspace.handle = FloatingMenus.present(viewer, scene);
        else if (visible(workspace) || workspace.handle.state() == FloatingMenuState.SUSPENDED) workspace.handle.update(scene);
    }

    private MenuScene scene(Player viewer, Workspace workspace) {
        var own = store.read(viewer.getUniqueId());
        PlayerNameTag effective = nameTags.current(viewer);
        List<NameTagStyle> profiles = profiles(viewer);
        List<NameTagComposition.Row> rows = profiles.stream()
                .map(profile -> new NameTagComposition.Row(profile.id(), identity(profile))).toList();
        var language = text.language(viewer);
        Component previousPage = label(viewer, Message.NAMETAG_PREVIOUS), nextPage = label(viewer, Message.NAMETAG_NEXT);
        IntConsumer selectPage = index -> { workspace.page = index; show(viewer, workspace); };
        UiNode gallery = NameTagComposition.gallery(rows, label(viewer, Message.NAMETAG_COPY_HINT),
                id -> (player, _) -> copyStyle(player, id), workspace.page,
                page -> MenuScene.screen("nametag-pages").content(UiTemplates.pagination("pages"))
                        .pagination("pages", page, previousPage,
                                text.text(language, Message.NAMETAG_PAGE, NamedTextColor.WHITE, page.index() + 1, page.count()), nextPage,
                                selectPage).build().content(), selectPage);
        var fields = Ui.form().key("fields");
        for (NameTagField field : NameTagField.values()) fields.field(field.name().toLowerCase(java.util.Locale.ROOT),
                Component.text(fieldLabel(viewer, field), NamedTextColor.AQUA), field(viewer, workspace, own, effective, field));
        var refresh = Ui.button(loadingStyles ? UiIcons.LOADING : UiIcons.REFRESH, label(viewer, Message.NAMETAG_REFRESH)).key("refresh")
                .primary((player, _) -> { refreshStyles(true); show(player, workspace); });
        if (loadingStyles) refresh.disabled(label(viewer, Message.NAMETAG_STYLES_LOADING));
        MenuScene.Builder menu = MenuScene.screen("nametag", label(viewer, Message.NAMETAG_TITLE))
                .stableAnchor().content(NameTagComposition.content(fields.build(), gallery, refresh.build()))
                .lifecycle((player, handle, previous, current, _) -> {
                    if (current == FloatingMenuState.SUSPENDED) endEdit(workspace);
                    else if (current == FloatingMenuState.CLOSING || current == FloatingMenuState.CLOSED)
                        forget(workspace);
                    else if (current == FloatingMenuState.OPENING && previous == FloatingMenuState.SUSPENDED) {
                        workspace.handle = handle;
                        show(player, workspace);
                    }
                })
                .refreshWhenChanged(20, player -> {
                    refreshStyles(false);
                    return List.of(profiles(player), loadingStyles, stylesFailed, text.language(player),
                            RolePermissions.isMember(player), nameTags.current(player), store.read(player.getUniqueId()));
                },
                        (player, _) -> show(player, workspace));
        menu.information("mine", label(viewer, Message.NAMETAG_MINE)).region("heading");
        Component current = nameTags.render(viewer, baseName.apply(viewer), effective).component();
        menu.information("current", label(viewer, Message.NAMETAG_CURRENT).append(Component.newline())
                        .append(current)).region("current");
        NameTagEdit edit = workspace.edit;
        if (edit != null) {
            Component unavailable = unavailable(viewer, workspace, own, edit.field());
            var confirm = menu.control("confirm", label(viewer, Message.NAMETAG_CONFIRM)).region("edit-actions")
                    .primary((player, _) -> { if (workspace.edit == edit) confirm(player, workspace); });
            if (unavailable != null) confirm.disabled(unavailable);
            else if (!edit.canSave()) confirm.disabled(edit.saving() ? label(viewer, Message.NAMETAG_SAVING)
                    : !edit.valid() ? firstFailure(viewer, edit) : label(viewer, Message.NAMETAG_NO_CHANGES));
            var cancel = menu.control("cancel", label(viewer, Message.CANCEL)).region("edit-actions")
                    .primary((player, _) -> {
                        if (closed || !workspaces.contains(workspace) || workspace.edit != edit || edit.saving()) return;
                        endEdit(workspace); workspace.notice = null; show(player, workspace);
                    });
            if (edit.saving()) cancel.disabled(label(viewer, Message.NAMETAG_SAVING));
            PlayerNameTag preview = edit.preview(effective);
            Component result = nameTags.render(viewer, baseName.apply(viewer), preview).component();
            menu.information("preview", label(viewer, Message.NAMETAG_PREVIEW_LABEL).append(Component.newline())
                            .append(result)).region("current");
            Component status = edit.saving() ? label(viewer, Message.NAMETAG_SAVING)
                    : !edit.valid() ? firstFailure(viewer, edit) : workspace.inputField == null ? null
                    : label(viewer, Message.NAMETAG_EDIT_PROMPT, fieldLabel(viewer, workspace.inputField));
            if (status != null) menu.information("input-status", status).region("edit-status").textWidth(FloatingMenuTextWidth.EXPANDED);
            if (edit.removesCustomValue())
                menu.information("clear-hint", label(viewer, Message.NAMETAG_CLEAR_HINT))
                        .region("edit-status").textWidth(FloatingMenuTextWidth.EXPANDED);
        }
        if (workspace.notice != null) menu.information("notice", workspace.notice).region("edit-status").textWidth(FloatingMenuTextWidth.EXPANDED);
        menu.information("players", label(viewer, Message.NAMETAG_PLAYERS)).region("players-heading");
        if (loadingStyles) menu.information("loading", label(viewer, Message.NAMETAG_STYLES_LOADING)).region("gallery-status");
        else if (stylesFailed) menu.information("load-failed", label(viewer, Message.NAMETAG_STYLES_FAILED)).region("gallery-status");
        else if (stylesLoaded && profiles.isEmpty()) menu.information("empty", label(viewer, Message.NAMETAG_NO_PLAYERS)).region("gallery-status");
        if (workspace.redraw == null) menu.back(label(viewer, Message.BACK_TO_MAIN)).region("navigation");
        return menu.build();
    }

    private UiNode field(Player viewer, Workspace workspace, Optional<NameTagStore.Snapshot> own, PlayerNameTag effective, NameTagField field) {
        NameTagEdit edit = workspace.edit;
        boolean editing = edit != null && edit.field() == field;
        String raw = editing ? edit.value(field) : field.value(effective);
        var input = Ui.textField(raw).preview(value -> render(viewer, value)).placeholder(label(viewer, Message.NAMETAG_UNSET))
                .key("value").editing(editing)
                .primary((player, _) -> { if (workspace.edit == edit) begin(player, workspace, field, null); });
        Component unavailable = unavailable(viewer, workspace, own, field);
        if (unavailable != null) input.disabled(unavailable);
        if (editing ? !raw.isEmpty() : own.map(snapshot -> !snapshot.version(field).isEmpty()).orElse(false)) {
            var clear = Ui.button(label(viewer, Message.NAMETAG_CLEAR)).key("clear")
                    .primary((player, _) -> { if (workspace.edit == edit) begin(player, workspace, field, ""); });
            if (unavailable != null) clear.disabled(unavailable);
            input.actions(clear.build());
        }
        return input.build();
    }

    private Component unavailable(Player viewer, Workspace workspace, Optional<NameTagStore.Snapshot> own, NameTagField field) {
        if (!RolePermissions.isMember(viewer)) return label(viewer, Message.NAMETAG_NO_PERMISSION);
        if (own.isEmpty()) return label(viewer, Message.NAMETAG_USER_DATA_LOAD_FAILED);
        if (workspace.edit != null && workspace.edit.saving()) return label(viewer, Message.NAMETAG_SAVING);
        if (workspace.edit != null && workspace.edit.field() != field) return label(viewer, Message.NAMETAG_FINISH_FIELD);
        return null;
    }

    private void copyStyle(Player viewer, UUID id) {
        profiles(viewer).stream().filter(profile -> profile.id().equals(id)).findFirst()
                .ifPresent(profile -> sendStyle(viewer, profile));
    }

    void sendStyle(Player viewer, NameTagStyle profile) {
        viewer.sendMessage(label(viewer, Message.NAMETAG_COPY_SENT, profile.name()));
        for (NameTagField field : NameTagField.values()) {
            String raw = field.value(profile.tag());
            viewer.sendMessage(Component.text(fieldLabel(viewer, field), NamedTextColor.AQUA).append(Component.space())
                    .append(raw.isEmpty() ? label(viewer, Message.NAMETAG_UNSET) : rawWithCopy(viewer, raw)));
        }
    }

    private boolean begin(Player viewer, Workspace workspace, NameTagField field, String candidate) {
        if (closed || !workspaces.contains(workspace)) return false;
        var own = store.read(viewer.getUniqueId());
        Component unavailable = unavailable(viewer, workspace, own, field);
        if (unavailable != null) { workspace.notice = unavailable; show(viewer, workspace); return false; }
        if (workspace.edit == null) workspace.edit = new NameTagEdit(field, own.orElseThrow(), nameTags.current(viewer));
        NameTagEdit edit = workspace.edit;
        boolean firstInput = workspace.inputField != field || workspace.input == null || !workspace.input.active();
        if (firstInput) {
            endInput(workspace);
            Object token = new Object();
            workspace.inputToken = token;
            workspace.inputField = field;
            workspace.input = FloatingMenus.beginChatInput(viewer, raw -> {
                if (workspace.edit != edit || workspace.inputToken != token || edit.saving()) return;
                edit.input(raw);
                workspace.notice = null;
                show(viewer, workspace);
            }, () -> {
                if (workspace.edit != edit || workspace.inputToken != token) return;
                endEdit(workspace);
                workspace.notice = label(viewer, Message.NAMETAG_EDIT_ENDED);
                show(viewer, workspace);
            });
        }
        if (candidate != null) edit.input(candidate);
        workspace.notice = null;
        show(viewer, workspace);
        if (firstInput && !edit.value(field).isEmpty()) viewer.sendMessage(rawWithCopy(viewer, edit.value(field)));
        return edit.valid();
    }

    private void confirm(Player viewer, Workspace workspace) {
        if (closed || !workspaces.contains(workspace)) return;
        NameTagEdit edit = workspace.edit;
        if (edit == null || unavailable(viewer, workspace, store.read(viewer.getUniqueId()), edit.field()) != null
                || (workspace.input != null && !workspace.input.active()) || !edit.beginSave()) return;
        workspace.notice = null;
        show(viewer, workspace);
        store.save(viewer.getUniqueId(), edit.changes()).whenComplete((_, error) -> {
            if (closed || !plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (closed) return;
                if (error == null) {
                    try { changed.accept(viewer.getUniqueId()); }
                    catch (RuntimeException refreshError) {
                        plugin.getLogger().log(java.util.logging.Level.WARNING, "Could not refresh the saved name tag", refreshError);
                    }
                }
                if (!workspaces.contains(workspace) || workspace.edit != edit || !viewer.isOnline()) return;
                if (error == null) {
                    endEdit(workspace);
                    refreshStyles(true);
                    workspace.notice = label(viewer, Message.NAMETAG_SET_SUCCESS, editLabel(viewer, edit));
                } else {
                    edit.failed();
                    Throwable cause = error;
                    while (cause.getCause() != null) cause = cause.getCause();
                    if (cause instanceof NameTagStore.Conflict) {
                        store.read(viewer.getUniqueId()).ifPresent(current -> edit.rebase(current, nameTags.current(viewer)));
                        workspace.notice = label(viewer, Message.NAMETAG_CONFLICT);
                    } else {
                        workspace.notice = label(viewer, Message.NAMETAG_SET_ERROR, editLabel(viewer, edit));
                        plugin.getLogger().warning("Failed to save name tag for " + viewer.getUniqueId() + ": " + cause.getMessage());
                    }
                }
                show(viewer, workspace);
            });
        });
    }

    private void endEdit(Workspace workspace) {
        workspace.edit = null;
        endInput(workspace);
    }

    private void endInput(Workspace workspace) {
        workspace.inputToken = null;
        workspace.inputField = null;
        MenuInputLease input = workspace.input;
        workspace.input = null;
        if (input != null) input.close();
    }

    private void forget(Workspace workspace) {
        if (!workspaces.remove(workspace)) return;
        endEdit(workspace);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        for (Workspace workspace : Set.copyOf(workspaces))
            if (workspace.playerId.equals(event.getPlayer().getUniqueId())) forget(workspace);
    }

    private static boolean visible(Workspace workspace) {
        return workspace.handle != null && (workspace.handle.state() == FloatingMenuState.OPENING || workspace.handle.state() == FloatingMenuState.ACTIVE);
    }

    private void refreshStyles(boolean force) {
        if (closed || loadingStyles || (!force && System.nanoTime() < refreshStylesAt)) return;
        loadingStyles = true;
        refreshStylesAt = System.nanoTime() + java.time.Duration.ofSeconds(30).toNanos();
        directory.load().whenComplete((loaded, error) -> {
            if (closed || !plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (closed) return;
                loadingStyles = false;
                stylesFailed = error != null;
                if (error == null) { styles = loaded; stylesLoaded = true; }
                else plugin.getLogger().log(java.util.logging.Level.WARNING, "Could not load player name tag styles", error);
                for (Workspace workspace : Set.copyOf(workspaces)) {
                    Player viewer = Bukkit.getPlayer(workspace.playerId);
                    if (viewer != null && visible(workspace)) show(viewer, workspace);
                }
            });
        });
    }

    private List<NameTagStyle> profiles(Player viewer) {
        var result = new HashMap<UUID, NameTagStyle>();
        styles.forEach(style -> result.put(style.id(), new NameTagStyle(style.id(),
                PlayerNames.resolve(style.id(), style.name()), style.tag())));
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (!viewer.canSee(player)) { result.remove(id); continue; }
            // Loaded users also remove stale persisted entries after a clear operation.
            if (store.read(id).isPresent()) {
                result.remove(id);
                store.style(id, player.getName()).ifPresent(style -> result.put(id, style));
            }
        }
        return result.values().stream().sorted(Comparator.comparing(NameTagStyle::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(NameTagStyle::id)).toList();
    }

    private Component identity(NameTagStyle profile) {
        Player online = Bukkit.getPlayer(profile.id());
        var subject = online != null ? online : Bukkit.getOfflinePlayer(profile.id());
        return nameTags.render(subject,
                Component.text(profile.name(), NamedTextColor.WHITE), profile.tag()).component();
    }

    private String editLabel(Player viewer, NameTagEdit edit) {
        return fieldLabel(viewer, edit.field());
    }

    private Component firstFailure(Player viewer, NameTagEdit edit) {
        return failure(viewer, edit.field(), edit.failure());
    }

    private Component failure(Player viewer, NameTagField field, NameTagFormat.Failure failure) {
        return switch (failure.problem()) {
            case TOO_LONG -> text.text(viewer, Message.NAMETAG_TOO_LONG, NamedTextColor.RED, fieldLabel(viewer, field), NameTagFormat.MAX_LENGTH);
            case UNSUPPORTED_PLACEHOLDER -> text.rich(viewer, Message.NAMETAG_UNSUPPORTED_PLACEHOLDER_RICH, NamedTextColor.RED,
                    RichArg.component("supported", Component.text("%player_...%", NamedTextColor.AQUA)),
                    RichArg.component("unsupported", Component.text("%" + failure.detail() + "%", NamedTextColor.YELLOW)));
            case INVALID -> text.text(viewer, Message.NAMETAG_INPUT_INVALID, NamedTextColor.RED);
        };
    }

    private static Component render(Player viewer, String raw) {
        try { return NameMetaRenderer.deserialize(viewer, raw); }
        catch (RuntimeException error) { return NameMetaRenderer.fallback(viewer, raw); }
    }

    private Component rawWithCopy(Player viewer, String raw) {
        return Component.text(raw, NamedTextColor.YELLOW).append(Component.space()).append(label(viewer, Message.NAMETAG_COPY_LABEL)
                .clickEvent(ClickEvent.copyToClipboard(raw)));
    }

    private String fieldLabel(Player viewer, NameTagField field) {
        return text.t(viewer, field == NameTagField.PREFIX ? Message.NAMETAG_LEADING_LABEL : Message.NAMETAG_TRAILING_LABEL);
    }

    private Component label(Player viewer, Message message, Object... args) { return text.text(viewer, message, NamedTextColor.WHITE, args); }

    @Override public void close() {
        closed = true;
        HandlerList.unregisterAll(this);
        var remaining = Set.copyOf(workspaces);
        workspaces.clear();
        ShutdownSequence shutdown = new ShutdownSequence();
        for (Workspace workspace : remaining) {
            shutdown.attempt("name tag input for " + workspace.playerId, () -> endEdit(workspace));
            if (workspace.handle != null) shutdown.attempt("name tag menu for " + workspace.playerId, workspace.handle::close);
        }
        shutdown.finish("name tag workspace");
    }
}
