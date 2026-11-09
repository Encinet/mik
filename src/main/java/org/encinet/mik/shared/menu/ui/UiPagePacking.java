package org.encinet.mik.shared.menu.ui;

import java.util.ArrayList;
import java.util.List;

/** Bounded collection packing, preserving item order and sharing the verified layout with realization. */
final class UiPagePacking {
    record Range(int from, int to) { }
    record Packed(List<Range> pages, UiSize size, UiLayout layout) { }

    private UiPagePacking() { }

    static Packed flow(List<UiSize> items, UiSize available, int rows, double gap, double rowGap) {
        var pages = new ArrayList<Range>();
        int start = 0, row = 0;
        double x = 0, naturalWidth = 0, rowHeight = 0, pageHeight = 0, maximumHeight = 0;
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            if (item.equals(UiSize.ZERO)) continue;
            if (item.height() > available.height() + 1e-6)
                throw new UiOverflowException("Flow item " + i + " exceeds one page's reading height");
            boolean wrap = x > 0 && x + item.width() > available.width();
            int nextRow = row + (wrap ? 1 : 0);
            double nextTop = wrap ? pageHeight + rowHeight + rowGap : pageHeight;
            double nextX = wrap ? 0 : x;
            double nextHeight = wrap ? item.height() : Math.max(rowHeight, item.height());
            if (i > start && (nextRow >= rows || nextTop + nextHeight > available.height() + 1e-6)) {
                pages.add(new Range(start, i));
                maximumHeight = Math.max(maximumHeight, pageHeight + rowHeight);
                start = i;
                nextRow = 0;
                nextTop = 0;
                nextX = 0;
                nextHeight = item.height();
            }
            row = nextRow;
            pageHeight = nextTop;
            rowHeight = nextHeight;
            naturalWidth = Math.max(naturalWidth, nextX + item.width());
            x = nextX + item.width() + gap;
        }
        pages.add(new Range(start, items.size()));
        maximumHeight = Math.max(maximumHeight, pageHeight + rowHeight);
        return new Packed(List.copyOf(pages), new UiSize(naturalWidth, maximumHeight),
                UiLayouts.wrap(UiLength.ui(gap), UiLength.ui(rowGap)));
    }

    static Packed column(List<UiSize> items, double height, double gap) {
        var pages = new ArrayList<Range>();
        int start = 0;
        double used = 0, maximumHeight = 0, width = 0;
        for (int index=0; index<items.size(); index++) {
            double next = items.get(index).height();
            width = Math.max(width, items.get(index).width());
            if (next == 0) continue;
            if (next > height+1e-6) throw new UiOverflowException("A reading item exceeds one list page; split long paragraphs");
            if (used>0 && used+gap+next > height+1e-6) {
                pages.add(new Range(start,index));
                maximumHeight = Math.max(maximumHeight, used);
                start=index; used=0;
            }
            used += (used==0 ? 0 : gap) + next;
        }
        pages.add(new Range(start,items.size()));
        return new Packed(List.copyOf(pages), new UiSize(width, Math.max(maximumHeight, used)), UiLayouts.column(UiLength.ui(gap)));
    }

    static Packed layout(int count, UiLayout layout, java.util.function.BiFunction<Integer, Integer, UiSize> measure) {
        var pages = new ArrayList<Range>();
        double width = 0, height = 0;
        for (int from = 0; from < count;) {
            int to = count;
            UiSize size = null;
            UiOverflowException failure = null;
            // An arbitrary responsive layout need not fit monotonically as children are added.
            // Try complete prefixes, retaining their domain order and only handling real overflow.
            for (; to > from; to--) {
                try { size = measure.apply(from, to); break; }
                catch (UiOverflowException overflow) { failure = overflow; }
            }
            if (size == null) throw java.util.Objects.requireNonNull(failure);
            pages.add(new Range(from, to));
            width = Math.max(width, size.width()); height = Math.max(height, size.height());
            from = to;
        }
        if (pages.isEmpty()) pages.add(new Range(0, 0));
        return new Packed(List.copyOf(pages), new UiSize(width, height), layout);
    }
}
