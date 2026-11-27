package org.encinet.mik.feature.community.chat.link;

import com.google.gson.Gson;
import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.encinet.mik.feature.community.chat.model.ChatStyle;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatResourceLinks {
    public enum Resource { GENERIC, BILIBILI, GITHUB, MINECRAFT_WIKI, MOJIRA, MATRIX }

    private static final Rules RULES = load();
    private static final Pattern SHORT_LINKS = Pattern.compile(RULES.shortLinks());
    private static final List<Site> SITES = RULES.sites().stream()
            .map(rule -> new Site(Resource.valueOf(rule.resource()), Pattern.compile(rule.host()),
                    rule.name(), rule.paths().stream().map(path ->
                            new PathRule(Pattern.compile(path.pattern()), path.label(), path.target())).toList(), rule.titleQuery()))
            .toList();
    private static final List<CompiledTarget> SHORT_TARGETS = RULES.shortTargets().stream()
            .map(rule -> new CompiledTarget(Pattern.compile(rule.pattern()), rule.target())).toList();

    private ChatResourceLinks() {
    }

    private static Rules load() {
        var resource = ChatResourceLinks.class.getResourceAsStream("/chat/link-rules.json");
        if (resource == null) throw new IllegalStateException("Missing chat link rules");
        try (var reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, Rules.class);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load chat link rules", error);
        }
    }

    public static ChatNode.Reference automatic(String token, URI target) {
        Site site = site(target);
        String label = site == null ? token : site.label(target);
        return generated(label, site == null ? target : site.target(target));
    }

    public static ChatNode.Reference generated(String label, URI target) {
        return new ChatNode.Reference(label, ChatStyle.EMPTY, new ChatNode.Reference.Link(target, true));
    }

    public static String absoluteLinks() {
        return RULES.absoluteLinks();
    }

    static Set<String> trackingParameters() {
        return Set.copyOf(RULES.trackingParameters());
    }

    public static Resource resource(URI target) {
        Site site = site(target);
        return site == null ? Resource.GENERIC : site.resource();
    }

    public static boolean isKnown(URI target) {
        return site(target) != null;
    }

    private static Site site(URI target) {
        String host = target.getHost();
        if (host == null) return null;
        String name = host.toLowerCase(Locale.ROOT);
        if (name.endsWith(".")) name = name.substring(0, name.length() - 1);
        for (Site site : SITES) {
            if (site.host().matcher(name).matches()) return site;
        }
        return null;
    }

    public static ShortLink shortLink(String text, int fromIndex) {
        Matcher matcher = SHORT_LINKS.matcher(text);
        int search = fromIndex;
        while (matcher.find(search)) {
            int start = matcher.start();
            String token = matcher.group();
            token = token.substring(0, ChatUrls.visibleUrlEnd(token));
            int end = start + token.length();
            search = start + 1;
            if (start > 0 && joined(text.charAt(start - 1))
                    || end < text.length() && joined(text.charAt(end))
                    || ChatUrls.insideUrl(text, start)) continue;
            URI target = shortTarget(token);
            if (target != null) {
                return new ShortLink(start, end, automatic(token, target));
            }
        }
        return null;
    }

    private static boolean joined(char ch) {
        return ch <= 127 && (Character.isLetterOrDigit(ch) || "_/-@#!:".indexOf(ch) >= 0);
    }

    private static final String SERVER = "(?:[a-zA-Z0-9][a-zA-Z0-9.\\-]*|\\[[a-fA-F0-9:.]+\\])(?::[0-9]{1,5})?";

    private static URI shortTarget(String token) {
        for (CompiledTarget rule : SHORT_TARGETS) {
            Matcher match = rule.pattern().matcher(token);
            if (match.matches()) return ChatUrls.httpTarget(match.replaceFirst(rule.target()));
        }
        try {
            String id = token;
            String event = "";
            StringBuilder via = new StringBuilder();
            if (token.regionMatches(true, 0, "matrix:", 0, 7)) {
                String[] uri = token.substring(7).split("\\?", -1);
                if (uri.length > 2) return null;
                String[] parts = uri[0].split("/", -1);
                if (parts.length != 2 && parts.length != 4) return null;
                id = switch (parts[0]) {
                    case "u", "user" -> "@" + decode(parts[1], false);
                    case "r", "room" -> "#" + decode(parts[1], false);
                    case "roomid" -> "!" + decode(parts[1], false);
                    default -> "";
                };
                if (parts.length == 4) {
                    if ((!parts[2].equals("e") && !parts[2].equals("event")) || id.startsWith("@")) return null;
                    event = "$" + decode(parts[3], false);
                    if (event.length() == 1 || event.length() > 255 || event.codePoints().anyMatch(Character::isWhitespace)) return null;
                }
                if (uri.length == 2) {
                    boolean hasAction = false;
                    for (String entry : uri[1].split("&")) {
                        String[] pair = entry.split("=", 2);
                        if (pair.length != 2) return null;
                        if (pair[0].equals("via") && decode(pair[1], true).matches(SERVER)) {
                            via.append(via.isEmpty() ? "?via=" : "&via=").append(decode(pair[1], true));
                        } else if (!pair[0].equals("action") || hasAction) {
                            return null;
                        } else {
                            hasAction = true;
                        }
                    }
                }
            }
            if (id.length() < 2 || id.getBytes(StandardCharsets.UTF_8).length > 255
                    || "@#!".indexOf(id.charAt(0)) < 0 || id.codePoints().anyMatch(Character::isWhitespace)) return null;
            int server = id.indexOf(':');
            if (server >= 0 ? server < 2 || !id.substring(server + 1).matches(SERVER) : !id.startsWith("!")) return null;
            return ChatUrls.httpTarget("https://matrix.to/#/" + matrixSegment(id)
                    + (event.isEmpty() ? "" : "/" + matrixSegment(event)) + via);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String matrixSegment(String id) {
        var result = new StringBuilder();
        String hex = "0123456789ABCDEF";
        for (byte raw : id.getBytes(StandardCharsets.UTF_8)) {
            int c = Byte.toUnsignedInt(raw);
            if (c <= 32 || c >= 127 || "\"#<>?`{}/\\".indexOf(c) >= 0) {
                result.append('%').append(hex.charAt(c >>> 4)).append(hex.charAt(c & 15));
            } else {
                result.append((char) c);
            }
        }
        return result.toString();
    }

    private static String decode(String text, boolean query) {
        try {
            String value = URLDecoder.decode(query ? text : text.replace("+", "%2B"), StandardCharsets.UTF_8);
            return value.codePoints().anyMatch(Character::isISOControl) ? "" : value;
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    public record ShortLink(int start, int end, ChatNode.Reference link) {}

    private record Rules(String absoluteLinks, Set<String> trackingParameters, String shortLinks, List<SiteRule> sites, List<TargetRule> shortTargets) {}
    private record SiteRule(String resource, String host, String name, List<PathSpec> paths, boolean titleQuery) {}
    private record TargetRule(String pattern, String target) {}
    private record CompiledTarget(Pattern pattern, String target) {}

    private record PathSpec(String pattern, String label, String target) {}
    private record PathRule(Pattern pattern, String label, String target) {}

    private record Site(Resource resource, Pattern host, String name, List<PathRule> paths,
                        boolean titleQuery) {
        URI target(URI original) {
            for (PathRule path : paths) {
                Matcher match = path.pattern().matcher(original.getRawPath());
                if (!match.matches() || path.target() == null) continue;
                String url = match.replaceFirst(path.target());
                if (original.getRawQuery() != null) url += "?" + original.getRawQuery();
                if (original.getRawFragment() != null) url += "#" + original.getRawFragment();
                URI normalized = ChatUrls.httpTarget(url);
                return normalized == null ? original : normalized;
            }
            return original;
        }

        String label(URI target) {
            String detail = "";
            if (resource == Resource.MATRIX && target.getRawFragment() != null) {
                String fragment = target.getRawFragment();
                if (fragment.startsWith("/")) detail = decode(fragment.substring(1).split("\\?", 2)[0], false);
            }
            for (PathRule path : paths) {
                Matcher match = path.pattern().matcher(target.getRawPath());
                if (!match.matches()) continue;
                detail = decode(match.replaceFirst(path.label()), false);
                break;
            }
            if (titleQuery) {
                if (detail.isBlank() || detail.equalsIgnoreCase("index.php")) {
                    detail = "";
                    if (target.getRawQuery() != null) {
                        for (String param : target.getRawQuery().split("&")) {
                            int equals = param.indexOf('=');
                            if (equals >= 0 && param.substring(0, equals).equalsIgnoreCase("title")) {
                                detail = decode(param.substring(equals + 1), true);
                                break;
                            }
                        }
                    }
                }
                detail = detail.replace('_', ' ');
            }
            return detail.isBlank() ? name : name + ": " + detail;
        }
    }
}
