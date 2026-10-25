package org.encinet.mik.feature.community.ai.tool.web;

import java.net.URI;

/** Validates and canonicalizes one destination immediately before an HTTP request. */
@FunctionalInterface
interface WebUriPolicy {
    URI requirePublic(URI uri);
}
