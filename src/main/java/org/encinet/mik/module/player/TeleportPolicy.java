package org.encinet.mik.module.player;

enum TeleportPolicy {
    ALWAYS_ALLOW("always-allow"),
    REQUIRE_CONSENT("require-consent"),
    ALWAYS_DENY("always-deny");

    private final String id;

    TeleportPolicy(String id) {
        this.id = id;
    }

    String id() {
        return id;
    }

    TeleportPolicy next() {
        return switch (this) {
            case ALWAYS_ALLOW -> REQUIRE_CONSENT;
            case REQUIRE_CONSENT -> ALWAYS_DENY;
            case ALWAYS_DENY -> ALWAYS_ALLOW;
        };
    }

    static TeleportPolicy fromId(String id) {
        if (id != null) {
            for (TeleportPolicy policy : values()) {
                if (policy.id.equalsIgnoreCase(id)) {
                    return policy;
                }
            }
        }
        return REQUIRE_CONSENT;
    }
}
