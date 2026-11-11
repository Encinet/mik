package org.encinet.mik.feature.community.social.platform.matrix;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import org.encinet.mik.feature.community.identity.ExternalIdentityKey;
import org.encinet.mik.feature.community.identity.IdentityMemberships.State;
import org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomDirectory;
import org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomMessage;
import org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixSyncProtocol;
import org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixSyncRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class MatrixSyncStateTest {
    private static final String BOT = "@bot:example.org";
    private static final String USER = "@alice:example.org";
    private static final String SPACE = "!space:example.org";
    private static final String OTHER = "!other:example.org";
    private static final String CHAT = "!chat:example.org";
    private static final ExternalIdentityKey ALICE = new ExternalIdentityKey("matrix", "example.org", "", USER);
    private final MatrixRoomDirectory rooms = new MatrixRoomDirectory();
    private MatrixSyncState state;
    private int sequence;

    @AfterEach
    void close() {
        if (state != null) state.close();
        rooms.close();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void initialStateEstablishesCompleteMembershipWithoutPaginationTokens(boolean stateAfter) {
        start(Set.of(SPACE));
        var space = room(stateAfter, joined(USER), List.of());
        assertFalse(space.getAsJsonObject("timeline").has("prev_batch"));
        apply(null, false, Map.of(SPACE, space));
        assertEquals(State.JOINED, state.memberships().state(ALICE));
        assertEquals(State.ABSENT, state.memberships().state(identity("@other:example.org")));
        assertEquals(State.UNKNOWN, state.memberships().state(new ExternalIdentityKey(
                "matrix", "wrong-issuer.example", "", USER)));
        assertFalse(state.requiresFullState());
    }

    @Test
    void emptyCommunityStillHasAnAuthoritativeRosterContainingTheBot() {
        start(Set.of(SPACE));
        apply(null, true, Map.of(SPACE, room(true, joined(), List.of())));
        assertEquals(State.ABSENT, state.memberships().state(ALICE));
        assertFalse(state.requiresFullState());
    }

    @Test
    void spaceExitRevokesEligibilityWhileItsChatRoomStillContainsTheUser() {
        start(Set.of(SPACE));
        var spaceEvents = new ArrayList<>(joined(USER));
        var create = new JsonObject();
        create.addProperty("type", "m.room.create");
        create.addProperty("state_key", "");
        var content = new JsonObject();
        content.addProperty("type", "m.space");
        create.add("content", content);
        spaceEvents.add(create);
        var messages = apply(null, true, Map.of(
                SPACE, room(true, spaceEvents, List.of(message("$space-history"))),
                CHAT, room(true, joined(USER), List.of(message("$chat-history")))));
        assertTrue(messages.isEmpty());
        assertEquals(State.JOINED, state.memberships().state(ALICE));
        assertFalse(rooms.allowsMessages(SPACE));
        assertTrue(rooms.allowsMessages(CHAT));

        var limited = room(false, List.of(member(USER, "leave")), List.of());
        limited.getAsJsonObject("timeline").addProperty("limited", true);
        apply("initial", false, Map.of(SPACE, limited));
        assertEquals(State.ABSENT, state.memberships().state(ALICE));
        assertTrue(state.joinedMember(CHAT, USER).isPresent());
        assertEquals(List.of("$chat-live"), apply("left", false, Map.of(
                SPACE, room(true, List.of(), List.of(message("$space-live"))),
                CHAT, room(true, List.of(), List.of(message("$chat-live"))))).stream()
                .map(MatrixRoomMessage::eventId).toList());
    }

    @Test
    void joiningAnUnknownRoomRequestsFullStateWithoutInferringAbsenceOrReplayingItsHistory() {
        start(Set.of(SPACE, OTHER));
        apply(null, true, Map.of(SPACE, room(true, joined(), List.of())));
        assertEquals(State.UNKNOWN, state.memberships().state(ALICE));
        var newlyJoined = room(true, joined(USER), List.of(message("$history")));
        assertTrue(apply("before-join", false, Map.of(OTHER, newlyJoined, CHAT, newlyJoined)).isEmpty());
        assertTrue(state.requiresFullState());
        assertEquals(State.UNKNOWN, state.memberships().state(ALICE));
        assertTrue(apply("before-join", false, Map.of(OTHER, newlyJoined, CHAT, newlyJoined)).isEmpty());

        var live = apply("after-join", true, Map.of(
                SPACE, room(true, joined(), List.of()),
                OTHER, room(true, joined(USER), List.of()),
                CHAT, room(true, joined(USER), List.of(message("$confirmed-live")))));
        assertEquals(List.of("$confirmed-live"), live.stream().map(MatrixRoomMessage::eventId).toList());
        assertEquals(State.JOINED, state.memberships().state(ALICE));
        assertFalse(state.requiresFullState());
        assertEquals(List.of("$chat-live"), apply("later", false,
                Map.of(CHAT, room(true, List.of(), List.of(message("$chat-live"))))).stream()
                .map(MatrixRoomMessage::eventId).toList());
    }

    @Test
    void unknownCommunitiesCannotProveAbsenceAndKnownMembershipStillGrantsAdmission() {
        start(Set.of(SPACE, OTHER));
        apply(null, true, Map.of(SPACE, room(true, joined(USER), List.of())));
        assertEquals(State.JOINED, state.memberships().state(ALICE));
        apply("initial", false, Map.of(SPACE, room(true, List.of(member(USER, "leave")), List.of())));
        assertEquals(State.UNKNOWN, state.memberships().state(ALICE));
        apply("left", true, Map.of(
                SPACE, room(true, joined(), List.of()), OTHER, room(true, joined(), List.of())));
        assertEquals(State.ABSENT, state.memberships().state(ALICE));
    }

    @Test
    void authoritativeFullStateReplacesTheOldRosterWithoutNeedingExplicitDepartures() {
        start(Set.of(SPACE));
        apply(null, true, Map.of(SPACE, room(true, joined(USER), List.of())));
        assertEquals(State.JOINED, state.memberships().state(ALICE));
        apply("initial", true, Map.of(SPACE, room(true, joined(), List.of(member(USER, "join")))));
        assertEquals(State.ABSENT, state.memberships().state(ALICE));
    }

    @Test
    void stateBeforeTheTimelineAndStateAfterHaveDistinctOrdering() {
        start(Set.of(SPACE));
        apply(null, true, Map.of(SPACE, room(false, joined(USER), List.of(member(USER, "leave")))));
        assertEquals(State.ABSENT, state.memberships().state(ALICE));
        apply("left", true, Map.of(SPACE, room(true, joined(USER), List.of(member(USER, "leave")))));
        assertEquals(State.JOINED, state.memberships().state(ALICE));
    }

    @Test
    void oneBatchPublishesMembershipAcrossCommunitiesAtomically() throws Exception {
        start(Set.of(SPACE, OTHER));
        apply(null, true, Map.of(SPACE, room(true, joined(USER), List.of()), OTHER, room(true, joined(), List.of())));
        var states = new ArrayList<State>();
        try (var _ = state.memberships().subscribe(() -> states.add(state.memberships().state(ALICE)))) {
            apply("initial", false, Map.of(
                    SPACE, room(true, List.of(member(USER, "leave")), List.of()),
                    OTHER, room(true, List.of(member(USER, "join")), List.of())));
        }
        assertEquals(List.of(State.JOINED), states);
    }

    @Test
    void forgottenAndLeftRoomsWithdrawTheirProofAndDoNotScheduleEndlessFullSyncs() {
        start(Set.of(SPACE));
        apply(null, true, Map.of(SPACE, room(true, joined(USER), List.of())));
        apply("initial", true, Map.of());
        assertEquals(State.UNKNOWN, state.memberships().state(ALICE));
        assertFalse(state.requiresFullState());
        assertTrue(apply("forgotten", false, Map.of(SPACE, room(true, joined(USER), List.of()))).isEmpty());
        assertTrue(state.requiresFullState());
        apply("rejoined", false, Map.of(), SPACE);
        assertFalse(state.requiresFullState());
        assertEquals(State.UNKNOWN, state.memberships().state(ALICE));
    }

    @Test
    void disconnectSuspendsProofsAndResetRequiresACompleteStateRebuild() {
        start(Set.of(SPACE));
        apply(null, true, Map.of(SPACE, room(true, joined(USER), List.of())));
        state.disconnected();
        assertEquals(State.UNKNOWN, state.memberships().state(ALICE));
        apply("initial", false, Map.of());
        assertEquals(State.JOINED, state.memberships().state(ALICE));
        state.reset();
        assertEquals(State.UNKNOWN, state.memberships().state(ALICE));
        apply("old-cursor", false, Map.of(SPACE, room(true, List.of(member(USER, "leave")), List.of())));
        assertTrue(state.requiresFullState());
        assertEquals(State.UNKNOWN, state.memberships().state(ALICE));
        apply(null, false, Map.of(SPACE, room(true, joined(), List.of())));
        assertEquals(State.ABSENT, state.memberships().state(ALICE));
    }

    @Test
    void malformedFullStateCannotEraseTheLastConfirmedMembership() {
        start(Set.of(SPACE));
        apply(null, true, Map.of(SPACE, room(true, joined(USER), List.of())));
        for (var invalid : List.of(
                room(true, List.of(), List.of()),
                room(true, List.of(member(BOT, "leave")), List.of()),
                room(true, List.of(member(BOT, "join"), member(USER, "unknown")), List.of()))) {
            assertThrows(IllegalArgumentException.class, () -> apply("initial", true, Map.of(SPACE, invalid)));
            assertEquals(State.JOINED, state.memberships().state(ALICE));
        }
    }

    @Test
    void closedStateCannotPublishLateProofsOrMessages() {
        start(Set.of(SPACE));
        apply(null, true, Map.of(SPACE, room(true, joined(USER), List.of())));
        state.close();
        assertTrue(apply("initial", true, Map.of(SPACE, room(true, joined(USER), List.of()))).isEmpty());
        assertEquals(State.UNKNOWN, state.memberships().state(ALICE));
        assertFalse(state.requiresFullState());
    }

    private void start(Set<String> communities) {
        state = new MatrixSyncState(new MatrixRoomPolicy(communities, Set.of(CHAT), List.of()), rooms);
    }

    private List<MatrixRoomMessage> apply(String since, boolean fullState, Map<String, JsonObject> joined, String... left) {
        var groups = new JsonObject();
        var joinedRooms = new JsonObject();
        joined.forEach(joinedRooms::add);
        groups.add("join", joinedRooms);
        var leftRooms = new JsonObject();
        for (String roomId : left) leftRooms.add(roomId, new JsonObject());
        groups.add("leave", leftRooms);
        var response = new JsonObject();
        response.addProperty("next_batch", "next-" + ++sequence);
        response.add("rooms", groups);
        return state.apply(MatrixSyncProtocol.parse(response),
                new MatrixSyncRequest(Optional.ofNullable(since), 0, fullState), BOT);
    }

    private static JsonObject room(boolean stateAfter, List<JsonObject> state, List<JsonObject> timeline) {
        var room = new JsonObject();
        var stateSection = new JsonObject();
        var stateEvents = new JsonArray();
        state.forEach(stateEvents::add);
        stateSection.add("events", stateEvents);
        room.add(stateAfter ? "state_after" : "state", stateSection);
        var timelineSection = new JsonObject();
        var timelineEvents = new JsonArray();
        timeline.forEach(timelineEvents::add);
        timelineSection.add("events", timelineEvents);
        room.add("timeline", timelineSection);
        return room;
    }

    private static List<JsonObject> joined(String... users) {
        var events = new ArrayList<JsonObject>();
        events.add(member(BOT, "join"));
        for (String userId : users) events.add(member(userId, "join"));
        return events;
    }

    private static JsonObject member(String userId, String membership) {
        var event = new JsonObject();
        event.addProperty("type", "m.room.member");
        event.addProperty("state_key", userId);
        var content = new JsonObject();
        content.addProperty("membership", membership);
        content.addProperty("displayname", userId.equals(USER) ? "Alice" : userId);
        event.add("content", content);
        return event;
    }

    private static JsonObject message(String eventId) {
        var event = new JsonObject();
        event.addProperty("event_id", eventId);
        event.addProperty("type", "m.room.message");
        event.addProperty("sender", USER);
        var content = new JsonObject();
        content.addProperty("msgtype", "m.text");
        content.addProperty("body", "!bind 123456");
        event.add("content", content);
        return event;
    }

    private static ExternalIdentityKey identity(String userId) {
        return new ExternalIdentityKey("matrix", "example.org", "", userId);
    }
}
