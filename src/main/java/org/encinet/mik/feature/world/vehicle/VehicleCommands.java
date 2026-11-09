package org.encinet.mik.feature.world.vehicle;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import net.kyori.adventure.text.format.NamedTextColor;

/** The command only opens the workspace. All operations live in feature-owned controls. */
final class VehicleCommands {
    private final VehicleModule module;
    private final LanguageService language;
    VehicleCommands(VehicleModule module, LanguageService language) { this.module = module; this.language = language; }
    void register(LifecycleEventManager<Plugin> manager) {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(build(),
                language.t(Language.DEFAULT, Message.VEHICLE_COMMAND_DESCRIPTION)));
    }
    LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("vehicle").requires(source -> source.getSender().hasPermission("mik.vehicle.use")
                || source.getSender().hasPermission("mik.vehicle.admin")).executes(context -> {
            var sender = context.getSource().getSender();
            if (!(sender instanceof Player player)) {
                sender.sendMessage(language.text(Language.DEFAULT, Message.PLAYER_ONLY, NamedTextColor.RED)); return 0;
            }
            try { module.openMenu(player); return 1; }
            catch (IllegalArgumentException | IllegalStateException exception) {
                player.sendMessage(language.text(player, Message.VEHICLE_ERROR, NamedTextColor.RED, exception.getMessage())); return 0;
            }
        }).build();
    }
}
