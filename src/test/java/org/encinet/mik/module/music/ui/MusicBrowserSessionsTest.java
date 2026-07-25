package org.encinet.mik.module.music.ui;

import org.bukkit.inventory.Inventory;
import org.encinet.mik.module.music.catalog.AudioProperties;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.TrackDetails;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicBrowserSessionsTest {

    @Test
    void rejectsResultFromAnOlderSearchGeneration() {
        MusicBrowserSessions sessions = new MusicBrowserSessions();
        UUID playerId = UUID.randomUUID();
        Inventory firstInventory = inventory();
        MusicBrowserSessions.Session first = sessions.beginSearch(playerId, "first");
        int firstGeneration = first.generation();
        sessions.attachInventory(first, firstInventory);

        MusicBrowserSessions.Session current = sessions.beginSearch(playerId, "second");
        Inventory currentInventory = inventory();
        sessions.attachInventory(current, currentInventory);

        assertFalse(sessions.completeSearch(playerId, first, firstGeneration,
                firstInventory, List.of(track("stale")), null, 0));
        assertTrue(current.loading());
        assertTrue(current.tracks().isEmpty());
    }

    @Test
    void closingCurrentInventoryInvalidatesLateResultsAndOldClicks() {
        MusicBrowserSessions sessions = new MusicBrowserSessions();
        UUID playerId = UUID.randomUUID();
        Inventory inventory = inventory();
        MusicBrowserSessions.Session state = sessions.beginSearch(playerId, "song");
        int generation = state.generation();
        sessions.attachInventory(state, inventory);

        assertTrue(sessions.isCurrentInventory(playerId, inventory, generation,
                state.view(), state.page()));
        sessions.closeInventory(playerId, inventory);

        assertFalse(sessions.isCurrentInventory(playerId, inventory, generation,
                state.view(), state.page()));
        assertFalse(sessions.completeSearch(playerId, state, generation, inventory,
                List.of(track("late")), null, 0));
    }

    @Test
    void publishesAnImmutableSearchSnapshotIntoTheCurrentSession() {
        MusicBrowserSessions sessions = new MusicBrowserSessions();
        UUID playerId = UUID.randomUUID();
        Inventory inventory = inventory();
        MusicBrowserSessions.Session state = sessions.beginSearch(playerId, "song");
        sessions.attachInventory(state, inventory);
        MusicTrack track = track("online");

        assertTrue(sessions.completeSearch(playerId, state, state.generation(), inventory,
                List.of(track), null, 2));
        assertSame(track, state.tracks().getFirst());
        assertFalse(state.loading());
    }

    private static Inventory inventory() {
        return (Inventory) Proxy.newProxyInstance(Inventory.class.getClassLoader(),
                new Class<?>[]{Inventory.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("toString")) {
                        return "test-inventory";
                    }
                    Class<?> type = method.getReturnType();
                    if (!type.isPrimitive()) {
                        return null;
                    }
                    if (type == boolean.class) {
                        return false;
                    }
                    return 0;
                });
    }

    private static MusicTrack track(String id) {
        return new MusicTrack(id,
                new TrackDetails(id, null, null, "MP3", AudioProperties.EMPTY),
                new TrackTarget.LocalFile(Path.of(id + ".mp3")));
    }
}
