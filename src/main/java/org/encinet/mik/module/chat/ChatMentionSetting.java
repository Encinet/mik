package org.encinet.mik.module.chat;

import org.bukkit.Material;
import org.encinet.mik.module.i18n.Message;

enum ChatMentionSetting {
    ALERTS("mention-alerts", Message.MENTION_ALERTS, Message.MENTION_ALERTS_DESC, Material.LIME_DYE, Material.GRAY_DYE),
    SOUND("mention-sound", Message.MENTION_SOUND, Message.MENTION_SOUND_DESC, Material.NOTE_BLOCK, Material.GRAY_DYE),
    ACTION_BAR("mention-actionbar", Message.MENTION_ACTION_BAR, Message.MENTION_ACTION_BAR_DESC, Material.PAPER, Material.GRAY_DYE),
    MUTE_WHILE_AFK("mute-mentions-while-afk", Message.MENTION_MUTE_AFK, Message.MENTION_MUTE_AFK_DESC, Material.CLOCK, Material.GRAY_DYE);

    private final String id;
    private final Message label;
    private final Message description;
    private final Material enabledMaterial;
    private final Material disabledMaterial;

    ChatMentionSetting(String id, Message label, Message description, Material enabledMaterial, Material disabledMaterial) {
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

    static ChatMentionSetting fromId(String id) {
        for (ChatMentionSetting setting : values()) {
            if (setting.id.equals(id)) {
                return setting;
            }
        }
        return null;
    }
}
