package org.encinet.mik.module.chat.modifier;

import org.encinet.mik.module.chat.model.ChatCapability;
import org.encinet.mik.module.chat.model.ChatProcessingContext;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GitHubModifier implements ChatModifier {

    private static final String ACCOUNT =
            "[a-z0-9](?:[a-z0-9-]{0,37}[a-z0-9])?";
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)(?<![a-z0-9_@.-])(?:https?://)?(?:"
                    + "(?:www\\.)?github\\.com"
                    + "|gist\\.github\\.com"
                    + "|api\\.github\\.com"
                    + "|github\\.dev"
                    + "|" + ACCOUNT + "\\.github\\.io"
                    + "|(?:raw|gist)\\.githubusercontent\\.com"
                    + ")(?![a-z0-9_@.-]|:[0-9])(?:[/?#][^\\s<>]*)?"
    );
    private static final Pattern GIST_ID = Pattern.compile("(?i)[0-9a-f]{5,}");

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public Set<ChatCapability> requiredCapabilities() {
        return Set.of(ChatCapability.URL);
    }

    @Override
    public ChatReplacement find(
            String text, int fromIndex, ChatProcessingContext context
    ) {
        Matcher matcher = URL_PATTERN.matcher(text);
        if (!matcher.find(fromIndex)) {
            return null;
        }

        String token = matcher.group();
        int linkLength = ChatUrlSupport.visibleUrlEnd(token);
        String link = token.substring(0, linkLength);
        String url = ChatUrlSupport.normalizedHttpUrl(link);
        return new ChatReplacement(
                matcher.start(),
                matcher.start() + linkLength,
                ChatLinkPresentation.link(labelFor(link), URI.create(url),
                        ChatLinkPalette.GITHUB)
        );
    }

    private String labelFor(String link) {
        String host = host(link).toLowerCase(Locale.ROOT);
        List<String> segments = pathSegments(link);
        String label;
        if ("github.com".equals(host) || "www.github.com".equals(host)) {
            label = githubLabel(segments);
        } else if ("gist.github.com".equals(host)) {
            label = gistLabel(segments, "GitHub Gist");
        } else if (host.endsWith(".github.io")) {
            label = pagesLabel(host, segments);
        } else if ("github.dev".equals(host)) {
            label = repositoryLabel("GitHub.dev", segments);
        } else if ("raw.githubusercontent.com".equals(host)) {
            label = rawLabel(segments);
        } else if ("gist.githubusercontent.com".equals(host)) {
            label = gistRawLabel(segments);
        } else {
            label = apiLabel(segments);
        }
        return label;
    }

    private String githubLabel(List<String> segments) {
        if (segments.isEmpty()) {
            return "GitHub";
        }
        if (segments.size() == 1) {
            return "GitHub: " + segments.getFirst();
        }

        String first = segments.getFirst().toLowerCase(Locale.ROOT);
        if (("orgs".equals(first) || "users".equals(first))
                && segments.size() >= 2) {
            return projectLabel(segments);
        }
        if ("sponsors".equals(first) && segments.size() >= 2) {
            return "GitHub Sponsors: " + segments.get(1);
        }
        if ("marketplace".equals(first)) {
            return "GitHub Marketplace";
        }
        if ("settings".equals(first) || "notifications".equals(first)
                || "explore".equals(first) || "topics".equals(first)
                || "collections".equals(first) || "trending".equals(first)
                || "search".equals(first) || "login".equals(first)
                || "signup".equals(first) || "features".equals(first)) {
            return "GitHub: " + segments.getFirst();
        }
        return repositoryLabel("GitHub", segments);
    }

    private String projectLabel(List<String> segments) {
        String owner = segments.get(1);
        if (segments.size() >= 4 && "projects".equalsIgnoreCase(segments.get(2))) {
            return "GitHub: " + owner + " Project #" + segments.get(3);
        }
        return "GitHub: " + owner;
    }

    private String repositoryLabel(String prefix, List<String> segments) {
        if (segments.isEmpty()) {
            return prefix;
        }
        if (segments.size() == 1) {
            return prefix + ": " + segments.getFirst();
        }

        String repository = segments.get(0) + "/" + stripGitSuffix(segments.get(1));
        if (segments.size() < 4) {
            return prefix + ": " + repository;
        }

        String route = segments.get(2).toLowerCase(Locale.ROOT);
        String value = segments.get(3);
        return switch (route) {
            case "issues" -> numericReference(prefix, repository, value, "#");
            case "pull" -> numericReference(prefix, repository, value, "PR #");
            case "discussions" -> numericReference(
                    prefix, repository, value, "Discussion #");
            case "commit" -> prefix + ": " + repository + " @"
                    + abbreviate(value, 7);
            case "blob", "tree" -> prefix + ": " + repository + " / "
                    + segments.getLast();
            case "releases" -> releaseLabel(prefix, repository, segments);
            case "actions" -> actionsLabel(prefix, repository, segments);
            case "wiki" -> prefix + ": " + repository + " / " + value;
            case "compare" -> prefix + ": " + repository + " / " + value;
            case "security" -> securityLabel(prefix, repository, segments);
            default -> prefix + ": " + repository;
        };
    }

    private String numericReference(
            String prefix,
            String repository,
            String value,
            String kind
    ) {
        if (!value.chars().allMatch(Character::isDigit)) {
            return prefix + ": " + repository;
        }
        return prefix + ": " + repository + " " + kind + value;
    }

    private String releaseLabel(
            String prefix,
            String repository,
            List<String> segments
    ) {
        if (segments.size() >= 5 && "tag".equalsIgnoreCase(segments.get(3))) {
            return prefix + ": " + repository + " Release " + segments.get(4);
        }
        if (segments.size() >= 6
                && "download".equalsIgnoreCase(segments.get(3))) {
            return prefix + ": " + repository + " Release " + segments.get(4)
                    + " / " + segments.getLast();
        }
        if ("latest".equalsIgnoreCase(segments.get(3))) {
            return prefix + ": " + repository + " Latest release";
        }
        return prefix + ": " + repository;
    }

    private String actionsLabel(
            String prefix,
            String repository,
            List<String> segments
    ) {
        if (segments.size() >= 5 && "runs".equalsIgnoreCase(segments.get(3))) {
            return prefix + ": " + repository + " Actions #" + segments.get(4);
        }
        if (segments.size() >= 5
                && "workflows".equalsIgnoreCase(segments.get(3))) {
            return prefix + ": " + repository + " Actions / " + segments.get(4);
        }
        return prefix + ": " + repository + " Actions";
    }

    private String securityLabel(
            String prefix,
            String repository,
            List<String> segments
    ) {
        if (segments.size() >= 5
                && "advisories".equalsIgnoreCase(segments.get(3))) {
            return prefix + ": " + repository + " / " + segments.get(4);
        }
        return prefix + ": " + repository + " Security";
    }

    private String gistLabel(List<String> segments, String prefix) {
        if (segments.isEmpty()) {
            return prefix;
        }
        if (segments.size() == 1) {
            String value = stripGitSuffix(segments.getFirst());
            return GIST_ID.matcher(value).matches()
                    ? prefix + ": " + abbreviate(value, 8)
                    : prefix + ": " + value;
        }
        String id = stripGitSuffix(segments.get(1));
        return prefix + ": " + segments.getFirst() + "/" + abbreviate(id, 8);
    }

    private String pagesLabel(String host, List<String> segments) {
        String owner = host.substring(0, host.length() - ".github.io".length());
        if (segments.isEmpty()) {
            return "GitHub Pages: " + owner;
        }
        return "GitHub Pages: " + owner + "/" + segments.getFirst();
    }

    private String rawLabel(List<String> segments) {
        if (segments.size() < 2) {
            return "GitHub Raw";
        }
        String label = "GitHub Raw: " + segments.get(0) + "/"
                + stripGitSuffix(segments.get(1));
        if (segments.size() >= 4) {
            label += " / " + segments.getLast();
        }
        return label;
    }

    private String gistRawLabel(List<String> segments) {
        if (segments.size() < 2) {
            return "GitHub Gist Raw";
        }
        String label = "GitHub Gist Raw: " + segments.get(0) + "/"
                + abbreviate(stripGitSuffix(segments.get(1)), 8);
        if (segments.size() >= 4) {
            label += " / " + segments.getLast();
        }
        return label;
    }

    private String apiLabel(List<String> segments) {
        if (segments.size() >= 3 && "repos".equalsIgnoreCase(segments.getFirst())) {
            return repositoryLabel("GitHub API", segments.subList(1, segments.size()));
        }
        if (segments.size() >= 2 && "gists".equalsIgnoreCase(segments.getFirst())) {
            return "GitHub API Gist: " + abbreviate(segments.get(1), 8);
        }
        if (segments.size() >= 2 && "users".equalsIgnoreCase(segments.getFirst())) {
            return "GitHub API: " + segments.get(1);
        }
        return "GitHub API";
    }

    private String host(String link) {
        int scheme = link.indexOf("://");
        int hostStart = scheme >= 0 ? scheme + 3 : 0;
        int hostEnd = firstIndexOf(link, hostStart, '/', '?', '#');
        return link.substring(hostStart, hostEnd);
    }

    private List<String> pathSegments(String link) {
        int scheme = link.indexOf("://");
        int hostStart = scheme >= 0 ? scheme + 3 : 0;
        int pathStart = link.indexOf('/', hostStart);
        if (pathStart < 0 || pathStart + 1 >= link.length()) {
            return List.of();
        }

        int pathEnd = firstIndexOf(link, pathStart, '?', '#');
        List<String> segments = new ArrayList<>();
        for (String segment : link.substring(pathStart + 1, pathEnd).split("/")) {
            if (!segment.isEmpty()) {
                segments.add(segment);
            }
        }
        return segments;
    }

    private int firstIndexOf(String value, int fromIndex, char... candidates) {
        int first = value.length();
        for (char candidate : candidates) {
            int index = value.indexOf(candidate, fromIndex);
            if (index >= 0) {
                first = Math.min(first, index);
            }
        }
        return first;
    }

    private String stripGitSuffix(String value) {
        return value.toLowerCase(Locale.ROOT).endsWith(".git")
                ? value.substring(0, value.length() - 4)
                : value;
    }

    private String abbreviate(String value, int maximumLength) {
        return value.length() <= maximumLength
                ? value : value.substring(0, maximumLength);
    }

}
