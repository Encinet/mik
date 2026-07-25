package org.encinet.mik.module.music.online;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;

/** Maintains trusted LX source snapshots imported from HTTP(S) and file URLs. */
final class LxSubscriptionManager implements AutoCloseable {

    static final int MAX_SCRIPT_BYTES = 2 * 1024 * 1024;
    private static final int MAX_REGISTRY_BYTES = 1024 * 1024;
    private static final int MAX_HEADER_BYTES = 1024;
    private static final int MAX_SOURCES = 128;
    private static final int REGISTRY_VERSION = 1;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final String REGISTRY_FILE = "sources.json";
    private static final String FILE_PREFIX = "remote-";
    private static final java.util.regex.Pattern SOURCE_ID =
            java.util.regex.Pattern.compile("remote-[0-9a-f]{24}");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path remoteDirectory;
    private final Path registryFile;
    private final HttpClient httpClient;
    private final Consumer<String> warningLogger;
    private final Duration requestTimeout;
    private final Map<String, SourceRecord> sources = new LinkedHashMap<>();
    private volatile List<RemoteSourceStatus> statusSnapshot = List.of();
    private final AtomicBoolean closed = new AtomicBoolean();

    LxSubscriptionManager(Path lxDirectory, Consumer<String> warningLogger) {
        this(lxDirectory, warningLogger, REQUEST_TIMEOUT);
    }

    LxSubscriptionManager(Path lxDirectory, Consumer<String> warningLogger,
                          Duration requestTimeout) {
        Objects.requireNonNull(lxDirectory, "lxDirectory");
        this.remoteDirectory = lxDirectory.toAbsolutePath().normalize().resolve("remote");
        this.registryFile = remoteDirectory.resolve(REGISTRY_FILE);
        this.warningLogger = Objects.requireNonNull(warningLogger, "warningLogger");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(requestTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        try {
            Files.createDirectories(remoteDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize LX remote sources", exception);
        }
        try {
            loadRegistry();
        } catch (IOException exception) {
            sources.clear();
            publishStatuses();
            warningLogger.accept("Ignoring invalid LX remote source registry at "
                    + registryFile + ": " + exception.getMessage());
        }
    }

    public synchronized ImportResult importSource(String value) throws IOException {
        ensureOpen();
        URI url = normalizeUrl(value);
        SourceRecord existing = sources.values().stream()
                .filter(source -> source.url().equals(url.toString()))
                .findFirst().orElse(null);
        if (existing != null) {
            UpdateResult update = refresh(existing);
            return new ImportResult(existing.id(), false, update.changed());
        }
        if (sources.size() >= MAX_SOURCES) {
            throw new IOException("LX remote source limit reached (" + MAX_SOURCES + ")");
        }

        String id = stableId(url);
        SourceRecord collision = sources.get(id);
        if (collision != null && !collision.url().equals(url.toString())) {
            throw new IOException("LX remote source ID collision");
        }
        SourceRecord record = new SourceRecord(id, url.toString(), null, null,
                null, null, null);
        UpdateResult update = fetchAndInstall(record, false);
        return new ImportResult(id, true, update.changed());
    }

    public synchronized RefreshResult refreshAll() {
        if (closed.get()) {
            return new RefreshResult(sources.size(), 0, sources.size());
        }
        int changed = 0;
        int failed = 0;
        List<SourceRecord> snapshot = List.copyOf(sources.values());
        for (SourceRecord source : snapshot) {
            try {
                if (refresh(source).changed()) {
                    changed++;
                }
            } catch (IOException exception) {
                failed++;
                warningLogger.accept("Failed to update LX remote source " + source.id()
                        + ": " + exception.getMessage());
            }
        }
        return new RefreshResult(snapshot.size(), changed, failed);
    }

    public synchronized boolean remove(String id) throws IOException {
        ensureOpen();
        String normalizedId = requireSourceId(id);
        SourceRecord existing = sources.get(normalizedId);
        if (existing == null) {
            return false;
        }
        Path snapshot = snapshotPath(normalizedId);
        sources.remove(normalizedId);
        try {
            writeRegistry();
            publishStatuses();
        } catch (IOException exception) {
            sources.put(normalizedId, existing);
            throw exception;
        }
        try {
            Files.deleteIfExists(snapshot);
        } catch (IOException exception) {
            warningLogger.accept("Removed LX remote source " + normalizedId
                    + " but could not delete its inactive snapshot: " + exception.getMessage());
        }
        return true;
    }

    public List<RemoteSourceStatus> statuses() {
        return statusSnapshot;
    }

    public java.util.Set<String> managedScriptNames() {
        return statusSnapshot.stream()
                .map(status -> status.id() + ".js")
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    Path remoteDirectory() {
        return remoteDirectory;
    }

    private UpdateResult refresh(SourceRecord source) throws IOException {
        return fetchAndInstall(source, true);
    }

    private UpdateResult fetchAndInstall(SourceRecord source, boolean registered)
            throws IOException {
        ensureOpen();
        Path destination = snapshotPath(source.id());
        Instant checkedAt = Instant.now();
        try {
            FetchResult fetched = fetch(source, destination);
            if (fetched.notModified()) {
                if (!registered) {
                    throw new IOException(
                            "LX source URL returned 304 for a new subscription");
                }
                SourceRecord checked = source.checked(checkedAt, fetched.etag(),
                        fetched.lastModified());
                replaceAndPersist(source, checked, registered);
                return new UpdateResult(false);
            }

            byte[] script = fetched.script();
            boolean changed = !sameContent(destination, script);
            Instant updatedAt = changed || source.updatedAt() == null
                    ? checkedAt : source.updatedAt();
            SourceRecord updated = source.updated(checkedAt, updatedAt,
                    fetched.etag(), fetched.lastModified());
            if (!changed && registered) {
                replaceAndPersist(source, updated, registered);
                return new UpdateResult(false);
            }

            Path candidate = Files.createTempFile(remoteDirectory, source.id() + "-", ".candidate");
            try {
                Files.write(candidate, script);
                validate(candidate, source.id());
                installAndPersist(candidate, destination, source, updated, registered);
            } finally {
                deleteQuietly(candidate);
            }
            return new UpdateResult(true);
        } catch (IOException exception) {
            if (registered) {
                SourceRecord failed = source.failed(checkedAt, exception.getMessage());
                try {
                    replaceAndPersist(source, failed, true);
                } catch (IOException registryError) {
                    exception.addSuppressed(registryError);
                }
            }
            throw exception;
        }
    }

    private FetchResult fetch(SourceRecord source, Path destination) throws IOException {
        URI url = URI.create(source.url());
        return switch (url.getScheme().toLowerCase(Locale.ROOT)) {
            case "http", "https" -> fetchHttp(source, destination, url);
            case "file" -> fetchFile(url);
            default -> throw new IOException("Unsupported LX source URL scheme");
        };
    }

    private FetchResult fetchHttp(SourceRecord source, Path destination, URI url)
            throws IOException {
        HttpRequest request;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(url)
                    .timeout(requestTimeout)
                    .header("Accept", "application/javascript, text/javascript, text/plain;q=0.9, */*;q=0.1")
                    .header("User-Agent", "MIK Minecraft server")
                    .GET();
            if (Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)) {
                addConditionalHeader(builder, "If-None-Match", source.etag());
                addConditionalHeader(builder, "If-Modified-Since", source.lastModified());
            }
            request = builder.build();
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid LX source URL", exception);
        }
        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(request, limitedBodyHandler(MAX_SCRIPT_BYTES));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("LX source download was interrupted", exception);
        }
        if (response.statusCode() == 304) {
            if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("LX source URL returned 304 without a local snapshot");
            }
            return FetchResult.notModified(response.headers().firstValue("ETag").orElse(source.etag()),
                    response.headers().firstValue("Last-Modified").orElse(source.lastModified()));
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("LX source URL responded with HTTP " + response.statusCode());
        }
        return FetchResult.script(requireUtf8Script(response.body()),
                headerValue(response, "ETag"), headerValue(response, "Last-Modified"));
    }

    private static FetchResult fetchFile(URI url) throws IOException {
        Path source;
        try {
            source = Path.of(url).toAbsolutePath().normalize();
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid local LX source URL", exception);
        }
        if (!Files.isRegularFile(source)) {
            throw new IOException("Local LX source is not a regular file: " + source);
        }
        byte[] bytes = readLimited(Files.newInputStream(source), MAX_SCRIPT_BYTES);
        String modified = Files.getLastModifiedTime(source).toInstant().toString();
        return FetchResult.script(requireUtf8Script(bytes), null, modified);
    }

    private void validate(Path candidate, String id) throws IOException {
        try (LxCustomSourceRuntime runtime = new LxCustomSourceRuntime(
                "validation/" + id, candidate, requestTimeout, warningLogger)) {
            runtime.initialized().get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("LX source validation was interrupted", exception);
        } catch (Exception exception) {
            throw new IOException("LX source validation failed: " + rootMessage(exception), exception);
        }
    }

    private void replaceAndPersist(SourceRecord previous, SourceRecord replacement,
                                   boolean registered) throws IOException {
        sources.put(replacement.id(), replacement);
        try {
            writeRegistry();
            publishStatuses();
        } catch (IOException exception) {
            if (registered) {
                sources.put(previous.id(), previous);
            } else {
                sources.remove(replacement.id());
            }
            throw exception;
        }
    }

    private void installAndPersist(Path candidate, Path destination, SourceRecord previous,
                                   SourceRecord replacement, boolean registered) throws IOException {
        Path backup = backup(destination, previous.id());
        boolean installed = false;
        try {
            moveIntoPlace(candidate, destination);
            installed = true;
            replaceAndPersist(previous, replacement, registered);
        } catch (IOException exception) {
            if (installed) {
                if (backup == null) {
                    try {
                        Files.deleteIfExists(destination);
                    } catch (IOException rollbackError) {
                        exception.addSuppressed(rollbackError);
                    }
                } else {
                    restore(backup, destination, exception);
                }
            }
            throw exception;
        } finally {
            deleteQuietly(backup);
        }
    }

    private void loadRegistry() throws IOException {
        if (!Files.exists(registryFile)) {
            return;
        }
        if (!Files.isRegularFile(registryFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("LX remote source registry is not a regular file");
        }
        byte[] bytes = readLimited(Files.newInputStream(registryFile), MAX_REGISTRY_BYTES);
        JsonElement root;
        try {
            root = JsonParser.parseString(requireUtf8(bytes));
        } catch (RuntimeException exception) {
            throw new IOException("LX remote source registry is invalid JSON", exception);
        }
        if (!root.isJsonObject()) {
            throw new IOException("LX remote source registry root must be an object");
        }
        JsonObject object = root.getAsJsonObject();
        if (intValue(object, "version", -1) != REGISTRY_VERSION) {
            throw new IOException("Unsupported LX remote source registry version");
        }
        JsonElement entries = object.get("sources");
        if (entries == null || !entries.isJsonArray()) {
            throw new IOException("LX remote source registry has no sources array");
        }
        if (entries.getAsJsonArray().size() > MAX_SOURCES) {
            throw new IOException("LX remote source registry exceeds " + MAX_SOURCES + " sources");
        }
        for (JsonElement entry : entries.getAsJsonArray()) {
            SourceRecord source = parseRecord(entry);
            if (sources.putIfAbsent(source.id(), source) != null) {
                throw new IOException("Duplicate LX remote source ID: " + source.id());
            }
        }
        publishStatuses();
    }

    private SourceRecord parseRecord(JsonElement value) throws IOException {
        if (!value.isJsonObject()) {
            throw new IOException("LX remote source entry must be an object");
        }
        JsonObject object = value.getAsJsonObject();
        String id = requireSourceId(stringValue(object, "id"));
        URI url = normalizeUrl(stringValue(object, "url"));
        if (!id.equals(stableId(url))) {
            throw new IOException("LX remote source ID does not match its URL: " + id);
        }
        return new SourceRecord(id, url.toString(), boundedHeader(nullableString(object, "etag")),
                boundedHeader(nullableString(object, "lastModified")),
                nullableInstant(object, "updatedAt"),
                nullableInstant(object, "lastCheckedAt"), nullableString(object, "lastError"));
    }

    private void writeRegistry() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", REGISTRY_VERSION);
        JsonArray entries = new JsonArray();
        sources.values().stream().sorted(Comparator.comparing(SourceRecord::id))
                .map(LxSubscriptionManager::toJson)
                .forEach(entries::add);
        root.add("sources", entries);
        byte[] bytes = (GSON.toJson(root) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_REGISTRY_BYTES) {
            throw new IOException("LX remote source registry exceeds "
                    + MAX_REGISTRY_BYTES + " bytes");
        }
        Path temporary = Files.createTempFile(remoteDirectory, ".sources-", ".json.tmp");
        try {
            Files.write(temporary, bytes);
            moveIntoPlace(temporary, registryFile);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static JsonObject toJson(SourceRecord source) {
        JsonObject object = new JsonObject();
        object.addProperty("id", source.id());
        object.addProperty("url", source.url());
        addNullable(object, "etag", source.etag());
        addNullable(object, "lastModified", source.lastModified());
        addNullable(object, "updatedAt", format(source.updatedAt()));
        addNullable(object, "lastCheckedAt", format(source.lastCheckedAt()));
        addNullable(object, "lastError", source.lastError());
        return object;
    }

    private Path snapshotPath(String id) throws IOException {
        String safeId = requireSourceId(id);
        Path path = remoteDirectory.resolve(safeId + ".js").normalize();
        if (!path.getParent().equals(remoteDirectory)) {
            throw new IOException("LX remote source path escapes its directory");
        }
        return path;
    }

    private static URI normalizeUrl(String value) throws IOException {
        if (value == null || value.isBlank()) {
            throw new IOException("LX source URL is blank");
        }
        if (value.length() > 4096) {
            throw new IOException("LX source URL is too long");
        }
        URI parsed;
        try {
            parsed = new URI(value.strip()).normalize();
        } catch (URISyntaxException exception) {
            throw new IOException("Invalid LX source URL", exception);
        }
        String scheme = parsed.getScheme() == null
                ? "" : parsed.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https") && !scheme.equals("file")) {
            throw new IOException("LX source URL must use http, https, or file");
        }
        if ((scheme.equals("http") || scheme.equals("https"))
                && (parsed.getHost() == null || parsed.getHost().isBlank())) {
            throw new IOException("HTTP LX source URL has no host");
        }
        if ((scheme.equals("http") || scheme.equals("https"))
                && parsed.getUserInfo() != null) {
            throw new IOException("HTTP LX source URL cannot contain credentials");
        }
        if (scheme.equals("file") && (parsed.getQuery() != null || parsed.getFragment() != null)) {
            throw new IOException("Local LX source URL cannot have a query or fragment");
        }
        if (parsed.getFragment() != null) {
            try {
                parsed = new URI(parsed.getScheme(), parsed.getUserInfo(), parsed.getHost(),
                        parsed.getPort(), parsed.getPath(), parsed.getQuery(), null);
            } catch (URISyntaxException exception) {
                throw new IOException("Invalid LX source URL", exception);
            }
        }
        return parsed;
    }

    private static String stableId(URI url) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(url.toString().getBytes(StandardCharsets.UTF_8));
            return FILE_PREFIX + HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is unavailable", exception);
        }
    }

    private static String requireSourceId(String id) throws IOException {
        if (id == null || !SOURCE_ID.matcher(id.strip()).matches()) {
            throw new IOException("Invalid LX remote source ID");
        }
        return id.strip();
    }

    private static boolean sameContent(Path path, byte[] expected) throws IOException {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                && Files.size(path) == expected.length
                && MessageDigest.isEqual(Files.readAllBytes(path), expected);
    }

    private static byte[] readLimited(InputStream input, int maximum) throws IOException {
        try (input) {
            byte[] bytes = input.readNBytes(maximum + 1);
            if (bytes.length > maximum) {
                throw new IOException("LX source data exceeds " + maximum + " bytes");
            }
            return bytes;
        }
    }

    private static byte[] requireUtf8Script(byte[] bytes) throws IOException {
        String script = requireUtf8(bytes);
        if (script.isBlank()) {
            throw new IOException("LX source script is empty");
        }
        if (script.indexOf('\0') >= 0) {
            throw new IOException("LX source script contains NUL characters");
        }
        return script.getBytes(StandardCharsets.UTF_8);
    }

    private static String requireUtf8(byte[] bytes) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new IOException("LX source data is not valid UTF-8", exception);
        }
    }

    private static void moveIntoPlace(Path temporary, Path destination) throws IOException {
        try {
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path backup(Path source, String id) throws IOException {
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        Path backup = Files.createTempFile(remoteDirectory, "." + id + "-", ".backup");
        try {
            Files.copy(source, backup, StandardCopyOption.REPLACE_EXISTING);
            return backup;
        } catch (IOException exception) {
            Files.deleteIfExists(backup);
            throw exception;
        }
    }

    private static void restore(Path backup, Path destination, IOException failure) {
        if (backup == null) {
            return;
        }
        try {
            moveIntoPlace(backup, destination);
        } catch (IOException rollbackError) {
            failure.addSuppressed(rollbackError);
        }
    }

    private void publishStatuses() {
        statusSnapshot = sources.values().stream()
                .sorted(Comparator.comparing(SourceRecord::id))
                .map(source -> new RemoteSourceStatus(source.id(), URI.create(source.url()),
                        source.updatedAt(), source.lastCheckedAt(), source.lastError()))
                .toList();
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static String stringValue(JsonObject object, String key) throws IOException {
        String value = nullableString(object, key);
        if (value == null || value.isBlank()) {
            throw new IOException("LX remote source is missing " + key);
        }
        return value;
    }

    private static String nullableString(JsonObject object, String key) throws IOException {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IOException("LX remote source field " + key + " must be a string");
        }
        return value.getAsString();
    }

    private static Instant nullableInstant(JsonObject object, String key) throws IOException {
        String value = nullableString(object, key);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IOException("LX remote source field " + key + " is not an instant", exception);
        }
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        try {
            return value.getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static void addNullable(JsonObject object, String key, String value) {
        if (value == null) {
            object.add(key, com.google.gson.JsonNull.INSTANCE);
        } else {
            object.addProperty(key, value);
        }
    }

    private static String format(Instant value) {
        return value == null ? null : value.toString();
    }

    private static String headerValue(HttpResponse<?> response, String name) {
        return boundedHeader(response.headers().firstValue(name).orElse(null));
    }

    private static String boundedHeader(String value) {
        if (value == null || value.isBlank()
                || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0
                || value.getBytes(StandardCharsets.UTF_8).length > MAX_HEADER_BYTES) {
            return null;
        }
        return value;
    }

    private static void addConditionalHeader(HttpRequest.Builder request,
                                             String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            request.header(name, value);
        } catch (IllegalArgumentException ignored) {
            // A damaged registry validator falls back to an unconditional request.
        }
    }

    private void ensureOpen() throws IOException {
        if (closed.get()) {
            throw new IOException("LX remote source manager is closed");
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            httpClient.shutdownNow();
        }
    }

    public record ImportResult(String id, boolean added, boolean changed) {
    }

    public record RefreshResult(int total, int changed, int failed) {
    }

    public record RemoteSourceStatus(String id, URI url, Instant updatedAt,
                                     Instant lastCheckedAt, String lastError) {
    }

    private record UpdateResult(boolean changed) {
    }

    private record FetchResult(byte[] script, boolean notModified,
                               String etag, String lastModified) {
        private static FetchResult script(byte[] script, String etag, String lastModified) {
            return new FetchResult(script, false, etag, lastModified);
        }

        private static FetchResult notModified(String etag, String lastModified) {
            return new FetchResult(null, true, etag, lastModified);
        }
    }

    private record SourceRecord(String id, String url, String etag, String lastModified,
                                Instant updatedAt, Instant lastCheckedAt, String lastError) {
        private SourceRecord checked(Instant checkedAt, String newEtag, String newLastModified) {
            return new SourceRecord(id, url, coalesce(boundedHeader(newEtag), etag),
                    coalesce(boundedHeader(newLastModified), lastModified),
                    updatedAt, checkedAt, null);
        }

        private SourceRecord updated(Instant checkedAt, Instant newUpdatedAt,
                                     String newEtag, String newLastModified) {
            return new SourceRecord(id, url, newEtag, newLastModified,
                    newUpdatedAt, checkedAt, null);
        }

        private SourceRecord failed(Instant checkedAt, String error) {
            String message = error == null ? "Unknown update failure" : error;
            if (message.length() > 1024) {
                message = message.substring(0, 1024);
            }
            return new SourceRecord(id, url, etag, lastModified,
                    updatedAt, checkedAt, message);
        }

        private static String coalesce(String preferred, String fallback) {
            return preferred == null || preferred.isBlank() ? fallback : preferred;
        }
    }

    private static HttpResponse.BodyHandler<byte[]> limitedBodyHandler(int maximum) {
        return response -> response.statusCode() >= 200 && response.statusCode() < 300
                ? new LimitedBodySubscriber(maximum)
                : HttpResponse.BodySubscribers.replacing(new byte[0]);
    }

    private static final class LimitedBodySubscriber
            implements HttpResponse.BodySubscriber<byte[]> {
        private final int maximum;
        private final ByteArrayOutputStream output;
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;

        private LimitedBodySubscriber(int maximum) {
            this.maximum = maximum;
            this.output = new ByteArrayOutputStream(Math.min(maximum, 16 * 1024));
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (this.subscription != null) {
                subscription.cancel();
                return;
            }
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (body.isDone()) {
                return;
            }
            for (ByteBuffer buffer : buffers) {
                if ((long) output.size() + buffer.remaining() > maximum) {
                    subscription.cancel();
                    body.completeExceptionally(new IOException(
                            "LX source data exceeds " + maximum + " bytes"));
                    return;
                }
                byte[] chunk = new byte[Math.min(buffer.remaining(), 16 * 1024)];
                while (buffer.hasRemaining()) {
                    int length = Math.min(buffer.remaining(), chunk.length);
                    buffer.get(chunk, 0, length);
                    output.write(chunk, 0, length);
                }
            }
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            body.complete(output.toByteArray());
        }
    }
}
