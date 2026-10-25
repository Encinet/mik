package org.encinet.mik.feature.community.identity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class IdentityPackageArchitectureTest {

    private static final Path IDENTITY =
            Path.of("src/main/java/org/encinet/mik/feature/community/identity");
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
            assertFalse(source.contains("org.encinet.mik.feature.community.social."), file);
        }
    }
}
