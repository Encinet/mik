package org.encinet.mik.feature.community.social.platform.qq.join;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Strictly decoded list page. Invalid applications remain pending instead of being declined. */
public record QqGroupJoinPage(List<QqGroupJoinRequest> requests, String nextCursor, int skippedEntries) {
    public QqGroupJoinPage {
        requests = List.copyOf(requests);
    }

    public static QqGroupJoinPage parse(String body, String groupOpenId, Instant observedAt) {
        JsonElement parsed = JsonParser.parseString(body);
        if (!parsed.isJsonObject()) throw invalidPage();
        JsonObject object = parsed.getAsJsonObject();
        JsonElement list = object.get("list");
        if (list == null || !list.isJsonArray() || list.getAsJsonArray().size() > 50) throw invalidPage();
        JsonElement cursorValue = object.get("next_cursor");
        String cursor = "";
        if (cursorValue != null) {
            if (!cursorValue.isJsonPrimitive() || !cursorValue.getAsJsonPrimitive().isString()) throw invalidPage();
            cursor = cursorValue.getAsString();
        }
        if (cursor.length() > 1_024 || cursor.codePoints().anyMatch(Character::isISOControl)) throw invalidPage();
        List<QqGroupJoinRequest> requests = new ArrayList<>();
        int skipped = 0;
        for (JsonElement item : list.getAsJsonArray()) {
            var request = QqGroupJoinRequest.fromListItem(item, groupOpenId, observedAt);
            if (request.isPresent()) requests.add(request.orElseThrow());
            else skipped++;
        }
        return new QqGroupJoinPage(requests, cursor, skipped);
    }

    private static IllegalArgumentException invalidPage() {
        return new IllegalArgumentException("Invalid QQ group join request list response");
    }
}
