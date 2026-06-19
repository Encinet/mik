package org.encinet.mik.module.presentation;

import net.kyori.adventure.text.Component;
import org.bukkit.ServerLinks;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;

/**
 * Module for registering server links
 */
public class ServerLinksModule {

    private final JavaPlugin plugin;

    public ServerLinksModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Register all server links
     */
    public void register() {
        ServerLinks serverLinks = plugin.getServer().getServerLinks();

        serverLinks.addLink(ServerLinks.Type.WEBSITE, URI.create("https://mik.noctiro.moe"));
        serverLinks.addLink(ServerLinks.Type.COMMUNITY_GUIDELINES, URI.create("https://mik.noctiro.moe/wiki"));
        serverLinks.addLink(Component.text("QQ群1 | QQ Group 1"), URI.create("https://jq.qq.com/?_wv=1027&k=HSSe2Rxe"));
        serverLinks.addLink(Component.text("QQ群2 | QQ Group 2"), URI.create("https://qun.qq.com/universal-share/share?ac=1&authKey=LF4qpagS25O%2FV1h5TD6LHusLzRsgMBsJSG433rhR8pW0HW6PSFHGntMAm4DfGKCP&busi_data=eyJncm91cENvZGUiOiI0NzcyNDYxNzQiLCJ0b2tlbiI6IkRRN3g0bk42dklJK3lmcEZrZ09sQ1hXeExxRHNoUXVhblIwMk8zOGpPYXJORC9zdlJzYzBRSFluaW1nWnh4aWciLCJ1aW4iOiIxNjY1ODA3MTA5In0%3D&data=EgH9s3aPcNE53_fgxWrVUEslrO9hAPVjsuFMuiaJE6XNzAIA2f6L1-LcnovG41WpP8PZD27ejZdYsgklvF2B_A&svctype=4&tempid=h5_group_info"));
        serverLinks.addLink(Component.text("赞助列表 | Sponsor List"), URI.create("https://docs.qq.com/sheet/DRUV3c2Z6a21MYnZ6?tab=BB08J2"));
        serverLinks.addLink(Component.text("装饰头颅在线浏览 | Minecraft Heads"), URI.create("https://minecraft-heads.com/"));
        serverLinks.addLink(Component.text("Minecraft Wiki中文版"), URI.create("https://zh.minecraft.wiki/"));
        serverLinks.addLink(Component.text("Minecraft Wiki English Version"), URI.create("https://www.minecraft.wiki/"));
        serverLinks.addLink(Component.text("MiniMessage格式 | MiniMessage Format"), URI.create("https://docs.papermc.io/adventure/minimessage/format/"));
        serverLinks.addLink(Component.text("MiniMessage在线预览 | MiniMessage Preview"), URI.create("https://webui.advntr.dev/"));
        serverLinks.addLink(Component.text("CC BY-NC-SA 4.0"), URI.create("https://creativecommons.org/licenses/by-nc-sa/4.0/"));
        serverLinks.addLink(Component.text("繁空工作室 | Encinet Studio"), URI.create("https://encinet.netlify.app"));
    }
}
