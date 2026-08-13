package org.encinet.mik.module.social;

import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.afk.AfkService;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.identity.IdentityBindingManager;
import org.encinet.mik.module.social.chat.SocialChatGateway;
import org.encinet.mik.module.social.chat.SocialChatPublisher;
import org.encinet.mik.module.social.command.SocialCommand;
import org.encinet.mik.module.social.command.SocialCommandDispatcher;
import org.encinet.mik.module.social.command.SocialCommandLanguageResolver;
import org.encinet.mik.module.social.command.builtin.LinkIdentityCommand;
import org.encinet.mik.module.social.command.builtin.OnlinePlayersCommand;
import org.encinet.mik.module.social.command.builtin.PlayerProfileCommand;
import org.encinet.mik.module.social.command.builtin.ServerStatusCommand;
import org.encinet.mik.module.social.command.builtin.SocialHelpCommand;
import org.encinet.mik.module.social.command.builtin.UnknownSocialCommand;
import org.encinet.mik.module.social.command.builtin.UnlinkIdentityCommand;
import org.encinet.mik.module.social.document.SocialDocument;
import org.encinet.mik.module.social.game.BukkitMainThreadGateway;
import org.encinet.mik.module.social.game.BukkitSocialGameService;
import org.encinet.mik.module.social.game.SocialGameService;
import org.encinet.mik.module.social.management.SocialManagementCommandRegistrar;
import org.encinet.mik.module.social.platform.matrix.MatrixPlatformAdapter;
import org.encinet.mik.module.social.platform.qq.QqPlatformAdapter;
import org.encinet.mik.module.social.platform.qq.QqPlatformConfig;
import org.encinet.mik.module.social.runtime.SocialContentGuard;
import org.encinet.mik.module.social.runtime.SocialPlatformHost;
import org.encinet.mik.module.social.safety.SocialContentSafetyFilter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

/** Bukkit composition root for shared social features and platform adapters. */
public final class SocialModule {
    private final SocialPlatformHost host;
    private final SocialManagementCommandRegistrar managementCommands;

    public SocialModule(
            JavaPlugin plugin,
            IdentityBindingManager identityBindings,
            LanguageService languages,
            AfkService afkService,
            SocialChatGateway chatGateway
    ) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(languages, "languages");
        Objects.requireNonNull(chatGateway, "chatGateway");
        SocialCommandLanguageResolver languageResolver = new SocialCommandLanguageResolver(
                identityBindings, languages);
        SocialGameService game = new BukkitSocialGameService(plugin, afkService,
                new BukkitMainThreadGateway(plugin));

        List<SocialCommand<?, ?>> featureCommands = List.of(
                new OnlinePlayersCommand(game, languageResolver, languages),
                new ServerStatusCommand(game, languageResolver, languages),
                new LinkIdentityCommand(identityBindings, languageResolver, languages),
                new PlayerProfileCommand(identityBindings, game, languageResolver, languages,
                        ZoneId.systemDefault()),
                new UnlinkIdentityCommand(identityBindings, languageResolver, languages));
        List<SocialCommand<?, ?>> commands = new java.util.ArrayList<>(featureCommands);
        commands.add(new SocialHelpCommand(languageResolver, languages, featureCommands));
        commands.add(new UnknownSocialCommand(languageResolver, languages));
        SocialCommandDispatcher dispatcher = new SocialCommandDispatcher(commands);

        java.util.function.Function<Language, SocialDocument> blockedDocument = language ->
                SocialDocument.of("🛡 " + languages.t(
                                language, Message.SOCIAL_TITLE_CONTENT_HIDDEN),
                        SocialDocument.Tone.WARNING,
                                SocialDocument.paragraph(languages.t(
                                        language, Message.SOCIAL_REPLY_HIDDEN)))
                        .localized(language);
        SocialContentGuard contentGuard = loadContentGuard(plugin, blockedDocument);
        host = new SocialPlatformHost(
                List.of(new QqPlatformAdapter(plugin), new MatrixPlatformAdapter(plugin)),
                dispatcher,
                identityBindings::registerPlatform, contentGuard,
                chatGateway, identityBindings::findByPlayer, plugin.getLogger());
        managementCommands = new SocialManagementCommandRegistrar(host, languages);
    }

    public void enable() {
        host.start();
    }

    public void disable() {
        host.close();
    }

    public void registerCommands(LifecycleEventManager<Plugin> lifecycleManager) {
        managementCommands.register(lifecycleManager);
    }

    public SocialChatPublisher chatPublisher() {
        return host;
    }

    private static SocialContentGuard loadContentGuard(
            JavaPlugin plugin,
            java.util.function.Function<Language, SocialDocument> blockedDocument
    ) {
        Path legacy = plugin.getDataFolder().toPath().resolve("qq-blocked-keywords.txt");
        if (Files.isRegularFile(legacy)) {
            plugin.getLogger().warning("Legacy qq-blocked-keywords.txt is ignored; migrate it to "
                    + QqPlatformConfig.SAFETY_FILE_NAME);
        }
        Path source = plugin.getDataFolder().toPath()
                .resolve(QqPlatformConfig.SAFETY_FILE_NAME);
        try {
            if (!Files.isRegularFile(source)) {
                plugin.saveResource(QqPlatformConfig.SAFETY_FILE_NAME, false);
            }
            SocialContentSafetyFilter filter = SocialContentSafetyFilter.compile(source);
            plugin.getLogger().info("Compiled " + filter.ruleCount()
                    + " shared social safety rules into " + filter.stateCount() + " states");
            return SocialContentGuard.available(filter, blockedDocument);
        } catch (IOException | RuntimeException error) {
            plugin.getLogger().log(Level.SEVERE,
                    "Could not load shared social content safety rules", error);
            RuntimeException failure = error instanceof RuntimeException runtime
                    ? runtime : new IllegalStateException(error);
            return SocialContentGuard.unavailable(failure, blockedDocument);
        }
    }
}
