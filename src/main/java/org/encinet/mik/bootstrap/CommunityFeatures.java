package org.encinet.mik.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.community.chat.ChatModule;
import org.encinet.mik.feature.community.chat.bridge.PaperChatBridge;
import org.encinet.mik.feature.player.profile.TabListModule;
import org.encinet.mik.feature.community.chat.settings.ChatSettingsStore;
import org.encinet.mik.feature.community.chat.game.GameMentionNotifier;
import org.encinet.mik.feature.community.chat.game.GameChatDirectory;
import org.encinet.mik.feature.community.communication.Announcement;
import org.encinet.mik.feature.community.communication.AnnouncementModule;
import org.encinet.mik.feature.community.communication.AnnouncementSnapshot;
import org.encinet.mik.feature.community.governance.GovernanceModule;
import org.encinet.mik.feature.community.identity.IdentityBindingModule;
import org.encinet.mik.feature.server.access.IdentityAccess;
import org.encinet.mik.integration.mikd.MikdModule;
import org.encinet.mik.protocol.GameRoster;
import org.encinet.mik.protocol.GovernanceCredential;
import org.encinet.mik.protocol.PlayerSnapshot;

import java.util.UUID;
import java.util.function.Predicate;

record CommunityFeatures(
        ChatModule chat,
        GovernanceModule governance,
        AnnouncementModule announcements) {

    static CommunityFeatures enable(
            JavaPlugin plugin, ModuleLifecycle lifecycle, SharedServices shared,
            PlayerFeatures players, ServerFeatures server, Predicate<UUID> confined) {
        var languageService = shared.language();
        var afkModule = players.afk();
        var playerNameTags = players.nameTags();
        var banModule = server.bans();

        ChatSettingsStore chatSettingsStore = new ChatSettingsStore(plugin);
        lifecycle.startManaged("chat-settings", chatSettingsStore::enable, chatSettingsStore::disable);

        GameMentionNotifier mentionNotifier = new GameMentionNotifier(afkModule, languageService, chatSettingsStore);

        IdentityBindingModule identity = new IdentityBindingModule(plugin, languageService, shared.mikd().requests(),
                players.security()::authentication);
        identity.registerCommands();
        IdentityAccess identityAccess = new IdentityAccess(plugin, languageService, shared.mikd().requests(),
                players.security()::configurationAuthenticated, server.maintenance()::admissionRefusal,
                identity::challenge, IdentityBindingModule::platformName);
        lifecycle.startManaged("identity-access", identityAccess::enable, identityAccess::close);

        var directory = new GameChatDirectory(
                player -> players.security().authentication(player).getAsBoolean(),
                (id, name) -> banModule.manager().active(id, name).isEmpty(), confined);
        ChatModule chatModule = new ChatModule(plugin, directory, players.security()::authentication, mentionNotifier,
                languageService, chatSettingsStore, playerNameTags, players.sources(),
                lifecycle.own("chat-tips", players.guidance().scope()), shared.menus()::consumeChatInput);

        var requests = shared.mikd().requests();
        AnnouncementModule announcementModule = new AnnouncementModule(plugin, languageService,
                () -> requests.fetchAnnouncements().thenApply(snapshot -> new AnnouncementSnapshot(
                        snapshot.getStale() ? AnnouncementSnapshot.State.STALE : AnnouncementSnapshot.State.READY,
                        snapshot.getEntriesList().stream()
                                .map(entry -> new Announcement(entry.getTimestamp(), entry.getContent())).toList())));
        lifecycle.startManaged("announcement", announcementModule::enable, announcementModule::disable);
        announcementModule.registerCommands(plugin.getLifecycleManager());

        PaperChatBridge paperChat = new PaperChatBridge(plugin, requests,
                chatModule.game()::deliverRouted, chatModule.game()::deliverWebPresence);
        players.tabList().webVisibility((viewer, subject) -> directory.webAvailable(subject) && directory.canSee(viewer, subject));
        lifecycle.startManaged("chat", () -> chatModule.enable(paperChat), chatModule::disable);
        chatModule.registerCommands(plugin.getLifecycleManager());
        lifecycle.startManaged("paper-chat-bridge", paperChat::start, paperChat::close);
        shared.mikd().startGameState(() -> {
            var peers = directory.gamePeers();
            var publicRoster = GameRoster.newBuilder();
            for (var peer : peers) {
                if (!peer.eligible() || peer.viewers().isPresent()) continue;
                publicRoster.addPlayers(PlayerSnapshot.newBuilder().setPlayerId(peer.id().toString())
                        .setName(peer.name()).setJoinedAt(peer.joinedAt().toString()).setAfk(afkModule.isAfk(peer.id())));
            }
            directory.disallowedWebPlayers().forEach(requests::invalidateWebChat);
            return new MikdModule.GameRosters(publicRoster.build(), PaperChatBridge.encodeRoster(peers));
        }, roster -> {
            var peers = PaperChatBridge.decodeRoster(roster);
            directory.replaceWebPeers(peers);
            players.tabList().webPlayers(peers.stream().map(peer ->
                    new TabListModule.WebPlayer(peer.id(), peer.name(), peer.connections())).toList());
        });
        var permissions = org.bukkit.Bukkit.getServicesManager().load(net.luckperms.api.LuckPerms.class);
        if (permissions != null) {
            var subscription = permissions.getEventBus().subscribe(plugin,
                    net.luckperms.api.event.user.UserDataRecalculateEvent.class,
                    event -> requests.invalidateWebChat(event.getUser().getUniqueId()));
            lifecycle.own("web-chat-permissions", subscription::close);
        }

        GovernanceModule governanceModule = new GovernanceModule(
                plugin, languageService, afkModule, banModule.manager(), players.security(),
                player -> players.security().authentication(player).getAsBoolean(), id -> {
                    var fact = players.security().credentialFact(id);
                    return GovernanceCredential.newBuilder().setRevision(fact.revision())
                            .setChangedMs(fact.revision()).setPresent(fact.present()).build();
                }, shared.mikd().requests());
        lifecycle.startManaged("governance", governanceModule::enable, governanceModule::disable);
        lifecycle.own("governance-ban-changes", banModule.onAccountChange(governanceModule::accountBanChanged));
        governanceModule.registerCommands(plugin.getLifecycleManager());
        players.security().governance(governanceModule::requiresPassword, governanceModule, governanceModule::canResetPassword);

        return new CommunityFeatures(chatModule, governanceModule, announcementModule);
    }
}
