package org.encinet.mik.module.identity;

import java.util.Objects;

/** An authenticated identity supplied by a platform adapter. */
public record ExternalIdentity(ExternalIdentityKey key, String displayName) {

    private static final int MAX_DISPLAY_NAME_LENGTH = 64;

    public ExternalIdentity {
        key = Objects.requireNonNull(key, "key");
        displayName = cleanDisplayName(displayName);
    }

    private static String cleanDisplayName(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder result = new StringBuilder(Math.min(value.length(), MAX_DISPLAY_NAME_LENGTH));
        boolean whitespace = false;
        for (int index = 0; index < value.length() && result.length() < MAX_DISPLAY_NAME_LENGTH;
             index++) {
            char character = value.charAt(index);
            if (Character.isWhitespace(character) || Character.isISOControl(character)) {
                if (!whitespace && !result.isEmpty()) {
                    result.append(' ');
                }
                whitespace = true;
            } else {
                result.append(character);
                whitespace = false;
            }
        }
        if (!result.isEmpty() && Character.isHighSurrogate(result.charAt(result.length() - 1))) {
            result.setLength(result.length() - 1);
        }
        return result.toString().strip();
    }
}
