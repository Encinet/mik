package org.encinet.mik.module.ai.tool.web;

/** Compact model-facing representation of an external HTML page. */
record ReadableWebPage(
        String title,
        String description,
        String markdown,
        boolean truncated
) {
}
