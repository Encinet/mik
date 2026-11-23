package org.encinet.mik.integration.mikd;

import org.encinet.mik.feature.entertainment.music.gateway.RemoteMusic;
import org.encinet.mik.protocol.ConnectionRole;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/** Owns a real isolated daemon for game-side domain behavior tests. */
public final class MikdTestBackend implements AutoCloseable {
    private final Process process;
    private final MikdClient client;
    private final Path communication;

    private MikdTestBackend(Process process, MikdClient client, Path communication) {
        this.process = process; this.client = client; this.communication = communication;
    }

    public RequestPort requests() { return client; }

    public RemoteMusic music() { return new RemoteMusic(client::openMusicChannel); }

    public Path adminSocket() { return communication.resolve("mikd.admin.sock"); }

    public String log() throws IOException { return Files.readString(communication.resolve("daemon.log")); }

    public static MikdTestBackend webApi(Path directory) {
        return open(directory, true, false);
    }

    public static MikdTestBackend music(Path directory) {
        return open(directory, false, true);
    }

    private static final Set<Process> PROCESSES = ConcurrentHashMap.newKeySet();
    static {
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(() -> PROCESSES.forEach(Process::destroy)));
    }

    private static MikdTestBackend open(Path directory, boolean web, boolean requireMusic) {
        Process process = null;
        MikdClient client = null;
        try {
            Path state = directory.toAbsolutePath();
            Files.createDirectories(state);
            Files.setPosixFilePermissions(state, PosixFilePermissions.fromString("rwx------"));
            Path communication = Files.createTempDirectory(Path.of("/tmp"), "mikd-history-");
            Files.setPosixFilePermissions(communication, PosixFilePermissions.fromString("rwx------"));
            Path socket = communication.resolve("mikd.sock");
            String uid = Files.getAttribute(state, "unix:uid").toString();
            String binary = System.getProperty("mikd.binary", Path.of("target/debug/mikd").toAbsolutePath().toString());
            var command = new ArrayList<>(List.of(binary, "serve", "--instance", socket.toString(), uid, state.toString()));
            if (web) command.addAll(List.of("0", "0"));
            ProcessBuilder builder = new ProcessBuilder(command);
            // The subprocess must not read deployed databases, keys, or integration configuration.
            builder.environment().keySet().removeIf(name -> name.startsWith("MIKD_"));
            process = builder
                    .redirectError(ProcessBuilder.Redirect.appendTo(communication.resolve("daemon.log").toFile()))
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            PROCESSES.add(process);
            client = new MikdClient(socket, System.getProperty("user.name"), "history-test");
            if (requireMusic) {
                // The music client must work without starting or negotiating shared IPC.
                awaitMusicSocket(communication.resolve("mikd.sock.music.sock"), process);
            } else {
                client.start();
                awaitReady(client, process);
            }
            return new MikdTestBackend(process, client, communication);
        } catch (Exception failure) {
            if (client != null) client.close();
            if (process != null) { process.destroyForcibly(); PROCESSES.remove(process); }
            throw new IllegalStateException("Could not start real Rust backend", failure);
        }
    }

    private static void awaitMusicSocket(Path socket, Process process) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (process.isAlive() && System.nanoTime() - deadline < 0) {
            if (Files.exists(socket)) return;
            Thread.sleep(25);
        }
        throw new IllegalStateException("mikd music socket did not become ready");
    }

    private static void awaitReady(MikdClient client, Process process) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() - deadline < 0) {
            try {
                var status = client.status(ConnectionRole.GENERAL).get(Math.max(1, deadline - System.nanoTime()),
                        TimeUnit.NANOSECONDS).daemon();
                var ready = status.getReadyDomainsList();
                if (ready.contains("chat")) return;
            } catch (ExecutionException failure) {
                // The first status may time out while the isolated process is still starting.
                if (!process.isAlive()) throw failure;
            }
            Thread.sleep(25);
        }
        throw new IllegalStateException("mikd chat did not become ready");
    }

    @Override public void close() {
        Path directory = communication;
        client.close();
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IllegalStateException("Test backend did not drain"); }
            if (process.exitValue() != 0) throw new IllegalStateException("Test backend failed: " + Files.readString(directory.resolve("daemon.log")));
            PROCESSES.remove(process);
            try (var paths = Files.list(directory)) {
                for (Path path : paths.toList()) Files.deleteIfExists(path);
            }
            Files.delete(directory);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        } catch (IOException failure) { throw new IllegalStateException(failure); }
    }
}
