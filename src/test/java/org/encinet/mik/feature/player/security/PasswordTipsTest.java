package org.encinet.mik.feature.player.security;

import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.player.guidance.TipTestScope;
import org.encinet.mik.shared.role.PlayerRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PasswordTipsTest {
    @TempDir Path directory;
    private static final String PASSWORD = "river8642!";
    private final UUID id = UUID.randomUUID();
    private final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
            new Class<?>[]{Player.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "hasPermission" -> true;
                default -> throw new AssertionError(method.getName());
            });

    @Test
    void dailyAdviceOffersThePasswordCommandAndUsesTheSharedReminderInterval() {
        try (var accounts = open(); var tips = new TipTestScope()) {
            PasswordTips.register(tips, accounts);
            tips.join(player);
            tips.chat(player, "password");
            assertTrue(tips.collect(player, 0).isEmpty());
            var batch = tips.deliver(player, 15 * 60_000);
            assertEquals(1, batch.size());
            assertEquals("password-tip-set-mm", PlainTextComponentSerializer.plainText().serialize(batch.getFirst()));
            assertEquals(ClickEvent.suggestCommand("/password"), batch.getFirst().clickEvent());
            assertTrue(tips.deliver(player, 30 * 60_000).isEmpty());
        }
    }

    @Test
    void settingAPasswordBeforeTheDailyOpportunitySuppressesAdviceAndRemovalRestoresEligibility() {
        try (var accounts = open(); var tips = new TipTestScope()) {
            PasswordTips.register(tips, accounts);
            assertTrue(tips.collect(player, 0).isEmpty());
            accounts.set(id, "Alex", accounts.credential(id), PASSWORD, PASSWORD, () -> PlayerRole.MEMBER, null);
            assertTrue(tips.collect(player, 15 * 60_000).isEmpty());
            accounts.remove(id, accounts.credential(id), PASSWORD, () -> PlayerRole.MEMBER, null);
            assertEquals(1, tips.collect(player, 30 * 60_000).size());
        }
    }

    private PasswordService open() {
        var accounts = new PasswordService(directory.resolve("passwords.db"));
        accounts.open();
        accounts.retention(ignored -> false);
        return accounts;
    }
}
