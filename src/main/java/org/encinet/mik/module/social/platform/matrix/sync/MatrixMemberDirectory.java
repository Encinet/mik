package org.encinet.mik.module.social.platform.matrix.sync;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Current joined-member snapshots used by inbound mapping and outbound mentions. */
public final class MatrixMemberDirectory {
    private final Map<String, Map<String, String>> joinedByRoom =
            new LinkedHashMap<>();

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
        joinedByRoom.put(room, Map.copyOf(members));
    }

    synchronized void applyMember(
            String roomId,
            MatrixSyncProtocol.Event event
    ) {
        if (!"m.room.member".equals(event.type()) || event.stateKey().isBlank()) {
            return;
        }
        Map<String, String> current = new LinkedHashMap<>(
                joinedByRoom.getOrDefault(roomId, Map.of()));
        String membership = MatrixSyncProtocol.string(event.content(), "membership");
        if ("join".equals(membership)) {
            current.put(event.stateKey(), cleanDisplayName(
                    MatrixSyncProtocol.string(event.content(), "displayname"),
                    event.stateKey()));
        } else {
            current.remove(event.stateKey());
        }
        joinedByRoom.put(roomId, Map.copyOf(current));
    }

    public synchronized boolean isJoined(String roomId, String userId) {
        return joinedByRoom.getOrDefault(roomId, Map.of()).containsKey(userId);
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

    private static String requireText(String value, String field) {
        String checked = Objects.requireNonNull(value, field).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return checked;
    }
}
