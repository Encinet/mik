package org.encinet.mik.feature.community.social.platform.qq.gateway;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqGatewayJoinRequestTest {
    private static final Instant RECEIVED = Instant.parse("2026-11-10T08:00:00Z");

    @Test
    void parsesOfficialMessageAndQuestionAnswerShapesUsingLocalReceiveTime() {
        JsonObject data = application();
        QqGatewayJoinRequest message = QqGatewayJoinRequest.from(data, RECEIVED).orElseThrow();
        assertEquals("group-a", message.groupOpenId());
        assertEquals("member", message.memberOpenId());
        assertEquals("application", message.joinRequestId());
        assertEquals(List.of("081804"), message.answers());
        assertEquals(RECEIVED, message.receivedAt());

        data.add("verify_info", JsonParser.parseString("""
                {"method":"admin_review_qa","review_qa_list":[
                  {"question":"Name?","answer":"Alex"},
                  {"question":"Code?","answer":"005924"}
                ]}
                """));
        assertEquals(List.of("Alex", "005924"), QqGatewayJoinRequest.from(data, RECEIVED)
                .orElseThrow().answers());
    }

    @Test
    void ignoresBotsAlreadyApprovedRequestsAndMissingIdentifiers() {
        JsonObject data = application();
        data.addProperty("bot", true);
        assertTrue(QqGatewayJoinRequest.from(data, RECEIVED).isEmpty());
        data.remove("bot");
        data.add("auto_approved", JsonParser.parseString("{\"strategy_id\":\"strategy\"}"));
        assertTrue(QqGatewayJoinRequest.from(data, RECEIVED).isEmpty());
        data.remove("auto_approved");
        for (String field : List.of("group_openid", "member_openid", "join_request_id")) {
            JsonObject incomplete = data.deepCopy();
            incomplete.remove(field);
            assertTrue(QqGatewayJoinRequest.from(incomplete, RECEIVED).isEmpty());
        }
        assertTrue(QqGatewayJoinRequest.from(JsonParser.parseString("[]"), RECEIVED).isEmpty());
    }

    @Test
    void missingUnsupportedOrNonStringVerificationNeverBecomesACode() {
        for (String verification : List.of("null", "[]",
                "{\"method\":\"unknown\",\"verify_message\":\"081804\"}",
                "{\"method\":\"verify_message\",\"verify_message\":81804}",
                "{\"method\":\"admin_review_qa\",\"review_qa_list\":{}}")) {
            JsonObject data = application();
            data.add("verify_info", JsonParser.parseString(verification));
            assertTrue(QqGatewayJoinRequest.from(data, RECEIVED).orElseThrow()
                    .answers().stream().allMatch(String::isEmpty));
        }
    }

    private static JsonObject application() {
        return JsonParser.parseString("""
                {"group_openid":"group-a","member_openid":"member",
                 "join_request_id":"application","apply_at":"2000-01-01T00:00:00Z",
                 "verify_info":{"method":"verify_message","verify_message":"081804"}}
                """).getAsJsonObject();
    }
}
