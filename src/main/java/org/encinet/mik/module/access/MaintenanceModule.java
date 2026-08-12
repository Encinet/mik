package org.encinet.mik.module.access;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerLoginConnection;
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.Mik;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.util.UUID;

public class MaintenanceModule implements Listener {

    private static final String HELPER_PERMISSION = "group." + Mik.GROUP_HELPER;

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private LuckPerms luckPerms;
    private volatile boolean maintenanceEnabled;

    public MaintenanceModule(JavaPlugin plugin, LanguageService languageService) {
        this.plugin = plugin;
        this.languageService = languageService;
    }

    public void enable() {
        RegisteredServiceProvider<LuckPerms> provider =
                Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider == null) {
            plugin.getLogger().warning("LuckPerms not found; MaintenanceModule disabled.");
            return;
        }
        luckPerms = provider.getProvider();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void registerCommands(LifecycleEventManager<Plugin> lifecycleManager) {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(
                        Commands.literal("maintenance")
                                .requires(source -> source.getSender().hasPermission(HELPER_PERMISSION))
                                .executes(context -> sendMaintenanceStatus(context.getSource().getSender()))
                                .then(Commands.literal("on")
                                        .executes(context -> setMaintenanceMode(
                                                context.getSource().getSender(), true)))
                                .then(Commands.literal("off")
                                        .executes(context -> setMaintenanceMode(
                                                context.getSource().getSender(), false)))
                                .build(),
                        languageService.t(Language.DEFAULT,
                                Message.MAINTENANCE_COMMAND_DESCRIPTION)));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerConnectionValidate(PlayerConnectionValidateLoginEvent event) {
        if (!isMaintenanceEnabled()) {
            return;
        }

        PlayerProfile playerProfile = playerProfile(event);
        if (playerProfile == null) {
            return;
        }

        if (canBypass(playerProfile.getId(), playerProfile.getName())) {
            return;
        }

        event.kickMessage(kickMessage());
    }

    private int sendMaintenanceStatus(CommandSender sender) {
        sender.sendMessage(Component.text(
                t(sender, Message.MAINTENANCE_STATUS, state(sender, maintenanceEnabled)),
                maintenanceEnabled ? NamedTextColor.GREEN : NamedTextColor.YELLOW));

        return Command.SINGLE_SUCCESS;
    }

    private int setMaintenanceMode(CommandSender sender, boolean requestedEnabled) {
        if (maintenanceEnabled == requestedEnabled) {
            sender.sendMessage(Component.text(t(sender, Message.MAINTENANCE_UNCHANGED,
                    state(sender, requestedEnabled)), NamedTextColor.GRAY));
            return Command.SINGLE_SUCCESS;
        }

        setMaintenanceEnabled(requestedEnabled);
        plugin.getLogger().info("Maintenance mode " + (requestedEnabled ? "enabled" : "disabled")
                + " by " + sender.getName());

        sender.sendMessage(Component.text(t(sender, Message.MAINTENANCE_SET,
                state(sender, requestedEnabled)), NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private String state(CommandSender sender, boolean enabled) {
        return t(sender, enabled
                ? Message.MAINTENANCE_STATE_ENABLED
                : Message.MAINTENANCE_STATE_DISABLED);
    }

    private String t(CommandSender sender, Message message, Object... arguments) {
        if (sender instanceof org.bukkit.entity.Player player) {
            return languageService.t(player, message, arguments);
        }
        return languageService.t(Language.DEFAULT, message, arguments);
    }

    private boolean canBypass(UUID playerUuid, String playerName) {
        if (playerUuid == null && playerName == null) {
            return false;
        }

        if (luckPerms == null) {
            return false;
        }

        if (playerUuid == null) {
            return false;
        }

        User user = luckPerms.getUserManager().getUser(playerUuid);
        return user != null && user.getCachedData().getPermissionData()
                .checkPermission(HELPER_PERMISSION)
                .asBoolean();
    }

    public boolean isMaintenanceEnabled() {
        return maintenanceEnabled;
    }

    public void setMaintenanceEnabled(boolean maintenanceEnabled) {
        this.maintenanceEnabled = maintenanceEnabled;
    }

    private Component kickMessage() {
        return Component.text()
                .append(Component.text("服务器维护中", NamedTextColor.RED))
                .append(Component.newline())
                .append(Component.text("Server is under maintenance", NamedTextColor.GRAY))
                .build();
    }

    private static PlayerProfile playerProfile(PlayerConnectionValidateLoginEvent event) {
        if (event.getConnection() instanceof PlayerConfigurationConnection configuration) {
            return configuration.getProfile();
        }
        if (event.getConnection() instanceof PlayerLoginConnection login) {
            PlayerProfile authenticated = login.getAuthenticatedProfile();
            return authenticated != null ? authenticated : login.getUnsafeProfile();
        }

        return null;
    }
}
