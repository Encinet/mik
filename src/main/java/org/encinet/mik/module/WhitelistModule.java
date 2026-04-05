package org.encinet.mik.module;

import com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class WhitelistModule implements Listener {

    // Bilingual since IP is not available at this stage
    private static final Component KICK_MSG = Component.text()
            .append(Component.text("你不在白名单中", NamedTextColor.WHITE))
            .appendNewline()
            .append(Component.text("前往 ", NamedTextColor.GRAY))
            .append(Component.text("mikapply.noctiro.moe", NamedTextColor.AQUA))
            .append(Component.text(" 申请白名单", NamedTextColor.GRAY))
            .appendNewline()
            .append(Component.text("官网 ", NamedTextColor.GRAY))
            .append(Component.text("mik.noctiro.moe", NamedTextColor.AQUA))
            .appendNewline()
            .appendNewline()
            .append(Component.text("You are not whitelisted", NamedTextColor.WHITE))
            .appendNewline()
            .append(Component.text("Apply at ", NamedTextColor.GRAY))
            .append(Component.text("mikapply.noctiro.moe", NamedTextColor.AQUA))
            .appendNewline()
            .append(Component.text("Website: ", NamedTextColor.GRAY))
            .append(Component.text("mik.noctiro.moe", NamedTextColor.AQUA))
            .build();

    private final JavaPlugin plugin;

    public WhitelistModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWhitelistVerify(ProfileWhitelistVerifyEvent event) {
        if (event.isWhitelisted()) return;
        event.kickMessage(KICK_MSG);
    }
}
