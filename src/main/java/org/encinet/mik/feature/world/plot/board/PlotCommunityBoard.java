package org.encinet.mik.feature.world.plot.board;

import org.encinet.mik.shared.menu.MenuInputForm;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.FloatingMenuAppearance;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.feature.world.plot.PlotMenuLayouts;
import org.encinet.mik.feature.world.plot.PlotCardList;
import org.encinet.mik.shared.menu.FloatingMenuContext;
import org.encinet.mik.shared.menu.FloatingMenus;
import org.encinet.mik.shared.menu.MenuDialogs;
import org.encinet.mik.shared.menu.MenuSearch;
import org.encinet.mik.shared.menu.ui.UiKey;
import org.encinet.mik.shared.util.ShutdownSequence;
import org.encinet.mik.shared.role.RolePermissions;
import org.encinet.mik.feature.world.plot.Plot;
import org.encinet.mik.feature.world.plot.PlotDataPaths;
import org.encinet.mik.feature.world.plot.PlotGeometry;
import org.encinet.mik.feature.world.plot.PlotNoticePublisher;
import org.encinet.mik.feature.world.plot.PlotProblem;
import org.encinet.mik.feature.world.plot.PlotRegistry;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Owns the community board's persistence, game menus, commands and public snapshot. */
public final class PlotCommunityBoard {
    private static final int NOTICE_AUTO_RADIUS = 64;

    private final JavaPlugin plugin;
    private final LanguageService language;
    private final PlotRegistry registry;
    private final PlotBoardStore boardStore;
    private final PlotBoardNotifications boardNotifications;
    private final ExecutorService reads = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("mik-plot-board-read-",0).factory());
    private volatile JsonObject communityBoard;
    private long boardRevision;
    private volatile boolean closed;
    private final PlotBoardRecordMenus recordMenus;

    public PlotCommunityBoard(JavaPlugin plugin, LanguageService language, PlotNoticePublisher notices,
                              PlotRegistry registry) {
        this.plugin = plugin;
        this.language = language;
        this.registry = registry;
        boardStore = new PlotBoardStore(PlotDataPaths.in(
                plugin.getDataFolder().toPath()).boardDatabase());
        boardNotifications = new PlotBoardNotifications(plugin, language, notices);
        recordMenus = new PlotBoardRecordMenus(plugin, language, () -> boardRevision, reads, boardStore::readFor, this::boardEntryDefinition);
    }

    public void open() throws Exception {
        boardStore.open();
        boardNotifications.enable();
        reloadCommunityBoard();
    }

    public void close() throws SQLException {
        closed = true;
        var shutdown = new ShutdownSequence();
        shutdown.attempt("board record menus", recordMenus::close);
        shutdown.attempt("board reads", reads::shutdownNow);
        shutdown.attempt("board notifications", boardNotifications::disable);
        shutdown.attempt("board storage", boardStore::close);
        shutdown.finish("plot community board");
    }

    private void reloadCommunityBoard() throws SQLException {
        JsonObject publicBoard = new JsonObject();
        publicBoard.add("notices", boardStore.listPublic());
        communityBoard = publicBoard;
        boardRevision++;
        boardNotifications.publish(boardStore.listAlerts());
    }

    public void openMenu(Player player) {
        openMenu(player, 0);
    }

    private void openMenu(Player player, int requestedPage) {
        FloatingMenus.present(player, boardDefinition(player, requestedPage));
    }

    private MenuScene boardDefinition(Player player, int requestedPage) {
        long revision = boardRevision;
        List<JsonObject> records = boardRecords();
        var cards = records.stream().map(record -> {
            String id = record.get("id").getAsString();
            Message kind = record.get("kind").getAsString().equals("objection")
                    ? Message.PLOT_BOARD_KIND_OBJECTION : Message.PLOT_BOARD_KIND_NOTICE;
            Component label = Component.text(t(player,kind) + " · " + t(player,boardStatus(record.get("status").getAsString())),NamedTextColor.AQUA)
                    .append(Component.newline()).append(Component.text(record.get("subject").getAsString(),NamedTextColor.WHITE));
            return new PlotCardList.Card(UiKey.named("record:" + id),label,(viewer,_) -> {
                if (boardRevision == revision) openBoardEntry(viewer,record);
            });
        }).toList();
        var list = PlotCardList.content(cards,Component.text(t(player,communityBoard == null
                ? Message.PLOT_BOARD_UNAVAILABLE : Message.PLOT_BOARD_EMPTY),NamedTextColor.WHITE),requestedPage,
                index -> { if (boardRevision == revision) openMenu(player,index); });
        MenuScene.Builder menu = PlotMenuLayouts.screen("plot-board",
                        FloatingMenuAppearance.DEFAULT, PlotMenuLayouts.browser(list))
                .refreshEvery(40, (p, handle) -> {
                    if (boardRevision != revision)
                        handle.update(boardDefinition(p, requestedPage));
                });
        menu.information("heading", Component.text(t(player, Message.PLOT_BOARD_MENU),
                NamedTextColor.GOLD)).region("heading").keepAccessible();
        menu.control("find", Component.text(t(player, Message.PLOT_MENU_FIND_RECORD), NamedTextColor.AQUA))
                .region("actions")
                .primary((p, _) -> FloatingMenus.search(p, MenuSearch.<JsonObject>builder(
                        Component.text(t(p, Message.PLOT_MENU_FIND_RECORD), NamedTextColor.GOLD),
                        Component.text(t(p, Message.PLOT_MENU_RECORD_SEARCH), NamedTextColor.AQUA))
                        .find(query -> CompletableFuture.supplyAsync(() -> {
                            try { return boardStore.searchPublic(query); }
                            catch (SQLException error) { throw new java.util.concurrent.CompletionException(error); }
                        },reads))
                        .key(record -> UiKey.item(record.get("id").getAsString()))
                        .label(record -> Component.text(record.get("subject").getAsString(),NamedTextColor.WHITE)
                                .append(Component.text(" · "+record.get("id").getAsString().substring(0,8),NamedTextColor.AQUA)))
                        .selected(this::openBoardEntry).build()));
        menu.back(Component.text(t(player, Message.BACK), NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        return menu.build();
    }

    private List<JsonObject> boardRecords() {
        JsonObject loaded = communityBoard;
        if (loaded == null) return List.of();
        List<JsonObject> records = new ArrayList<>();
        for (JsonElement element : loaded.getAsJsonArray("notices"))
            records.add(element.getAsJsonObject());
        return records.stream()
                .sorted((left, right) -> Long.compare(
                        right.get("createdAt").getAsLong(), left.get("createdAt").getAsLong()))
                .toList();
    }

    private JsonObject snapshotBoardRecord(String id) {
        JsonObject loaded = communityBoard;
        if (loaded != null) for (JsonElement element : loaded.getAsJsonArray("notices")) {
            JsonObject record = element.getAsJsonObject();
            if (record.get("id").getAsString().equals(id)) return record;
        }
        return null;
    }

    private JsonObject cachedBoardRecord(String id) {
        JsonObject cached = snapshotBoardRecord(id);
        if (cached != null) return cached;
        try { return boardStore.getPublic(id); }
        catch (SQLException error) {
            plugin.getLogger().log(Level.WARNING, "Could not read community board record", error);
            return null;
        }
    }

    private void openBoardEntry(Player player, JsonObject record) {
        if (!closed) recordMenus.open(player, PlotBoardRecord.unverified(record));
    }

    private MenuScene.Builder boardEntryDefinition(FloatingMenuContext<PlotBoardRecordState> context, PlotBoardRecordMenus.Reader reader) {
        Player player = context.player();
        PlotBoardRecordState state = context.state();
        JsonObject record = state.source().record();
        String id = record.get("id").getAsString();
        var locale = language.language(player);
        boolean moderator = RolePermissions.canModerate(player);
        Message kind = record.get("kind").getAsString().equals("objection")
                ? Message.PLOT_BOARD_KIND_OBJECTION : Message.PLOT_BOARD_KIND_NOTICE;
        String location = record.get("world").getAsString() + " "
                + record.get("x").getAsInt() + ", " + record.get("y").getAsInt() + ", " + record.get("z").getAsInt();
        Component metadata = Component.text(t(player, kind) + " · "
                        + t(player, boardStatus(record.get("status").getAsString())) + " · " + id.substring(0, 8), NamedTextColor.AQUA)
                .append(Component.newline()).append(Component.text(record.get("authorName").getAsString() + " · " + location, NamedTextColor.WHITE));
        Optional<Component> ruling = record.has("ruling") && !record.get("ruling").isJsonNull()
                ? Optional.of(Component.text(t(player, Message.PLOT_BOARD_RULING_DETAIL, record.get("ruling").getAsString()), NamedTextColor.WHITE))
                : Optional.empty();
        var reading = PlotBoardRecordContent.reading(Component.text(record.get("subject").getAsString(), NamedTextColor.GOLD),
                metadata, Component.text(boardDescription(player, record), NamedTextColor.WHITE), ruling, state.position(),
                next -> reader.read(context, state, next));
        MenuScene.Builder menu = PlotMenuLayouts.screen("plot-board-entry:" + id,
                        FloatingMenuAppearance.DEFAULT, PlotMenuLayouts.record(reading))
                .refreshEvery(40, (viewer, _) -> {
                    if (!reader.current(state)) return;
                    if (boardRevision != state.revision() || state.source().ownership().isEmpty() && !state.failed() && !reader.loading())
                        reader.reload(state);
                    else if (language.language(viewer) != locale || RolePermissions.canModerate(viewer) != moderator) context.redraw();
                });
        if (reader.loading() || state.source().ownership().isEmpty() && !state.failed())
            menu.information("loading", Component.text(t(player, Message.PLOT_BOARD_LOADING), NamedTextColor.WHITE)).region("status");
        if (state.failed()) {
            menu.information("unavailable", Component.text(t(player, Message.PLOT_BOARD_UNAVAILABLE), NamedTextColor.RED)).region("status");
            menu.control("retry", Component.text(t(player, Message.MENU_SEARCH_RETRY), NamedTextColor.AQUA)).region("actions")
                    .primary((_, _) -> reader.reload(state));
        }
        if (record.get("kind").getAsString().equals("notice")) {
            reader.control(state, menu, "feedback", Component.text(t(player, Message.PLOT_MENU_FEEDBACK), NamedTextColor.YELLOW))
                    .primary(reader.action(state, (p, _) -> textInput(p, Message.PLOT_MENU_FEEDBACK,
                            Message.PLOT_MENU_DESCRIPTION, "", 1000, true,
                            (viewer, value) -> {
                                if (!reader.actionable(state)) return;
                                perform(viewer, () -> postObjection(viewer, id, value));
                                openBoardEntry(viewer, record);
                            })));
        }
        if (record.has("relatedNoticeId") && !record.get("relatedNoticeId").isJsonNull())
            reader.control(state, menu, "related", Component.text(t(player, Message.PLOT_MENU_RELATED_NOTICE), NamedTextColor.AQUA))
                    .primary(reader.action(state, (_, _) -> reader.openRelated(state,
                            record.get("relatedNoticeId").getAsString())));
        if (record.get("kind").getAsString().equals("objection")
                && record.get("status").getAsString().equals("open")) {
            var ownership = state.source().ownership();
            boolean ownsFeedback = ownership.map(PlotBoardRecord.Ownership::feedbackAuthor).orElse(false);
            boolean ownDispute = ownership.map(PlotBoardRecord.Ownership::disputeAuthor).orElse(true);
            if (ownsFeedback)
                reader.control(state, menu, "withdraw", Component.text(t(player, Message.PLOT_MENU_WITHDRAW), NamedTextColor.RED))
                        .primary(reader.action(state, (p, _) -> MenuDialogs.openConfirm(plugin, p,
                                Component.text(t(p, Message.PLOT_MENU_WITHDRAW), NamedTextColor.RED),
                                Component.text(t(p, Message.PLOT_MENU_CONFIRM_WITHDRAW), NamedTextColor.WHITE),
                                Component.text(t(p, Message.PLOT_MENU_WITHDRAW), NamedTextColor.RED),
                                Component.text(t(p, Message.BACK), NamedTextColor.WHITE),
                                viewer -> { if (reader.actionable(state)) perform(viewer, () -> withdraw(viewer, id)); })));
            if (moderator && !ownDispute)
                reader.control(state, menu, "rule", Component.text(t(player, Message.PLOT_MENU_RULE), NamedTextColor.YELLOW))
                        .primary(reader.action(state, (p, _) -> textInput(p, Message.PLOT_MENU_RULE,
                                Message.PLOT_MENU_RULING, "", 1000, true,
                                (viewer, value) -> { if (reader.actionable(state)) perform(viewer,
                                        () -> rule(viewer, id, value)); })));
        }
        menu.back(Component.text(t(player, Message.BACK), NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        return menu;
    }

    public void show(Player player, String recordId) {
        JsonObject board = communityBoard;
        if (!recordId.isBlank()) {
            if (board == null && !fullBoardId(recordId)) {
                send(player, NamedTextColor.YELLOW, Message.PLOT_BOARD_UNAVAILABLE);
                return;
            }
            String id = boardId(recordId);
            if (board != null) for (JsonElement element : board.getAsJsonArray("notices")) {
                JsonObject item = element.getAsJsonObject();
                if (!item.get("id").getAsString().equals(id)) continue;
                showBoardEntry(player, item);
                return;
            }
            fetchBoardDetail(player, id);
            return;
        }
        if (board == null) {
            send(player, NamedTextColor.YELLOW, Message.PLOT_BOARD_UNAVAILABLE);
            return;
        }
        JsonArray entries = board.getAsJsonArray("notices");
        send(player, NamedTextColor.GOLD, Message.PLOT_BOARD_HEADER, entries.size());
        int shown = 0;
        for (JsonElement element : entries) {
            JsonObject item = element.getAsJsonObject();
            String kind = item.get("kind").getAsString();
            String status = item.get("status").getAsString();
            String subject = item.get("subject").getAsString().replace('\n', ' ');
            send(player, NamedTextColor.WHITE, Message.PLOT_BOARD_ITEM,
                    t(player, kind.equals("objection") ? Message.PLOT_BOARD_KIND_OBJECTION : Message.PLOT_BOARD_KIND_NOTICE),
                    item.get("id").getAsString().substring(0, 8),
                    t(player, boardStatus(status)),
                    subject.substring(0, Math.min(80, subject.length())));
            if (++shown >= 8) break;
        }
    }

    private void showBoardEntry(Player player, JsonObject item) {
        String id = item.get("id").getAsString();
        send(player, NamedTextColor.GOLD, Message.PLOT_BOARD_ITEM,
                t(player, item.get("kind").getAsString().equals("objection")
                        ? Message.PLOT_BOARD_KIND_OBJECTION : Message.PLOT_BOARD_KIND_NOTICE),
                id.substring(0, 8), t(player, boardStatus(item.get("status").getAsString())),
                item.get("subject").getAsString());
        send(player, NamedTextColor.WHITE, Message.PLOT_BOARD_DETAIL,
                item.get("authorName").getAsString(), item.get("world").getAsString(),
                item.get("x").getAsInt(), item.get("y").getAsInt(), item.get("z").getAsInt(),
                boardDescription(player, item));
        if (item.has("ruling") && !item.get("ruling").isJsonNull())
            send(player, NamedTextColor.GREEN, Message.PLOT_BOARD_RULING_DETAIL,
                    item.get("ruling").getAsString());
    }

    private void fetchBoardDetail(Player player, String id) {
        JsonObject record = cachedBoardRecord(id);
        if (record == null) {
            send(player, NamedTextColor.RED, Message.PLOT_BOARD_RECORD_INVALID);
            return;
        }
        showBoardEntry(player, record);
    }

    private static Message boardStatus(String status) {
        return switch (status) {
            case "ruled" -> Message.PLOT_BOARD_STATUS_RULED;
            case "withdrawn" -> Message.PLOT_BOARD_STATUS_WITHDRAWN;
            case "elapsed" -> Message.PLOT_BOARD_STATUS_ELAPSED;
            default -> Message.PLOT_BOARD_STATUS_OPEN;
        };
    }

    private String boardDescription(Player player, JsonObject record) {
        if (record.has("source") && record.get("source").getAsString().equals("plot")
                && record.has("details") && record.get("details").isJsonObject()) {
            try {
                return autoText(language.language(player), record.getAsJsonObject("details"));
            } catch (RuntimeException invalid) {
                plugin.getLogger().log(Level.WARNING, "Invalid automatic plot notice", invalid);
            }
        }
        return record.get("text").getAsString();
    }

    private String autoText(Language locale, JsonObject details) {
        Message message = switch (details.get("autoEvent").getAsString()) {
            case "registered" -> Message.PLOT_BOARD_AUTO_REGISTERED_TEXT;
            default -> throw new IllegalArgumentException("Unknown automatic plot event");
        };
        Object[] area = {
                details.get("plotName").getAsString(),
                details.get("minX").getAsInt(), details.get("maxX").getAsInt(),
                details.get("minY").getAsInt(), details.get("maxY").getAsInt(),
                details.get("minZ").getAsInt(), details.get("maxZ").getAsInt()
        };
        return language.t(locale, message, area);
    }

    private record Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        private static Bounds ofPlot(Plot plot) {
            int cell = PlotGeometry.CELL;
            return new Bounds(
                    plot.cells().stream().mapToInt(part -> part.x() * cell).min().orElseThrow(),
                    plot.cells().stream().mapToInt(part -> (part.x() + 1) * cell - 1).max().orElseThrow(),
                    plot.cells().stream().mapToInt(part -> part.y() * cell).min().orElseThrow(),
                    plot.cells().stream().mapToInt(part -> (part.y() + 1) * cell - 1).max().orElseThrow(),
                    plot.cells().stream().mapToInt(part -> part.z() * cell).min().orElseThrow(),
                    plot.cells().stream().mapToInt(part -> (part.z() + 1) * cell - 1).max().orElseThrow());
        }
    }

    public void publishRegistration(Player actor, Plot plot, String ownerName, World world) {
        publishAutomatic(actor, plot, ownerName, world.getName(), "registered",
                Bounds.ofPlot(plot));
    }

    private void publishAutomatic(Player actor, Plot plot, String ownerName, String worldName,
                                  String event, Bounds bounds) {
        JsonObject details = new JsonObject();
        details.addProperty("autoEvent", event);
        details.addProperty("plotName", plot.name());
        details.addProperty("minX", bounds.minX());
        details.addProperty("maxX", bounds.maxX());
        details.addProperty("minY", bounds.minY());
        details.addProperty("maxY", bounds.maxY());
        details.addProperty("minZ", bounds.minZ());
        details.addProperty("maxZ", bounds.maxZ());
        JsonObject payload = new JsonObject();
        payload.addProperty("kind", "notice");
        payload.addProperty("subject", plot.name());
        payload.addProperty("text", autoText(Language.EN_US, details));
        payload.addProperty("world", worldName);
        payload.addProperty("x", bounds.minX());
        payload.addProperty("y", bounds.minY());
        payload.addProperty("z", bounds.minZ());
        payload.addProperty("projectId", plot.id().toString());
        payload.add("details", details);
        payload.add("notifiedPlayers", nearbyBoardTargets(plot.world(), bounds, plot.owner()));
        JsonObject saved;
        try {
            saved = boardStore.submitAutomaticNotice(plot.owner(),
                    ownerName.equals(plot.owner().toString()) ? "Player" : ownerName, payload);
        } catch (SQLException | PlotBoardStore.Failure error) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not publish automatic plot notice for " + plot.id(), error);
            send(actor, NamedTextColor.RED, Message.PLOT_BOARD_WRITE_FAILED);
            return;
        }
        try { reloadCommunityBoard(); }
        catch (SQLException error) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not refresh community board after publishing " + saved.get("id"), error);
        }
        reportBoardTargets(actor, saved);
    }

    private JsonArray nearbyBoardTargets(UUID world, Bounds bounds, UUID author) {
        JsonArray targets = new JsonArray();
        List<UUID> nearbyOwners = PlotNoticeRecipients.nearbyOwners(registry.all(), world,
                bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ(),
                author, NOTICE_AUTO_RADIUS);
        for (UUID ownerId : nearbyOwners) {
            OfflinePlayer known = Bukkit.getOfflinePlayer(ownerId);
            JsonObject target = new JsonObject();
            target.addProperty("uuid", ownerId.toString());
            target.addProperty("name", known.getName() == null || known.getName().isBlank()
                    ? "Player" : known.getName());
            targets.add(target);
        }
        return targets;
    }

    private void reportBoardTargets(Player player, JsonObject saved) {
        int arranged = saved.getAsJsonArray("notifiedPlayers").size();
        send(player, NamedTextColor.AQUA, Message.PLOT_BOARD_AUTO_NOTIFY_RESULT, arranged);
    }

    public void postObjection(Player player, String raw) {
        int space = raw.indexOf(' ');
        if (space < 1) throw new PlotProblem(Message.PLOT_ERROR_ARGS);
        postObjection(player, raw.substring(0, space), raw.substring(space + 1));
    }

    private void postObjection(Player player, String target, String text) {
        String description = text.strip();
        validBoardText(description);
        JsonObject payload = new JsonObject();
        payload.addProperty("text", description);
        payload.addProperty("kind", "objection");
        payload.addProperty("relatedNoticeId", boardId(target));
        payload.add("notifiedPlayers", new JsonArray());
        writeBoard(player, "create", payload, saved -> reportBoardTargets(player, saved));
    }

    public void ruleRaw(Player player, String raw) {
        if (!RolePermissions.canModerate(player))
            throw new PlotProblem(Message.PLOT_BOARD_RECORD_INVALID);
        int space = raw.indexOf(' ');
        if (space < 1) throw new PlotProblem(Message.PLOT_ERROR_ARGS);
        rule(player, raw.substring(0, space), raw.substring(space + 1));
    }

    private void rule(Player player, String rawId, String rawReason) {
        if (!RolePermissions.canModerate(player))
            throw new PlotProblem(Message.PLOT_BOARD_RECORD_INVALID);
        JsonObject payload = new JsonObject();
        payload.addProperty("id", boardId(rawId));
        String reason = rawReason.strip();
        if (reason.length() < 8 || reason.length() > 1000)
            throw new PlotProblem(Message.PLOT_BOARD_TEXT_INVALID);
        payload.addProperty("ruling", reason);
        writeBoard(player, "rule", payload);
    }

    public void withdraw(Player player, String rawId) {
        if (rawId.isBlank()) throw new PlotProblem(Message.PLOT_ERROR_ARGS);
        JsonObject payload = new JsonObject();
        payload.addProperty("id", boardId(rawId));
        writeBoard(player, "withdraw", payload);
    }

    private void perform(Player player, Runnable action) {
        try { action.run(); }
        catch (PlotProblem error) {
            send(player, NamedTextColor.RED, error.message(), error.args());
        } catch (IllegalArgumentException error) {
            plugin.getLogger().log(Level.WARNING, "Plot board action failed", error);
            send(player, NamedTextColor.RED, Message.PLOT_ERROR_STORAGE);
        }
    }

    private static void validBoardText(String description) {
        if (description.length() < 12 || description.length() > 1000)
            throw new PlotProblem(Message.PLOT_BOARD_TEXT_INVALID);
    }

    private String boardId(String prefix) {
        String id = prefix.toLowerCase(Locale.ROOT);
        if (fullBoardId(id)) return id;
        if (!id.matches("[0-9a-f]{4,32}"))
            throw new PlotProblem(Message.PLOT_BOARD_RECORD_INVALID);
        try {
            String match = boardStore.findIdByPrefix(id);
            if (match != null) return match;
        } catch (SQLException | PlotBoardStore.Failure error) {
            throw new PlotProblem(Message.PLOT_BOARD_RECORD_INVALID);
        }
        throw new PlotProblem(Message.PLOT_BOARD_RECORD_INVALID);
    }

    private static boolean fullBoardId(String id) {
        return id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }

    private void writeBoard(Player player, String action, JsonObject payload) {
        writeBoard(player, action, payload, _ -> { });
    }

    private void writeBoard(Player player, String action, JsonObject payload,
                            Consumer<JsonObject> onSaved) {
        JsonObject saved;
        try {
            saved = boardStore.submit(action, player.getUniqueId(), player.getName(),
                    RolePermissions.canModerate(player), payload);
        } catch (PlotBoardStore.Failure error) {
            Message reason = error.getMessage().equals("rate_limited")
                    ? Message.PLOT_BOARD_RATE_LIMITED
                    : error.getMessage().equals("invalid_payload")
                    ? Message.PLOT_BOARD_TEXT_INVALID : Message.PLOT_BOARD_RECORD_INVALID;
            send(player, NamedTextColor.RED, reason);
            return;
        } catch (SQLException error) {
            plugin.getLogger().log(Level.WARNING, "Could not update community board", error);
            send(player, NamedTextColor.RED, Message.PLOT_BOARD_WRITE_FAILED);
            return;
        }
        try { reloadCommunityBoard(); }
        catch (SQLException error) {
            plugin.getLogger().log(Level.WARNING, "Could not refresh community board", error);
        }
        String id = saved.get("id").getAsString();
        send(player, NamedTextColor.GREEN, Message.PLOT_BOARD_SAVED, id.substring(0, 8));
        onSaved.accept(saved);
    }

    public String publicBoardListJson() throws SQLException {
        JsonObject response = new JsonObject();
        response.add("notices", boardStore.listPublic());
        return response.toString();
    }

    public String publicBoardDetailJson(String id) throws SQLException {
        JsonObject record = boardStore.getPublic(id);
        if (record == null) return null;
        JsonObject response = new JsonObject();
        response.add("notice", record);
        return response.toString();
    }

    private void textInput(Player player, Message title, Message label, String initial,
                           int maximumLength, boolean multiline,
                           BiConsumer<Player, String> submit) {
        FloatingMenus.input(player, MenuInputForm.builder(Component.text(t(player, title), NamedTextColor.GOLD))
                .field(MenuInputForm.Field.text("value", Component.text(t(player, label), NamedTextColor.WHITE), initial, maximumLength).multiline(multiline))
                .cancel(Component.text(t(player, Message.BACK), NamedTextColor.WHITE))
                .submit(Component.text(t(player, Message.PLOT_MENU_SUBMIT), NamedTextColor.GREEN), (responder, inputValues) -> submit.accept(responder, inputValues.text("value").strip())).build());
    }

    private String t(Player player, Message message, Object... args) {
        return language.t(player, message, args);
    }

    private void send(Player player, NamedTextColor color, Message message, Object... args) {
        player.sendMessage(Component.text(t(player, message, args), color));
    }
}
