package org.encinet.mik.feature.world.plot;

import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;

/** A scoped reading position; the host supplies current cached data and owns refresh and dismissal. */
final class PlotNoticeReader implements MenuPanel {
    private final Supplier<PlotNoticeBoard> current;
    private final Supplier<Component> title;
    private final Runnable redraw;
    private UiReadingPosition position = UiReadingPosition.START;
    private boolean closed;

    PlotNoticeReader(Supplier<PlotNoticeBoard> current, Supplier<Component> title, Runnable redraw) {
        this.current = current; this.title = title; this.redraw = redraw;
    }

    @Override public MenuScene render() {
        if (closed) return MenuScene.screen("plot-notice-reader").content(Ui.column().build()).build();
        var notice = current.get();
        if (notice == null || notice.body().isBlank()) return MenuScene.screen("plot-notice-reader").content(Ui.column().build()).build();
        var reading = PlotNoticeContent.reading(notice, position, next -> {
            if (!closed) { position = next; redraw.run(); }
        });
        return MenuScene.screen("plot-notice-reader", title.get()).content(reading).build();
    }

    @Override public void close() { closed = true; }
}
