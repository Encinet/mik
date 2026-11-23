package org.encinet.mik.integration.mikd;

import org.encinet.mik.protocol.AssistantCommand;
import org.encinet.mik.protocol.ChatCommand;
import org.encinet.mik.protocol.ChatGameRoster;
import org.encinet.mik.protocol.ConnectionRole;
import org.encinet.mik.protocol.Envelope;
import org.encinet.mik.protocol.GameRoster;
import org.encinet.mik.protocol.GameStatusView;
import org.encinet.mik.protocol.PaperStateUpdate;
import org.encinet.mik.protocol.PlayerSnapshot;
import org.encinet.mik.protocol.Response;
import org.encinet.mik.protocol.Status;
import org.encinet.mik.protocol.Welcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MikdClientTest {
    @TempDir Path directory;

    @Test
    void unavailableDaemonHasBoundedIndependentQueuesAndCloseCompletesEveryRequest() {
        MikdClient client = new MikdClient(directory.resolve("absent.sock"), "mikd", "test");
        assertThrows(IllegalArgumentException.class, () -> client.status(ConnectionRole.CONNECTION_ROLE_UNSPECIFIED));
        client.start();
        ArrayList<CompletableFuture<RequestPort.Diagnostics>> ordinary = new ArrayList<>();
        for (int i = 0; i < 80; i++) ordinary.add(client.status(ConnectionRole.GENERAL));
        assertTrue(ordinary.stream().filter(CompletableFuture::isCompletedExceptionally).count() >= 48);
        CompletableFuture<RequestPort.Diagnostics> control = client.status(ConnectionRole.CONTROL);
        assertTrue(!control.isDone(), "ordinary queue cannot consume control capacity");
        client.close();
        assertTrue(ordinary.stream().allMatch(CompletableFuture::isCompletedExceptionally));
        assertTrue(control.isCompletedExceptionally());
        assertTrue(client.status(ConnectionRole.GENERAL).isCompletedExceptionally());
        client.close();
    }

    @Test
    void onlyLatestUnsentProjectionIsRetainedAndOversizedFramesAreRejected() throws Exception {
        try (MikdClient client = new MikdClient(directory.resolve("absent.sock"), "mikd", "test")) {
            client.start();
            var old = client.publish(state(1).build());
            var current = client.publish(state(2).build());
            assertTrue(old.isCompletedExceptionally());
            assertTrue(!current.isDone());
            var huge = state(3).setPublicRoster(GameRoster.newBuilder().addPlayers(PlayerSnapshot.newBuilder()
                    .setName("x".repeat(MikdFrames.MAX_BYTES)))).build();
            assertTrue(client.publish(huge).isCompletedExceptionally());
            assertTrue(!current.isDone(), "invalid replacement must preserve admitted snapshot");
            assertThrows(java.util.concurrent.ExecutionException.class, () -> current.get(6, TimeUnit.SECONDS));
        }
    }

    @Test
    void chatQueueBytesDoNotConsumePaperStateCapacity() {
        var client = new MikdClient(directory.resolve("absent.sock"), "mikd", "test");
        client.start();
        try {
            for (int index = 0; index < 4; index++) {
                var command = ChatCommand.newBuilder().setNotice(org.encinet.mik.protocol.HistoryEntry.newBuilder()
                        .setRecord(org.encinet.mik.protocol.ChatRecord.newBuilder().setNotice(org.encinet.mik.protocol.ChatNoticeRecord.newBuilder()
                                .setPresence(org.encinet.mik.protocol.ChatPresenceNotice.newBuilder().setName("x".repeat(240 * 1024)))))).build();
                assertTrue(!client.chat(command).isDone());
            }
            var admitted = client.publish(state(1).build());
            var replacement = state(2).setPublicRoster(GameRoster.newBuilder().addPlayers(PlayerSnapshot.newBuilder()
                    .setName("x".repeat(80 * 1024)))).build();
            var current = client.publish(replacement);
            assertTrue(admitted.isCompletedExceptionally());
            assertTrue(!current.isDone(), "chat backpressure must not consume the Paper state lane budget");
            client.close();
            assertTrue(current.isCompletedExceptionally(), "shutdown must complete the retained request");
        } finally {
            client.close();
        }
    }

    @Test
    void cancellingRequestsImmediatelyReleasesQueueCapacity() {
        try (MikdClient client = new MikdClient(directory.resolve("absent.sock"), "mikd", "test")) {
            client.start();
            ArrayList<CompletableFuture<RequestPort.Diagnostics>> requests = new ArrayList<>();
            for (int i = 0; i < 32; i++) requests.add(client.status(ConnectionRole.GENERAL));
            assertTrue(client.status(ConnectionRole.GENERAL).isCompletedExceptionally());
            requests.forEach(future -> future.cancel(false));
            assertTrue(!client.status(ConnectionRole.GENERAL).isDone());
        }
    }

    @Test
    void legacyDaemonIsDiagnosableButNeverReceivesAnUnsupportedAssistantRequest() throws Exception {
        var socket = directory.resolve("old.sock");
        var observed = new CompletableFuture<Envelope>();
        try (var listener = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
             var client = new MikdClient(socket, System.getProperty("user.name"), "test")) {
            listener.bind(UnixDomainSocketAddress.of(socket));
            Thread.ofVirtual().start(() -> {
                try (var peer = accept(listener, ConnectionRole.GENERAL)) {
                    welcome(peer, List.of("assistant-turns.v8"));
                    replyWithStatus(peer, MikdFrames.read(peer));
                    var request = MikdFrames.read(peer);
                    replyWithStatus(peer, request);
                    observed.complete(request);
                } catch (IOException | RuntimeException failure) { observed.completeExceptionally(failure); }
            });
            client.start();
            var status = client.status(ConnectionRole.GENERAL).get(5, TimeUnit.SECONDS);
            assertTrue(status.missingWorkflows().contains("assistant-turns.v9"));
            var failure = assertThrows(ExecutionException.class, () -> client.assistant(AssistantCommand.getDefaultInstance())
                    .get(5, TimeUnit.SECONDS));
            assertTrue(RequestPort.definitelyNotSubmitted(failure));
            assertEquals(1, client.status(ConnectionRole.GENERAL).get(5, TimeUnit.SECONDS).daemon().getProtocolVersion());
            assertTrue(observed.get(5, TimeUnit.SECONDS).getRequest().hasStatus(), "No assistant request may cross the old connection");
        }
    }

    @Test
    void lostResponseAfterAChatWriteIsNotReportedAsUnsubmitted() throws Exception {
        var socket = directory.resolve("drop.sock");
        var observed = new CompletableFuture<Envelope>();
        try (var listener = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
             var client = new MikdClient(socket, System.getProperty("user.name"), "test")) {
            listener.bind(UnixDomainSocketAddress.of(socket));
            Thread.ofVirtual().start(() -> {
                try (var peer = accept(listener, ConnectionRole.CHAT_UPLINK)) {
                    welcome(peer, List.of("chat-receipts.v1"));
                    observed.complete(MikdFrames.read(peer));
                    // Simulate a disconnect after the daemon consumed the request and before it returned its receipt.
                } catch (IOException | RuntimeException failure) { observed.completeExceptionally(failure); }
            });
            client.start();
            var operation = ChatCommand.newBuilder().setNotice(org.encinet.mik.protocol.HistoryEntry.getDefaultInstance()).build();
            var failure = assertThrows(ExecutionException.class, () -> client.chat(operation).get(5, TimeUnit.SECONDS));
            assertEquals(operation, observed.get(5, TimeUnit.SECONDS).getRequest().getChat());
            assertFalse(RequestPort.definitelyNotSubmitted(failure));
        }
    }

    private static SocketChannel accept(ServerSocketChannel listener, ConnectionRole role) throws IOException {
        while (true) {
            var peer = listener.accept();
            try {
                if (MikdFrames.read(peer).getHello().getRole() == role) return peer;
            } catch (IOException | RuntimeException failure) {
                peer.close();
                throw failure;
            }
            peer.close();
        }
    }

    private static void welcome(SocketChannel peer, List<String> workflows) throws IOException {
        MikdFrames.write(peer, Envelope.newBuilder().setSessionId("test-session").setWelcome(Welcome.newBuilder()
                .setProtocolVersion(MikdFrames.VERSION).setMaximumFrameBytes(MikdFrames.MAX_BYTES)
                .addCapabilities("status.v1").addCapabilities("paper-state.v1").addAllCapabilities(workflows)).build());
    }

    private static void replyWithStatus(SocketChannel peer, Envelope request) throws IOException {
        MikdFrames.write(peer, Envelope.newBuilder().setSessionId(request.getSessionId()).setRequestId(request.getRequestId())
                .setResponse(Response.newBuilder().setCode(Response.Code.OK).setStatus(Status.newBuilder().setProtocolVersion(1)))
                .build());
    }

    private static PaperStateUpdate.Builder state(long version) {
        return PaperStateUpdate.newBuilder().setVersion(version).setServer(GameStatusView.getDefaultInstance())
                .setPublicRoster(GameRoster.getDefaultInstance()).setChatRoster(ChatGameRoster.getDefaultInstance());
    }
}
