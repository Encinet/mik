package org.encinet.mik.shared.menu.ui;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** An identity segment, independent of labels, list positions and entity ids. */
public record UiKey(String value) {
    private static final int MAX_LENGTH = 512;
    public static final UiKey AUTO = new UiKey("$auto");
    public UiKey {
        if (value == null || value.isBlank() || value.length() > MAX_LENGTH || value.indexOf('/') >= 0) {
            throw new IllegalArgumentException("UI key must be a non-empty path segment");
        }
    }

    public static UiKey named(String value) { return new UiKey(value); }
    public static UiKey item(UUID id) { return new UiKey(Objects.requireNonNull(id, "id").toString()); }
    public static UiKey item(long id) { return new UiKey(Long.toString(id)); }
    /** Encodes domain identifiers as one bounded segment, including paths and literal percent escapes. */
    public static UiKey item(String id) {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) throw new IllegalArgumentException("UI item id must not be blank");
        String encoded = id.replace("%", "%25").replace("/", "%2F");
        if (encoded.length() <= MAX_LENGTH) return new UiKey(encoded);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8));
            // Ordinary identifiers can only produce %25 or %2F, so this namespace cannot alias them.
            return new UiKey("%hash:" + HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
