package org.encinet.mik.module.identity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityPackageArchitectureTest {

    private static final Path IDENTITY =
            Path.of("src/main/java/org/encinet/mik/module/identity");
    private static final List<String> PUBLIC_API = List.of(
            "ExternalIdentity.java",
            "ExternalIdentityKey.java",
            "ExternalIdentityLinker.java",
            "IdentityBinding.java",
            "IdentityBindingException.java",
            "IdentityBindingManager.java",
            "IdentityLinkCode.java",
            "IdentityLinkResult.java",
            "IdentityPlatform.java",
            "IdentityPlatformRegistration.java");

    @Test
    void publicIdentityApiRemainsIndependentOfBukkitAndPlatformAdapters()
            throws IOException {
        for (String file : PUBLIC_API) {
            String source = Files.readString(IDENTITY.resolve(file));
            assertFalse(source.contains("import org.bukkit"), file);
            assertFalse(source.contains("org.encinet.mik.module.social.qq"), file);
        }
    }

    @Test
    void persistenceAndApplicationServicesStayInternal() throws IOException {
        for (String file : List.of(
                "IdentityBindingRepository.java",
                "IdentityBindingService.java",
                "IdentityPlatformRegistry.java")) {
            String source = Files.readString(IDENTITY.resolve(file));
            assertFalse(source.contains("public final class"), file);
            assertFalse(source.contains("public interface"), file);
        }
    }

    @Test
    void identityCompositionDoesNotRegisterPlatformSpecificCommands()
            throws IOException {
        String source = Files.readString(IDENTITY.resolve("IdentityBindingModule.java"));

        assertFalse(source.contains("Commands.literal(\"qqbind\")"));
        assertFalse(source.contains("Message.IDENTITY_QQ_COMMAND_DESCRIPTION"));
        assertFalse(source.contains("Commands.literal"));
        assertTrue(Files.exists(IDENTITY.resolve("IdentityBindingRuntime.java")));
        assertTrue(Files.exists(IDENTITY.resolve("IdentityBindingCommandRegistrar.java")));
        assertTrue(Files.exists(IDENTITY.resolve("IdentityBindingCommandPresenter.java")));
        assertTrue(Files.exists(IDENTITY.resolve("IdentityPlayerListener.java")));
    }

    @Test
    void bindingsUseNaturalKeysInsteadOfPublicSequenceNumbers() throws IOException {
        String binding = Files.readString(IDENTITY.resolve("IdentityBinding.java"));
        String commands = Files.readString(
                IDENTITY.resolve("IdentityBindingCommandRegistrar.java"));
        String repository = Files.readString(
                IDENTITY.resolve("IdentityBindingRepository.java"));

        assertFalse(binding.contains("long id"));
        assertFalse(commands.contains("LongArgumentType"));
        assertFalse(commands.contains("binding-id"));
        assertTrue(commands.contains("unlinkPlayerPlatform"));
        assertTrue(repository.contains(
                "PRIMARY KEY(platform, issuer, identity_scope, subject)"));
    }
}
