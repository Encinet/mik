package org.encinet.mik.module.player;

import org.bukkit.entity.Player;
import org.encinet.mik.Mik;

import java.util.Objects;
import java.util.function.Predicate;

/** Ordered MIK player roles exposed to external integrations. */
public enum PlayerRole {
    MANAGER(Mik.GROUP_MANAGER),
    HELPER(Mik.GROUP_HELPER),
    MEMBER(Mik.GROUP_MEMBER),
    DEFAULT("default");

    private final String id;

    PlayerRole(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static PlayerRole resolve(Player player) {
        Objects.requireNonNull(player, "player");
        return resolve(player::hasPermission);
    }

    static PlayerRole resolve(Predicate<String> hasPermission) {
        Objects.requireNonNull(hasPermission, "hasPermission");
        if (hasPermission.test("group." + Mik.GROUP_MANAGER)) {
            return MANAGER;
        }
        if (hasPermission.test("group." + Mik.GROUP_HELPER)) {
            return HELPER;
        }
        if (hasPermission.test("group." + Mik.GROUP_MEMBER)) {
            return MEMBER;
        }
        return DEFAULT;
    }
}
