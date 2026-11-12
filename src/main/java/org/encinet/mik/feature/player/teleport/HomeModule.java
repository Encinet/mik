package org.encinet.mik.feature.player.teleport;

import org.encinet.mik.shared.menu.MenuInputForm;
import org.encinet.mik.shared.menu.MenuQuerySession;
import org.encinet.mik.shared.menu.MenuPopover;
import org.encinet.mik.shared.menu.MenuPanel;
import org.encinet.mik.shared.menu.FloatingMenus;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.player.guidance.Tip;
import org.encinet.mik.feature.player.guidance.TipScope;
import org.encinet.mik.feature.player.guidance.TipTriggers;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.i18n.RichArg;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.FloatingMenuAppearance;
import org.encinet.mik.shared.menu.FloatingMenuContext;
import org.encinet.mik.shared.menu.FloatingMenuFeedbackKind;
import org.encinet.mik.shared.menu.FloatingMenuTextWidth;
import org.encinet.mik.shared.menu.FloatingMenuScreen;
import org.encinet.mik.shared.menu.FloatingMenuState;
import org.encinet.mik.shared.menu.ui.Ui;
import org.encinet.mik.shared.menu.ui.UiNode;
import org.encinet.mik.shared.menu.ui.UiKey;
import org.encinet.mik.shared.role.RolePermissions;
import org.encinet.mik.shared.util.ShutdownSequence;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Manages player homes: /sethome, /home, /delhome
 * <p>
 * HomeStore owns the homes.yml cache and ordered persistence.
 */
public final class HomeModule implements Listener {

    private static final Pattern HOME_MENTION = Pattern.compile(
            "/(?:sethome|home)(?![a-z0-9_])|(?<![\\p{L}\\p{N}_])(?:sethome|homes?)(?![\\p{L}\\p{N}_])"
                    + "|家园|家園|设(?:置)?家|設(?:置)?家|回家|我家|我的家",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final FloatingMenuScreen<HomeMenuState> homeScreen;
    private final Map<UUID, MenuQuerySession> searchInputs = new HashMap<>();
    private final HomeStore store;
    private final Tip<String> returnTip;

    public HomeModule(JavaPlugin plugin, LanguageService languageService, TipScope tips) {
        this(plugin, languageService, tips, new HomeStore(plugin.getDataFolder().toPath().resolve("homes.yml"),
                plugin.getLogger()));
    }

    HomeModule(JavaPlugin plugin, LanguageService languageService, TipScope tips, HomeStore store) {
        this.plugin = plugin;
        this.languageService = languageService;
        this.store = store;
        this.homeScreen = new FloatingMenuScreen<>("homes",
                _ -> HomeMenuState.initial(), this::buildHomeMenu);
        tips.define("home-create", Message.HOME_TIP_CREATE_MM)
                .group("home").priority(90)
                .when(player -> getHomeNames(player).isEmpty() && homeScreen.flow(player).isEmpty())
                .on(TipTriggers.chatMentions(HOME_MENTION))
                .on(TipTriggers.teleport(0.1))
                .suggestCommand("/sethome ").build();
        this.returnTip = tips.<String>define("home-return", Message.HOME_TIP_RETURN_MM)
                .group("home").priority(80).interval(Duration.ofDays(30))
                .when((player, name) -> homeScreen.flow(player).isEmpty() && getHome(player, name) != null)
                .suggestCommand("/home").build();
    }

    public void enable() {
        plugin.getLogger().info("Homes loaded: " + store.load() + " entries.");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void disable() {
        var shutdown = new ShutdownSequence();
        shutdown.attempt("home listeners", () -> HandlerList.unregisterAll(this));
        shutdown.attempt("home menus", homeScreen::forgetAll);
        for (var input : List.copyOf(searchInputs.values())) shutdown.attempt("home query", input::close);
        searchInputs.clear();
        shutdown.attempt("home store", store::close);
        shutdown.finish("home module");
    }

    private int getMaxHomes(Player player) {
        return RolePermissions.isMember(player) ? 20 : 2;
    }

    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();

            // /sethome <name>
            commands.register(
                    Commands.literal("sethome")
                            .executes(ctx -> {
                                Player player = requirePlayer(ctx.getSource().getSender());
                                if (player != null) {
                                    sendUsage(player, "/sethome <name>", Message.HOME_SET_COMMAND_DESCRIPTION);
                                }
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(Commands.argument("name", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        Player player = requirePlayer(ctx.getSource().getSender());
                                        if (player == null) {
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        String name = StringArgumentType.getString(ctx, "name");
                                        HomeWriteResult result = saveHome(player, name,
                                                player.getLocation(), false);
                                        if (result != HomeWriteResult.SAVED) {
                                            sendHomeWriteFailure(player, result);
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        player.sendMessage(homeMessage(player, Message.HOME_SET_RICH,
                                                name, NamedTextColor.GREEN));
                                        return Command.SINGLE_SUCCESS;
                                    }))
                            .build(),
                    languageService.t(Language.DEFAULT, Message.HOME_SET_COMMAND_DESCRIPTION)
            );

            // /home, /home <name>
            commands.register(
                    Commands.literal("home")
                            .executes(ctx -> {
                                Player player = requirePlayer(ctx.getSource().getSender());
                                if (player != null) {
                                    openHomeMenu(player);
                                }
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(Commands.argument("name", StringArgumentType.greedyString())
                                    .suggests((ctx, builder) -> {
                                        if (ctx.getSource().getSender() instanceof Player player) {
                                            getHomeNames(player).forEach(builder::suggest);
                                        }
                                        return builder.buildFuture();
                                    })
                                    .executes(ctx -> {
                                        Player player = requirePlayer(ctx.getSource().getSender());
                                        if (player == null) {
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        String name = StringArgumentType.getString(ctx, "name");
                                        teleportHome(player, name);
                                        return Command.SINGLE_SUCCESS;
                                    }))
                            .build(),
                    languageService.t(Language.DEFAULT, Message.HOME_MENU_TITLE)
            );

            // /delhome <name>
            commands.register(
                    Commands.literal("delhome")
                            .executes(ctx -> {
                                Player player = requirePlayer(ctx.getSource().getSender());
                                if (player != null) {
                                    sendUsage(player, "/delhome <name>", Message.HOME_DELETE_COMMAND_DESCRIPTION);
                                    sendHomeList(player);
                                }
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(Commands.argument("name", StringArgumentType.greedyString())
                                    .suggests((ctx, builder) -> {
                                        if (ctx.getSource().getSender() instanceof Player player) {
                                            getHomeNames(player).forEach(builder::suggest);
                                        }
                                        return builder.buildFuture();
                                    })
                                    .executes(ctx -> {
                                        Player player = requirePlayer(ctx.getSource().getSender());
                                        if (player == null) {
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        String name = StringArgumentType.getString(ctx, "name");
                                        if (!isValidHomeName(name)) {
                                            sendInvalidHomeName(player);
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        if (!deleteHome(player, name)) {
                                            player.sendMessage(homeMessage(player, Message.HOME_NOT_FOUND_RICH,
                                                    name, NamedTextColor.RED));
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        player.sendMessage(homeMessage(player, Message.HOME_DELETED_RICH,
                                                name, NamedTextColor.GREEN));
                                        return Command.SINGLE_SUCCESS;
                                    }))
                            .build(),
                    languageService.t(Language.DEFAULT, Message.HOME_DELETE_COMMAND_DESCRIPTION)
            );

        });
    }


    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(Component.text(languageService.t(Language.DEFAULT, Message.PLAYER_ONLY), NamedTextColor.RED));
        return null;
    }

    private void sendUsage(Player player, String command, Message description) {
        player.sendMessage(Component.text()
                .append(Component.text(languageService.t(player, Message.USAGE), NamedTextColor.YELLOW))
                .append(Component.space())
                .append(Component.text(command, NamedTextColor.AQUA))
                .append(Component.text("  " + languageService.t(player, description), NamedTextColor.WHITE))
                .build());
    }


    private void sendHomeList(Player player) {
        List<String> homes = getHomeNames(player);
        if (homes.isEmpty()) {
            player.sendMessage(languageService.text(player, Message.HOME_NO_HOMES, NamedTextColor.WHITE));
            return;
        }
        String joinedHomes = String.join(", ", homes);
        player.sendMessage(languageService.rich(player, Message.HOME_LIST_RICH, NamedTextColor.WHITE,
                RichArg.component("homes", Component.text(joinedHomes, NamedTextColor.YELLOW))));
    }

    private void openHomeMenu(Player player) {
        closeSearchInput(player);
        homeScreen.open(player, HomeMenuState.initial());
    }

    private MenuScene buildHomeMenu(FloatingMenuContext<HomeMenuState> context) {
        Player player = context.player();
        var browser = context.state().view(getHomeNames(player));
        String query = context.state().query();
        String activeHome = browser.focusedHome();
        Component empty = browser.homeCount() == 0 ? languageService.text(player, Message.HOME_EMPTY_TITLE, NamedTextColor.WHITE)
                .append(Component.newline()).append(languageService.text(player, Message.HOME_EMPTY_DESCRIPTION, NamedTextColor.WHITE))
                : languageService.text(player, Message.HOME_SEARCH_EMPTY, NamedTextColor.WHITE);
        MenuScene.Builder menu = MenuScene.screen("homes")
                .appearance(FloatingMenuAppearance.DEFAULT)
                .stableAnchor()
                .viewport(HomeMenuComposition.VIEWPORT)
                .content(HomeMenuComposition.content(searchContent(context), HomeMenuComposition.homes(browser.matches(), activeHome,
                        name -> context.update(state -> state.query().equals(query) ? state.focus(name) : state), context.state().position(),
                        position -> context.update(state -> state.query().equals(query) ? state.browse(position) : state)),
                        Ui.text(empty).key(browser.homeCount() == 0 ? "empty" : "no-matches").build()))
                .lifecycle((viewer, _, previous, current, _) -> {
                    if (current == FloatingMenuState.SUSPENDED || current == FloatingMenuState.CLOSING
                            || current == FloatingMenuState.CLOSED) closeSearchInput(viewer);
                    else if (current == FloatingMenuState.OPENING && previous == FloatingMenuState.SUSPENDED)
                        context.redraw();
                });
        menu.information("summary", summaryLabel(player, browser.homeCount()))
                .region("header");

        if (!browser.matches().isEmpty()) {
            menu.information("active-home", homeDetails(player, activeHome))
                    .region("detail").textWidth(FloatingMenuTextWidth.WIDE);
            menu.control("action:teleport",
                            Component.text(languageService.t(player,
                                    Message.HOME_ACTION_TELEPORT), NamedTextColor.GREEN))
                    .region("actions")
                    .primary((p, _) -> teleportHome(p, activeHome));
            menu.control("action:update",
                            Component.text(languageService.t(player,
                                    Message.HOME_ACTION_UPDATE), NamedTextColor.YELLOW))
                    .region("actions")
                    .primary((p, _) -> openUpdateConfirmMenu(
                            context, activeHome, p.getLocation()))
                    .hotkey((p, _) -> openUpdateConfirmMenu(
                            context, activeHome, p.getLocation()));
            menu.control("action:delete",
                            Component.text(languageService.t(player,
                                    Message.HOME_ACTION_DELETE), NamedTextColor.RED))
                    .region("actions")
                    .primary((_, _) -> openDeleteConfirmMenu(context, activeHome))
                    .secondary((_, _) -> openDeleteConfirmMenu(context, activeHome));
        }
        var create = menu.control("create",
                        Component.text(languageService.t(player,
                                Message.HOME_ACTION_CREATE), NamedTextColor.GREEN))
                .region("global-actions")
                .primary((p, _) -> openCreateHomeInput(p));
        if (browser.homeCount() >= getMaxHomes(player)) {
            create.disabled(Component.text(languageService.t(player,
                    Message.HOME_MAX_REACHED, getMaxHomes(player)), NamedTextColor.RED));
        }
        menu.control("help",
                        Component.text(languageService.t(player, Message.HOME_USAGE_BOOK),
                                NamedTextColor.GOLD))
                .region("global-actions")
                .primary((p, handle) -> handle.openPopover(UiKey.item("help"),redraw -> homeHelpPanel(p,redraw)));
        menu.dismiss(
                        Component.text(languageService.t(player,
                                        context.canGoBack() ? Message.BACK_TO_MAIN : Message.CLOSE),
                                context.canGoBack() ? NamedTextColor.GREEN : NamedTextColor.RED))
                .region("global-actions");
        return menu.build();
    }

    private UiNode searchContent(FloatingMenuContext<HomeMenuState> context) {
        Player player = context.player();
        var input = searchInputs.computeIfAbsent(player.getUniqueId(), _ -> FloatingMenus.queryInput(player,
                languageService.text(player, Message.HOME_SEARCH_PLACEHOLDER, NamedTextColor.AQUA), 128,
                value -> context.update(state -> state.search(value)), context::redraw));
        return input.content(context.state().query());
    }

    private void closeSearchInput(Player player) {
        var input = searchInputs.remove(player.getUniqueId());
        if (input != null) input.close();
    }

    private void openCreateHomeInput(Player player) {
        Location savedPosition = player.getLocation().clone();
        FloatingMenus.input(player, MenuInputForm.builder(Component.text(languageService.t(player, Message.HOME_ACTION_CREATE)))
                .field(MenuInputForm.Field.text("name", Component.text(languageService.t(player, Message.HOME_USAGE_NAME_RULE)), "", 48))
                .cancel(Component.text(languageService.t(player, Message.BACK)))
                .submit(Component.text(languageService.t(player, Message.HOME_ACTION_CREATE)), (responder, inputValues) -> {
                    String name = inputValues.text("name").strip();
                    HomeWriteResult result = saveHome(responder, name, savedPosition, true);
                    if (result != HomeWriteResult.SAVED) {
                        sendHomeWriteFailure(responder, result);
                        return;
                    }
                    responder.sendMessage(homeMessage(responder, Message.HOME_SET_RICH,
                            name, NamedTextColor.GREEN));
                    List<String> sorted = getHomeNames(responder);
                    sorted.sort(String.CASE_INSENSITIVE_ORDER);
                    homeScreen.open(responder, HomeMenuState.created(name, sorted.indexOf(name)));
                }).build());
    }

    private void openDeleteConfirmMenu(FloatingMenuContext<HomeMenuState> context, String homeName) {
        Player player = context.player();
        UiNode content = HomeMenuComposition.confirmation(confirmDeleteLabel(player, homeName),
                Component.text(languageService.t(player, Message.HOME_CONFIRM_DELETE), NamedTextColor.RED),
                (p, _) -> {
                    if (deleteHome(p, homeName)) p.sendMessage(homeMessage(p, Message.HOME_DELETED_RICH, homeName, NamedTextColor.GREEN));
                    else p.sendMessage(homeMessage(p, Message.HOME_NOT_FOUND_RICH, homeName, NamedTextColor.RED));
                    homeScreen.update(p, state -> state.focus(null));
                });
        context.openPopover(MenuPopover.attached(UiKey.item("action:delete"), Component.text(homeName, NamedTextColor.GOLD), content, languageService.text(player, Message.CLOSE, NamedTextColor.WHITE)).exclusive());
    }

    private void openUpdateConfirmMenu(FloatingMenuContext<HomeMenuState> context, String homeName, Location proposedLocation) {
        Player player = context.player();
        Location target = proposedLocation.clone();
        UiNode content = HomeMenuComposition.confirmation(confirmUpdateLabel(player, homeName, target),
                Component.text(languageService.t(player, Message.HOME_CONFIRM_UPDATE), NamedTextColor.GREEN),
                (p, handle) -> {
                    if (getHomeEntry(p, homeName) == null) {
                        handle.feedback(homeMessage(p, Message.HOME_NOT_FOUND_RICH,
                                homeName, NamedTextColor.RED), FloatingMenuFeedbackKind.ERROR);
                    } else {
                        setHome(p, homeName, target);
                        handle.feedback(homeMessage(p, Message.HOME_UPDATED_RICH, homeName, NamedTextColor.GREEN), FloatingMenuFeedbackKind.SUCCESS);
                        homeScreen.update(p, state -> state.focus(homeName));
                    }
                });
        context.openPopover(MenuPopover.attached(UiKey.item("action:update"), Component.text(homeName, NamedTextColor.GOLD), content, languageService.text(player, Message.CLOSE, NamedTextColor.WHITE)).exclusive());
    }

    private MenuPanel homeHelpPanel(Player player, Runnable redraw) {
        return new MenuPanel() {
            private int page;
            @Override public MenuScene render() {
                Message[] usage = {Message.HOME_USAGE_SETHOME,Message.HOME_USAGE_HOME,Message.HOME_USAGE_DELHOME,Message.HOME_USAGE_NAME_RULE};
                var lines = java.util.Arrays.stream(usage).<Component>map(message ->
                        Component.text(languageService.t(player,message),NamedTextColor.WHITE)).toList();
                return MenuScene.screen("home-help", languageService.text(player, Message.HOME_USAGE_BOOK, NamedTextColor.GOLD)).content(HomeMenuComposition.help(
                        lines, page, selected -> { page = selected; redraw.run(); })).build();
            }
        };
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        closeSearchInput(event.getPlayer());
        homeScreen.forget(event.getPlayer());
    }

    private Component summaryLabel(Player player, int homeCount) {
        int max = getMaxHomes(player);
        return Component.text(languageService.t(player, Message.HOME_MENU_TITLE),
                        NamedTextColor.LIGHT_PURPLE)
                .append(Component.newline())
                .append(Component.text(languageService.t(player,
                        Message.HOME_SUMMARY_COUNT, homeCount, max), NamedTextColor.WHITE));
    }

    private Component homeDetails(Player player, String homeName) {
        Location location = getHome(player, homeName);
        Component label = Component.text(homeName, NamedTextColor.YELLOW);
        if (location != null) {
            label = label.append(Component.newline())
                    .append(Component.text(languageService.t(player, Message.HOME_WORLD,
                            readableWorldName(player, location.getWorld())), NamedTextColor.WHITE))
                    .append(Component.newline())
                    .append(Component.text(languageService.t(player, Message.HOME_LOCATION,
                            location.getBlockX(), location.getBlockY(), location.getBlockZ()), NamedTextColor.WHITE))
                    .append(Component.newline())
                    .append(Component.text(languageService.t(player, Message.HOME_DISTANCE,
                            distanceText(player, location)), NamedTextColor.WHITE));
        } else {
            label = label.append(Component.newline())
                    .append(Component.text(languageService.t(player,
                            Message.HOME_LOCATION_INVALID), NamedTextColor.RED));
        }
        return label;
    }

    private Component confirmDeleteLabel(Player player, String homeName) {
        return Component.text(languageService.t(player, Message.HOME_CONFIRM_DELETE), NamedTextColor.RED)
                .append(Component.newline())
                .append(homeMessage(player, Message.HOME_CONFIRM_DELETE_DETAIL_RICH,
                        homeName, NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text(languageService.t(player,
                        Message.HOME_CONFIRM_DELETE_WARNING), NamedTextColor.WHITE));
    }

    private Component confirmUpdateLabel(Player player, String homeName, Location target) {
        return homeMessage(player, Message.HOME_CONFIRM_UPDATE_DETAIL_RICH,
                        homeName, NamedTextColor.WHITE)
                .append(Component.newline())
                .append(Component.text(languageService.t(player, Message.HOME_WORLD,
                        readableWorldName(player, target.getWorld())), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text(languageService.t(player, Message.HOME_LOCATION,
                        target.getBlockX(), target.getBlockY(), target.getBlockZ()),
                        NamedTextColor.YELLOW));
    }

    private Component homeMessage(Player player, Message message, String homeName, NamedTextColor baseColor) {
        return languageService.rich(player, message, baseColor,
                RichArg.component("home", Component.text(homeName, NamedTextColor.YELLOW)));
    }

    private HomeWriteResult saveHome(Player player, String name, Location location,
                                     boolean createOnly) {
        if (!isValidHomeName(name)) return HomeWriteResult.INVALID_NAME;
        List<String> existing = getHomeNames(player);
        if (existing.contains(name)) {
            if (createOnly) return HomeWriteResult.ALREADY_EXISTS;
        } else if (existing.size() >= getMaxHomes(player)) {
            return HomeWriteResult.MAX_REACHED;
        }
        setHome(player, name, location);
        if (existing.isEmpty()) returnTip.offer(player, name);
        return HomeWriteResult.SAVED;
    }

    private void sendHomeWriteFailure(Player player, HomeWriteResult result) {
        switch (result) {
            case INVALID_NAME -> sendInvalidHomeName(player);
            case ALREADY_EXISTS -> player.sendMessage(languageService.text(player,
                    Message.HOME_ALREADY_EXISTS, NamedTextColor.YELLOW));
            case MAX_REACHED -> player.sendMessage(languageService.text(player,
                    Message.HOME_MAX_REACHED, NamedTextColor.RED, getMaxHomes(player)));
            case SAVED -> throw new IllegalArgumentException("No home write failure to report");
        }
    }

    private void setHome(Player player, String name, Location location) {
        Location loc = location.clone();
        World world = Objects.requireNonNull(loc.getWorld(), "Home location needs a world");
        String value = world.getName() + ":"
                + loc.getX() + "," + loc.getY() + "," + loc.getZ() + ","
                + loc.getYaw() + "," + loc.getPitch();
        store.put(player.getUniqueId(), name, new HomeStore.Entry(value));
    }

    private Location getHome(Player player, String name) {
        HomeStore.Entry entry = getHomeEntry(player, name);
        if (entry == null) return null;
        return parseLocation(entry.locationRaw(), player.getName(), name);
    }

    private HomeStore.Entry getHomeEntry(Player player, String name) {
        return store.get(player.getUniqueId(), name);
    }

    private boolean deleteHome(Player player, String name) {
        return store.remove(player.getUniqueId(), name);
    }

    private List<String> getHomeNames(Player player) {
        return store.names(player.getUniqueId());
    }

    private void teleportHome(Player player, String name) {
        Location loc = getHome(player, name);
        if (loc == null) {
            player.sendMessage(homeMessage(player, Message.HOME_NOT_FOUND_RICH, name, NamedTextColor.RED));
            return;
        }
        homeScreen.flow(player).ifPresent(flow -> flow.close());
        player.teleportAsync(loc).whenComplete((success, error) -> {
            if (!plugin.isEnabled()) return;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (error == null && Boolean.TRUE.equals(success)) {
                    player.sendMessage(homeMessage(player, Message.HOME_TELEPORTED_RICH, name, NamedTextColor.GREEN));
                } else {
                    player.sendMessage(languageService.text(player, Message.HOME_TELEPORT_FAILED, NamedTextColor.RED));
                }
            });
        });
    }

    /** "world:x,y,z,yaw,pitch"  →  Location，格式有误返回 null */
    private Location parseLocation(String raw, String playerName, String homeName) {
        int colon = raw.indexOf(':');
        if (colon < 1) return malformed(playerName, homeName);
        World world = plugin.getServer().getWorld(raw.substring(0, colon));
        if (world == null) return malformed(playerName, homeName);
        String[] c = raw.substring(colon + 1).split(",", 5);
        if (c.length != 5) return malformed(playerName, homeName);
        try {
            return new Location(world,
                    Double.parseDouble(c[0]),
                    Double.parseDouble(c[1]),
                    Double.parseDouble(c[2]),
                    Float.parseFloat(c[3]),
                    Float.parseFloat(c[4]));
        } catch (NumberFormatException e) {
            return malformed(playerName, homeName);
        }
    }

    private Location malformed(String player, String home) {
        plugin.getLogger().warning("Malformed home entry: player=" + player + " home=" + home);
        return null;
    }

    private boolean isValidHomeName(String name) {
        return !name.isBlank()
                && name.indexOf('<') < 0
                && name.indexOf('>') < 0
                && name.indexOf(':') < 0;
    }

    private void sendInvalidHomeName(Player player) {
        player.sendMessage(languageService.text(player, Message.HOME_INVALID_NAME, NamedTextColor.RED));
    }

    private String readableWorldName(Player player, World world) {
        return switch (world.getEnvironment()) {
            case NORMAL -> world.getName();
            case NETHER -> world.getName() + "  " + languageService.t(player, Message.HOME_WORLD_NETHER);
            case THE_END -> world.getName() + "  " + languageService.t(player, Message.HOME_WORLD_END);
            case CUSTOM -> world.getName();
        };
    }

    private String distanceText(Player player, Location location) {
        if (!player.getWorld().equals(location.getWorld())) {
            return languageService.t(player, Message.HOME_DISTANCE_OTHER_WORLD);
        }
        return languageService.t(player, Message.HOME_DISTANCE_BLOCKS,
                Math.round(player.getLocation().distance(location)));
    }

    private enum HomeWriteResult {
        SAVED,
        INVALID_NAME,
        ALREADY_EXISTS,
        MAX_REACHED
    }

}
