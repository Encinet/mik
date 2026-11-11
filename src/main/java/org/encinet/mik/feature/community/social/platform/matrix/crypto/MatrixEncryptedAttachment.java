package org.encinet.mik.feature.community.social.platform.matrix.crypto;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Matrix v2 attachment envelope; only ciphertext may be uploaded to the media repository. */
public record MatrixEncryptedAttachment(byte[] ciphertext, JsonObject file) {
    public static MatrixEncryptedAttachment encrypt(byte[] plaintext) {
        try {
            SecureRandom random = new SecureRandom();
            byte[] key = new byte[32];
            byte[] iv = new byte[16];
            random.nextBytes(key);
            byte[] nonce = new byte[8];
            random.nextBytes(nonce);
            System.arraycopy(nonce, 0, iv, 0, nonce.length);
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            JsonObject jwk = new JsonObject();
            jwk.addProperty("kty", "oct");
            jwk.addProperty("alg", "A256CTR");
            jwk.addProperty("ext", true);
            jwk.addProperty("k", Base64.getUrlEncoder().withoutPadding().encodeToString(key));
            JsonArray operations = new JsonArray();
            operations.add("encrypt");
            operations.add("decrypt");
            jwk.add("key_ops", operations);
            JsonObject hashes = new JsonObject();
            hashes.addProperty("sha256", base64(MessageDigest.getInstance("SHA-256").digest(ciphertext)));
            JsonObject file = new JsonObject();
            file.addProperty("v", "v2");
            file.add("key", jwk);
            file.addProperty("iv", base64(iv));
            file.add("hashes", hashes);
            return new MatrixEncryptedAttachment(ciphertext, file);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Could not encrypt Matrix attachment", error);
        }
    }

    public JsonObject at(String contentUri) {
        JsonObject result = file.deepCopy();
        result.addProperty("url", contentUri);
        return result;
    }

    private static String base64(byte[] value) {
        return Base64.getEncoder().withoutPadding().encodeToString(value);
    }
}
