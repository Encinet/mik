package org.encinet.mik.feature.community.communication;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.community.chat.bridge.ServerChatPublisher;
import org.encinet.mik.feature.community.chat.model.ChatServerMessage;
import org.encinet.mik.shared.i18n.LanguageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnnouncementPublicationTest {
    @TempDir Path directory;

    @Test
    void onlyNewReloadedAnnouncementsFanOutAndFailedReadsKeepThePublishedRevision() throws Exception {
        List<ChatServerMessage> web = new ArrayList<>();
        List<String> social = new ArrayList<>();
        Path file = directory.resolve("announcements.txt");
        Files.writeString(file, "2026-11-09 12:00:00\nOld announcement");
        AnnouncementModule module = module((message, _) -> web.add(message),
                (_, content) -> social.add(content));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            module.enable();
            assertTrue(web.isEmpty());
            assertTrue(social.isEmpty());
            Files.writeString(file, "2026-11-10 12:00:00\nNew announcement\nSecond line\n---\n"
                    + "2026-11-09 12:00:00\nOld announcement");
            assertTrue(module.reload());
            assertEquals(List.of("New announcement\nSecond line"), social);
            assertEquals(1, web.size());
            assertEquals("New announcement Second line", ((ChatServerMessage.RichText) web.getFirst().content())
                    .content().plainText());
            assertTrue(module.reload());
            assertEquals(1, web.size());
            assertEquals(1, social.size());
            Files.writeString(file, "2026-11-10 12:00:00\nEdited at the same timestamp");
            assertTrue(module.reload());
            assertEquals(1, social.size());
            Files.delete(file);
            Files.createDirectory(file);
            assertFalse(module.reload());
            assertEquals(1, web.size());
            assertEquals(1, social.size());
            module.disable();
        }
    }

    @Test
    void failureInEitherOutputDoesNotBlockTheOtherOrChangeSuccessfulReload() throws Exception {
        Path file = directory.resolve("announcements.txt");
        for (boolean failWeb : List.of(true, false)) {
            Files.writeString(file, "2026-11-09 12:00:00\nOld");
            List<String> deliveries = new ArrayList<>();
            AnnouncementModule module = module((_, _) -> {
                if (failWeb) throw new IllegalStateException("WebChat unavailable");
                deliveries.add("web");
            }, (_, _) -> {
                if (!failWeb) throw new IllegalStateException("Social unavailable");
                deliveries.add("social");
            });
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
                module.enable();
                Files.writeString(file, "2026-11-10 12:00:00\nNew");
                assertTrue(module.reload());
                assertEquals(List.of(failWeb ? "social" : "web"), deliveries);
                module.disable();
            }
        }
    }

    private AnnouncementModule module(ServerChatPublisher web, AnnouncementPublisher social) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        return new AnnouncementModule(plugin, mock(LanguageService.class), web, social);
    }
}
