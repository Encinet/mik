package org.encinet.mik.feature.community.social.platform.qq.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.ConnectException;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;
import javax.net.ssl.SSLException;

/** One error contract for token, OpenAPI and Gateway requests; raw response bodies never escape. */
public final class QqApiErrors {
    private static final Gson GSON = new Gson();
    private static final Pattern VERIFICATION_CODE = Pattern.compile("(?<!\\d)\\d{6}(?!\\d)");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]+");

    private QqApiErrors() { }

    public static boolean hasError(JsonObject body) {
        return longValue(body, "code", 0) != 0
                || longValue(body, "err_code", 0) != 0;
    }

    public static QqHttpException responseError(String operation, HttpResponse<String> response,
                                               JsonObject body, String appSecret, String accessToken,
                                               JsonElement requestBody) {
        List<String> sensitive = new ArrayList<>();
        sensitive.add(appSecret);
        sensitive.add(accessToken);
        collectStrings(requestBody, sensitive);
        String message = safeText(string(body, "message"), sensitive, 300);
        if (message.isBlank()) message = "QQ API rejected the request or returned an incomplete response";
        String trace = string(body, "trace_id");
        if (trace.isBlank()) trace = response.headers().firstValue("X-Tps-trace-ID").orElse("");
        trace = redact(trace, sensitive);
        // A trace is an opaque identifier, not free-form remote text.
        if (!trace.matches("[A-Za-z0-9_.-]{1,100}")) trace = "";
        return new QqHttpException(operation, response.statusCode(),
                longValue(body, "code", 0),
                longValue(body, "err_code", 0),
                message, trace, retryAfterMillis(response));
    }

    public static Throwable unwrap(Throwable error) {
        Throwable result = error;
        while ((result instanceof CompletionException || result instanceof ExecutionException)
                && result.getCause() != null) result = result.getCause();
        return result;
    }

    public static String describe(Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof QqHttpException) return cause.getMessage();
        if (cause instanceof HttpTimeoutException) return "HTTP request timed out";
        if (cause instanceof UnknownHostException) return "DNS lookup failed";
        if (cause instanceof ConnectException) return "Could not connect to QQ API";
        if (cause instanceof SSLException) return "TLS connection failed";
        // Unknown exceptions can echo application answers, URLs or credentials in their message.
        return cause.getClass().getSimpleName();
    }

    public static String safeMessage(String message, String... sensitive) {
        return safeText(message, List.of(sensitive), 300);
    }

    public static JsonObject parseObject(String value) {
        try {
            JsonElement element = JsonParser.parseString(value == null || value.isBlank() ? "{}" : value);
            return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException ignored) {
            return new JsonObject();
        }
    }

    static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return "";
        }
        try {
            return value.getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    static long longValue(JsonObject object, String name, long fallback) {
        JsonElement value = object.get(name);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return value.getAsLong();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static long retryAfterMillis(HttpResponse<?> response) {
        String value = response.headers().firstValue("Retry-After").orElse("").strip();
        long delay = 0;
        try {
            delay = Math.multiplyExact(Long.parseLong(value), 1_000L);
        } catch (NumberFormatException | ArithmeticException invalidSeconds) {
            try {
                delay = Duration.between(Instant.now(), ZonedDateTime.parse(
                        value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toMillis();
            } catch (DateTimeException | ArithmeticException invalidDate) {
                // An absent or malformed header leaves timing to the caller's backoff.
            }
        }
        return Math.clamp(delay, 0, 86_400_000);
    }

    private static void collectStrings(JsonElement value, List<String> sensitive) {
        if (value == null || value.isJsonNull()) return;
        if (value.isJsonObject()) value.getAsJsonObject().asMap().values().forEach(item -> collectStrings(item, sensitive));
        else if (value.isJsonArray()) value.getAsJsonArray().forEach(item -> collectStrings(item, sensitive));
        else if (value.getAsJsonPrimitive().isString()) sensitive.add(value.getAsString());
    }

    private static String redact(String value, List<String> sensitive) {
        String result = value;
        for (String secret : sensitive.stream().filter(valuePart -> valuePart != null && !valuePart.isBlank())
                .distinct().sorted(Comparator.comparingInt(String::length).reversed()).toList()) {
            if (secret.length() > value.length()) continue;
            String escaped = GSON.toJson(secret);
            result = result.replace(secret, "[redacted]")
                    .replace(escaped.substring(1, escaped.length() - 1), "[redacted]")
                    .replace(URLEncoder.encode(secret, StandardCharsets.UTF_8), "[redacted]");
        }
        return result;
    }

    private static String safeText(String value, List<String> sensitive, int limit) {
        String text = redact(value, sensitive);
        text = VERIFICATION_CODE.matcher(text).replaceAll("[redacted]");
        text = CONTROL.matcher(text).replaceAll(" ").strip();
        return text.length() <= limit ? text : text.substring(0, limit) + "…";
    }
}
