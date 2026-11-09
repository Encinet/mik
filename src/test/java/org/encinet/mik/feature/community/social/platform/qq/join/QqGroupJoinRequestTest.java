package org.encinet.mik.feature.community.social.platform.qq.join;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqGroupJoinRequestTest {
    private static final Instant OBSERVED = Instant.parse("2026-11-10T08:00:00Z");

    @Test
    void parsesOfficialMessageAndQuestionAnswerShapesUsingApplicationTime() {
        JsonObject data = application();
        QqGroupJoinRequest message = QqGroupJoinRequest.fromEvent(data, OBSERVED).orElseThrow();
        assertEquals("group-a", message.groupOpenId());
        assertEquals("member", message.memberOpenId());
        assertEquals("application", message.joinRequestId());
        assertEquals(List.of("081804"), message.answers());
        assertEquals(Instant.parse("2000-01-01T00:00:00Z"), message.appliedAt());

        data.add("verify_info", JsonParser.parseString("""
                {"method":"admin_review_qa","review_qa_list":[
                  {"question":"Name?","answer":"Alex"},
                  {"question":"Code?","answer":"005924"}
                ]}
                """));
        assertEquals(List.of("Alex", "005924"), QqGroupJoinRequest.fromEvent(data, OBSERVED)
                .orElseThrow().answers());
    }

    @Test
    void ignoresBotsAlreadyApprovedRequestsAndMissingIdentifiers() {
        JsonObject data = application();
        data.addProperty("bot", true);
        assertTrue(QqGroupJoinRequest.fromEvent(data, OBSERVED).isEmpty());
        data.remove("bot");
        data.add("auto_approved", JsonParser.parseString("{\"strategy_id\":\"strategy\"}"));
        assertTrue(QqGroupJoinRequest.fromEvent(data, OBSERVED).isEmpty());
        data.remove("auto_approved");
        for (String field : List.of("group_openid", "member_openid", "join_request_id")) {
            JsonObject incomplete = data.deepCopy();
            incomplete.remove(field);
            assertTrue(QqGroupJoinRequest.fromEvent(incomplete, OBSERVED).isEmpty());
        }
        assertTrue(QqGroupJoinRequest.fromEvent(JsonParser.parseString("[]"), OBSERVED).isEmpty());
    }

    @Test
    void missingUnsupportedOrNonStringVerificationNeverBecomesACode() {
        for (String verification : List.of("null", "[]",
                "{\"method\":\"unknown\",\"verify_message\":\"081804\"}",
                "{\"method\":\"verify_message\",\"verify_message\":81804}",
                "{\"method\":\"admin_review_qa\",\"review_qa_list\":{}}")) {
            JsonObject data = application();
            data.add("verify_info", JsonParser.parseString(verification));
            assertTrue(QqGroupJoinRequest.fromEvent(data, OBSERVED).orElseThrow()
                    .answers().stream().allMatch(String::isEmpty));
        }
    }

    @Test
    void listItemsUseTheirListedGroupAndCannotSwitchToAnotherGroup() {
        JsonObject data = application();
        data.remove("group_openid");
        assertTrue(QqGroupJoinRequest.fromEvent(data, OBSERVED).isEmpty());
        assertEquals("group-b", QqGroupJoinRequest.fromListItem(data, "group-b", OBSERVED)
                .orElseThrow().groupOpenId());
        data.addProperty("group_openid", "group-a");
        assertTrue(QqGroupJoinRequest.fromListItem(data, "group-b", OBSERVED).isEmpty());
    }

    @Test
    void missingMalformedAndFutureApplicationTimesRemainForManualReview() {
        for (String time : List.of("", "not-a-date", "2026-11-10T08:00:31Z", "1969-12-31T23:59:59Z")) {
            JsonObject data = application();
            data.addProperty("apply_at", time);
            assertTrue(QqGroupJoinRequest.fromEvent(data, OBSERVED).isEmpty(), time);
        }
        JsonObject data = application();
        data.remove("apply_at");
        assertTrue(QqGroupJoinRequest.fromEvent(data, OBSERVED).isEmpty());
        data.addProperty("apply_at", "2026-11-10T16:00:30+08:00");
        assertEquals(OBSERVED.plusSeconds(30), QqGroupJoinRequest.fromEvent(data, OBSERVED)
                .orElseThrow().appliedAt());
    }

    private static JsonObject application() {
        return JsonParser.parseString("""
                {"group_openid":"group-a","member_openid":"member",
                 "join_request_id":"application","apply_at":"2000-01-01T00:00:00Z",
                 "verify_info":{"method":"verify_message","verify_message":"081804"}}
                """).getAsJsonObject();
    }
}
