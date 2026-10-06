package org.encinet.mik.module.plot;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.plot.board.PlotBoardNotifications;
import org.encinet.mik.module.plot.board.PlotCommunityBoard;
import org.encinet.mik.module.role.RolePermissions;

import java.sql.SQLException;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.logging.Level;

final class PlotCreationController {
    private final JavaPlugin plugin;
    private final LanguageService language;
    private final PlotNoticePublisher notices;
    private final PlotEdits edits;
    private final PlotRegistry registry;
    private final PlotSelectionState selections;
    private final PlotCommunityBoard board;
    private final BiConsumer<Player, UUID> showDetail;

    PlotCreationController(JavaPlugin plugin, LanguageService language, PlotNoticePublisher notices,
                           PlotRegistry registry, PlotSelectionState selections, PlotCommunityBoard board,
                           BiConsumer<Player, UUID> showDetail) {
        this.plugin = plugin;
        this.language = language;
        this.notices = notices;
        this.edits = new PlotEdits(registry);
        this.registry = registry;
        this.selections = selections;
        this.board = board;
        this.showDetail = showDetail;
    }

    void create(Player player, String name) throws SQLException {
        createFor(player, new PlotOwner(player.getUniqueId(), player.getName()), name, false);
    }

    void createFor(Player player, PlotOwner target, String name) throws SQLException {
        createFor(player, target, name, true);
    }

    private void createFor(Player player, PlotOwner target, String name, boolean assisted) throws SQLException {
        selections.bind(player.getUniqueId(), null, player.getWorld().getUID());
        create(player, target, name, selections.submission(player.getUniqueId(), null,
                player.getWorld().getUID()), assisted);
    }

    void create(Player player, PlotOwner target, String name, PlotSelectionState.Submission submitted,
                boolean assisted) throws SQLException {
        if (!assisted && !target.id().equals(player.getUniqueId()))
            throw new PlotProblem(Message.PLOT_ERROR_STAFF_ONLY);
        if (assisted ? !RolePermissions.canModerate(player) : !RolePermissions.isMember(player))
            throw new PlotProblem(assisted ? Message.PLOT_ERROR_STAFF_ONLY : Message.PLOT_ERROR_MEMBER_REQUIRED);
        UUID actor = player.getUniqueId();
        UUID world = player.getWorld().getUID();
        if (submitted.context().intent() != PlotEditorContext.Intent.CREATE_PLOT)
            throw new PlotProblem(Message.PLOT_ERROR_SELECTION_CHANGED);
        selections.requireSubmission(actor, world, submitted);
        PlotWriteFeedback.finish(plugin, language, registry, player,
                edits.writeAsync(staged -> staged.create(target.id(), name, world, submitted.shape())), plot -> {
                    boolean cleared = selections.clearIfMatches(actor, null, submitted);
                    announceCreated(player, plot, new CreateRequest(target, name, assisted),
                            cleared && selections.context(actor).equals(submitted.context()) && player.isOnline()
                                    && player.getWorld().getUID().equals(world));
                });
    }

    private record CreateRequest(PlotOwner target, String name, boolean assisted) { }

    private void announceCreated(Player player, Plot plot, CreateRequest request, boolean navigate) {
        if (request.assisted()) {
            plugin.getLogger().info("Staff member " + player.getUniqueId() + " registered plot "
                    + plot.id() + " for " + plot.owner() + " in world " + plot.world());
            send(player, NamedTextColor.GREEN, Message.PLOT_ADMIN_CREATED,
                    plot.name(), request.target().name(), plot.id());
            if (!plot.owner().equals(player.getUniqueId())) {
                Player owner = Bukkit.getPlayer(plot.owner());
                if (owner != null)
                    send(owner, NamedTextColor.GREEN, Message.PLOT_CREATED_FOR_YOU,
                            plot.name(), player.getName());
                Language ownerLanguage = owner == null
                        ? language.preferredLanguage(plot.owner()).orElse(Language.DEFAULT)
                        : language.language(owner);
                try {
                    notices.publish(plot.owner(), request.target().name(),
                            language.t(ownerLanguage, Message.PLOT_CREATED_FOR_YOU,
                            PlotBoardNotifications.safeAlertText(plot.name()), player.getName()));
                } catch (RuntimeException error) {
                    plugin.getLogger().log(Level.WARNING,
                            "Could not send assisted plot registration notice", error);
                }
            }
        } else {
            send(player, NamedTextColor.GREEN, Message.PLOT_CREATED, plot.name(), plot.id());
        }
        World world = Bukkit.getWorld(plot.world());
        if (world != null) board.publishRegistration(player, plot, request.target().name(), world);
        if (navigate) showDetail.accept(player, plot.id());
    }

    static PlotOwner managedPlayer(String input) {
        String value = input.strip();
        if (value.isEmpty()) throw new PlotProblem(Message.PLOT_ERROR_ARGS);
        OfflinePlayer target;
        try {
            target = Bukkit.getOfflinePlayer(UUID.fromString(value));
        } catch (IllegalArgumentException invalidUuid) {
            Player online = Bukkit.getPlayerExact(value);
            target = online == null ? Bukkit.getOfflinePlayerIfCached(value) : online;
            if (target == null || target.getName() == null
                    || !target.getName().equalsIgnoreCase(value))
                throw new PlotProblem(Message.PLOT_ERROR_PLAYER_UNKNOWN);
        }
        if (!target.isOnline() && !target.hasPlayedBefore())
            throw new PlotProblem(Message.PLOT_ERROR_PLAYER_UNKNOWN);
        String name = target.getName();
        return new PlotOwner(target.getUniqueId(),
                name == null || name.isBlank() ? target.getUniqueId().toString() : name);
    }

    private String t(Player player, Message message, Object... args) {
        return language.t(player, message, args);
    }

    private void send(Player player, NamedTextColor color, Message message, Object... args) {
        player.sendMessage(Component.text(t(player, message, args), color));
    }
}
