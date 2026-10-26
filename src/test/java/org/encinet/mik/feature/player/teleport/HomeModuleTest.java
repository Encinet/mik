package org.encinet.mik.feature.player.teleport;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.encinet.mik.feature.player.guidance.TipTestScope;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class HomeModuleTest {
    private static final LanguageService LANGUAGES = new LanguageService(null) {
        @Override
        public Component text(Language language, Message message, TextColor color, Object... args) {
            return Component.text(message.key(), color);
        }
    };
    @TempDir Path directory;
    private final UUID id = UUID.randomUUID();
    private final AtomicReference<Location> position = new AtomicReference<>(new Location(null, 0, 64, 0));
    private final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
            new Class<?>[]{Player.class}, (proxy, method, args) -> {
                if (method.getName().equals("getUniqueId")) return id;
                if (method.getName().equals("getLocation")) return position.get().clone();
                throw new AssertionError(method.getName());
            });

    @Test
    void savedHomeAddedDuringTheDelayCancelsTheCreationReminder() {
        try (HomeStore store = store(); TipTestScope tips = new TipTestScope()) {
            new HomeModule(null, LANGUAGES, tips, store);
            teleport(tips);
            store.put(id, "base", new HomeStore.Entry("world:0,64,0,0,0", null));
            assertTrue(tips.collect(player, 2000).isEmpty());

            store.remove(id, "base");
            teleport(tips);
            var batch = tips.collect(player, 4000);
            assertEquals(1, batch.size(), "a player without homes can be reminded again after deleting them");
            assertEquals(ClickEvent.suggestCommand("/sethome "), batch.getFirst().clickEvent());
        }
    }

    @Test
    void playersWithHomesReceiveNeitherTeleportNorChatCreationReminders() {
        try (HomeStore store = store(); TipTestScope tips = new TipTestScope()) {
            new HomeModule(null, LANGUAGES, tips, store);
            store.put(id, "base", new HomeStore.Entry("world:0,64,0,0,0", null));
            teleport(tips);
            tips.chat(player, "how do I set a home?");
            assertTrue(tips.collect(player, 2000).isEmpty());
        }
    }

    @Test
    void missingHomesDoNotBypassTheTeleportProbability() {
        try (HomeStore store = store(); TipTestScope tips = new TipTestScope(() -> 0.1)) {
            new HomeModule(null, LANGUAGES, tips, store);
            teleport(tips);
            assertTrue(tips.collect(player, 2000).isEmpty());
        }
    }

    private void teleport(TipTestScope tips) {
        Location destination = new Location(null, position.get().getX() + 100, 70, position.get().getZ() + 100);
        tips.teleport(new PlayerTeleportEvent(player, position.get().clone(), destination,
                PlayerTeleportEvent.TeleportCause.COMMAND));
        position.set(destination);
        tips.flush();
    }

    private HomeStore store() {
        return new HomeStore(directory.resolve("homes.yml"), Logger.getAnonymousLogger());
    }
}
