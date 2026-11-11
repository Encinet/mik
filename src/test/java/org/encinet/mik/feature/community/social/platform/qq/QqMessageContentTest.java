package org.encinet.mik.feature.community.social.platform.qq;

import org.encinet.mik.feature.community.social.platform.qq.gateway.QqGatewayGroupMessage;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.encinet.mik.feature.community.chat.model.ChatProcessingContext;
import org.encinet.mik.feature.community.chat.pipeline.ChatProcessor;
import org.encinet.mik.feature.community.chat.render.MinecraftChatContentRenderer;
import org.encinet.mik.feature.community.chat.web.WebChatMessage;
import org.encinet.mik.feature.community.social.api.SocialInboundMessage;
import org.encinet.mik.feature.community.social.chat.SocialChatPolicy;
import org.encinet.mik.feature.community.social.platform.matrix.render.MatrixChatRenderer;
import org.encinet.mik.feature.community.social.platform.qq.render.QqChatRenderer;
import org.encinet.mik.test.ChatTestMessages;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqMessageContentTest {
    @Test
    void attachmentOnlyMessagesShowTypeAndVoiceTranscriptionWithoutTransportDetails() {
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
        assertEquals("[image] [video] [audio] 今天一起建房子 [file]", text.body());
    }

    @Test
    void structuredCardsRetainReadableFieldsWithoutAutomaticLinks() {
        SocialInboundMessage.Text text = text("""
                {"message_type":3,"ark_data":{"ark_type":"map","ark_name":"位置",
                 "prompt":"一起去这里","fields":{"title":"公园","address":"人民路 1 号","desc":"集合地点",
                 "jump_url":"https://maps.example.org/place?x=1&y=2","preview":"https://cdn.example.org/map.png",
                 "source_logo":"https://unused.example.org/logo","unknown":"private-metadata"}}}
                """);
        assertEquals("[location] 公园 集合地点 人民路 1 号 一起去这里", text.body());
        assertTrue(text.commandBody().isEmpty());
        java.util.Map.of("tuwen", "[card]", "feed", "[card]", "miniapp", "[card]",
                "contact_card", "[contact]", "video_share", "[video]", "music_together", "[music]",
                "picture", "[image]", "future-card", "[card]").forEach((type, label) -> {
            SocialInboundMessage.Text card = text("{\"message_type\":3,\"ark_data\":{\"ark_type\":\"" + type
                    + "\",\"fields\":{\"title\":\"shared title\",\"jump_url\":\"https://example.org/shared?rkey=private\","
                    + "\"preview\":\"https://cdn.example.org/private.png\"}}}");
            assertEquals(label + " shared title", card.body(), type);
            assertTrue(card.commandBody().isEmpty(), type);
        });
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
        assertEquals("/bind forged [history] 💬 Alice: /ai quoted [multiple] 💬 Bob: hello 💬 [image]", display.body());
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
    void qqFaceTokensBecomeReadableBeforeMentionSpansAndQuotedTextAreBuilt() {
        String face = "<faceType=1,faceId=\"32\",ext=\"eyJ0ZXh0Ijoi55aR6ZeuIn0=\">";
        JsonObject payload = new JsonObject();
        payload.addProperty("content", "你好 " + face + " <@member-one> <faceType=2,faceId=\"14\"> 😀");
        JsonObject mention = new JsonObject();
        mention.addProperty("id", "member-one");
        mention.addProperty("member_openid", "member-one");
        mention.addProperty("username", "Alice");
        JsonArray mentions = new JsonArray();
        mentions.add(mention);
        payload.add("mentions", mentions);

        SocialInboundMessage inbound = inbound(payload);
        SocialInboundMessage.Text text = (SocialInboundMessage.Text) inbound.content();
        assertEquals("你好 [face] @Alice [face] 😀", text.body());
        assertTrue(text.commandBody().isEmpty());
        var span = inbound.references().mentionSpans().getFirst();
        assertEquals("@Alice", text.body().substring(span.start(), span.end()));
        assertFalse(text.body().contains("faceType="));

        payload = new JsonObject();
        payload.addProperty("content", "<faceType=4,faceId=\"\",ext=\"eyJ0ZXh0Ijoi55aR6ZeuIn0=\"> <emoji:123>");
        assertEquals("[face] [face]", ((SocialInboundMessage.Text) inbound(payload).content()).body());

        JsonObject quote = new JsonObject();
        quote.addProperty("content", "疑问 " + face + " <@member-one>");
        JsonObject author = new JsonObject();
        author.addProperty("username", "Bob");
        quote.add("author", author);
        JsonArray elements = new JsonArray();
        elements.add(quote);
        payload = new JsonObject();
        payload.addProperty("message_type", 103);
        payload.add("msg_elements", elements);
        SocialInboundMessage quoted = inbound(payload);
        assertEquals("↪ Bob: 疑问 [face] @member-one",
                ((SocialInboundMessage.Text) quoted.content()).body());
        assertTrue(quoted.references().mentionSpans().isEmpty());
        payload = new JsonObject();
        payload.addProperty("content", "literal <faceType=1,faceId=\"unknown\">");
        assertEquals("literal <faceType=1,faceId=\"unknown\">",
                ((SocialInboundMessage.Text) inbound(payload).content()).body());
    }

    @Test
    void structuredMessageTypesKeepTheirMeaningWhenNestedContentIsMissing() {
        for (var entry : java.util.Map.of(
                3, "[card]", 101, "[multiple]", 102, "[history]", 103, "[reply]").entrySet()) {
            JsonObject payload = new JsonObject();
            payload.addProperty("message_type", entry.getKey());
            SocialInboundMessage.Text text = (SocialInboundMessage.Text) inbound(payload).content();
            assertEquals(entry.getValue(), text.body());
            assertEquals("", text.commandBody());
        }

        JsonObject record = new JsonObject();
        record.addProperty("message_type", 102);
        record.addProperty("content", "今天的学习计划已完成");
        JsonArray elements = new JsonArray();
        elements.add(record);
        JsonObject payload = new JsonObject();
        payload.addProperty("message_type", 103);
        payload.add("msg_elements", elements);
        assertEquals("[history] 今天的学习计划已完成",
                ((SocialInboundMessage.Text) inbound(payload).content()).body());
    }

    @Test
    void historyLabelsPreserveRoutePrefixesAndNeverEnterCommandInput() {
        SocialInboundMessage.Text history = text("""
                {"message_type":102,"content":"# 看这段记录","msg_elements":[
                  {"author":{"username":"Alice"},"content":"/bind quoted"}]}
                """);
        String selected = SocialChatPolicy.prefixed("#", true).select(history.body()).orElseThrow();
        assertEquals("看这段记录 [history] 💬 Alice: /bind quoted", selected);
        assertTrue(history.commandBody().isEmpty());

        // The official event example also carries flattened history inside a reply element.
        SocialInboundMessage.Text flattened = text("""
                {"message_type":103,"content":" ","msg_elements":[
                  {"content":"=== 消息 1 ===\\n[消息内容] /bind quoted"}]}
                """);
        assertTrue(flattened.body().contains("/bind quoted"));
        assertTrue(flattened.commandBody().isEmpty());
    }

    @Test
    void typedPlaceholdersReachEveryDestinationAsReadableText() {
        JsonObject payload = new JsonObject();
        payload.addProperty("message_type", 102);
        payload.addProperty("content", "<faceType=1,faceId=\"32\",ext=\"eyJ0ZXh0Ijoi55aR6ZeuIn0=\">");
        SocialInboundMessage.Text source = (SocialInboundMessage.Text) inbound(payload).content();
        assertEquals("[face] [history]", source.body());
        for (String rendered : renderedDestinations(source)) {
            assertTrue(rendered.contains("[表情]"), rendered);
            assertTrue(rendered.contains("[聊天记录]"), rendered);
            assertFalse(rendered.contains("faceType="), rendered);
        }
    }

    @Test
    void textBudgetsKeepInlineElementsWholeInOwnAndNestedContent() {
        String face = "<faceType=1,faceId=\"32\">";
        JsonObject own = new JsonObject();
        own.addProperty("content", "a".repeat(1_997) + face);
        String body = ((SocialInboundMessage.Text) inbound(own).content()).body();
        assertTrue(body.endsWith("…"));
        assertFalse(body.contains("["));

        JsonObject child = new JsonObject();
        child.addProperty("content", "a".repeat(509) + face);
        JsonArray elements = new JsonArray();
        elements.add(child);
        JsonObject quoted = new JsonObject();
        quoted.addProperty("message_type", 103);
        quoted.add("msg_elements", elements);
        String nested = ((SocialInboundMessage.Text) inbound(quoted).content()).body();
        assertTrue(nested.endsWith("…"));
        assertFalse(nested.contains("["));
        assertFalse(nested.contains("faceType="));
    }

    @Test
    void limitsNestingAndElementWorkWithoutSplittingSurrogatePairs() {
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
        assertFalse(bounded.contains("https://"));
        for (int index = 0; index < bounded.length(); index++) {
            char point = bounded.charAt(index);
            if (Character.isHighSurrogate(point)) assertTrue(++index < bounded.length() && Character.isLowSurrogate(bounded.charAt(index)));
            else assertFalse(Character.isLowSurrogate(point));
        }
    }

    @Test
    void attachmentUrlsNeverBecomeBodyTextAndMalformedTypesRemainReadable() {
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
            assertEquals("[file]", body);
        }
        assertEquals("[message]", text("{\"message_type\":999}").body());
        assertTrue(text("{\"message_type\":999,\"content\":\"future format\"}").commandBody().isEmpty());
        assertEquals("hello", text("{\"content\":\"hello\",\"attachments\":[null,{},42],\"ark_data\":[],\"msg_elements\":{}}")
                .body());
    }

    @Test
    void faceAndMediaMetadataStayOutOfEveryDestination() {
        SocialInboundMessage.Text source = text("""
                {"content":"<faceType=1,faceId=\\\"32\\\">", "attachments":[
                 {"content_type":"image/png","filename":"56F59943ADF5AE9A90EF429DE520B47C.png",
                  "width":240,"height":240,"size":261296,
                  "url":"https://multimedia.nt.qq.com.cn/download?appid=1407&fileid=private&rkey=private"}]}
                """);
        assertEquals("[face] [image]", source.body());
        for (String rendered : renderedDestinations(source)) {
            assertTrue(rendered.contains("[表情]"), rendered);
            assertTrue(rendered.contains("[图片]"), rendered);
            for (String hidden : new String[]{"56F59943", "240", "261296", "multimedia.nt.qq.com.cn", "fileid=", "rkey="}) {
                assertFalse(rendered.contains(hidden), rendered);
            }
        }
    }

    @Test
    void explicitlyWrittenLinksRemainPartOfTheAuthorsText() {
        String url = "https://example.org/shared?fileid=abc&rkey=def";
        String body = text("{\"content\":\"" + url + "\",\"attachments\":[{\"content_type\":\"image/png\","
                + "\"url\":\"https://cdn.example.org/private-image\"}]}").body();
        var content = new ChatProcessor().process(body, ChatProcessingContext.external());
        assertEquals(url + " [image]", body);
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

    private static List<String> renderedDestinations(SocialInboundMessage.Text text) {
        var original = ChatTestMessages.minecraft("Alice", text.body()).submission();
        var source = new org.encinet.mik.feature.community.chat.model.ChatSubmission(original.id(), original.origin(),
                original.sender(), original.conversation(), original.sourceText(),
                new org.encinet.mik.feature.community.chat.model.ChatReferences(java.util.Optional.empty(),
                        java.util.Optional.empty(), List.of(), text.placeholders()),
                original.receivedAt(), original.processingContext());
        var languages = org.encinet.mik.shared.i18n.FluentTestSupport.languages();
        var content = new ChatProcessor().process(source).content()
                .localized(key -> languages.t(org.encinet.mik.shared.i18n.Language.ZH_CN, key));
        var message = ChatTestMessages.minecraft("Alice", text.body(), content);
        var game = new MinecraftChatContentRenderer().render(content, MinecraftChatContentRenderer.Context.empty());
        return List.of(
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(game),
                MatrixChatRenderer.render(message, 1_500).plainText(),
                QqChatRenderer.render(message, 1_500).markdown().replace("\\", ""),
                new WebChatMessage(message, null, null).payload().toString());
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
        return QqInboundMapper.map(QqGatewayGroupMessage.from(payload, "event-id").orElseThrow(),
                "app-id", _ -> CompletableFuture.completedFuture(null));
    }
}
