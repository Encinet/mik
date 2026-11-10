package org.encinet.mik.feature.community.social.platform.qq.gateway;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.encinet.mik.feature.community.chat.model.ChatProcessingContext;
import org.encinet.mik.feature.community.chat.pipeline.ChatProcessor;
import org.encinet.mik.feature.community.chat.render.MinecraftChatContentRenderer;
import org.encinet.mik.feature.community.chat.web.WebChatMessage;
import org.encinet.mik.feature.community.social.api.SocialInboundMessage;
import org.encinet.mik.feature.community.social.platform.matrix.render.MatrixChatRenderer;
import org.encinet.mik.feature.community.social.platform.qq.render.QqChatRenderer;
import org.encinet.mik.test.ChatTestMessages;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqMessageContentTest {
    @Test
    void attachmentOnlyMessagesKeepMediaDetailsAndVoiceTranscription() {
        SocialInboundMessage inbound = inbound("""
                {"attachments":[
                  {"content_type":"image/gif","filename":"动画.gif","width":640,"height":480,
                   "size":1234,"url":"//cdn.example.org/image.gif?fileid=abc&rkey=def"},
                  {"content_type":"video/mp4","filename":"video.mp4","url":"https://cdn.example.org/video.mp4"},
                  {"content_type":"voice","url":"https://cdn.example.org/voice.silk",
                   "voice_wav_url":"https://cdn.example.org/voice.wav","asr_refer_text":"今天一起建房子"},
                  {"content_type":"file","filename":"blueprint.zip","size":2048,"url":"https://cdn.example.org/file"}
                ], "message_scene":{"ext":["auth_token=private-scene-token"]}}
                """);
        SocialInboundMessage.Text text = (SocialInboundMessage.Text) inbound.content();
        assertTrue(text.commandBody().isEmpty());
        for (String value : new String[]{"🖼️", "动画.gif", "640×480", "1234 B", "🎞️", "video.mp4",
                "🎙️", "https://cdn.example.org/voice.wav", "今天一起建房子", "📎", "blueprint.zip", "2048 B",
                "https://cdn.example.org/image.gif?fileid=abc&rkey=def"}) assertTrue(text.body().contains(value), text.body());
        assertFalse(text.body().contains("private-scene-token"));
    }

    @Test
    void structuredCardsRetainReadableFieldsAndOnlySafeLinks() {
        SocialInboundMessage.Text text = text("""
                {"message_type":3,"ark_data":{"ark_type":"map","ark_name":"位置",
                 "prompt":"一起去这里","fields":{"title":"公园","address":"人民路 1 号","desc":"集合地点",
                 "jump_url":"https://maps.example.org/place?x=1&y=2","preview":"https://cdn.example.org/map.png",
                 "source_logo":"https://unused.example.org/logo","unknown":"private-metadata"}}}
                """);
        for (String value : new String[]{"📍", "公园", "人民路 1 号", "集合地点", "一起去这里",
                "https://maps.example.org/place?x=1&y=2", "https://cdn.example.org/map.png"}) assertTrue(text.body().contains(value));
        assertTrue(text.commandBody().isEmpty());
        assertFalse(text.body().contains("unused.example.org"));
        assertFalse(text.body().contains("private-metadata"));
        for (String type : new String[]{"tuwen", "feed", "miniapp", "contact_card", "video_share", "music_together", "picture", "future-card"}) {
            SocialInboundMessage.Text card = text("{\"message_type\":3,\"ark_data\":{\"ark_type\":\"" + type
                    + "\",\"fields\":{\"title\":\"shared title\",\"jump_url\":\"javascript:alert(1)\"}}}");
            assertTrue(card.body().contains("shared title"));
            assertFalse(card.body().contains("javascript:"));
        }
    }

    @Test
    void forwardedAndQuotedMessagesKeepAuthorsButCannotBecomeCommandsOrTrustedTargets() {
        SocialInboundMessage forwarded = inbound("""
                {"message_type":102,"content":"/bind forged","msg_elements":[
                 {"message_type":0,"author":{"username":"Alice","member_openid":"alice"},"content":"/ai quoted"},
                 {"message_type":101,"msg_elements":[
                  {"content":"hello","author":{"username":"Bob"}},
                  {"attachments":[{"content_type":"image/png","filename":"quoted.png","url":"https://cdn.example.org/quoted.png"}]}]}]}
                """);
        SocialInboundMessage.Text display = (SocialInboundMessage.Text) forwarded.content();
        for (String value : new String[]{"Alice:", "/ai quoted", "Bob:", "hello", "quoted.png"}) assertTrue(display.body().contains(value));
        assertTrue(display.commandBody().isEmpty());
        assertTrue(forwarded.references().targetIdentities().isEmpty());
        SocialInboundMessage reply = inbound("""
                {"message_type":103,"content":"/我的","msg_elements":[
                 {"author":{"username":"Alice","member_openid":"alice"},"content":"/bind quoted"}]}
                """);
        assertEquals("/我的", ((SocialInboundMessage.Text) reply.content()).commandBody());
        assertTrue(((SocialInboundMessage.Text) reply.content()).body().contains("↪ Alice: /bind quoted"));
        assertEquals("alice", reply.references().repliedAuthor().orElseThrow().key().subject());
    }

    @Test
    void memberOpenIdMentionTokensAndUnicodeEmojiSurviveRichMessages() {
        SocialInboundMessage inbound = inbound("""
                {"content":"你好 <@member-one> 😀 <@all>","mentions":[
                 {"id":"different-user-id","member_openid":"member-one","username":"Alice"}],
                 "attachments":[{"content_type":"image/jpeg","url":"https://cdn.example.org/image.jpg"}]}
                """);
        String body = ((SocialInboundMessage.Text) inbound.content()).body();
        assertTrue(body.startsWith("你好 @Alice 😀 @all"));
        var span = inbound.references().mentionSpans().getFirst();
        assertEquals("@Alice", body.substring(span.start(), span.end()));
        assertEquals("member-one", span.identity().key().subject());
        var content = new ChatProcessor().process(body, ChatProcessingContext.external());
        assertTrue(ChatProcessor.effects(content).isEmpty());
    }

    @Test
    void limitsNestingAndElementWorkWithoutCuttingSignedLinksOrSurrogatePairs() {
        JsonObject payload = new JsonObject();
        JsonObject cursor = payload;
        for (int index = 0; index < 500; index++) {
            JsonObject next = new JsonObject();
            next.addProperty("content", "😀 " + index);
            JsonArray array = new JsonArray();
            array.add(next);
            cursor.add("msg_elements", array);
            cursor = next;
        }
        cursor.addProperty("content", "deep-value-must-not-appear");
        String nested = ((SocialInboundMessage.Text) inbound(payload).content()).body();
        assertTrue(nested.endsWith("…"));
        assertFalse(nested.contains("deep-value-must-not-appear"));
        JsonArray attachments = new JsonArray();
        for (int index = 0; index < 500; index++) {
            JsonObject attachment = new JsonObject();
            attachment.addProperty("filename", "a😀".repeat(100));
            attachment.addProperty("url", "https://cdn.example.org/?key=" + "a".repeat(1_200));
            attachments.add(attachment);
        }
        payload = new JsonObject();
        payload.addProperty("content", "😀".repeat(2_000));
        payload.add("attachments", attachments);
        String bounded = ((SocialInboundMessage.Text) inbound(payload).content()).body();
        assertTrue(bounded.length() <= 4_000);
        assertTrue(bounded.endsWith("…"));
        assertFalse(bounded.contains("?key=" + "a".repeat(300) + "…"));
        for (int index = 0; index < bounded.length(); index++) {
            char point = bounded.charAt(index);
            if (Character.isHighSurrogate(point)) assertTrue(++index < bounded.length() && Character.isLowSurrogate(bounded.charAt(index)));
            else assertFalse(Character.isLowSurrogate(point));
        }
    }

    @Test
    void rejectsUnsafeAttachmentUrlsAndKeepsUnknownAndMalformedTypesReadable() {
        for (String url : new String[]{"javascript:alert(1)", "file:///etc/passwd", "https://user:password@example.org/file",
                "https://example.org/white space", "https://example.org/\\\\evil"}) {
            JsonObject attachment = new JsonObject();
            attachment.addProperty("url", url);
            attachment.addProperty("filename", "still-visible");
            JsonObject payload = new JsonObject();
            JsonArray values = new JsonArray();
            values.add(attachment);
            payload.add("attachments", values);
            String body = ((SocialInboundMessage.Text) inbound(payload).content()).body();
            assertTrue(body.contains("still-visible"));
            assertFalse(body.contains(url));
        }
        assertEquals("📨", text("{\"message_type\":999}").body());
        assertTrue(text("{\"message_type\":999,\"content\":\"future format\"}").commandBody().isEmpty());
        assertEquals("hello", text("{\"content\":\"hello\",\"attachments\":[null,{},42],\"ark_data\":[],\"msg_elements\":{}}")
                .body());
    }

    @Test
    void mediaLinksFlowThroughSharedParsingAndTheGameMatrixAndQqRenderers() {
        String url = "https://cdn.example.org/image.png?fileid=abc&rkey=def";
        String body = text("{\"attachments\":[{\"content_type\":\"image/png\",\"url\":\"" + url + "\"}]}").body();
        var content = new ChatProcessor().process(body, ChatProcessingContext.external());
        assertEquals(url, content.nodes().stream().filter(ChatNode.Link.class::isInstance)
                .map(ChatNode.Link.class::cast).findFirst().orElseThrow().target().toString());
        var message = ChatTestMessages.minecraft("Alice", body, content);
        assertTrue(MatrixChatRenderer.render(message, 1_500).html().contains("fileid=abc&amp;rkey=def"));
        assertTrue(QqChatRenderer.render(message, 1_500).markdown().contains(url));
        assertTrue(new WebChatMessage(message, null, null).payload().getAsJsonArray("content").asList().stream()
                .map(com.google.gson.JsonElement::getAsJsonObject)
                .anyMatch(part -> part.has("href") && part.get("href").getAsString().equals(url)));
        var rendered = new MinecraftChatContentRenderer().render(content, MinecraftChatContentRenderer.Context.empty());
        assertTrue(rendered.children().stream().anyMatch(component -> component.clickEvent() != null));
    }

    private static SocialInboundMessage.Text text(String fields) {
        return (SocialInboundMessage.Text) inbound(fields).content();
    }

    private static SocialInboundMessage inbound(String fields) {
        return inbound(JsonParser.parseString(fields).getAsJsonObject());
    }

    private static SocialInboundMessage inbound(JsonObject payload) {
        payload.addProperty("id", "message-id");
        payload.addProperty("group_openid", "group-id");
        JsonObject author = new JsonObject();
        author.addProperty("member_openid", "author-member");
        author.addProperty("username", "Author");
        payload.add("author", author);
        return QqGatewayGroupMessage.from(payload, "event-id").orElseThrow()
                .toInboundMessage("qq", "app-id", _ -> CompletableFuture.completedFuture(null));
    }
}
