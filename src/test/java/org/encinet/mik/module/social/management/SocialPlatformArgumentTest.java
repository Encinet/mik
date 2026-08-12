package org.encinet.mik.module.social.management;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SocialPlatformArgumentTest {

    @Test
    void platformCompletionIsFilteredCaseInsensitiveAndStable() {
        List<String> installed = List.of("matrix", "qq", "discord", "qq");

        assertEquals(List.of("discord", "matrix", "qq"),
                SocialManagementCommandRegistrar.matchingPlatformIds(installed, ""));
        assertEquals(List.of("qq"),
                SocialManagementCommandRegistrar.matchingPlatformIds(installed, "q"));
        assertEquals(List.of("qq"),
                SocialManagementCommandRegistrar.matchingPlatformIds(installed, "Q"));
        assertEquals(List.of(),
                SocialManagementCommandRegistrar.matchingPlatformIds(installed, "telegram"));
    }
}
