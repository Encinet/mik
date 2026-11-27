package org.encinet.mik.feature.world.heads.catalog;

import com.github.luben.zstd.ZstdInputStream;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import org.encinet.mik.feature.world.heads.HeadFiles;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Reads the public HeadsDB distribution. A release is visible only after both resources validate. */
final class HeadFeed implements AutoCloseable {
    static final URI MANIFEST = URI.create("https://data.headsdb.com/manifest.json");
    private static final String MANIFEST_ENTRY = "manifest.json";
    private static final int MANIFEST_LIMIT = 64 * 1024;
    private static final int COMPRESSED_LIMIT = 16 * 1024 * 1024;
    private static final int JSON_LIMIT = 32 * 1024 * 1024;
    private static final int PART_LIMIT = 32;
    private static final int ENTRY_LIMIT = 300_000;
    private static final long RELEASE_LIMIT = 64L * 1024 * 1024;
    private static final Pattern PATH = Pattern.compile("artifacts/[a-z0-9-]+/[a-z0-9-]+\\.json(?:\\.zst)?");
    private static final Pattern SHA256 = Pattern.compile("[a-f0-9]{64}");
    private static final Set<String> HOSTS = Set.of("data.headsdb.com", "mirror.headsdb.com");
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();

    record Release(long revision, HeadCatalog catalog) { }
    record Refresh(Release release, IOException cacheFailure) { }
    @FunctionalInterface
    interface Download { byte[] get(URI uri, int limit) throws IOException, InterruptedException; }

    private final Path cache;
    private final HttpClient client;
    private final Download download;

    HeadFeed(Path cache) {
        this.cache = cache;
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        download = this::get;
    }

    HeadFeed(Path cache, Download download) {
        this.cache = cache;
        client = null;
        this.download = download;
    }

    Release load() throws IOException, InterruptedException {
        if (!Files.exists(cache)) return null;
        if (Files.size(cache) > RELEASE_LIMIT + MANIFEST_LIMIT * 2L) throw new IOException("HeadsDB cache is too large");
        try (var zip = new ZipFile(cache.toFile())) {
            byte[] manifest = read(zip, MANIFEST_ENTRY, MANIFEST_LIMIT);
            if (manifest == null) throw new IOException("Missing HeadsDB cache manifest");
            return assemble(manifest(manifest), (uri, limit) -> read(zip, uri.getPath().substring(1), limit),
                    new LinkedHashMap<>());
        }
    }

    Refresh refresh(Release previous) throws IOException, InterruptedException {
        byte[] bytes = download.get(MANIFEST, MANIFEST_LIMIT);
        Manifest manifest = manifest(bytes);
        if (previous != null && manifest.revision() <= previous.revision()) {
            if (manifest.revision() < previous.revision()) throw new IOException("HeadsDB manifest revision went backwards");
            return new Refresh(previous, null);
        }
        var artifacts = new LinkedHashMap<String, byte[]>();
        Release release = assemble(manifest, download, artifacts);
        IOException failure = null;
        try {
            HeadFiles.replace(cache, output -> {
                try (var zip = new ZipOutputStream(output)) {
                    zip.setLevel(Deflater.NO_COMPRESSION);
                    write(zip, MANIFEST_ENTRY, bytes);
                    for (var artifact : artifacts.entrySet()) write(zip, artifact.getKey(), artifact.getValue());
                }
            });
        } catch (IOException error) {
            failure = error;
        }
        return new Refresh(release, failure);
    }

    private static Release assemble(Manifest manifest, Download source, Map<String, byte[]> artifacts)
            throws IOException, InterruptedException {
        URI mirror = manifest.mirrors().stream().sorted(Comparator.comparingInt(Mirror::priority))
                .map(Mirror::url).map(URI::create).filter(HeadFeed::allowedMirror).findFirst()
                .orElseThrow(() -> new IOException("No recognized HeadsDB mirror"));
        var reader = new ReleaseReader(mirror, source, artifacts);

        var removals = new HashSet<Integer>();
        var revokeIndex = reader.decode(resource(manifest, "revocations"), "revocations-index", Index.class);
        for (Artifact artifact : parts(revokeIndex, "revocations")) {
            var part = reader.decode(artifact, "revocations-part", Revocations.class);
            checkSchema(part.schema());
            for (Revoked revoked : part.revocations()) {
                if (revoked.id() <= 0 || !removals.add(revoked.id()) || removals.size() > ENTRY_LIMIT)
                    throw new IOException("Invalid or duplicate revoked head ID");
            }
        }

        var index = reader.decode(resource(manifest, "catalog"), "catalog-index", CatalogIndex.class);
        checkSchema(index.schema());
        var heads = new ArrayList<HeadEntry>();
        for (Artifact artifact : parts(new Index(index.schema(), index.id(), index.artifacts()), "catalog")) {
            var part = reader.decode(artifact, "catalog-part", CatalogPart.class);
            checkSchema(part.schema());
            if (heads.size() + (long) part.heads().size() > ENTRY_LIMIT) throw new IOException("Too many HeadsDB entries");
            for (RemoteHead head : part.heads()) {
                if (head.id() <= 0 || !HeadEntry.validTexture(head.texture()) || head.category() == null)
                    throw new IOException("Invalid HeadsDB entry " + head.id());
                if (!removals.contains(head.id())) heads.add(new HeadEntry(Integer.toString(head.id()), head.name(),
                        head.texture(), head.category(), head.tags(), head.collections()));
            }
        }
        if (heads.isEmpty()) throw new IOException("HeadsDB catalog is empty");
        return new Release(manifest.revision(), new HeadCatalog(heads, taxonomy(index.categories(), index.tags(), index.collections())));
    }

    static HeadCatalog local(Path path) throws IOException {
        if (!Files.exists(path)) return null;
        var local = json(HeadFiles.read(path, 1024 * 1024), LocalCatalog.class);
        checkSchema(local.schema());
        if (local.heads().size() > 2_000) throw new IOException("Too many local decorative heads");
        var heads = new ArrayList<HeadEntry>();
        for (LocalHead head : local.heads()) {
            HeadEntry.requireFacet(head.id());
            if (!HeadEntry.validTexture(head.texture())) throw new IOException("Invalid local head texture: " + head.id());
            heads.add(new HeadEntry("custom:" + head.id(), head.name(), head.texture(), head.category(), head.tags(), head.collections()));
        }
        return new HeadCatalog(heads, taxonomy(local.categories(), local.tags(), local.collections()));
    }

    private static Map<HeadCatalog.Facet, List<HeadCatalog.Label>> taxonomy(List<HeadCatalog.Label> categories,
            List<HeadCatalog.Label> tags, List<HeadCatalog.Label> collections) {
        return Map.of(HeadCatalog.Facet.CATEGORY, categories == null ? List.of() : categories,
                HeadCatalog.Facet.TAG, tags == null ? List.of() : tags,
                HeadCatalog.Facet.COLLECTION, collections == null ? List.of() : collections);
    }

    private static Manifest manifest(byte[] bytes) throws IOException {
        Manifest manifest = json(bytes, Manifest.class);
        checkSchema(manifest.schema());
        if (!"heads".equals(manifest.id()) || manifest.revision() <= 0) throw new IOException("Invalid HeadsDB manifest");
        return manifest;
    }

    private static Artifact resource(Manifest manifest, String id) throws IOException {
        Resource resource = manifest.resources().get(id);
        if (resource == null) throw new IOException("Missing HeadsDB resource: " + id);
        return resource.index();
    }

    private static List<Artifact> parts(Index index, String id) throws IOException {
        checkSchema(index.schema());
        if (!id.equals(index.id()) || index.artifacts().isEmpty() || index.artifacts().size() > PART_LIMIT)
            throw new IOException("Invalid HeadsDB index: " + id);
        var sorted = index.artifacts().stream().sorted(Comparator.comparingInt(Artifact::index)).toList();
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).index() != i) throw new IOException("Incomplete HeadsDB index: " + id);
        }
        return sorted;
    }

    private static boolean allowedMirror(URI uri) {
        return "https".equals(uri.getScheme()) && uri.getHost() != null && HOSTS.contains(uri.getHost())
                && uri.getUserInfo() == null && uri.getPort() == -1 && uri.getQuery() == null
                && uri.getFragment() == null && "/".equals(uri.getPath());
    }

    private static void checkSchema(int schema) throws IOException {
        if (schema != 1) throw new IOException("Unsupported HeadsDB schema: " + schema);
    }

    private static <T> T json(byte[] bytes, Class<T> type) throws IOException {
        try {
            T value = JSON.fromJson(new String(bytes, StandardCharsets.UTF_8), type);
            if (value == null) throw new IOException("Missing " + type.getSimpleName());
            return value;
        } catch (RuntimeException error) {
            throw new IOException("Invalid " + type.getSimpleName(), error);
        }
    }

    private byte[] get(URI uri, int limit) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(25))
                .header("User-Agent", "MIK-Heads/1.0 (+https://mcmik.top)").GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofByteArray(), limit));
        if (response.statusCode() != 200) throw new IOException("HeadsDB returned HTTP " + response.statusCode());
        return response.body();
    }

    private static byte[] read(ZipFile zip, String name, int limit) throws IOException {
        var entry = zip.getEntry(name);
        if (entry == null) return null;
        try (var input = zip.getInputStream(entry)) { return HeadFiles.read(input, limit); }
    }

    private static void write(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException("SHA-256 is unavailable", error); }
    }

    @Override public void close() { if (client != null) client.shutdownNow(); }

    private static final class ReleaseReader {
        private final URI mirror;
        private final Download source;
        private final Map<String, byte[]> artifacts;
        private long total;

        ReleaseReader(URI mirror, Download source, Map<String, byte[]> artifacts) {
            this.mirror = mirror;
            this.source = source;
            this.artifacts = artifacts;
        }

        <T> T decode(Artifact artifact, String resourceType, Class<T> type) throws IOException, InterruptedException {
            Integrity integrity = artifact.integrity();
            String path = artifact.path();
            if (!PATH.matcher(path).matches() || !artifact.contentType().equals("application/vnd.heads." + resourceType + "+json")
                    || !"sha256".equals(integrity.algorithm()) || !SHA256.matcher(integrity.digest()).matches()
                    || integrity.bytes() < 1 || integrity.bytes() > COMPRESSED_LIMIT
                    || total + integrity.bytes() > RELEASE_LIMIT || artifacts.containsKey(path))
                throw new IOException("Invalid HeadsDB artifact: " + path);
            byte[] bytes = source.get(mirror.resolve(path), integrity.bytes());
            if (bytes == null || bytes.length != integrity.bytes() || !sha256(bytes).equals(integrity.digest()))
                throw new IOException("HeadsDB artifact failed SHA-256 verification: " + path);
            artifacts.put(path, bytes);
            total += bytes.length;
            return switch (artifact.compression()) {
                case "none" -> json(bytes, type);
                case "zstd" -> {
                    try (var input = new ZstdInputStream(new ByteArrayInputStream(bytes))) {
                        input.setLongMax(25);
                        yield json(HeadFiles.read(input, JSON_LIMIT), type);
                    }
                }
                default -> throw new IOException("Unsupported HeadsDB compression: " + artifact.compression());
            };
        }
    }

    private record Manifest(int schema, String id, long revision, List<Mirror> mirrors, Map<String, Resource> resources) { }
    private record Mirror(int priority, String url) { }
    private record Resource(Artifact index) { }
    private record Artifact(int index, String path, String contentType, String compression, Integrity integrity) { }
    private record Integrity(String algorithm, String digest, int bytes) { }
    private record Index(int schema, String id, List<Artifact> artifacts) { }
    private record CatalogIndex(int schema, String id, List<Artifact> artifacts, List<HeadCatalog.Label> categories,
                                List<HeadCatalog.Label> tags, List<HeadCatalog.Label> collections) { }
    private record CatalogPart(int schema, List<RemoteHead> heads) { }
    private record RemoteHead(int id, String name, String texture, String category, Set<String> tags, Set<String> collections) { }
    private record Revocations(int schema, List<Revoked> revocations) { }
    private record Revoked(int id) { }
    private record LocalCatalog(int schema, List<LocalHead> heads, List<HeadCatalog.Label> categories,
                                List<HeadCatalog.Label> tags, List<HeadCatalog.Label> collections) { }
    private record LocalHead(String id, String name, String texture, String category, Set<String> tags, Set<String> collections) { }
}
