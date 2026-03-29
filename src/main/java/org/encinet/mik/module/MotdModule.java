package org.encinet.mik.module;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.util.GeoUtil;

import java.net.InetAddress;

public class MotdModule implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final Component MOTD_CN = MM.deserialize(
            "<gold>               米<white>客 <gray>| <green>26.1<gray> | <gold>创造<white>休闲服\n" +
            "<white>               <gradient:#5e4fa2:#f79459:red>创造 休闲 音乐 蹦跶 挂机"
    );

    private static final Component MOTD_EN = MM.deserialize(
            "<gold>             Mi<white>k  <gray>| <green>26.1<gray> | <gold>Creative<white> Casual\n" +
            "<white>           <gradient:#5e4fa2:#f79459:red>Build • Chill • Music • Fun • AFK"
    );

    private final JavaPlugin plugin;

    public MotdModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPing(PaperServerListPingEvent event) {
        event.setMaxPlayers(2026);
        event.getListedPlayers().clear();

        InetAddress address = event.getAddress();
        event.motd(GeoUtil.isChinaIp(address) ? MOTD_CN : MOTD_EN);
    }
}