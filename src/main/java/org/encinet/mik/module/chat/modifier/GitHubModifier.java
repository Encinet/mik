package org.encinet.mik.module.chat.modifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GitHubModifier implements ChatModifier {

    private static final int MAX_LABEL_LENGTH = 48;
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}_@.-])(?:https?://)?(?:www\\.)?github\\.com"
                    + "(?![\\p{L}\\p{N}.-])(?:[/?#][^\\s<>]*)?"
    );

    @Override
    public ChatReplacement find(String text, int fromIndex, ChatModifierContext context) {
        Matcher matcher = URL_PATTERN.matcher(text);
        if (!matcher.find(fromIndex)) {
            return null;
        }

        String token = matcher.group();
        int linkLength = urlEnd(token);
        String link = token.substring(0, linkLength);
        String url = normalizedUrl(link);
        return new ChatReplacement(
                matcher.start(),
                matcher.start() + linkLength,
                ChatRenderUtil.github(labelFor(link), url)
        );
    }

    private String labelFor(String link) {
        List<String> segments = pathSegments(link);
        String label;
        if (segments.isEmpty()) {
            label = "GitHub";
        } else if (segments.size() == 1) {
            label = "GitHub: " + segments.getFirst();
        } else {
            String repository = segments.get(0) + "/" + stripGitSuffix(segments.get(1));
            label = repositoryLabel(repository, segments);
        }
        if (label.length() > MAX_LABEL_LENGTH) {
            label = label.substring(0, MAX_LABEL_LENGTH - 3) + "...";
        }
        return "[" + label + "]";
    }

    private String repositoryLabel(String repository, List<String> segments) {
        if (segments.size() < 4) {
            return "GitHub: " + repository;
        }
        String route = segments.get(2).toLowerCase(Locale.ROOT);
        String value = segments.get(3);
        return switch (route) {
            case "issues" -> "GitHub: " + repository + " #" + value;
            case "pull" -> "GitHub: " + repository + " PR #" + value;
            case "commit" -> "GitHub: " + repository + " @" + abbreviateCommit(value);
            case "blob", "tree" -> "GitHub: " + repository + " / " + segments.getLast();
            case "releases" -> releaseLabel(repository, segments);
            default -> "GitHub: " + repository;
        };
    }

    private String releaseLabel(String repository, List<String> segments) {
        if (segments.size() >= 5 && "tag".equalsIgnoreCase(segments.get(3))) {
            return "GitHub: " + repository + " / " + segments.get(4);
        }
        return "GitHub: " + repository;
    }

    private List<String> pathSegments(String link) {
        int scheme = link.indexOf("://");
        int hostStart = scheme >= 0 ? scheme + 3 : 0;
        int pathStart = link.indexOf('/', hostStart);
        if (pathStart < 0 || pathStart + 1 >= link.length()) {
            return List.of();
        }

        int pathEnd = link.length();
        int queryStart = link.indexOf('?', pathStart);
        int fragmentStart = link.indexOf('#', pathStart);
        if (queryStart >= 0) {
            pathEnd = queryStart;
        }
        if (fragmentStart >= 0) {
            pathEnd = Math.min(pathEnd, fragmentStart);
        }

        List<String> segments = new ArrayList<>();
        for (String segment : link.substring(pathStart + 1, pathEnd).split("/")) {
            if (!segment.isEmpty()) {
                segments.add(segment);
            }
        }
        return segments;
    }

    private String stripGitSuffix(String repository) {
        return repository.toLowerCase(Locale.ROOT).endsWith(".git")
                ? repository.substring(0, repository.length() - 4)
                : repository;
    }

    private String abbreviateCommit(String commit) {
        return commit.length() <= 7 ? commit : commit.substring(0, 7);
    }

    private String normalizedUrl(String link) {
        if (link.regionMatches(true, 0, "http://", 0, 7)
                || link.regionMatches(true, 0, "https://", 0, 8)) {
            return link;
        }
        return "https://" + link;
    }

    private int urlEnd(String token) {
        int end = token.length();
        while (end > 0 && isTrailingPunctuation(token.codePointBefore(end))) {
            end -= Character.charCount(token.codePointBefore(end));
        }
        return Math.max(end, 1);
    }

    private boolean isTrailingPunctuation(int codePoint) {
        return switch (codePoint) {
            case '.', ',', '!', '?', ':', ';', '\'', '"', ')', ']', '}',
                    '。', '，', '！', '？', '：', '；', '、', '…', '）', '】', '》', '』' -> true;
            default -> false;
        };
    }
}
