package org.encinet.mik.module.ai.tool.web;

import java.net.IDN;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Locale;
import java.util.Objects;

/** Produces fragment-free ASCII HTTP(S) URIs without double-encoding raw paths. */
final class HttpUriCanonicalizer {
    private HttpUriCanonicalizer() {
    }

    static URI canonicalize(URI input, int maximumCharacters) {
        URI uri = Objects.requireNonNull(input, "input").normalize();
        String scheme = Objects.requireNonNullElse(uri.getScheme(), "")
                .toLowerCase(Locale.ROOT);
        if (!(scheme.equals("http") || scheme.equals("https")) || !uri.isAbsolute()) {
            throw new IllegalArgumentException("Only absolute HTTP(S) URLs are supported");
        }
        URL url;
        try {
            url = uri.toURL();
        } catch (MalformedURLException error) {
            throw new IllegalArgumentException("URL is invalid", error);
        }
        if (uri.getRawUserInfo() != null || url.getUserInfo() != null) {
            throw new IllegalArgumentException("URL credentials are not allowed");
        }
        String rawHost = stripIpv6Brackets(url.getHost());
        if (rawHost.isBlank()) {
            throw new IllegalArgumentException("URL host is blank");
        }
        if (rawHost.endsWith(".")) {
            rawHost = rawHost.substring(0, rawHost.length() - 1);
        }
        String host;
        try {
            host = rawHost.indexOf(':') >= 0
                    ? canonicalIpv6(rawHost)
                    : IDN.toASCII(rawHost, IDN.USE_STD3_ASCII_RULES)
                    .toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("URL host is invalid", error);
        }
        if (host.isBlank()) {
            throw new IllegalArgumentException("URL host is blank");
        }
        int port = url.getPort();
        if (port == 0 || port > 65_535) {
            throw new IllegalArgumentException("URL port is invalid");
        }
        String authority = host.indexOf(':') >= 0 ? "[" + host + "]" : host;
        if (port > 0) {
            authority += ":" + port;
        }
        String path = uri.getRawPath();
        StringBuilder value = new StringBuilder(scheme).append("://")
                .append(authority).append(path == null || path.isEmpty() ? "/" : path);
        if (uri.getRawQuery() != null) {
            value.append('?').append(uri.getRawQuery());
        }
        URI canonical = URI.create(value.toString()).normalize();
        String ascii = canonical.toASCIIString();
        if (ascii.length() > maximumCharacters) {
            throw new IllegalArgumentException("URL is too long");
        }
        return URI.create(ascii);
    }

    private static String canonicalIpv6(String host) {
        if (host.indexOf('%') >= 0 || !host.matches("[0-9A-Fa-f:.]+")) {
            throw new IllegalArgumentException("Scoped or malformed IPv6 host");
        }
        return host.toLowerCase(Locale.ROOT);
    }

    private static String stripIpv6Brackets(String host) {
        return host != null && host.length() > 1
                && host.charAt(0) == '[' && host.charAt(host.length() - 1) == ']'
                ? host.substring(1, host.length() - 1) : Objects.requireNonNullElse(host, "");
    }
}
