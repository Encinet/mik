package org.encinet.mik.feature.world.plot;

import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.FloatingMenuCommand;
import org.encinet.mik.shared.menu.ui.*;

/** Uses feature-owned notice content in host composition and geometry tests. */
public final class PlotNoticeTestSupport {
    private PlotNoticeTestSupport() { }
    public static UiNode preview(PlotNoticeBoard notice, Component readFull, FloatingMenuCommand open) {
        return PlotNoticeContent.preview(notice, readFull, UiKey.named("notice-read"), open);
    }
    public static UiNode reading(PlotNoticeBoard notice, UiReadingPosition position, Consumer<UiReadingPosition> select) {
        return PlotNoticeContent.reading(notice, position, select);
    }
}
