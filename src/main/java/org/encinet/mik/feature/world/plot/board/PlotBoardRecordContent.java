package org.encinet.mik.feature.world.plot.board;

import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.ui.*;

/** Written fields keep their complete source and independent reading anchors. */
final class PlotBoardRecordContent {
    private PlotBoardRecordContent() { }

    static UiNode reading(Component subject, Component metadata, Component description, Optional<Component> ruling,
                          UiReadingPosition position, Consumer<UiReadingPosition> read) {
        var blocks = new ArrayList<UiNode>();
        blocks.add(text("subject", subject));
        blocks.add(text("metadata", metadata));
        blocks.add(text("description", description));
        ruling.ifPresent(value -> blocks.add(text("ruling", value)));
        return Ui.document().key("record-reading").header(Ui.heading(subject).key("heading").summaryLines(1).tooltip(subject).build())
                .blocks(blocks).position(position, read).readingBody()
                .sizing(new UiSizing(UiSize.ZERO, new UiSize(600, 280))).build();
    }

    private static UiElement text(String key, Component value) {
        return Ui.text(value).key(key).textWidth(4096).alignment(FloatingMenuDecoration.Alignment.LEFT).keepAccessible().build();
    }
}
