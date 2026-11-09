package org.encinet.mik.feature.community.chat.web;

import com.google.gson.JsonObject;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.hpke.HPKE;
import org.bouncycastle.crypto.hpke.HPKEContext;
import org.bouncycastle.crypto.hpke.HPKEContextWithEncapsulation;
import org.bouncycastle.crypto.signers.DSADigestSigner;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.PlainDSAEncoding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.*;

class WebChatEncryptionTest {
    static final String ID = "A".repeat(32);
    @TempDir Path directory;

    @Test void signingIdentitySurvivesRestartAndItsFileIsPrivate() throws Exception {
        Path path = directory.resolve("web-chat.key");
        var first = WebChatEncryption.load(path);
        assertEquals(first.publicKey(), WebChatEncryption.load(path).publicKey());
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(path));
        Files.writeString(path, "invalid");
        assertThrows(java.io.IOException.class, () -> WebChatEncryption.load(path));
        assertEquals("invalid", Files.readString(path), "A broken identity must not silently rotate the trusted key");
    }

    @Test void ephemeralKeySignatureBindsThePinnedIdentityAndConnection() throws Exception {
        var identity = WebChatEncryption.generate();
        var reverse = WebChatEncryption.suite(HPKE.mode_base).generatePrivateKey();
        String binding = publicKey(reverse);
        var announcement = identity.session(ID, 0, binding).announcement();
        var verifier = new DSADigestSigner(new ECDSASigner(), new SHA256Digest(), PlainDSAEncoding.INSTANCE);
        verifier.init(false, WebChatEncryption.suite(HPKE.mode_base).deserializePublicKey(WebChatEncryption.decode(identity.publicKey(), 65)));
        byte[] transcript = WebChatEncryption.info(ID, 0, binding, "key\0" + announcement.get("key").getAsString());
        verifier.update(transcript, 0, transcript.length);
        assertTrue(verifier.verifySignature(WebChatEncryption.decode(announcement.get("signature").getAsString(), 64)));
        byte[] other = WebChatEncryption.info("B".repeat(32), 0, binding, "key\0" + announcement.get("key").getAsString());
        verifier.update(other, 0, other.length);
        assertFalse(verifier.verifySignature(WebChatEncryption.decode(announcement.get("signature").getAsString(), 64)));
        assertNotEquals(announcement.get("key"), identity.session(ID, 0, binding).announcement().get("key"));
    }

    @Test void independentDirectionalContextsRejectReplayTamperingAndCrossConnectionTraffic() throws Exception {
        var identity = WebChatEncryption.generate();
        var reverse = WebChatEncryption.suite(HPKE.mode_base).generatePrivateKey();
        String binding = publicKey(reverse);
        var server = identity.session(ID, 0, binding);
        Client client = new Client(server.announcement(), ID, reverse, binding);
        client.confirm(server.accept(client.initial()));
        server.finish(client.finished());
        byte[] text = "private message".getBytes(StandardCharsets.UTF_8);
        byte[] packet = client.sender.seal(WebChatEncryption.EMPTY, text);
        assertArrayEquals(text, server.open(packet));
        assertThrows(Exception.class, () -> server.open(packet));
        byte[] answer = server.seal(text);
        assertArrayEquals(text, client.recipient.open(WebChatEncryption.EMPTY, answer));
        assertThrows(Exception.class, () -> client.recipient.open(WebChatEncryption.EMPTY, answer));

        var second = identity.session("B".repeat(32), 0, binding);
        Client other = new Client(second.announcement(), "B".repeat(32), reverse, binding);
        other.confirm(second.accept(other.initial()));
        second.finish(other.finished());
        assertThrows(Exception.class, () -> second.open(client.sender.seal(WebChatEncryption.EMPTY, text)));
        byte[] changed = other.sender.seal(WebChatEncryption.EMPTY, text); changed[0] ^= 1;
        assertThrows(Exception.class, () -> second.open(changed));
    }

    @Test void closedSessionsCannotEncryptDecryptOrRestartAHandshake() throws Exception {
        var identity = WebChatEncryption.generate();
        var reverse = WebChatEncryption.suite(HPKE.mode_base).generatePrivateKey();
        String binding = publicKey(reverse);
        var session = identity.session(ID, 0, binding);
        Client client = new Client(session.announcement(), ID, reverse, binding);
        client.confirm(session.accept(client.initial()));
        session.finish(client.finished());
        byte[] packet = client.sender.seal(WebChatEncryption.EMPTY, new byte[]{1});
        session.close(); session.close();
        assertThrows(IllegalStateException.class, () -> session.seal(new byte[]{1}));
        assertThrows(IllegalStateException.class, () -> session.open(packet));
        assertThrows(IllegalStateException.class, session::announcement);
    }

    @Test void generationsSeparateKeysOnTheSameConnectionAndRejectOldHandshakes() throws Exception {
        var identity = WebChatEncryption.generate();
        var reverse = WebChatEncryption.suite(HPKE.mode_base).generatePrivateKey();
        String binding = publicKey(reverse);
        var first = identity.session(ID, 0, binding);
        Client old = new Client(first.announcement(), ID, reverse, binding);
        JsonObject oldHandshake = old.initial();
        old.confirm(first.accept(oldHandshake));
        first.finish(old.finished());
        var rejected = identity.session(ID, 1, binding);
        assertThrows(IllegalArgumentException.class, () -> rejected.accept(oldHandshake));
        assertThrows(IllegalStateException.class, rejected::announcement);

        var next = identity.session(ID, 1, binding);
        Client current = new Client(next.announcement(), ID, WebChatEncryption.suite(HPKE.mode_base).generatePrivateKey(), binding);
        current.confirm(next.accept(current.initial()));
        next.finish(current.finished());
        byte[] packet = old.sender.seal(WebChatEncryption.EMPTY, new byte[]{1});
        assertThrows(Exception.class, () -> next.open(packet));
        assertArrayEquals(new byte[]{2}, next.open(current.sender.seal(WebChatEncryption.EMPTY, new byte[]{2})));
    }

    static String publicKey(AsymmetricCipherKeyPair key) {
        return WebChatEncryption.encode(WebChatEncryption.suite(HPKE.mode_base).serializePublicKey(key.getPublic()));
    }

    static final class Client {
        final String id, authorizationKey;
        final long generation;
        final AsymmetricCipherKeyPair reverse;
        final org.bouncycastle.crypto.params.AsymmetricKeyParameter serverKey;
        final HPKEContextWithEncapsulation sender;
        HPKEContext recipient;

        Client(JsonObject announcement, String id, AsymmetricCipherKeyPair reverse, String authorizationKey) {
            this.id = id; this.authorizationKey = authorizationKey; this.reverse = reverse;
            this.generation = announcement.get("generation").getAsLong();
            var suite = WebChatEncryption.suite(HPKE.mode_base);
            serverKey = suite.deserializePublicKey(WebChatEncryption.decode(announcement.get("key").getAsString(), 65));
            sender = suite.setupBaseS(serverKey, WebChatEncryption.info(id, generation, authorizationKey, "client"));
        }

        JsonObject initial() throws Exception {
            var frame = new JsonObject(); frame.addProperty("type", "key");
            frame.addProperty("generation", generation);
            frame.addProperty("enc", WebChatEncryption.encode(sender.getEncapsulation()));
            frame.addProperty("ciphertext", WebChatEncryption.encode(sender.seal(WebChatEncryption.EMPTY,
                    WebChatEncryption.suite(HPKE.mode_base).serializePublicKey(reverse.getPublic()))));
            return frame;
        }

        JsonObject finished() {
            var frame = new JsonObject(); frame.addProperty("type", "finished"); frame.addProperty("generation", generation);
            byte[] proof = recipient.export(WebChatEncryption.info(id, generation, authorizationKey, "client-finished"), 32);
            try { frame.addProperty("verify", WebChatEncryption.encode(proof)); return frame; }
            finally { java.util.Arrays.fill(proof, (byte) 0); }
        }

        void confirm(JsonObject frame) throws Exception {
            assertEquals(generation, frame.get("generation").getAsLong());
            recipient = WebChatEncryption.suite(HPKE.mode_auth).setupAuthR(WebChatEncryption.decode(frame.get("enc").getAsString(), 65),
                    reverse, WebChatEncryption.info(id, generation, authorizationKey, "server"), serverKey);
            assertArrayEquals(WebChatEncryption.READY, recipient.open(WebChatEncryption.EMPTY, WebChatEncryption.decode(frame.get("ciphertext").getAsString(), 21)));
        }
    }
}
