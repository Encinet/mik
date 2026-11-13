package org.encinet.mik.feature.server.monitor;

import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.function.Predicate;

/** Prevents native entity-target commands from bypassing per-viewer visibility. */
final class MonitorCommandGuard {
    private MonitorCommandGuard() {}

    static boolean targetsMonitor(Player sender, String command, Predicate<Player> monitoring) {
        CommandSourceStack source = ((CraftPlayer) sender).getHandle().createCommandSourceStack();
        var parsed = source.getServer().getCommands().getDispatcher().parse(command, source);
        return targetsMonitor(parsed.getContext(), monitoring);
    }

    static boolean targetsMonitor(CommandContextBuilder<CommandSourceStack> context, Predicate<Player> monitoring) {
        for (var argument : context.getArguments().values()) {
            if (!(argument.getResult() instanceof EntitySelector selector)) continue;
            try {
                for (var entity : selector.findEntities(context.getSource())) {
                    if (entity.getBukkitEntity() instanceof Player player && monitoring.test(player)) return true;
                }
            } catch (CommandSyntaxException ignored) {
                // Let the command report its normal permission or selector error.
            }
        }
        return context.getChild() != null && targetsMonitor(context.getChild(), monitoring);
    }
}
