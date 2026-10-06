package org.encinet.mik.module.plot;

import org.encinet.mik.module.i18n.Message;

import java.util.ArrayList;
import java.util.List;

final class PlotNoticeText {
    static final int MAX_LENGTH = 4096;
    static final int MAX_INPUT_LENGTH = MAX_LENGTH * 2;
    static final int PREVIEW_LENGTH = 96;
    static final int PAGE_LENGTH = 240;
    static final int PAGE_LINES = 8;

    private PlotNoticeText() { }

    static String normalize(String raw) {
        if (raw == null) throw new PlotProblem(Message.PLOT_ERROR_NOTICE_TEXT, MAX_LENGTH);
        String text = raw.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (text.codePointCount(0, text.length()) > MAX_LENGTH)
            throw new PlotProblem(Message.PLOT_ERROR_NOTICE_TEXT, MAX_LENGTH);
        if (text.codePoints().anyMatch(codePoint -> codePoint != '\n'
                && Character.isISOControl(codePoint)))
            throw new PlotProblem(Message.PLOT_ERROR_NOTICE_TEXT, MAX_LENGTH);
        return text;
    }

    static String preview(String text) {
        String compact = text.replaceAll("\\s+", " ").strip();
        if (compact.codePointCount(0, compact.length()) <= PREVIEW_LENGTH) return compact;
        return compact.substring(0, compact.offsetByCodePoints(0, PREVIEW_LENGTH - 1)) + "…";
    }

    static List<String> pages(String text) {
        if (text.isEmpty()) return List.of("");
        List<String> pages = new ArrayList<>();
        int start = 0;
        int offset = 0;
        int characters = 0;
        int lines = 1;
        while (offset < text.length()) {
            int character = text.codePointAt(offset);
            offset += Character.charCount(character);
            characters++;
            if (character == '\n') lines++;
            if (characters == PAGE_LENGTH || lines == PAGE_LINES || offset == text.length()) {
                pages.add(text.substring(start, offset));
                start = offset;
                characters = 0;
                lines = 1;
            }
        }
        return List.copyOf(pages);
    }
}
