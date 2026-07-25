package org.encinet.mik.module.music.online;

import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LxSourceServiceTest {

    @TempDir
    Path directory;

    @Test
    void importsFileSourceAndImmediatelyExposesSearchAndResolution() throws Exception {
        Path source = directory.resolve("external.js");
        Files.writeString(source, searchableScript("Imported", "one"));

        try (LxSourceService service = service()) {
            LxSourceService.ImportResult imported = service
                    .importSourceAsync(source.toUri().toString()).get(5, TimeUnit.SECONDS);

            assertTrue(imported.added());
            assertTrue(imported.changed());
            assertEquals(1, service.subscriptionStatuses().size());
            assertEquals(1, service.statuses().size());
            assertTrue(service.statuses().getFirst().available());

            MusicTrack track = service.searchMusic("song", 1, 30)
                    .get(5, TimeUnit.SECONDS).items().getFirst();

            assertEquals("Song one", track.details().title());
            assertEquals("https://cdn.example/one.mp3",
                    service.resolve((TrackTarget.Lx) track.target())
                            .get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void unchangedRefreshKeepsRuntimeWhileChangedRefreshReplacesIt() throws Exception {
        Path source = directory.resolve("external.js");
        Files.writeString(source, pendingScript("Pending"));

        try (LxSourceService service = service()) {
            service.importSourceAsync(source.toUri().toString()).get(5, TimeUnit.SECONDS);
            TrackTarget.Lx target = target("one");
            var pending = service.resolve(target);

            LxSourceService.RefreshResult unchanged = service.refreshSubscriptionsAsync()
                    .get(5, TimeUnit.SECONDS);

            assertEquals(0, unchanged.changed());
            Thread.sleep(100);
            assertFalse(pending.isDone());

            Files.writeString(source, searchableScript("Replacement", "two"));
            LxSourceService.RefreshResult changed = service.refreshSubscriptionsAsync()
                    .get(5, TimeUnit.SECONDS);

            assertEquals(1, changed.changed());
            assertThrows(ExecutionException.class,
                    () -> pending.get(500, TimeUnit.MILLISECONDS));
            assertEquals("https://cdn.example/two.mp3",
                    service.resolve(target).get(5, TimeUnit.SECONDS));
            assertEquals("Replacement", service.statuses().getFirst().name());
        }
    }

    @Test
    void removingSubscriptionAlsoRemovesItsActiveRuntime() throws Exception {
        Path source = directory.resolve("external.js");
        Files.writeString(source, searchableScript("Removable", "one"));

        try (LxSourceService service = service()) {
            String id = service.importSourceAsync(source.toUri().toString())
                    .get(5, TimeUnit.SECONDS).id();

            assertTrue(service.removeSourceAsync(id).get(5, TimeUnit.SECONDS));
            assertFalse(service.removeSourceAsync(id).get(5, TimeUnit.SECONDS));
            assertEquals(List.of(), service.subscriptionStatuses());
            assertEquals(List.of(), service.statuses());
        }
    }

    @Test
    void rejectsManagementOperationsAfterClose() {
        LxSourceService service = service();
        service.close();

        assertTrue(service.reloadAsync().isCompletedExceptionally());
        assertTrue(service.importSourceAsync("file:///tmp/source.js").isCompletedExceptionally());
        assertTrue(service.removeSourceAsync("remote-000000000000000000000000")
                .isCompletedExceptionally());
        assertTrue(service.refreshSubscriptionsAsync().isCompletedExceptionally());
    }

    private LxSourceService service() {
        return new LxSourceService(directory.resolve("lxmusic"), ignored -> {}, ignored -> {});
    }

    private static TrackTarget.Lx target(String id) {
        return new TrackTarget.Lx("kw", id, List.of("320k"),
                "{\"id\":\"kw_" + id + "\",\"source\":\"kw\","
                        + "\"songmid\":\"" + id + "\"}");
    }

    private static String searchableScript(String name, String id) {
        return """
                /*!
                 * @name %s
                 */
                const { EVENT_NAMES, on, send } = globalThis.lx
                on(EVENT_NAMES.request, ({ action }) => {
                  if (action === 'musicSearch') return { total: 1, list: [{
                    id: 'kw_%s', name: 'Song %s', singer: 'Artist', source: 'kw',
                    songmid: '%s', types: ['320k']
                  }]}
                  if (action === 'musicUrl') return 'https://cdn.example/%s.mp3'
                  throw new Error('unsupported')
                })
                send(EVENT_NAMES.inited, { status: true, sources: {
                  kw: { type: 'music', actions: ['musicUrl', 'musicSearch'], qualitys: ['320k'] }
                }})
                """.formatted(name, id, id, id, id);
    }

    private static String pendingScript(String name) {
        return """
                /*!
                 * @name %s
                 */
                const { EVENT_NAMES, on, send } = globalThis.lx
                on(EVENT_NAMES.request, () => new Promise(() => {}))
                send(EVENT_NAMES.inited, { status: true, sources: {
                  kw: { type: 'music', actions: ['musicUrl'], qualitys: ['320k'] }
                }})
                """.formatted(name);
    }
}
