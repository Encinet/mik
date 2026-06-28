package org.encinet.mik.module.chat;

import java.util.Locale;

public enum ChatDelayOption {
    OFF("off", 0),
    THREE_SECONDS("3s", 3),
    FIVE_SECONDS("5s", 5),
    SEVEN_SECONDS("7s", 7);

    private final String id;
    private final int delaySeconds;

    ChatDelayOption(String id, int delaySeconds) {
        this.id = id;
        this.delaySeconds = delaySeconds;
    }

    String id() {
        return id;
    }

    int delaySeconds() {
        return delaySeconds;
    }

    static ChatDelayOption fromInput(String input) {
        if (input == null) {
            return null;
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if (normalized.matches("\\d+")) {
            normalized = normalized + "s";
        }
        for (ChatDelayOption option : values()) {
            if (option.id.equals(normalized)) {
                return option;
            }
        }
        return null;
    }
}
