package org.encinet.mik.feature.community.chat.web;

import com.google.gson.JsonObject;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.hpke.HPKE;
import org.bouncycastle.crypto.hpke.HPKEContext;
import org.bouncycastle.crypto.hpke.HPKEContextWithEncapsulation;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.signers.DSADigestSigner;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.bouncycastle.crypto.signers.PlainDSAEncoding;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;

/** HPKE contexts and the persistent signing identity. Private keys never enter the wire contract. */
final class WebChatEncryption {
    static final byte[] EMPTY = new byte[0];
    static final byte[] READY = "ready".getBytes(StandardCharsets.UTF_8);
    private static final String PROTOCOL = "mik-chat-hpke-2026-11-08\0https://mcmik.top\0";
    private final ECPrivateKeyParameters identity;

    private WebChatEncryption(ECPrivateKeyParameters identity) { this.identity = identity; }

    static WebChatEncryption generate() {
        return new WebChatEncryption((ECPrivateKeyParameters) suite(HPKE.mode_base).generatePrivateKey().getPrivate());
    }

    static WebChatEncryption load(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        if (!Files.exists(path)) {
            WebChatEncryption generated = generate();
            byte[] encoded = PrivateKeyInfoFactory.createPrivateKeyInfo(generated.identity).getEncoded();
            try (var file = Files.newByteChannel(path, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
                ByteBuffer bytes = ByteBuffer.wrap(encoded);
                while (bytes.hasRemaining()) file.write(bytes);
            } finally { Arrays.fill(encoded, (byte) 0); }
            return generated;
        }
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        byte[] encoded;
        try (var input = Files.newInputStream(path)) { encoded = input.readNBytes(1025); }
        try {
            if (encoded.length > 1024 || !(PrivateKeyFactory.createKey(encoded) instanceof ECPrivateKeyParameters key))
                throw new IOException("Invalid web chat signing key");
            var curve = org.bouncycastle.crypto.ec.CustomNamedCurves.getByName("secp256r1");
            if (!key.getParameters().getN().equals(curve.getN()) || !key.getParameters().getG().equals(curve.getG()))
                throw new IOException("Invalid web chat signing curve");
            return new WebChatEncryption(key);
        } catch (RuntimeException invalid) {
            throw new IOException("Could not load web chat signing key", invalid);
        } finally { Arrays.fill(encoded, (byte) 0); }
    }

    String publicKey() {
        var publicKey = new ECPublicKeyParameters(identity.getParameters().getG().multiply(identity.getD()), identity.getParameters());
        return encode(suite(HPKE.mode_base).serializePublicKey(publicKey));
    }

    Session session(String connectionId, long generation, String clientPublicKey) {
        return new Session(connectionId, generation, clientPublicKey);
    }

    static HPKE suite(byte mode) {
        return new HPKE(mode, HPKE.kem_P256_SHA256, HPKE.kdf_HKDF_SHA256, HPKE.aead_AES_GCM256);
    }

    static byte[] info(String id, long generation, String clientPublicKey, String direction) {
        if (!id.matches("[A-Za-z0-9_-]{32}") || generation < 0 || generation > 9_007_199_254_740_991L)
            throw new IllegalArgumentException("Invalid chat key generation");
        return (PROTOCOL + id + "\0" + generation + "\0" + clientPublicKey + "\0" + direction).getBytes(StandardCharsets.UTF_8);
    }

    static String encode(byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }

    static byte[] decode(String value, int expectedLength) {
        if (value.length() != (expectedLength * 8 + 5) / 6 || !value.matches("[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("Invalid encryption field");
        byte[] decoded = Base64.getUrlDecoder().decode(value);
        if (decoded.length != expectedLength || !encode(decoded).equals(value)) throw new IllegalArgumentException("Invalid encryption field");
        return decoded;
    }

    final class Session implements AutoCloseable {
        private final String id;
        private final long generation;
        private final String clientPublicKey;
        private AsymmetricCipherKeyPair ephemeral;
        private HPKEContext incoming, outgoing;
        private boolean closed, confirmed;

        private Session(String id, long generation, String clientPublicKey) {
            info(id, generation, clientPublicKey, "client");
            decode(clientPublicKey, 65);
            this.id = id; this.generation = generation; this.clientPublicKey = clientPublicKey;
            ephemeral = suite(HPKE.mode_base).generatePrivateKey();
        }

        long generation() { return generation; }

        JsonObject announcement() {
            if (ephemeral == null) throw new IllegalStateException("No pending chat key");
            String key = encode(suite(HPKE.mode_base).serializePublicKey(ephemeral.getPublic()));
            var signer = new DSADigestSigner(new ECDSASigner(new HMacDSAKCalculator(new SHA256Digest())),
                    new SHA256Digest(), PlainDSAEncoding.INSTANCE);
            signer.init(true, identity);
            byte[] transcript = info(id, generation, clientPublicKey, "key\0" + key);
            signer.update(transcript, 0, transcript.length);
            JsonObject body = new JsonObject(); body.addProperty("type", "key"); body.addProperty("key", key);
            body.addProperty("generation", generation);
            body.addProperty("signature", encode(signer.generateSignature()));
            return body;
        }

        JsonObject accept(JsonObject body) throws InvalidCipherTextException {
            try {
                if (ephemeral == null || !body.keySet().equals(Set.of("type", "generation", "enc", "ciphertext"))
                        || !body.get("type").getAsString().equals("key")
                        || !body.get("generation").isJsonPrimitive() || !body.getAsJsonPrimitive("generation").isNumber()
                        || body.get("generation").getAsBigDecimal().longValueExact() != generation)
                    throw new IllegalArgumentException("Invalid key exchange");
                incoming = suite(HPKE.mode_base).setupBaseR(decode(body.get("enc").getAsString(), 65), ephemeral,
                        info(id, generation, clientPublicKey, "client"));
                byte[] clientKey = incoming.open(EMPTY, decode(body.get("ciphertext").getAsString(), 81));
                try {
                    if (clientKey.length != 65) throw new IllegalArgumentException("Invalid client key");
                    if (generation == 0 && !MessageDigest.isEqual(clientKey, decode(clientPublicKey, 65)))
                        throw new IllegalArgumentException("Authorization key mismatch");
                    HPKEContextWithEncapsulation sender = suite(HPKE.mode_auth).setupAuthS(
                            suite(HPKE.mode_base).deserializePublicKey(clientKey),
                            info(id, generation, clientPublicKey, "server"), ephemeral);
                    outgoing = sender;
                    JsonObject reply = new JsonObject(); reply.addProperty("type", "key");
                    reply.addProperty("generation", generation);
                    reply.addProperty("enc", encode(sender.getEncapsulation()));
                    reply.addProperty("ciphertext", encode(sender.seal(EMPTY, READY)));
                    return reply;
                } finally { Arrays.fill(clientKey, (byte) 0); }
            } catch (InvalidCipherTextException | RuntimeException invalid) {
                close(); throw invalid;
            } finally { ephemeral = null; }
        }

        void finish(JsonObject body) {
            if (closed || outgoing == null || confirmed || !body.keySet().equals(Set.of("type", "generation", "verify"))
                    || !body.get("type").getAsString().equals("finished")
                    || !body.get("generation").isJsonPrimitive() || !body.getAsJsonPrimitive("generation").isNumber()
                    || body.get("generation").getAsBigDecimal().longValueExact() != generation)
                throw new IllegalArgumentException("Invalid key confirmation");
            byte[] proof = decode(body.get("verify").getAsString(), 32);
            byte[] expected = outgoing.export(info(id, generation, clientPublicKey, "client-finished"), 32);
            try {
                if (!MessageDigest.isEqual(expected, proof)) throw new IllegalArgumentException("Invalid key confirmation");
                confirmed = true;
            } finally { Arrays.fill(expected, (byte) 0); Arrays.fill(proof, (byte) 0); }
        }

        byte[] seal(byte[] clear) throws InvalidCipherTextException {
            if (closed || outgoing == null || !confirmed) throw new IllegalStateException("Chat key unavailable");
            return outgoing.seal(EMPTY, clear);
        }
        byte[] open(byte[] encrypted) throws InvalidCipherTextException {
            if (closed || incoming == null) throw new IllegalStateException("Chat key unavailable");
            return incoming.open(EMPTY, encrypted);
        }

        @Override public void close() {
            closed = true;
            ephemeral = null; incoming = null; outgoing = null;
        }
    }
}
