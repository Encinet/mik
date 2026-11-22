package org.encinet.mik.feature.community.ai.chat;

import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.encinet.mik.protocol.AssistantPart;
import org.encinet.mik.protocol.AssistantSpan;
import org.encinet.mik.protocol.AssistantSpan.Style;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class AiAnswerTextTest {
    private static final NamespacedKey IRON_BLOCK = NamespacedKey.minecraft("iron_block");

    @Test
    void styleAndHoverComposeAcrossTheWholeLabel() {
        var part = AssistantPart.newBuilder().setItemId(IRON_BLOCK.asString())
                .addSpans(span("铁").addStyles(Style.BOLD)).addSpans(span("块").addStyles(Style.ITALIC)).build();
        var reply = AiAnswerText.render(part, true, "", IRON_BLOCK::equals);

        assertEquals("铁块", PlainTextComponentSerializer.plainText().serialize(reply));
        assertEquals(TextDecoration.State.TRUE, reply.decoration(TextDecoration.UNDERLINED));
        assertEquals(HoverEvent.showItem(IRON_BLOCK, 1), reply.hoverEvent());
        assertEquals(NamedTextColor.GOLD, reply.children().getFirst().color());
        assertEquals(TextDecoration.State.TRUE, reply.children().getFirst().decoration(TextDecoration.BOLD));
        assertEquals(TextDecoration.State.TRUE, reply.children().getLast().decoration(TextDecoration.ITALIC));
        assertNull(reply.clickEvent());
        assertNull(AiAnswerText.render(part, false, "", _ -> fail("Basic chat does not consult the registry")).hoverEvent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"minecraft:unknown_item", "iron_block", "other:iron_block", "minecraft:air",
            "minecraft:iron_block[custom_name='click']"})
    void badItemHintsKeepExplicitEmphasisWithoutInteraction(String id) {
        var part = AssistantPart.newBuilder().addSpans(span("🔧"))
                .addSpans(span("铁块").addStyles(Style.BOLD)).setItemId(id).build();
        var reply = AiAnswerText.render(part, true, "", key -> {
            assertEquals("minecraft:unknown_item", key.asString());
            return false;
        });

        assertEquals("🔧铁块", PlainTextComponentSerializer.plainText().serialize(reply));
        var item = reply.children().getLast();
        assertEquals(NamedTextColor.GOLD, item.color());
        assertEquals(TextDecoration.State.TRUE, item.decoration(TextDecoration.BOLD));
        assertNull(reply.hoverEvent());
        assertNull(reply.clickEvent());
    }

    @Test
    void literalCommandsHaveStyleWithoutInteraction() {
        var part = AssistantPart.newBuilder().addSpans(span("/home base").addStyles(Style.CODE)).build();
        var reply = AiAnswerText.render(part, true, "", IRON_BLOCK::equals);

        assertEquals("/home base", PlainTextComponentSerializer.plainText().serialize(reply));
        assertEquals(NamedTextColor.GREEN, reply.children().getFirst().color());
        assertNull(reply.clickEvent());
        assertTrue(reply.children().stream().noneMatch(child -> child.clickEvent() != null || child.hoverEvent() != null));
    }

    private static AssistantSpan.Builder span(String text) {
        return AssistantSpan.newBuilder().setText(text);
    }
}
