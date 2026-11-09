package org.encinet.mik.shared.menu.ui;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class UiContentLinearLayoutTest {
    @Test void remainingSpaceIsABudgetRatherThanAPhantomRegionOnEitherAxis() {
        for (boolean horizontal : new boolean[]{false,true}) for (double extent : new double[]{400,40,160,400}) {
            var layout = horizontal ? UiLayouts.contentRow(UiKey.named("body"),UiLength.ui(12))
                    : UiLayouts.contentColumn(UiKey.named("body"),UiLength.ui(12));
            var tree = Ui.layout(layout).child(box("navigation",size(horizontal,80,100)))
                    .child(UiKey.named("body"),box("body",size(horizontal,extent,40)),
                            new LinearSlot(0,LinearSlot.Alignment.START))
                    .child(box("tools",size(horizontal,20,60))).build();
            var frame = measure(tree,horizontal,600,200);
            assertEquals(size(horizontal,extent + 124,100),frame.size());
            var body = element(frame,"body");
            var tools = element(frame,"tools");
            assertEquals(92,horizontal ? body.x() : body.y(),1e-6);
            assertEquals(extent + 104,horizontal ? tools.x() : tools.y(),1e-6);
            assertDisjoint(frame);
        }
    }

    @Test void theBodyUsesOnlyTheSpaceLeftByPersistentRegionsRegardlessOfItsOrder() {
        for (boolean horizontal : new boolean[]{false,true}) for (boolean bodyFirst : new boolean[]{false,true}) {
            var layout = horizontal ? UiLayouts.contentRow(UiKey.named("body"),UiLength.ui(12))
                    : UiLayouts.contentColumn(UiKey.named("body"),UiLength.ui(12));
            UiNode body = Ui.text(Component.text("Body")).key("body").build();
            UiNode rail = box("navigation",size(horizontal,80,100));
            var tree = Ui.layout(layout).children(bodyFirst ? List.of(body,rail) : List.of(rail,body)).build();
            var frame = measure(tree,horizontal,200,200);
            assertEquals(size(horizontal,108,40),element(frame,"body").size());
            assertEquals(size(horizontal,200,100),frame.size());
            assertDisjoint(frame);
            assertThrows(UiOverflowException.class,() -> measure(tree,horizontal,70,200));
        }
    }

    @Test void anEmptyBodyAddsNoGapAndExplicitHostSizingStillReservesSpace() {
        for (boolean horizontal : new boolean[]{false,true}) {
            var layout = horizontal ? UiLayouts.contentRow(UiKey.named("body"),UiLength.ui(12))
                    : UiLayouts.contentColumn(UiKey.named("body"),UiLength.ui(12));
            var tree = Ui.layout(layout).child(box("navigation",size(horizontal,80,100)))
                    .child(UiKey.named("body"),Ui.stack().build()).build();
            assertEquals(size(horizontal,80,100),measure(tree,horizontal,200,200).size());
            var fixed = Ui.stack().size(size(horizontal,200,160)).child(tree).build();
            assertEquals(size(horizontal,200,160),measure(fixed,horizontal,200,200).size());
        }
    }

    private static UiElement box(String key, UiSize size) {
        return Ui.text(Component.empty()).key(key).size(size).build();
    }

    private static UiSize size(boolean horizontal, double main, double cross) {
        return horizontal ? new UiSize(main,cross) : new UiSize(cross,main);
    }

    private static UiFrame measure(UiNode node, boolean horizontal, double main, double cross) {
        var viewport = size(horizontal,main,cross);
        return UiCompiler.compile(node,UiConstraints.bounded(viewport.width(),viewport.height()),(_,limits) ->
                new UiCompiler.ElementMeasurement(size(horizontal,Math.min(400,horizontal ? limits.maxWidth() : limits.maxHeight()),
                        Math.min(40,horizontal ? limits.maxHeight() : limits.maxWidth())),100));
    }
}
