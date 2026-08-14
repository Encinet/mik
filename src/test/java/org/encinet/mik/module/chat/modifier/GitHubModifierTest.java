package org.encinet.mik.module.chat.modifier;

import org.encinet.mik.module.chat.model.ChatNode;
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
    void labelsMoreRepositoryRoutesWithoutTreatingFormsAsNumberedItems() {
        assertReplacement(
                modifier.find("github.com/o/r/discussions/42", 0, null),
                "[GitHub: o/r Discussion #42]",
                "https://github.com/o/r/discussions/42");
        assertReplacement(
                modifier.find("github.com/o/r/actions/runs/987", 0, null),
                "[GitHub: o/r Actions #987]",
                "https://github.com/o/r/actions/runs/987");
        assertReplacement(
                modifier.find("github.com/o/r/releases/download/v1/a.jar", 0, null),
                "[GitHub: o/r Release v1 / a.jar]",
                "https://github.com/o/r/releases/download/v1/a.jar");
        assertReplacement(
                modifier.find("github.com/o/r/security/advisories/GHSA-abcd", 0, null),
                "[GitHub: o/r / GHSA-abcd]",
                "https://github.com/o/r/security/advisories/GHSA-abcd");
        assertReplacement(
                modifier.find("github.com/o/r/issues/new", 0, null),
                "[GitHub: o/r]",
                "https://github.com/o/r/issues/new");
    }

    @Test
    void supportsGistsWithAndWithoutAnOwner() {
        assertReplacement(
                modifier.find("https://gist.github.com/octocat/6cad326836d38bd3a7ae", 0, null),
                "[GitHub Gist: octocat/6cad3268]",
                "https://gist.github.com/octocat/6cad326836d38bd3a7ae");
        assertReplacement(
                modifier.find("gist.github.com/2decf6c462d9b4418f2.git", 0, null),
                "[GitHub Gist: 2decf6c4]",
                "https://gist.github.com/2decf6c462d9b4418f2.git");
    }

    @Test
    void supportsPagesAndAllowsBareDomainsNextToCjkText() {
        String message = "查看octocat.github.io/Spoon-Knife/guide/";

        ChatReplacement replacement = modifier.find(message, 0, null);

        assertEquals(2, replacement.start());
        assertReplacement(replacement,
                "[GitHub Pages: octocat/Spoon-Knife]",
                "https://octocat.github.io/Spoon-Knife/guide/");
    }

    @Test
    void supportsEditorRawGistRawAndApiLinks() {
        assertReplacement(
                modifier.find("github.dev/openai/codex/blob/main/README.md", 0, null),
                "[GitHub.dev: openai/codex / README.md]",
                "https://github.dev/openai/codex/blob/main/README.md");
        assertReplacement(
                modifier.find("raw.githubusercontent.com/openai/codex/main/README.md", 0, null),
                "[GitHub Raw: openai/codex / README.md]",
                "https://raw.githubusercontent.com/openai/codex/main/README.md");
        assertReplacement(
                modifier.find("gist.githubusercontent.com/octocat/abcde/raw/a.rb", 0, null),
                "[GitHub Gist Raw: octocat/abcde / a.rb]",
                "https://gist.githubusercontent.com/octocat/abcde/raw/a.rb");
        assertReplacement(
                modifier.find("api.github.com/repos/openai/codex/issues/123", 0, null),
                "[GitHub API: openai/codex #123]",
                "https://api.github.com/repos/openai/codex/issues/123");
    }

    @Test
    void labelsOrganizationProjectsAndDoesNotInventARepository() {
        assertReplacement(
                modifier.find("github.com/orgs/openai/projects/7", 0, null),
                "[GitHub: openai Project #7]",
                "https://github.com/orgs/openai/projects/7");
        assertReplacement(
                modifier.find("github.com/settings/profile", 0, null),
                "[GitHub: settings]",
                "https://github.com/settings/profile");
    }

    @Test
    void rejectsLookalikeDomainsEmbeddedEmailDomainsAndUnknownSubdomains() {
        assertNull(modifier.find("https://github.com.example/repository", 0, null));
        assertNull(modifier.find("user@github.com/openai/codex", 0, null));
        assertNull(modifier.find("https://octocat.github.io.example/project", 0, null));
        assertNull(modifier.find("https://avatars.githubusercontent.com/u/1", 0, null));
    }

    private void assertReplacement(ChatReplacement replacement, String label, String url) {
        ChatNode.Link link = (ChatNode.Link) replacement.nodes().getFirst();
        assertEquals(label, link.label());
        assertEquals(url, link.target().toString());
    }
}
