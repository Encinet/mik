package org.encinet.mik.module.communication.tip;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
        result.entries().stream()
                .filter(tip -> tip.triggers().contains(TipScene.CHAT))
                .flatMap(tip -> tip.topics().stream())
                .forEach(topic -> assertTrue(matcher.supportedTopics().contains(topic), topic));
        result.entries().forEach(tip -> {
            assertTrue(tip.supports(TipScene.PERIODIC), tip.template().plainText());
            assertTrue(tip.supports(TipScene.MANUAL), tip.template().plainText());
        });
    }

    @Test
    void parsesNestedSemanticInformationAndContextualTriggers() {
        TipCatalog.LoadResult result = new TipCatalog().parse("""
                <tip topics="home navigation" triggers="join, chat">
                  Use <command>/home <value>name</value></command> to return.
                </tip>
                """);

        assertTrue(result.errors().isEmpty());
        TipEntry tip = result.entries().getFirst();
        assertEquals(java.util.Set.of("home", "navigation"), tip.topics());
        assertEquals(java.util.Set.of(TipScene.JOIN, TipScene.CHAT), tip.triggers());
        assertTrue(tip.supports(TipScene.PERIODIC));
        assertEquals("Use /home name to return.", tip.template().plainText().strip());
    }

    @Test
    void rotationIsImplicitAndLegacyScenesRemainReadable() {
        TipCatalog.LoadResult result = new TipCatalog().parse("""
                <tip topics="general">Rotation only</tip>
                <tip topics="home" scenes="join periodic chat">Legacy metadata</tip>
                """);

        assertTrue(result.errors().isEmpty(), () -> String.join("\n", result.errors()));
        assertTrue(result.entries().get(0).triggers().isEmpty());
        assertTrue(result.entries().get(0).supports(TipScene.PERIODIC));
        assertEquals(java.util.Set.of(TipScene.JOIN, TipScene.CHAT),
                result.entries().get(1).triggers());
        assertTrue(result.entries().get(1).supports(TipScene.PERIODIC));
    }

    @Test
    void rejectsExplicitPeriodicTriggerBecauseEveryTipAlreadyRotates() {
        TipCatalog.LoadResult result = new TipCatalog().parse(
                "<tip topics=\"general\" triggers=\"periodic\">Redundant</tip>");

        assertTrue(result.entries().isEmpty());
        assertTrue(result.errors().getFirst().contains("implicit"));
    }

    @Test
    void ignoresTipExamplesWrittenInsideComments() {
        TipCatalog.LoadResult result = new TipCatalog().parse("""
                <!-- Every <tip topics="example"> record rotates. -->
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
                <tip topics="home" triggers="chat"><aqua>/home</aqua></tip>
                <tip topics="spawn" triggers="chat">Use <command>/spawn</command></tip>
                """);

        assertEquals(1, result.entries().size());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().getFirst().contains("Unsupported semantic tag <aqua>"));
    }

    @Test
    void recognizesOnlyTheRemovedSeparatorAndPresentationFormatAsLegacy() {
        TipCatalog catalog = new TipCatalog();

        assertTrue(catalog.isLegacyDocument("<aqua>Old tip</aqua>\n===\nPlain tip"));
        assertFalse(catalog.isLegacyDocument(
                "<tip topics=\"general\">Plain tip</tip>"));
        assertFalse(catalog.isLegacyDocument("<tip topics=\"general\"><aqua>broken"));
        assertFalse(catalog.isLegacyDocument("an unrelated malformed document"));
    }
}
