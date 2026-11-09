package org.encinet.mik.feature.player.pvp;

import org.encinet.mik.shared.i18n.Message;

enum PvpSettingKey {
    ENABLED("enabled", Message.PVP_ENABLE, Message.PVP_ENABLE_DESC),
    PROTECT_MOBS("protect-mobs", Message.PVP_PROTECT_MOBS, Message.PVP_PROTECT_MOBS_DESC),
    ALLOW_MOUNTED_DAMAGE("allow-mounted-mob-damage", Message.PVP_ALLOW_MOUNTED_DAMAGE, Message.PVP_ALLOW_MOUNTED_DAMAGE_DESC),
    ENABLE_ON_DEATH("enable-on-death", Message.PVP_ENABLE_ON_DEATH, Message.PVP_ENABLE_ON_DEATH_DESC);

    private final String id;
    private final Message label;
    private final Message description;

    PvpSettingKey(String id, Message label, Message description) {
        this.id = id;
        this.label = label;
        this.description = description;
    }

    String id() {
        return id;
    }

    Message label() {
        return label;
    }

    Message description() {
        return description;
    }

    static PvpSettingKey fromId(String id) {
        for (PvpSettingKey key : values()) {
            if (key.id.equals(id)) {
                return key;
            }
        }
        return null;
    }
}
