package org.encinet.mik.module.commands;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandHierarchyArchitectureTest {

    private static final Path MAIN = Path.of("src/main/java/org/encinet/mik/module");

    @Test
    void homeTeleportUsesAnExplicitOperationNode() throws IOException {
        String source = Files.readString(MAIN.resolve("player/HomeModule.java"));
        String homeCommand = between(source, "// /home,", "// /delhome");

        assertTrue(homeCommand.contains(".then(Commands.literal(\"tp\")"));
        assertTrue(homeCommand.contains("sendUsage(player, \"/home tp <name>\""));
        assertFalse(homeCommand.contains("\n                            .then(Commands.argument(\"name\""));
    }

    @Test
    void pvpPlayerManagementLivesUnderAdmin() throws IOException {
        String source = Files.readString(MAIN.resolve("pvp/PvpModule.java"));

        assertTrue(source.contains(".then(Commands.literal(\"admin\")"));
        assertTrue(source.contains("\n                        .then(Commands.argument(\"player\""));
        assertFalse(source.contains("\n                .then(Commands.argument(\"player\""));
    }

    private String between(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue(start >= 0 && end > start, "command block markers must exist in order");
        return source.substring(start, end);
    }
}
