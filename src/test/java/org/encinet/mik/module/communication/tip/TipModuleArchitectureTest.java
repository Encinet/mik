package org.encinet.mik.module.communication.tip;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TipModuleArchitectureTest {

    private static final Path MODULE = Path.of(
            "src/main/java/org/encinet/mik/module/communication/TipModule.java");

    @Test
    void recognizedChatIsTheOnlyTipDeliveryTrigger() throws IOException {
        String source = Files.readString(MODULE);

        assertTrue(source.contains("AsyncChatEvent"));
        assertTrue(source.contains("intentMatcher.match(message)"));
        assertFalse(source.contains("PlayerJoinEvent"));
        assertFalse(source.contains("PlayerRespawnEvent"));
        assertFalse(source.contains("PlayerChangedWorldEvent"));
        assertFalse(source.contains("runTaskTimer("));
        assertFalse(source.contains("Commands.literal(\"tip\")"));
        assertFalse(source.contains("public void trigger("));
    }

    @Test
    void removedFormatsAreDeletedWithoutConversionOrBackup() throws IOException {
        String source = Files.readString(MODULE);

        assertTrue(source.contains("Files.delete(tipsFile.toPath())"));
        assertFalse(source.toLowerCase(java.util.Locale.ROOT).contains("migrat"));
        assertFalse(source.contains("Files.copy("));
    }
}
