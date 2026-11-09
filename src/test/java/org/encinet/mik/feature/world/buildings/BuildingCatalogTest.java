package org.encinet.mik.feature.world.buildings;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingCatalogTest {
    static final String ID = "58ba2dec69dcefe068d0c9b32431ef72";
    static final String ENTRY = """
            {"id":"58ba2dec69dcefe068d0c9b32431ef72",
             "name":{"zh-CN":"樱：序曲","en":"Sakura: Prelude"},
             "coordinates":{"world":"world","x":2681,"y":79,"z":-1094},
             "builders":[{"name":"Second","weight":0.2},{"name":"First","weight":1}],
             "buildType":"derivative","images":["https://images.example/cover.webp"],
             "source":{"originalAuthor":"Original author"}}
            """;

    @Test
    void projectsRealCatalogShapeIncludingCreditsAndWeightedAuthors() {
        var entry = BuildingCatalog.parse("[" + ENTRY + "]").entries().getFirst();
        assertEquals(ID, entry.id());
        assertEquals("樱：序曲", entry.nameZh());
        assertEquals(-1094, entry.z());
        assertEquals("First", entry.builders().getFirst().name());
        assertEquals("Original author", entry.originalAuthor());
        assertEquals("https://images.example/cover.webp", entry.cover());
        assertTrue(BuildingCatalog.parse("[]").entries().isEmpty());
    }

    @Test
    void rejectsIncompleteSnapshotsInsteadOfSilentlyDroppingMarkers() {
        for (String invalid : new String[]{"{}", "null", "[", "[" + ENTRY + ",null]",
                "[" + ENTRY + "," + ENTRY.replace(ID, ID.toUpperCase(java.util.Locale.ROOT)) + "]",
                "[" + ENTRY.replace("2681", "2681.5") + "]",
                "[" + ENTRY.replace("2681", "30000001") + "]",
                "[" + ENTRY.replace("\"world\":\"world\"", "\"world\":null") + "]",
                "[" + ENTRY.replace("\"derivative\"", "\"unknown\"") + "]"}) {
            assertThrows(RuntimeException.class, () -> BuildingCatalog.parse(invalid), invalid);
        }
    }

    @Test
    void unsafeImagesAreOmittedWithoutRemovingTheBuilding() {
        var entry = JsonParser.parseString(ENTRY).getAsJsonObject();
        entry.getAsJsonArray("images").set(0, new com.google.gson.JsonPrimitive("javascript:alert(1)"));
        assertEquals("", BuildingCatalog.parse("[" + entry + "]").entries().getFirst().cover());
    }

    @Test
    void cachesOnlyExplicitlySavedValidDownloads(@TempDir Path directory) throws Exception {
        Path cache = directory.resolve("building-map/catalog.json");
        try (var source = new BuildingCatalogSource(cache)) {
            String json = "[" + ENTRY + "]";
            var catalog = BuildingCatalog.parse(json);
            source.save(new BuildingCatalogSource.Download(catalog, json));
            assertEquals(catalog, source.cached());
            assertThrows(RuntimeException.class, () -> BuildingCatalog.parse("[invalid]"));
            assertEquals(catalog, source.cached());
            Files.writeString(cache, "bad cache");
            assertThrows(RuntimeException.class, source::cached);
        }
    }
}
