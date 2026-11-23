package org.encinet.mik.shell.mikd;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.integration.mikd.RequestPort;
import org.encinet.mik.protocol.ConnectionRole;
import org.encinet.mik.protocol.Status.DomainProblem;

public final class MikdStatusCommand {
    private final JavaPlugin plugin;
    private final RequestPort daemon;

    public MikdStatusCommand(JavaPlugin plugin, RequestPort daemon) {
        this.plugin = plugin;
        this.daemon = daemon;
    }

    public void register() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(Commands.literal("mikapi")
                        .requires(source -> source.getSender().hasPermission("mik.command.api"))
                        .executes(context -> send(context.getSource().getSender()))
                        .then(Commands.literal("status").executes(context -> send(context.getSource().getSender())))
                        .build(), "Show Mik API status"));
    }

    private int send(CommandSender sender) {
        daemon.status(ConnectionRole.CONTROL).whenComplete((diagnostics, failure) -> {
            if (!plugin.isEnabled()) return;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (failure != null) {
                    sender.sendMessage(Component.text("mikd unavailable", NamedTextColor.RED));
                    return;
                }
                var status = diagnostics.daemon();
                var ready = status.getReadyDomainsList();
                var output = Component.text().append(Component.text("mikd Web API: " + (status.getWebApiPort() > 0 ? "listening" : "not listening")))
                        .append(Component.newline()).append(Component.text("Public online players: "
                                + (status.getSnapshotVersion() > 0 ? Integer.toString(status.getPlayerCount()) : "unknown")))
                        .append(Component.newline()).append(Component.text("Locally ready: " + (ready.isEmpty() ? "none" : String.join(", ", ready))));
                status.getDomainProblemsMap().forEach((domain, problem) -> output.append(Component.newline()).append(Component.text(
                        domain + (problem == DomainProblem.STALE
                                ? ": serving the last valid snapshot" : ": unavailable"), NamedTextColor.YELLOW)));
                if (!diagnostics.missingWorkflows().isEmpty()) {
                    output.append(Component.newline()).append(Component.text("Incompatible workflows: "
                            + String.join(", ", diagnostics.missingWorkflows()), NamedTextColor.RED));
                    output.append(Component.newline()).append(Component.text("Deploy Paper and mikd from the same build.", NamedTextColor.YELLOW));
                }
                sender.sendMessage(output.build());
            });
        });
        return Command.SINGLE_SUCCESS;
    }
}
