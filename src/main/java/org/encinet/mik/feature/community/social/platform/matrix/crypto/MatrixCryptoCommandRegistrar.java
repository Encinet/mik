package org.encinet.mik.feature.community.social.platform.matrix.crypto;

import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.community.social.platform.matrix.client.MatrixApiErrors;
import java.util.Arrays;
import java.util.List;

/** Console-only device verification: public fingerprints and SAS never enter bridged chat. */
public final class MatrixCryptoCommandRegistrar {
    private final JavaPlugin plugin;
    private final MatrixCryptoControl control;
    private final MatrixCryptoPresenter presenter;
    private final LanguageService languages;

    public MatrixCryptoCommandRegistrar(JavaPlugin plugin, MatrixCryptoControl control, LanguageService languages) {
        this.plugin = plugin;
        this.control = control;
        this.languages = languages;
        this.presenter = new MatrixCryptoPresenter(languages);
    }

    public void register(LifecycleEventManager<Plugin> lifecycle) {
        lifecycle.registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                Commands.literal("matrixcrypto")
                        .requires(source -> source.getSender() instanceof ConsoleCommandSender)
                        .executes(context -> execute(context.getSource().getSender()::sendMessage, "status", List.of()))
                        .then(Commands.argument("operation", StringArgumentType.greedyString())
                                .suggests((_, builder) -> {
                                    List.of("status", "verify", "accept", "confirm", "cancel").stream()
                                            .filter(value -> value.startsWith(builder.getRemaining())).forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    String[] words = StringArgumentType.getString(context, "operation").strip().split("\\s+");
                                    return execute(context.getSource().getSender()::sendMessage, words[0],
                                            Arrays.asList(words).subList(1, words.length));
                                })).build(), languages.t(Language.DEFAULT, Message.SOCIAL_CRYPTO_COMMAND_DESCRIPTION)));
    }

    private int execute(java.util.function.Consumer<String> reply, String action, List<String> arguments) {
        control.execute(action, arguments).whenComplete((result, failure) -> {
            if (failure != null && !(failure.getCause() instanceof MatrixCryptoException)
                    && !(failure instanceof MatrixCryptoException)) {
                plugin.getLogger().warning("Matrix device verification failed: " + MatrixApiErrors.describe(failure));
            }
            if (plugin.isEnabled()) plugin.getServer().getScheduler().runTask(plugin,
                    () -> reply.accept(failure == null ? presenter.render(result) : presenter.failure(failure)));
        });
        return 1;
    }
}
