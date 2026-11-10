package org.encinet.mik.shared.role;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerRoleTest {

    @Test
    void resolvesRolesInDescendingOrder() {
        assertEquals(PlayerRole.CUSTODIAN, PlayerRole.resolve(permissions("member", "moderator", "custodian")::contains));
        assertEquals(PlayerRole.MODERATOR, PlayerRole.resolve(permissions("member", "moderator")::contains));
        assertEquals(PlayerRole.MEMBER, PlayerRole.resolve(permissions("member")::contains));
        assertEquals(PlayerRole.DEFAULT, PlayerRole.resolve(permissions()::contains));
        assertEquals(PlayerRole.DEFAULT,
                PlayerRole.resolve(permissions("manager", "helper")::contains));
    }

    private static Set<String> permissions(String... groups) {
        return Set.of(groups).stream()
                .map(group -> "group." + group)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
