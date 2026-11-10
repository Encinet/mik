package org.encinet.mik.feature.community.social.platform.qq.client;

import java.net.URI;

/** Official QQ HTTPS endpoints; Gateway WebSocket discovery remains dynamic. */
public final class QqApiEndpoints {
    private static final String API_BASE = "https://api.bot.qq.com";
    public static final URI ACCESS_TOKEN = apiUri("/app/getAppAccessToken");
    public static final URI GATEWAY = apiUri("/gateway/bot");

    private QqApiEndpoints() { }

    public static URI apiUri(String path) {
        return URI.create(API_BASE + path);
    }
}
