package org.encinet.mik.shared.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerNamesTest {
    private final UUID id = new UUID(0, 1);

    @Test void offlineCasingAndRenamesComeFromTheUuidCacheRatherThanPermissionStorage() {
        var cache = new Cache();
        cache.name = "MixedCase_Name";
        assertEquals("MixedCase_Name", PlayerNames.resolve(cache.server(), id, "mixedcase_name"));
        cache.name = "RenamedPlayer";
        assertEquals("RenamedPlayer", PlayerNames.resolve(cache.server(), id, "mixedcase_name"));
    }

    @Test void liveLoginNameOverridesAnOlderCachedProfile() {
        var cache = new Cache();
        cache.online = "CurrentName";
        cache.name = "OldName";
        assertEquals("CurrentName", PlayerNames.resolve(cache.server(), id, "oldname"));
        assertEquals(0, cache.profiles);
    }

    @Test void missingOrUnrelatedProfilesKeepTheKnownFallbackWithoutInventingCasing() {
        var cache = new Cache();
        assertEquals("knownName", PlayerNames.resolve(cache.server(), id, "knownName"));
        assertEquals(id.toString(), PlayerNames.resolve(cache.server(), id, null));
        assertEquals(id.toString(), PlayerNames.resolve(cache.server(), id, " "));
        cache.name = " ";
        assertEquals("knownName", PlayerNames.resolve(cache.server(), id, "knownName"));
        cache.name = "WrongPlayer";
        cache.profileId = new UUID(0, 2);
        assertEquals("knownName", PlayerNames.resolve(cache.server(), id, "knownName"));
    }

    private final class Cache {
        UUID profileId = id;
        String online, name;
        int profiles;

        Server server() {
            return proxy(Server.class, (_, method, args) -> switch (method.getName()) {
                case "getPlayer" -> {
                    assertEquals(id, args[0]);
                    yield online == null ? null : proxy(Player.class, (_, call, _) -> {
                        assertEquals("getName", call.getName());
                        return online;
                    });
                }
                case "createProfile" -> {
                    assertEquals(1, args.length, "Do not seed a profile with the permission provider's name");
                    assertEquals(id, args[0]);
                    profiles++;
                    boolean[] completed = {false};
                    yield proxy(PlayerProfile.class, (_, call, _) -> switch (call.getName()) {
                        case "completeFromCache" -> { completed[0] = true; yield name != null; }
                        case "getName" -> { assertTrue(completed[0]); yield name; }
                        case "getId" -> profileId;
                        default -> throw new AssertionError("Unexpected profile access: " + call.getName());
                    });
                }
                default -> throw new AssertionError("No network lookup or offline player data: " + method.getName());
            });
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler calls) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, calls));
    }
}
