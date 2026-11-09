package org.encinet.mik.feature.player.profile.nametag;

import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.ChatMetaNode;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.SuffixNode;
import org.encinet.mik.feature.player.profile.PlayerNameTag;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/** A player's portable, directly configured decoration, without inherited role or platform metadata. */
record NameTagStyle(UUID id, String name, PlayerNameTag tag) {
    NameTagStyle {
        if (name == null || name.isBlank()) name = id.toString();
    }

    static Optional<PlayerNameTag> configured(Collection<? extends Node> nodes) {
        PlayerNameTag tag = new PlayerNameTag(value(nodes, NameTagField.PREFIX), value(nodes, NameTagField.SUFFIX));
        return tag.prefix().isEmpty() && tag.suffix().isEmpty() ? Optional.empty() : Optional.of(tag);
    }

    private static String value(Collection<? extends Node> nodes, NameTagField field) {
        return nodes.stream().filter(node -> field == NameTagField.PREFIX ? node instanceof PrefixNode : node instanceof SuffixNode)
                .filter(node -> node.getValue() && !node.hasExpiry() && node.getContexts().isEmpty())
                .map(node -> (ChatMetaNode<?, ?>) node)
                .sorted(Comparator.<ChatMetaNode<?, ?>>comparingInt(ChatMetaNode::getPriority).reversed()
                        .thenComparing(ChatMetaNode::getMetaValue))
                .map(ChatMetaNode::getMetaValue).findFirst().orElse("");
    }
}
