package org.encinet.mik.feature.community.social.platform.matrix;

import org.encinet.mik.feature.community.identity.ExternalIdentity;
import org.encinet.mik.feature.community.identity.ExternalIdentityKey;
import org.encinet.mik.feature.community.identity.IdentityMemberships;
import org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixMemberDirectory;
import org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomDirectory;
import org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomMessage;
import org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixSyncProcessor;
import org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixSyncProtocol;
import org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixSyncRequest;
import org.encinet.mik.feature.community.social.runtime.SocialMembershipDirectory;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Owns one /sync-derived member view, its completeness and community evidence. No network I/O. */
final class MatrixSyncState implements AutoCloseable {
    private final MatrixRoomPolicy policy;
    private final Set<String> observedRooms;
    private final MatrixRoomDirectory rooms;
    private final MatrixMemberDirectory members = new MatrixMemberDirectory();
    private final MatrixSyncProcessor processor = new MatrixSyncProcessor(members);
    private final SocialMembershipDirectory memberships;
    private final Map<String, Optional<String>> initialCursors = new LinkedHashMap<>();
    private boolean closed;

    MatrixSyncState(MatrixRoomPolicy policy, MatrixRoomDirectory rooms) {
        this.policy = policy;
        this.observedRooms = policy.observedRoomIds();
        this.rooms = rooms;
        memberships = new SocialMembershipDirectory(
                MatrixAccountIdentity::owns,
                policy.membershipRoomIds());
    }

    synchronized List<MatrixRoomMessage> apply(MatrixSyncProtocol.Batch received, MatrixSyncRequest request, String botUserId) {
        if (closed) return List.of();
        List<MatrixSyncProtocol.RoomBatch> joined = received.rooms().stream()
                .filter(room -> observedRooms.contains(room.roomId())).toList();
        Set<String> left = new LinkedHashSet<>(received.leftRooms());
        if (request.completeState()) {
            joined.forEach(room -> MatrixMemberDirectory.validateFullState(room, botUserId));
            // A forgotten room can disappear without another leave event. Full state enumerates all joined rooms.
            Set<String> absent = new LinkedHashSet<>(observedRooms);
            joined.forEach(room -> absent.remove(room.roomId()));
            left.addAll(absent);
        }
        left.retainAll(observedRooms);
        MatrixSyncProtocol.Batch batch = new MatrixSyncProtocol.Batch(received.nextBatch(), joined, List.copyOf(left));
        batch.leftRooms().forEach(initialCursors::remove);
        batch.rooms().forEach(room -> initialCursors.putIfAbsent(room.roomId(), request.since()));
        rooms.apply(batch);
        List<MatrixRoomMessage> messages = processor.process(batch, request.completeState()).stream()
                .filter(message -> policy.acceptsMessages(message.roomId()))
                .filter(message -> live(message.roomId(), request)).toList();
        publishMemberships(botUserId);
        return messages;
    }

    private void publishMemberships(String botUserId) {
        Map<String, Set<ExternalIdentityKey>> loaded = new LinkedHashMap<>();
        for (String roomId : policy.membershipRoomIds()) {
            if (!members.hasCompleteRoster(roomId)) continue;
            Set<ExternalIdentityKey> identities = members.joinedUserIds(roomId).stream()
                    .filter(userId -> !userId.equals(botUserId))
                    .map(userId -> new MatrixRoomMessage.MatrixMember(userId, userId))
                    .map(MatrixAccountIdentity::from).flatMap(Optional::stream)
                    .map(ExternalIdentity::key).collect(Collectors.toUnmodifiableSet());
            loaded.put(roomId, identities);
        }
        memberships.replace(memberships.revision(), loaded);
    }

    synchronized boolean requiresFullState() {
        return !closed && initialCursors.keySet().stream().anyMatch(roomId -> !members.hasCompleteRoster(roomId));
    }

    IdentityMemberships memberships() {
        return memberships;
    }

    Optional<MatrixRoomMessage.MatrixMember> joinedMember(String roomId, String userId) {
        return rooms.allowsMessages(roomId) && policy.acceptsMessages(roomId)
                ? members.joinedMember(roomId, userId) : Optional.empty();
    }

    synchronized boolean live(String roomId, MatrixSyncRequest request) {
        return !closed && initialCursors.containsKey(roomId)
                && !request.since().equals(initialCursors.get(roomId));
    }

    synchronized Optional<MatrixRoomMessage> decrypted(String roomId, MatrixSyncProtocol.Event event) {
        return !closed && rooms.encryptedMessageRoom(roomId)
                && policy.acceptsMessages(roomId) ? processor.message(roomId, event) : Optional.empty();
    }

    synchronized void disconnected() {
        rooms.suspend();
        memberships.invalidate();
    }

    synchronized void reset() {
        disconnected();
        processor.clear();
        initialCursors.clear();
        rooms.clear();
    }

    @Override
    public synchronized void close() {
        closed = true;
        memberships.close();
        processor.clear();
        initialCursors.clear();
        rooms.close();
    }
}
