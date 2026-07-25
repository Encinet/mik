package org.encinet.mik.module.music.disc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Signs server-owned online disc snapshots with a persistent local key. */
public final class MusicDiscSigner {

    private static final int KEY_BYTES = 32;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final SecretKeySpec key;

    public MusicDiscSigner(Path keyFile) {
        try {
            this.key = new SecretKeySpec(loadOrCreateKey(keyFile), HMAC_ALGORITHM);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize music disc signing key", exception);
        }
    }

    MusicDiscSigner(byte[] key) {
        if (key == null || key.length != KEY_BYTES) {
            throw new IllegalArgumentException("Music disc signing key must be 32 bytes");
        }
        this.key = new SecretKeySpec(key.clone(), HMAC_ALGORITHM);
    }

    public String sign(String snapshot) {
        if (snapshot == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    public boolean verify(String snapshot, String signature) {
        if (snapshot == null || signature == null || signature.length() > 64) {
            return false;
        }
        try {
            byte[] expected = Base64.getUrlDecoder().decode(sign(snapshot));
            byte[] actual = Base64.getUrlDecoder().decode(signature);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static byte[] loadOrCreateKey(Path value) throws IOException {
        Path keyFile = value.toAbsolutePath().normalize();
        Path directory = keyFile.getParent();
        if (directory == null) {
            throw new IOException("Music disc signing key has no parent directory");
        }
        Files.createDirectories(directory);
        if (Files.exists(keyFile, LinkOption.NOFOLLOW_LINKS)) {
            return readKey(keyFile);
        }

        byte[] generated = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(generated);
        Path temporary = Files.createTempFile(directory, ".music-disc-", ".key.tmp");
        try {
            Files.write(temporary, generated);
            try {
                Files.move(temporary, keyFile, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                try {
                    Files.move(temporary, keyFile);
                } catch (java.nio.file.FileAlreadyExistsException race) {
                    return readKey(keyFile);
                }
            } catch (java.nio.file.FileAlreadyExistsException exception) {
                return readKey(keyFile);
            }
            restrictPermissions(keyFile);
            return generated;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static byte[] readKey(Path keyFile) throws IOException {
        if (!Files.isRegularFile(keyFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Music disc signing key is not a regular file");
        }
        byte[] bytes = Files.readAllBytes(keyFile);
        if (bytes.length != KEY_BYTES) {
            throw new IOException("Music disc signing key must contain exactly 32 bytes");
        }
        return bytes;
    }

    private static void restrictPermissions(Path keyFile) {
        try {
            Files.setPosixFilePermissions(keyFile, java.util.Set.of(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
        } catch (IOException | UnsupportedOperationException ignored) {
        }
    }
}
