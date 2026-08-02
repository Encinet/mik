package org.encinet.mik.module.space;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Configurable non-Euclidean topology made from reversible, same-world spatial seams.
 * Every outermost entity root uses Paper's passenger-aware same-world relocation, then
 * the complete nested passenger tree receives transformed orientation and velocity.
 */
public final class NonEuclideanSpaceModule
        implements Listener, NonEuclideanSpaceService {

    private static final String CONFIG_RESOURCE = "non-euclidean-spaces.yml";
    private static final List<String> EXAMPLE_RESOURCES = List.of(
            "examples/non-euclidean/infinite-stairwell.yml",
            "examples/non-euclidean/endless-corridor.yml",
            "examples/non-euclidean/impossible-corner.yml",
            "examples/non-euclidean/looping-fall.yml");
    private static final String COMMAND_PERMISSION = "mik.command.space";

    private final JavaPlugin plugin;
    private final SpaceConfigurationLoader loader = new SpaceConfigurationLoader();
    private final Map<String, SpaceLink> configuredLinks = new LinkedHashMap<>();
    private final Map<String, DynamicLink> dynamicLinks = new LinkedHashMap<>();

    private volatile SpaceNetwork network = SpaceNetwork.empty();
    private SpaceChunkTicketManager chunkTickets;
    private SpaceTraversalController traversalController;
    private Path configPath;
    private boolean configurationEnabled = true;
    private boolean enabled;

    public NonEuclideanSpaceModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        if (enabled) {
            return;
        }
        ensureConfigExists();
        chunkTickets = new SpaceChunkTicketManager(plugin);
        traversalController = new SpaceTraversalController(plugin, this::snapshot);
        SpaceReloadResult result = reload();
        if (!result.successful()) {
            plugin.getLogger().severe("Non-Euclidean spaces started with no configured links");
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getPluginManager().registerEvents(traversalController, plugin);
        Bukkit.getServicesManager().register(
                NonEuclideanSpaceService.class, this, plugin, ServicePriority.Normal);
        enabled = true;
        plugin.getLogger().info("NonEuclideanSpaceModule enabled with "
                + network.links().size() + " spatial link(s)");
    }

    public void disable() {
        if (!enabled && chunkTickets == null) {
            return;
        }
        HandlerList.unregisterAll(this);
        if (traversalController != null) {
            HandlerList.unregisterAll(traversalController);
            traversalController.close();
            traversalController = null;
        }
        Bukkit.getServicesManager().unregister(NonEuclideanSpaceService.class, this);
        dynamicLinks.clear();
        configuredLinks.clear();
        network = SpaceNetwork.empty();
        if (chunkTickets != null) {
            chunkTickets.close();
            chunkTickets = null;
        }
        enabled = false;
    }

    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                Commands.literal("mikspace")
                        .requires(source -> source.getSender().hasPermission(COMMAND_PERMISSION))
                        .executes(context -> showStatus(context.getSource().getSender()))
                        .then(Commands.literal("status")
                                .executes(context -> showStatus(context.getSource().getSender())))
                        .then(Commands.literal("list")
                                .executes(context -> listLinks(context.getSource().getSender())))
                        .then(Commands.literal("reload")
                                .executes(context -> reloadCommand(context.getSource().getSender())))
                        .build(),
                "Reload and inspect non-Euclidean spatial links"));
    }

    @Override
    public SpaceNetwork snapshot() {
        return network;
    }

    @Override
    public SpaceReloadResult reload() {
        requirePrimaryThread();
        if (configPath == null) {
            configPath = plugin.getDataFolder().toPath().resolve(CONFIG_RESOURCE);
        }
        try {
            SpaceConfiguration loaded = loader.load(configPath);
            Map<String, SpaceLink> nextConfigured = index(loaded.links());
            for (String id : nextConfigured.keySet()) {
                if (dynamicLinks.containsKey(id)) {
                    return failedReload(List.of("Configured link '" + id
                            + "' conflicts with a programmatically registered link"));
                }
            }
            SpaceNetwork next = buildNetwork(
                    loaded.enabled(), nextConfigured, dynamicLinks);
            configuredLinks.clear();
            configuredLinks.putAll(nextConfigured);
            configurationEnabled = loaded.enabled();
            installNetwork(next);
            logUnavailableWorlds(next);
            return SpaceReloadResult.success(nextConfigured.size());
        } catch (SpaceConfigurationException error) {
            return failedReload(error.problems());
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException error) {
            return failedReload(List.of(error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage()));
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.SEVERE,
                    "Unexpected failure while loading " + CONFIG_RESOURCE, error);
            return failedReload(List.of(error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage()));
        }
    }

    @Override
    public SpaceRegistration register(SpaceLink link) {
        requirePrimaryThread();
        Objects.requireNonNull(link, "link");
        if (configuredLinks.containsKey(link.id())
                || dynamicLinks.containsKey(link.id())) {
            throw new IllegalArgumentException(
                    "A spatial link named '" + link.id() + "' is already registered");
        }
        DynamicLink registration = new DynamicLink(link);
        dynamicLinks.put(link.id(), registration);
        try {
            installNetwork(buildNetwork(configurationEnabled, configuredLinks, dynamicLinks));
        } catch (RuntimeException error) {
            dynamicLinks.remove(link.id(), registration);
            throw error;
        }
        logUnavailableWorlds(network);
        return registration;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (chunkTickets != null) {
            chunkTickets.update(network);
        }
    }

    private void installNetwork(SpaceNetwork next) {
        network = next;
        if (traversalController != null) {
            traversalController.networkChanged();
        }
        if (chunkTickets != null) {
            chunkTickets.update(next);
        }
    }

    private SpaceNetwork buildNetwork(
            boolean configurationEnabled,
            Map<String, SpaceLink> configured,
            Map<String, DynamicLink> dynamic
    ) {
        if (!configurationEnabled) {
            return SpaceNetwork.empty();
        }
        List<SpaceLink> links = new ArrayList<>(configured.values());
        dynamic.values().stream()
                .filter(DynamicLink::active)
                .map(DynamicLink::link)
                .forEach(links::add);
        return SpaceNetwork.of(links);
    }

    private Map<String, SpaceLink> index(List<SpaceLink> links) {
        Map<String, SpaceLink> result = new LinkedHashMap<>();
        for (SpaceLink link : links) {
            if (result.putIfAbsent(link.id(), link) != null) {
                throw new IllegalArgumentException(
                        "Duplicate configured link id: " + link.id());
            }
        }
        return result;
    }

    private void ensureConfigExists() {
        configPath = plugin.getDataFolder().toPath().resolve(CONFIG_RESOURCE);
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IllegalStateException("Could not create plugin data folder");
        }
        saveBundledResource(CONFIG_RESOURCE);
        for (String example : EXAMPLE_RESOURCES) {
            saveBundledResource(example);
        }
    }

    private void saveBundledResource(String resource) {
        if (!Files.exists(plugin.getDataFolder().toPath().resolve(resource))) {
            plugin.saveResource(resource, false);
        }
    }

    private SpaceReloadResult failedReload(List<String> problems) {
        for (String problem : problems) {
            plugin.getLogger().severe(CONFIG_RESOURCE + ": " + problem);
        }
        return SpaceReloadResult.failure(problems);
    }

    private void logUnavailableWorlds(SpaceNetwork next) {
        for (SpaceLink link : next.links()) {
            if (SpaceWorldResolver.resolve(link.first().world()) == null) {
                plugin.getLogger().warning("Spatial link '" + link.id()
                        + "' is waiting for unloaded world: " + link.first().world());
            }
        }
    }

    private int showStatus(org.bukkit.command.CommandSender sender) {
        sender.sendMessage(Component.text(
                "Spatial network: " + (configurationEnabled ? "enabled" : "disabled")
                        + ", " + network.links().size() + " active link(s)",
                configurationEnabled ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        return Command.SINGLE_SUCCESS;
    }

    private int listLinks(org.bukkit.command.CommandSender sender) {
        if (network.links().isEmpty()) {
            sender.sendMessage(Component.text("No active spatial links.", NamedTextColor.GRAY));
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage(Component.text("Active spatial links:", NamedTextColor.AQUA));
        for (SpaceLink link : network.links()) {
            sender.sendMessage(Component.text("- " + link.id()
                    + " [entrances="
                    + link.entrances().name().toLowerCase(Locale.ROOT) + "]: "
                    + describe(link.first()) + " ↔ " + describe(link.second()),
                    NamedTextColor.GRAY));
        }
        return Command.SINGLE_SUCCESS;
    }

    private String describe(SpaceSurface surface) {
        SpaceVector center = surface.frame().origin();
        return String.format(Locale.ROOT,
                "%s[%s] %.2f×%.2f @ (%.2f, %.2f, %.2f)",
                surface.id(), surface.world(),
                surface.aperture().width(), surface.aperture().height(),
                center.x(), center.y(), center.z());
    }

    private int reloadCommand(org.bukkit.command.CommandSender sender) {
        SpaceReloadResult result = reload();
        if (result.successful()) {
            sender.sendMessage(Component.text("Loaded " + result.linkCount()
                    + " configured spatial link(s).", NamedTextColor.GREEN));
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage(Component.text(
                "Spatial configuration was not changed; check the server log for "
                        + result.problems().size() + " problem(s).",
                NamedTextColor.RED));
        return 0;
    }

    private void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Spatial service mutations must run on the server thread");
        }
    }

    private final class DynamicLink implements SpaceRegistration {

        private final SpaceLink link;
        private volatile boolean active = true;

        private DynamicLink(SpaceLink link) {
            this.link = link;
        }

        private SpaceLink link() {
            return link;
        }

        @Override
        public String linkId() {
            return link.id();
        }

        @Override
        public boolean active() {
            return active;
        }

        @Override
        public void close() {
            requirePrimaryThread();
            if (!active) {
                return;
            }
            active = false;
            dynamicLinks.remove(link.id(), this);
            installNetwork(buildNetwork(
                    configurationEnabled, configuredLinks, dynamicLinks));
        }
    }
}
