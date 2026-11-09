package org.encinet.mik.feature.entertainment.music.ui;

import java.util.List;
import java.util.function.Function;
import org.encinet.mik.shared.menu.ui.UiKey;

/** A feature-owned source bookmark, independent of measured page capacity and row representation. */
record MusicSourcePosition(UiKey anchor, int index) {
    static final MusicSourcePosition START = new MusicSourcePosition(UiKey.AUTO, 0);
    MusicSourcePosition {
        java.util.Objects.requireNonNull(anchor);
        if (index < 0) throw new IllegalArgumentException("Negative music reading position");
    }
    <T> int resolve(List<T> rows, Function<T, UiKey> identity) {
        if (!anchor.equals(UiKey.AUTO)) for (int item = 0; item < rows.size(); item++)
            if (anchor.equals(identity.apply(rows.get(item)))) return item;
        return Math.min(index, Math.max(0, rows.size() - 1));
    }
    static <T> MusicSourcePosition at(List<T> rows, int index, Function<T, UiKey> identity) {
        if (rows.isEmpty()) return START;
        int actual = Math.clamp(index, 0, rows.size() - 1);
        return new MusicSourcePosition(identity.apply(rows.get(actual)), actual);
    }
}
