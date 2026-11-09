package org.encinet.mik.feature.player.profile;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerIdentityComponentTest {

    @Test
    void sourcesAlwaysStayOutsideTheCompleteAccountNameTag() {
        PlayerIdentityComponent identity = new PlayerIdentityComponent(
                Component.text().append(Component.text("[Member] ")).append(Component.text("Alex"))
                        .append(Component.text(" [Tag]")).build());

        assertEquals("[Member] Alex [Tag]", plain(identity.component()));
        for (String label : new String[]{"基岩", "Bedrock", "网页", "Web", "Web ×3", "QQ", "Matrix"}) {
            var source = new PlayerSource(label);
            assertEquals("[" + label + "] [Member] Alex [Tag]", plain(identity.chat(source)));
            assertEquals("[Member] Alex [Tag] [" + label + "]", plain(identity.tabList(source)));
        }
        assertEquals(identity.component(), identity.chat(PlayerSource.NONE));
        assertEquals(identity.component(), identity.tabList(PlayerSource.NONE));
    }

    @Test
    void sourceLabelsDoNotInheritTheNamesClickOrHoverEvents() {
        Component name = Component.text("Alex").clickEvent(ClickEvent.suggestCommand("/tell Alex "))
                .hoverEvent(HoverEvent.showText(Component.text("Player details")));
        var identity = new PlayerIdentityComponent(name);
        var source = new PlayerSource("Web");
        assertSourceHasNoInteractions(identity.chat(source), false);
        assertSourceHasNoInteractions(identity.tabList(source), false);
    }

    @Test
    void rawNameTagsNormalizeMissingDecorations() {
        PlayerNameTag nameTag = new PlayerNameTag(null, null);

        assertEquals("", nameTag.prefix());
        assertEquals("", nameTag.suffix());
        assertEquals(PlayerNameTag.empty(), nameTag);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static void assertSourceHasNoInteractions(Component component, boolean inherited) {
        boolean interactive = inherited || component.clickEvent() != null || component.hoverEvent() != null;
        if (component instanceof TextComponent text && text.content().contains("[Web]")) assertFalse(interactive);
        for (Component child : component.children()) assertSourceHasNoInteractions(child, interactive);
    }
}
