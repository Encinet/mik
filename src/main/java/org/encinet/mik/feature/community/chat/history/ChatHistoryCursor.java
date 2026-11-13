package org.encinet.mik.feature.community.chat.history;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

/** Randomized authenticated cursors. Neither row counts nor visibility sets enter the wire contract. */
final class ChatHistoryCursor {
    record Position(long after, long head, long before) { }
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;
    private final UUID database;

    ChatHistoryCursor(String secret, String database) {
        byte[] bytes = Base64.getUrlDecoder().decode(secret);
        if (bytes.length != 32) throw new IllegalArgumentException("Invalid history cursor key");
        key = new SecretKeySpec(bytes, "AES");
        Arrays.fill(bytes, (byte) 0);
        this.database = UUID.fromString(database);
    }

    String seal(Position position, String binding) {
        byte[] nonce = new byte[12]; RANDOM.nextBytes(nonce);
        byte[] clear = ByteBuffer.allocate(40).putLong(database.getMostSignificantBits()).putLong(database.getLeastSignificantBits())
                .putLong(position.after()).putLong(position.head()).putLong(position.before()).array();
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(binding.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(clear);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
        } catch (GeneralSecurityException error) { throw new IllegalStateException("Could not seal history cursor", error); }
        finally { Arrays.fill(clear, (byte) 0); }
    }

    Position open(String token, String binding) {
        if (token == null) return null;
        byte[] clear = null;
        try {
            if (!token.matches("[A-Za-z0-9_-]{91}")) return null;
            byte[] packet = Base64.getUrlDecoder().decode(token);
            if (!Base64.getUrlEncoder().withoutPadding().encodeToString(packet).equals(token)) return null;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, packet, 0, 12));
            cipher.updateAAD(binding.getBytes(StandardCharsets.UTF_8));
            clear = cipher.doFinal(packet, 12, packet.length - 12);
            ByteBuffer data = ByteBuffer.wrap(clear);
            if (!database.equals(new UUID(data.getLong(), data.getLong()))) return null;
            Position position = new Position(data.getLong(), data.getLong(), data.getLong());
            return position.after() >= 0 && position.head() >= position.after() && position.before() > position.after()
                    ? position : null;
        } catch (GeneralSecurityException | IllegalArgumentException invalid) { return null; }
        finally { if (clear != null) Arrays.fill(clear, (byte) 0); }
    }
}
