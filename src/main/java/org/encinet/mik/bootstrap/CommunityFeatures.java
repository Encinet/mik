package org.encinet.mik.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.community.ai.AiModule;
import org.encinet.mik.feature.community.chat.ChatModule;
import org.encinet.mik.feature.community.chat.web.WebChatModule;
import org.encinet.mik.feature.player.profile.TabListModule;
import org.encinet.mik.feature.community.chat.settings.ChatSettingsStore;
import org.encinet.mik.feature.community.chat.game.GameMentionNotifier;
import org.encinet.mik.feature.community.chat.game.GameChatPresenceListener;
import org.encinet.mik.feature.community.communication.AnnouncementModule;
import org.encinet.mik.feature.community.governance.GovernanceModule;
import org.encinet.mik.feature.community.identity.IdentityBindingModule;
import org.encinet.mik.feature.community.social.SocialModule;
import org.encinet.mik.feature.community.social.game.BukkitSocialChatGateway;

record CommunityFeatures(
        ChatModule chat,
        WebChatModule webChat,
        SocialModule social,
        GovernanceModule governance,
        AnnouncementModule announcements) {

    static CommunityFeatures enable(
            JavaPlugin plugin, ModuleLifecycle lifecycle, SharedServices shared,
            PlayerFeatures players, ServerFeatures server) {
        var languageService = shared.language();
        var afkModule = players.afk();
        var playerNameTags = players.nameTags();
        var banModule = server.bans();

        AiModule aiModule = new AiModule(plugin, languageService);
        lifecycle.startManaged("ai", aiModule::enable, aiModule::disable);
        aiModule.registerCommands(plugin.getLifecycleManager());

        ChatSettingsStore chatSettingsStore = new ChatSettingsStore(plugin);
        lifecycle.startManaged("chat-settings", chatSettingsStore::enable, chatSettingsStore::disable);

        GameMentionNotifier mentionNotifier = new GameMentionNotifier(plugin, afkModule, languageService, chatSettingsStore);

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
                plugin, mentionNotifier, languageService, chatSettingsStore, playerNameTags, players.sources(),
                socialModule.chatPublisher(), identityBindingModule.manager(),
                lifecycle.own("chat-tips", players.guidance().scope()), shared.menus()::consumeChatInput);
        socialChatGateway.bind(chatModule.messages());

        WebChatModule webChat = new WebChatModule(plugin, languageService,
                players.security()::authentication, players.security()::webAccountPolicy,
                id -> playerNameTags.resolve(id, id.toString()),
                (id, name) -> banModule.manager().active(id, name).isEmpty(), peers -> players.tabList().webPlayers(peers.stream().map(peer ->
                        new TabListModule.WebPlayer(peer.playerUuid(), peer.name(), peer.webConnections())).toList()),
                chatModule.messages()::process, chatModule.messages()::publish, chatModule.ledger());
        players.tabList().webVisibility(webChat::canSee);
        lifecycle.startManaged("chat", () -> chatModule.enable(webChat, webChat), chatModule::disable);
        chatModule.registerCommands(plugin.getLifecycleManager());
        lifecycle.startManaged("web-chat", webChat::enable, webChat::disable);
        GameChatPresenceListener gameChatPresence = new GameChatPresenceListener(plugin, webChat, player -> {
            var peer = webChat.peer(player.getUniqueId(), player.getUniqueId());
            return peer != null && peer.game();
        }, webChat::canSee);
        lifecycle.startManaged("chat-game-presence", gameChatPresence::enable, gameChatPresence::disable);
        var permissions = org.bukkit.Bukkit.getServicesManager().load(net.luckperms.api.LuckPerms.class);
        if (permissions != null) {
            var subscription = permissions.getEventBus().subscribe(plugin,
                    net.luckperms.api.event.user.UserDataRecalculateEvent.class,
                    event -> webChat.invalidatePolicy(event.getUser().getUniqueId()));
            lifecycle.own("web-chat-permissions", subscription::close);
        }

        lifecycle.startManaged("social", socialModule::enable, socialModule::disable);
        socialModule.registerCommands(plugin.getLifecycleManager());

        GovernanceModule governanceModule = new GovernanceModule(
                plugin, languageService, afkModule, banModule.manager(), players.security());
        lifecycle.startManaged("governance", governanceModule::enable, governanceModule::disable);
        governanceModule.registerCommands(plugin.getLifecycleManager());
        players.security().retention(governanceModule::requiresPassword);

        AnnouncementModule announcementModule = new AnnouncementModule(
                plugin, languageService, webChat, socialModule.announcementPublisher());
        lifecycle.startManaged("announcement", announcementModule::enable, announcementModule::disable);
        announcementModule.registerCommands(plugin.getLifecycleManager());

        return new CommunityFeatures(chatModule, webChat, socialModule, governanceModule, announcementModule);
    }
}
