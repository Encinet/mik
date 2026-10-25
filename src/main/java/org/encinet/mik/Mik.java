package org.encinet.mik;

import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.bootstrap.MikRuntime;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.server.PlasmoVoiceServer;

@Addon(
        id = "mik-music",
        name = "MIK Music",
        scope = AddonLoaderScope.SERVER,
        version = "1.0",
        authors = {"Noctiro", "Aeolic"}
)
public final class Mik extends JavaPlugin {
    private MikRuntime runtime;

    @InjectPlasmoVoice
    private PlasmoVoiceServer voiceServer;

    @Override
    public void onLoad() {
        runtime = new MikRuntime(this);
        runtime.load();
    }

    @Override
    public void onEnable() {
        runtime.enable(voiceServer);
    }

    @Override
    public void onDisable() {
        if (runtime != null) runtime.disable();
    }
}
