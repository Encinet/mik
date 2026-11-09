package org.encinet.mik.feature.community.chat.bridge;

import org.encinet.mik.feature.community.chat.model.ChatChannel;
import org.encinet.mik.feature.community.chat.model.ChatAudience;
import org.encinet.mik.feature.community.chat.model.ChatParticipant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Account presence and current eligible game recipients, independent of message transport. */
public interface ChatParticipants {
    List<ChatParticipant> peers(UUID viewer);
    ChatParticipant peer(UUID viewer, UUID target);
    List<ChatParticipant> channelPeers(UUID viewer, ChatChannel channel, UUID privateTarget);
    Set<UUID> gameRecipients(ChatAudience audience);
    default ChatParticipant findPeer(UUID viewer, String name) {
        return peers(viewer).stream().filter(peer -> peer.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }
}
