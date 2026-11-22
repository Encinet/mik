package org.encinet.mik.feature.community.ai.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AiWebLinksTest {
    @Test
    void keepsTheAuthoredLabelAndShowsTheDestinationInBasicChat() {
        var url = "https://modrinth.com/mod/sodium/versions";
        var label = Component.text("Compatible releases");
        var nativeLine = AiWebLinks.render(label, "Compatible releases", url, true);
        var basicLine = AiWebLinks.render(label, "Compatible releases", url, false);
        var plain = PlainTextComponentSerializer.plainText();
        assertEquals("Compatible releases", plain.serialize(nativeLine));
        assertEquals(ClickEvent.openUrl(url), nativeLine.clickEvent());
        assertEquals("Compatible releases · " + url, plain.serialize(basicLine));
        assertNull(basicLine.clickEvent());
        assertEquals(url, plain.serialize(AiWebLinks.render(Component.text(url), url, url, false)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://modrinth.com/mod/sodium/versions",
            "https://mcmik.top@modrinth.com/mod/sodium/versions",
            "https://modrinth.com:443/mod/sodium/versions",
            "https://127.0.0.1/",
            "https://wiki.local/",
            "https://modrinth.com\\@localhost/"
    })
    void rejectsUnsafeDestinations(String url) {
        var label = Component.text("Compatible releases");
        assertEquals(label, AiWebLinks.render(label, "Compatible releases", url, true));
        assertEquals(label, AiWebLinks.render(label, "Compatible releases", url, false));
    }
}
