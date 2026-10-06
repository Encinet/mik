package org.encinet.mik.module.plot;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.logging.Level;

final class PlotWriteFeedback {
    private PlotWriteFeedback() { }

    static <Result> void finish(JavaPlugin plugin, LanguageService language, PlotRegistry registry,
                              Player player, CompletableFuture<Result> future, Consumer<Result> success) {
        future.whenComplete((value, failure) -> {
            if (!registry.writesOpen()) return;
            if (failure != null) {
                Throwable error = failure;
                while (error instanceof CompletionException && error.getCause() != null) error = error.getCause();
                if (error instanceof PlotProblem problem)
                    player.sendMessage(Component.text(language.t(player, problem.message(), problem.args()), NamedTextColor.RED));
                else {
                    plugin.getLogger().log(Level.WARNING, "Plot logical update failed", error);
                    player.sendMessage(Component.text(language.t(player, Message.PLOT_ERROR_STORAGE), NamedTextColor.RED));
                }
                return;
            }
            try { success.accept(value); }
            catch (RuntimeException error) {
                plugin.getLogger().log(Level.WARNING, "Plot logical update accepted but follow-up failed", error);
            }
        });
    }
}
