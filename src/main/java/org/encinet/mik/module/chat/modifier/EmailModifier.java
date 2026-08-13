package org.encinet.mik.module.chat.modifier;

import org.encinet.mik.module.chat.model.ChatCapability;
import org.encinet.mik.module.chat.model.ChatNode;
import org.encinet.mik.module.chat.model.ChatProcessingContext;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns ordinary email addresses into portable, safe {@code mailto:} links. */
public final class EmailModifier implements ChatModifier {
    private static final String LOCAL_ATOM =
            "[a-z0-9!#$&'*+/=?^_`{|}~-]+";
    private static final String LOCAL_PART =
            LOCAL_ATOM + "(?:\\." + LOCAL_ATOM + ")*";
    private static final String DOMAIN_LABEL =
            "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?";
    private static final String TOP_LEVEL_DOMAIN =
            "(?:[a-z]{2,63}|xn--[a-z0-9-]{2,59})";
    private static final String DOMAIN =
            "(?:" + DOMAIN_LABEL + "\\.)+" + TOP_LEVEL_DOMAIN;
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "(?i)(?<![a-z0-9!#$&'*+/=?^_`{|}~.-])"
                    + "(?:mailto:)?(" + LOCAL_PART + "@" + DOMAIN + ")"
                    + "(?![a-z0-9_-]|\\.[a-z0-9])"
    );

    @Override
    public int priority() {
        return 45;
    }

    @Override
    public Set<ChatCapability> requiredCapabilities() {
        return Set.of(ChatCapability.URL);
    }

    @Override
    public ChatReplacement find(
            String text, int fromIndex, ChatProcessingContext context
    ) {
        Matcher matcher = EMAIL_PATTERN.matcher(text);
        if (!matcher.find(fromIndex)) {
            return null;
        }
        String address = matcher.group(1);
        return new ChatReplacement(matcher.start(), matcher.end(),
                new ChatNode.Link("[Email: " + address + "]",
                        mailto(address),
                        ChatSemanticStyles.EMAIL));
    }

    private URI mailto(String address) {
        try {
            return new URI("mailto", address, null);
        } catch (URISyntaxException impossible) {
            throw new IllegalArgumentException("invalid email address", impossible);
        }
    }
}
