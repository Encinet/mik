package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MenuTextBaselineTest {
    @Test void differentTypographySharesTheSamePublishedTextAlignmentLine() {
        var row = Ui.layout(UiLayouts.baselineRow(UiLength.ui(6), UiBaseline.Line.FIRST, LinearSlot.Alignment.END))
                .child(Ui.text(Component.text("Small")).key("small").style(UiStyle.PLAIN.withTextScale(.7)).build())
                .child(Ui.text(Component.text("Large")).key("large").style(UiStyle.PLAIN.withTextScale(1.8)).build()).build();
        var frame = ComponentSceneLayout.measure(MenuScene.screen("baselines").content(row).build(),
                1, FloatingMenuFieldOfView.DEFAULT, 1).frame();
        var first = frame.elements().get(0); var second = frame.elements().get(1);
        assertEquals(first.y() + first.baseline().orElseThrow().first(), second.y() + second.baseline().orElseThrow().first(), 1e-8);
        assertTrue(first.size().height() < second.size().height());
    }
}
