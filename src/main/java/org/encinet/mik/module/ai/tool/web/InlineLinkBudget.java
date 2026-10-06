package org.encinet.mik.module.ai.tool.web;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Validates, deduplicates, and bounds absolute links retained in converted content. */
final class InlineLinkBudget {
    private static final int MAXIMUM_LINK_URL = 1_000;

    private final int maximumLinks;
    private final Set<String> links = new LinkedHashSet<>();

    InlineLinkBudget(int maximumLinks) {
        this.maximumLinks = maximumLinks;
    }

    String add(String rawUrl) {
        String url = httpUrl(rawUrl);
        if (url.isBlank()) {
            return null;
        }
        if (links.contains(url)) {
            return url;
        }
        if (links.size() >= maximumLinks) {
            return null;
        }
        links.add(url);
        return url;
    }

    static String httpUrl(String rawUrl) {
        try {
            return HttpUriCanonicalizer.canonicalize(
                    URI.create(Objects.requireNonNullElse(rawUrl, "")),
                    MAXIMUM_LINK_URL).toASCIIString();
        } catch (RuntimeException error) {
            return "";
        }
    }
}
