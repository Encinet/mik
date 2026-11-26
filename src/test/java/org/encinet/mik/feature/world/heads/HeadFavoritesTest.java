package org.encinet.mik.feature.world.heads;

import org.encinet.mik.feature.world.heads.catalog.HeadEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HeadFavoritesTest {
    @Test void acceptsIdempotentChangesAndKeepsFavoritesPrivateAcrossRestarts(@TempDir Path directory) throws IOException {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        var catalogHead = new HeadEntry("123", "Watermelon", "abc123", "plants", Set.of(), Set.of());
        var playerHead = HeadEntry.player(bob, "Bob", "abc456");
        var store = new HeadFavorites(directory);

        store.set(alice, catalogHead, true);
        store.set(alice, catalogHead, true);
        store.set(alice, playerHead, true);
        var reopened = new HeadFavorites(directory);
        assertEquals(Set.of(catalogHead.id(), playerHead.id()), reopened.load(alice).keySet());
        assertEquals("Bob", reopened.load(alice).get(playerHead.id()).name());
        assertTrue(reopened.load(bob).isEmpty());
        reopened.set(alice, playerHead, false);
        assertEquals(Set.of(catalogHead.id()), store.load(alice).keySet());
    }

    @Test void refusesToReplaceAnUnreadableFavoritesFile(@TempDir Path directory) throws IOException {
        UUID player = UUID.randomUUID();
        var file = directory.resolve(player + ".json");
        Files.writeString(file, "{\"schema\": 2, \"entries\": []}");
        var head = HeadEntry.saved("123", "Watermelon");

        var store = new HeadFavorites(directory);
        assertThrows(IOException.class, () -> store.set(player, head, true));
        assertEquals("{\"schema\": 2, \"entries\": []}", Files.readString(file));
    }
}
