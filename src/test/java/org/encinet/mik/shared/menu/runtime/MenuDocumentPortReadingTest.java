package org.encinet.mik.shared.menu.runtime;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MenuDocumentPortReadingTest {
    @Test void declaredParagraphsAreReadInFullAndTheFinalActionKeepsItsBinding() {
        var read = new AtomicReference<>(UiReadingPosition.START); var completed = new AtomicInteger();
        String source = "甲乙丙丁戊己庚辛壬癸".repeat(80);
        var visible = new StringBuilder();
        for (int pass = 0; pass < 100; pass++) {
            var port = new UiPort(UiKey.named("reader"),UiLayouts.column(UiLength.ui(8)),UiStyle.PLAIN,UiSizing.CONTENT,
                    List.of("paragraphs","actions"),UiRole.TEXT);
            var builder = MenuScene.screen("reading").content(port.document(read.get(),read::set).readingBody());
            builder.information("body",Component.text(source)).region("paragraphs");
            builder.control("complete",Component.text("Complete")).region("actions").primary((_,_) -> completed.incrementAndGet());
            var frame = SceneGeometryTestSupport.readableFrame(builder.build(),1.8,new FloatingMenuFieldOfView(30),1.3);
            assertDisjoint(frame);
            for (var item : frame.elements()) if (item.node().key().value().equals("body"))
                visible.append(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(item.node().content().label()));
            var page = frame.pagination().getFirst();
            if (!page.page().hasNext()) {
                element(frame,"complete").node().input().actions().get(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
                break;
            }
            assertEquals(0,completed.get());
            page.selectPage().accept(page.page().next().index());
        }
        assertEquals(source,visible.toString()); assertEquals(1,completed.get());
    }
}
