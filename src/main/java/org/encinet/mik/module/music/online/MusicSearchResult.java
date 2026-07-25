package org.encinet.mik.module.music.online;

import java.util.List;

public record MusicSearchResult<T>(List<T> items, int total, List<String> failures) {
    public MusicSearchResult {
        items = items == null ? List.of() : List.copyOf(items);
        total = Math.max(items.size(), total);
        failures = failures == null ? List.of() : List.copyOf(failures);
    }
}
