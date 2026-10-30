package org.encinet.mik.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.community.ai.AiModule;
import org.encinet.mik.feature.community.chat.ChatDisplayRenderer;
import org.encinet.mik.feature.community.chat.ChatModule;
import org.encinet.mik.feature.community.chat.ChatSettingsStore;
import org.encinet.mik.feature.community.chat.mention.MentionService;
import org.encinet.mik.feature.community.communication.AnnouncementModule;
import org.encinet.mik.feature.community.governance.GovernanceModule;
import org.encinet.mik.feature.community.identity.IdentityBindingModule;
import org.encinet.mik.feature.community.social.SocialModule;
import org.encinet.mik.feature.community.social.game.BukkitSocialChatGateway;

record CommunityFeatures(
        ChatModule chat,
        SocialModule social,
        GovernanceModule governance,
        AnnouncementModule announcements) {

    static CommunityFeatures enable(
            JavaPlugin plugin, ModuleLifecycle lifecycle, SharedServices shared,
            PlayerFeatures players, ServerFeatures server) {
        var languageService = shared.language();
        var afkModule = players.afk();
        var playerNameTags = players.nameTags();
        var playerIdentities = players.identities();
        var banModule = server.bans();

        AiModule aiModule = new AiModule(plugin, languageService);
        lifecycle.startManaged("ai", aiModule::enable, aiModule::disable);
        aiModule.registerCommands(plugin.getLifecycleManager());

        ChatSettingsStore chatSettingsStore = new ChatSettingsStore(plugin);
        lifecycle.startManaged("chat-settings", chatSettingsStore::enable, chatSettingsStore::disable);

        MentionService mentionService = new MentionService(plugin, afkModule, languageService, chatSettingsStore,
                ChatDisplayRenderer::playerName);

        IdentityBindingModule identityBindingModule = new IdentityBindingModule(plugin, languageService);
        lifecycle.startManaged("identity-binding", identityBindingModule::enable, identityBindingModule::disable);
        identityBindingModule.registerCommands(plugin.getLifecycleManager());

        BukkitSocialChatGateway socialChatGateway =
                new BukkitSocialChatGateway(
                        plugin, identityBindingModule.manager(), playerNameTags);
        SocialModule socialModule = new SocialModule(
                plugin, identityBindingModule.manager(), languageService, afkModule,
                socialChatGateway, aiModule);

        ChatModule chatModule = new ChatModule(
                plugin, mentionService, languageService, chatSettingsStore, playerIdentities,
                socialModule.chatPublisher(), identityBindingModule.manager(),
                lifecycle.own("chat-tips", players.guidance().scope()));
        socialChatGateway.bind(chatModule);
        lifecycle.startManaged("chat", chatModule::enable, chatModule::disable);
        chatModule.registerCommands(plugin.getLifecycleManager());

        lifecycle.startManaged("social", socialModule::enable, socialModule::disable);
        socialModule.registerCommands(plugin.getLifecycleManager());

        GovernanceModule governanceModule = new GovernanceModule(
                plugin, languageService, afkModule, banModule.manager(), players.security());
        lifecycle.startManaged("governance", governanceModule::enable, governanceModule::disable);
        governanceModule.registerCommands(plugin.getLifecycleManager());
        players.security().retention(governanceModule::requiresPassword);

        AnnouncementModule announcementModule = new AnnouncementModule(plugin, languageService);
        lifecycle.startManaged("announcement", announcementModule::enable, announcementModule::disable);
        announcementModule.registerCommands(plugin.getLifecycleManager());

        return new CommunityFeatures(chatModule, socialModule, governanceModule, announcementModule);
    }
}
