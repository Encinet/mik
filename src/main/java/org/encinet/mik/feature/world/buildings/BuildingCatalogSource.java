package org.encinet.mik.feature.world.buildings;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/** All network and cache operations belong to the module's background worker. */
final class BuildingCatalogSource implements AutoCloseable {
    private static final URI ENDPOINT = URI.create("https://data.mcmik.top/api/buildings");
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private final Path cache;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    BuildingCatalogSource(Path cache) {
        this.cache = cache;
    }

    BuildingCatalog cached() throws IOException {
        if (!Files.exists(cache)) return null;
        if (Files.size(cache) > MAX_BYTES) throw new IOException("Building catalog cache exceeds 2 MiB");
        return BuildingCatalog.parse(Files.readString(cache));
    }

    Download fetch() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(ENDPOINT).timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("User-Agent", "Mik-BuildingMap/1.0 (+https://mcmik.top)").GET().build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8), MAX_BYTES));
        if (response.statusCode() != 200) throw new IOException("Building catalog HTTP " + response.statusCode());
        return new Download(BuildingCatalog.parse(response.body()), response.body());
    }

    void save(Download download) throws IOException {
        Files.createDirectories(cache.getParent());
        Path temporary = Files.createTempFile(cache.getParent(), "catalog-", ".tmp");
        try {
            Files.writeString(temporary, download.json());
            try {
                Files.move(temporary, cache, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, cache, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public void close() {
        client.shutdownNow();
    }

    record Download(BuildingCatalog catalog, String json) { }
}
