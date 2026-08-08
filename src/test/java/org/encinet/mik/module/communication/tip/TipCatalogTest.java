package org.encinet.mik.module.communication.tip;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TipCatalogTest {

    @Test
    void bundledTipsUseOnlySemanticMarkupAndKnownChatTopics() throws IOException {
        String source = Files.readString(Path.of("src/main/resources/tips.txt"));
        TipCatalog.LoadResult result = new TipCatalog().parse(source);
        TipIntentMatcher matcher = new TipIntentMatcher();

        assertTrue(result.errors().isEmpty(), () -> String.join("\n", result.errors()));
        assertTrue(result.entries().size() >= 30,
                () -> "Bundled catalog unexpectedly shrank to " + result.entries().size());
        assertFalse(source.matches("(?is).*<(aqua|yellow|green|gold|red|blue|gray)>.*"));
        assertFalse(source.contains("triggers="));
        assertFalse(source.contains("scenes="));
        Set<String> catalogTopics = result.entries().stream()
                .flatMap(tip -> tip.topics().stream())
                .collect(Collectors.toUnmodifiableSet());
        assertEquals(matcher.supportedTopics(), catalogTopics,
                "Every recognized chat topic needs at least one tip and vice versa");
    }

    @Test
    void parsesNestedSemanticInformationAndTopics() {
        TipCatalog.LoadResult result = new TipCatalog().parse("""
                <tip topics="home navigation">
                  Use <command>/home tp <value>name</value></command> to return.
                </tip>
                """);

        assertTrue(result.errors().isEmpty());
        TipEntry tip = result.entries().getFirst();
        assertEquals(java.util.Set.of("home", "navigation"), tip.topics());
        assertEquals("Use /home tp name to return.", tip.template().plainText().strip());
    }

    @Test
    void rejectsRemovedTriggerMetadata() {
        TipCatalog.LoadResult result = new TipCatalog().parse("""
                <tip topics="spawn" triggers="join chat respawn world-change">Old triggers</tip>
                <tip topics="home" scenes="join periodic chat">Older scenes</tip>
                """);

        assertTrue(result.entries().isEmpty());
        assertEquals(2, result.errors().size());
        result.errors().forEach(error ->
                assertTrue(error.contains("Unsupported <tip> attribute"), error));
    }

    @Test
    void rejectsAttributesOtherThanTopics() {
        TipCatalog.LoadResult result = new TipCatalog().parse(
                "<tip topics=\"general\" priority=\"1\">Unsupported</tip>");

        assertTrue(result.entries().isEmpty());
        assertTrue(result.errors().getFirst().contains("Unsupported <tip> attribute"));
    }

    @Test
    void ignoresTipExamplesWrittenInsideComments() {
        TipCatalog.LoadResult result = new TipCatalog().parse("""
                <!-- Example: <tip topics="example">content</tip>. -->
                <tip topics="general">Visible</tip>
                """);

        assertTrue(result.errors().isEmpty(), () -> String.join("\n", result.errors()));
        assertEquals(1, result.entries().size());
        assertEquals("Visible", result.entries().getFirst().template().plainText());
    }

    @Test
    void collapsesFormattingWhitespaceButKeepsExplicitBreaks() {
        TipCatalog.LoadResult result = new TipCatalog().parse("""
                <tip topics="general">
                  First
                  <strong>important</strong><br>second
                </tip>
                """);

        assertTrue(result.errors().isEmpty());
        assertEquals("First important\nsecond",
                result.entries().getFirst().template().plainText().strip());
    }

    @Test
    void rejectsPresentationTagsAndKeepsOtherValidRecords() {
        TipCatalog.LoadResult result = new TipCatalog().parse("""
                <tip topics="home"><aqua>/home</aqua></tip>
                <tip topics="spawn">Use <command>/spawn</command></tip>
                """);

        assertEquals(1, result.entries().size());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().getFirst().contains("Unsupported semantic tag <aqua>"));
    }
}
