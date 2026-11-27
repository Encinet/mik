package org.encinet.mik.feature.world.heads;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.world.heads.catalog.HeadCatalogs;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.util.ShutdownSequence;

import java.util.List;

/** Owns the public head library and its Paper entry points. */
public final class HeadsModule {
    public static final String PERMISSION = "mik.heads.use";
    private final JavaPlugin plugin;
    private final LanguageService languages;
    private HeadCatalogs catalogs;
    private HeadWork worker;
    private HeadPlayers players;
    private HeadMenu menu;

    public HeadsModule(JavaPlugin plugin, LanguageService languages) {
        this.plugin = plugin;
        this.languages = languages;
    }

    public void enable() {
        var folder = plugin.getDataFolder().toPath().resolve("heads");
        var main = plugin.getServer().getScheduler().getMainThreadExecutor(plugin);
        catalogs = new HeadCatalogs(folder, plugin.getLogger());
        worker = new HeadWork();
        players = new HeadPlayers(main);
        menu = new HeadMenu(catalogs, new HeadFavorites(folder.resolve("favorites")), players, worker,
                main, languages, plugin.getLogger());
        catalogs.start();
    }

    public void disable() {
        var shutdown = new ShutdownSequence();
        if (menu != null) shutdown.attempt("head menus", menu::close);
        if (players != null) shutdown.attempt("player head lookups", players::close);
        if (worker != null) shutdown.attempt("head queries", worker::close);
        if (catalogs != null) shutdown.attempt("head catalog", catalogs::close);
        menu = null;
        players = null;
        worker = null;
        catalogs = null;
        shutdown.finish("Decorative heads shutdown failed");
    }

    public void open(Player player) { open(player, null, null); }

    private int open(Player player, HeadScenes.Section section, String query) {
        if (menu == null) return 0;
        if (query != null && query.codePointCount(0, query.length()) > HeadMenu.QUERY_LIMIT) {
            player.sendMessage(languages.text(player, Message.MENU_QUERY_TOO_LONG,
                    net.kyori.adventure.text.format.NamedTextColor.YELLOW,
                    query.codePointCount(0, query.length()), HeadMenu.QUERY_LIMIT));
            return 0;
        }
        menu.open(player, section, query);
        return Command.SINGLE_SUCCESS;
    }

    public void registerCommands() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                Commands.literal("heads").requires(source -> source.getSender() instanceof Player player && player.hasPermission(PERMISSION))
                        .executes(context -> open((Player) context.getSource().getSender(), null, null))
                        .then(Commands.literal("search").then(Commands.argument("query", StringArgumentType.greedyString())
                                .executes(context -> open((Player) context.getSource().getSender(), HeadScenes.Section.CATALOG,
                                        StringArgumentType.getString(context, "query")))))
                        .then(Commands.literal("player")
                                .executes(context -> open((Player) context.getSource().getSender(), HeadScenes.Section.PLAYERS, ""))
                                .then(Commands.argument("name-or-uuid", StringArgumentType.word())
                                        .executes(context -> open((Player) context.getSource().getSender(), HeadScenes.Section.PLAYERS,
                                                StringArgumentType.getString(context, "name-or-uuid")))))
                        .then(Commands.literal("favorites")
                                .executes(context -> open((Player) context.getSource().getSender(), HeadScenes.Section.FAVORITES, null)))
                        .build(), languages.t(Language.DEFAULT, Message.HEADS_TITLE), List.of("hdb", "headdb")));
    }
}
