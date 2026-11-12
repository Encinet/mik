package org.encinet.mik.feature.community.social.platform.qq;

import com.google.gson.JsonParser;
import org.encinet.mik.feature.community.social.api.SocialInboundMessage;
import org.encinet.mik.feature.community.social.platform.qq.gateway.QqGatewayGroupMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqInboundMapperTest {
    @Test
    void sameMemberInThreeGroupsHasOneAccountKeyWhileAppIdsRemainIsolated() {
        var first = message("group-a", "same-member", "app");
        for (String group : new String[]{"group-b", "group-c"}) {
            var other = message(group, "same-member", "app");
            assertEquals(first.authenticatedIdentity(), other.authenticatedIdentity());
            assertEquals(group, other.conversation().id());
        }
        org.junit.jupiter.api.Assertions.assertNotEquals(first.authenticatedIdentity(),
                message("group-a", "same-member", "another-app").authenticatedIdentity());
    }

    static SocialInboundMessage message(String group, String member, String appId) {
        var payload = new com.google.gson.JsonObject();
        payload.addProperty("id", "message");
        payload.addProperty("group_openid", group);
        payload.addProperty("content", "hello");
        var author = new com.google.gson.JsonObject();
        author.addProperty("member_openid", member);
        author.addProperty("username", "Name");
        payload.add("author", author);
        return QqInboundMapper.map(QqGatewayGroupMessage.from(payload, "event").orElseThrow(),
                appId, org.encinet.mik.feature.community.social.SocialTestReplies::accepted);
    }

    @Test
    void authenticatedGroupDispatchMapsToSharedInboundMessage() {
        QqGatewayGroupMessage group = event("""
                {
                  "op": 0,
                  "id": "event-id",
                  "s": 91,
                  "t": "GROUP_AT_MESSAGE_CREATE",
                  "d": {
                    "id": "message-id",
                    "group_openid": "group-id",
                    "content": "  /状态\\n now  ",
                    "author": {
                      "username": "User\\nName",
                      "member_openid": "member-id",
                      "bot": false
                    }
                  }
                }
                """);
        SocialInboundMessage inbound = QqInboundMapper.map(group, "app-id", org.encinet.mik.feature.community.social.SocialTestReplies::accepted);

        assertEquals("event-id", inbound.eventId());
        assertEquals("group-id", inbound.conversation().id());
        assertEquals("/状态 now", ((SocialInboundMessage.Text) inbound.content()).body());
        assertFalse(inbound.authorIsBot());
        assertEquals("User Name", inbound.authenticatedIdentity().orElseThrow().displayName());
        assertEquals("qq", inbound.authenticatedIdentity().orElseThrow().key().platform());
        assertEquals("app-id", inbound.authenticatedIdentity().orElseThrow().key().issuer());
        assertEquals("", inbound.authenticatedIdentity().orElseThrow().key().scope());
        assertEquals("member-id", inbound.authenticatedIdentity().orElseThrow().key().subject());
    }

    @Test
    void fullGroupMessageWithoutMentionUsesTheSameInboundMapping() {
        QqGatewayGroupMessage group = event("""
                {
                  "op": 0,
                  "id": "full-event-id",
                  "s": 92,
                  "t": "GROUP_MESSAGE_CREATE",
                  "d": {
                    "id": "full-message-id",
                    "group_openid": "group-id",
                    "content": "/在线",
                    "author": {
                      "member_openid": "member-id",
                      "bot": false
                    }
                  }
                }
                """);

        SocialInboundMessage inbound = QqInboundMapper.map(group, "app-id", org.encinet.mik.feature.community.social.SocialTestReplies::accepted);

        assertEquals("full-event-id", inbound.eventId());
        assertEquals("full-message-id", group.messageId());
        assertEquals("/在线", ((SocialInboundMessage.Text) inbound.content()).body());
        assertEquals("member-id",
                inbound.authenticatedIdentity().orElseThrow().key().subject());
    }

    @Test
    void authenticatedMentionsAndReplyAuthorsBecomeTrustedSharedReferences() {
        QqGatewayGroupMessage group = event("""
                {
                  "op": 0,
                  "id": "reference-event",
                  "s": 93,
                  "t": "GROUP_MESSAGE_CREATE",
                  "d": {
                    "id": "reference-message",
                    "group_openid": "group-id",
                    "content": "<@bot-token> /我的 <@target-token>",
                    "author": {
                      "username": "Caller",
                      "member_openid": "caller-member",
                      "bot": false
                    },
                    "mentions": [
                      {
                        "id": "bot-token", "username": "MIK", "bot": true,
                        "is_you": true, "member_openid": "bot-member"
                      },
                      {
                        "id": "target-token", "username": "Target", "bot": false,
                        "is_you": false, "member_openid": "target-member"
                      }
                    ],
                    "msg_elements": [{
                      "msg_idx": "reply-id", "message_type": 103, "content": "hello",
                      "author": {
                        "username": "Target", "member_openid": "target-member",
                        "bot": false
                      }
                    }]
                  }
                }
                """);

        SocialInboundMessage inbound = QqInboundMapper.map(group, "app-id", org.encinet.mik.feature.community.social.SocialTestReplies::accepted);

        assertEquals("/我的 @Target",
                ((SocialInboundMessage.Text) inbound.content()).commandBody());
        assertTrue(((SocialInboundMessage.Text) inbound.content()).body().contains("↪ Target: hello"));
        assertEquals(1, inbound.references().mentions().size());
        assertEquals("target-member", inbound.references().mentions().getFirst()
                .key().subject());
        assertEquals("target-member", inbound.references().repliedAuthor().orElseThrow()
                .key().subject());
        assertEquals(1, inbound.references().targetIdentities().size());
        assertEquals(1, inbound.references().mentionSpans().size());
        assertEquals("@Target", ((SocialInboundMessage.Text) inbound.content()).body()
                .substring(inbound.references().mentionSpans().getFirst().start(),
                        inbound.references().mentionSpans().getFirst().end()));
    }

    @Test
    void handwrittenMentionMarkupDoesNotBecomeATrustedReference() {
        QqGatewayGroupMessage group = event("""
                {
                  "op": 0,
                  "id": "spoofed-mention-event",
                  "s": 94,
                  "t": "GROUP_MESSAGE_CREATE",
                  "d": {
                    "id": "spoofed-mention-message",
                    "group_openid": "group-id",
                    "content": "/我的 <@target-token>",
                    "author": {
                      "username": "Caller",
                      "member_openid": "caller-member",
                      "bot": false
                    },
                    "mentions": []
                  }
                }
                """);

        SocialInboundMessage inbound = QqInboundMapper.map(group, "app-id", org.encinet.mik.feature.community.social.SocialTestReplies::accepted);

        assertEquals("/我的 @target-token",
                ((SocialInboundMessage.Text) inbound.content()).body());
        assertTrue(inbound.references().targetIdentities().isEmpty());
    }

    private static QqGatewayGroupMessage event(String envelope) {
        var frame = JsonParser.parseString(envelope).getAsJsonObject();
        return QqGatewayGroupMessage.from(frame.get("d"), frame.get("id").getAsString()).orElseThrow();
    }
}
