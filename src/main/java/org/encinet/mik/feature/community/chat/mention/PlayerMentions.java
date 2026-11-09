package org.encinet.mik.feature.community.chat.mention;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Per-message visibility and sender exclusions over reusable name indexes. */
public record PlayerMentions(
        PlayerMentionIndex online,
        PlayerMentionIndex bound,
        Set<UUID> allowedOnline,
        UUID excludedPlayer
) {
    private static final PlayerMentions EMPTY = new PlayerMentions(
            PlayerMentionIndex.empty(), PlayerMentionIndex.empty(), Set.of(), null);

    public PlayerMentions {
        online = Objects.requireNonNull(online, "online");
        bound = Objects.requireNonNull(bound, "bound");
        allowedOnline = Set.copyOf(allowedOnline);
    }

    public static PlayerMentions empty() {
        return EMPTY;
    }

    public static PlayerMentions of(Collection<PlayerMentionIndex.Target> targets) {
        PlayerMentionIndex index = PlayerMentionIndex.of(targets);
        return new PlayerMentions(index, PlayerMentionIndex.empty(), index.playerIds(), null);
    }

    /** Adds visible participants from another transport without granting offline or self mentions. */
    public PlayerMentions including(Collection<PlayerMentionIndex.Target> targets) {
        if (targets.isEmpty()) return this;
        Set<UUID> allowed = new java.util.HashSet<>(allowedOnline);
        targets.stream().map(PlayerMentionIndex.Target::id)
                .filter(id -> !id.equals(excludedPlayer)).forEach(allowed::add);
        return new PlayerMentions(online.including(targets), bound, allowed, excludedPlayer);
    }

    public Match find(String text, int fromIndex) {
        long lengths = (allowedOnline.isEmpty() ? 0 : online.lengths()) | bound.lengths();
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
            while (nameStart < text.length() && Character.isWhitespace(text.charAt(nameStart))) {
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
                String name = key.toString();
                PlayerMentionIndex.Target target = online.lookup(name, length, allowedOnline, excludedPlayer);
                if (target == null) {
                    target = bound.lookup(name, length, null, excludedPlayer);
                }
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
