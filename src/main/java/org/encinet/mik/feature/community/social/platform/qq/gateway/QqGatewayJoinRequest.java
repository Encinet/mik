package org.encinet.mik.feature.community.social.platform.qq.gateway;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Authenticated join-application fields plus the local Gateway receive time. */
public record QqGatewayJoinRequest(
        String groupOpenId,
        String memberOpenId,
        String joinRequestId,
        List<String> answers,
        Instant receivedAt
) {
    public QqGatewayJoinRequest {
        answers = List.copyOf(answers);
    }

    static Optional<QqGatewayJoinRequest> from(JsonElement payload, Instant receivedAt) {
        if (payload == null || !payload.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject data = payload.getAsJsonObject();
        String group = string(data, "group_openid");
        String member = string(data, "member_openid");
        String request = string(data, "join_request_id");
        JsonElement bot = data.get("bot");
        if (!validId(group, 256) || !validId(member, 256) || !validId(request, 2_048)
                || (data.has("auto_approved") && !data.get("auto_approved").isJsonNull())
                || (bot != null && (!bot.isJsonPrimitive()
                || !bot.getAsJsonPrimitive().isBoolean() || bot.getAsBoolean()))) {
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
        return Optional.of(new QqGatewayJoinRequest(group, member, request,
                answers, receivedAt));
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
