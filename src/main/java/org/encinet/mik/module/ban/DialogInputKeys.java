package org.encinet.mik.module.ban;

import java.util.regex.Pattern;

final class DialogInputKeys {

    private static final Pattern VALID_KEY = Pattern.compile("^[a-z0-9_.]+$");

    private DialogInputKeys() {
    }

    static String requireValid(String key) {
        if (!VALID_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid dialog input key: " + key);
        }
        return key;
    }
}
