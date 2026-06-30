package org.encinet.mik.module.presentation;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ServerLinks;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLinksSendEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;

import java.net.URI;
import java.util.List;

public final class ServerLinksModule implements Listener {

    private static final List<LinkDefinition> LINKS = List.of(
            LinkDefinition.typed(ServerLinks.Type.WEBSITE,
                    "https://mcmik.top"),
            LinkDefinition.typed(ServerLinks.Type.COMMUNITY_GUIDELINES,
                    "https://mcmik.top/wiki"),
            LinkDefinition.named("QQ群 1", "QQ Group 1",
                    "https://jq.qq.com/?_wv=1027&k=HSSe2Rxe"),
            LinkDefinition.named("QQ群 2", "QQ Group 2",
                    "https://qun.qq.com/universal-share/share?ac=1&authKey=LF4qpagS25O%2FV1h5TD6LHusLzRsgMBsJSG433rhR8pW0HW6PSFHGntMAm4DfGKCP&busi_data=eyJncm91cENvZGUiOiI0NzcyNDYxNzQiLCJ0b2tlbiI6IkRRN3g0bk42dklJK3lmcEZrZ09sQ1hXeExxRHNoUXVhblIwMk8zOGpPYXJORC9zdlJzYzBRSFluaW1nWnh4aWciLCJ1aW4iOiIxNjY1ODA3MTA5In0%3D&data=EgH9s3aPcNE53_fgxWrVUEslrO9hAPVjsuFMuiaJE6XNzAIA2f6L1-LcnovG41WpP8PZD27ejZdYsgklvF2B_A&svctype=4&tempid=h5_group_info"),
            LinkDefinition.named("赞助列表", "Sponsor List",
                    "https://docs.qq.com/sheet/DRUV3c2Z6a21MYnZ6?tab=BB08J2"),
            LinkDefinition.named("装饰头颅浏览", "Minecraft Heads",
                    "https://minecraft-heads.com/"),
            LinkDefinition.named("Minecraft Wiki 中文版", "Minecraft Wiki Chinese",
                    "https://zh.minecraft.wiki/"),
            LinkDefinition.named("Minecraft Wiki 英文版", "Minecraft Wiki English",
                    "https://www.minecraft.wiki/"),
            LinkDefinition.named("MiniMessage 格式文档", "MiniMessage Format",
                    "https://docs.papermc.io/adventure/minimessage/format/"),
            LinkDefinition.named("MiniMessage 在线预览", "MiniMessage Preview",
                    "https://webui.advntr.dev/"),
            LinkDefinition.named("CC BY-NC-SA 4.0", "CC BY-NC-SA 4.0",
                    "https://creativecommons.org/licenses/by-nc-sa/4.0/"),
            LinkDefinition.named("繁空工作室", "Encinet Studio",
                    "https://encinet.netlify.app")
    );

    private final JavaPlugin plugin;
    private final LanguageService languageService;

    public ServerLinksModule(JavaPlugin plugin, LanguageService languageService) {
        this.plugin = plugin;
        this.languageService = languageService;
    }

    public void register() {
        fillLinks(plugin.getServer().getServerLinks(), Language.DEFAULT);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerLinksSend(PlayerLinksSendEvent event) {
        Language language = resolveLanguage(event);
        ServerLinks links = event.getLinks();
        for (ServerLinks.ServerLink link : List.copyOf(links.getLinks())) {
            links.removeLink(link);
        }
        fillLinks(links, language);
    }

    private Language resolveLanguage(PlayerLinksSendEvent event) {
        if (event.getConnection() instanceof io.papermc.paper.connection.PlayerConfigurationConnection connection
                && connection.getProfile().getId() != null) {
            return languageService.language(connection.getProfile().getId(), null);
        }
        return Language.DEFAULT;
    }

    private void fillLinks(ServerLinks links, Language language) {
        for (LinkDefinition link : LINKS) {
            if (link.type() != null) {
                links.addLink(link.type(), link.uri());
            } else {
                links.addLink(Component.text(link.label(language)), link.uri());
            }
        }
    }

    private record LinkDefinition(ServerLinks.Type type, String zhName, String enName, URI uri) {
        static LinkDefinition typed(ServerLinks.Type type, String url) {
            return new LinkDefinition(type, null, null, URI.create(url));
        }

        static LinkDefinition named(String zhName, String enName, String url) {
            return new LinkDefinition(null, zhName, enName, URI.create(url));
        }

        String label(Language language) {
            return language.isChinese() ? zhName : enName;
        }
    }
}
