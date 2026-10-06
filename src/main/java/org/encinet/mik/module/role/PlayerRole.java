package org.encinet.mik.module.role;

import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.function.Predicate;

/** Ordered MIK player roles exposed to external integrations. */
public enum PlayerRole {
    CUSTODIAN("custodian"),
    MODERATOR("moderator"),
    MEMBER("member"),
    DEFAULT("default");

    private final String id;

    PlayerRole(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String permission() {
        return "group." + id;
    }

    public static PlayerRole resolve(Player player) {
        Objects.requireNonNull(player, "player");
        return resolve(player::hasPermission);
    }

    static PlayerRole resolve(Predicate<String> hasPermission) {
        Objects.requireNonNull(hasPermission, "hasPermission");
        if (hasPermission.test(CUSTODIAN.permission())) {
            return CUSTODIAN;
        }
        if (hasPermission.test(MODERATOR.permission())) {
            return MODERATOR;
        }
        if (hasPermission.test(MEMBER.permission())) {
            return MEMBER;
        }
        return DEFAULT;
    }
}
