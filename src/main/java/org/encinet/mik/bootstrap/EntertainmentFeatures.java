package org.encinet.mik.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.entertainment.event.FifthAnniversaryEventModule;
import org.encinet.mik.feature.entertainment.music.MusicModule;
import su.plo.voice.api.server.PlasmoVoiceServer;

final class EntertainmentFeatures {

    static void enable(
            JavaPlugin plugin, ModuleLifecycle lifecycle, SharedServices shared,
            PlayerFeatures players, WorldFeatures world, PlasmoVoiceServer voiceServer) {
        var languageService = shared.language();
        var afkModule = players.afk();

        FifthAnniversaryEventModule fifthAnniversaryEventModule =
                new FifthAnniversaryEventModule(plugin, afkModule, languageService);
        lifecycle.startManaged("fifth-anniversary", fifthAnniversaryEventModule::enable, fifthAnniversaryEventModule::disable);
        fifthAnniversaryEventModule.registerCommands(plugin.getLifecycleManager());

        MusicModule musicModule = new MusicModule(plugin, languageService, voiceServer,
                afkModule, world.plots()::mayControlMusic);
        lifecycle.startManaged("music", musicModule::enable, musicModule::disable);
        musicModule.registerCommands(plugin.getLifecycleManager());
    }
}
