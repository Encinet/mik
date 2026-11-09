package org.encinet.mik.feature.world.plot;

import org.encinet.mik.shared.i18n.Message;

final class PlotNoticeText {
    static final int MAX_LENGTH = 4096;
    static final int PREVIEW_LENGTH = 96;

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

}
