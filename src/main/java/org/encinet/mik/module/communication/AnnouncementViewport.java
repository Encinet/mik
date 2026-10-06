package org.encinet.mik.module.communication;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

final class AnnouncementViewport {
    static final int WIDTH = 352;
    static final int ROWS = AnnouncementReading.LINES_PER_PAGE + 2;
    static final float FONT_SCALE = 0.92F;
    private static final int MARGIN = (WIDTH - AnnouncementReading.LINE_WIDTH_PIXELS) / 2;
    private static final int EDGE = 2;

    record Fragment(int page, int row, String text, double left, int width) { }

    private AnnouncementViewport() { }

    static List<Fragment> fragments(AnnouncementBoard board, double position,
                                    Function<AnnouncementBoard.Page, String> date) {
        if (!Double.isFinite(position)) throw new IllegalArgumentException("Invalid viewport position");
        if (board.size() == 0) return List.of();
        double bounded = Math.clamp(position, 0, board.maximumStart());
        int first = (int) Math.floor(bounded);
        int last = Math.min(board.maximumStart(), (int) Math.ceil(bounded));
        List<Fragment> fragments = new ArrayList<>();
        for (int pageIndex = first; pageIndex <= last; pageIndex++) {
            AnnouncementBoard.Page page = board.page(pageIndex);
            double left = (pageIndex - bounded) * WIDTH + MARGIN;
            String header = date.apply(page);
            if (page.parts() > 1) header += " · " + (page.part() + 1) + "/" + page.parts();
            add(fragments, pageIndex, 0, header, left);
            String[] lines = page.text().split("\n", -1);
            for (int row = 0; row < lines.length; row++) add(fragments, pageIndex, row + 2, lines[row], left);
        }
        return List.copyOf(fragments);
    }

    private static void add(List<Fragment> fragments, int page, int row, String text, double left) {
        int start = -1;
        int end = -1;
        double firstPixel = 0;
        double cursor = left;
        int width = 0;
        var glyphs = AnnouncementReading.GLYPHS.matcher(text);
        while (glyphs.find()) {
            int glyph = AnnouncementReading.textWidth(glyphs.group());
            if (glyph > 0 && cursor >= EDGE && cursor + glyph <= WIDTH - EDGE) {
                if (start < 0) {
                    start = glyphs.start();
                    firstPixel = cursor;
                }
                end = glyphs.end();
                width += glyph;
            } else if (start >= 0) {
                break;
            }
            cursor += glyph;
        }
        if (start >= 0 && !text.substring(start, end).isBlank())
            fragments.add(new Fragment(page, row, text.substring(start, end), firstPixel, width));
    }
}
