package org.encinet.mik.module.social.platform.qq.gateway;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.encinet.mik.module.social.api.SocialInboundMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqGatewayProtocolTest {

    @Test
    void helloAndSequenceAreParsed() {
        QqGatewayProtocol.Frame frame = QqGatewayProtocol.parse("""
                {"op":10,"s":23,"d":{"heartbeat_interval":41250}}
                """).orElseThrow();

        assertEquals(QqGatewayProtocol.OP_HELLO, frame.opcode());
        assertEquals(23L, frame.sequence());
        assertEquals(41_250L,
                QqGatewayProtocol.heartbeatInterval(frame).orElseThrow());
        assertTrue(QqGatewayProtocol.parse("not-json").isEmpty());
        assertTrue(QqGatewayProtocol.parse("{\"op\":\"10\",\"d\":{}}").isEmpty());
        assertTrue(QqGatewayProtocol.parse("{\"op\":-1,\"d\":{}}").isEmpty());
        assertTrue(QqGatewayProtocol.parse("{\"op\":0,\"s\":1.5,\"d\":{}}").isEmpty());
        assertTrue(QqGatewayProtocol.parse("{\"op\":0,\"s\":-1,\"d\":{}}").isEmpty());
    }

    @Test
    void identifyAndResumeUseGatewayAuthenticationShape() {
        JsonObject identify = JsonParser.parseString(
                QqGatewayProtocol.identify("access-token")).getAsJsonObject();
        JsonObject identifyData = identify.getAsJsonObject("d");

        assertEquals(QqGatewayProtocol.OP_IDENTIFY, identify.get("op").getAsInt());
        assertEquals("QQBot access-token", identifyData.get("token").getAsString());
        assertEquals(1 << 25, identifyData.get("intents").getAsInt());
        assertEquals(0, identifyData.getAsJsonArray("shard").get(0).getAsInt());
        assertEquals(1, identifyData.getAsJsonArray("shard").get(1).getAsInt());
        assertEquals("mik", identifyData.getAsJsonObject("properties")
                .get("$browser").getAsString());

        JsonObject resume = JsonParser.parseString(
                QqGatewayProtocol.resume("access-token", "session", 77L))
                .getAsJsonObject();
        assertEquals(QqGatewayProtocol.OP_RESUME, resume.get("op").getAsInt());
        assertEquals("QQBot access-token",
                resume.getAsJsonObject("d").get("token").getAsString());
        assertEquals("session", resume.getAsJsonObject("d")
                .get("session_id").getAsString());
        assertEquals(77L, resume.getAsJsonObject("d").get("seq").getAsLong());
    }

    @Test
    void heartbeatCarriesTheLastSequenceOrNull() {
        JsonObject initial = JsonParser.parseString(QqGatewayProtocol.heartbeat(null))
                .getAsJsonObject();
        JsonObject resumed = JsonParser.parseString(QqGatewayProtocol.heartbeat(81L))
                .getAsJsonObject();

        assertEquals(QqGatewayProtocol.OP_HEARTBEAT, initial.get("op").getAsInt());
        assertTrue(initial.get("d").isJsonNull());
        assertEquals(81L, resumed.get("d").getAsLong());
    }

    @Test
    void authenticatedGroupDispatchMapsToSharedInboundMessage() {
        QqGatewayProtocol.Frame frame = QqGatewayProtocol.parse("""
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
                """).orElseThrow();
        QqGatewayGroupMessage group = QqGatewayProtocol.groupMessage(frame).orElseThrow();
        SocialInboundMessage inbound = group.toInboundMessage(
                "qq", "app-id", document -> java.util.concurrent.CompletableFuture.completedFuture(null));

        assertEquals("event-id", inbound.eventId());
        assertEquals("group-id", inbound.conversation().id());
        assertEquals("/状态 now", ((SocialInboundMessage.Text) inbound.content()).body());
        assertFalse(inbound.authorIsBot());
        assertEquals("User Name", inbound.authenticatedIdentity().orElseThrow().displayName());
        assertEquals("qq", inbound.authenticatedIdentity().orElseThrow().key().platform());
        assertEquals("app-id", inbound.authenticatedIdentity().orElseThrow().key().issuer());
        assertEquals("group-id", inbound.authenticatedIdentity().orElseThrow().key().scope());
        assertEquals("member-id", inbound.authenticatedIdentity().orElseThrow().key().subject());
    }

    @Test
    void fullGroupMessageWithoutMentionUsesTheSameInboundMapping() {
        QqGatewayProtocol.Frame frame = QqGatewayProtocol.parse("""
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
                """).orElseThrow();

        QqGatewayGroupMessage group = QqGatewayProtocol.groupMessage(frame).orElseThrow();
        SocialInboundMessage inbound = group.toInboundMessage(
                "qq", "app-id", document ->
                        java.util.concurrent.CompletableFuture.completedFuture(null));

        assertEquals("full-event-id", inbound.eventId());
        assertEquals("full-message-id", group.messageId());
        assertEquals("/在线", ((SocialInboundMessage.Text) inbound.content()).body());
        assertEquals("member-id",
                inbound.authenticatedIdentity().orElseThrow().key().subject());
    }

    @Test
    void authenticatedMentionsAndReplyAuthorsBecomeTrustedSharedReferences() {
        QqGatewayProtocol.Frame frame = QqGatewayProtocol.parse("""
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
                """).orElseThrow();

        SocialInboundMessage inbound = QqGatewayProtocol.groupMessage(frame).orElseThrow()
                .toInboundMessage("qq", "app-id", ignored ->
                        java.util.concurrent.CompletableFuture.completedFuture(null));

        assertEquals("/我的 @Target",
                ((SocialInboundMessage.Text) inbound.content()).body());
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
        QqGatewayProtocol.Frame frame = QqGatewayProtocol.parse("""
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
                """).orElseThrow();

        SocialInboundMessage inbound = QqGatewayProtocol.groupMessage(frame).orElseThrow()
                .toInboundMessage("qq", "app-id", ignored ->
                        java.util.concurrent.CompletableFuture.completedFuture(null));

        assertEquals("/我的 <@target-token>",
                ((SocialInboundMessage.Text) inbound.content()).body());
        assertTrue(inbound.references().targetIdentities().isEmpty());
    }

    @Test
    void unrelatedDispatchIsNotTreatedAsAGroupMessage() {
        QqGatewayProtocol.Frame frame = QqGatewayProtocol.parse("""
                {"op":0,"s":93,"t":"GROUP_ADD_ROBOT","d":{}}
                """).orElseThrow();

        assertTrue(QqGatewayProtocol.groupMessage(frame).isEmpty());
    }
}
