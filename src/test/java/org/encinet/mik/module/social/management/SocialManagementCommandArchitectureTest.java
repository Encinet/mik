package org.encinet.mik.module.social.management;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialManagementCommandArchitectureTest {

    private static final Path COMMANDS = Path.of(
            "src/main/java/org/encinet/mik/module/social/management");

    @Test
    void registrarOwnsOneCohesiveSocialCommandTree() throws IOException {
        String registrar = Files.readString(
                COMMANDS.resolve("SocialManagementCommandRegistrar.java"));
        assertTrue(registrar.contains("Commands.literal(\"social\")"));
        for (String command : java.util.List.of(
                "help", "platforms", "status", "reload", "conversations")) {
            assertTrue(registrar.contains("Commands.literal(\"" + command + "\")"), command);
        }
        assertTrue(registrar.contains("SocialPlatformAdmin"));
        assertTrue(registrar.contains("sendStatus(CommandSender"));
        assertFalse(registrar.contains("SocialAdminPresenter"));
        assertFalse(Files.exists(Path.of(
                "src/main/java/org/encinet/mik/module/social/presenter/SocialAdminPresenter.java")));
    }
}
