package org.encinet.mik.module.social.platform.qq.gateway;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.OptionalLong;

/** Pure QQ Gateway frame parsing and client-frame encoding. */
final class QqGatewayProtocol {

    static final int OP_DISPATCH = 0;
    static final int OP_HEARTBEAT = 1;
    static final int OP_IDENTIFY = 2;
    static final int OP_RESUME = 6;
    static final int OP_RECONNECT = 7;
    static final int OP_INVALID_SESSION = 9;
    static final int OP_HELLO = 10;
    static final int OP_HEARTBEAT_ACK = 11;

    static final int INTENT_GROUP_AND_C2C_EVENT = 1 << 25;
    static final String EVENT_READY = "READY";
    static final String EVENT_RESUMED = "RESUMED";
    static final String EVENT_GROUP_AT_MESSAGE_CREATE = "GROUP_AT_MESSAGE_CREATE";
    static final String EVENT_GROUP_MESSAGE_CREATE = "GROUP_MESSAGE_CREATE";

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private QqGatewayProtocol() {
    }

    static Optional<Frame> parse(String payload) {
        try {
            JsonElement parsed = JsonParser.parseString(payload);
            if (!parsed.isJsonObject()) {
                return Optional.empty();
            }
            JsonObject object = parsed.getAsJsonObject();
            OptionalLong parsedOpcode = integerValue(object, "op");
            if (parsedOpcode.isEmpty() || parsedOpcode.getAsLong() < 0
                    || parsedOpcode.getAsLong() > Integer.MAX_VALUE) {
                return Optional.empty();
            }
            Long sequence = null;
            JsonElement sequenceValue = object.get("s");
            if (sequenceValue != null && !sequenceValue.isJsonNull()) {
                OptionalLong parsedSequence = integerValue(object, "s");
                if (parsedSequence.isEmpty() || parsedSequence.getAsLong() < 0) {
                    return Optional.empty();
                }
                sequence = parsedSequence.getAsLong();
            }
            return Optional.of(new Frame((int) parsedOpcode.getAsLong(), sequence,
                    string(object, "id"), string(object, "t"), object.get("d")));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    static OptionalLong heartbeatInterval(Frame frame) {
        if (frame.opcode() != OP_HELLO || frame.data() == null
                || !frame.data().isJsonObject()) {
            return OptionalLong.empty();
        }
        return integerValue(frame.data().getAsJsonObject(), "heartbeat_interval");
    }

    static Optional<String> readySessionId(Frame frame) {
        if (!EVENT_READY.equals(frame.type()) || frame.data() == null
                || !frame.data().isJsonObject()) {
            return Optional.empty();
        }
        String value = string(frame.data().getAsJsonObject(), "session_id");
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    static Optional<QqGatewayGroupMessage> groupMessage(Frame frame) {
        if (frame.opcode() != OP_DISPATCH
                || !isGroupMessageEvent(frame.type())) {
            return Optional.empty();
        }
        return QqGatewayGroupMessage.from(frame.data(), frame.eventId());
    }

    private static boolean isGroupMessageEvent(String eventType) {
        return EVENT_GROUP_AT_MESSAGE_CREATE.equals(eventType)
                || EVENT_GROUP_MESSAGE_CREATE.equals(eventType);
    }

    static String identify(String accessToken) {
        JsonObject data = new JsonObject();
        data.addProperty("token", "QQBot " + accessToken);
        data.addProperty("intents", INTENT_GROUP_AND_C2C_EVENT);
        com.google.gson.JsonArray shard = new com.google.gson.JsonArray();
        shard.add(0);
        shard.add(1);
        data.add("shard", shard);
        JsonObject properties = new JsonObject();
        properties.addProperty("$os", System.getProperty("os.name", "unknown"));
        properties.addProperty("$browser", "mik");
        properties.addProperty("$device", "mik");
        data.add("properties", properties);
        return frame(OP_IDENTIFY, data);
    }

    static String resume(String accessToken, String sessionId, long sequence) {
        JsonObject data = new JsonObject();
        data.addProperty("token", "QQBot " + accessToken);
        data.addProperty("session_id", sessionId);
        data.addProperty("seq", sequence);
        return frame(OP_RESUME, data);
    }

    static String heartbeat(Long sequence) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("op", OP_HEARTBEAT);
        if (sequence == null) {
            envelope.add("d", com.google.gson.JsonNull.INSTANCE);
        } else {
            envelope.addProperty("d", sequence);
        }
        return GSON.toJson(envelope);
    }

    private static String frame(int opcode, JsonElement data) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("op", opcode);
        envelope.add("d", data);
        return GSON.toJson(envelope);
    }

    private static OptionalLong integerValue(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()) {
            return OptionalLong.empty();
        }
        try {
            BigDecimal number = value.getAsBigDecimal();
            return OptionalLong.of(number.longValueExact());
        } catch (ArithmeticException | NumberFormatException ignored) {
            return OptionalLong.empty();
        }
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            return "";
        }
        try {
            return value.getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    record Frame(int opcode, Long sequence, String eventId, String type, JsonElement data) {
    }
}
