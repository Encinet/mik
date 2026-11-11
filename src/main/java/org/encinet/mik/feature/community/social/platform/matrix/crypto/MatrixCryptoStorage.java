package org.encinet.mik.feature.community.social.platform.matrix.crypto;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/** Owns the encrypted database key and exclusive device lock across reloads. */
public final class MatrixCryptoStorage implements AutoCloseable {
    private final Path directory;
    private final FileChannel channel;
    private final FileLock lock;
    private final String password;

    private MatrixCryptoStorage(Path directory, FileChannel channel, FileLock lock, String password) {
        this.directory = directory;
        this.channel = channel;
        this.lock = lock;
        this.password = password;
    }

    public static MatrixCryptoStorage open(Path root, String homeserver, String userId, String deviceId)
            throws IOException, InterruptedException {
        String identity = homeserver + '\n' + userId + '\n' + deviceId;
        Path directory = root.resolve(digest(identity)).toAbsolutePath();
        Files.createDirectories(directory);
        restrict(directory, true);
        FileChannel channel = FileChannel.open(directory.resolve("device.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock = null;
            for (int attempt = 0; attempt < 200 && lock == null; attempt++) {
                try {
                    lock = channel.tryLock();
                } catch (OverlappingFileLockException ignored) {
                    // The preceding host generation may still be closing its SDK transactions.
                }
                if (lock == null) Thread.sleep(100);
            }
            if (lock == null) throw new IOException("Matrix encryption device is already open");
            Path keyFile = directory.resolve("database.key");
            if (!Files.exists(keyFile)) {
                if (Files.exists(directory.resolve("crypto.mv.db"))) {
                    throw new IOException("Matrix encryption database exists but database.key is missing; restore both files");
                }
                byte[] key = new byte[32];
                new SecureRandom().nextBytes(key);
                Files.writeString(keyFile, HexFormat.of().formatHex(key), StandardOpenOption.CREATE_NEW);
            }
            restrict(keyFile, false);
            String password = Files.readString(keyFile).strip();
            if (!password.matches("[a-f0-9]{64}")) throw new IOException("Invalid Matrix database.key");
            return new MatrixCryptoStorage(directory, channel, lock, password);
        } catch (IOException | InterruptedException | RuntimeException error) {
            channel.close();
            throw error;
        }
    }

    public Path database() {
        return directory.resolve("crypto");
    }

    public String password() {
        return password;
    }

    public boolean initialized() {
        return Files.exists(directory.resolve("initialized"));
    }

    public void markInitialized() throws IOException {
        Files.writeString(directory.resolve("initialized"), "1");
    }

    private static String digest(String identity) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void restrict(Path path, boolean directory) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
        }
    }

    @Override
    public void close() throws IOException {
        try {
            lock.release();
        } finally {
            channel.close();
        }
    }
}
