package org.encinet.mik.module.identity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityPlatformRegistryTest {

    private static final IdentityPlatform QQ = new IdentityPlatform(
            "qq", "QQ", "/绑定 {code}", true);

    @Test
    void equalAdaptersHoldIndependentRegistrationLeases() {
        IdentityPlatformRegistry registry = new IdentityPlatformRegistry();
        IdentityPlatformRegistration first = registry.register(QQ);
        IdentityPlatformRegistration second = registry.register(QQ);

        assertEquals(1, registry.size());
        assertTrue(first.isActive());
        assertTrue(second.isActive());

        first.close();
        assertFalse(first.isActive());
        assertTrue(second.isActive());
        assertEquals(QQ, registry.find("QQ").orElseThrow());

        second.close();
        second.close();
        assertTrue(registry.platforms().isEmpty());
    }

    @Test
    void conflictingDescriptorsForOnePlatformAreRejected() {
        IdentityPlatformRegistry registry = new IdentityPlatformRegistry();
        registry.register(QQ);

        assertThrows(IllegalArgumentException.class, () -> registry.register(
                new IdentityPlatform("qq", "Another QQ", "/link {code}")));
    }

    @Test
    void staleLeaseCannotRemoveANewerRegistration() {
        IdentityPlatformRegistry registry = new IdentityPlatformRegistry();
        IdentityPlatformRegistration stale = registry.register(QQ);
        registry.clear();
        IdentityPlatformRegistration current = registry.register(QQ);

        stale.close();

        assertFalse(stale.isActive());
        assertTrue(current.isActive());
        assertEquals(QQ, registry.find("qq").orElseThrow());
    }

    @Test
    void activePlatformIdsProvideCaseInsensitiveCommandSuggestions() {
        IdentityPlatformRegistry registry = new IdentityPlatformRegistry();
        IdentityPlatformRegistration qq = registry.register(QQ);
        registry.register(new IdentityPlatform("discord", "Discord", "/bind {code}"));

        assertEquals(java.util.List.of("discord", "qq"), registry.suggestions(""));
        assertEquals(java.util.List.of("qq"), registry.suggestions("q"));
        assertEquals(java.util.List.of("qq"), registry.suggestions("Q"));

        qq.close();
        assertTrue(registry.suggestions("q").isEmpty());
    }
}
