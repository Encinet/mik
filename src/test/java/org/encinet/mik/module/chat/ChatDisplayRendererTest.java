package org.encinet.mik.module.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatDisplayRendererTest {

    @Test
    void clickingPlayerNameSuggestsTellCommand() {
        Component name = ChatDisplayRenderer.clickablePlayerName(Component.text("TestPlayer"), "TestPlayer");

        assertEquals(ClickEvent.suggestCommand("/tell TestPlayer "), name.clickEvent());
    }
}
