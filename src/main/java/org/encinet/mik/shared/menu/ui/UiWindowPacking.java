package org.encinet.mik.shared.menu.ui;

import org.encinet.mik.shared.menu.FloatingMenuPage;

/** Constant-cost window sizing, independent of the backing collection's length. */
final class UiWindowPacking {
    private UiWindowPacking() { }

    static FloatingMenuPage page(int index, int count, double rowHeight, double available, double gap, java.util.OptionalInt anchorIndex, int columns) {
        if (available < -1e-6) throw new UiOverflowException("Window header and pager exceed the viewport");
        if (count == 0) return new FloatingMenuPage(0, 0, 1);
        int capacity = (int) Math.min(UiWindowedList.MAX_VISIBLE_ITEMS / columns,
                Math.floor((available + gap) / (rowHeight + gap))) * columns;
        if (capacity < 1) throw new UiOverflowException("Window needs one complete row of height " + rowHeight + " within remaining height " + available);
        int requested = anchorIndex.isPresent() ? Math.min(anchorIndex.getAsInt(),count - 1) / capacity : index;
        return new FloatingMenuPage(requested,count,capacity);
    }

    static double height(int count, double rowHeight, double gap) {
        return count == 0 ? 0 : count * rowHeight + (count - 1) * gap;
    }
}
