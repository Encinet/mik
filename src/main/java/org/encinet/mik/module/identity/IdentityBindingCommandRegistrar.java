package org.encinet.mik.module.identity;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.Mik;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/** Declares and executes the complete Minecraft-side {@code /bind} command tree. */
final class IdentityBindingCommandRegistrar {

    private static final String ADMIN_PERMISSION = "group." + Mik.GROUP_MANAGER;

    private final JavaPlugin plugin;
    private final LanguageService languages;
    private final IdentityBindingRuntime runtime;
    private final IdentityBindingCommandPresenter presenter;

    IdentityBindingCommandRegistrar(
            JavaPlugin plugin,
            LanguageService languages,
            IdentityBindingRuntime runtime,
            IdentityBindingCommandPresenter presenter
    ) {
        this.plugin = plugin;
        this.languages = languages;
        this.runtime = runtime;
        this.presenter = presenter;
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
                                            .suggests((context, builder) ->
                                                    suggestPlatforms(builder))
                                            .executes(context -> cancel(
                                                    context.getSource().getSender(),
                                                    StringArgumentType.getString(
                                                            context, "platform")))))
                            .then(Commands.literal("unlink")
                                    .then(Commands.argument(
                                                    "platform", StringArgumentType.word())
                                            .suggests((context, builder) ->
                                                    suggestPlatforms(builder))
                                            .executes(context -> unlinkOwn(
                                                    context.getSource().getSender(),
                                                    StringArgumentType.getString(
                                                            context, "platform")))))
                            .then(Commands.argument("platform", StringArgumentType.word())
                                    .suggests((context, builder) ->
                                            suggestPlatforms(builder))
                                    .executes(context -> issueCode(
                                            context.getSource().getSender(),
                                            StringArgumentType.getString(context, "platform"))))
                            .build(),
                    languages.t(Language.DEFAULT, Message.IDENTITY_COMMAND_DESCRIPTION),
                    List.of("link", "accountlink"));

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
                                    .then(Commands.argument("player", StringArgumentType.word())
                                            .then(Commands.argument(
                                                            "platform",
                                                            StringArgumentType.word())
                                                    .suggests((context, builder) ->
                                                            suggestPlatforms(builder))
                                                    .then(Commands.literal("confirm")
                                                            .executes(context -> adminUnlink(
                                                                    context.getSource().getSender(),
                                                                    StringArgumentType.getString(
                                                                            context, "player"),
                                                                    StringArgumentType.getString(
                                                                            context, "platform")))))))
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
        try {
            Optional<IdentityLinkCode> issued = runtime.issueCode(
                    player.getUniqueId(), player.getName(), platform.id());
            if (issued.isEmpty()) {
                presenter.unsupportedPlatform(sender, requestedPlatform, false);
                return 0;
            }
            presenter.codeIssued(sender, platform, issued.get());
            return Command.SINGLE_SUCCESS;
        } catch (IdentityBindingException error) {
            return storageError(sender, error);
        }
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
        try {
            presenter.codeCancelled(sender,
                    runtime.cancelCode(player.getUniqueId(), platform.id()), platform);
            return Command.SINGLE_SUCCESS;
        } catch (IdentityBindingException error) {
            return storageError(sender, error);
        }
    }

    private int unlinkOwn(CommandSender sender, String platformId) {
        Player player = requirePlayer(sender);
        if (player == null || !checkAvailable(sender)) {
            return 0;
        }
        final String platform;
        try {
            platform = ExternalIdentityKey.normalizePlatform(platformId);
        } catch (IllegalArgumentException error) {
            presenter.unsupportedPlatform(sender, platformId, false);
            return 0;
        }
        try {
            List<IdentityBinding> removed = runtime.unlinkPlayerPlatform(
                    player.getUniqueId(), platform);
            if (removed.isEmpty()) {
                presenter.bindingNotOwned(sender);
                return 0;
            }
            presenter.bindingUnlinked(sender, platform, removed.size());
            return Command.SINGLE_SUCCESS;
        } catch (IdentityBindingException error) {
            return storageError(sender, error);
        }
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

    private int adminUnlink(CommandSender sender, String playerName, String platformId) {
        if (!checkAvailable(sender)) {
            return 0;
        }
        final String platform;
        try {
            platform = ExternalIdentityKey.normalizePlatform(platformId);
        } catch (IllegalArgumentException error) {
            presenter.unsupportedPlatform(sender, platformId, false);
            return 0;
        }
        try {
            List<IdentityBinding> matches = parseUuid(playerName)
                    .map(runtime::findByPlayer)
                    .orElseGet(() -> runtime.findByPlayerName(playerName)).stream()
                    .filter(binding -> binding.externalKey().platform().equals(platform))
                    .toList();
            List<UUID> players = matches.stream().map(IdentityBinding::playerId).distinct().toList();
            if (players.size() != 1) {
                presenter.adminBindingMissing(sender);
                return 0;
            }
            List<IdentityBinding> removed = runtime.unlinkPlayerPlatform(
                    players.getFirst(), platform);
            if (removed.isEmpty()) {
                presenter.adminBindingMissing(sender);
                return 0;
            }
            presenter.adminBindingUnlinked(sender, platform,
                    removed.getFirst().playerName(), removed.size());
            return Command.SINGLE_SUCCESS;
        } catch (IdentityBindingException error) {
            return storageError(sender, error);
        }
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
