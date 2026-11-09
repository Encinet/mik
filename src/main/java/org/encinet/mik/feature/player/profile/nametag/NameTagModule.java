package org.encinet.mik.feature.player.profile.nametag;

import com.mojang.brigadier.Command;
import net.kyori.adventure.text.Component;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.player.guidance.TipScope;
import org.encinet.mik.feature.player.profile.PlayerNameTagRenderer;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.MenuPanel;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.ui.Ui;
import org.encinet.mik.shared.menu.ui.UiTemplates;
import org.encinet.mik.shared.role.RolePermissions;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/** Owns the name-tag workspace, its chat input and the custom-decoration persistence boundary. */
public final class NameTagModule {
    private final JavaPlugin plugin;
    private final LanguageService language;
    private final PlayerNameTagRenderer nameTags;
    private final Consumer<UUID> changed;
    private final Function<Player, Component> baseName;
    private NameTagStore store;
    private NameTagDirectory directory;
    private NameTagMenu menu;

    public NameTagModule(JavaPlugin plugin, LanguageService language, PlayerNameTagRenderer nameTags, Consumer<UUID> changed, Function<Player, Component> baseName, TipScope tips) {
        this.plugin = plugin;
        this.language = language;
        this.nameTags = nameTags;
        this.changed = changed;
        this.baseName = baseName;
        tips.define("nametag-customize", Message.NAMETAG_TIP_CUSTOMIZE_MM).ambient().when(RolePermissions::isMember).build();
    }

    public void enable() {
        if (menu != null) return;
        var provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider == null) {
            plugin.getLogger().warning("LuckPerms not found. Name tags are disabled.");
            return;
        }
        store = new NameTagStore(provider.getProvider());
        directory = new NameTagDirectory(provider.getProvider());
        menu = new NameTagMenu(plugin, language, nameTags, store, directory, changed, baseName);
        menu.enable();
    }

    public void disable() {
        try { if (menu != null) menu.close(); }
        finally {
            if (store != null) store.close();
            if (directory != null) directory.close();
            menu = null;
            store = null;
            directory = null;
        }
    }

    public void registerCommands(LifecycleEventManager<Plugin> lifecycle) {
        lifecycle.registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                Commands.literal("nametag").executes(context -> {
                    var sender = context.getSource().getSender();
                    if (!(sender instanceof Player player))
                        sender.sendMessage(language.text(Language.DEFAULT, Message.PLAYER_ONLY, NamedTextColor.RED));
                    else if (menu == null)
                        player.sendMessage(language.text(player, Message.NAMETAG_USER_DATA_LOAD_FAILED, NamedTextColor.RED));
                    else menu.open(player);
                    return Command.SINGLE_SUCCESS;
                }).build(), language.t(Language.DEFAULT, Message.NAMETAG_COMMAND_DESCRIPTION), List.of()));
    }

    public MenuPanel settingsPanel(Player player, Runnable redraw) {
        if (menu != null) return menu.panel(player, redraw);
        return () -> MenuScene.screen("nametag").content(UiTemplates.window("name-tags",
                Ui.text(language.text(player, Message.NAMETAG_USER_DATA_LOAD_FAILED, NamedTextColor.YELLOW))
                        .key("unavailable").build())).build();
    }
}
