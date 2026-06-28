package org.encinet.mik.module.pvp;

import org.bukkit.Material;
import org.encinet.mik.module.i18n.Message;

enum PvpSettingKey {
    ENABLED("enabled", Message.PVP_ENABLE, Message.PVP_ENABLE_DESC, Material.IRON_SWORD, Material.WOODEN_SWORD),
    PROTECT_MOBS("protect-mobs", Message.PVP_PROTECT_MOBS, Message.PVP_PROTECT_MOBS_DESC, Material.SHIELD, Material.GRAY_DYE),
    ALLOW_MOUNTED_DAMAGE("allow-mounted-mob-damage", Message.PVP_ALLOW_MOUNTED_DAMAGE, Message.PVP_ALLOW_MOUNTED_DAMAGE_DESC, Material.SADDLE, Material.GRAY_DYE),
    ENABLE_ON_DEATH("enable-on-death", Message.PVP_ENABLE_ON_DEATH, Message.PVP_ENABLE_ON_DEATH_DESC, Material.TOTEM_OF_UNDYING, Material.GRAY_DYE);

    private final String id;
    private final Message label;
    private final Message description;
    private final Material enabledMaterial;
    private final Material disabledMaterial;

    PvpSettingKey(String id, Message label, Message description, Material enabledMaterial, Material disabledMaterial) {
        this.id = id;
        this.label = label;
        this.description = description;
        this.enabledMaterial = enabledMaterial;
        this.disabledMaterial = disabledMaterial;
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

    Material enabledMaterial() {
        return enabledMaterial;
    }

    Material disabledMaterial() {
        return disabledMaterial;
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
