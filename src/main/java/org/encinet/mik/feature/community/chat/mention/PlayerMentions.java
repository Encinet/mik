package org.encinet.mik.feature.community.chat.mention;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Per-message visibility and sender exclusions over a reusable participant index. */
public record PlayerMentions(
        PlayerMentionIndex index,
        Set<UUID> allowedPlayers,
        UUID excludedPlayer
) {
    private static final PlayerMentions EMPTY = new PlayerMentions(
            PlayerMentionIndex.empty(), Set.of(), null);

    public PlayerMentions {
        index = Objects.requireNonNull(index, "index");
        allowedPlayers = Set.copyOf(allowedPlayers);
    }

    public static PlayerMentions empty() {
        return EMPTY;
    }

    public static PlayerMentions of(Collection<PlayerMentionIndex.Target> targets) {
        PlayerMentionIndex index = PlayerMentionIndex.of(targets);
        return new PlayerMentions(index, index.playerIds(), null);
    }

    public boolean knowsName(String name) { return index.containsName(name); }

    public Match find(String text, int fromIndex) {
        long lengths = allowedPlayers.isEmpty() ? 0 : index.lengths();
        if (lengths == 0) {
            return null;
        }
        int maximumLength = Long.SIZE - Long.numberOfLeadingZeros(lengths);
        StringBuilder key = null;
        for (int start = text.indexOf('@', fromIndex); start >= 0;
             start = text.indexOf('@', start + 1)) {
            if (!MentionBoundaries.isStart(text, start)) {
                continue;
            }
            int nameStart = start + 1;
            while (nameStart < text.length() && (text.charAt(nameStart) == ' ' || text.charAt(nameStart) == '\t')) {
                nameStart++;
            }
            if (key == null) {
                key = new StringBuilder(maximumLength);
            } else {
                key.setLength(0);
            }
            PlayerMentionIndex.Target best = null;
            int bestEnd = nameStart;
            for (int end = nameStart; end < text.length();) {
                int codePoint = text.codePointAt(end);
                end += Character.charCount(codePoint);
                int length = end - nameStart;
                if (length > maximumLength) {
                    break;
                }
                key.appendCodePoint(PlayerMentionIndex.fold(codePoint));
                if ((lengths & (1L << (length - 1))) == 0
                        || !MentionBoundaries.isEnd(text, end)) {
                    continue;
                }
                PlayerMentionIndex.Target target = index.lookup(key.toString(), length, allowedPlayers, excludedPlayer);
                if (target != null) {
                    best = target;
                    bestEnd = end;
                }
            }
            if (best != null) {
                return new Match(start, bestEnd, best);
            }
        }
        return null;
    }

    public record Match(int start, int end, PlayerMentionIndex.Target target) {
    }
}
