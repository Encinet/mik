package org.encinet.mik.feature.world.heads.catalog;

import com.github.luben.zstd.Zstd;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HeadFeedTest {
    @Test void validatesASplitReleaseAndPreservesTheLastVerifiedCache(@TempDir Path folder) throws Exception {
        var data = release(5);
        Path cache = folder.resolve("catalog.zip");
        try (var feed = new HeadFeed(cache, (uri, _) -> data.get(uri.getPath()))) {
            assertNull(feed.load());
            var fetched = feed.refresh(null);
            assertNull(fetched.cacheFailure());
            var original = fetched.release();
            assertEquals(5, original.revision());
            assertEquals(3, original.catalog().size());
            assertNull(original.catalog().find("4"));
            assertNotNull(original.catalog().find("10"));
            assertSame(original, feed.refresh(original).release());

            data.putAll(release(6));
            data.get("/artifacts/catalog/part-one.json.zst")[0] ^= 1;
            assertThrows(IOException.class, () -> feed.refresh(original));
            assertEquals(5, feed.load().revision());
            assertEquals(3, feed.load().catalog().size());

            data.putAll(release(4));
            assertThrows(IOException.class, () -> feed.refresh(original));
        }
        try (var offline = new HeadFeed(cache, (_, _) -> { throw new IOException("offline"); })) {
            assertEquals("Silver Fox", offline.load().catalog().find("10").name());
        }
    }

    @Test void searchesAcrossTaxonomyAndKeepsNumericIDsInOrder(@TempDir Path folder) throws Exception {
        var data = release(5);
        try (var feed = new HeadFeed(folder.resolve("catalog.zip"), (uri, _) -> data.get(uri.getPath()))) {
            var catalog = feed.refresh(null).release().catalog();
            assertEquals(List.of("2", "10"), search(catalog, "fox forest", Map.of(), HeadCatalog.Sort.RELEVANCE, false));
            assertEquals(List.of("10"), search(catalog, "#10", Map.of(), HeadCatalog.Sort.RELEVANCE, false));
            assertEquals(List.of("10", "2", "1"), search(catalog, "", Map.of(), HeadCatalog.Sort.ID, true));
            assertEquals(List.of("10"), search(catalog, "", Map.of(HeadCatalog.Facet.CATEGORY, Set.of("plants", "animals"),
                    HeadCatalog.Facet.TAG, Set.of("fox", "silver"), HeadCatalog.Facet.COLLECTION, Set.of("forest")), HeadCatalog.Sort.NAME, false));
        }
    }

    @Test void localHeadsUseSeparateStableIDsAndTheirOwnTaxonomy(@TempDir Path folder) throws Exception {
        var file = folder.resolve("custom.json");
        Files.writeString(file, """
                {"schema":1,"categories":[{"id":"garden","name":"花园"}],
                  "heads":[{"id":"fox","name":"狐狸","texture":"abc123","category":"garden"},
                           {"id":"lamp","name":"灯笼","texture":"c0fefe"}]}
                """);
        var catalog = HeadFeed.local(file);
        assertEquals(List.of("custom:fox"), search(catalog, "花园", Map.of(), HeadCatalog.Sort.RELEVANCE, false));
        assertEquals(List.of("custom:fox"), search(catalog, "custom:fox", Map.of(), HeadCatalog.Sort.ID, false));
        assertEquals(List.of("custom:fox"), search(catalog, "", Map.of(HeadCatalog.Facet.CATEGORY, Set.of("garden")),
                HeadCatalog.Sort.ID, false));
        assertNull(catalog.find("fox"));
    }

    private static List<String> search(HeadCatalog catalog, String query, Map<HeadCatalog.Facet, Set<String>> facets,
                                       HeadCatalog.Sort sort, boolean descending) {
        return catalog.search(new HeadCatalog.Filter(query, facets, sort, descending)).stream().map(HeadEntry::id).toList();
    }

    private static Map<String, byte[]> release(int revision) throws NoSuchAlgorithmException {
        var data = new HashMap<String, byte[]>();
        String first = artifact(data, "catalog", "part-zero", "catalog-part", 0, """
                {"schema":1,"heads":[
                 {"id":1,"name":"Melon","texture":"10ba","category":"plants"},
                 {"id":2,"name":"Blue Fox","texture":"20aa","category":"animals","tags":["fox"],"collections":["forest"]}]}
                """);
        String second = artifact(data, "catalog", "part-one", "catalog-part", 1, """
                {"schema":1,"heads":[
                 {"id":4,"name":"Removed","texture":"40ba","category":"animals"},
                 {"id":10,"name":"Silver Fox","texture":"a0aa","category":"animals","tags":["fox","silver"],"collections":["forest"]}]}
                """);
        String catalog = artifact(data, "catalog", "index", "catalog-index", 0, """
                {"schema":1,"id":"catalog","categories":[{"id":"animals","name":"Animals"},{"id":"plants","name":"Plants"}],
                 "tags":[{"id":"fox","name":"Fox"},{"id":"silver","name":"Silver"}],
                 "collections":[{"id":"forest","name":"Deep Forest"}],"artifacts":[%s,%s]}
                """.formatted(first, second));
        String revoked = artifact(data, "revocations", "part-zero", "revocations-part", 0,
                "{\"schema\":1,\"revocations\":[{\"id\":4}]}");
        String revocations = artifact(data, "revocations", "index", "revocations-index", 0,
                "{\"schema\":1,\"id\":\"revocations\",\"artifacts\":[" + revoked + "]}");
        data.put("/manifest.json", """
                {"schema":1,"id":"heads","revision":%d,"mirrors":[{"priority":0,"url":"https://mirror.headsdb.com/"}],
                 "resources":{"catalog":{"index":%s},"revocations":{"index":%s}}}
                """.formatted(revision, catalog, revocations).getBytes(StandardCharsets.UTF_8));
        return data;
    }

    private static String artifact(Map<String, byte[]> data, String folder, String name, String type, int index, String json)
            throws NoSuchAlgorithmException {
        String path = "artifacts/" + folder + "/" + name + ".json.zst";
        byte[] bytes = Zstd.compress(json.getBytes(StandardCharsets.UTF_8));
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        data.put("/" + path, bytes);
        return """
                {"index":%d,"path":"%s","contentType":"application/vnd.heads.%s+json","compression":"zstd",
                 "integrity":{"algorithm":"sha256","digest":"%s","bytes":%d}}
                """.formatted(index, path, type, sha, bytes.length);
    }
}
