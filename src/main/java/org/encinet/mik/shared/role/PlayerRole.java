package org.encinet.mik.shared.role;

import org.bukkit.entity.Player;
import org.encinet.mik.shared.i18n.Message;

import java.util.Objects;
import java.util.function.Predicate;

/** Ordered MIK player roles exposed to external integrations. */
public enum PlayerRole {
    CUSTODIAN("custodian", Message.ROLE_CUSTODIAN),
    MODERATOR("moderator", Message.ROLE_MODERATOR),
    MEMBER("member", Message.ROLE_MEMBER),
    DEFAULT("default", Message.ROLE_NEW_PLAYER);

    private final String id;
    private final Message label;

    PlayerRole(String id, Message label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public Message label() {
        return label;
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
