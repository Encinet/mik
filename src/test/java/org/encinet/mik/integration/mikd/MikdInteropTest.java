package org.encinet.mik.integration.mikd;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.encinet.mik.protocol.AssistantAsk;
import org.encinet.mik.protocol.AssistantCommand;
import org.encinet.mik.protocol.AssistantFailure;
import org.encinet.mik.protocol.AssistantResult;
import org.encinet.mik.protocol.ChatGameRoster;
import org.encinet.mik.protocol.ConnectionRole;
import org.encinet.mik.protocol.GameRoster;
import org.encinet.mik.protocol.GameStatusView;
import org.encinet.mik.protocol.PaperStateUpdate;
import org.encinet.mik.protocol.PlayerSnapshot;
import org.encinet.mik.protocol.Status;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("mikd-interop")
class MikdInteropTest {
    @TempDir Path directory;

    @Test
    void javaRustSnapshotsAndDaemonRestartUseNewSessions() throws Exception {
        Process first = startDaemon();
        try (MikdClient client = client(System.getProperty("user.name"))) {
            client.start();
            Status initial = awaitStatus(client, ConnectionRole.GENERAL, _ -> true);
            assertTrue(initial.getReadyDomainsList().containsAll(List.of("chat", "identity", "governance")));
            assertEquals(0, initial.getWebApiPort());
            assertEquals(0, initial.getWebChatPort());
            assertEquals(1, initial.getProtocolVersion());
            var assistant = client.assistant(AssistantCommand.newBuilder().setPlayerUuid(UUID.randomUUID().toString())
                    .setTurnId(UUID.randomUUID().toString())
                    .setAsk(AssistantAsk.newBuilder().setPrompt("How do I use an elevator?"))
                    .build()).get(6, TimeUnit.SECONDS);
            assertEquals(AssistantResult.OutcomeCase.FAILURE, assistant.getOutcomeCase());
            assertEquals(AssistantFailure.Reason.DISABLED, assistant.getFailure().getReason());
            var snapshot = PaperStateUpdate.newBuilder().setVersion(7).setServer(GameStatusView.getDefaultInstance())
                    .setPublicRoster(GameRoster.newBuilder().addPlayers(PlayerSnapshot.newBuilder()
                            .setPlayerId(UUID.randomUUID().toString()).setName("测试玩家")))
                    .setChatRoster(ChatGameRoster.getDefaultInstance()).build();
            assertEquals(7, client.publish(snapshot).get(6, TimeUnit.SECONDS).getVersion());
            Status projection = awaitStatus(client, ConnectionRole.GENERAL, status -> status.getSnapshotVersion() == 7);
            assertEquals(1, projection.getPlayerCount());
            assertEquals(7, awaitStatus(client, ConnectionRole.CONTROL, _ -> true).getSnapshotVersion());
            first.destroyForcibly();
            assertTrue(first.waitFor(3, TimeUnit.SECONDS));
            Process second = startDaemon();
            try {
                Status restarted = awaitStatus(client, ConnectionRole.GENERAL,
                        status -> !status.getDaemonId().equals(initial.getDaemonId()));
                assertNotEquals(initial.getDaemonId(), restarted.getDaemonId());
                assertEquals(0, restarted.getSnapshotVersion());
                long restored = publishAfterReconnect(client, snapshot);
                assertEquals(1, awaitStatus(client, ConnectionRole.GENERAL,
                        status -> status.getSnapshotVersion() == restored).getPlayerCount());
            } finally {
                stopDaemon(second);
            }
        } finally {
            stopDaemon(first);
        }
        assertTrue(!Files.exists(directory.resolve("mikd.sock")));
    }

    @Test
    void unexpectedDaemonUserIsRejectedAndQueuedRequestsExpire() throws Exception {
        Process process = startDaemon();
        try (MikdClient client = client("not-the-mikd-owner")) {
            client.start();
            assertThrows(ExecutionException.class, () -> client.status(ConnectionRole.CONTROL).get(6, TimeUnit.SECONDS));
        } finally {
            stopDaemon(process);
        }
    }

    @Test
    void pluginConsolesAnswerAdminQueriesAndCloseIndependently() throws Exception {
        Process process = startDaemon();
        try (MikdClient client = client(System.getProperty("user.name"))) {
            client.start();
            awaitStatus(client, ConnectionRole.GENERAL, _ -> true);
            var commands = new LinkedBlockingQueue<String>();
            var reading = new CountDownLatch(1);
            var release = new CountDownLatch(1);

            try (var _ = client.register("observer", "Observer logs", null,
                    (_, _) -> new ConsolePort.LogPage(List.of("observer output"), "", "", ""))) {
                try (var _ = client.register("managed", "Managed console", (line, work) -> {
                    assertTrue(work.active());
                    commands.add(line);
                    return ConsolePort.CommandOutcome.SUBMITTED;
                }, (query, work) -> {
                    if (query.position() == ConsolePort.Position.BEFORE) {
                        assertEquals("opaque:previous", query.cursor());
                        reading.countDown();
                        if (!release.await(work.remainingNanos(), TimeUnit.NANOSECONDS)) {
                            throw new IOException("The history query expired");
                        }
                        return new ConsolePort.LogPage(List.of("启动日志"), "", "opaque:newer", "");
                    }
                    return new ConsolePort.LogPage(List.of("启动完成"), "opaque:previous", "opaque:next", "");
                })) {

                    awaitConsole("managed", item -> connected(item, "commands") && connected(item, "logs"));
                    awaitConsole("observer", item -> connected(item, "logs") && item.get("commands").getAsString().equals("unsupported"));
                    assertTrue(adminOutput(admin("logs", "interop-paper/managed")).contains("启动完成"));
                    assertTrue(adminOutput(admin("logs", "interop-paper/observer")).contains("observer output"));
                    Process past = admin("logs", "interop-paper/managed", "--before", "opaque:previous");
                    try {
                        assertTrue(reading.await(4, TimeUnit.SECONDS));
                        assertTrue(adminOutput(admin("run", "interop-paper/managed", "say interop")).contains("submitted"));
                        assertEquals("say interop", commands.poll(1, TimeUnit.SECONDS));
                        release.countDown();
                        assertTrue(adminOutput(past).contains("启动日志"));
                    } finally {
                        release.countDown();
                        if (past.isAlive()) past.destroyForcibly();
                    }
                }
                awaitConsole("managed", item -> item.get("commands").isJsonPrimitive()
                        && item.get("commands").getAsString().equals("offline")
                        && item.get("logs").isJsonPrimitive() && item.get("logs").getAsString().equals("offline"));
                assertTrue(adminOutput(admin("logs", "interop-paper/observer")).contains("observer output"));
                awaitStatus(client, ConnectionRole.GENERAL, _ -> true);
            }
        } finally {
            stopDaemon(process);
        }
    }

    private static boolean connected(JsonObject entry, String capability) {
        return entry.get(capability) instanceof JsonObject state && state.has("connected");
    }

    private void awaitConsole(String provider, Predicate<JsonObject> condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        do {
            var data = JsonParser.parseString(adminOutput(admin("list"))).getAsJsonObject().getAsJsonArray("data");
            for (var value : data) {
                var entry = value.getAsJsonObject();
                if (provider.equals(entry.getAsJsonObject("id").get("provider").getAsString()) && condition.test(entry)) return;
            }
            Thread.sleep(25);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Console " + provider + " did not reach the expected state");
    }

    private Process admin(String operation, String... arguments) throws IOException {
        var command = new java.util.ArrayList<>(List.of(System.getProperty("mikd.binary"),
                "admin", "--socket", directory.resolve("mikd.admin.sock").toString(), "console", operation));
        command.addAll(List.of(arguments));
        return new ProcessBuilder(command).redirectErrorStream(true).start();
    }

    private static String adminOutput(Process process) throws IOException, InterruptedException {
        try {
            assertTrue(process.waitFor(6, TimeUnit.SECONDS));
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), output);
            return output;
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private MikdClient client(String user) {
        return new MikdClient(directory.resolve("mikd.sock"), user, "interop-paper");
    }

    private Process startDaemon() throws IOException {
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        String uid = Files.getAttribute(directory, "unix:uid").toString();
        var builder = new ProcessBuilder(System.getProperty("mikd.binary"), "serve", "--instance",
                directory.resolve("mikd.sock").toString(), uid, directory.toAbsolutePath().toString());
        builder.environment().keySet().removeIf(name -> name.startsWith("MIKD_"));
        return builder.redirectError(ProcessBuilder.Redirect.appendTo(directory.resolve("daemon.log").toFile()))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
    }

    private static long publishAfterReconnect(MikdClient client, PaperStateUpdate snapshot) throws Exception {
        // GENERAL and PAPER_STATE reconnect independently. Paper keeps publishing fresh snapshots.
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        IOException lastFailure = null;
        for (long version = snapshot.getVersion(); System.nanoTime() < deadline; version++) {
            try {
                var next = snapshot.toBuilder().setVersion(version).build();
                assertEquals(version, client.publish(next).get(6, TimeUnit.SECONDS).getVersion());
                return version;
            } catch (ExecutionException error) {
                if (!(error.getCause() instanceof IOException failure)) throw error;
                lastFailure = failure;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("mikd did not accept a snapshot after restart", lastFailure);
    }

    private static Status awaitStatus(MikdClient client, ConnectionRole role, Predicate<Status> condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            try {
                var diagnostics = client.status(role).get(6, TimeUnit.SECONDS);
                assertTrue(diagnostics.missingWorkflows().isEmpty(), "current counterparts must agree on workflow contracts");
                Status result = diagnostics.daemon();
                if (condition.test(result)) return result;
            } catch (ExecutionException ignored) {
                // An in-flight request fails when the session disconnects; wait explicitly for this lane.
            }
            Thread.sleep(25);
        }
        throw new AssertionError("mikd did not reconnect within 15 seconds");
    }

    private static void stopDaemon(Process process) throws InterruptedException {
        if (!process.isAlive()) return;
        process.destroy();
        if (!process.waitFor(3, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(3, TimeUnit.SECONDS);
            throw new AssertionError("mikd did not shut down on SIGTERM");
        }
        assertEquals(0, process.exitValue());
    }
}
