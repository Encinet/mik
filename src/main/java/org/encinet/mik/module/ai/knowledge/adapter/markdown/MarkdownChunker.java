package org.encinet.mik.module.ai.knowledge.adapter.markdown;

import org.encinet.mik.module.ai.knowledge.model.KnowledgeChunk;
import org.encinet.mik.module.ai.knowledge.model.KnowledgeDocument;
import org.encinet.mik.module.ai.knowledge.model.KnowledgeScope;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Heading-aware Markdown chunking that keeps fenced blocks intact where possible. */
public final class MarkdownChunker {
    private final int maximumCharacters;
    private final int overlapCharacters;

    public MarkdownChunker(int maximumCharacters, int overlapCharacters) {
        if (maximumCharacters < 500) {
            throw new IllegalArgumentException("maximumCharacters must be at least 500");
        }
        if (overlapCharacters < 0 || overlapCharacters >= maximumCharacters) {
            throw new IllegalArgumentException("overlapCharacters is outside the valid range");
        }
        this.maximumCharacters = maximumCharacters;
        this.overlapCharacters = overlapCharacters;
    }

    public List<KnowledgeChunk> chunk(KnowledgeDocument document) {
        Objects.requireNonNull(document, "document");
        List<Section> sections = sections(document.body());
        List<KnowledgeChunk> result = new ArrayList<>();
        int ordinal = 0;
        for (Section section : sections) {
            for (String text : split(section.markdown())) {
                if (text.isBlank()) {
                    continue;
                }
                String anchor = "chunk-" + ordinal;
                URI uri = logicalUri(document, anchor);
                result.add(new KnowledgeChunk(document.subjectKey(), document.id(),
                        document.scope(), document.owner(), ordinal++, document.title(),
                        document.language(), document.aliases(), document.tags(),
                        section.heading(), text, uri));
            }
        }
        return List.copyOf(result);
    }

    private List<Section> sections(String markdown) {
        List<Section> result = new ArrayList<>();
        String heading = "";
        StringBuilder current = new StringBuilder();
        boolean fenced = false;
        String fence = "";
        for (String line : markdown.split("\\R", -1)) {
            String stripped = line.stripLeading();
            if (stripped.startsWith("```") || stripped.startsWith("~~~")) {
                String marker = stripped.substring(0, 3);
                if (!fenced) {
                    fenced = true;
                    fence = marker;
                } else if (stripped.startsWith(fence)) {
                    fenced = false;
                    fence = "";
                }
            }
            if (!fenced && stripped.matches("^#{1,6}\\s+.+$")) {
                flush(result, heading, current);
                heading = stripped.replaceFirst("^#{1,6}\\s+", "").strip();
            }
            current.append(line).append('\n');
        }
        flush(result, heading, current);
        return result.isEmpty() ? List.of(new Section("", markdown)) : result;
    }

    private static void flush(List<Section> result, String heading, StringBuilder current) {
        String text = current.toString().strip();
        if (!text.isEmpty()) {
            result.add(new Section(heading, text));
        }
        current.setLength(0);
    }

    private List<String> split(String markdown) {
        if (markdown.length() <= maximumCharacters) {
            return List.of(markdown);
        }
        List<String> blocks = blocks(markdown);
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String block : blocks) {
            if (!current.isEmpty() && current.length() + block.length() + 2
                    > maximumCharacters) {
                String completed = current.toString().strip();
                result.add(completed);
                current.setLength(0);
                String overlap = tail(completed, overlapCharacters);
                if (!overlap.isBlank()) {
                    current.append(overlap).append("\n\n");
                }
            }
            if (block.length() > maximumCharacters) {
                hardSplit(block, result, current);
            } else {
                current.append(block).append("\n\n");
            }
        }
        if (!current.toString().isBlank()) {
            result.add(current.toString().strip());
        }
        return List.copyOf(result);
    }

    private void hardSplit(String block, List<String> result, StringBuilder current) {
        int cursor = 0;
        while (cursor < block.length()) {
            int room = maximumCharacters - current.length();
            if (room < 100) {
                String completed = current.toString().strip();
                if (!completed.isEmpty()) {
                    result.add(completed);
                }
                current.setLength(0);
                String overlap = tail(completed, overlapCharacters);
                if (!overlap.isBlank()) {
                    current.append(overlap).append("\n\n");
                }
                room = maximumCharacters - current.length();
            }
            int end = Math.min(block.length(), cursor + room);
            if (end < block.length()) {
                int boundary = block.lastIndexOf(' ', end);
                if (boundary > cursor + room / 2) {
                    end = boundary;
                }
            }
            current.append(block, cursor, end);
            cursor = end;
        }
    }

    private static List<String> blocks(String markdown) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean fenced = false;
        String fence = "";
        for (String line : markdown.split("\\R", -1)) {
            String stripped = line.stripLeading();
            if (stripped.startsWith("```") || stripped.startsWith("~~~")) {
                String marker = stripped.substring(0, 3);
                if (!fenced) {
                    fenced = true;
                    fence = marker;
                } else if (stripped.startsWith(fence)) {
                    fenced = false;
                    fence = "";
                }
            }
            if (!fenced && line.isBlank() && !current.isEmpty()) {
                result.add(current.toString().stripTrailing());
                current.setLength(0);
            } else {
                current.append(line).append('\n');
            }
        }
        if (!current.isEmpty()) {
            result.add(current.toString().stripTrailing());
        }
        return result;
    }

    private static String tail(String text, int maximum) {
        if (maximum == 0 || text.isEmpty()) {
            return "";
        }
        int start = Math.max(0, text.length() - maximum);
        int boundary = text.indexOf('\n', start);
        if (boundary >= 0 && boundary + 1 < text.length()) {
            start = boundary + 1;
        }
        return text.substring(start).strip();
    }

    private static URI logicalUri(KnowledgeDocument document, String anchor) {
        String authority = document.scope() == KnowledgeScope.PUBLIC
                ? "public" : "user";
        String owner = document.owner().map(value -> "/" + value).orElse("");
        String encodedId = java.net.URLEncoder.encode(document.id(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return URI.create("knowledge://" + authority + owner + '/' + encodedId + '#' + anchor);
    }

    private record Section(String heading, String markdown) {
    }
}
