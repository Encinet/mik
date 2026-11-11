package org.encinet.mik.feature.community.social.platform.matrix.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.encinet.mik.feature.community.social.platform.matrix.MatrixPlatformConfig;
import org.encinet.mik.feature.community.social.platform.matrix.crypto.MatrixEncryption;
import org.encinet.mik.feature.community.social.platform.matrix.crypto.MatrixEncryptedAttachment;
import org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomDirectory.Security;
import org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixSyncRequest;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/** Minimal authenticated Matrix Client-Server API client used by the adapter. */
public final class MatrixClient {
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private final Duration requestTimeout;

    private final HttpClient httpClient;
    private final MatrixPlatformConfig config;
    private final Gson gson = new Gson();
    private final String transactionPrefix = "mik-" + UUID.randomUUID() + '-';
    private final AtomicLong transactionSequence = new AtomicLong();
    private final String syncFilter;
    private final Function<String, CompletableFuture<Security>> security;
    private final MatrixEncryption encryption;

    public MatrixClient(HttpClient httpClient, MatrixPlatformConfig config,
                        Function<String, CompletableFuture<Security>> security, MatrixEncryption encryption) {
        this(httpClient, config, security, encryption, DEFAULT_REQUEST_TIMEOUT);
    }

    MatrixClient(HttpClient httpClient, MatrixPlatformConfig config,
                 Function<String, CompletableFuture<Security>> security, MatrixEncryption encryption, Duration requestTimeout) {
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        if (requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException("Matrix request timeout must be positive");
        }
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.config = Objects.requireNonNull(config, "config");
        this.encryption = Objects.requireNonNull(encryption, "encryption");
        Objects.requireNonNull(security, "security");
        this.security = roomId -> config.rooms().acceptsMessages(roomId) ? security.apply(roomId)
                : CompletableFuture.completedFuture(Security.UNAVAILABLE);
        syncFilter = createSyncFilter(config.rooms().observedRoomIds());
    }

    public String whoAmI() throws IOException, InterruptedException {
        HttpRequest request = authorizedRequest(
                config.apiUri("/_matrix/client/v3/account/whoami"))
                .timeout(requestTimeout).GET().build();
        JsonObject body = sendJson(request, "Identify Matrix access token");
        String userId = string(body, "user_id");
        if (userId.isBlank()) {
            throw new IllegalStateException("Matrix /whoami omitted user_id");
        }
        return userId;
    }

    public JsonObject sync(MatrixSyncRequest sync)
            throws IOException, InterruptedException {
        StringBuilder query = new StringBuilder("?timeout=")
                .append(sync.timeoutMillis())
                .append("&set_presence=offline&use_state_after=true&filter=")
                .append(encode(syncFilter));
        if (sync.fullState()) query.append("&full_state=true");
        sync.since().ifPresent(since -> query.append("&since=").append(encode(since)));
        HttpRequest request = authorizedRequest(config.apiUri(
                        "/_matrix/client/v3/sync" + query))
                .timeout(Duration.ofMillis(sync.timeoutMillis()).plus(requestTimeout))
                .GET().build();
        return sendJson(request, "Synchronize Matrix events");
    }

    public CompletableFuture<Void> replyText(
            String roomId,
            String repliedEventId,
            Optional<String> threadRootEventId,
            String plainText,
            String html
    ) {
        requireText(plainText, "Matrix reply body");
        requireText(html, "Matrix formatted reply body");
        JsonObject body = new JsonObject();
        body.addProperty("msgtype", "m.text");
        body.addProperty("body", plainText);
        body.addProperty("format", "org.matrix.custom.html");
        body.addProperty("formatted_body", html);
        body.add("m.mentions", new JsonObject());
        body.add("m.relates_to", relation(repliedEventId, threadRootEventId));
        return sendRoomMessage(roomId, body, "Send Matrix text reply");
    }

    public CompletableFuture<Void> sendText(String roomId, String text) {
        JsonObject body = new JsonObject();
        body.addProperty("msgtype", "m.text");
        body.addProperty("body", requireText(text, "Matrix chat body"));
        body.add("m.mentions", new JsonObject());
        return sendRoomMessage(roomId, body, "Send Matrix chat");
    }

    public CompletableFuture<Void> sendFormattedText(
            String roomId,
            String plainText,
            String html
    ) {
        return sendFormattedText(roomId, plainText, html, Set.of());
    }

    public CompletableFuture<Void> sendFormattedText(
            String roomId,
            String plainText,
            String html,
            Collection<String> mentionedUserIds
    ) {
        JsonObject body = new JsonObject();
        body.addProperty("msgtype", "m.text");
        body.addProperty("body", requireText(plainText, "Matrix chat body"));
        body.addProperty("format", "org.matrix.custom.html");
        body.addProperty("formatted_body", requireText(
                html, "Matrix formatted chat body"));
        JsonObject mentions = new JsonObject();
        JsonArray userIds = new JsonArray();
        new java.util.LinkedHashSet<>(Objects.requireNonNullElse(
                mentionedUserIds, Set.<String>of())).stream()
                .filter(userId -> userId != null && !userId.isBlank())
                .forEach(userIds::add);
        if (!userIds.isEmpty()) {
            mentions.add("user_ids", userIds);
        }
        body.add("m.mentions", mentions);
        return sendRoomMessage(roomId, body, "Send formatted Matrix chat");
    }

    public CompletableFuture<Void> replyImage(
            String roomId,
            String repliedEventId,
            Optional<String> threadRootEventId,
            String mediaType,
            byte[] data,
            int width,
            int height,
            String alternativeText,
            String plainCaption,
            String htmlCaption
    ) {
        Objects.requireNonNull(data, "data");
        if (data.length == 0 || data.length > 10_485_760) {
            throw new IllegalArgumentException("Matrix image data has an invalid length");
        }
        if (mediaType == null || !mediaType.matches("image/(png|jpeg|gif|webp)")) {
            throw new IllegalArgumentException("Unsupported Matrix image media type");
        }
        String extension = switch (mediaType) {
            case "image/jpeg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "png";
        };
        String fileName = fileName(alternativeText, extension);
        return MatrixFutures.compose(security.apply(roomId), roomSecurity -> {
            if (roomSecurity == Security.UNAVAILABLE) return unavailableRoom();
            MatrixEncryptedAttachment attachment = roomSecurity == Security.ENCRYPTED ? MatrixEncryptedAttachment.encrypt(data) : null;
            return MatrixFutures.compose(upload(attachment == null ? mediaType : "application/octet-stream",
                    attachment == null ? fileName : "encrypted", attachment == null ? data : attachment.ciphertext()), contentUri ->
                MatrixFutures.compose(security.apply(roomId), currentSecurity -> {
                    if (roomSecurity != currentSecurity) return unavailableRoom();
                    JsonObject body = new JsonObject();
                    body.addProperty("msgtype", "m.image");
                    if (attachment == null) body.addProperty("url", contentUri);
                    else body.add("file", attachment.at(contentUri));
                    body.addProperty("filename", fileName);
                    body.addProperty("body", requireText(plainCaption, "Matrix image caption"));
                    body.addProperty("format", "org.matrix.custom.html");
                    body.addProperty("formatted_body",
                            requireText(htmlCaption, "Matrix formatted image caption"));
                    JsonObject info = new JsonObject();
                    info.addProperty("mimetype", mediaType);
                    info.addProperty("size", data.length);
                    info.addProperty("w", width);
                    info.addProperty("h", height);
                    body.add("info", info);
                    body.add("m.mentions", new JsonObject());
                    body.add("m.relates_to", relation(repliedEventId, threadRootEventId));
                    return sendRoomMessage(roomId, body, "Send Matrix image");
                }));
        });
    }

    private CompletableFuture<String> upload(
            String mediaType,
            String fileName,
            byte[] data
    ) {
        URI endpoint = config.apiUri(
                "/_matrix/media/v3/upload?filename=" + encode(fileName));
        HttpRequest request = authorizedRequest(endpoint)
                .timeout(requestTimeout)
                .header("Content-Type", mediaType)
                .POST(HttpRequest.BodyPublishers.ofByteArray(data))
                .build();
        return MatrixFutures.compose(sendJsonAsync(request, "Upload Matrix image"), body -> {
            String contentUri = string(body, "content_uri");
            if (!contentUri.startsWith("mxc://")) {
                throw new IllegalStateException(
                        "Matrix media upload omitted a valid content_uri");
            }
            return CompletableFuture.completedFuture(contentUri);
        });
    }

    private CompletableFuture<Void> sendRoomMessage(
            String roomId,
            JsonObject body,
            String operation
    ) {
        requireText(roomId, "Matrix room ID");
        return MatrixFutures.compose(security.apply(roomId), roomSecurity -> {
            if (roomSecurity == Security.UNAVAILABLE) return unavailableRoom();
            CompletableFuture<JsonObject> protectedContent = roomSecurity == Security.ENCRYPTED
                    ? encryption.encrypt(roomId, body) : CompletableFuture.completedFuture(body);
            return MatrixFutures.compose(protectedContent, content ->
                    MatrixFutures.compose(security.apply(roomId), currentSecurity -> {
                        if (roomSecurity != currentSecurity) return unavailableRoom();
                        return sendRoomEvent(roomId, roomSecurity == Security.ENCRYPTED
                                ? "m.room.encrypted" : "m.room.message", content, operation);
                    }));
        });
    }

    private CompletableFuture<Void> sendRoomEvent(String roomId, String type, JsonObject body, String operation) {
        String transactionId = transactionPrefix
                + transactionSequence.incrementAndGet();
        URI endpoint = config.apiUri("/_matrix/client/v3/rooms/"
                + encode(roomId) + "/send/" + encode(type) + "/" + encode(transactionId));
        HttpRequest request = authorizedRequest(endpoint)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json; charset=utf-8")
                .PUT(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();
        return MatrixFutures.compose(sendJsonAsync(request, operation), response -> {
            if (string(response, "event_id").isBlank()) {
                throw new IllegalStateException(operation + " response omitted event_id");
            }
            return CompletableFuture.completedFuture(null);
        });
    }

    private HttpRequest.Builder authorizedRequest(URI endpoint) {
        return HttpRequest.newBuilder(endpoint)
                .header("Authorization", "Bearer " + config.accessToken())
                .header("Accept", "application/json")
                .header("User-Agent", "MIK Matrix bot");
    }

    private static <T> CompletableFuture<T> unavailableRoom() {
        return CompletableFuture.failedFuture(new IllegalStateException(
                "Matrix room is unavailable or its encryption state changed during delivery"));
    }

    private JsonObject sendJson(HttpRequest request, String operation)
            throws IOException, InterruptedException {
        CompletableFuture<JsonObject> pending = sendJsonAsync(request, operation);
        try {
            return pending.get();
        } catch (InterruptedException error) {
            pending.cancel(true);
            throw error;
        } catch (ExecutionException error) {
            Throwable cause = MatrixApiErrors.unwrap(error);
            if (cause instanceof TimeoutException) {
                HttpTimeoutException timeout = new HttpTimeoutException(operation + " timed out");
                timeout.initCause(cause);
                throw timeout;
            }
            if (cause instanceof IOException ioError) throw ioError;
            if (cause instanceof RuntimeException runtimeError) throw runtimeError;
            throw new IOException(operation + " failed", cause);
        }
    }

    private CompletableFuture<JsonObject> sendJsonAsync(HttpRequest request, String operation) {
        var receiving = httpClient.sendAsync(request, HttpResponse.BodyHandlers.limiting(
                HttpResponse.BodyHandlers.ofByteArray(), config.syncMaxResponseBytes()));
        // The deadline covers the entire body. Cancel the original exchange, not just its result view.
        var result = receiving.copy().orTimeout(
                        request.timeout().orElse(requestTimeout).toMillis(), TimeUnit.MILLISECONDS)
                .thenApply(response -> parseResponse(operation, response,
                        new String(response.body(), StandardCharsets.UTF_8)));
        result.whenComplete((_, error) -> {
            if (error != null) receiving.cancel(true);
        });
        return result;
    }

    private static JsonObject parseResponse(
            String operation,
            HttpResponse<?> response,
            String responseBody
    ) {
        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            JsonObject body = parseObject(responseBody);
            throw new MatrixHttpException(operation, statusCode,
                    string(body, "errcode"), string(body, "error"),
                    retryAfterMillis(response, body));
        }
        try {
            JsonElement parsed = JsonParser.parseString(responseBody);
            if (parsed.isJsonObject()) return parsed.getAsJsonObject();
        } catch (RuntimeException ignored) {
            // A successful HTTP status cannot turn invalid JSON into an empty roster.
        }
        throw new IllegalStateException(operation + " returned an invalid JSON object");
    }

    private static long retryAfterMillis(HttpResponse<?> response, JsonObject body) {
        String header = response.headers().firstValue("Retry-After").orElse("").strip();
        if (header.matches("[0-9]+")) {
            try {
                return Math.multiplyExact(Long.parseLong(header), 1_000L);
            } catch (ArithmeticException | NumberFormatException ignored) {
                return Long.MAX_VALUE;
            }
        }
        if (!header.isEmpty()) {
            try {
                Instant retryAt = ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant();
                return Math.max(0, Duration.between(Instant.now(), retryAt).toMillis());
            } catch (DateTimeParseException | ArithmeticException ignored) {
                // An invalid header cannot supply a delay; inspect the protocol error body instead.
            }
        }
        return longValue(body, "retry_after_ms");
    }

    private static JsonObject parseObject(String value) {
        if (value == null || value.isBlank()) {
            return new JsonObject();
        }
        try {
            JsonElement parsed = JsonParser.parseString(value);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException ignored) {
            return new JsonObject();
        }
    }

    private static JsonObject relation(
            String repliedEventId,
            Optional<String> threadRootEventId
    ) {
        JsonObject relation = new JsonObject();
        JsonObject reply = new JsonObject();
        reply.addProperty("event_id", requireText(
                repliedEventId, "Matrix replied event ID"));
        relation.add("m.in_reply_to", reply);
        Optional<String> thread = threadRootEventId == null
                ? Optional.empty() : threadRootEventId;
        thread.filter(value -> !value.isBlank()).ifPresent(root -> {
            relation.addProperty("rel_type", "m.thread");
            relation.addProperty("event_id", root);
            relation.addProperty("is_falling_back", false);
        });
        return relation;
    }

    private static String createSyncFilter(Set<String> observedRoomIds) {
        JsonObject filter = new JsonObject();
        filter.add("presence", types());
        // Secret storage, cross-signing and backup account data must reach the crypto SDK.
        filter.add("account_data", new JsonObject());

        JsonObject room = new JsonObject();
        room.add("account_data", types());
        room.add("ephemeral", types());
        JsonArray rooms = new JsonArray();
        observedRoomIds.forEach(rooms::add);
        room.add("rooms", rooms);
        room.addProperty("include_leave", true);
        JsonObject state = new JsonObject();
        // Every membership delta is required, including state changes in limited timeline gaps.
        state.addProperty("lazy_load_members", false);
        room.add("state", state);

        JsonObject timeline = new JsonObject();
        JsonArray timelineTypes = new JsonArray();
        timelineTypes.add("m.room.message");
        timelineTypes.add("m.room.encrypted");
        timelineTypes.add("m.room.redaction");
        timelineTypes.add("m.sticker");
        timelineTypes.add("m.room.member");
        timelineTypes.add("m.room.encryption");
        timelineTypes.add("m.room.create");
        timeline.add("types", timelineTypes);
        timeline.addProperty("limit", 100);
        room.add("timeline", timeline);
        filter.add("room", room);
        return new Gson().toJson(filter);
    }

    private static JsonObject types() {
        JsonObject filter = new JsonObject();
        filter.add("types", new JsonArray());
        return filter;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String requireText(String value, String field) {
        String checked = Objects.requireNonNull(value, field).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return checked;
    }

    private static String fileName(String alternativeText, String extension) {
        String value = requireText(alternativeText, "Matrix image alternative text");
        StringBuilder name = new StringBuilder(Math.min(value.length(), 64));
        for (int index = 0; index < value.length() && name.length() < 64; index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character)
                    || character == '/' || character == '\\' || character == ':') {
                name.append('_');
            } else {
                name.append(character);
            }
        }
        return name.toString().strip() + '.' + extension;
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            return "";
        }
        try {
            return value.getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static long longValue(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()) {
            return 0;
        }
        try {
            return Math.max(0, value.getAsLong());
        } catch (RuntimeException ignored) {
            return 0;
        }
    }
}
