package org.encinet.mik.shared.menu.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.encinet.mik.shared.menu.FloatingMenuPage;

/** Bounded document flow and bookmark resolution. It never changes the owner's reading position. */
final class UiDocumentPages {
    interface Measurer {
        UiSize block(UiNode node, UiKey scope, double height);
        List<Integer> textPages(UiElement node, UiDocumentText text, double height);
    }
    private record Source(UiChild child, Optional<UiDocumentText> text, int index) { }
    record Fragment(UiChild child, UiKey source, int index, int from, int to, UiSize size) {
        UiReadingPosition position() { return new UiReadingPosition(source, from, index); }
    }
    record Layout(List<Fragment> fragments, UiPagePacking.Packed body) {
        FloatingMenuPage page(UiReadingPosition position) {
            int target = -1;
            if (position.block().isPresent()) {
                for (int index = 0; index < fragments.size(); index++) {
                    var fragment = fragments.get(index);
                    if (!fragment.source().equals(position.block().orElseThrow())) continue;
                    target = index;
                    if (position.offset() < fragment.to()) break;
                }
            }
            if (target < 0 && !fragments.isEmpty()) {
                target = fragments.size() - 1;
                for (int index = 0; index < fragments.size(); index++) if (fragments.get(index).index() >= position.index()) {
                    target = index; break;
                }
            }
            for (int page = 0; page < body.pages().size(); page++) {
                var range = body.pages().get(page);
                if (target >= range.from() && target < range.to()) return new FloatingMenuPage(page, body.pages().size(), 1);
            }
            return new FloatingMenuPage(0, body.pages().size(), 1);
        }
        UiReadingPosition position(int page) {
            var range = body.pages().get(page);
            for (int index = range.from(); index < range.to(); index++)
                if (!fragments.get(index).size().equals(UiSize.ZERO)) return fragments.get(index).position();
            return UiReadingPosition.START;
        }
        List<UiChild> children(int page) {
            var range = body.pages().get(page);
            return fragments.subList(range.from(), range.to()).stream().map(Fragment::child).toList();
        }
    }

    private final List<Source> sources;
    private final Measurer measurer;
    private final double gap;
    private final java.util.Map<Double, Layout> layouts = new java.util.HashMap<>();

    UiDocumentPages(UiDocument document, Measurer measurer) {
        this.measurer = measurer; gap = document.gap().value();
        var values = new ArrayList<Source>();
        int points = 0;
        for (int index = 0; index < document.blocks().size(); index++) {
            UiChild child = document.blocks().get(index);
            Optional<UiDocumentText> text = Optional.empty();
            if (child.node() instanceof UiElement element && !element.input().interactive()
                    && element.content() instanceof UiContent.Text content && content.wrapping() && content.maximumLines() == Integer.MAX_VALUE)
                text = UiDocumentText.literal(content.label());
            if (text.isPresent()) points += text.orElseThrow().length();
            if (points > UiDocument.MAX_TEXT_CODE_POINTS) throw new IllegalArgumentException("Document text budget exceeded");
            values.add(new Source(child, text, index));
        }
        sources = List.copyOf(values);
    }

    Layout layout(double height) {
        if (height < 0) throw new UiOverflowException("Document header and pager exceed the viewport");
        var retained = layouts.get(height);
        if (retained != null) return retained;
        var fragments = new ArrayList<Fragment>();
        for (Source source : sources) {
            var child = source.child();
            if (source.text().isEmpty() || source.text().orElseThrow().length() == 0) {
                UiSize size = measurer.block(child.node(), child.scope(), height);
                if (size.height() > 0) fragments.add(new Fragment(child, child.scope(), source.index(), 0,
                        source.text().map(UiDocumentText::length).orElse(0), size));
            } else {
                var text = source.text().orElseThrow();
                var element = (UiElement) child.node();
                var ends = measurer.textPages(element, text, height);
                int from = 0;
                for (int to : ends) {
                    if (to <= from || to > text.length()) throw new IllegalArgumentException("Invalid document text page boundary");
                    var scope = UiKey.item(child.scope().value() + ":part:" + from);
                    UiElement part = element.withText(text.page(from, to));
                    UiSize size = measurer.block(part, scope, height);
                    fragments.add(new Fragment(new UiChild(scope, part, child.linear(), child.grid()), child.scope(), source.index(), from, to, size));
                    from = to;
                    if (fragments.size() > 2048) throw new IllegalArgumentException("Document fragment budget exceeded");
                }
                if (from != text.length()) throw new IllegalArgumentException("Document text pages lost source content");
            }
        }
        var body = UiPagePacking.column(fragments.stream().map(Fragment::size).toList(), height, gap);
        var result = new Layout(List.copyOf(fragments), body);
        if (layouts.size() >= 16) throw new IllegalArgumentException("Document remeasurement budget exceeded");
        layouts.put(height, result);
        return result;
    }
}
