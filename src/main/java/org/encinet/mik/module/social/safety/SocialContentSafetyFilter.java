package org.encinet.mik.module.social.safety;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Immutable Aho-Corasick content filter shared by all social platforms. */
public final class SocialContentSafetyFilter {

    private static final int MAXIMUM_REDUNDANCY_SCAN_RULES = 4_096;

    private final boolean enabled;
    private final List<Node> nodes;
    private final int ruleCount;
    private final int removedRuleCount;

    private SocialContentSafetyFilter(
            boolean enabled,
            List<Node> nodes,
            int ruleCount,
            int removedRuleCount
    ) {
        this.enabled = enabled;
        this.nodes = nodes;
        this.ruleCount = ruleCount;
        this.removedRuleCount = removedRuleCount;
    }

    public static SocialContentSafetyFilter disabled() {
        return new SocialContentSafetyFilter(false, List.of(), 0, 0);
    }

    public static SocialContentSafetyFilter compile(Path source) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            var iterator = reader.lines().iterator();
            return compile(() -> iterator, source.getFileName().toString());
        } catch (UncheckedIOException error) {
            throw error.getCause();
        }
    }

    public static SocialContentSafetyFilter compile(Iterable<String> source, String sourceName) {
        LinkedHashMap<String, Integer> unique = new LinkedHashMap<>();
        int lineNumber = 0;
        int sourceRules = 0;
        for (String line : source) {
            lineNumber++;
            String raw = line == null ? "" : line.strip();
            if (raw.startsWith("\uFEFF")) {
                raw = raw.substring(1).strip();
            }
            if (raw.isEmpty() || raw.startsWith("#")) {
                continue;
            }
            sourceRules++;
            String normalized = normalize(raw);
            if (normalized.codePointCount(0, normalized.length()) < 2) {
                throw new IllegalArgumentException(sourceName + " line " + lineNumber
                        + " is too short after normalization");
            }
            unique.putIfAbsent(normalized, lineNumber);
        }
        if (unique.isEmpty()) {
            throw new IllegalArgumentException(sourceName
                    + " has no usable rules while content safety is enabled");
        }

        List<Map.Entry<String, Integer>> candidates = new ArrayList<>(unique.entrySet());
        candidates.sort(java.util.Comparator
                .comparingInt((Map.Entry<String, Integer> entry) -> entry.getKey().length())
                .thenComparing(Map.Entry::getKey));
        LinkedHashMap<String, Integer> retained = new LinkedHashMap<>();
        if (candidates.size() > MAXIMUM_REDUNDANCY_SCAN_RULES) {
            for (Map.Entry<String, Integer> candidate : candidates) {
                retained.put(candidate.getKey(), candidate.getValue());
            }
        } else {
            for (Map.Entry<String, Integer> candidate : candidates) {
                boolean redundant = retained.keySet().stream()
                        .anyMatch(candidate.getKey()::contains);
                if (!redundant) {
                    retained.put(candidate.getKey(), candidate.getValue());
                }
            }
        }

        List<Node> nodes = new ArrayList<>();
        nodes.add(new Node());
        for (Map.Entry<String, Integer> rule : retained.entrySet()) {
            int state = 0;
            for (int offset = 0; offset < rule.getKey().length();) {
                int codePoint = rule.getKey().codePointAt(offset);
                offset += Character.charCount(codePoint);
                Integer next = nodes.get(state).transitions.get(codePoint);
                if (next == null) {
                    next = nodes.size();
                    nodes.get(state).transitions.put(codePoint, next);
                    nodes.add(new Node());
                }
                state = next;
            }
            nodes.get(state).ruleLine = rule.getValue();
        }
        buildFailureLinks(nodes);
        return new SocialContentSafetyFilter(true, freeze(nodes), retained.size(),
                sourceRules - retained.size());
    }

    public Optional<Match> match(String content) {
        if (!enabled || content == null) {
            return Optional.empty();
        }
        String normalized = normalize(content);
        int state = 0;
        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset);
            offset += Character.charCount(codePoint);
            Integer next = nodes.get(state).transitions.get(codePoint);
            while (next == null && state != 0) {
                state = nodes.get(state).failure;
                next = nodes.get(state).transitions.get(codePoint);
            }
            state = next == null ? 0 : next;
            if (nodes.get(state).ruleLine != 0) {
                return Optional.of(new Match(nodes.get(state).ruleLine));
            }
        }
        return Optional.empty();
    }

    public boolean enabled() {
        return enabled;
    }

    public int ruleCount() {
        return ruleCount;
    }

    public int stateCount() {
        return nodes.size();
    }

    public int removedRuleCount() {
        return removedRuleCount;
    }

    public static String normalize(String content) {
        String normalized = Normalizer.normalize(content, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        StringBuilder compact = new StringBuilder(normalized.length());
        normalized.codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(compact::appendCodePoint);
        return compact.toString();
    }

    private static void buildFailureLinks(List<Node> nodes) {
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int child : nodes.getFirst().transitions.values()) {
            queue.add(child);
        }
        while (!queue.isEmpty()) {
            int state = queue.removeFirst();
            for (Map.Entry<Integer, Integer> transition
                    : nodes.get(state).transitions.entrySet()) {
                int codePoint = transition.getKey();
                int child = transition.getValue();
                int fallback = nodes.get(state).failure;
                while (fallback != 0
                        && !nodes.get(fallback).transitions.containsKey(codePoint)) {
                    fallback = nodes.get(fallback).failure;
                }
                nodes.get(child).failure = nodes.get(fallback).transitions
                        .getOrDefault(codePoint, 0);
                if (nodes.get(child).ruleLine == 0) {
                    nodes.get(child).ruleLine = nodes.get(nodes.get(child).failure).ruleLine;
                }
                queue.addLast(child);
            }
        }
    }

    private static List<Node> freeze(List<Node> mutable) {
        List<Node> frozen = new ArrayList<>(mutable.size());
        for (Node node : mutable) {
            Node copy = new Node();
            copy.transitions = Map.copyOf(node.transitions);
            copy.failure = node.failure;
            copy.ruleLine = node.ruleLine;
            frozen.add(copy);
        }
        return List.copyOf(frozen);
    }

    public record Match(int ruleLine) {
    }

    private static final class Node {
        private Map<Integer, Integer> transitions = new HashMap<>();
        private int failure;
        private int ruleLine;
    }
}
