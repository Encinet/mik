package org.encinet.mik.module.music.disc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicDiscSignerTest {

    @TempDir
    Path directory;

    @Test
    void persistsKeyAndRejectsTamperedSnapshots() throws Exception {
        Path keyFile = directory.resolve("state/music-disc.key");
        MusicDiscSigner first = new MusicDiscSigner(keyFile);
        String signature = first.sign("snapshot");
        byte[] key = Files.readAllBytes(keyFile);

        MusicDiscSigner restored = new MusicDiscSigner(keyFile);

        assertTrue(restored.verify("snapshot", signature));
        assertFalse(restored.verify("tampered", signature));
        assertFalse(restored.verify("snapshot", "invalid"));
        assertArrayEquals(key, Files.readAllBytes(keyFile));
        assertTrue(Files.size(keyFile) == 32);
    }
}
