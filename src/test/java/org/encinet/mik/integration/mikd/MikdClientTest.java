package org.encinet.mik.integration.mikd;

import org.encinet.mik.protocol.AssistantCommand;
import org.encinet.mik.protocol.ChatPublication;
import org.encinet.mik.protocol.ChatPart;
import org.encinet.mik.protocol.ChatResult;
import org.encinet.mik.protocol.ChatTextRun;
import org.encinet.mik.protocol.ChatGameRoster;
import org.encinet.mik.protocol.ConnectionRole;
import org.encinet.mik.protocol.Envelope;
import org.encinet.mik.protocol.GameRoster;
import org.encinet.mik.protocol.GameStatusView;
import org.encinet.mik.protocol.PaperStateUpdate;
import org.encinet.mik.protocol.PaperStateAccepted;
import org.encinet.mik.protocol.PlayerSnapshot;
import org.encinet.mik.protocol.Response;
import org.encinet.mik.protocol.Status;
import org.encinet.mik.protocol.Welcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
                var publication = ChatPublication.newBuilder()
                        .setPost(ChatPublication.Post.newBuilder().addParts(ChatPublication.Post.Part.newBuilder()
                                .setResolved(ChatPart.newBuilder().setText(ChatTextRun.newBuilder().setText("x".repeat(240 * 1024)))))).build();
                assertTrue(!client.publishChat(publication, () -> true).isDone());
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
                    welcome(peer, List.of("chat-messages.v1"));
                    observed.complete(MikdFrames.read(peer));
                    // Simulate a disconnect after the daemon consumed the request and before it returned its receipt.
                } catch (IOException | RuntimeException failure) { observed.completeExceptionally(failure); }
            });
            client.start();
            var publication = ChatPublication.newBuilder().setMessageId("expected").build();
            var failure = assertThrows(ExecutionException.class, () -> client.publishChat(publication, () -> true).get(5, TimeUnit.SECONDS));
            assertEquals(publication, observed.get(5, TimeUnit.SECONDS).getRequest().getChat().getPublish());
            assertFalse(RequestPort.definitelyNotSubmitted(failure));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aChatReplyMustMatchTheOperationAndPublication(boolean publishing) throws Exception {
        var socket = directory.resolve("mismatched.sock");
        var disconnected = new CompletableFuture<Boolean>();
        var publication = ChatPublication.newBuilder().setMessageId("expected").build();
        try (var listener = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
             var client = new MikdClient(socket, System.getProperty("user.name"), "test")) {
            listener.bind(UnixDomainSocketAddress.of(socket));
            Thread.ofVirtual().start(() -> {
                try (var peer = accept(listener, publishing ? ConnectionRole.CHAT_UPLINK : ConnectionRole.CHAT_DOWNLINK)) {
                    welcome(peer, List.of("chat-messages.v1"));
                    var request = MikdFrames.read(peer);
                    // An OK envelope is not proof of persistence or an empty poll.
                    var chat = ChatResult.newBuilder().setPublishedId("unrelated");
                    reply(peer, request, Response.newBuilder().setChat(chat));
                    disconnected.complete(peer.read(java.nio.ByteBuffer.allocate(1)) == -1);
                } catch (IOException | RuntimeException failure) { disconnected.completeExceptionally(failure); }
            });
            client.start();
            CompletableFuture<?> result = publishing ? client.publishChat(publication, () -> true) : client.pollChat();
            var failure = assertThrows(ExecutionException.class, () -> result.get(5, TimeUnit.SECONDS));
            assertInstanceOf(IOException.class, failure.getCause());
            assertFalse(RequestPort.definitelyNotSubmitted(failure));
            assertTrue(disconnected.get(5, TimeUnit.SECONDS), "The invalid reply must invalidate the lane");
        }
    }

    @Test
    void basePaperStateDoesNotDependOnChatMessageNegotiation() throws Exception {
        var socket = directory.resolve("state.sock");
        var observed = new CompletableFuture<PaperStateUpdate>();
        try (var listener = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
             var client = new MikdClient(socket, System.getProperty("user.name"), "test")) {
            listener.bind(UnixDomainSocketAddress.of(socket));
            Thread.ofVirtual().start(() -> {
                try (var peer = accept(listener, ConnectionRole.PAPER_STATE)) {
                    welcome(peer, List.of());
                    var request = MikdFrames.read(peer);
                    var snapshot = request.getRequest().getPaperState();
                    reply(peer, request, Response.newBuilder().setPaperState(
                            PaperStateAccepted.newBuilder().setVersion(snapshot.getVersion())));
                    observed.complete(snapshot);
                } catch (IOException | RuntimeException failure) { observed.completeExceptionally(failure); }
            });
            client.start();
            var update = state(1).build();
            assertEquals(1, client.publish(update).get(5, TimeUnit.SECONDS).getVersion());
            assertEquals(update, observed.get(5, TimeUnit.SECONDS));
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
        reply(peer, request, Response.newBuilder().setStatus(Status.newBuilder().setProtocolVersion(1)));
    }

    private static void reply(SocketChannel peer, Envelope request, Response.Builder response) throws IOException {
        MikdFrames.write(peer, Envelope.newBuilder().setSessionId(request.getSessionId()).setRequestId(request.getRequestId())
                .setResponse(response.setCode(Response.Code.OK))
                .build());
    }

    private static PaperStateUpdate.Builder state(long version) {
        return PaperStateUpdate.newBuilder().setVersion(version).setServer(GameStatusView.getDefaultInstance())
                .setPublicRoster(GameRoster.getDefaultInstance()).setChatRoster(ChatGameRoster.getDefaultInstance());
    }
}
