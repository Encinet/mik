package org.encinet.mik.feature.community.social.platform.matrix.sync;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Current joined-member snapshots used by inbound mapping and outbound mentions. */
public final class MatrixMemberDirectory {
    private final Map<String, Map<String, String>> joinedByRoom =
            new LinkedHashMap<>();
    private final java.util.Set<String> completeRooms = new java.util.HashSet<>();

    public synchronized void replaceJoined(
            String roomId,
            Map<String, String> displayNames
    ) {
        String room = requireText(roomId, "roomId");
        Map<String, String> members = new LinkedHashMap<>();
        Objects.requireNonNull(displayNames, "displayNames")
                .forEach((userId, displayName) -> {
                    if (userId != null && !userId.isBlank()) {
                        members.put(userId, cleanDisplayName(displayName, userId));
                    }
                });
        joinedByRoom.put(room, members);
        completeRooms.add(room);
    }

    public synchronized boolean hasCompleteRoster(String roomId) {
        return completeRooms.contains(roomId);
    }

    /** A malformed full state cannot establish absence and revoke existing bindings. */
    public static void validateFullState(MatrixSyncProtocol.RoomBatch room, String botUserId) {
        boolean botJoined = false;
        for (MatrixSyncProtocol.Event event : room.stateUpdates().toList()) {
            if (!"m.room.member".equals(event.type())) continue;
            String membership = MatrixSyncProtocol.string(event.content(), "membership");
            if (event.stateKey().isBlank() || !validMembership(membership)) {
                throw new IllegalArgumentException("Invalid Matrix member in full state for " + room.roomId());
            }
            if (botUserId.equals(event.stateKey())) botJoined = "join".equals(membership);
        }
        if (!botJoined) {
            throw new IllegalArgumentException("Matrix full state omitted the joined bot member for " + room.roomId());
        }
    }

    synchronized void applyMember(
            String roomId,
            MatrixSyncProtocol.Event event
    ) {
        if (!"m.room.member".equals(event.type()) || event.stateKey().isBlank()) {
            return;
        }
        String membership = MatrixSyncProtocol.string(event.content(), "membership");
        if (!validMembership(membership)) return;
        Map<String, String> current = joinedByRoom.computeIfAbsent(roomId, _ -> new LinkedHashMap<>());
        if ("join".equals(membership)) {
            current.put(event.stateKey(), cleanDisplayName(
                    MatrixSyncProtocol.string(event.content(), "displayname"),
                    event.stateKey()));
        } else {
            current.remove(event.stateKey());
        }
    }

    public synchronized boolean isJoined(String roomId, String userId) {
        return joinedByRoom.getOrDefault(roomId, Map.of()).containsKey(userId);
    }

    public synchronized java.util.Set<String> joinedUserIds(String roomId) {
        return java.util.Set.copyOf(joinedByRoom.getOrDefault(roomId, Map.of()).keySet());
    }

    public synchronized Optional<MatrixRoomMessage.MatrixMember> joinedMember(
            String roomId,
            String userId
    ) {
        String name = joinedByRoom.getOrDefault(roomId, Map.of()).get(userId);
        return name == null ? Optional.empty()
                : Optional.of(new MatrixRoomMessage.MatrixMember(userId, name));
    }

    synchronized MatrixRoomMessage.MatrixMember member(
            String roomId,
            String userId
    ) {
        return joinedMember(roomId, userId).orElseGet(() ->
                new MatrixRoomMessage.MatrixMember(userId, userId));
    }

    public synchronized void clear() {
        joinedByRoom.clear();
        completeRooms.clear();
    }

    synchronized void removeRoom(String roomId) {
        joinedByRoom.remove(roomId);
        completeRooms.remove(roomId);
    }

    private static String cleanDisplayName(String value, String fallback) {
        String clean = value == null ? "" : value.strip();
        if (clean.isEmpty()) {
            return fallback;
        }
        StringBuilder result = new StringBuilder(Math.min(clean.length(), 64));
        for (int index = 0; index < clean.length() && result.length() < 64; index++) {
            char character = clean.charAt(index);
            result.append(Character.isISOControl(character) ? ' ' : character);
        }
        return result.toString().strip();
    }

    private static boolean validMembership(String membership) {
        return switch (membership) {
            case "join", "leave", "ban", "invite", "knock" -> true;
            default -> false;
        };
    }

    private static String requireText(String value, String field) {
        String checked = Objects.requireNonNull(value, field).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return checked;
    }
}
