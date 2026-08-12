package org.encinet.mik.module.social.platform.qq.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.encinet.mik.module.social.platform.qq.QqPlatformConfig;

import java.net.URLEncoder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class QqOpenApiClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient httpClient;
    private final QqPlatformConfig config;
    private final QqAccessTokenProvider tokenProvider;
    private final Gson gson = new Gson();

    public QqOpenApiClient(HttpClient httpClient, QqPlatformConfig config) {
        this(httpClient, config, new QqAccessTokenProvider(httpClient, config));
    }

    public QqOpenApiClient(HttpClient httpClient, QqPlatformConfig config,
                           QqAccessTokenProvider tokenProvider) {
        this.httpClient = httpClient;
        this.config = config;
        this.tokenProvider = tokenProvider;
    }

    public CompletableFuture<Void> replyMarkdown(String groupOpenId, String messageId,
                                                  int messageSequence, String content) {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("A passive QQ reply requires msg_id");
        }
        if (messageSequence < 1 || messageSequence > 5) {
            throw new IllegalArgumentException("QQ msg_seq must be between 1 and 5");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("QQ Markdown content must not be blank");
        }
        return sendMarkdown(groupOpenId, content, messageId, messageSequence, false);
    }

    /** Uploads a remote image without sending it, then attaches it to one passive reply. */
    public CompletableFuture<Void> replyRemoteImage(
            String groupOpenId,
            String messageId,
            int messageSequence,
            URI imageUrl,
            String content
    ) {
        validateReply(messageId, messageSequence);
        if (imageUrl == null || !imageUrl.isAbsolute() || imageUrl.getHost() == null
                || (!"https".equalsIgnoreCase(imageUrl.getScheme())
                && !"http".equalsIgnoreCase(imageUrl.getScheme()))
                || imageUrl.getUserInfo() != null || imageUrl.getRawFragment() != null) {
            throw new IllegalArgumentException("QQ image URL must be an absolute HTTP(S) URL");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("QQ image reply content must not be blank");
        }
        JsonObject source = new JsonObject();
        source.addProperty("url", imageUrl.normalize().toASCIIString());
        return sendImage(groupOpenId, messageId, messageSequence,
                source, content, false);
    }

    /** Uploads Base64-encoded image bytes, then attaches them to one passive reply. */
    public CompletableFuture<Void> replyBase64Image(
            String groupOpenId,
            String messageId,
            int messageSequence,
            String base64Data,
            String content
    ) {
        validateReply(messageId, messageSequence);
        if (base64Data == null || base64Data.isBlank() || base64Data.length() > 14_000_000) {
            throw new IllegalArgumentException("QQ image Base64 data has an invalid length");
        }
        try {
            Base64.getDecoder().decode(base64Data);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("QQ image data is not valid Base64", error);
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("QQ image reply content must not be blank");
        }
        JsonObject source = new JsonObject();
        source.addProperty("file_data", base64Data);
        return sendImage(groupOpenId, messageId, messageSequence,
                source, content, false);
    }

    private CompletableFuture<Void> sendMarkdown(String groupOpenId, String content,
                                                 String messageId, int messageSequence,
                                                 boolean retriedAuthentication) {
        JsonObject body = new JsonObject();
        body.addProperty("msg_type", 2);
        JsonObject markdown = new JsonObject();
        markdown.addProperty("content", content);
        body.add("markdown", markdown);
        body.addProperty("msg_id", messageId);
        body.addProperty("msg_seq", messageSequence);

        return tokenProvider.accessToken()
                .thenCompose(token -> sendAuthorizedMessage(groupOpenId, body, token))
                .exceptionallyCompose(error -> {
                    Throwable cause = unwrap(error);
                    if (!retriedAuthentication && cause instanceof QqHttpException httpError
                            && httpError.statusCode() == 401) {
                        tokenProvider.invalidate();
                        return sendMarkdown(groupOpenId, content, messageId,
                                messageSequence, true);
                    }
                    return CompletableFuture.failedFuture(cause);
                });
    }

    private CompletableFuture<Void> sendImage(
            String groupOpenId,
            String messageId,
            int messageSequence,
            JsonObject imageSource,
            String content,
            boolean retriedAuthentication
    ) {
        return tokenProvider.accessToken()
                .thenCompose(token -> uploadImage(groupOpenId, imageSource, token)
                        .thenCompose(fileInfo -> sendAuthorizedMessage(groupOpenId,
                                imageMessageBody(messageId, messageSequence,
                                        fileInfo, content), token)))
                .exceptionallyCompose(error -> {
                    Throwable cause = unwrap(error);
                    if (!retriedAuthentication && cause instanceof QqHttpException httpError
                            && httpError.statusCode() == 401) {
                        tokenProvider.invalidate();
                        return sendImage(groupOpenId, messageId, messageSequence,
                                imageSource, content, true);
                    }
                    return CompletableFuture.failedFuture(cause);
                });
    }

    private CompletableFuture<String> uploadImage(
            String groupOpenId,
            JsonObject imageSource,
            String accessToken
    ) {
        JsonObject body = imageSource.deepCopy();
        body.addProperty("file_type", 1);
        body.addProperty("srv_send_msg", false);
        body.addProperty("group_openid", groupOpenId);

        HttpRequest request = requestBuilder(groupOpenId, "/files", accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    JsonObject responseBody = QqAccessTokenProvider.parseObject(response.body());
                    long errorCode = QqAccessTokenProvider.longValue(responseBody, "err_code",
                            QqAccessTokenProvider.longValue(responseBody, "code", 0));
                    String fileInfo = QqAccessTokenProvider.string(responseBody, "file_info");
                    if (response.statusCode() < 200 || response.statusCode() >= 300
                            || errorCode != 0 || fileInfo.isBlank()) {
                        throw QqAccessTokenProvider.responseError(
                                "Upload QQ group image", response.statusCode(), responseBody);
                    }
                    return fileInfo;
                });
    }

    private static JsonObject imageMessageBody(
            String messageId,
            int messageSequence,
            String fileInfo,
            String content
    ) {
        JsonObject body = new JsonObject();
        body.addProperty("msg_type", 7);
        body.addProperty("content", content);
        JsonObject media = new JsonObject();
        media.addProperty("file_info", fileInfo);
        body.add("media", media);
        body.addProperty("msg_id", messageId);
        body.addProperty("msg_seq", messageSequence);
        return body;
    }

    private CompletableFuture<Void> sendAuthorizedMessage(String groupOpenId, JsonObject body,
                                                          String accessToken) {
        HttpRequest request = requestBuilder(groupOpenId, "/messages", accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    JsonObject responseBody = QqAccessTokenProvider.parseObject(response.body());
                    long errorCode = QqAccessTokenProvider.longValue(responseBody, "err_code",
                            QqAccessTokenProvider.longValue(responseBody, "code", 0));
                    if (response.statusCode() < 200 || response.statusCode() >= 300
                            || errorCode != 0) {
                        throw QqAccessTokenProvider.responseError(
                                "Send QQ group message", response.statusCode(), responseBody);
                    }
                    return null;
                });
    }

    private HttpRequest.Builder requestBuilder(
            String groupOpenId,
            String suffix,
            String accessToken
    ) {
        String encodedGroupId = URLEncoder.encode(groupOpenId, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return HttpRequest.newBuilder(
                        config.apiUri("/v2/groups/" + encodedGroupId + suffix))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "QQBot " + accessToken)
                .header("X-Union-Appid", config.appId())
                .header("Content-Type", "application/json; charset=utf-8");
    }

    private static void validateReply(String messageId, int messageSequence) {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("A passive QQ reply requires msg_id");
        }
        if (messageSequence < 1 || messageSequence > 5) {
            throw new IllegalArgumentException("QQ msg_seq must be between 1 and 5");
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable result = error;
        while ((result instanceof CompletionException
                || result instanceof java.util.concurrent.ExecutionException)
                && result.getCause() != null) {
            result = result.getCause();
        }
        return result;
    }
}
