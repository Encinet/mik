package org.encinet.mik.feature.community.social.platform.qq.gateway;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
    void membershipEventsUseTheMemberIdentifierAndRejectMissingIds() {
        var event = QqGatewayProtocol.memberEvent(QqGatewayProtocol.parse("""
                {"op":0,"s":5,"t":"GROUP_MEMBER_REMOVE","d":{
                 "group_openid":"group","member_openid":"member","user_openid":"different"}}
                """).orElseThrow()).orElseThrow();
        assertEquals("group", event.groupOpenId());
        assertEquals("member", event.memberOpenId());
        assertFalse(event.joined());
        assertTrue(QqGatewayProtocol.memberEvent(QqGatewayProtocol.parse("""
                {"op":0,"t":"GROUP_MEMBER_REMOVE","d":{"group_openid":"group","user_openid":"different"}}
                """).orElseThrow()).isEmpty());
    }

    @Test
    void identifyAndResumeUseGatewayAuthenticationShape() {
        JsonObject identify = JsonParser.parseString(
                QqGatewayProtocol.identify("access-token")).getAsJsonObject();
        JsonObject identifyData = identify.getAsJsonObject("d");

        assertEquals(QqGatewayProtocol.OP_IDENTIFY, identify.get("op").getAsInt());
        assertEquals("QQBot access-token", identifyData.get("token").getAsString());
        assertEquals((1 << 25) | (1 << 24), identifyData.get("intents").getAsInt());
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
    void bothGroupDispatchesDecodeProtocolFieldsWithoutAssigningAccountScope() {
        for (String type : new String[]{"GROUP_AT_MESSAGE_CREATE", "GROUP_MESSAGE_CREATE"}) {
            var frame = QqGatewayProtocol.parse("""
                    {"op":0,"id":"event","s":91,"t":"%s","d":{
                     "id":"message","group_openid":"group","content":"hello",
                     "author":{"member_openid":"member","username":"Name"}}}
                    """.formatted(type)).orElseThrow();
            var group = QqGatewayProtocol.groupMessage(frame).orElseThrow();
            assertEquals("event", group.eventId());
            assertEquals("message", group.messageId());
            assertEquals("group", group.groupOpenId());
            assertEquals("member", group.memberOpenId());
            assertEquals("Name", group.authorName());
            assertFalse(group.authorIsBot());
        }
    }

    @Test
    void unrelatedDispatchIsNotTreatedAsAGroupMessage() {
        QqGatewayProtocol.Frame frame = QqGatewayProtocol.parse("""
                {"op":0,"s":93,"t":"GROUP_ADD_ROBOT","d":{}}
                """).orElseThrow();

        assertTrue(QqGatewayProtocol.groupMessage(frame).isEmpty());
    }
}
