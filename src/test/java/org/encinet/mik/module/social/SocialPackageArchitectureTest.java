package org.encinet.mik.module.social;

import org.encinet.mik.module.i18n.Language;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialPackageArchitectureTest {
    private static final Path SOCIAL = Path.of("src/main/java/org/encinet/mik/module/social");

    @Test
    void sharedKernelAndFeaturesDoNotDependOnQq() throws IOException {
        for (String directory : List.of(
                "api", "chat", "command", "document", "game", "runtime", "safety")) {
            try (Stream<Path> paths = Files.walk(SOCIAL.resolve(directory))) {
                for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(path);
                    assertFalse(source.contains("module.social.platform.qq"), path.toString());
                }
            }
        }
    }

    @Test
    void runtimeDependsOnTheIdentityLeasePortNotTheBindingManager() throws IOException {
        try (Stream<Path> paths = Files.walk(SOCIAL.resolve("runtime"))) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java"))
                    .toList()) {
                String source = Files.readString(path);
                assertFalse(source.contains("IdentityBindingManager"), path.toString());
            }
        }
        String host = Files.readString(SOCIAL.resolve("runtime/SocialPlatformHost.java"));
        assertTrue(host.contains("SocialIdentityLeaseManager identityLeases"));
    }

    @Test
    void qqIsAPlatformAdapterAndOldQqPackageIsGone() throws IOException {
        if (Files.exists(SOCIAL.resolve("qq"))) {
            try (Stream<Path> legacy = Files.walk(SOCIAL.resolve("qq"))) {
                assertTrue(legacy.noneMatch(path -> path.toString().endsWith(".java")));
            }
        }
        String adapter = Files.readString(
                SOCIAL.resolve("platform/qq/QqPlatformAdapter.java"));
        assertTrue(adapter.contains("implements SocialPlatformAdapter"));
        assertFalse(adapter.contains("IdentityBindingManager"));
        assertFalse(adapter.contains("Bukkit.getScheduler"));
        assertFalse(Files.exists(SOCIAL.resolve("platform/qq/command")));
        assertTrue(Files.exists(SOCIAL.resolve("command/SocialCommandDispatcher.java")));
        assertFalse(Files.exists(SOCIAL.resolve("command/SocialCommandManager.java")));
        assertFalse(Files.exists(SOCIAL.resolve("command/SocialCommandRegistry.java")));
        assertFalse(Files.exists(SOCIAL.resolve("command/SocialCommandRouter.java")));
        assertFalse(adapter.contains("QqCommandCatalog"));
    }

    @Test
    void compositionRootInstallsOnlyTheSixUserFacingSharedCommands() throws IOException {
        String module = Files.readString(SOCIAL.resolve("SocialModule.java"));
        for (String command : List.of(
                "OnlinePlayersCommand", "ServerStatusCommand", "SocialHelpCommand",
                "LinkIdentityCommand", "PlayerProfileCommand", "UnlinkIdentityCommand")) {
            assertTrue(module.contains("new " + command + '('), command);
        }
        for (String removed : List.of(
                "TpsCommand", "ServerVersionCommand", "BindingStatusCommand",
                "BindingHelpCommand")) {
            assertFalse(module.contains(removed), removed);
            assertFalse(Files.exists(SOCIAL.resolve("command/builtin/" + removed + ".java")),
                    removed);
        }
    }

    @Test
    void everyUserFacingCommandDeclaresAliasesForEverySupportedLanguage()
            throws IOException {
        Path builtins = SOCIAL.resolve("command/builtin");
        for (String command : List.of(
                "OnlinePlayersCommand", "ServerStatusCommand", "SocialHelpCommand",
                "LinkIdentityCommand", "PlayerProfileCommand", "UnlinkIdentityCommand")) {
            String source = Files.readString(builtins.resolve(command + ".java"));
            for (Language language : Language.values()) {
                assertTrue(source.contains("Language." + language.name()),
                        command + " is missing aliases for " + language.id());
            }
            assertTrue(source.contains("Optional<Message> description()"),
                    command + " is missing help metadata");
        }
    }

    @Test
    void eachBuiltinCommandOwnsItsResponseAndFeaturePresentersAreGone()
            throws IOException {
        Path builtins = SOCIAL.resolve("command/builtin");
        for (String command : List.of(
                "OnlinePlayersCommand", "ServerStatusCommand", "SocialHelpCommand",
                "LinkIdentityCommand", "PlayerProfileCommand", "UnlinkIdentityCommand",
                "UnknownSocialCommand")) {
            String source = Files.readString(builtins.resolve(command + ".java"));
            assertTrue(source.contains("SocialDocument present("),
                    command + " does not own its response");
            assertFalse(source.contains("CommandPresenter"),
                    command + " delegates its response to a feature presenter");
            assertFalse(source.contains("SocialIdentityService"),
                    command + " delegates its use case to an identity command service");
            assertFalse(source.contains("SocialProfileService"),
                    command + " delegates its use case to a profile command service");
            assertFalse(source.contains("SocialCommandHelpCatalog"),
                    command + " delegates help generation to a command-only helper");
        }
        for (String removed : List.of(
                "ServerCommandPresenter", "IdentityCommandPresenter",
                "ProfileCommandPresenter")) {
            assertFalse(Files.exists(SOCIAL.resolve("presenter/" + removed + ".java")),
                    removed + " should not split responses away from commands");
        }
        assertFalse(Files.exists(SOCIAL.resolve("service")),
                "generic service packages hide command-specific use cases");
        assertFalse(Files.exists(SOCIAL.resolve("result")),
                "command result types must be nested in their command");
        assertFalse(Files.exists(SOCIAL.resolve("command/SocialCommandHelpCatalog.java")));
        assertFalse(Files.exists(SOCIAL.resolve("presenter")),
                "social command presentation must not have a separate package");
    }

    @Test
    void gameBoundaryExposesDataWithoutDependingOnCommandsOrReplies()
            throws IOException {
        try (Stream<Path> paths = Files.walk(SOCIAL.resolve("game"))) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java"))
                    .toList()) {
                String source = Files.readString(path);
                assertFalse(source.contains("module.social.command"), path.toString());
                assertFalse(source.contains("module.social.document"), path.toString());
                assertFalse(source.contains("module.social.platform"), path.toString());
                assertFalse(source.contains("module.i18n.Message"), path.toString());
            }
        }
    }
}
