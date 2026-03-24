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

/**
 * High-fidelity Cloudflare Error 1020 honeypot response.
 * Private-API sentinel: any caller is an intruder — drop with maximum deception.
 * <p>
 * Performance contract:
 *   - Zero SecureRandom (ThreadLocalRandom only)
 *   - Zero intermediate buffers (Content-Length pre-computed, single streaming write)
 *   - Date string cached 1 s (double-checked volatile)
 *   - Host bytes cached per-thread (SoftReference, evicted under GC pressure)
 *   - All Ray/ReqId encoding done in pre-allocated byte arrays (no String.format)
 */
public final class CloudflareSpoof {

    /* ── PoP pool (pre-encoded ASCII) ─────────────────────────────────── */
    private static final byte[][] POPS;
    static {
        String[] names = {
                "NRT","SIN","LAX","LHR","FRA","SJC",
                "HKG","AMS","CDG","SEA","SYD","GRU",
                "EWR","ORD","DFW","MIA","ATL","IAD",
                "ICN","BOM","DXB","JNB","GIG","MAD"
        };
        POPS = new byte[names.length][];
        for (int i = 0; i < names.length; i++)
            POPS[i] = names[i].getBytes(StandardCharsets.US_ASCII);
    }

    /* ── HEX LUT ───────────────────────────────────────────────────────── */
    private static final byte[] HEX =
            "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

    /* ─────────────────────────────────────────────────────────────────────
     * HTML segments — sentinels (each appears EXACTLY ONCE in template):
     *   \u0000  HOST in <title>                          → 1 occurrence
     *   \u0001  HOST in subheading / paragraph / detail  → 3 occurrences
     *   \u0002  RAY  in data-cf-ray meta attribute       → 1 occurrence
     *   \u0003  RAY  in footer Ray-ID span               → 1 occurrence
     *
     * Split result: 7 segments  S0 … S6   (NO S7 — previous declaration bug)
     * ──────────────────────────────────────────────────────────────────── */
    private static final byte[] S0, S1, S2, S3, S4, S5, S6;

    static {
        // language=HTML
        final String T =
                """
                        <!DOCTYPE html>
                        <html lang="en-US">
                        <head>
                        <title>Access denied | \u0000 used Cloudflare to restrict access</title>
                        <meta charset="UTF-8"/>
                        <meta http-equiv="X-UA-Compatible" content="IE=Edge"/>
                        <meta name="robots" content="noindex,nofollow"/>
                        <meta name="cf-error" content="1020"/>
                        <meta id="cf-error-details"
                              data-cf-error-type="access_denied"
                              data-cf-ray="\u0002"
                              data-cf-timestamp=""/>
                        <style>
                        *,*::before,*::after{box-sizing:border-box}
                        html,body{margin:0;padding:0;height:100%}
                        body{
                          background:#f0f2f5;
                          font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;
                          color:#313131;
                          display:flex;flex-direction:column;min-height:100vh;
                        }
                        .cf-topbar{
                          background:#fff;border-bottom:1px solid #e5e5e5;
                          padding:10px 24px;display:flex;align-items:center;
                        }
                        .cf-topbar-logo svg{width:100px;display:block}
                        main{flex:1;display:flex;align-items:flex-start;justify-content:center;padding:48px 16px 32px}
                        .cf-card{
                          background:#fff;border-radius:8px;
                          box-shadow:0 1px 3px rgba(0,0,0,.08),0 4px 16px rgba(0,0,0,.06);
                          max-width:720px;width:100%;padding:40px 48px 36px;
                        }
                        @media(max-width:600px){.cf-card{padding:28px 20px 24px}}
                        .cf-header{display:flex;gap:20px;align-items:flex-start;margin-bottom:28px}
                        .cf-shield{
                          flex-shrink:0;width:56px;height:56px;border-radius:50%;
                          background:#fff4ec;display:flex;align-items:center;justify-content:center;
                        }
                        .cf-shield svg{width:30px;height:30px}
                        .cf-title-group{flex:1}
                        .cf-tag{
                          display:inline-block;font-size:11px;font-weight:700;letter-spacing:.1em;
                          text-transform:uppercase;color:#f38020;
                          background:#fff4ec;border:1px solid #fcd9b6;
                          border-radius:3px;padding:2px 7px;margin-bottom:8px;
                        }
                        h1{font-size:26px;font-weight:700;color:#1a1a1a;margin:0 0 6px;line-height:1.2}
                        .cf-sub{font-size:14px;color:#606060;margin:0}
                        hr{border:none;border-top:1px solid #ebebeb;margin:0 0 24px}
                        p{font-size:14px;line-height:1.75;color:#555;margin:0 0 14px}
                        p:last-of-type{margin-bottom:0}
                        a{color:#f38020;text-decoration:none}
                        a:hover{text-decoration:underline}
                        .cf-detail{
                          margin-top:20px;background:#fafafa;
                          border:1px solid #ebebeb;border-radius:5px;
                          padding:14px 18px;font-size:13px;color:#555;
                        }
                        .cf-detail dt{font-weight:600;color:#1a1a1a;display:inline}
                        .cf-detail dd{display:inline;margin:0}
                        footer{
                          background:#f7f7f9;border-top:1px solid #e5e5e5;
                          padding:12px 24px;
                          display:flex;align-items:center;justify-content:space-between;
                          flex-wrap:wrap;gap:8px;
                        }
                        .cf-ray{font-size:13px;color:#737373}
                        .cf-ray b{color:#1a1a1a;font-family:"SFMono-Regular",Consolas,monospace;font-size:12px}
                        .cf-powered{display:flex;align-items:center;gap:8px}
                        .cf-powered-label{font-size:12px;color:#909090}
                        .cf-powered-logo svg{width:92px;display:block}
                        </style>
                        </head>
                        <body>
                        <header class="cf-topbar">
                          <div class="cf-topbar-logo">
                            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 180 40" role="img" aria-label="Cloudflare">
                              <g transform="translate(0,4)">
                                <path fill="#f38020" d="
                                  M55 28.5H14.6c-3.9 0-7-3.1-7-7 0-3.4 2.4-6.2 5.6-6.9
                                  C13.5 9.3 17.8 6 22.9 6c2.8 0 5.4 1 7.4 2.7
                                  C31.9 5.1 35.4 3 39.4 3c6.5 0 11.8 5 12.2 11.4
                                  C54.4 15 57 17.6 57 20.8c0 0 0 0.1 0 0.1C56.4 27.1 52.4 28.5 55 28.5z"/>
                                <path fill="#fcd9b6" d="
                                  M46.5 15.2c-0.1 0-0.2 0-0.3 0C45.5 11 42 8 37.8 8
                                  c-2.1 0-4 0.8-5.4 2C31.2 7.7 28.3 6 25 6c-0.4 0-0.8 0-1.2 0.1
                                  C25.1 8.3 26 11 26 14c0 0.4 0 0.9-0.1 1.3C26.6 15.1 27.3 15 28 15
                                  c2.6 0 4.9 1.1 6.6 2.9C35.9 15.7 38.2 14.5 40.8 14.5c1.3 0 2.5 0.3 3.6 0.7z"/>
                              </g>
                              <text x="63" y="28" font-family="Arial,Helvetica,sans-serif"
                                    font-size="17" font-weight="700" fill="#404040"
                                    letter-spacing="-0.2">Cloudflare</text>
                            </svg>
                          </div>
                        </header>
                        <main>
                          <div class="cf-card">
                            <div class="cf-header">
                              <div class="cf-shield">
                                <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                  <path d="M12 2L3 7v5c0 5.25 3.75 10.15 9 11.35C17.25 22.15 21 17.25 21 12V7L12 2z"
                                        fill="#f38020"/>
                                  <path d="M9 12l2 2 4-4" stroke="#fff" stroke-width="2.2"
                                        stroke-linecap="round" stroke-linejoin="round"/>
                                </svg>
                              </div>
                              <div class="cf-title-group">
                                <span class="cf-tag">Error 1020</span>
                                <h1>Access denied</h1>
                                <p class="cf-sub">\u0001 is using Cloudflare to protect itself from online attacks.</p>
                              </div>
                            </div>
                            <hr/>
                            <p>You cannot access <strong>\u0001</strong>. The action you just performed triggered the
                            security solution. There are several actions that could trigger this block including
                            submitting a certain word or phrase, a SQL command, or malformed data.</p>
                            <p>If you believe you have been blocked in error, please contact the website owner and
                            provide them with your Ray&nbsp;ID. You can also visit
                            <a href="https://www.cloudflare.com/5xx-error-landing" rel="noopener">Cloudflare&rsquo;s
                            support page</a> for more information.</p>
                            <div class="cf-detail">
                              <dl>
                                <dt>Error code: </dt><dd>1020</dd>
                                &nbsp;&middot;&nbsp;
                                <dt>Site: </dt><dd>\u0001</dd>
                              </dl>
                            </div>
                          </div>
                        </main>
                        <footer>
                          <span class="cf-ray">Ray ID:&nbsp;<b>\u0003</b></span>
                          <div class="cf-powered">
                            <span class="cf-powered-label">Performance &amp; security by</span>
                            <div class="cf-powered-logo">
                              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 180 32" role="img" aria-label="Cloudflare">
                                <g transform="translate(0,2)">
                                  <path fill="#f38020" d="
                                    M46 23H12.2c-3.2 0-5.7-2.6-5.7-5.7 0-2.8 2-5.1 4.6-5.6
                                    C11.5 7.6 15.2 5 19.5 5c2.3 0 4.4 0.8 6 2.2C27.1 4.1 30 2.5 33.3 2.5
                                    c5.3 0 9.7 4.1 10 9.3 2.6 0.7 4.5 3 4.5 5.8C47.8 20.7 47.1 23 46 23z"/>
                                  <path fill="#fcd9b6" d="
                                    M38.8 12.4c-0.1 0-0.1 0-0.2 0C37.9 9.2 35 6.9 31.5 6.9
                                    c-1.7 0-3.3 0.6-4.5 1.6C26 6.3 23.6 5 20.8 5c-0.3 0-0.7 0-1 0.1
                                    C21.5 7 22.2 9.3 22.2 11.8c0 0.4 0 0.7-0.1 1.1C22.7 12.7 23.3 12.6 24 12.6
                                    c2.1 0 4 0.9 5.4 2.4C30.5 13.3 32.4 12.3 34.5 12.3c1.1 0 2.1 0.2 3 0.6z"/>
                                </g>
                                <text x="51" y="22" font-family="Arial,Helvetica,sans-serif"
                                      font-size="14" font-weight="700" fill="#404040"
                                      letter-spacing="-0.1">Cloudflare</text>
                              </svg>
                            </div>
                          </div>
                        </footer>
                        </body>
                        </html>
                        """;

        //  \u0000 ×1  → split(limit=2) → p[0..1]
        //  \u0001 ×3  → split(limit=4) → q[0..3]
        //  \u0002 ×1  → split(limit=2) → r[0..1]
        //  \u0003 ×1  → split(limit=2) → s[0..1]
        //  Total segments assigned: S0…S6  (7 fields, no S7)
        String[] p = T.split("\u0000", 2);
        String[] q = p[1].split("\u0001", 4);
        String[] r = q[3].split("\u0002", 2);
        String[] s = r[1].split("\u0003", 2);

        S0 = p[0].getBytes(StandardCharsets.UTF_8);  // before HOST(title)
        S1 = q[0].getBytes(StandardCharsets.UTF_8);  // after HOST(title)   → before HOST(subheading)
        S2 = q[1].getBytes(StandardCharsets.UTF_8);  // after HOST(subheading) → before HOST(<p>)
        S3 = q[2].getBytes(StandardCharsets.UTF_8);  // after HOST(<p>)     → before HOST(detail)
        S4 = r[0].getBytes(StandardCharsets.UTF_8);  // after HOST(detail)  → before RAY(meta)
        S5 = s[0].getBytes(StandardCharsets.UTF_8);  // after RAY(meta)     → before RAY(footer)
        S6 = s[1].getBytes(StandardCharsets.UTF_8);  // after RAY(footer)
    }

    /** Pre-computed static byte count; dynamic part = host×4 + ray×2. */
    private static final int STATIC_LEN =
            S0.length + S1.length + S2.length + S3.length +
                    S4.length + S5.length + S6.length;

    /* ── Date cache — double-checked volatile ──────────────────────────── */
    private static final DateTimeFormatter RFC_FMT = DateTimeFormatter.RFC_1123_DATE_TIME;
    private static volatile long   _dateEpoch = 0;
    private static volatile String _dateValue = "";

    private static String cachedDate() {
        long sec = System.currentTimeMillis() / 1000L;
        if (_dateEpoch != sec) {
            synchronized (CloudflareSpoof.class) {
                if (_dateEpoch != sec) {
                    _dateValue = RFC_FMT.format(ZonedDateTime.now(ZoneOffset.UTC));
                    _dateEpoch = sec;
                }
            }
        }
        return _dateValue;
    }

    /* ── Per-thread Host-byte cache ────────────────────────────────────── */
    private record HostCache(String key, byte[] val) {}
    private static final ThreadLocal<SoftReference<HostCache>> HOST_TL =
            ThreadLocal.withInitial(() -> new SoftReference<>(null));

    private static byte[] hostBytes(HttpExchange ex) {
        String raw = Optional.ofNullable(ex.getRequestHeaders().getFirst("Host"))
                .filter(s -> !s.isBlank()).orElse("unknown");
        SoftReference<HostCache> ref = HOST_TL.get();
        HostCache c = ref.get();
        if (c != null && c.key().equals(raw)) return c.val();
        byte[] b = raw.getBytes(StandardCharsets.UTF_8);
        HOST_TL.set(new SoftReference<>(new HostCache(raw, b)));
        return b;
    }

    /* ── Ray-ID: byte[] only, no String.format, no SecureRandom ───────── */
    private static byte[] rayIdBytes() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        long v   = rng.nextLong() | 0x1000_0000_0000_0000L;
        byte[] pop = POPS[rng.nextInt(POPS.length)];
        byte[] out = new byte[17 + pop.length];
        for (int i = 15; i >= 0; i--) { out[i] = HEX[(int)(v & 0xF)]; v >>>= 4; }
        out[16] = '-';
        System.arraycopy(pop, 0, out, 17, pop.length);
        return out;
    }

    /** cf-request-id: 32-char lower-hex, ThreadLocalRandom only. */
    private static String reqId() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        long a = rng.nextLong(), b = rng.nextLong();
        char[] buf = new char[32];
        for (int i = 15; i >= 0; i--) { buf[i]      = (char) HEX[(int)(a & 0xF)]; a >>>= 4; }
        for (int i = 31; i >= 16; i--){ buf[i]      = (char) HEX[(int)(b & 0xF)]; b >>>= 4; }
        return new String(buf);
    }

    /* ── Public API ────────────────────────────────────────────────────── */

    /**
     * Reject {@code exchange} with a pixel-accurate Cloudflare Error 1020 page.
     *
     * <p>Caller contract: any caller reaching this method is an intruder.
     * The response maximises attacker confusion and dwell-time waste.
     */
    public static void drop(HttpExchange exchange) throws IOException {
        final byte[] host  = hostBytes(exchange);
        final byte[] ray   = rayIdBytes();
        // ✅ PERF: ray→String once, reused for both CF-Ray header and body write
        //          (body uses raw byte[], header uses String — no double-encode)
        final String rayStr = new String(ray, StandardCharsets.US_ASCII);
        final int    len    = STATIC_LEN + host.length * 4 + ray.length * 2;

        var h = exchange.getResponseHeaders();
        // Header order mirrors real CF edge response (Wireshark-verified)
        h.set("Server",                 "cloudflare");
        h.set("Date",                   cachedDate());
        h.set("Content-Type",           "text/html; charset=UTF-8");
        h.set("Connection",             "close");
        h.set("Cache-Control",          "private, max-age=0, no-store, no-cache, must-revalidate, post-check=0, pre-check=0");
        h.set("Expires",                "Thu, 01 Jan 1970 00:00:01 GMT");
        h.set("Vary",                   "Accept-Encoding");
        h.set("CF-Ray",                 rayStr);
        h.set("cf-request-id",          reqId());
        h.set("CF-Cache-Status",        "DYNAMIC");
        h.set("CF-Mitigated",           "threat");
        h.set("X-Content-Type-Options", "nosniff");
        h.set("X-Frame-Options",        "SAMEORIGIN");
        h.set("Referrer-Policy",        "same-origin");
        h.set("Permissions-Policy",     "browsing-topics=()");
        h.set("NEL",
                "{\"success_fraction\":0,\"report_to\":\"cf-nel\",\"max_age\":604800}");
        h.set("Report-To",
                "{\"endpoints\":[{\"url\":\"https:\\/\\/a.nel.cloudflare.com" +
                        "\\/report\\/v4?s=REDACTED\"}],\"group\":\"cf-nel\",\"max_age\":604800}");
        h.set("alt-svc",                "h3=\":443\"; ma=86400");

        exchange.sendResponseHeaders(403, len);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(S0); out.write(host);  // <title> HOST
            out.write(S1); out.write(host);  // subheading HOST
            out.write(S2); out.write(host);  // <p> HOST
            out.write(S3); out.write(host);  // detail dl HOST
            out.write(S4); out.write(ray);   // meta data-cf-ray
            out.write(S5); out.write(ray);   // footer Ray-ID
            out.write(S6);
        }
    }

    private CloudflareSpoof() {}
}