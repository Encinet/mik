package org.encinet.mik.module.skript;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkriptDocumentationTest {

    private static final Path DOCUMENT = Path.of("docs", "skript.md");

    @Test
    void documentsEveryPublicMikSyntaxFamily() throws IOException {
        String document = Files.readString(DOCUMENT);
        List<String> requiredSyntax = List.of(
                "mik language of %players%",
                "mik client version of %players%",
                "mik role of %players%",
                "mik afk state of %players%",
                "mik afk message of %players%",
                "mik afk source of %players%",
                "mik afk duration of %players%",
                "mik pvp state of %players%",
                "mik pvp preference of %players%",
                "mik pvp overridden state of %players%",
                "mik pvp override value of %players%",
                "mik pvp override id of %players%",
                "mik pvp override owner of %players%",
                "mik pvp override priority of %players%",
                "mik pvp override remaining time of %players%",
                "mik pvp override ids of %players%",
                "mik combat tagged state of %players%",
                "mik combat tag remaining time of %players%",
                "%players% is/are mik afk",
                "%players% has/have mik pvp enabled",
                "%players% is/are mik pvp overridden",
                "%players% is/are mik combat tagged",
                "%players% has/have [the] mik pvp override %string%",
                "set %players% to mik afk",
                "clear mik afk state of %players%",
                "set mik pvp preference of %players% to %boolean%",
                "set mik pvp override %string% of %players% to %boolean%",
                "force mik pvp on for %players%",
                "clear mik pvp override %string% from %players%",
                "clear all mik pvp overrides from %players%"
        );

        assertAll(requiredSyntax.stream()
                .map(syntax -> () -> assertTrue(document.contains(syntax),
                        () -> "Missing documented syntax: " + syntax)));
    }

    @Test
    void documentationStaysFocusedOnTheMikApi() throws IOException {
        String document = Files.readString(DOCUMENT);

        assertAll(
                () -> assertEquals(
                        "https://github.com/Encinet/mik/blob/main/docs/skript.md",
                        SkriptInfoCommand.MIK_DOCS),
                () -> assertFalse(document.contains("`/script`")),
                () -> assertFalse(document.contains("AI 提示词")),
                () -> assertFalse(document.contains("SkBee")),
                () -> assertFalse(document.contains("skript-particle"))
        );
    }
}
