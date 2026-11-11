package org.encinet.mik.feature.community.identity;

import org.encinet.mik.shared.role.RolePermissions;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.function.Supplier;
import java.util.function.Consumer;

/** Declares and executes the complete Minecraft-side {@code /bind} command tree. */
final class IdentityBindingCommandRegistrar {

    private static final String ADMIN_PERMISSION = RolePermissions.CUSTODIAN;

    private final JavaPlugin plugin;
    private final LanguageService languages;
    private final IdentityBindingRuntime runtime;
    private final IdentityBindingCommandPresenter presenter;
    private final Executor writes;

    IdentityBindingCommandRegistrar(
            JavaPlugin plugin,
            LanguageService languages,
            IdentityBindingRuntime runtime,
            IdentityBindingCommandPresenter presenter,
            Executor writes
    ) {
        this.plugin = plugin;
        this.languages = languages;
        this.runtime = runtime;
        this.presenter = presenter;
        this.writes = writes;
    }

    void register(LifecycleEventManager<Plugin> manager) {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(Commands.literal("bind")
                            .executes(context -> showHelp(context.getSource().getSender()))
                            .then(Commands.literal("list")
                                    .executes(context -> listOwn(
                                            context.getSource().getSender())))
                            .then(Commands.literal("cancel")
                                    .then(Commands.argument("platform", StringArgumentType.word())
                                            .suggests((_, builder) ->
                                                    suggestPlatforms(builder))
                                            .executes(context -> cancel(
                                                    context.getSource().getSender(),
                                                    StringArgumentType.getString(
                                                            context, "platform")))))
                            .then(Commands.literal("unlink")
                                    .executes(context -> listOwn(context.getSource().getSender()))
                                    .then(Commands.argument("binding", StringArgumentType.word())
                                            .suggests((context, builder) -> suggestBindings(
                                                    context.getSource().getSender(), builder))
                                            .executes(context -> unlinkOwn(
                                                    context.getSource().getSender(),
                                                    StringArgumentType.getString(context, "binding")))))
                            .then(Commands.argument("platform", StringArgumentType.word())
                                    .suggests((_, builder) ->
                                            suggestPlatforms(builder))
                                    .executes(context -> issueCode(
                                            context.getSource().getSender(),
                                            StringArgumentType.getString(context, "platform"))))
                            .build(),
                    languages.t(Language.DEFAULT, Message.IDENTITY_COMMAND_DESCRIPTION));

            commands.register(Commands.literal("bindadmin")
                            .requires(source -> source.getSender().hasPermission(ADMIN_PERMISSION))
                            .executes(context -> showAdminHelp(
                                    context.getSource().getSender()))
                            .then(Commands.literal("lookup")
                                    .then(Commands.argument("player", StringArgumentType.word())
                                            .executes(context -> adminLookup(
                                                    context.getSource().getSender(),
                                                    StringArgumentType.getString(
                                                            context, "player")))))
                            .then(Commands.literal("unlink")
                                    .then(Commands.argument("binding", StringArgumentType.word())
                                            .then(Commands.literal("confirm")
                                                    .executes(context -> adminUnlink(
                                                            context.getSource().getSender(),
                                                            StringArgumentType.getString(context, "binding"))))))
                            .build(),
                    languages.t(Language.DEFAULT,
                            Message.IDENTITY_ADMIN_COMMAND_DESCRIPTION));
        });
    }

    private int issueCode(CommandSender sender, String requestedPlatform) {
        Player player = requirePlayer(sender);
        if (player == null || !checkAvailable(sender)) {
            return 0;
        }
        IdentityPlatform platform = runtime.platform(requestedPlatform).orElse(null);
        if (platform == null) {
            presenter.unsupportedPlatform(sender, requestedPlatform, true);
            return 0;
        }
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        return mutate(sender, () -> runtime.issueCode(playerId, playerName, platform.id()), issued -> {
            if (issued.isEmpty()) presenter.unsupportedPlatform(sender, requestedPlatform, false);
            else presenter.codeIssued(sender, platform, issued.get());
        });
    }

    private int listOwn(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null || !checkAvailable(sender)) {
            return 0;
        }
        try {
            presenter.ownBindings(sender, runtime.findByPlayer(player.getUniqueId()));
            return Command.SINGLE_SUCCESS;
        } catch (IdentityBindingException error) {
            return storageError(sender, error);
        }
    }

    private int cancel(CommandSender sender, String requestedPlatform) {
        Player player = requirePlayer(sender);
        if (player == null || !checkAvailable(sender)) {
            return 0;
        }
        IdentityPlatform platform = runtime.platform(requestedPlatform).orElse(null);
        if (platform == null) {
            presenter.unsupportedPlatform(sender, requestedPlatform, false);
            return 0;
        }
        UUID playerId = player.getUniqueId();
        return mutate(sender, () -> runtime.cancelCode(playerId, platform.id()),
                cancelled -> presenter.codeCancelled(sender, cancelled, platform));
    }

    private int unlinkOwn(CommandSender sender, String selection) {
        Player player = requirePlayer(sender);
        if (player == null || !checkAvailable(sender)) return 0;
        Optional<UUID> bindingId = parseUuid(selection);
        if (bindingId.isEmpty()) {
            presenter.bindingNotOwned(sender);
            return 0;
        }
        UUID playerId = player.getUniqueId();
        return mutate(sender, () -> runtime.unlinkOwned(playerId, bindingId.get()), removed -> {
            if (removed.isEmpty()) presenter.bindingNotOwned(sender);
            else {
                presenter.bindingUnlinked(sender, removed.get());
                presenter.ownBindings(sender, runtime.findByPlayer(playerId));
            }
        });
    }

    private int adminLookup(CommandSender sender, String playerName) {
        if (!checkAvailable(sender)) {
            return 0;
        }
        try {
            List<IdentityBinding> bindings = parseUuid(playerName)
                    .map(runtime::findByPlayer)
                    .orElseGet(() -> runtime.findByPlayerName(playerName));
            presenter.adminLookup(sender, playerName, bindings);
            return Command.SINGLE_SUCCESS;
        } catch (IdentityBindingException error) {
            return storageError(sender, error);
        }
    }

    private int adminUnlink(CommandSender sender, String selection) {
        if (!checkAvailable(sender)) return 0;
        Optional<UUID> bindingId = parseUuid(selection);
        if (bindingId.isEmpty()) {
            presenter.adminBindingMissing(sender);
            return 0;
        }
        return mutate(sender, () -> runtime.unlinkById(bindingId.get()), removed -> {
            if (removed.isEmpty()) presenter.adminBindingMissing(sender);
            else presenter.adminBindingUnlinked(sender, removed.get());
        });
    }

    private int showHelp(CommandSender sender) {
        presenter.help(sender);
        return Command.SINGLE_SUCCESS;
    }

    private int showAdminHelp(CommandSender sender) {
        presenter.adminHelp(sender);
        return Command.SINGLE_SUCCESS;
    }

    private CompletableFuture<Suggestions> suggestPlatforms(SuggestionsBuilder builder) {
        runtime.platformSuggestions(builder.getRemaining()).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestBindings(CommandSender sender, SuggestionsBuilder builder) {
        if (sender instanceof Player player && runtime.isAvailable()) {
            String remaining = builder.getRemainingLowerCase();
            for (IdentityBinding binding : runtime.findByPlayer(player.getUniqueId())) {
                String id = binding.bindingId().toString();
                if (id.startsWith(remaining)) builder.suggest(id,
                        new com.mojang.brigadier.LiteralMessage(presenter.selectionLabel(binding)));
            }
        }
        return builder.buildFuture();
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        presenter.playerOnly(sender);
        return null;
    }

    private boolean checkAvailable(CommandSender sender) {
        if (runtime.isAvailable()) {
            return true;
        }
        presenter.unavailable(sender);
        return false;
    }

    private <T> int mutate(CommandSender sender, Supplier<T> operation, Consumer<T> response) {
        try {
            writes.execute(() -> {
                try {
                    T result = operation.get();
                    replyOnMainThread(sender, () -> response.accept(result));
                } catch (IdentityBindingException error) {
                    replyOnMainThread(sender, () -> storageError(sender, error));
                }
            });
            return Command.SINGLE_SUCCESS;
        } catch (RejectedExecutionException ignored) {
            presenter.unavailable(sender);
            return 0;
        }
    }

    private void replyOnMainThread(CommandSender sender, Runnable reply) {
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!(sender instanceof Player player) || player.isOnline()) reply.run();
            });
        } catch (IllegalPluginAccessException ignored) {
            // Bukkit cancels plugin tasks on shutdown; completed database work stays committed.
        }
    }

    private int storageError(CommandSender sender, IdentityBindingException error) {
        plugin.getLogger().log(Level.SEVERE, "Identity binding operation failed", error);
        presenter.storageError(sender);
        return 0;
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException error) {
            return Optional.empty();
        }
    }
}
