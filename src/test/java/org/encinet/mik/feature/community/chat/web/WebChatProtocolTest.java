package org.encinet.mik.feature.community.chat.web;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WebChatProtocolTest {
    @Test void parsesOnlyOneFlatStringObject() throws Exception {
        var message = WebChatModule.readMessage("{\"type\":\"send\",\"requestId\":\"1234567890123456\",\"channel\":\"public\",\"text\":\"<script>plain text</script>\"}");
        assertEquals("<script>plain text</script>", message.get("text").getAsString());
    }

    @Test void rejectsNestedJsonDuplicateKeysAndTrailingDocuments() {
        assertThrows(Exception.class, () -> WebChatModule.readMessage("{\"text\":" + "[".repeat(3000) + "0" + "]".repeat(3000) + "}"));
        assertThrows(Exception.class, () -> WebChatModule.readMessage("{\"channel\":\"public\",\"channel\":\"staff\"}"));
        assertThrows(Exception.class, () -> WebChatModule.readMessage("{\"type\":\"send\"} {}"));
        assertThrows(Exception.class, () -> WebChatModule.readMessage("{\"a\":\"1\",\"b\":\"2\",\"c\":\"3\",\"d\":\"4\",\"e\":\"5\",\"f\":\"6\"}"));
    }
}
