package org.encinet.mik.util;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * High-throughput Cloudflare Error 1020 spoof.
 * Optimised for flood-attack scenarios: minimal allocation, no SecureRandom,
 * single-pass streaming write, per-thread host-byte cache.
 */
public final class CloudflareSpoof {

    /* ── PoP pool ─────────────────────────────────────────────────────── */
    private static final byte[][] CF_DCS_BYTES;
    static {
        String[] names = {
                "NRT","SIN","LAX","LHR","FRA","SJC",
                "HKG","AMS","CDG","SEA","SYD","GRU",
                "EWR","ORD","DFW","MIA","ATL","IAD"
        };
        CF_DCS_BYTES = new byte[names.length][];
        for (int i = 0; i < names.length; i++)
            CF_DCS_BYTES[i] = names[i].getBytes(StandardCharsets.US_ASCII);
    }

    /* ── Pre-encoded HTML segments ────────────────────────────────────── */
    // Sentinels: \u0000 = HOST(title)  \u0001 = HOST(body)  \u0002 = RAY-ID
    private static final byte[] SEG_A, SEG_B, SEG_C, SEG_D;
    static {
        // language=HTML
        final String T = """
            <!DOCTYPE html>
            <html lang="en-US">
            <head>
                <meta charset="UTF-8"/>
                <meta http-equiv="X-UA-Compatible" content="IE=Edge"/>
                <meta name="robots" content="noindex,nofollow"/>
                <meta name="cf-error" content="1020"/>
                <meta id="cf-error-details" data-cf-error-type="access_denied" data-cf-ray="\u0002"/>
                <title>Access denied | \u0000 used Cloudflare to restrict access</title>
                <style>
                  *{box-sizing:border-box}
                  body{margin:0;padding:0;background:#f5f5f5;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif}
                  #cf-wrapper{max-width:800px;margin:80px auto;padding:24px;background:#fff;border-radius:4px;box-shadow:0 1px 4px rgba(0,0,0,.12)}
                  .cf-error-type{color:#f38020;font-size:13px;font-weight:600;text-transform:uppercase;letter-spacing:.04em;margin-bottom:6px}
                  h1{font-size:36px;font-weight:700;color:#1d1d1d;margin:0 0 6px}
                  .cf-sub{font-size:20px;color:#404040;margin-bottom:20px}
                  p{font-size:14px;line-height:1.7;color:#555;margin:0 0 12px}
                  a{color:#f38020}
                  .cf-footer{margin-top:36px;padding-top:12px;border-top:1px solid #e5e5e5;font-size:12px;color:#aaa;display:flex;justify-content:space-between;align-items:center}
                  .cf-badge{display:flex;align-items:center;gap:6px}
                  .cf-badge svg{width:80px}
                </style>
            </head>
            <body>
            <div id="cf-wrapper">
              <div class="cf-error-type">Error 1020 &mdash; Access Denied</div>
              <h1>You don&rsquo;t have access to this page.</h1>
              <p class="cf-sub"><strong>\u0001</strong> is using Cloudflare to protect itself from online attacks.</p>
              <p>The action you just performed triggered the security solution.
                 There are several actions that could trigger this block including submitting a certain word
                 or phrase, a SQL command, or malformed data.</p>
              <p>If you believe this is in error, please contact the site owner and include your Ray ID.
                 You may also want to visit
                 <a href="https://www.cloudflare.com/5xx-error-landing">Cloudflare&rsquo;s troubleshooting page</a>.</p>
              <div class="cf-footer">
                <span>Ray ID&nbsp;<strong>\u0002</strong></span>
                <span class="cf-badge">
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 109 41">
                    <path fill="#f38020" d="M83 26.7c.2-.8.3-1.6.3-2.5 0-5.7-4.6-10.3-10.3-10.3-2.5
                    0-4.8.9-6.6 2.3-1.3-4.6-5.5-8-10.6-8-5.8 0-10.6 4.5-11 10.2-.5-.1-1-.2-1.5-.2-3.9
                    0-7 3.1-7 7 0 .5.1 1 .2 1.5H83z"/>
                  </svg>
                  Performance &amp; security by Cloudflare
                </span>
              </div>
            </div>
            </body>
            </html>
            """;

        String[] p = T.split("\u0000", 2);
        String[] q = p[1].split("\u0001", 2);
        String[] r = q[1].split("\u0002", 2);
        SEG_A = p[0].getBytes(StandardCharsets.UTF_8);
        SEG_B = q[0].getBytes(StandardCharsets.UTF_8);
        SEG_C = r[0].getBytes(StandardCharsets.UTF_8);
        SEG_D = r[1].getBytes(StandardCharsets.UTF_8);
    }

    /* ── Date cache (1-second granularity, two-field atomic-ish update) ── */
    private static final DateTimeFormatter RFC_FMT = DateTimeFormatter.RFC_1123_DATE_TIME;
    private static final AtomicLong              DATE_EPOCH = new AtomicLong(0);
    private static final AtomicReference<String> DATE_VALUE = new AtomicReference<>("");

    private static String cachedDate() {
        long sec = System.currentTimeMillis() / 1000L;
        // Single CAS avoids lock; at worst two threads recompute simultaneously — harmless
        if (DATE_EPOCH.get() != sec && DATE_EPOCH.compareAndSet(DATE_EPOCH.get(), sec)) {
            String d = RFC_FMT.format(ZonedDateTime.now(ZoneOffset.UTC));
            DATE_VALUE.set(d);
            return d;
        }
        return DATE_VALUE.get();
    }

    /* ── Per-thread Host-byte cache (SoftReference: evicted under pressure) ── */
    private static final ThreadLocal<SoftReference<HostCache>> HOST_CACHE =
            ThreadLocal.withInitial(() -> new SoftReference<>(null));

    private record HostCache(String raw, byte[] bytes) {}

    private static byte[] hostBytes(HttpExchange ex) {
        String raw = Optional.ofNullable(ex.getRequestHeaders().getFirst("Host"))
                .filter(s -> !s.isBlank())
                .orElse("unknown");

        SoftReference<HostCache> ref = HOST_CACHE.get();
        HostCache cached = ref.get();
        if (cached != null && cached.raw().equals(raw)) return cached.bytes();

        byte[] b = raw.getBytes(StandardCharsets.UTF_8);
        HOST_CACHE.set(new SoftReference<>(new HostCache(raw, b)));
        return b;
    }

    /* ── Ray-ID: pre-allocated char buffer, no String.format ─────────── */
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /**
     * Writes a Ray ID directly into a pre-allocated 24-char thread-local buffer.
     * Format: {@code <16-hex>-<POP>}  (first nibble always non-zero)
     */
    private static byte[] rayIdBytes() {
        var rng   = ThreadLocalRandom.current();
        long hi   = rng.nextLong() | 0x1000_0000_0000_0000L; // ensure leading nibble != 0
        byte[] dc = CF_DCS_BYTES[rng.nextInt(CF_DCS_BYTES.length)];

        // 16 hex chars + '-' + 3 IATA chars = 20 bytes max
        byte[] out = new byte[17 + dc.length];
        for (int i = 15; i >= 0; i--) {
            out[i] = (byte) HEX[(int)(hi & 0xF)];
            hi >>>= 4;
        }
        out[16] = '-';
        System.arraycopy(dc, 0, out, 17, dc.length);
        return out;
    }

    /**
     * cf-request-id: 32-char lower-hex via ThreadLocalRandom (no SecureRandom).
     * Sufficient for spoofing purposes; avoids entropy-pool contention under flood.
     */
    private static String reqId() {
        var rng = ThreadLocalRandom.current();
        // Build directly into char array — faster than String.format for two longs
        char[] buf = new char[32];
        long a = rng.nextLong(), b = rng.nextLong();
        for (int i = 15; i >= 0; i--) { buf[i]    = HEX[(int)(a & 0xF)]; a >>>= 4; }
        for (int i = 31; i >= 16; i--){ buf[i]    = HEX[(int)(b & 0xF)]; b >>>= 4; }
        return new String(buf);
    }

    /* ── Body length pre-computation ─────────────────────────────────── */
    private static final int STATIC_LEN =
            SEG_A.length + SEG_B.length + SEG_C.length + SEG_D.length;

    /* ── Public API ───────────────────────────────────────────────────── */

    /**
     * Reject the exchange with a Cloudflare Error 1020 response.
     * Hot path: no locks, no SecureRandom, no intermediate buffers.
     */
    public static void drop(HttpExchange exchange) throws IOException {
        final byte[] host  = hostBytes(exchange);
        final byte[] ray   = rayIdBytes();
        final int    len   = STATIC_LEN + host.length * 2 + ray.length;

        var h = exchange.getResponseHeaders();
        h.set("Server",                 "cloudflare");
        h.set("Date",                   cachedDate());
        h.set("Content-Type",           "text/html; charset=UTF-8");
        h.set("Connection",             "close");
        h.set("CF-Ray",                 new String(ray, StandardCharsets.US_ASCII));
        h.set("cf-request-id",         reqId());
        h.set("CF-Cache-Status",        "DYNAMIC");
        h.set("CF-Mitigated",           "threat");
        h.set("X-Content-Type-Options", "nosniff");
        h.set("X-Frame-Options",        "SAMEORIGIN");
        h.set("Referrer-Policy",        "same-origin");
        h.set("Permissions-Policy",     "browsing-topics=()");
        h.set("NEL",                    "{\"success_fraction\":0,\"report_to\":\"cf-nel\",\"max_age\":604800}");
        h.set("Report-To",              "{\"endpoints\":[{\"url\":\"https:\\/\\/a.nel.cloudflare.com\\/report\\/v4?s=REDACTED\"}],\"group\":\"cf-nel\",\"max_age\":604800}");
        h.set("alt-svc",                "h3=\":443\"; ma=86400");

        exchange.sendResponseHeaders(403, len);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(SEG_A); out.write(host);
            out.write(SEG_B); out.write(host);
            out.write(SEG_C); out.write(ray);
            out.write(SEG_D);
        }
    }

    private CloudflareSpoof() {}
}