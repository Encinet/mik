package org.encinet.mik.feature.server.console;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.encinet.mik.integration.mikd.ConsolePort;
import org.encinet.mik.integration.mikd.ConsolePort.CommandOutcome;
import org.encinet.mik.integration.mikd.ConsolePort.Work;

import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public final class PaperConsoleModule {
    private final JavaPlugin plugin;
    private final ConsolePort port;
    private ConsolePort.Registration registration;

    public PaperConsoleModule(JavaPlugin plugin, ConsolePort port) {
        this.plugin = plugin;
        this.port = port;
    }

    public void enable() {
        var server = (CraftServer) plugin.getServer();
        var paper = server.getServer();
        var scheduler = server.getScheduler();
        Consumer<String> submit = line -> paper.handleConsoleInput(line, paper.createCommandSourceStack());
        registration = port.register("native", "Paper console",
                (line, work) -> dispatch(scheduler, submit, line, work),
                new PaperConsoleHistory(Path.of("logs")));
    }

    private CommandOutcome dispatch(BukkitScheduler scheduler, Consumer<String> submit, String line, Work work)
            throws InterruptedException {
        if (!work.active()) return CommandOutcome.EXPIRED;
        try {
            var pending = scheduler.callSyncMethod(plugin, () -> {
                if (!work.active()) return CommandOutcome.EXPIRED;
                submit.accept(line);
                return CommandOutcome.SUBMITTED;
            });
            try {
                long millis = TimeUnit.NANOSECONDS.toMillis(work.remainingNanos());
                return pending.get(Math.max(1, millis), TimeUnit.MILLISECONDS);
            } catch (TimeoutException timeout) {
                return pending.cancel(false) ? CommandOutcome.EXPIRED : CommandOutcome.UNCONFIRMED;
            } finally {
                if (!pending.isDone()) pending.cancel(false);
            }
        } catch (IllegalPluginAccessException | CancellationException disabled) {
            return CommandOutcome.EXPIRED;
        } catch (ExecutionException failed) {
            return CommandOutcome.UNCONFIRMED;
        }
    }

    public void disable() {
        var current = registration;
        registration = null;
        if (current != null) current.close();
    }
}
