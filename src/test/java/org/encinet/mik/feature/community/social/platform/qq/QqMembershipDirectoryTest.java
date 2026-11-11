package org.encinet.mik.feature.community.social.platform.qq;

import com.google.gson.JsonParser;
import org.bukkit.configuration.file.YamlConfiguration;
import org.encinet.mik.feature.community.identity.IdentityMemberships.State;
import org.encinet.mik.feature.community.social.api.SocialConversation;
import org.encinet.mik.feature.community.social.api.SocialEventSink.Acceptance;
import org.encinet.mik.feature.community.social.api.SocialSessionStatus;
import org.encinet.mik.feature.community.social.platform.qq.gateway.QqGatewayGroupMessage;
import org.encinet.mik.feature.community.social.platform.qq.gateway.QqGroupMemberEvent;
import org.encinet.mik.feature.community.social.platform.qq.client.QqOpenApiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class QqMembershipDirectoryTest {
    @TempDir Path temporary;

    @Test
    void onlyAuthenticatedMessageAuthorsAndMemberEventsProveMembershipAndAllGroupsMustConfirmAbsence() throws Exception {
        var key = QqAccountIdentity.from("app", "alice", "").orElseThrow().key();
        try (var directory = directory("app")) {
            directory.gateway(SocialSessionStatus.ready("gateway"));
            assertEquals(State.UNKNOWN, directory.state(key));
            assertEquals(Acceptance.ACCEPTED, directory.accept(message(false)));
            assertEquals(State.JOINED, directory.state(key));
            assertTrue(directory.contains(new SocialConversation("one", SocialConversation.Type.GROUP), key));
            assertEquals(State.UNKNOWN, directory.state(QqAccountIdentity.from("app", "mentioned", "").orElseThrow().key()));
            assertEquals(State.UNKNOWN, directory.state(QqAccountIdentity.from("other-app", "alice", "").orElseThrow().key()));

            directory.accept(new QqGroupMemberEvent("one", "alice", false));
            assertEquals(State.UNKNOWN, directory.state(key));
            assertFalse(directory.contains(new SocialConversation("one", SocialConversation.Type.GROUP), key));
            assertEquals(Acceptance.IGNORED, directory.accept(new QqGroupMemberEvent("outside", "alice", false)));
            assertEquals(Acceptance.IGNORED, directory.accept(message(true)));
            assertEquals(State.UNKNOWN, directory.state(key));
            directory.accept(new QqGroupMemberEvent("two", "alice", false));
            assertEquals(State.ABSENT, directory.state(key));
            directory.accept(message(false));
            assertEquals(State.JOINED, directory.state(key));
        }
    }

    @Test
    void leavingOneOfTwoKnownGroupsPreservesMembershipAndDuplicateEventsDoNotRepublish() throws Exception {
        var key = QqAccountIdentity.from("app", "alice", "").orElseThrow().key();
        var notifications = new AtomicInteger();
        try (var directory = directory("app")) {
            directory.gateway(SocialSessionStatus.ready("gateway"));
            var subscription = directory.subscribe(notifications::incrementAndGet);
            directory.accept(new QqGroupMemberEvent("one", "alice", true));
            directory.accept(new QqGroupMemberEvent("two", "alice", true));
            directory.accept(new QqGroupMemberEvent("one", "alice", false));
            assertEquals(State.JOINED, directory.state(key));
            directory.accept(new QqGroupMemberEvent("one", "alice", false));
            assertEquals(3, notifications.get());
            directory.accept(new QqGroupMemberEvent("two", "alice", false));
            assertEquals(State.ABSENT, directory.state(key));
            subscription.close();
            directory.accept(new QqGroupMemberEvent("one", "alice", true));
            assertEquals(4, notifications.get());
        }
    }

    @Test
    void observationsSurviveReloadWhileConnectionGapsAreUnknownAndAppIdsStayIsolated() throws Exception {
        var key = QqAccountIdentity.from("app", "alice", "").orElseThrow().key();
        try (var directory = directory("app")) {
            directory.accept(new QqGroupMemberEvent("one", "alice", true));
        }
        try (var directory = directory("app")) {
            assertEquals(State.UNKNOWN, directory.state(key));
            directory.gateway(SocialSessionStatus.ready("gateway"));
            assertEquals(State.JOINED, directory.state(key));
            directory.gateway(SocialSessionStatus.starting("gateway"));
            assertEquals(State.UNKNOWN, directory.state(key));
            directory.gateway(SocialSessionStatus.ready("gateway"));
            assertEquals(State.JOINED, directory.state(key));
            directory.accept(new QqGroupMemberEvent("one", "alice", false));
            directory.accept(new QqGroupMemberEvent("two", "alice", false));
        }
        try (var directory = directory("app")) {
            directory.gateway(SocialSessionStatus.ready("gateway"));
            assertEquals(State.ABSENT, directory.state(key));
        }
        try (var directory = directory("new-app")) {
            directory.gateway(SocialSessionStatus.ready("gateway"));
            assertEquals(State.UNKNOWN, directory.state(QqAccountIdentity.from("new-app", "alice", "").orElseThrow().key()));
        }
    }

    @Test
    void invalidStoredEvidenceFailsClosedAndCannotBeOverwrittenByAnEmptyDirectory() throws Exception {
        for (String invalid : List.of("not json", "{}", """
                {"version":1,"app_id":"app","groups":{"one":{"alice":"true"}}}
                """, """
                {"version":1,"app_id":"app","groups":{"one":{"":true}}}
                """)) {
            Files.writeString(temporary.resolve("memberships.json"), invalid);
            assertThrows(UncheckedIOException.class, () -> directory("app"));
            assertEquals(invalid, Files.readString(temporary.resolve("memberships.json")));
        }
    }

    @Test
    void storageFailureRequestsReplayBeforePublishingAnyMembership() throws Exception {
        var key = QqAccountIdentity.from("app", "alice", "").orElseThrow().key();
        var admitted = new AtomicInteger();
        try (var directory = directory("app")) {
            directory.gateway(SocialSessionStatus.ready("gateway"));
            var mapper = new QqInboundMapper(config("app"), org.mockito.Mockito.mock(QqOpenApiClient.class), _ -> {
                assertEquals(State.JOINED, directory.state(key));
                admitted.incrementAndGet();
                return Acceptance.ACCEPTED;
            }, directory);
            Files.createDirectory(temporary.resolve("memberships.json"));
            assertEquals(Acceptance.RETRY_LATER, mapper.accept(message(false)));
            assertEquals(State.UNKNOWN, directory.state(key));
            assertEquals(0, admitted.get());
            Files.delete(temporary.resolve("memberships.json"));
            assertEquals(Acceptance.ACCEPTED, mapper.accept(message(false)));
            assertEquals(State.JOINED, directory.state(key));
            assertEquals(1, admitted.get());
        }
    }

    private QqMembershipDirectory directory(String appId) throws Exception {
        return new QqMembershipDirectory(config(appId), temporary.resolve("memberships.json"),
                org.mockito.Mockito.mock(Logger.class));
    }

    private static QqPlatformConfig config(String appId) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString("app-id: " + appId + "\napp-secret: secret\nallowed-group-openids: [one, two]");
        return QqPlatformConfig.from(yaml);
    }

    private static QqGatewayGroupMessage message(boolean bot) {
        var payload = JsonParser.parseString("""
                {"id":"message","group_openid":"one","content":"/绑定 code",
                 "author":{"member_openid":"alice"},
                 "mentions":[{"id":"target","member_openid":"mentioned"}]}
                """).getAsJsonObject();
        payload.getAsJsonObject("author").addProperty("bot", bot);
        return QqGatewayGroupMessage.from(payload, "event").orElseThrow();
    }
}
