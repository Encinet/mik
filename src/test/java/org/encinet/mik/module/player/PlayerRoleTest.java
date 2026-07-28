package org.encinet.mik.module.player;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerRoleTest {

    @Test
    void resolvesRolesInDescendingOrder() {
        assertEquals(PlayerRole.MANAGER, PlayerRole.resolve(permissions("member", "helper", "manager")::contains));
        assertEquals(PlayerRole.HELPER, PlayerRole.resolve(permissions("member", "helper")::contains));
        assertEquals(PlayerRole.MEMBER, PlayerRole.resolve(permissions("member")::contains));
        assertEquals(PlayerRole.DEFAULT, PlayerRole.resolve(permissions()::contains));
    }

    @Test
    void exposesStableLowercaseIds() {
        assertEquals("manager", PlayerRole.MANAGER.id());
        assertEquals("helper", PlayerRole.HELPER.id());
        assertEquals("member", PlayerRole.MEMBER.id());
        assertEquals("default", PlayerRole.DEFAULT.id());
    }

    private static Set<String> permissions(String... groups) {
        return Set.of(groups).stream()
                .map(group -> "group." + group)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
