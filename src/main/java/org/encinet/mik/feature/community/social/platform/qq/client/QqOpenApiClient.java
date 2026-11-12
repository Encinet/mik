package org.encinet.mik.feature.community.social.platform.qq.client;

import org.encinet.mik.feature.community.social.api.SocialDeliveryResult;
import org.encinet.mik.feature.community.social.api.SocialFutures;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.encinet.mik.feature.community.social.platform.qq.QqPlatformConfig;
import org.encinet.mik.feature.community.social.platform.qq.join.QqGroupJoinPage;

import java.net.URLEncoder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

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

    /** Sends one proactive Markdown message to a configured QQ group. */
    public CompletableFuture<Void> sendMarkdown(String groupOpenId, String content) {
        if (groupOpenId == null || groupOpenId.isBlank()) {
            throw new IllegalArgumentException("QQ group OpenID must not be blank");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("QQ Markdown content must not be blank");
        }
        return sendMarkdown(groupOpenId, content, null, 0, false);
    }

    /** Reviews the exact application identified by an authenticated event or pending-list response. */
    public CompletableFuture<Void> reviewGroupJoinRequest(
            String groupOpenId,
            String memberOpenId,
            String joinRequestId,
            boolean approve
    ) {
        if (groupOpenId == null || groupOpenId.isBlank()
                || memberOpenId == null || memberOpenId.isBlank()
                || joinRequestId == null || joinRequestId.isBlank()) {
            throw new IllegalArgumentException("QQ join approval requires group, member and request IDs");
        }
        JsonObject body = new JsonObject();
        body.addProperty("op", approve ? "approve" : "decline");
        body.addProperty("join_request_id", joinRequestId);
        return reviewGroupJoinRequest(groupOpenId, memberOpenId, body, false);
    }

    private CompletableFuture<Void> reviewGroupJoinRequest(
            String groupOpenId,
            String memberOpenId,
            JsonObject body,
            boolean retriedAuthentication
    ) {
        return tokenProvider.accessToken()
                .thenCompose(token -> postAuthorized(groupOpenId,
                        "/approval_join_request/" + encodePathSegment(memberOpenId),
                        body, token, "Review QQ group join request"))
                .exceptionallyCompose(error -> {
                    Throwable cause = QqApiErrors.unwrap(error);
                    if (!retriedAuthentication && cause instanceof QqHttpException httpError
                            && httpError.statusCode() == 401) {
                        tokenProvider.invalidate();
                        return reviewGroupJoinRequest(groupOpenId, memberOpenId, body, true);
                    }
                    return CompletableFuture.failedFuture(cause);
                });
    }

    public CompletableFuture<QqGroupJoinPage> listGroupJoinRequests(String groupOpenId, String cursor) {
        if (groupOpenId == null || groupOpenId.isBlank() || cursor == null || cursor.length() > 1_024
                || cursor.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("QQ join list requires a group and valid cursor");
        }
        return listGroupJoinRequests(groupOpenId, cursor, false);
    }

    private CompletableFuture<QqGroupJoinPage> listGroupJoinRequests(
            String groupOpenId, String cursor, boolean retriedAuthentication) {
        String suffix = "/join_request_list?limit=50"
                + (cursor.isEmpty() ? "" : "&cursor=" + encodePathSegment(cursor));
        return tokenProvider.accessToken().thenCompose(token -> httpClient.sendAsync(
                requestBuilder(groupOpenId, suffix, token).GET().build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    JsonObject body = QqApiErrors.parseObject(response.body());
                    if (response.statusCode() < 200 || response.statusCode() >= 300 || QqApiErrors.hasError(body)) {
                        throw QqApiErrors.responseError("List QQ group join requests", response, body,
                                config.appSecret(), token, new JsonPrimitive(cursor));
                    }
                    try {
                        return QqGroupJoinPage.parse(response.body(), groupOpenId, Instant.now());
                    } catch (RuntimeException invalidPage) {
                        throw QqApiErrors.responseError("List QQ group join requests", response, body,
                                config.appSecret(), token, new JsonPrimitive(cursor));
                    }
                })).exceptionallyCompose(error -> {
                    Throwable cause = QqApiErrors.unwrap(error);
                    if (!retriedAuthentication && cause instanceof QqHttpException httpError
                            && httpError.statusCode() == 401) {
                        tokenProvider.invalidate();
                        return listGroupJoinRequests(groupOpenId, cursor, true);
                    }
                    return CompletableFuture.failedFuture(cause);
                });
    }

    /** Uploads a remote image without sending it, then attaches it to one passive reply. */
    public CompletableFuture<SocialDeliveryResult> replyRemoteImage(
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
        var submitting = new AtomicBoolean();
        return SocialDeliveryResult.observe(sendImage(groupOpenId, messageId, messageSequence,
                source, content, false, submitting), error -> submitting.get()
                ? QqApiErrors.deliveryStatus(error) : SocialDeliveryResult.Status.NOT_SENT);
    }

    /** Uploads Base64-encoded image bytes, then attaches them to one passive reply. */
    public CompletableFuture<SocialDeliveryResult> replyBase64Image(
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
        var submitting = new AtomicBoolean();
        return SocialDeliveryResult.observe(sendImage(groupOpenId, messageId, messageSequence,
                source, content, false, submitting), error -> submitting.get()
                ? QqApiErrors.deliveryStatus(error) : SocialDeliveryResult.Status.NOT_SENT);
    }

    private CompletableFuture<Void> sendMarkdown(String groupOpenId, String content,
                                                 String messageId, int messageSequence,
                                                 boolean retriedAuthentication) {
        JsonObject body = new JsonObject();
        body.addProperty("msg_type", 2);
        JsonObject markdown = new JsonObject();
        markdown.addProperty("content", content);
        body.add("markdown", markdown);
        if (messageId != null) {
            body.addProperty("msg_id", messageId);
            body.addProperty("msg_seq", messageSequence);
        }

        var request = SocialFutures.compose(tokenProvider.accessToken(),
                token -> sendAuthorizedMessage(groupOpenId, body, token));
        return SocialFutures.recover(request, error -> {
            Throwable cause = QqApiErrors.unwrap(error);
            if (!retriedAuthentication && cause instanceof QqHttpException httpError && httpError.statusCode() == 401) {
                tokenProvider.invalidate();
                return sendMarkdown(groupOpenId, content, messageId, messageSequence, true);
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
            boolean retriedAuthentication, AtomicBoolean submitting
    ) {
        var request = SocialFutures.compose(tokenProvider.accessToken(), token -> SocialFutures.compose(uploadImage(groupOpenId, imageSource, token), fileInfo -> {
                    submitting.set(true);
                    return sendAuthorizedMessage(groupOpenId, imageMessageBody(messageId, messageSequence, fileInfo, content), token);
                }));
        return SocialFutures.recover(request, error -> {
                    Throwable cause = QqApiErrors.unwrap(error);
                    if (!retriedAuthentication && cause instanceof QqHttpException httpError
                            && httpError.statusCode() == 401) {
                        tokenProvider.invalidate();
                        submitting.set(false);
                        return sendImage(groupOpenId, messageId, messageSequence,
                                imageSource, content, true, submitting);
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
        var receiving = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        var result = receiving.thenApply(response -> {
                    JsonObject responseBody = QqApiErrors.parseObject(response.body());
                    String fileInfo = QqApiErrors.string(responseBody, "file_info");
                    if (response.statusCode() < 200 || response.statusCode() >= 300
                            || QqApiErrors.hasError(responseBody) || fileInfo.isBlank()) {
                        throw QqApiErrors.responseError("Upload QQ group image", response, responseBody,
                                config.appSecret(), accessToken, body);
                    }
                    return fileInfo;
                });
        result.whenComplete((_, error) -> { if (error != null) receiving.cancel(true); });
        return result;
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
        return postAuthorized(groupOpenId, "/messages", body, accessToken,
                "Send QQ group message");
    }

    private CompletableFuture<Void> postAuthorized(
            String groupOpenId,
            String suffix,
            JsonObject body,
            String accessToken,
            String operation
    ) {
        HttpRequest request = requestBuilder(groupOpenId, suffix, accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        var receiving = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        CompletableFuture<Void> result = receiving.thenApply(response -> {
                    JsonObject responseBody = QqApiErrors.parseObject(response.body());
                    if (response.statusCode() < 200 || response.statusCode() >= 300
                            || QqApiErrors.hasError(responseBody)) {
                        throw QqApiErrors.responseError(operation, response, responseBody,
                                config.appSecret(), accessToken, body);
                    }
                    return null;
                });
        result.whenComplete((_, error) -> { if (error != null) receiving.cancel(true); });
        return result;
    }

    private HttpRequest.Builder requestBuilder(
            String groupOpenId,
            String suffix,
            String accessToken
    ) {
        String encodedGroupId = encodePathSegment(groupOpenId);
        return HttpRequest.newBuilder(
                        QqApiEndpoints.apiUri("/v2/groups/" + encodedGroupId + suffix))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "QQBot " + accessToken)
                .header("X-Union-Appid", config.appId())
                .header("Content-Type", "application/json; charset=utf-8");
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static void validateReply(String messageId, int messageSequence) {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("A passive QQ reply requires msg_id");
        }
        if (messageSequence < 1 || messageSequence > 5) {
            throw new IllegalArgumentException("QQ msg_seq must be between 1 and 5");
        }
    }

}
