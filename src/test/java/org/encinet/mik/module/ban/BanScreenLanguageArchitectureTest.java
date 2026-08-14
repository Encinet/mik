package org.encinet.mik.module.ban;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BanScreenLanguageArchitectureTest {

    @Test
    void banScreensUseAddressLanguageAtEveryEntryPoint() throws IOException {
        String listener = source("BanLoginListener.java");
        String commands = source("BanCommandController.java");
        String dialogs = source("BanDialogController.java");

        assertTrue(listener.contains("languageService.languageForAddress(address)"));
        assertTrue(commands.contains("languageService.languageForAddress(address)"));
        assertTrue(dialogs.contains("languageService.languageForAddress(address)"));
    }

    private String source(String fileName) throws IOException {
        return Files.readString(Path.of(
                "src/main/java/org/encinet/mik/module/ban", fileName));
    }
}
