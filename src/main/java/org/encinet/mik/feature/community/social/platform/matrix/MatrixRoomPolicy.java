package org.encinet.mik.feature.community.social.platform.matrix;

import org.bukkit.configuration.file.YamlConfiguration;
import org.encinet.mik.feature.community.social.api.SocialConversation;
import org.encinet.mik.feature.community.social.chat.SocialChatPolicy;
import org.encinet.mik.feature.community.social.chat.SocialChatRoute;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Explicit, independent grants for community membership, commands, and chat delivery. */
public record MatrixRoomPolicy(
        Set<String> membershipRoomIds,
        Set<String> commandRoomIds,
        List<SocialChatRoute> chatRoutes
) {
    public MatrixRoomPolicy {
        membershipRoomIds = validatedIds(membershipRoomIds, "membership.room-ids");
        commandRoomIds = validatedIds(commandRoomIds, "commands.room-ids");
        chatRoutes = List.copyOf(chatRoutes);
        Set<String> observed = new LinkedHashSet<>(membershipRoomIds);
        observed.addAll(commandRoomIds);
        Set<String> routed = new LinkedHashSet<>();
        for (SocialChatRoute route : chatRoutes) {
            String id = route.conversation().id();
            if (!roomId(id, "chat-bridge.routes room-id").equals(id)
                    || route.conversation().type() != SocialConversation.Type.GROUP) {
                throw new IllegalArgumentException("Matrix chat routes require group room IDs");
            }
            if (!routed.add(id)) throw new IllegalArgumentException("Duplicate Matrix chat route: " + id);
        }
        observed.addAll(routed);
        if (observed.size() > 256) {
            throw new IllegalArgumentException("Matrix supports at most 256 distinct configured rooms");
        }
    }

    static MatrixRoomPolicy from(YamlConfiguration yaml) {
        Set<String> membershipRooms = roomIds(yaml, "membership.room-ids");
        Set<String> commandRooms = roomIds(yaml, "commands.room-ids");
        List<SocialChatRoute> routes = new ArrayList<>();
        for (Object value : list(yaml, "chat-bridge.routes")) {
            if (!(value instanceof Map<?, ?> entry)) {
                throw new IllegalArgumentException("chat-bridge.routes entries must be mappings");
            }
            String roomId = roomId(entry.get("room-id"), "chat-bridge.routes room-id");
            routes.add(new SocialChatRoute(
                    new SocialConversation(roomId, SocialConversation.Type.GROUP),
                    SocialChatPolicy.fromConfig(entry.get("inbound"), "chat-bridge.routes inbound"),
                    SocialChatPolicy.fromConfig(entry.get("outbound"), "chat-bridge.routes outbound")));
        }
        return new MatrixRoomPolicy(membershipRooms, commandRooms, routes);
    }

    public Set<String> observedRoomIds() {
        Set<String> observed = new LinkedHashSet<>(membershipRoomIds);
        observed.addAll(commandRoomIds);
        chatRoutes.forEach(route -> observed.add(route.conversation().id()));
        return Collections.unmodifiableSet(observed);
    }

    public boolean acceptsMessages(String roomId) {
        return commandRoomIds.contains(roomId) || chatRoute(roomId).isPresent();
    }

    public Optional<SocialChatRoute> chatRoute(String roomId) {
        return chatRoutes.stream()
                .filter(route -> route.conversation().id().equals(roomId))
                .findFirst();
    }

    private static Set<String> roomIds(YamlConfiguration yaml, String path) {
        Set<String> values = new LinkedHashSet<>();
        for (Object value : list(yaml, path)) {
            values.add(roomId(value, path));
        }
        return values;
    }

    private static List<?> list(YamlConfiguration yaml, String path) {
        Object value = yaml.get(path);
        if (value == null) return List.of();
        if (value instanceof List<?> values) return values;
        throw new IllegalArgumentException(path + " must be a list");
    }

    private static Set<String> validatedIds(Set<String> ids, String field) {
        Set<String> checked = new LinkedHashSet<>();
        ids.forEach(value -> checked.add(roomId(value, field)));
        return Collections.unmodifiableSet(checked);
    }

    private static String roomId(Object value, String field) {
        String id = value instanceof String text ? text.strip() : "";
        if (!id.startsWith("!") || id.length() < 2 || id.length() > 255
                || id.codePoints().anyMatch(character -> Character.isWhitespace(character)
                || Character.isISOControl(character))) {
            throw new IllegalArgumentException(field + " entries must be Matrix room IDs beginning with '!'");
        }
        return id;
    }
}
