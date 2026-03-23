package org.encinet.mik.util;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mimics a Cloudflare edge node rejecting requests with Error 1020.
 * All static content is pre-encoded at class-load time; per-request
 * allocations are limited to the three dynamic values (date, ray-id, host).
 */
public final class CloudflareSpoof {

    // Cloudflare edge PoP codes (IATA)
    private static final String[] CF_DCS = {
            "NRT", "SIN", "LAX", "LHR", "FRA", "SJC",
            "HKG", "AMS", "CDG", "SEA", "SYD", "GRU"
    };

    // Static response headers
    private static final String H_SERVER       = "cloudflare";
    private static final String H_CT           = "text/html; charset=UTF-8";
    private static final String H_CACHE        = "DYNAMIC";
    private static final String H_CONN         = "close";
    private static final String H_XCTO         = "nosniff";
    private static final String H_XFO          = "SAMEORIGIN";
    private static final String H_RP           = "same-origin";
    private static final String H_PP           = "browsing-topics=()";
    private static final String H_ALT_SVC      = "h3=\":443\"; ma=86400";
    private static final String H_NEL          =
            "{\"success_fraction\":0,\"report_to\":\"cf-nel\",\"max_age\":604800}";
    private static final String H_REPORT_TO    =
            "{\"endpoints\":[{\"url\":\"https:\\/\\/a.nel.cloudflare.com\\/report\\/v4?s=REDACTED\"}]," +
                    "\"group\":\"cf-nel\",\"max_age\":604800}";
    /**
     * Real CF adds a timing header on cache misses; keeps scanners busy
     * chasing a CDN topology that does not exist.
     */
    private static final String H_CACHE_TIMING = "miss, miss";

    // ── Pre-encoded HTML segments (NUL/SOH/STX used as unique sentinels) ──
    // Slots: [A] {HOST} [B] {HOST} [C] {RAY} [D]
    private static final byte[] SEG_A, SEG_B, SEG_C, SEG_D;

    static {
        // language=HTML
        final String TEMPLATE = """
            <!DOCTYPE html>
            <html lang="en-US">
            <head>
                <meta charset="UTF-8"/>
                <meta http-equiv="X-UA-Compatible" content="IE=Edge"/>
                <meta name="robots" content="noindex,nofollow"/>
                <title>Access denied | \u0000 used Cloudflare to restrict access</title>
                <meta name="cf-error" content="1020"/>
                <style>
                  *{box-sizing:border-box}
                  body{margin:0;padding:0;background:#f5f5f5;font-family:-apple-system,BlinkMacSystemFont,
                       "Segoe UI",Roboto,Helvetica,Arial,sans-serif}
                  #cf-wrapper{max-width:800px;margin:80px auto;padding:24px;
                       background:#fff;border-radius:4px;box-shadow:0 1px 4px rgba(0,0,0,.12)}
                  .cf-error-type{color:#f38020;font-size:13px;font-weight:600;
                       text-transform:uppercase;letter-spacing:.04em;margin-bottom:6px}
                  h1{font-size:36px;font-weight:700;color:#1d1d1d;margin:0 0 6px}
                  .cf-sub{font-size:20px;color:#404040;margin-bottom:20px}
                  p{font-size:14px;line-height:1.7;color:#555;margin:0 0 12px}
                  a{color:#f38020}
                  .cf-footer{margin-top:36px;padding-top:12px;border-top:1px solid #e5e5e5;
                       font-size:12px;color:#aaa;display:flex;justify-content:space-between;
                       align-items:center}
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
              <p>If you believe this is in error, please contact the site owner and include your Ray ID below.
                 You may also want to visit <a href="https://www.cloudflare.com/5xx-error-landing">Cloudflare's troubleshooting page</a>.</p>
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

        String[] p = TEMPLATE.split("\u0000", 2);
        String[] q = p[1].split("\u0001", 2);
        String[] r = q[1].split("\u0002", 2);

        SEG_A = p[0].getBytes(StandardCharsets.UTF_8);
        SEG_B = q[0].getBytes(StandardCharsets.UTF_8);
        SEG_C = r[0].getBytes(StandardCharsets.UTF_8);
        SEG_D = r[1].getBytes(StandardCharsets.UTF_8);
    }

    // ── ThreadLocal reusable date formatter (DateTimeFormatter is thread-safe,
    //    but ZonedDateTime.format still allocates; we cache the formatter ref) ─
    private static final DateTimeFormatter RFC_FMT = DateTimeFormatter.RFC_1123_DATE_TIME;

    // Public API

    /** Drop the request with a full Cloudflare Error 1020 response. */
    public static void drop(HttpExchange exchange) throws IOException {
        final String rayId = rayId();
        final String reqId = reqId();
        final String date  = RFC_FMT.format(ZonedDateTime.now(ZoneOffset.UTC));
        final byte[] host  = hostBytes(exchange);
        final byte[] ray   = rayId.getBytes(StandardCharsets.UTF_8);

        final int bodyLen = SEG_A.length + host.length
                + SEG_B.length + host.length
                + SEG_C.length + ray.length
                + SEG_D.length;

        var h = exchange.getResponseHeaders();
        h.set("Server",                 H_SERVER);
        h.set("Date",                   date);
        h.set("Content-Type",           H_CT);
        h.set("Connection",             H_CONN);
        h.set("CF-Ray",                 rayId);
        h.set("cf-request-id",         reqId);
        h.set("CF-Cache-Status",        H_CACHE);
        h.set("X-Cache",                H_CACHE_TIMING);
        h.set("X-Content-Type-Options", H_XCTO);
        h.set("X-Frame-Options",        H_XFO);
        h.set("Referrer-Policy",        H_RP);
        h.set("Permissions-Policy",     H_PP);
        h.set("NEL",                    H_NEL);
        h.set("Report-To",              H_REPORT_TO);
        h.set("alt-svc",                H_ALT_SVC);

        exchange.sendResponseHeaders(403, bodyLen);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(SEG_A); out.write(host);
            out.write(SEG_B); out.write(host);
            out.write(SEG_C); out.write(ray);
            out.write(SEG_D);
        }
    }

    // Private helpers

    private static byte[] hostBytes(HttpExchange exchange) {
        return Optional.ofNullable(exchange.getRequestHeaders().getFirst("Host"))
                .filter(s -> !s.isBlank())
                .orElse("unknown")
                .getBytes(StandardCharsets.UTF_8);
    }

    /** CF Ray ID: 16-char lowercase hex + IATA PoP code */
    private static String rayId() {
        var rng = ThreadLocalRandom.current();
        return String.format("%016x-%s", rng.nextLong(), CF_DCS[rng.nextInt(CF_DCS.length)]);
    }

    /** cf-request-id: 32-char lowercase hex */
    private static String reqId() {
        var rng = ThreadLocalRandom.current();
        return String.format("%016x%016x", rng.nextLong(), rng.nextLong());
    }

    private CloudflareSpoof() {}
}
