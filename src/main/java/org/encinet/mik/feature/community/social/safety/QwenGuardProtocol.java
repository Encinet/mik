package org.encinet.mik.feature.community.social.safety;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.regex.Pattern;

/** Provider-specific wire formats for the same outbound Qwen3Guard moderation input. */
enum QwenGuardProtocol {
    OVH {
        @Override
        String requestBody(String model, String text) {
            JsonObject message = new JsonObject();
            message.addProperty("role", "user");
            message.addProperty("content", text);
            JsonArray messages = new JsonArray();
            messages.add(message);
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.addProperty("temperature", 0);
            body.addProperty("max_tokens", 512);
            body.addProperty("stream", false);
            body.add("messages", messages);
            return body.toString();
        }

        @Override
        boolean parse(JsonObject root) {
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices.size() != 1) throw new IllegalArgumentException();
            JsonObject choice = choices.get(0).getAsJsonObject();
            if (!"stop".equals(string(choice.get("finish_reason")))) {
                throw new IllegalArgumentException();
            }
            var verdict = VERDICT.matcher(string(choice.getAsJsonObject("message").get("content")).strip());
            if (!verdict.matches()) throw new IllegalArgumentException();
            if ("Safe".equals(verdict.group(1)) && !"None".equals(verdict.group(2).strip())) {
                throw new IllegalArgumentException();
            }
            return "Safe".equals(verdict.group(1));
        }
    },
    EDEN {
        @Override
        String requestBody(String model, String text) {
            JsonObject input = new JsonObject();
            input.addProperty("text", text);
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.add("input", input);
            return body.toString();
        }

        @Override
        boolean parse(JsonObject root) {
            if (!"success".equals(string(root.get("status")))
                    || !"ovhcloud".equals(string(root.get("provider")))
                    || !"text".equals(string(root.get("feature")))
                    || !"moderation".equals(string(root.get("subfeature")))) {
                throw new IllegalArgumentException();
            }
            JsonObject output = root.getAsJsonObject("output");
            // Eden normalizes provider verdicts to likelihoods. Accept only its lowest risk level.
            boolean safe = lowestRisk(output, "nsfw_likelihood", "nsfw_likelihood_score");
            JsonElement items = output.get("items");
            if (items != null && !items.isJsonNull()) {
                for (JsonElement element : items.getAsJsonArray()) {
                    JsonObject item = element.getAsJsonObject();
                    if (string(item.get("label")).isBlank()) throw new IllegalArgumentException();
                    safe &= lowestRisk(item, "likelihood", "likelihood_score");
                }
            }
            return safe;
        }
    };

    private static final Pattern VERDICT = Pattern.compile(
            "\\ASafety:\\h*(Safe|Unsafe|Controversial)\\s+Categories:\\h*([^\\r\\n]+)"
                    + "(?:\\RRefusal:\\h*(?:Yes|No))?\\s*\\z");

    abstract String requestBody(String model, String text);

    abstract boolean parse(JsonObject root);

    final boolean allows(String body) {
        try {
            return parse(JsonParser.parseString(body).getAsJsonObject());
        } catch (RuntimeException error) {
            throw new IllegalStateException("Qwen Guard returned an invalid moderation result");
        }
    }

    private static String string(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException();
        }
        return value.getAsString();
    }

    private static boolean lowestRisk(JsonObject object, String levelField, String scoreField) {
        double level = number(object.get(levelField));
        double score = number(object.get(scoreField));
        if (level < 1 || level > 5 || level != Math.rint(level) || score < 0 || score > 1) {
            throw new IllegalArgumentException();
        }
        return level == 1;
    }

    private static double number(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException();
        }
        double number = value.getAsDouble();
        if (!Double.isFinite(number)) throw new IllegalArgumentException();
        return number;
    }
}
