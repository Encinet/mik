package org.encinet.mik.feature.community.governance.delivery;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.data.NodeMap;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.community.governance.gateway.GovernanceGateway;
import org.encinet.mik.feature.community.governance.GovernanceText;
import org.encinet.mik.integration.mikd.RequestPort;
import org.encinet.mik.protocol.GovernanceCommand;
import org.encinet.mik.protocol.GovernanceGameGrant;
import org.encinet.mik.protocol.GovernanceQuery;
import org.encinet.mik.protocol.GovernanceResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@Timeout(5)
final class GovernanceDeliveryCoordinatorTest {
    @Test
    void checksCurrentTargetAfterSlowUserLoad() {
        try (var server = new Server()) {
            var player = server.player(false);
            server.target(player, true);
            server.poll();
            assertTrue(player.saves.isEmpty());

            long revoke = server.target(player, false);
            player.loaded.complete(player.user);
            server.drain();

            assertEquals(List.of(Set.of()), player.saves);
            assertFalse(player.live.contains(server.moderator));
            assertEquals(List.of(revoke), server.receipts);
        }
    }

    @Test
    void revokesLiveAndOtherPlayersWhileOldSaveRunsThenRetriesFailedCorrection() {
        try (var server = new Server()) {
            var slow = server.player(true);
            var owner = server.player(true);
            owner.live.add(server.moderator);
            owner.live.add(server.custodian);
            owner.primary = "custodian";
            var oldSave = new CompletableFuture<Void>();
            slow.saveResults.add(oldSave);
            server.target(slow, true);
            server.poll();
            assertTrue(slow.live.contains(server.moderator));

            long revokeSlow = server.target(slow, false);
            long revokeOwner = server.target(owner, false);
            server.poll();
            assertFalse(slow.live.contains(server.moderator));
            slow.live.add(server.moderator); // Login reloaded a previously saved node.
            server.poll();
            assertFalse(slow.live.contains(server.moderator));
            assertEquals(1, slow.saves.size(), "no second save until LuckPerms finishes the first");
            assertEquals(Set.of(server.custodian), owner.stored);
            assertEquals("custodian", owner.primary);
            assertEquals(List.of(revokeOwner), server.receipts);

            slow.saveResults.add(CompletableFuture.failedFuture(new IOException("test storage failure")));
            oldSave.complete(null);
            server.drain();
            assertTrue(slow.stored.contains(server.moderator), "the old save can still reach persistent storage");
            assertFalse(slow.live.contains(server.moderator));
            assertEquals(2, slow.saves.size());
            assertEquals(List.of(revokeOwner), server.receipts, "failed writes stay unconfirmed");

            server.poll();
            server.poll();
            assertFalse(slow.stored.contains(server.moderator));
            assertEquals(List.of(revokeOwner, revokeSlow), server.receipts);
        }
    }

    @Test
    void revokesLoadedUserWithoutSavingWhileAllSlotsArePinned() {
        try (var server = new Server()) {
            var pinned = new ArrayList<CompletableFuture<Void>>();
            for (int i = 0; i < 8; i++) {
                var player = server.player(true);
                var save = new CompletableFuture<Void>();
                pinned.add(save);
                player.saveResults.add(save);
                server.target(player, false);
            }
            server.poll();
            assertTrue(server.players.values().stream().allMatch(player -> player.saves.size() == 1));

            var owner = server.player(true);
            owner.live.addAll(Set.of(server.moderator, server.custodian));
            owner.stored = Set.copyOf(owner.live);
            owner.primary = "custodian";
            long revoke = server.target(owner, false);
            server.poll();

            assertEquals(Set.of(server.custodian), owner.live);
            assertEquals("custodian", owner.primary);
            assertTrue(owner.stored.contains(server.moderator));
            assertTrue(owner.saves.isEmpty());
            assertTrue(server.receipts.isEmpty());

            pinned.getFirst().complete(null);
            server.drain();
            server.poll();
            assertEquals(List.of(Set.of(server.custodian)), owner.saves);
            assertEquals(Set.of(server.custodian), owner.stored);
            assertTrue(server.confirmed.contains(revoke));
        }
    }

    @Test
    void ignoresDelayedRevokeAfterNewerGrantCompletesAndReleasesSlot() {
        try (var server = new Server()) {
            var player = server.player(true);
            player.live.add(server.moderator);
            var oldSave = new CompletableFuture<Void>();
            player.saveResults.add(oldSave);
            long revoke = server.target(player, false);
            server.poll();

            var delayedPoll = new CompletableFuture<Void>();
            server.pollResults.add(delayedPoll);
            server.poll(); // Read the old revoke but delay delivering the snapshot to Paper.
            long grant = server.target(player, true);
            oldSave.complete(null);
            server.drain();
            assertEquals(List.of(revoke, grant), server.receipts);
            assertEquals(Set.of(grant), server.confirmed);

            delayedPoll.complete(null);
            server.drain();
            assertEquals(Set.of(server.moderator), player.live);
            assertEquals(Set.of(server.moderator), player.stored);
            assertEquals(2, player.saves.size());
        }
    }

    @Test
    void supersededReceiptTriggersCorrectionBeforeNextPoll() {
        try (var server = new Server()) {
            var player = server.player(true);
            var oldSave = new CompletableFuture<Void>();
            player.saveResults.add(oldSave);
            long grant = server.target(player, true);
            server.poll();
            long revoke = server.target(player, false);

            oldSave.complete(null);
            server.drain();

            assertEquals(List.of(grant, revoke), server.receipts);
            assertFalse(player.live.contains(server.moderator));
            assertFalse(player.stored.contains(server.moderator));
            assertEquals(Set.of(revoke), server.confirmed);
        }
    }

    /** Only storage/IPC completion are simulated; the coordinator and LuckPerms projection both run. */
    private static final class Server implements AutoCloseable {
        private final MockedStatic<InheritanceNode> nodes = mockStatic(InheritanceNode.class);
        private final InheritanceNode moderator = node("moderator");
        private final InheritanceNode custodian = node("custodian");
        private final Map<UUID, Account> players = new HashMap<>();
        private final Map<UUID, GovernanceGameGrant> targets = new HashMap<>();
        private final Map<Long, UUID> issued = new HashMap<>();
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        private final ArrayDeque<CompletableFuture<Void>> pollResults = new ArrayDeque<>();
        private final List<Long> receipts = new ArrayList<>();
        private final Set<Long> confirmed = new HashSet<>();
        private final GovernanceDeliveryCoordinator coordinator;

        private Server() {
            node("member");
            var users = mock(UserManager.class);
            when(users.getUser(any(UUID.class))).thenAnswer(call -> players.get(call.getArgument(0)).loaded.getNow(null));
            when(users.loadUser(any(UUID.class))).thenAnswer(call -> players.get(call.getArgument(0)).loaded);
            when(users.saveUser(any(User.class))).thenAnswer(call -> {
                User user = call.getArgument(0);
                return players.get(user.getUniqueId()).save();
            });
            var permissions = mock(LuckPerms.class);
            when(permissions.getUserManager()).thenReturn(users);
            var requests = mock(RequestPort.class);
            when(requests.governance(any())).thenAnswer(call -> {
                GovernanceCommand command = call.getArgument(0);
                var result = reply(command);
                if (command.hasPollDeliveries() && !pollResults.isEmpty()) {
                    return pollResults.removeFirst().thenApply(_ -> result);
                }
                return CompletableFuture.completedFuture(result);
            });
            var plugin = mock(JavaPlugin.class);
            var logger = mock(Logger.class);
            when(plugin.getLogger()).thenReturn(logger);
            var worker = mock(ExecutorService.class);
            doAnswer(call -> { tasks.add(call.getArgument(0)); return null; }).when(worker).execute(any());
            coordinator = new GovernanceDeliveryCoordinator(plugin, new GovernanceGateway(requests),
                    new LuckPermsGovernanceRoles(permissions, _ -> { }), mock(GovernanceText.class), worker);
        }

        private InheritanceNode node(String group) {
            var node = mock(InheritanceNode.class);
            var builder = mock(InheritanceNode.Builder.class);
            when(builder.build()).thenReturn(node);
            nodes.when(() -> InheritanceNode.builder(group)).thenReturn(builder);
            return node;
        }

        private long target(Account player, boolean enabled) {
            long generation = issued.size() + 1L;
            issued.put(generation, player.id);
            targets.put(player.id, GovernanceGameGrant.newBuilder().setId(generation).setPlayerId(player.id.toString())
                    .setEnabled(enabled).setPermission(GovernanceGameGrant.Permission.MODERATOR).build());
            return generation;
        }

        private GovernanceResult reply(GovernanceCommand command) {
            var result = GovernanceResult.newBuilder();
            switch (command.getOperationCase()) {
                case POLL_DELIVERIES -> targets.values().stream()
                        .filter(target -> !confirmed.contains(target.getId()))
                        .filter(target -> !target.getEnabled() || !command.getPollDeliveries().getDeferredPlayersList().contains(target.getPlayerId()))
                        .sorted(Comparator.comparing(GovernanceGameGrant::getEnabled)).forEach(result::addGrants);
                case QUERY -> {
                    var query = command.getQuery();
                    assertEquals(GovernanceQuery.View.GRANT_TARGETS, query.getView());
                    var target = targets.get(UUID.fromString(query.getPlayerId()));
                    if (target != null && !confirmed.contains(target.getId())) result.addGrants(target);
                }
                case CONFIRM -> {
                    for (long generation : command.getConfirm().getGrantsList()) {
                        receipts.add(generation);
                        var current = targets.get(issued.get(generation));
                        if (current.getId() == generation) confirmed.add(generation);
                        else result.addGrants(current);
                    }
                }
                default -> throw new AssertionError(command);
            }
            return result.build();
        }

        private Account player(boolean ready) {
            var account = new Account();
            players.put(account.id, account);
            if (ready) account.loaded.complete(account.user);
            return account;
        }

        private void poll() { coordinator.poll(); drain(); }

        private void drain() { while (!tasks.isEmpty()) tasks.removeFirst().run(); }

        @Override public void close() { coordinator.close(); nodes.close(); }
    }

    private static final class Account {
        private final UUID id = UUID.randomUUID();
        private final User user = mock(User.class);
        private final CompletableFuture<User> loaded = new CompletableFuture<>();
        private final Set<Node> live = new HashSet<>();
        private final List<Set<Node>> saves = new ArrayList<>();
        private final ArrayDeque<CompletableFuture<Void>> saveResults = new ArrayDeque<>();
        private String primary = "default";
        private Set<Node> stored = Set.of();

        private Account() {
            var data = mock(NodeMap.class);
            when(user.getUniqueId()).thenReturn(id);
            when(user.getNodes()).thenReturn(live);
            when(user.data()).thenReturn(data);
            when(user.getPrimaryGroup()).thenAnswer(_ -> primary);
            when(user.setPrimaryGroup(any())).thenAnswer(call -> {
                primary = call.getArgument(0);
                return DataMutateResult.SUCCESS;
            });
            when(data.add(any())).thenAnswer(call -> live.add(call.getArgument(0))
                    ? DataMutateResult.SUCCESS : DataMutateResult.FAIL_ALREADY_HAS);
            when(data.remove(any())).thenAnswer(call -> live.remove(call.getArgument(0))
                    ? DataMutateResult.SUCCESS : DataMutateResult.FAIL_LACKS);
        }

        private CompletableFuture<Void> save() {
            Set<Node> snapshot = Set.copyOf(live);
            saves.add(snapshot);
            var next = saveResults.isEmpty() ? CompletableFuture.<Void>completedFuture(null) : saveResults.removeFirst();
            return next.thenRun(() -> stored = snapshot);
        }
    }
}
