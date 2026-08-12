package org.encinet.mik.module.identity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExternalIdentityKeyTest {

    @Test
    void platformIsCanonicalButIssuerScopeAndSubjectRemainOpaque() {
        ExternalIdentityKey key = new ExternalIdentityKey(
                " QQ ", " App-ABC ", " Group-ABC ", " Member-ABC ");

        assertEquals("qq", key.platform());
        assertEquals(" App-ABC ", key.issuer());
        assertEquals(" Group-ABC ", key.scope());
        assertEquals(" Member-ABC ", key.subject());
    }

    @Test
    void malformedKeysAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExternalIdentityKey("QQ Group", "app", "group", "member"));
        assertThrows(IllegalArgumentException.class,
                () -> new ExternalIdentityKey("qq", "", "group", "member"));
        assertThrows(IllegalArgumentException.class,
                () -> new ExternalIdentityKey("qq", "app", "   ", "member"));
        assertThrows(IllegalArgumentException.class,
                () -> new ExternalIdentityKey("qq", "app", "group", ""));
        assertThrows(IllegalArgumentException.class,
                () -> new ExternalIdentityKey("qq", "app", "group", "bad\nmember"));
    }
}
