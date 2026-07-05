package org.encinet.mik.module.presentation;

import io.papermc.paper.connection.PlayerConfigurationConnection;
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
import java.util.UUID;
import java.util.function.Consumer;

public final class ServerLinksModule implements Listener {

    private static final List<Consumer<ServerLinks>> ZH_LINKS = List.of(
            typed(ServerLinks.Type.WEBSITE,
                    "https://mcmik.top"),
            typed(ServerLinks.Type.COMMUNITY_GUIDELINES,
                    "https://mcmik.top/wiki"),
            named("QQ群 1",
                    "https://qm.qq.com/q/3Eg9D7EBJ6"),
            named("QQ群 2",
                    "https://qm.qq.com/q/hfZYEqarPa"),
            named("视频",
                    "https://www.bilibili.com/video/BV1GJ411x7h7"),
            named("赞助列表",
                    "https://docs.qq.com/sheet/DRUV3c2Z6a21MYnZ6?tab=BB08J2"),
            named("装饰头颅浏览",
                    "https://minecraft-heads.com/"),
            named("Minecraft Wiki 中文版",
                    "https://zh.minecraft.wiki/"),
            named("MiniMessage 格式文档",
                    "https://docs.papermc.io/adventure/minimessage/format/"),
            named("MiniMessage 在线预览",
                    "https://webui.advntr.dev/"),
            named("CC BY-NC-SA 4.0",
                    "https://creativecommons.org/licenses/by-nc-sa/4.0/"),
            named("繁空工作室",
                    "https://encinet.netlify.app")
    );

    private static final List<Consumer<ServerLinks>> EN_LINKS = List.of(
            typed(ServerLinks.Type.WEBSITE,
                    "https://mcmik.top"),
            typed(ServerLinks.Type.COMMUNITY_GUIDELINES,
                    "https://mcmik.top/wiki"),
            named("Video",
                    "https://www.youtube.com/watch?v=dQw4w9WgXcQ"),
            named("Sponsor List",
                    "https://docs.qq.com/sheet/DRUV3c2Z6a21MYnZ6?tab=BB08J2"),
            named("Minecraft Heads",
                    "https://minecraft-heads.com/"),
            named("Minecraft Wiki",
                    "https://www.minecraft.wiki/"),
            named("MiniMessage Format",
                    "https://docs.papermc.io/adventure/minimessage/format/"),
            named("MiniMessage Preview",
                    "https://webui.advntr.dev/"),
            named("CC BY-NC-SA 4.0",
                    "https://creativecommons.org/licenses/by-nc-sa/4.0/"),
            named("Encinet Studio",
                    "https://encinet.netlify.app")
    );

    private final LanguageService languageService;

    public ServerLinksModule(LanguageService languageService) {
        this.languageService = languageService;
    }

    public void register(JavaPlugin plugin) {
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
        PlayerConfigurationConnection connection = (PlayerConfigurationConnection) event.getConnection();
        UUID id = connection.getProfile().getId();
        return id != null ? languageService.language(id, null) : Language.DEFAULT;
    }

    private static void fillLinks(ServerLinks links, Language language) {
        (language.isChinese() ? ZH_LINKS : EN_LINKS).forEach(fill -> fill.accept(links));
    }

    private static Consumer<ServerLinks> typed(ServerLinks.Type type, String url) {
        URI uri = URI.create(url);
        return links -> links.addLink(type, uri);
    }

    private static Consumer<ServerLinks> named(String label, String url) {
        Component component = Component.text(label);
        URI uri = URI.create(url);
        return links -> links.addLink(component, uri);
    }
}