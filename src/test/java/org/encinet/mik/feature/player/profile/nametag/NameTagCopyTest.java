package org.encinet.mik.feature.player.profile.nametag;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.player.profile.PlayerNameTag;
import org.encinet.mik.shared.i18n.FluentTestSupport;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class NameTagCopyTest {
    @Test void sendsLiteralSourceWithSeparateClipboardPayloadsWithoutAccessingPlayerStateOrStorage() throws IOException {
        String prefix = " <blue>%player_name% ";
        String suffix = " <click:run_command:'/example'>Suffix</click> ";
        var messages = send(new PlayerNameTag(prefix, suffix));
        assertEquals(3, messages.size());
        assertTrue(plain(messages.get(0)).contains("SourcePlayer"));
        assertTrue(plain(messages.get(1)).contains(prefix));
        assertTrue(plain(messages.get(2)).contains(suffix));
        assertEquals(List.of(ClickEvent.copyToClipboard(prefix), ClickEvent.copyToClipboard(suffix)),
                messages.stream().flatMap(NameTagCopyTest::clicks).toList());
    }

    @Test void missingFieldsAreShownAsUnsetWithoutACopyAction() throws IOException {
        var bundle = FluentTestSupport.bundle(Language.DEFAULT);
        String unset = FluentTestSupport.text(bundle, Message.NAMETAG_UNSET);
        for (var tag : List.of(new PlayerNameTag("Prefix", ""), new PlayerNameTag("", "Suffix"))) {
            var messages = send(tag);
            int emptyIndex = tag.prefix().isEmpty() ? 1 : 2;
            assertTrue(plain(messages.get(emptyIndex)).contains(unset));
            assertEquals(List.of(), clicks(messages.get(emptyIndex)).toList());
            assertEquals(1, messages.stream().flatMap(NameTagCopyTest::clicks).count());
        }
    }

    private List<Component> send(PlayerNameTag tag) throws IOException {
        var bundle = FluentTestSupport.bundle(Language.DEFAULT);
        var language = new LanguageService(null) {
            @Override public String t(Player player, Message message, Object... args) {
                return FluentTestSupport.text(bundle, message, args);
            }
            @Override public Component text(Player player, Message message, TextColor color, Object... args) {
                return Component.text(t(player, message, args), color);
            }
        };
        var messages = new ArrayList<Component>();
        var viewer = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (_, method, args) -> {
                    if (method.getName().equals("sendMessage") && args.length == 1 && args[0] instanceof Component message) {
                        messages.add(message);
                        return null;
                    }
                    throw new AssertionError("Copying must only send private messages: " + method.getName());
                });
        // Copying source needs no persistence, identity rendering, permission check or menu input lease.
        try (var menu = new NameTagMenu(null, language, null, null, null, null, null)) {
            menu.sendStyle(viewer, new NameTagStyle(UUID.randomUUID(), "SourcePlayer", tag));
            return messages;
        }
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static Stream<ClickEvent<?>> clicks(Component component) {
        return Stream.concat(Stream.ofNullable(component.clickEvent()), component.children().stream().flatMap(NameTagCopyTest::clicks));
    }
}
