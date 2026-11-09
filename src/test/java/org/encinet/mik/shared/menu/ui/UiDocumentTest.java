package org.encinet.mik.shared.menu.ui;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiDocumentTest {
    @Test void theGenericMeasurerCanSplitParagraphsWithoutAnEngineSpecificPaginationHook() {
        var position = new AtomicReference<>(UiReadingPosition.START);
        String source = "A界e\u0301🌿".repeat(12);
        var document = Ui.document().blocks(List.of(Ui.text(Component.text(source)).key("body").build()))
                .position(position.get(), position::set).build();
        UiCompiler.ElementMeasurer measurer = (node, _) -> {
            if (node.content() instanceof UiContent.Icon) return new UiCompiler.ElementMeasurement(new UiSize(10,10),100);
            String plain = PlainTextComponentSerializer.plainText().serialize(node.content().label());
            int count = plain.codePointCount(0,plain.length());
            return new UiCompiler.ElementMeasurement(new UiSize(40, Math.max(10, Math.ceil(count / 8.0) * 10)),100);
        };
        var frame = UiCompiler.compile(document, UiConstraints.bounded(120,50),measurer);
        assertTrue(frame.pagination().getFirst().page().count() > 1);
        assertNotEquals(source, PlainTextComponentSerializer.plainText().serialize(UiSceneTestSupport.element(frame,"body").node().content().label()));
        frame.pagination().getFirst().selectPage().accept(1);
        assertTrue(position.get().offset() > 0);
        UiSceneTestSupport.assertDisjoint(frame);
    }

    @Test void readingBlocksRequireStableIdentitiesAndAnExplicitOwner() {
        assertThrows(NullPointerException.class, () -> Ui.document().blocks(List.of()).build());
        assertThrows(IllegalArgumentException.class, () -> Ui.document().blocks(List.of(Ui.text(Component.text("Anonymous")).build()))
                .position(UiReadingPosition.START, _ -> {}).build());
        assertThrows(IllegalArgumentException.class, () -> new UiReadingPosition(UiKey.named("body"), -1, 0));
    }

    @Test void emptyOptionalBlocksDoNotReserveWidthOrAGapBelowTheHeader() {
        var empty = Ui.document().header(Ui.text(Component.text("Header")).build())
                .blocks(List.of(Ui.column().key("optional").build())).position(UiReadingPosition.START, _ -> {}).build();
        var frame = UiCompiler.compile(empty, UiConstraints.bounded(100,10),
                (_, _) -> new UiCompiler.ElementMeasurement(new UiSize(40,10),100));
        assertEquals(new UiSize(40,10), frame.size());
        assertEquals(1, frame.elements().size());
    }

    @Test void styledSlicesKeepInheritedAndOverriddenFormattingAcrossSurrogates() {
        var font = net.kyori.adventure.key.Key.key("test", "font");
        var source = Component.text("A", net.kyori.adventure.text.format.NamedTextColor.GOLD)
                .font(font).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)
                .append(Component.text("🌿界", net.kyori.adventure.text.format.NamedTextColor.AQUA));
        var text = UiDocumentText.literal(source).orElseThrow();
        assertEquals(3, text.length());
        var part = text.slice(1, 3);
        assertEquals("🌿界", PlainTextComponentSerializer.plainText().serialize(part));
        var run = part.children().isEmpty() ? part : part.children().getFirst();
        assertEquals(font, run.font());
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.AQUA, run.color());
        assertEquals(net.kyori.adventure.text.format.TextDecoration.State.TRUE,
                run.decoration(net.kyori.adventure.text.format.TextDecoration.BOLD));
        assertTrue(UiDocumentText.literal(Component.translatable("item.minecraft.stone")).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> UiDocumentText.literal(Component.text("x".repeat(65537))));
    }
}
