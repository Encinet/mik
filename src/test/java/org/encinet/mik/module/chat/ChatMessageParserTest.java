package org.encinet.mik.module.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatMessageParserTest {

    @Test
    void convertsLegacyMojiraUrlBeforeGenericUrlRendering() {
        Component parsed = new ChatMessageParser().parse(
                sender(),
                "https://bugs.mojang.com/browse/MC-4",
                List.of(),
                "",
                "",
                ""
        );

        assertEquals("MC-4", PlainTextComponentSerializer.plainText().serialize(parsed));
    }

    @Test
    void rendersMinecraftWikiUrlWithItsDecodedPageTitle() {
        Component parsed = new ChatMessageParser().parse(
                sender(),
                "https://zh.minecraft.wiki/w/钻石",
                List.of(),
                "",
                "",
                ""
        );

        assertEquals("[Minecraft Wiki: 钻石]", PlainTextComponentSerializer.plainText().serialize(parsed));
    }

    private Player sender() {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if ("hasPermission".equals(method.getName())) {
                        return false;
                    }
                    return null;
                }
        );
    }
}
