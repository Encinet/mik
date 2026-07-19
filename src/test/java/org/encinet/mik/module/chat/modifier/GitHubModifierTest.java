package org.encinet.mik.module.chat.modifier;

import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GitHubModifierTest {

    private final GitHubModifier modifier = new GitHubModifier();

    @Test
    void parsesBareRepositoryLinkWithoutTrailingPunctuation() {
        String message = "see github.com/openai/codex.";

        ChatReplacement replacement = modifier.find(message, 0, null);

        assertEquals(4, replacement.start());
        assertEquals(message.length() - 1, replacement.end());
        assertReplacement(replacement, "[GitHub: openai/codex]", "https://github.com/openai/codex");
    }

    @Test
    void labelsPullRequestsAndCommits() {
        ChatReplacement pullRequest = modifier.find("https://github.com/openai/codex/pull/123", 0, null);
        ChatReplacement commit = modifier.find(
                "https://github.com/openai/codex/commit/0123456789abcdef", 0, null);

        assertReplacement(pullRequest, "[GitHub: openai/codex PR #123]",
                "https://github.com/openai/codex/pull/123");
        assertReplacement(commit, "[GitHub: openai/codex @0123456]",
                "https://github.com/openai/codex/commit/0123456789abcdef");
    }

    @Test
    void rejectsLookalikeDomainsAndEmbeddedEmailDomains() {
        assertNull(modifier.find("https://github.com.example/repository", 0, null));
        assertNull(modifier.find("user@github.com/openai/codex", 0, null));
    }

    private void assertReplacement(ChatReplacement replacement, String label, String url) {
        assertEquals(label, ((TextComponent) replacement.component()).content());
        assertEquals(ClickEvent.openUrl(url), replacement.component().clickEvent());
    }
}
