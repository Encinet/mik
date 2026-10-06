package org.encinet.mik.module.plot.board;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.menu.FloatingMenuAppearance;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.plot.PlotMenuLayouts;
import org.encinet.mik.module.menu.FloatingMenuPage;
import org.encinet.mik.module.menu.FloatingMenus;
import org.encinet.mik.module.menu.MenuDialogs;
import org.encinet.mik.module.role.RolePermissions;
import org.encinet.mik.module.plot.Plot;
import org.encinet.mik.module.plot.PlotDataPaths;
import org.encinet.mik.module.plot.PlotGeometry;
import org.encinet.mik.module.plot.PlotNoticePublisher;
import org.encinet.mik.module.plot.PlotProblem;
import org.encinet.mik.module.plot.PlotRegistry;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Owns the community board's persistence, game menus, commands and public snapshot. */
public final class PlotCommunityBoard {
    private static final int BOARD_RECORDS_PER_PAGE = 8;
    private static final int NOTICE_AUTO_RADIUS = 64;

    private final JavaPlugin plugin;
    private final LanguageService language;
    private final PlotRegistry registry;
    private final PlotBoardStore boardStore;
    private final PlotBoardNotifications boardNotifications;
    private volatile JsonObject communityBoard;
    private long boardRevision;

    public PlotCommunityBoard(JavaPlugin plugin, LanguageService language, PlotNoticePublisher notices,
                              PlotRegistry registry) {
        this.plugin = plugin;
        this.language = language;
        this.registry = registry;
        boardStore = new PlotBoardStore(PlotDataPaths.in(
                plugin.getDataFolder().toPath()).boardDatabase());
        boardNotifications = new PlotBoardNotifications(plugin, language, notices);
    }

    public void open() throws Exception {
        boardStore.open();
        boardNotifications.enable();
        reloadCommunityBoard();
    }

    public void close() throws SQLException {
        try {
            boardNotifications.disable();
        } finally {
            boardStore.close();
        }
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

    private FloatingMenuDefinition boardDefinition(Player player, int requestedPage) {
        long revision = boardRevision;
        List<JsonObject> records = boardRecords();
        FloatingMenuPage page = new FloatingMenuPage(requestedPage,
                records.size(), BOARD_RECORDS_PER_PAGE);
        FloatingMenuDefinition.Builder menu = PlotMenuLayouts.screen("plot-board",
                        FloatingMenuAppearance.ARCHIVE, PlotMenuLayouts.browser("records", "summary"))
                .refreshEvery(40, (p, handle) -> {
                    if (boardRevision != revision)
                        handle.update(boardDefinition(p, page.index()));
                });
        menu.information("heading", Component.text(t(player, Message.PLOT_BOARD_MENU),
                NamedTextColor.GOLD)).region("heading").keepAccessible();
        if (records.isEmpty()) {
            menu.information("empty", Component.text(t(player, communityBoard == null
                            ? Message.PLOT_BOARD_UNAVAILABLE : Message.PLOT_BOARD_EMPTY),
                    NamedTextColor.GRAY)).region("records");
        } else {
            for (JsonObject record : page.slice(records)) {
                String id = record.get("id").getAsString();
                String subject = shortBoardSubject(record.get("subject").getAsString());
                Message kind = record.get("kind").getAsString().equals("objection")
                        ? Message.PLOT_BOARD_KIND_OBJECTION : Message.PLOT_BOARD_KIND_NOTICE;
                menu.item("record:" + id, Material.PAPER,
                                Component.text(t(player, kind) + " · " + t(player, boardStatus(
                                        record.get("status").getAsString())), NamedTextColor.AQUA)
                                .append(Component.newline())
                                .append(Component.text(subject, NamedTextColor.GRAY)))
                        .region("records")
                        .primary((p, handle) -> openBoardEntry(p, record));
            }
        }
        if (page.count() > 1)
            menu.pagination("pagination", page, index -> openMenu(player, index));
        menu.item("find", Material.COMPASS,
                        Component.text(t(player, Message.PLOT_MENU_FIND_RECORD), NamedTextColor.AQUA))
                .region("actions")
                .primary((p, handle) -> textInput(p, Message.PLOT_MENU_FIND_RECORD,
                        Message.PLOT_MENU_RECORD_ID, "", 36, false,
                        this::openBoardRecord));
        menu.back(Component.text(t(player, Message.PLOT_BACK), NamedTextColor.GREEN))
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

    private JsonObject cachedBoardRecord(String id) {
        JsonObject loaded = communityBoard;
        if (loaded != null) for (JsonElement element : loaded.getAsJsonArray("notices")) {
            JsonObject record = element.getAsJsonObject();
            if (record.get("id").getAsString().equals(id)) return record;
        }
        try { return boardStore.getPublic(id); }
        catch (SQLException error) {
            plugin.getLogger().log(Level.WARNING, "Could not read community board record", error);
            return null;
        }
    }

    private void openBoardRecord(Player player, String rawId) {
        try {
            String id = boardId(rawId);
            JsonObject record = cachedBoardRecord(id);
            if (record != null) openBoardEntry(player, record);
            else fetchBoardDetail(player, id, true);
        } catch (PlotProblem invalid) {
            send(player, NamedTextColor.RED, invalid.message(), invalid.args());
            openMenu(player);
        }
    }

    private void openBoardEntry(Player player, JsonObject record) {
        openBoardEntry(player, record, 0);
    }

    private void openBoardEntry(Player player, JsonObject record, int requestedPage) {
        FloatingMenus.present(player, boardEntryDefinition(player, record, requestedPage));
    }

    private FloatingMenuDefinition boardEntryDefinition(Player player, JsonObject record,
                                                        int requestedPage) {
        String id = record.get("id").getAsString();
        String description = boardDescription(player, record);
        if (record.has("ruling") && !record.get("ruling").isJsonNull())
            description += "\n\n" + t(player, Message.PLOT_BOARD_RULING_DETAIL,
                    record.get("ruling").getAsString());
        List<String> textPages = textPages(description);
        FloatingMenuPage page = new FloatingMenuPage(requestedPage, textPages.size(), 1);
        long revision = boardRevision;
        FloatingMenuDefinition.Builder menu = PlotMenuLayouts.screen("plot-board-entry:" + id,
                        FloatingMenuAppearance.ARCHIVE, PlotMenuLayouts.record())
                .refreshEvery(40, (p, handle) -> {
                    if (boardRevision != revision) {
                        JsonObject fresh = cachedBoardRecord(id);
                        handle.update(boardEntryDefinition(p,
                                fresh == null ? record : fresh, page.index()));
                    }
                });
        menu.information("heading", Component.text(
                textPages(record.get("subject").getAsString()).getFirst(),
                NamedTextColor.GOLD)).region("heading").keepAccessible();
        Message kind = record.get("kind").getAsString().equals("objection")
                ? Message.PLOT_BOARD_KIND_OBJECTION : Message.PLOT_BOARD_KIND_NOTICE;
        String location = record.get("world").getAsString() + " "
                + record.get("x").getAsInt() + ", " + record.get("y").getAsInt()
                + ", " + record.get("z").getAsInt();
        menu.information("metadata", Component.text(t(player, kind) + " · "
                        + t(player, boardStatus(record.get("status").getAsString()))
                        + " · " + id.substring(0, 8), NamedTextColor.AQUA)
                .append(Component.newline())
                .append(Component.text(record.get("authorName").getAsString() + " · "
                        + location, NamedTextColor.GRAY))).region("metadata");
        menu.information("detail", Component.text(textPages.get(page.index()), NamedTextColor.WHITE))
                .region("detail").keepAccessible();
        if (page.count() > 1)
            menu.pagination("pagination", page, index -> openBoardEntry(player, record, index));
        if (record.get("kind").getAsString().equals("notice")) {
            menu.item("feedback", Material.FEATHER,
                            Component.text(t(player, Message.PLOT_MENU_FEEDBACK), NamedTextColor.YELLOW))
                    .region("actions")
                    .primary((p, handle) -> textInput(p, Message.PLOT_MENU_FEEDBACK,
                            Message.PLOT_MENU_DESCRIPTION, "", 1000, true,
                            (viewer, value) -> {
                                perform(viewer, () -> postObjection(viewer, id, value));
                                openBoardEntry(viewer, record);
                            }));
        }
        if (record.has("relatedNoticeId") && !record.get("relatedNoticeId").isJsonNull())
            menu.item("related", Material.MAP,
                            Component.text(t(player, Message.PLOT_MENU_RELATED_NOTICE), NamedTextColor.AQUA))
                    .region("actions")
                    .primary((p, handle) -> openBoardRecord(p,
                            record.get("relatedNoticeId").getAsString()));
        if (record.get("kind").getAsString().equals("objection")
                && record.get("status").getAsString().equals("open")) {
            JsonObject related = record.has("relatedNoticeId")
                    && !record.get("relatedNoticeId").isJsonNull()
                    ? cachedBoardRecord(record.get("relatedNoticeId").getAsString()) : null;
            boolean ownsFeedback = boardAuthoredBy(id, player.getUniqueId());
            boolean ownDispute = ownsFeedback || related != null
                    && boardAuthoredBy(related.get("id").getAsString(), player.getUniqueId());
            if (ownsFeedback)
                menu.item("withdraw", Material.BARRIER,
                                Component.text(t(player, Message.PLOT_MENU_WITHDRAW), NamedTextColor.RED))
                        .region("actions")
                        .primary((p, handle) -> MenuDialogs.openConfirm(plugin, p,
                                Component.text(t(p, Message.PLOT_MENU_WITHDRAW), NamedTextColor.RED),
                                Component.text(t(p, Message.PLOT_MENU_CONFIRM_WITHDRAW), NamedTextColor.GRAY),
                                Component.text(t(p, Message.PLOT_MENU_WITHDRAW), NamedTextColor.RED),
                                Component.text(t(p, Message.PLOT_BACK), NamedTextColor.GRAY),
                                viewer -> perform(viewer, () -> withdraw(viewer, id))));
            if (RolePermissions.canModerate(player) && !ownDispute)
                menu.item("rule", Material.WRITABLE_BOOK,
                                Component.text(t(player, Message.PLOT_MENU_RULE), NamedTextColor.YELLOW))
                        .region("actions")
                        .primary((p, handle) -> textInput(p, Message.PLOT_MENU_RULE,
                                Message.PLOT_MENU_RULING, "", 1000, true,
                                (viewer, value) -> perform(viewer,
                                        () -> rule(viewer, id, value))));
        }
        menu.back(Component.text(t(player, Message.PLOT_BACK), NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        return menu.build();
    }

    private static String shortBoardSubject(String subject) {
        StringBuilder shortText = new StringBuilder();
        int width = 0;
        for (int offset = 0; offset < subject.length();) {
            int character = subject.codePointAt(offset);
            offset += Character.charCount(character);
            if (Character.isISOControl(character)) character = ' ';
            int characterWidth = character < 0x80 ? 1 : 2;
            if (width + characterWidth > 37) return shortText.append('…').toString();
            shortText.appendCodePoint(character);
            width += characterWidth;
        }
        return shortText.toString();
    }

    public static List<String> textPages(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int width = 0;
        for (int offset = 0; offset < text.length();) {
            int character = text.codePointAt(offset);
            offset += Character.charCount(character);
            if (character == '\r') continue;
            if (character == '\n') {
                lines.add(line.toString());
                line.setLength(0);
                width = 0;
                continue;
            }
            int characterWidth = character < 0x80 ? 1 : 2;
            if (width + characterWidth > 48) {
                lines.add(line.toString());
                line.setLength(0);
                width = 0;
            }
            line.appendCodePoint(character);
            width += characterWidth;
        }
        lines.add(line.toString());
        List<String> pages = new ArrayList<>();
        for (int first = 0; first < lines.size(); first += 6)
            pages.add(String.join("\n", lines.subList(first, Math.min(first + 6, lines.size()))));
        return pages;
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
            send(player, NamedTextColor.GRAY, Message.PLOT_BOARD_ITEM,
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
        send(player, NamedTextColor.GRAY, Message.PLOT_BOARD_DETAIL,
                item.get("authorName").getAsString(), item.get("world").getAsString(),
                item.get("x").getAsInt(), item.get("y").getAsInt(), item.get("z").getAsInt(),
                boardDescription(player, item));
        if (item.has("ruling") && !item.get("ruling").isJsonNull())
            send(player, NamedTextColor.GREEN, Message.PLOT_BOARD_RULING_DETAIL,
                    item.get("ruling").getAsString());
    }

    private void fetchBoardDetail(Player player, String id) {
        fetchBoardDetail(player, id, false);
    }

    private void fetchBoardDetail(Player player, String id, boolean inMenu) {
        JsonObject record = cachedBoardRecord(id);
        if (record == null) {
            send(player, NamedTextColor.RED, Message.PLOT_BOARD_RECORD_INVALID);
            return;
        }
        if (inMenu) openBoardEntry(player, record);
        else showBoardEntry(player, record);
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

    private boolean boardAuthoredBy(String id, UUID playerId) {
        try { return boardStore.isAuthor(id, playerId); }
        catch (SQLException error) {
            plugin.getLogger().log(Level.WARNING, "Could not check community board author", error);
            return true;
        }
    }

    private static boolean fullBoardId(String id) {
        return id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }

    private void writeBoard(Player player, String action, JsonObject payload) {
        writeBoard(player, action, payload, saved -> { });
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
        MenuDialogs.openTextInput(plugin, player,
                Component.text(t(player, title), NamedTextColor.GOLD),
                Component.text(t(player, label), NamedTextColor.GRAY),
                initial, maximumLength, multiline,
                Component.text(t(player, Message.PLOT_MENU_SUBMIT), NamedTextColor.GREEN),
                Component.text(t(player, Message.PLOT_BACK), NamedTextColor.GRAY), submit);
    }

    private String t(Player player, Message message, Object... args) {
        return language.t(player, message, args);
    }

    private void send(Player player, NamedTextColor color, Message message, Object... args) {
        player.sendMessage(Component.text(t(player, message, args), color));
    }
}
