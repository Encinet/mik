package org.encinet.mik.feature.community.social.platform.matrix.crypto;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;

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
                boolean created = false;
                try {
                    new SecureRandom().nextBytes(key);
                    byte[] encoded = HexFormat.of().formatHex(key).getBytes(StandardCharsets.US_ASCII);
                    try (var output = Files.newByteChannel(keyFile,
                            java.util.Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
                        created = true;
                        restrict(keyFile, false);
                        ByteBuffer buffer = ByteBuffer.wrap(encoded);
                        while (buffer.hasRemaining()) output.write(buffer);
                    } finally {
                        java.util.Arrays.fill(encoded, (byte) 0);
                    }
                } catch (IOException failure) {
                    if (created) {
                        try { Files.deleteIfExists(keyFile); }
                        catch (IOException cleanup) { failure.addSuppressed(cleanup); }
                    }
                    throw failure;
                } finally {
                    java.util.Arrays.fill(key, (byte) 0);
                }
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
        PosixFileAttributeView posix = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (posix != null) {
            posix.setPermissions(PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
            return;
        }
        AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (acl == null) throw new IOException("File system cannot restrict Matrix encryption storage");
        var owner = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(Files.getOwner(path))
                .setPermissions(EnumSet.allOf(AclEntryPermission.class));
        if (directory) owner.setFlags(EnumSet.of(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT));
        acl.setAcl(List.of(owner.build()));
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
