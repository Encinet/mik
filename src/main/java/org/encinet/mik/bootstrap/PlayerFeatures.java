package org.encinet.mik.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.player.address.PlayerAddressModule;
import org.encinet.mik.feature.player.address.PlayerAssociationNotifier;
import org.encinet.mik.feature.player.afk.AfkModule;
import org.encinet.mik.feature.player.client.ClientVersions;
import org.encinet.mik.feature.player.client.detection.ClientModDetector;
import org.encinet.mik.feature.player.client.detection.ViaClientVersions;
import org.encinet.mik.feature.player.client.tip.ClientTips;
import org.encinet.mik.feature.player.effects.EffectCleanupModule;
import org.encinet.mik.feature.player.guidance.TipModule;
import org.encinet.mik.feature.player.mode.GameModeSwitchModule;
import org.encinet.mik.feature.player.movement.FlightModule;
import org.encinet.mik.feature.player.movement.PlayerBoundaryModule;
import org.encinet.mik.feature.player.presence.InvisibilityNotifyModule;
import org.encinet.mik.feature.player.presence.PlayerPresenceModule;
import org.encinet.mik.feature.player.presence.WelcomeModule;
import org.encinet.mik.feature.player.profile.NameTagModule;
import org.encinet.mik.feature.player.profile.PlayerIdentityRenderer;
import org.encinet.mik.feature.player.profile.PlayerNameTagRenderer;
import org.encinet.mik.feature.player.profile.TabListModule;
import org.encinet.mik.feature.player.pvp.PvpModule;
import org.encinet.mik.feature.player.teleport.BackModule;
import org.encinet.mik.feature.player.teleport.HomeModule;
import org.encinet.mik.feature.player.teleport.TeleportPreferenceModule;
import org.encinet.mik.feature.player.trash.TrashModule;
import org.encinet.mik.feature.player.utility.SimpleFeaturesModule;
import org.encinet.mik.integration.geyser.BedrockPlayerBadge;
import su.plo.voice.api.server.PlasmoVoiceServer;

record PlayerFeatures(
        AfkModule afk,
        PlayerAddressModule addresses,
        PlayerNameTagRenderer nameTags,
        PlayerIdentityRenderer identities,
        PvpModule pvp,
        TeleportPreferenceModule teleports,
        ClientVersions clientVersions,
        TipModule guidance) {

    static PlayerFeatures enable(
            JavaPlugin plugin, ModuleLifecycle lifecycle, SharedServices shared,
            PlasmoVoiceServer voiceServer) {
        var languageService = shared.language();
        var axiomGizmoService = shared.axiom();
        var geyserService = shared.geyser();

        BedrockPlayerBadge bedrockPlayerBadge =
                new BedrockPlayerBadge(geyserService, languageService);
        PlayerNameTagRenderer playerNameTags = new PlayerNameTagRenderer(plugin);
        lifecycle.startManaged("player-name-tags", playerNameTags::enable, playerNameTags::disable);
        PlayerIdentityRenderer playerIdentities =
                new PlayerIdentityRenderer(bedrockPlayerBadge, playerNameTags);

        PlayerAddressModule playerAddressModule = new PlayerAddressModule(plugin);
        lifecycle.startManaged("player-address", playerAddressModule::enable, playerAddressModule::disable);

        PlayerAssociationNotifier playerAssociationNotifier =
                new PlayerAssociationNotifier(plugin, languageService, playerAddressModule);
        lifecycle.startListener("player-association", playerAssociationNotifier,
                playerAssociationNotifier::enable);

        AfkModule afkModule = new AfkModule(plugin, languageService, axiomGizmoService);
        lifecycle.startManaged("afk", afkModule::enable, afkModule::disable);
        afkModule.registerCommands(plugin.getLifecycleManager());

        ClientVersions clientVersions;
        if (plugin.getServer().getPluginManager().isPluginEnabled("ViaVersion")) {
            clientVersions = new ViaClientVersions(plugin);
        } else {
            clientVersions = player -> java.util.Optional.empty();
            plugin.getLogger().warning("ViaVersion not found; client protocol lookup is unavailable.");
        }
        ClientModDetector clientMods = new ClientModDetector(player -> {
            if (voiceServer == null) return false;
            var voicePlayer = voiceServer.getPlayerManager().getPlayerByInstance(player);
            return voicePlayer != null && (voicePlayer.getModVersion().isPresent() || voicePlayer.hasVoiceChat());
        });
        lifecycle.startManaged("client-mod-detection", clientMods::enable, clientMods::disable);

        TipModule tipModule = new TipModule(plugin, languageService,
                player -> !afkModule.isAfk(player.getUniqueId()));
        lifecycle.startManaged("guidance", tipModule::enable, tipModule::disable);
        ClientTips.register(lifecycle.own("client-tips", tipModule.scope()), languageService,
                clientVersions, clientMods, geyserService::isBedrockPlayer);

        PvpModule pvpModule = new PvpModule(plugin, languageService,
                lifecycle.own("pvp-tips", tipModule.scope()));
        lifecycle.startManaged("pvp", pvpModule::enable, pvpModule::disable);
        pvpModule.registerCommands(plugin.getLifecycleManager());

        EffectCleanupModule effectCleanup = new EffectCleanupModule(plugin, languageService,
                pvpModule::isCombatTagged);
        lifecycle.startManaged("effect-cleanup", effectCleanup::enable, effectCleanup::disable);

        TeleportPreferenceModule teleportPreferenceModule =
                new TeleportPreferenceModule(plugin, afkModule, languageService);
        lifecycle.startListener("teleport-preference", teleportPreferenceModule,
                teleportPreferenceModule::enable);
        teleportPreferenceModule.registerCommands(plugin.getLifecycleManager());

        WelcomeModule welcomeModule = new WelcomeModule(plugin, languageService);
        lifecycle.startListener("welcome", welcomeModule, welcomeModule::enable);

        PlayerPresenceModule playerPresenceModule = new PlayerPresenceModule(plugin, languageService);
        lifecycle.startListener("player-presence", playerPresenceModule, playerPresenceModule::enable);

        SimpleFeaturesModule commandsModule = new SimpleFeaturesModule(plugin, languageService,
                lifecycle.own("utility-tips", tipModule.scope()));
        lifecycle.startListener("simple-features", commandsModule, commandsModule::enable);
        commandsModule.registerCommands(plugin.getLifecycleManager());

        TrashModule trashModule = new TrashModule(plugin, languageService,
                lifecycle.own("trash-tips", tipModule.scope()));
        lifecycle.startManaged("trash", trashModule::enable, trashModule::disable);
        trashModule.registerCommands(plugin.getLifecycleManager());

        GameModeSwitchModule gameModeSwitchModule = new GameModeSwitchModule(plugin);
        lifecycle.startManaged("game-mode-switch", gameModeSwitchModule::enable, gameModeSwitchModule::disable);

        FlightModule flightModule = new FlightModule(plugin, languageService);
        lifecycle.startManaged("flight", flightModule::enable, flightModule::disable);
        flightModule.registerCommands(plugin.getLifecycleManager());

        PlayerBoundaryModule playerBoundaryModule = new PlayerBoundaryModule(plugin, languageService);
        lifecycle.startManaged("player-boundary", playerBoundaryModule::enable, playerBoundaryModule::disable);

        TabListModule tabListModule = new TabListModule(
                plugin, afkModule, languageService, playerIdentities);
        lifecycle.startManaged("tab-list", tabListModule::enable, tabListModule::disable);

        HomeModule homeModule = new HomeModule(plugin, languageService, lifecycle.own("home-tips", tipModule.scope()));
        lifecycle.startManaged("home", homeModule::enable, homeModule::disable);
        homeModule.registerCommands(plugin.getLifecycleManager());

        BackModule backModule = new BackModule(plugin, languageService, lifecycle.own("back-tips", tipModule.scope()));
        lifecycle.startManaged("back", backModule::enable, backModule::disable);
        backModule.registerCommands(plugin.getLifecycleManager());

        NameTagModule prefixSuffixModule = new NameTagModule(plugin, languageService, playerIdentities,
                lifecycle.own("name-tag-tips", tipModule.scope()));
        lifecycle.startManaged("name-tag", prefixSuffixModule::enable, prefixSuffixModule::disable);
        prefixSuffixModule.registerCommands(plugin.getLifecycleManager());

        InvisibilityNotifyModule invisibilityNotifyModule =
                new InvisibilityNotifyModule(plugin, languageService);
        lifecycle.startManaged("invisibility-notify", invisibilityNotifyModule::enable, invisibilityNotifyModule::disable);

        return new PlayerFeatures(afkModule, playerAddressModule, playerNameTags, playerIdentities,
                pvpModule, teleportPreferenceModule, clientVersions, tipModule);
    }
}
