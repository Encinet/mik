package org.encinet.mik.module.menu;

import java.util.List;

/** Immutable, clamped pagination state shared by menus. */
public record FloatingMenuPage(int index, int itemCount, int pageSize) {
    public FloatingMenuPage {
        if (itemCount < 0) throw new IllegalArgumentException("itemCount must be >= 0");
        if (pageSize < 1) throw new IllegalArgumentException("pageSize must be >= 1");
        index = Math.clamp(index, 0, Math.max(0, (itemCount - 1) / pageSize));
    }

    public int count() { return Math.max(1, (itemCount + pageSize - 1) / pageSize); }
    public boolean hasPrevious() { return index > 0; }
    public boolean hasNext() { return index + 1 < count(); }
    public FloatingMenuPage previous() { return new FloatingMenuPage(index - 1, itemCount, pageSize); }
    public FloatingMenuPage next() { return new FloatingMenuPage(index + 1, itemCount, pageSize); }
    public int fromIndex() { return index * pageSize; }
    public int toIndex() { return Math.min(itemCount, fromIndex() + pageSize); }
    public <T> List<T> slice(List<T> items) { return items.subList(fromIndex(), toIndex()); }
}
