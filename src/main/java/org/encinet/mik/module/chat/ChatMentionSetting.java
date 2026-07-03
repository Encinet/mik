package org.encinet.mik.module.chat;

public enum ChatMentionSetting {
    ALERTS("mention-alerts"),
    SOUND("mention-sound"),
    ACTION_BAR("mention-actionbar"),
    MUTE_WHILE_AFK("mute-mentions-while-afk");

    private final String id;

    ChatMentionSetting(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static ChatMentionSetting fromId(String id) {
        for (ChatMentionSetting setting : values()) {
            if (setting.id.equals(id)) {
                return setting;
            }
        }
        return null;
    }
}
