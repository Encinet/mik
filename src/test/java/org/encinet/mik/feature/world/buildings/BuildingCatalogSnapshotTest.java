package org.encinet.mik.feature.world.buildings;

import org.junit.jupiter.api.Test;
import org.encinet.mik.feature.world.buildings.NearbyBuildings.Search;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class BuildingCatalogSnapshotTest {
    @Test
    void ranksSameWorldPagesByDistanceAndBreaksTiesByPublicId() {
        var snapshot = snapshot(
                entry(1, "world", 40, 64, 0), entry(2, "world", 0, 68, 0), entry(3, "world", -3, 64, 0),
                entry(4, "other", 0, 64, 0), entry(5, "world", 0, 64, 3), entry(6, "world", 5, 64, 0),
                entry(7, "world", 0, 64, 6), entry(8, "world", 1, 64, 0)).verified(10);
        var first = snapshot.nearby("world", 0, 64, 0, false, new Search("Hall", 0, 2), 11).orElseThrow();
        assertEquals("world", first.world());
        assertEquals("Hall", first.filter());
        assertEquals(7, first.totalMatches());
        assertEquals(2, first.nextOffset());
        assertEquals(List.of("Hall 8", "Hall 3"), names(first));
        var rest = snapshot.nearby("world", 0, 64, 0, false,
                new Search("Hall", first.nextOffset(), 5), 11).orElseThrow();
        assertEquals(List.of("Hall 5", "Hall 2", "Hall 6", "Hall 7", "Hall 1"), names(rest));
        assertEquals(List.of(3, 4, 5, 6, 40),
                rest.buildings().stream().map(NearbyBuildings.Building::distanceBlocks).toList());
        assertNull(rest.nextOffset());
        assertEquals(List.of(), snapshot.nearby("world-not-rendered", 0, 64, 0, false, search(""), 11)
                .orElseThrow().buildings());
        var beyondEnd = snapshot.nearby("world", 0, 64, 0, false, new Search("Hall", 100, 8), 11).orElseThrow();
        assertEquals(List.of(), beyondEnd.buildings());
        assertEquals(7, beyondEnd.totalMatches());
        assertNull(beyondEnd.nextOffset());
    }

    @Test
    void findsTheExactPublicObjectDespiteDuplicateNamesAndChangedDistance() {
        String targetId = "%032x".formatted(2);
        var snapshot = snapshot(entry(1, "world", 1, 64, 0),
                entry(2, "world", 40, 64, 0).replace("Hall 2", "Hall 1")).verified(10);
        var byName = snapshot.nearby("world", 0, 64, 0, false, search("Hall 1"), 11).orElseThrow();
        assertEquals(2, byName.buildings().size());
        var exact = snapshot.nearby("world", 99, 64, 0, false, search(targetId), 11).orElseThrow();
        assertEquals(List.of(targetId), exact.buildings().stream().map(NearbyBuildings.Building::id).toList());
        assertEquals(59, exact.buildings().getFirst().distanceBlocks());
        assertTrue(snapshot.nearby("other", 99, 64, 0, false, search(targetId), 11).orElseThrow().buildings().isEmpty());
        assertTrue(snapshot.nearby("world", 99, 64, 0, false, search("%032x".formatted(3)), 11)
                .orElseThrow().buildings().isEmpty());
        assertTrue(snapshot.unverified().nearby("world", 99, 64, 0, false, search(targetId), 11).isEmpty());
    }

    @Test
    void filtersBothNamesAndCanonicalBuildTypeIndependentlyOfDisplayLanguage() {
        var snapshot = snapshot(entry(1, "world", 1, 64, 0),
                entry(2, "world", 2, 64, 0).replace("original", "replica")).verified(10);
        assertEquals(List.of("展馆 1"), names(snapshot.nearby("world", 0, 64, 0, true, search(" HALL 1 "), 11).orElseThrow()));
        assertEquals(List.of("Hall 2"), names(snapshot.nearby("world", 0, 64, 0, false, search("展馆 2"), 11).orElseThrow()));
        assertEquals(List.of("Hall 2"), names(snapshot.nearby("world", 0, 64, 0, false, search(" REPLICA "), 11).orElseThrow()));
        assertEquals(new NearbyBuildings("world", "unlisted", 0, 0, List.of(), null),
                snapshot.nearby("world", 0, 64, 0, false, search("unlisted"), 11).orElseThrow());
    }

    @Test
    void refusesToRediscloseLocationsFromBootCacheFailedRefreshOrExpiredSnapshot() {
        var cache = snapshot(entry(1, "world", 1, 64, 0));
        var fresh = cache.verified(10);
        assertEquals(1, fresh.nearby("world", 0, 64, 0, false, search(""), 10).orElseThrow().buildings().size());
        for (var outdated : List.of(cache, fresh.unverified())) {
            assertTrue(outdated.nearby("world", 0, 64, 0, false, search(""), 11).isEmpty());
        }
        assertTrue(fresh.nearby("world", 0, 64, 0, false, search(""), 10 + TimeUnit.MINUTES.toNanos(6)).isEmpty());
        assertEquals(new NearbyBuildings("world", "", 0, 0, List.of(), null),
                snapshot().verified(12).nearby("world", 0, 64, 0, false, search(""), 12).orElseThrow());
    }

    private static Search search(String filter) {
        return new Search(filter, 0, 8);
    }

    private static List<String> names(NearbyBuildings page) {
        return page.buildings().stream().map(NearbyBuildings.Building::name).toList();
    }

    private static BuildingCatalogSnapshot snapshot(String... entries) {
        return new BuildingCatalogSnapshot(BuildingCatalog.parse("[" + String.join(",", entries) + "]"));
    }

    private static String entry(int id, String world, int x, int y, int z) {
        return """
                {"id":"%032x","name":{"zh-CN":"展馆 %d","en":"Hall %d"},
                 "coordinates":{"world":"%s","x":%d,"y":%d,"z":%d},
                 "builders":[{"name":"Builder","weight":1}],"buildType":"original"}
                """.formatted(id, id, id, world, x, y, z);
    }
}
