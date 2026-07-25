package org.encinet.mik.module.music.online;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.TrackTarget;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

final class LxCustomSourceRuntime implements AutoCloseable {

    private static final int MAX_SCRIPT_BYTES = 2 * 1024 * 1024;
    private static final int MAX_HTTP_OPTIONS_BYTES = 6 * 1024 * 1024;
    private static final int MAX_HTTP_REQUEST_BYTES = 4 * 1024 * 1024;
    private static final int MAX_HTTP_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_ACTION_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_URL_LENGTH = 4096;
    private static final int MAX_CONCURRENT_HTTP_REQUESTS = 16;
    private static final int MAX_PENDING_TIMERS = 128;
    private static final long MAX_TIMER_DELAY_MILLIS = TimeUnit.HOURS.toMillis(1);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final String id;
    private final String displayName;
    private final ScriptInfo scriptInfo;
    private final Path scriptPath;
    private final Duration requestTimeout;
    private final Consumer<String> warningLogger;
    private final ExecutorService executor;
    private final ScheduledExecutorService timerExecutor;
    private final HttpClient httpClient;
    private final CompletableFuture<SourceInfo> initialized = new CompletableFuture<>();
    private final Set<CompletableFuture<?>> httpRequests = ConcurrentHashMap.newKeySet();
    private final Set<HttpResponse<InputStream>> activeHttpResponses = ConcurrentHashMap.newKeySet();
    private final Set<CompletableFuture<?>> pendingRequests = ConcurrentHashMap.newKeySet();
    private final Map<Integer, ScriptTimer> timers = new ConcurrentHashMap<>();
    private final AtomicInteger nextTimerId = new AtomicInteger();
    private final Semaphore httpPermits = new Semaphore(MAX_CONCURRENT_HTTP_REQUESTS);
    private final Object contextLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Context context;
    private Value requestHandler;
    private Value parseJson;
    private Value stringifyJson;
    private SourceInfo declaredSourceInfo;
    private IOException initializationError;
    private boolean scriptEvaluated;

    LxCustomSourceRuntime(String id, Path scriptPath, Duration requestTimeout,
                          Consumer<String> warningLogger) throws IOException {
        this.id = requireId(id);
        this.scriptPath = scriptPath.toAbsolutePath().normalize();
        this.requestTimeout = requestTimeout;
        this.warningLogger = warningLogger;
        byte[] scriptBytes = readLimited(Files.newInputStream(this.scriptPath), MAX_SCRIPT_BYTES);
        String script = new String(scriptBytes, StandardCharsets.UTF_8);
        this.scriptInfo = parseScriptInfo(script, this.scriptPath.getFileName().toString());
        this.displayName = scriptInfo.name();
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "mik-lx-source-" + this.id);
            thread.setDaemon(true);
            return thread;
        });
        this.timerExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "mik-lx-timer-" + this.id);
            thread.setDaemon(true);
            return thread;
        });
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(requestTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        executor.execute(() -> initialize(script));
    }

    CompletableFuture<SourceInfo> initialized() {
        return initialized;
    }

    String displayName() {
        return displayName;
    }

    CompletableFuture<String> resolve(TrackTarget.Lx target, String quality) {
        CompletableFuture<String> future = newPendingRequest();
        if (future.isDone()) {
            return future;
        }
        try {
            executor.execute(() -> {
                if (closed.get() || context == null || requestHandler == null) {
                    future.completeExceptionally(new IOException("LX source is not initialized"));
                    return;
                }
                try {
                    JsonObject request = new JsonObject();
                    request.addProperty("action", "musicUrl");
                    request.addProperty("source", target.source());
                    JsonObject info = new JsonObject();
                    info.addProperty("type", quality);
                    info.add("musicInfo", JsonParser.parseString(target.musicInfoJson()));
                    request.add("info", info);
                    Value result = requestHandler.execute(parseJson.execute(request.toString()));
                    completeFromResult(result, future);
                } catch (Throwable throwable) {
                    future.completeExceptionally(scriptError(throwable));
                }
            });
        } catch (RuntimeException exception) {
            future.completeExceptionally(new IOException("LX source is closed", exception));
        }
        return future;
    }

    CompletableFuture<JsonElement> requestAction(String action, String source, JsonObject info) {
        CompletableFuture<JsonElement> future = newPendingRequest();
        if (future.isDone()) {
            return future;
        }
        try {
            executor.execute(() -> {
                if (closed.get() || context == null || requestHandler == null) {
                    future.completeExceptionally(new IOException("LX source is not initialized"));
                    return;
                }
                try {
                    JsonObject request = new JsonObject();
                    request.addProperty("action", action);
                    request.addProperty("source", source);
                    request.add("info", info == null ? new JsonObject() : info);
                    Value result = requestHandler.execute(parseJson.execute(request.toString()));
                    completeJsonFromResult(result, future);
                } catch (Throwable throwable) {
                    future.completeExceptionally(scriptError(throwable));
                }
            });
        } catch (RuntimeException exception) {
            future.completeExceptionally(new IOException("LX source is closed", exception));
        }
        return future;
    }

    private <T> CompletableFuture<T> newPendingRequest() {
        CompletableFuture<T> future = new CompletableFuture<>();
        pendingRequests.add(future);
        future.whenComplete((ignored, error) -> pendingRequests.remove(future));
        if (closed.get()) {
            future.completeExceptionally(new IOException("LX source is closed"));
        }
        return future;
    }

    private void initialize(String script) {
        Context newContext = null;
        try {
            newContext = Context.newBuilder("js")
                    .allowHostAccess(HostAccess.NONE)
                    .allowHostClassLookup(name -> false)
                    .allowIO(IOAccess.NONE)
                    .allowCreateThread(false)
                    .allowNativeAccess(false)
                    .option("js.ecmascript-version", "2022")
                    .option("engine.WarnInterpreterOnly", "false")
                    .build();
            synchronized (contextLock) {
                if (closed.get()) {
                    throw new IOException("LX source was closed during initialization");
                }
                context = newContext;
            }
            Value bindings = newContext.getBindings("js");
            bindings.putMember("__lxOn", (ProxyExecutable) this::on);
            bindings.putMember("__lxSend", (ProxyExecutable) this::send);
            bindings.putMember("__lxRequest", (ProxyExecutable) this::request);
            bindings.putMember("__lxBufferFrom", (ProxyExecutable) this::bufferFrom);
            bindings.putMember("__lxBufferToString", (ProxyExecutable) this::bufferToString);
            bindings.putMember("__lxMd5", (ProxyExecutable) args -> unchecked(() -> md5(args)));
            bindings.putMember("__lxRandomBytes", (ProxyExecutable) this::randomBytes);
            bindings.putMember("__lxAesEncrypt", (ProxyExecutable) args -> unchecked(() -> aesEncrypt(args)));
            bindings.putMember("__lxRsaEncrypt", (ProxyExecutable) args -> unchecked(() -> rsaEncrypt(args)));
            bindings.putMember("__lxDeflate", (ProxyExecutable) args -> compression(args, false));
            bindings.putMember("__lxInflate", (ProxyExecutable) args -> compression(args, true));
            bindings.putMember("__lxSetTimer", (ProxyExecutable) this::setTimer);
            bindings.putMember("__lxClearTimer", (ProxyExecutable) this::clearTimer);
            bindings.putMember("__lxScriptInfoJson", scriptInfo.toJson(script).toString());
            newContext.eval("js", BOOTSTRAP);
            parseJson = bindings.getMember("__lxParseJson");
            stringifyJson = bindings.getMember("__lxStringify");
            newContext.eval(Source.newBuilder("js", script, scriptPath.getFileName().toString()).buildLiteral());
            scriptEvaluated = true;
            completeInitializationIfReady();
        } catch (Throwable throwable) {
            initialized.completeExceptionally(scriptError(throwable));
            synchronized (contextLock) {
                if (context == newContext) {
                    context = null;
                }
            }
            if (newContext != null) {
                try {
                    newContext.close(true);
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    private Object on(Value... args) {
        if (args.length >= 2 && "request".equals(asString(args[0])) && args[1].canExecute()) {
            requestHandler = args[1];
            completeInitializationIfReady();
        }
        return null;
    }

    private Object send(Value... args) {
        if (args.length < 2) {
            return null;
        }
        String event = asString(args[0]);
        if ("inited".equals(event)) {
            try {
                if (declaredSourceInfo != null || initializationError != null) {
                    throw new IOException("LX source called the inited event more than once");
                }
                declaredSourceInfo = parseSourceInfo(args[1]);
                completeInitializationIfReady();
            } catch (IOException exception) {
                initializationError = exception;
                initialized.completeExceptionally(exception);
            }
        }
        return null;
    }

    private void completeInitializationIfReady() {
        if (initializationError != null) {
            initialized.completeExceptionally(initializationError);
        } else if (scriptEvaluated && declaredSourceInfo != null && requestHandler != null) {
            initialized.complete(declaredSourceInfo);
        }
    }

    private Object request(Value... args) {
        if (args.length < 3 || !args[2].canExecute()) {
            return null;
        }
        String url = asString(args[0]);
        String optionsJson = asString(args[1]);
        Value callback = args[2];
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicBoolean permitReleased = new AtomicBoolean();
        boolean permitAcquired = false;
        try {
            URI uri = validateHttpUri(url);
            if (optionsJson.getBytes(StandardCharsets.UTF_8).length > MAX_HTTP_OPTIONS_BYTES) {
                throw new IOException("LX source HTTP options are too large");
            }
            JsonObject options = JsonParser.parseString(optionsJson).getAsJsonObject();
            HttpRequest request = buildRequest(uri, options);
            if (!httpPermits.tryAcquire()) {
                throw new IOException("LX source exceeded " + MAX_CONCURRENT_HTTP_REQUESTS
                        + " concurrent HTTP requests");
            }
            permitAcquired = true;
            CompletableFuture<HttpResponse<InputStream>> pending = httpClient.sendAsync(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            httpRequests.add(pending);
            pending
                    .whenComplete((response, error) -> {
                        httpRequests.remove(pending);
                        if (response != null) {
                            activeHttpResponses.add(response);
                        }
                        if (cancelled.get() || closed.get()) {
                            closeBody(response);
                            removeActiveResponse(response);
                            releaseHttpPermit(permitReleased);
                            return;
                        }
                        if (error != null) {
                            invokeHttpCallback(callback, null, error);
                            removeActiveResponse(response);
                            releaseHttpPermit(permitReleased);
                            return;
                        }
                        try (InputStream body = response.body()) {
                            byte[] bytes = readLimited(body, MAX_HTTP_RESPONSE_BYTES);
                            invokeHttpCallback(callback, new ScriptHttpResponse(response, bytes), null);
                        } catch (Throwable throwable) {
                            invokeHttpCallback(callback, null, throwable);
                        } finally {
                            removeActiveResponse(response);
                            releaseHttpPermit(permitReleased);
                        }
                    });
            return (ProxyExecutable) ignored -> {
                if (cancelled.compareAndSet(false, true)) {
                    pending.cancel(true);
                }
                return null;
            };
        } catch (Throwable throwable) {
            if (permitAcquired) {
                releaseHttpPermit(permitReleased);
            }
            invokeHttpCallback(callback, null, throwable);
            return (ProxyExecutable) ignored -> null;
        }
    }

    private Object setTimer(Value... args) {
        if (closed.get()) {
            throw new IllegalStateException("LX source is closed");
        }
        if (args.length < 4 || !args[0].canExecute()) {
            throw new IllegalArgumentException("LX timer callback must be callable");
        }
        if (timers.size() >= MAX_PENDING_TIMERS) {
            throw new IllegalStateException("LX source exceeded " + MAX_PENDING_TIMERS
                    + " pending timers");
        }
        long delay = timerDelay(args[1]);
        Value[] callbackArgs = timerArguments(args[2]);
        boolean repeating = args[3].isBoolean() && args[3].asBoolean();
        if (repeating) {
            delay = Math.max(1, delay);
        }
        int id = nextTimerId.updateAndGet(current -> current == Integer.MAX_VALUE ? 1 : current + 1);
        ScriptTimer timer = new ScriptTimer(args[0], callbackArgs, delay, repeating);
        if (timers.putIfAbsent(id, timer) != null) {
            throw new IllegalStateException("LX timer ID collision");
        }
        try {
            scheduleTimer(id, timer);
        } catch (RuntimeException exception) {
            timers.remove(id, timer);
            timer.cancel();
            throw exception;
        }
        return id;
    }

    private Object clearTimer(Value... args) {
        if (args.length == 0 || !args[0].fitsInInt()) {
            return null;
        }
        ScriptTimer timer = timers.remove(args[0].asInt());
        if (timer != null) {
            timer.cancel();
        }
        return null;
    }

    private long timerDelay(Value value) {
        if (value == null || value.isNull() || !value.fitsInLong()) {
            return 0;
        }
        return Math.max(0, Math.min(MAX_TIMER_DELAY_MILLIS, value.asLong()));
    }

    private static Value[] timerArguments(Value value) {
        if (value == null || value.isNull() || !value.hasArrayElements()) {
            return new Value[0];
        }
        if (value.getArraySize() > 64) {
            throw new IllegalArgumentException("LX timer has too many callback arguments");
        }
        Value[] arguments = new Value[Math.toIntExact(value.getArraySize())];
        for (int index = 0; index < arguments.length; index++) {
            arguments[index] = value.getArrayElement(index);
        }
        return arguments;
    }

    private void scheduleTimer(int id, ScriptTimer timer) {
        ScheduledFuture<?> future = timerExecutor.schedule(() -> {
            if (closed.get() || timers.get(id) != timer) {
                return;
            }
            try {
                executor.execute(() -> runTimer(id, timer));
            } catch (RuntimeException exception) {
                timers.remove(id, timer);
            }
        }, timer.delayMillis(), TimeUnit.MILLISECONDS);
        timer.setFuture(future);
        if (closed.get() || timers.get(id) != timer) {
            future.cancel(false);
        }
    }

    private void runTimer(int id, ScriptTimer timer) {
        if (closed.get() || timers.get(id) != timer) {
            return;
        }
        if (!timer.repeating()) {
            timers.remove(id, timer);
        }
        try {
            timer.callback().execute((Object[]) timer.arguments());
        } catch (Throwable throwable) {
            warningLogger.accept("LX source " + LxCustomSourceRuntime.this.id
                    + " timer failed: " + message(throwable));
        }
        if (timer.repeating() && !closed.get() && timers.get(id) == timer) {
            try {
                scheduleTimer(id, timer);
            } catch (RuntimeException exception) {
                timers.remove(id, timer);
            }
        }
    }

    private void releaseHttpPermit(AtomicBoolean released) {
        if (released.compareAndSet(false, true)) {
            httpPermits.release();
        }
    }

    private void removeActiveResponse(HttpResponse<InputStream> response) {
        if (response != null) {
            activeHttpResponses.remove(response);
        }
    }

    private HttpRequest buildRequest(URI uri, JsonObject options) throws IOException {
        String method = jsonString(options, "method", "GET").toUpperCase(Locale.ROOT);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(requestDuration(options));
        JsonObject headers = jsonObject(options, "headers");
        boolean hasContentType = false;
        if (headers != null) {
            for (Map.Entry<String, JsonElement> entry : headers.entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    builder.header(entry.getKey(), entry.getValue().getAsString());
                    hasContentType |= entry.getKey().equalsIgnoreCase("Content-Type");
                }
            }
        }

        byte[] body = null;
        JsonElement bodyValue = options.get("body");
        JsonObject form = jsonObject(options, "form");
        JsonObject formData = jsonObject(options, "formData");
        if (bodyValue != null && !bodyValue.isJsonNull()) {
            body = requestBody(bodyValue);
        } else if (form != null) {
            body = encodeForm(form).getBytes(StandardCharsets.UTF_8);
            if (!hasContentType) {
                builder.header("Content-Type", "application/x-www-form-urlencoded");
            }
        } else if (formData != null) {
            String boundary = "----mik-lx-" + java.util.HexFormat.of().formatHex(randomBytes(12));
            body = encodeMultipart(formData, boundary);
            if (!hasContentType) {
                builder.header("Content-Type", "multipart/form-data; boundary=" + boundary);
            }
        }
        if (body != null && body.length > MAX_HTTP_REQUEST_BYTES) {
            throw new IOException("LX source HTTP request body is too large");
        }
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body);
        return builder.method(method, publisher).build();
    }

    private Duration requestDuration(JsonObject options) {
        JsonElement value = options.get("timeout");
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return requestTimeout;
        }
        try {
            long milliseconds = Math.max(1, value.getAsLong());
            return Duration.ofMillis(Math.min(milliseconds, requestTimeout.toMillis()));
        } catch (RuntimeException ignored) {
            return requestTimeout;
        }
    }

    private void invokeHttpCallback(Value callback, ScriptHttpResponse response, Throwable error) {
        try {
            executor.execute(() -> {
                if (closed.get()) {
                    return;
                }
                try {
                    if (error != null) {
                        callback.execute(message(error), null, null);
                        return;
                    }
                    String bodyText = new String(response.body(), StandardCharsets.UTF_8);
                    JsonElement body;
                    try {
                        body = JsonParser.parseString(bodyText);
                    } catch (RuntimeException ignored) {
                        body = new com.google.gson.JsonPrimitive(bodyText);
                    }
                    JsonObject responseJson = new JsonObject();
                    responseJson.addProperty("statusCode", response.response().statusCode());
                    responseJson.addProperty("statusMessage", "");
                    responseJson.addProperty("bytes", response.body().length);
                    JsonObject headers = new JsonObject();
                    response.response().headers().map().forEach((name, values) ->
                            headers.addProperty(name, String.join(", ", values)));
                    responseJson.add("headers", headers);
                    responseJson.add("body", body);
                    Value responseValue = parseJson.execute(responseJson.toString());
                    responseValue.putMember("raw", bytesProxy(response.body()));
                    callback.execute(null, responseValue, parseJson.execute(body.toString()));
                } catch (Throwable throwable) {
                    warningLogger.accept("LX source " + id + " HTTP callback failed: " + message(throwable));
                }
            });
        } catch (RuntimeException exception) {
            if (!closed.get()) {
                warningLogger.accept("LX source " + id + " could not schedule HTTP callback: "
                        + message(exception));
            }
        }
    }

    private void completeFromResult(Value result, CompletableFuture<String> future) {
        if (result == null || result.isNull()) {
            future.completeExceptionally(new IOException("LX source returned no playback URL"));
            return;
        }
        if (result.canInvokeMember("then")) {
            result.invokeMember("then",
                    (ProxyExecutable) args -> {
                        completeUrl(args.length == 0 ? null : args[0], future);
                        return null;
                    },
                    (ProxyExecutable) args -> {
                        future.completeExceptionally(new IOException(args.length == 0
                                ? "LX source request failed" : errorValue(args[0])));
                        return null;
                    });
            return;
        }
        completeUrl(result, future);
    }

    private void completeJsonFromResult(Value result, CompletableFuture<JsonElement> future) {
        if (result == null || result.isNull()) {
            future.completeExceptionally(new IOException("LX source returned no action result"));
            return;
        }
        if (result.canInvokeMember("then")) {
            result.invokeMember("then",
                    (ProxyExecutable) args -> {
                        completeJson(args.length == 0 ? null : args[0], future);
                        return null;
                    },
                    (ProxyExecutable) args -> {
                        future.completeExceptionally(new IOException(args.length == 0
                                ? "LX source request failed" : errorValue(args[0])));
                        return null;
                    });
            return;
        }
        completeJson(result, future);
    }

    private void completeJson(Value value, CompletableFuture<JsonElement> future) {
        try {
            if (value == null || value.isNull()) {
                throw new IOException("LX source returned no action result");
            }
            String json = stringifyJson.execute(value).asString();
            if (json.getBytes(StandardCharsets.UTF_8).length > MAX_ACTION_RESPONSE_BYTES) {
                throw new IOException("LX source action response exceeds "
                        + MAX_ACTION_RESPONSE_BYTES + " bytes");
            }
            future.complete(JsonParser.parseString(json));
        } catch (Throwable throwable) {
            future.completeExceptionally(scriptError(throwable));
        }
    }

    private void completeUrl(Value value, CompletableFuture<String> future) {
        try {
            String url = value == null || value.isNull() ? null : value.asString();
            validateHttpUri(url);
            if (url.length() > 2048) {
                throw new IOException("LX source returned an oversized playback URL");
            }
            future.complete(url);
        } catch (Throwable throwable) {
            future.completeExceptionally(scriptError(throwable));
        }
    }

    private SourceInfo parseSourceInfo(Value data) throws IOException {
        if (data == null || data.isNull() || !data.hasMembers()) {
            throw new IOException("LX source returned invalid initialization data");
        }
        Value status = data.getMember("status");
        if (status != null && status.isBoolean() && !status.asBoolean()) {
            Value message = data.getMember("message");
            throw new IOException(message == null ? "LX source initialization failed" : message.asString());
        }
        Value sources = data.getMember("sources");
        if (sources == null || !sources.hasMembers()) {
            throw new IOException("LX source declared no music sources");
        }
        Map<String, List<String>> supported = new LinkedHashMap<>();
        Map<String, List<String>> actionsBySource = new LinkedHashMap<>();
        for (String source : sources.getMemberKeys()) {
            if (!source.matches("[A-Za-z0-9_-]{1,32}")) {
                continue;
            }
            Value details = sources.getMember(source);
            if (details == null || !details.hasMembers()
                    || !"music".equals(memberString(details, "type"))) {
                continue;
            }
            List<String> actions = stringArray(details.getMember("actions"));
            if (!actions.contains("musicurl")) {
                continue;
            }
            actions = actions.stream()
                    .filter(action -> action.equals("musicurl") || action.equals("musicsearch")
                            || action.equals("lyric"))
                    .toList();
            List<String> qualities = stringArray(details.getMember("qualitys"));
            if (!qualities.isEmpty()) {
                String normalizedSource = source.toLowerCase(Locale.ROOT);
                supported.put(normalizedSource, qualities);
                actionsBySource.put(normalizedSource, actions);
            }
        }
        if (supported.isEmpty()) {
            throw new IOException("LX source does not provide a usable musicUrl action");
        }
        return new SourceInfo(id, displayName,
                Collections.unmodifiableMap(new LinkedHashMap<>(supported)),
                Collections.unmodifiableMap(new LinkedHashMap<>(actionsBySource)));
    }

    private Object bufferFrom(Value... args) {
        byte[] bytes = args.length == 0 ? new byte[0]
                : valueBytes(args[0], args.length > 1 ? asString(args[1]) : "utf8");
        return bytesProxy(bytes);
    }

    private Object bufferToString(Value... args) {
        byte[] bytes = args.length == 0 ? new byte[0] : valueBytes(args[0], "binary");
        String encoding = args.length > 1 ? asString(args[1]) : "utf8";
        return encodeBytes(bytes, encoding);
    }

    private Object md5(Value... args) throws Exception {
        byte[] digest = MessageDigest.getInstance("MD5")
                .digest((args.length == 0 ? "" : asString(args[0])).getBytes(StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(digest);
    }

    private Object randomBytes(Value... args) {
        int size = args.length == 0 ? 0 : Math.max(0, Math.min(65536, args[0].asInt()));
        byte[] bytes = new byte[size];
        SECURE_RANDOM.nextBytes(bytes);
        return bytesProxy(bytes);
    }

    private Object aesEncrypt(Value... args) throws Exception {
        if (args.length < 4) {
            throw new IllegalArgumentException("aesEncrypt requires buffer, mode, key and iv");
        }
        byte[] input = valueBytes(args[0], "binary");
        String transformation = aesTransformation(asString(args[1]));
        byte[] key = valueBytes(args[2], "binary");
        byte[] iv = valueBytes(args[3], "binary");
        Cipher cipher = Cipher.getInstance(transformation);
        if (transformation.contains("ECB")) {
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        } else {
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        }
        return bytesProxy(cipher.doFinal(input));
    }

    private Object rsaEncrypt(Value... args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("rsaEncrypt requires buffer and public key");
        }
        byte[] input = valueBytes(args[0], "binary");
        byte[] padded = new byte[128];
        int copyLength = Math.min(input.length, padded.length);
        System.arraycopy(input, input.length - copyLength, padded, padded.length - copyLength, copyLength);
        String pem = asString(args[1]).replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
        var key = KeyFactory.getInstance("RSA").generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(pem)));
        Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return bytesProxy(cipher.doFinal(padded));
    }

    private Object compression(Value[] args, boolean inflate) {
        if (args.length < 3 || !args[1].canExecute() || !args[2].canExecute()) {
            return null;
        }
        try {
            byte[] input = valueBytes(args[0], "binary");
            byte[] output = inflate ? inflate(input) : deflate(input);
            args[1].execute(bytesProxy(output));
        } catch (Throwable throwable) {
            args[2].execute(message(throwable));
        }
        return null;
    }

    private static byte[] deflate(byte[] input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DeflaterOutputStream stream = new DeflaterOutputStream(output)) {
            stream.write(input);
        }
        return output.toByteArray();
    }

    private static byte[] inflate(byte[] input) throws IOException {
        try (InflaterInputStream stream = new InflaterInputStream(new java.io.ByteArrayInputStream(input))) {
            return readLimited(stream, MAX_HTTP_RESPONSE_BYTES);
        }
    }

    private static String aesTransformation(String mode) {
        String normalized = mode.toLowerCase(Locale.ROOT);
        String blockMode = normalized.contains("ecb") ? "ECB" : normalized.contains("ctr") ? "CTR" : "CBC";
        String padding = blockMode.equals("CTR") ? "NoPadding" : "PKCS5Padding";
        return "AES/" + blockMode + "/" + padding;
    }

    private static ProxyArray bytesProxy(byte[] bytes) {
        return new ProxyArray() {
            @Override
            public Object get(long index) {
                if (index < 0 || index >= bytes.length) {
                    throw new ArrayIndexOutOfBoundsException(Math.toIntExact(index));
                }
                return bytes[(int) index] & 0xff;
            }

            @Override
            public void set(long index, Value value) {
                throw new UnsupportedOperationException("LX byte buffers are immutable");
            }

            @Override
            public long getSize() {
                return bytes.length;
            }
        };
    }

    private static byte[] valueBytes(Value value, String encoding) {
        if (value == null || value.isNull()) {
            return new byte[0];
        }
        if (value.isString()) {
            return decodeString(value.asString(), encoding);
        }
        if (!value.hasArrayElements()) {
            return new byte[0];
        }
        if (value.getArraySize() > 4 * 1024 * 1024L) {
            throw new IllegalArgumentException("LX byte array exceeds 4194304 bytes");
        }
        int length = Math.toIntExact(value.getArraySize());
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) value.getArrayElement(i).asInt();
        }
        return bytes;
    }

    private static byte[] decodeString(String value, String encoding) {
        return switch (encoding == null ? "utf8" : encoding.toLowerCase(Locale.ROOT)) {
            case "base64" -> Base64.getDecoder().decode(value);
            case "hex" -> java.util.HexFormat.of().parseHex(value);
            case "binary", "latin1" -> value.getBytes(StandardCharsets.ISO_8859_1);
            default -> value.getBytes(StandardCharsets.UTF_8);
        };
    }

    private static String encodeBytes(byte[] bytes, String encoding) {
        return switch (encoding == null ? "utf8" : encoding.toLowerCase(Locale.ROOT)) {
            case "base64" -> Base64.getEncoder().encodeToString(bytes);
            case "hex" -> java.util.HexFormat.of().formatHex(bytes);
            case "binary", "latin1" -> new String(bytes, StandardCharsets.ISO_8859_1);
            default -> new String(bytes, StandardCharsets.UTF_8);
        };
    }

    private static URI validateHttpUri(String url) throws IOException {
        if (url == null || url.isBlank() || url.length() > MAX_URL_LENGTH) {
            throw new IOException("Invalid HTTP URL");
        }
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || scheme == null
                    || !scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
                throw new IOException("Only HTTP(S) URLs are allowed");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid HTTP URL", exception);
        }
    }

    private static String encodeForm(JsonObject form) {
        List<String> values = new ArrayList<>();
        form.entrySet().forEach(entry -> values.add(
                URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(formValue(entry.getValue()), StandardCharsets.UTF_8)));
        return String.join("&", values);
    }

    private static byte[] encodeMultipart(JsonObject form, String boundary) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        form.entrySet().forEach(entry -> {
            String header = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"" + quoteHeader(entry.getKey())
                    + "\"\r\n\r\n";
            output.writeBytes(header.getBytes(StandardCharsets.UTF_8));
            output.writeBytes(formValue(entry.getValue()).getBytes(StandardCharsets.UTF_8));
            output.writeBytes("\r\n".getBytes(StandardCharsets.US_ASCII));
        });
        output.writeBytes(("--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        return output.toByteArray();
    }

    private static String formValue(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return "";
        }
        return value.isJsonPrimitive() ? value.getAsString() : value.toString();
    }

    private static String quoteHeader(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "").replace("\n", "");
    }

    private static byte[] requestBody(JsonElement value) throws IOException {
        if (value.isJsonObject() && value.getAsJsonObject().has("__mikBytesBase64")) {
            JsonElement encoded = value.getAsJsonObject().get("__mikBytesBase64");
            if (!encoded.isJsonPrimitive() || !encoded.getAsJsonPrimitive().isString()) {
                throw new IOException("LX source HTTP byte body is invalid or too large");
            }
            try {
                byte[] bytes = Base64.getDecoder().decode(encoded.getAsString());
                if (bytes.length > MAX_HTTP_REQUEST_BYTES) {
                    throw new IOException("LX source HTTP byte body is too large");
                }
                return bytes;
            } catch (IllegalArgumentException exception) {
                throw new IOException("LX source HTTP byte body is invalid", exception);
            }
        }
        String text = value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : value.toString();
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static void closeBody(HttpResponse<InputStream> response) {
        if (response == null || response.body() == null) {
            return;
        }
        try {
            response.body().close();
        } catch (IOException ignored) {
        }
    }

    private static ScriptInfo parseScriptInfo(String script, String fallback) {
        String header = script.substring(0, Math.min(script.length(), 8192));
        return new ScriptInfo(
                headerValue(header, "name", fallback, 128),
                headerValue(header, "description", "", 512),
                headerValue(header, "version", "", 64),
                headerValue(header, "author", "", 128),
                headerValue(header, "homepage", "", 1024));
    }

    private static String headerValue(String header, String key, String fallback, int maximum) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?m)^\\s*\\*\\s*@" + java.util.regex.Pattern.quote(key) + "\\s+(.+?)\\s*$")
                .matcher(header);
        String value = matcher.find() && !matcher.group(1).isBlank()
                ? matcher.group(1).strip() : fallback;
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private static String requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("LX source id is blank");
        }
        return id;
    }

    private static String memberString(Value value, String name) {
        Value member = value.getMember(name);
        return member == null || member.isNull() ? null : member.asString();
    }

    private static List<String> stringArray(Value value) throws IOException {
        if (value == null || !value.hasArrayElements()) {
            return List.of();
        }
        if (value.getArraySize() > 128) {
            throw new IOException("LX source declared too many action or quality entries");
        }
        Set<String> result = new LinkedHashSet<>();
        for (long i = 0; i < value.getArraySize(); i++) {
            Value item = value.getArrayElement(i);
            if (item != null && item.isString()) {
                String text = item.asString().strip().toLowerCase(Locale.ROOT);
                if (!text.isEmpty() && text.length() <= 32) {
                    result.add(text);
                }
            }
        }
        return List.copyOf(result);
    }

    private static JsonObject jsonObject(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static String jsonString(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static String asString(Value value) {
        return value == null || value.isNull() ? "" : value.asString();
    }

    private static String errorValue(Value value) {
        if (value == null || value.isNull()) {
            return "LX source request failed";
        }
        if (value.hasMember("message")) {
            return value.getMember("message").asString();
        }
        return value.toString();
    }

    private static IOException scriptError(Throwable throwable) {
        return throwable instanceof IOException io ? io : new IOException(message(throwable), throwable);
    }

    private static String message(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private static Object unchecked(ThrowingSupplier supplier) {
        try {
            return supplier.get();
        } catch (Exception exception) {
            throw new RuntimeException(message(exception), exception);
        }
    }

    private static byte[] readLimited(InputStream input, int limit) throws IOException {
        try (input) {
            byte[] bytes = input.readNBytes(limit + 1);
            if (bytes.length > limit) {
                throw new IOException("Data exceeds " + limit + " bytes");
            }
            return bytes;
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        Object get() throws Exception;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        IOException closeError = new IOException("LX source was closed");
        initialized.completeExceptionally(closeError);
        pendingRequests.forEach(request -> request.completeExceptionally(closeError));
        pendingRequests.clear();
        Context current;
        synchronized (contextLock) {
            current = context;
            context = null;
        }
        if (current != null) {
            try {
                current.close(true);
            } catch (RuntimeException ignored) {
            }
        }
        httpRequests.forEach(request -> request.cancel(true));
        httpRequests.clear();
        activeHttpResponses.forEach(LxCustomSourceRuntime::closeBody);
        activeHttpResponses.clear();
        httpClient.shutdownNow();
        timers.values().forEach(ScriptTimer::cancel);
        timers.clear();
        timerExecutor.shutdownNow();
        executor.shutdownNow();
    }

    record SourceInfo(String id, String name, Map<String, List<String>> sourceQualities,
                      Map<String, List<String>> sourceActions) {
        List<String> qualities(String source) {
            return sourceQualities.getOrDefault(source.toLowerCase(Locale.ROOT), List.of());
        }

        boolean supports(String source, String action) {
            return sourceActions.getOrDefault(source.toLowerCase(Locale.ROOT), List.of())
                    .contains(action.toLowerCase(Locale.ROOT));
        }
    }

    private record ScriptHttpResponse(HttpResponse<InputStream> response, byte[] body) {
    }

    private static final class ScriptTimer {
        private final Value callback;
        private final Value[] arguments;
        private final long delayMillis;
        private final boolean repeating;
        private volatile ScheduledFuture<?> future;

        private ScriptTimer(Value callback, Value[] arguments,
                            long delayMillis, boolean repeating) {
            this.callback = callback;
            this.arguments = arguments;
            this.delayMillis = delayMillis;
            this.repeating = repeating;
        }

        private Value callback() {
            return callback;
        }

        private Value[] arguments() {
            return arguments;
        }

        private long delayMillis() {
            return delayMillis;
        }

        private boolean repeating() {
            return repeating;
        }

        private void setFuture(ScheduledFuture<?> future) {
            this.future = future;
        }

        private void cancel() {
            ScheduledFuture<?> current = future;
            if (current != null) {
                current.cancel(false);
            }
        }
    }

    private record ScriptInfo(String name, String description, String version,
                              String author, String homepage) {
        private JsonObject toJson(String rawScript) {
            JsonObject json = new JsonObject();
            json.addProperty("name", name);
            json.addProperty("description", description);
            json.addProperty("version", version);
            json.addProperty("author", author);
            json.addProperty("homepage", homepage);
            json.addProperty("rawScript", rawScript);
            return json;
        }
    }

    private static final String BOOTSTRAP = """
            globalThis.__lxParseJson = JSON.parse;
            globalThis.__lxStringify = JSON.stringify;
            const __toBytes = value => Array.from(value ?? []);
            globalThis.setTimeout = (callback, delay = 0, ...args) =>
              __lxSetTimer(callback, Number(delay) || 0, args, false);
            globalThis.setInterval = (callback, delay = 0, ...args) =>
              __lxSetTimer(callback, Number(delay) || 0, args, true);
            globalThis.clearTimeout = timer => __lxClearTimer(timer);
            globalThis.clearInterval = timer => __lxClearTimer(timer);
            globalThis.window = globalThis;
            globalThis.lx = Object.freeze({
              EVENT_NAMES: Object.freeze({ request: 'request', inited: 'inited', updateAlert: 'updateAlert' }),
              request(url, options = {}, callback) {
                const body = options.body instanceof Uint8Array || ArrayBuffer.isView(options.body)
                  ? { __mikBytesBase64: __lxBufferToString(__toBytes(options.body), 'base64') }
                  : options.body;
                const safe = {
                  method: options.method ?? 'GET', headers: options.headers ?? {},
                  body, form: options.form, formData: options.formData,
                  timeout: options.timeout
                };
                return __lxRequest(String(url), JSON.stringify(safe), (error, response, body) => {
                  callback(error == null ? null : new Error(String(error)), response, body);
                });
              },
              on(event, handler) { __lxOn(String(event), handler); return Promise.resolve(); },
              send(event, data) { __lxSend(String(event), data); return Promise.resolve(); },
              setTimeout: globalThis.setTimeout,
              setInterval: globalThis.setInterval,
              clearTimeout: globalThis.clearTimeout,
              clearInterval: globalThis.clearInterval,
              utils: Object.freeze({
                crypto: Object.freeze({
                  aesEncrypt(buffer, mode, key, iv) { return Uint8Array.from(__lxAesEncrypt(__toBytes(buffer), mode, __toBytes(key), __toBytes(iv))); },
                  rsaEncrypt(buffer, key) { return Uint8Array.from(__lxRsaEncrypt(__toBytes(buffer), key)); },
                  randomBytes(size) { return Uint8Array.from(__lxRandomBytes(size)); },
                  md5(value) { return __lxMd5(String(value)); }
                }),
                buffer: Object.freeze({
                  from(value, encoding) { return Uint8Array.from(__lxBufferFrom(value, encoding ?? 'utf8')); },
                  bufToString(value, encoding) { return __lxBufferToString(__toBytes(value), encoding ?? 'utf8'); }
                }),
                zlib: Object.freeze({
                  inflate(value) { return new Promise((resolve, reject) => __lxInflate(__toBytes(value), v => resolve(Uint8Array.from(v)), reject)); },
                  deflate(value) { return new Promise((resolve, reject) => __lxDeflate(__toBytes(value), v => resolve(Uint8Array.from(v)), reject)); }
                })
              }),
              currentScriptInfo: Object.freeze(JSON.parse(__lxScriptInfoJson)),
              version: '2.0.0',
              env: 'desktop'
            });
            """;
}
