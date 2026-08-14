package org.encinet.mik.module.social.platform.matrix.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.encinet.mik.module.social.platform.matrix.MatrixPlatformConfig;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;

/** Minimal authenticated Matrix Client-Server API client used by the adapter. */
public final class MatrixClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient httpClient;
    private final MatrixPlatformConfig config;
    private final Gson gson = new Gson();
    private final String transactionPrefix = "mik-" + UUID.randomUUID() + '-';
    private final AtomicLong transactionSequence = new AtomicLong();
    private final String syncFilter;

    public MatrixClient(HttpClient httpClient, MatrixPlatformConfig config) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.config = Objects.requireNonNull(config, "config");
        syncFilter = createSyncFilter(config.allowedRoomIds());
    }

    public String whoAmI() throws IOException, InterruptedException {
        HttpRequest request = authorizedRequest(
                config.apiUri("/_matrix/client/v3/account/whoami"))
                .timeout(REQUEST_TIMEOUT).GET().build();
        JsonObject body = sendJson(request, "Identify Matrix access token");
        String userId = string(body, "user_id");
        if (userId.isBlank()) {
            throw new IllegalStateException("Matrix /whoami omitted user_id");
        }
        return userId;
    }

    public JsonObject sync(String since, long timeoutMillis)
            throws IOException, InterruptedException {
        StringBuilder query = new StringBuilder("?timeout=")
                .append(Math.max(0, timeoutMillis))
                .append("&set_presence=offline&filter=")
                .append(encode(syncFilter));
        if (since != null && !since.isBlank()) {
            query.append("&since=").append(encode(since));
        }
        HttpRequest request = authorizedRequest(config.apiUri(
                        "/_matrix/client/v3/sync" + query))
                .timeout(Duration.ofMillis(Math.max(0, timeoutMillis) + 15_000L))
                .GET().build();
        return sendJson(request, "Synchronize Matrix events");
    }

    /** Loads the authoritative joined-member set for one room. */
    public Map<String, String> joinedMembers(String roomId)
            throws IOException, InterruptedException {
        HttpRequest request = authorizedRequest(config.apiUri(
                        "/_matrix/client/v3/rooms/" + encode(
                                requireText(roomId, "Matrix room ID"))
                                + "/joined_members"))
                .timeout(REQUEST_TIMEOUT).GET().build();
        JsonObject body = sendJson(request, "Load joined Matrix members");
        JsonObject joined = object(body, "joined");
        if (joined == null) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        joined.entrySet().forEach(entry -> {
            if (!entry.getValue().isJsonObject()) {
                return;
            }
            JsonObject member = entry.getValue().getAsJsonObject();
            String displayName = string(member, "display_name");
            if (displayName.isBlank()) {
                displayName = string(member, "displayname");
            }
            result.put(entry.getKey(), displayName);
        });
        return Map.copyOf(result);
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
        return upload(mediaType, fileName, data).thenCompose(contentUri -> {
            JsonObject body = new JsonObject();
            body.addProperty("msgtype", "m.image");
            body.addProperty("url", contentUri);
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
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", mediaType)
                .POST(HttpRequest.BodyPublishers.ofByteArray(data))
                .build();
        return sendJsonAsync(request, "Upload Matrix image").thenApply(body -> {
            String contentUri = string(body, "content_uri");
            if (!contentUri.startsWith("mxc://")) {
                throw new IllegalStateException(
                        "Matrix media upload omitted a valid content_uri");
            }
            return contentUri;
        });
    }

    private CompletableFuture<Void> sendRoomMessage(
            String roomId,
            JsonObject body,
            String operation
    ) {
        requireText(roomId, "Matrix room ID");
        String transactionId = transactionPrefix
                + transactionSequence.incrementAndGet();
        URI endpoint = config.apiUri("/_matrix/client/v3/rooms/"
                + encode(roomId) + "/send/m.room.message/" + encode(transactionId));
        HttpRequest request = authorizedRequest(endpoint)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json; charset=utf-8")
                .PUT(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();
        return sendJsonAsync(request, operation).thenApply(response -> {
            if (string(response, "event_id").isBlank()) {
                throw new IllegalStateException(operation + " response omitted event_id");
            }
            return null;
        });
    }

    private HttpRequest.Builder authorizedRequest(URI endpoint) {
        return HttpRequest.newBuilder(endpoint)
                .header("Authorization", "Bearer " + config.accessToken())
                .header("Accept", "application/json")
                .header("User-Agent", "MIK Matrix bot");
    }

    private JsonObject sendJson(HttpRequest request, String operation)
            throws IOException, InterruptedException {
        HttpResponse<InputStream> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofInputStream());
        String body = readBody(response);
        return parseResponse(operation, response.statusCode(), body);
    }

    private CompletableFuture<JsonObject> sendJsonAsync(
            HttpRequest request,
            String operation
    ) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenApply(response -> {
                    try {
                        return parseResponse(operation, response.statusCode(),
                                readBody(response));
                    } catch (IOException error) {
                        throw new CompletionException(error);
                    }
                });
    }

    private String readBody(HttpResponse<InputStream> response) throws IOException {
        long declaredLength = response.headers().firstValueAsLong("Content-Length")
                .orElse(-1L);
        if (declaredLength > config.syncMaxResponseBytes()) {
            response.body().close();
            throw new IOException("Matrix response exceeded the configured size limit");
        }
        try (InputStream input = response.body()) {
            byte[] bytes = input.readNBytes(config.syncMaxResponseBytes() + 1);
            if (bytes.length > config.syncMaxResponseBytes()) {
                throw new IOException("Matrix response exceeded the configured size limit");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static JsonObject parseResponse(
            String operation,
            int statusCode,
            String responseBody
    ) {
        JsonObject body = parseObject(responseBody);
        if (statusCode < 200 || statusCode >= 300) {
            throw new MatrixHttpException(operation, statusCode,
                    string(body, "errcode"), string(body, "error"),
                    longValue(body, "retry_after_ms"));
        }
        return body;
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

    private static String createSyncFilter(Set<String> allowedRoomIds) {
        JsonObject filter = new JsonObject();
        filter.add("presence", types());
        filter.add("account_data", types());

        JsonObject room = new JsonObject();
        room.add("account_data", types());
        room.add("ephemeral", types());
        if (!allowedRoomIds.isEmpty()) {
            JsonArray rooms = new JsonArray();
            allowedRoomIds.forEach(rooms::add);
            room.add("rooms", rooms);
        }
        JsonObject state = new JsonObject();
        JsonArray stateTypes = new JsonArray();
        stateTypes.add("m.room.member");
        state.add("types", stateTypes);
        state.addProperty("lazy_load_members", true);
        room.add("state", state);

        JsonObject timeline = new JsonObject();
        JsonArray timelineTypes = new JsonArray();
        timelineTypes.add("m.room.message");
        timelineTypes.add("m.room.member");
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

    private static JsonObject object(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonObject()
                ? value.getAsJsonObject() : null;
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
