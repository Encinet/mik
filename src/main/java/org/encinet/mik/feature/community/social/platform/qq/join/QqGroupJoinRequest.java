package org.encinet.mik.feature.community.social.platform.qq.join;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** One authenticated QQ application, shared by Gateway delivery and list reconciliation. */
public record QqGroupJoinRequest(
        String groupOpenId,
        String memberOpenId,
        String joinRequestId,
        List<String> answers,
        Instant appliedAt
) {
    public QqGroupJoinRequest {
        answers = List.copyOf(answers);
    }

    public static Optional<QqGroupJoinRequest> fromEvent(JsonElement payload, Instant observedAt) {
        return parse(payload, null, observedAt);
    }

    public static Optional<QqGroupJoinRequest> fromListItem(JsonElement payload, String groupOpenId, Instant observedAt) {
        return parse(payload, groupOpenId, observedAt);
    }

    private static Optional<QqGroupJoinRequest> parse(JsonElement payload, String listedGroup, Instant observedAt) {
        if (payload == null || !payload.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject data = payload.getAsJsonObject();
        String group = string(data, "group_openid");
        if (listedGroup != null) {
            if (!group.isEmpty() && !group.equals(listedGroup)) return Optional.empty();
            group = listedGroup;
        }
        String member = string(data, "member_openid");
        String request = string(data, "join_request_id");
        JsonElement bot = data.get("bot");
        if (!validId(group, 256) || !validId(member, 256) || !validId(request, 2_048)
                || (data.has("auto_approved") && !data.get("auto_approved").isJsonNull())
                || (bot != null && (!bot.isJsonPrimitive()
                || !bot.getAsJsonPrimitive().isBoolean() || bot.getAsBoolean()))) {
            return Optional.empty();
        }
        final Instant appliedAt;
        try {
            appliedAt = OffsetDateTime.parse(string(data, "apply_at")).toInstant();
            if (appliedAt.getEpochSecond() < 0 || appliedAt.isAfter(observedAt.plusSeconds(30))) {
                return Optional.empty();
            }
        } catch (DateTimeException error) {
            return Optional.empty();
        }
        List<String> answers = new ArrayList<>();
        JsonElement verification = data.get("verify_info");
        if (verification != null && verification.isJsonObject()) {
            JsonObject info = verification.getAsJsonObject();
            switch (string(info, "method")) {
                case "verify_message" -> answers.add(string(info, "verify_message"));
                case "admin_review_qa" -> {
                    JsonElement questions = info.get("review_qa_list");
                    if (questions != null && questions.isJsonArray()
                            && questions.getAsJsonArray().size() <= 10) {
                        for (JsonElement question : questions.getAsJsonArray()) {
                            if (question.isJsonObject()) {
                                answers.add(string(question.getAsJsonObject(), "answer"));
                            }
                        }
                    }
                }
                default -> { }
            }
        }
        return Optional.of(new QqGroupJoinRequest(group, member, request,
                answers, appliedAt));
    }

    private static boolean validId(String value, int maximumLength) {
        return !value.isBlank() && value.length() <= maximumLength
                && value.codePoints().noneMatch(Character::isISOControl);
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive()
                && value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
    }
}
