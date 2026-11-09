package org.encinet.mik.feature.player.teleport;

import java.util.List;
import java.util.Objects;
import org.encinet.mik.shared.menu.ui.UiKey;
import org.encinet.mik.shared.menu.ui.UiReadingPosition;

/** Browser state only; filtering never changes saved homes or the player's quota. */
record HomeMenuState(UiReadingPosition position, String focusedHome, String query) {
    HomeMenuState {
        Objects.requireNonNull(position);
        query = Objects.requireNonNull(query).strip();
    }

    static HomeMenuState initial() { return new HomeMenuState(UiReadingPosition.START, null, ""); }
    HomeMenuState search(String value) {
        String next = Objects.requireNonNull(value).strip();
        return query.equals(next) ? this : new HomeMenuState(UiReadingPosition.START, null, next);
    }
    HomeMenuState browse(UiReadingPosition value) { return new HomeMenuState(value, focusedHome, query); }
    HomeMenuState focus(String name) { return new HomeMenuState(position, name, query); }
    static HomeMenuState created(String name, int index) {
        return new HomeMenuState(new UiReadingPosition(UiKey.item("home:" + name), 0, index), name, "");
    }

    View view(List<String> names) {
        List<String> matches = names.stream().filter(this::matches).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        String active = focusedHome != null && matches.contains(focusedHome)
                ? focusedHome : matches.isEmpty() ? null : matches.getFirst();
        return new View(names.size(), matches, active);
    }

    private boolean matches(String name) {
        for (int offset = 0; offset <= name.length() - query.length(); offset++)
            if (name.regionMatches(true, offset, query, 0, query.length())) return true;
        return false;
    }

    record View(int homeCount, List<String> matches, String focusedHome) { }
}
