package org.encinet.mik.feature.community.social.runtime;

import org.encinet.mik.feature.community.identity.ExternalIdentityKey;
import org.encinet.mik.feature.community.identity.IdentityMemberships.State;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SocialMembershipDirectoryTest {
    private static final ExternalIdentityKey ALICE = new ExternalIdentityKey("example", "app", "", "alice");

    @Test
    void onlyCompleteRostersCanProveAbsenceAndMembershipInAnyCommunityIsEnough() throws Exception {
        try (var directory = new SocialMembershipDirectory(key -> key.issuer().equals("app"), Set.of("one", "two"))) {
            assertEquals(State.UNKNOWN, directory.state(ALICE));
            var changes = new AtomicInteger();
            var subscription = directory.subscribe(changes::incrementAndGet);
            try {
                directory.replace(directory.revision(), Map.of("one", Set.of(ALICE), "two", Set.of(ALICE)));
                directory.member("one", ALICE, false);
                assertEquals(State.JOINED, directory.state(ALICE));
                directory.member("two", ALICE, false);
                assertEquals(State.ABSENT, directory.state(ALICE));
                assertEquals(State.UNKNOWN, directory.state(new ExternalIdentityKey("example", "other-app", "", "alice")));
                assertEquals(3, changes.get());
            } finally { subscription.close(); }
            directory.member("one", ALICE, true);
            assertEquals(3, changes.get());
        }
    }

    @Test
    void fetchOverlappingAnEventCannotOverwriteTheNewerMembershipState() {
        try (var directory = new SocialMembershipDirectory(_ -> true, Set.of("one"))) {
            directory.replace(directory.revision(), Map.of("one", Set.of(ALICE)));
            long beforeFetch = directory.revision();
            directory.member("one", ALICE, false);
            assertFalse(directory.replace(beforeFetch, Map.of("one", Set.of(ALICE))));
            assertEquals(State.ABSENT, directory.state(ALICE));
            directory.invalidate();
            assertEquals(State.UNKNOWN, directory.state(ALICE));
            directory.member("one", ALICE, false);
            assertEquals(State.UNKNOWN, directory.state(ALICE));
            directory.replace(directory.revision(), Map.of());
            assertThrows(IllegalArgumentException.class, () -> directory.replace(directory.revision(), Map.of("unconfigured", Set.of())));
            assertEquals(State.UNKNOWN, directory.state(ALICE));
        }
    }

    @Test
    void unknownCommunitiesCannotProveAbsenceButKnownMembershipStillCounts() {
        try (var directory = new SocialMembershipDirectory(_ -> true, Set.of("one", "two"))) {
            directory.replace(directory.revision(), Map.of("one", Set.of(ALICE)));
            assertEquals(State.JOINED, directory.state(ALICE));
            directory.member("one", ALICE, false);
            assertEquals(State.UNKNOWN, directory.state(ALICE));
            directory.replace(directory.revision(), Map.of("one", Set.of(), "two", Set.of()));
            assertEquals(State.ABSENT, directory.state(ALICE));
        }
    }
}
