package org.encinet.mik.feature.community.social.platform.qq.gateway;

import com.google.gson.JsonElement;
import java.util.Optional;

/** Verified identifiers from GROUP_MEMBER_ADD / GROUP_MEMBER_REMOVE, never message content. */
public record QqGroupMemberEvent(String groupOpenId, String memberOpenId, boolean joined) {
    static Optional<QqGroupMemberEvent> from(String eventType, JsonElement payload) {
        if (!"GROUP_MEMBER_ADD".equals(eventType) && !"GROUP_MEMBER_REMOVE".equals(eventType)) return Optional.empty();
        if (payload == null || !payload.isJsonObject()) return Optional.empty();
        var data = payload.getAsJsonObject();
        for (String name : java.util.List.of("group_openid", "member_openid")) {
            var value = data.get(name);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                    || value.getAsString().isBlank() || value.getAsString().codePoints().anyMatch(Character::isISOControl)) {
                return Optional.empty();
            }
        }
        return Optional.of(new QqGroupMemberEvent(data.get("group_openid").getAsString(),
                data.get("member_openid").getAsString(), "GROUP_MEMBER_ADD".equals(eventType)));
    }
}
