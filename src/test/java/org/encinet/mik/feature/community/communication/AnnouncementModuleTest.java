package org.encinet.mik.feature.community.communication;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.shared.i18n.LanguageService;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnnouncementModuleTest {
    @Test
    void distinguishesUnavailableEmptyAndLastGoodSnapshotsAndRecovers() {
        var plugin = mock(JavaPlugin.class);
        var server = mock(Server.class);
        var scheduler = mock(BukkitScheduler.class);
        var language = mock(LanguageService.class);
        var completions = new ArrayDeque<CompletableFuture<AnnouncementSnapshot>>();
        var mainThread = new ArrayDeque<Runnable>();
        var timer = new ArrayDeque<Runnable>();
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
            mainThread.add(invocation.getArgument(1));
            return null;
        });
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), anyLong())).thenAnswer(invocation -> {
            timer.add(invocation.getArgument(1));
            return mock(BukkitTask.class);
        });
        var module = new AnnouncementModule(plugin, language, () -> {
            var completion = new CompletableFuture<AnnouncementSnapshot>();
            completions.add(completion);
            return completion;
        });
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            var entry = new Announcement(1, "First update");
            var empty = new AnnouncementSnapshot(AnnouncementSnapshot.State.READY, List.of());
            var ready = new AnnouncementSnapshot(AnnouncementSnapshot.State.READY, List.of(entry));
            var unavailable = new IllegalStateException("Backend unavailable");
            module.enable();
            assertEquals(AnnouncementSnapshot.UNAVAILABLE, module.revision());
            var poll = timer.remove();
            poll.run();
            completions.remove().completeExceptionally(unavailable);
            mainThread.remove().run();
            assertEquals(AnnouncementSnapshot.UNAVAILABLE, module.revision());
            poll.run();
            completions.remove().complete(empty);
            mainThread.remove().run();
            assertEquals(empty, module.revision(), "A validated empty catalog is not unavailable");
            poll.run();
            completions.remove().complete(ready);
            mainThread.remove().run();
            assertEquals(ready, module.revision());
            poll.run();
            completions.remove().completeExceptionally(unavailable);
            mainThread.remove().run();
            assertEquals(new AnnouncementSnapshot(AnnouncementSnapshot.State.STALE, List.of(entry)), module.revision());
            poll.run();
            completions.remove().complete(ready);
            mainThread.remove().run();
            assertEquals(ready, module.revision(), "The same content is fresh again when the daemon recovers");
            module.disable();
        }
    }
}
