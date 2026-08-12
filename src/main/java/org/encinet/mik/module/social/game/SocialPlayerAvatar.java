package org.encinet.mik.module.social.game;

import java.util.Arrays;
import java.util.Objects;

/** Locally rendered PNG avatar for a linked Minecraft player. */
public record SocialPlayerAvatar(byte[] pngData) {
    private static final int MAXIMUM_BYTES = 1_048_576;
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a
    };

    public SocialPlayerAvatar {
        Objects.requireNonNull(pngData, "pngData");
        if (pngData.length < PNG_SIGNATURE.length || pngData.length > MAXIMUM_BYTES
                || !Arrays.equals(PNG_SIGNATURE,
                Arrays.copyOf(pngData, PNG_SIGNATURE.length))) {
            throw new IllegalArgumentException("avatar must be a PNG no larger than 1 MiB");
        }
        pngData = Arrays.copyOf(pngData, pngData.length);
    }

    @Override
    public byte[] pngData() {
        return Arrays.copyOf(pngData, pngData.length);
    }
}
