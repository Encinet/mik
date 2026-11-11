package org.encinet.mik.feature.community.social;

import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.player.afk.AfkService;
import org.encinet.mik.feature.community.ai.api.AiGateway;
import org.encinet.mik.feature.community.communication.AnnouncementPublisher;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.feature.community.identity.IdentityBindingManager;
import org.encinet.mik.feature.community.social.chat.SocialChatGateway;
import org.encinet.mik.feature.community.chat.bridge.SocialChatPublisher;
import org.encinet.mik.feature.community.social.chat.SocialDirectedNotice;
import org.encinet.mik.feature.community.social.command.SocialCommand;
import org.encinet.mik.feature.community.social.command.SocialCommandDispatcher;
import org.encinet.mik.feature.community.social.command.SocialCommandLanguageResolver;
import org.encinet.mik.feature.community.social.command.builtin.AiSocialCommand;
import org.encinet.mik.feature.community.social.command.builtin.LinkIdentityCommand;
import org.encinet.mik.feature.community.social.command.builtin.OnlinePlayersCommand;
import org.encinet.mik.feature.community.social.command.builtin.PlayerProfileCommand;
import org.encinet.mik.feature.community.social.command.builtin.ServerStatusCommand;
import org.encinet.mik.feature.community.social.command.builtin.SocialHelpCommand;
import org.encinet.mik.feature.community.social.command.builtin.UnknownSocialCommand;
import org.encinet.mik.feature.community.social.command.builtin.UnlinkIdentityCommand;
import org.encinet.mik.feature.community.social.document.SocialDocument;
import org.encinet.mik.feature.community.social.document.SocialAnnouncementPresenter;
import org.encinet.mik.feature.community.social.game.BukkitMainThreadGateway;
import org.encinet.mik.feature.community.social.game.BukkitSocialGameService;
import org.encinet.mik.feature.community.social.game.SocialGameService;
import org.encinet.mik.feature.community.social.management.SocialManagementCommandRegistrar;
import org.encinet.mik.feature.community.social.platform.matrix.MatrixPlatformAdapter;
import org.encinet.mik.feature.community.social.platform.matrix.crypto.MatrixCryptoControl;
import org.encinet.mik.feature.community.social.platform.matrix.crypto.MatrixCryptoCommandRegistrar;
import org.encinet.mik.feature.community.social.platform.qq.QqPlatformAdapter;
import org.encinet.mik.feature.community.social.runtime.SocialContentGuard;
import org.encinet.mik.feature.community.social.runtime.SocialPlatformHost;
import org.encinet.mik.feature.community.social.safety.QwenGuardClient;
import org.encinet.mik.feature.community.social.safety.SocialContentSafetyConfig;

import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.encinet.mik.feature.community.social.document.SocialDocument.Moderation.TRUSTED;

/** Bukkit composition root for shared social features and platform adapters. */
public final class SocialModule {
    private final SocialPlatformHost host;
    private final SocialManagementCommandRegistrar managementCommands;
    private final MatrixCryptoCommandRegistrar matrixCryptoCommands;
    private final AnnouncementPublisher announcements;

    public SocialModule(
            JavaPlugin plugin,
            IdentityBindingManager identityBindings,
            LanguageService languages,
            AfkService afkService,
            SocialChatGateway chatGateway,
            AiGateway ai
    ) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(languages, "languages");
        Objects.requireNonNull(chatGateway, "chatGateway");
        Objects.requireNonNull(ai, "ai");
        SocialCommandLanguageResolver languageResolver = new SocialCommandLanguageResolver(
                identityBindings, languages);
        SocialGameService game = new BukkitSocialGameService(plugin, afkService,
                new BukkitMainThreadGateway(plugin));

        List<SocialCommand<?, ?>> featureCommands = List.of(
                new AiSocialCommand(ai, languageResolver, languages, plugin.getLogger()),
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
                SocialDocument.of(TRUSTED, "🛡 " + languages.t(
                                language, Message.SOCIAL_TITLE_CONTENT_HIDDEN),
                        SocialDocument.Tone.WARNING,
                                SocialDocument.paragraph(languages.t(
                                        language, Message.SOCIAL_REPLY_HIDDEN)))
                        .localized(language);
        SocialContentGuard contentGuard = SocialContentGuard.reloadable(
                () -> new QwenGuardClient(SocialContentSafetyConfig.load(plugin)),
                blockedDocument);
        MatrixCryptoControl matrixCrypto = new MatrixCryptoControl();
        matrixCryptoCommands = new MatrixCryptoCommandRegistrar(plugin, matrixCrypto, languages);
        host = new SocialPlatformHost(
                List.of(new QqPlatformAdapter(plugin), new MatrixPlatformAdapter(plugin, matrixCrypto)),
                dispatcher,
                identityBindings::registerPlatform, contentGuard,
                chatGateway, identityBindings::findByPlayer, languages, plugin.getLogger());
        managementCommands = new SocialManagementCommandRegistrar(host, languages);
        var announcementPresenter = new SocialAnnouncementPresenter(
                languages, ZoneId.systemDefault());
        announcements = (timestamp, content) -> {
            var report = host.publishNotice(language -> announcementPresenter.present(language, timestamp, content));
            if (report.backpressured() > 0) {
                plugin.getLogger().warning("Announcement delivery is backpressured on "
                        + report.backpressured() + " social platform(s)");
            }
        };
    }

    public void enable() {
        host.start();
    }

    public void disable() {
        host.close();
    }

    public void registerCommands(LifecycleEventManager<Plugin> lifecycleManager) {
        managementCommands.register(lifecycleManager);
        matrixCryptoCommands.register(lifecycleManager);
    }

    public SocialChatPublisher chatPublisher() {
        return host;
    }

    public AnnouncementPublisher announcementPublisher() {
        return announcements;
    }

    /** Tries a native mention in a configured conversation linked to the player. */
    public void notifyPlayer(UUID playerId, String playerName, String body) {
        host.publishDirectedNotice(new SocialDirectedNotice(playerId, playerName, body));
    }

}
