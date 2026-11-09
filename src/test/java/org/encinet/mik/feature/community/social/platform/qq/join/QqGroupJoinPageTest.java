package org.encinet.mik.feature.community.social.platform.qq.join;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QqGroupJoinPageTest {
    @Test
    void parsesOfficialListShapeAndLeavesInvalidEntriesPending() {
        QqGroupJoinPage page = QqGroupJoinPage.parse("""
                {"list":[
                  {"member_openid":"member","join_request_id":"request","apply_at":"1970-01-01T00:01:00Z",
                   "verify_info":{"method":"verify_message","verify_message":"005924"}},
                  {"member_openid":"other","join_request_id":"invalid-time","apply_at":"bad"},
                  {"group_openid":"wrong-group","member_openid":"other","join_request_id":"wrong",
                   "apply_at":"1970-01-01T00:01:00Z"}
                ],"next_cursor":"opaque+/= cursor"}
                """, "group", Instant.now());
        assertEquals("opaque+/= cursor", page.nextCursor());
        assertEquals(2, page.skippedEntries());
        assertEquals(1, page.requests().size());
        QqGroupJoinRequest request = page.requests().getFirst();
        assertEquals("group", request.groupOpenId());
        assertEquals(Instant.ofEpochSecond(60), request.appliedAt());
        assertEquals(List.of("005924"), request.answers());
        assertEquals("", QqGroupJoinPage.parse("{\"list\":[]}", "group", Instant.now()).nextCursor());
    }

    @Test
    void malformedPagesCannotMasqueradeAsAnEmptyFinalPage() {
        for (String body : List.of("null", "[]", "{}", "{\"list\":{}}",
                "{\"list\":[],\"next_cursor\":123}", "{\"list\":[],\"next_cursor\":null}",
                "{\"list\":[],\"next_cursor\":\"\\n\"}",
                "{\"list\":[],\"next_cursor\":\"" + "x".repeat(1_025) + "\"}",
                "{\"list\":[" + String.join(",", java.util.Collections.nCopies(51, "{}")) + "]}")) {
            assertThrows(IllegalArgumentException.class,
                    () -> QqGroupJoinPage.parse(body, "group", Instant.now()));
        }
    }
}
