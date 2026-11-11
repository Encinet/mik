package org.encinet.mik.feature.community.social.platform.matrix.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatrixMessageContentTest {
    @Test
    void projectsStandardMessageKindsAndUnknownFallbackWithoutExecutingTheirText() {
        Map<String, String> labels = Map.of(
                "m.notice", "[notice]", "m.emote", "[emote]", "m.image", "[image]",
                "m.audio", "[audio]", "m.video", "[video]", "m.file", "[file]",
                "m.location", "[location]", "org.example.message", "[message]");
        labels.forEach((type, label) -> {
            JsonObject content = content(type, "!绑定 secret");
            content.addProperty("filename", "attachment.bin");
            MatrixRoomMessage message = message(content);
            assertEquals("!绑定 secret " + label, message.body(), type);
            assertEquals("", message.commandBody(), type);
        });
        MatrixRoomMessage text = message(content("m.text", "!状态"));
        assertEquals("!状态", text.commandBody());

        JsonObject sticker = content("m.sticker", "waving cat");
        assertEquals("waving cat [sticker]", process(new MatrixSyncProcessor(), "m.sticker", sticker)
                .getFirst().body());
    }

    @Test
    void preservesMediaCaptionAndBridgePrefixWithoutAppendingFileDetails() {
        JsonObject content = html("m.image", "#看图", "<p>#看图</p>");
        content.addProperty("filename", "cat.png");
        content.addProperty("url", "mxc://example.org/private-media");
        MatrixRoomMessage message = message(content);
        assertEquals("#看图 [image]", message.body());
        assertEquals("", message.commandBody());
        content.addProperty("body", "a".repeat(5_000));
        content.remove("format");
        assertTrue(message(content).body().length() <= 4_000);
        assertTrue(message(content).body().endsWith("… [image]"));
    }

    @Test
    void mediaFilenameFallbacksBecomeTypeLabelsWithoutTrustingHiddenHtmlMentions() {
        Map.of("m.image", "[image]", "m.audio", "[audio]", "m.video", "[video]", "m.file", "[file]")
                .forEach((type, label) -> {
                    JsonObject content = html(type, "56F59943.png", """
                            <a href="https://matrix.to/#/@bob:example.org">@Bob</a>
                            """);
                    content.addProperty("url", "mxc://example.org/private-media");
                    content.add("info", json("{\"w\":240,\"h\":240,\"size\":261296}"));
                    mentions(content, "@bob:example.org");
                    for (boolean explicitFilename : new boolean[]{false, true}) {
                        if (explicitFilename) content.addProperty("filename", "56F59943.png");
                        MatrixRoomMessage message = message(content);
                        assertEquals(label, message.body(), type);
                        assertEquals("", message.commandBody(), type);
                        assertTrue(message.mentions().isEmpty(), type);
                    }
                    content.addProperty("filename", "original-file.bin");
                    assertEquals("@Bob " + label, message(content).body(), type);
                    assertEquals(1, message(content).mentions().size(), type);
                });
    }

    @Test
    void stripsOnlyReplyFallbackAndPreservesOrdinaryQuotes() {
        JsonObject content = content("m.text", "> quoted\n\n!状态");
        assertEquals("> quoted !状态", message(content).body());
        content.add("m.relates_to", json("""
                {"m.in_reply_to":{"event_id":"$original"}}
                """));
        assertEquals("!状态", message(content).commandBody());

        content = html("m.text", "fallback", "<mx-reply>old</mx-reply><p>new</p>");
        assertEquals("old new", message(content).body());
        content.add("m.relates_to", json("""
                {"m.in_reply_to":{"event_id":"$original"}}
                """));
        assertEquals("new", message(content).body());
        content.addProperty("formatted_body", "<p>new</p><mx-reply>ordinary text</mx-reply>");
        assertEquals("new ordinary text", message(content).body());
    }

    @Test
    void rendersMatrix119CustomEmoticonsAndInlineImagesAsReadableText() {
        MatrixRoomMessage message = message(html("m.text", "fallback", """
                hello <img data-mx-emoticon src="mxc://example.org/a" alt="waving cat">
                <img data-mx-emoticon="ignored" src="mxc://example.org/b">
                <img src="mxc://example.org/c"><script>!绑定 hidden</script>
                """));
        assertEquals("hello waving cat [face] [image]", message.body());
        assertFalse(message.body().contains("mxc:"));
        assertEquals("", message.commandBody());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "<img src='mxc://example.org/a' alt='!unlink'>",
            "<img data-mx-emoticon src='mxc://example.org/a' alt='!unlink'>",
            "!bind <img src='mxc://example.org/a' alt='123456'>",
            "!bind <img data-mx-emoticon src='mxc://example.org/a' alt='123456'>"
    })
    void imageAlternativesRemainDisplayContentAndCannotSupplyCommands(String html) {
        MatrixRoomMessage message = message(html("m.text", "image fallback", html));
        assertTrue(message.body().startsWith("!"));
        assertTrue(message.placeholders().isEmpty(), "an authored alternative keeps its readable text");
        assertEquals("", message.commandBody());

        assertEquals("!unlink", message(content("m.text", "!unlink")).commandBody());
    }

    @Test
    void displayFallbackPreservesTheFormattedSourcesCommandRestrictions() {
        String html = "<img src='mxc://example.org/a' alt='" + Character.toString(7) + "'>";
        MatrixRoomMessage message = message(html("m.text", "!unlink", html));
        assertEquals("!unlink", message.body());
        assertEquals("", message.commandBody());
    }

    @Test
    void distinguishesThreadContinuityFallbackFromExplicitReplyTargets() {
        MatrixSyncProcessor processor = new MatrixSyncProcessor();
        process(processor, "m.room.message", content("m.text", "original"));
        JsonObject content = content("m.text", "> old\n\n!我的");
        JsonObject relation = json("""
                {"rel_type":"m.thread","event_id":"$root","is_falling_back":true,
                 "m.in_reply_to":{"event_id":"$event"}}
                """);
        content.add("m.relates_to", relation);
        MatrixRoomMessage fallback = process(processor, "m.room.message", content).getFirst();
        assertEquals("$root", fallback.threadRootEventId().orElseThrow());
        assertTrue(fallback.repliedAuthor().isEmpty());
        assertEquals("!我的", fallback.commandBody());
        relation.addProperty("is_falling_back", false);
        assertEquals("@alice:example.org", process(processor, "m.room.message", content).getFirst()
                .repliedAuthor().orElseThrow().userId());
    }

    @Test
    void percentDecodesMentionIdsWithoutTurningPlusIntoSpace() {
        String userId = "@alice+work/team:example.org";
        JsonObject content = html("m.text", "hello Alice", """
                hello <a href="https://matrix.to/#/%40alice+work%2Fteam%3Aexample.org">Alice</a>
                """);
        mentions(content, userId);
        MatrixRoomMessage message = message(content);
        assertEquals("hello Alice", message.body());
        assertEquals(userId, message.mentions().getFirst().member().userId());
        assertEquals(6, message.mentions().getFirst().start());
        assertEquals(11, message.mentions().getFirst().end());
        content.addProperty("formatted_body", "<a href=\"https://matrix.to.evil/#/" + userId + "\">Alice</a>");
        assertTrue(message(content).mentions().isEmpty());
    }

    @Test
    void truncationDoesNotAuthenticatePartialMentionsOrSplitUnicode() {
        JsonObject content = html("m.text", "fallback", "a".repeat(3_995)
                + " <a href=\"https://matrix.to/#/@bob:example.org\">@Bob Long Name</a>");
        mentions(content, "@bob:example.org");
        MatrixRoomMessage message = message(content);
        assertTrue(message.body().endsWith("…"));
        assertTrue(message.mentions().isEmpty());
        message = message(content("m.text", "a".repeat(3_999) + "😀more"));
        assertTrue(message.body().length() <= 4_000);
        assertEquals("a".repeat(3_999) + "…", message.body());
        assertEquals("", message.commandBody());
        message = message(html("m.text", "fallback", "a".repeat(3_998)
                + "<img data-mx-emoticon src=\"mxc://example.org/a\">"));
        assertEquals("a".repeat(3_998) + "…", message.body());
        content = html("m.text", "fallback", "<a href=\"https://matrix.to/#/@bob:example.org\">"
                + " ".repeat(7_998) + "@Bob" + "</a>");
        mentions(content, "@bob:example.org");
        assertTrue(message(content).mentions().isEmpty());
    }

    @Test
    void doesNotUseAnAmbiguousDisplayNameAsAnIdentity() {
        MatrixMemberDirectory members = new MatrixMemberDirectory();
        members.replaceJoined("!room:example.org", Map.of(
                "@first:example.org", "Alex", "@second:example.org", "Alex"));
        JsonObject content = content("m.text", "hello @Alex");
        mentions(content, "@first:example.org", "@second:example.org");
        assertTrue(process(new MatrixSyncProcessor(members), "m.room.message", content)
                .getFirst().mentions().isEmpty());
    }

    @Test
    void excludesEditsRedactedAndNonMessageEventsFromCommandAndChatAdmission() {
        JsonObject content = content("m.text", "!绑定 edited");
        content.add("m.relates_to", json("{\"rel_type\":\"m.replace\",\"event_id\":\"$old\"}"));
        assertTrue(process(new MatrixSyncProcessor(), "m.room.message", content).isEmpty());
        assertTrue(process(new MatrixSyncProcessor(), "m.room.message", new JsonObject()).isEmpty());
        assertTrue(process(new MatrixSyncProcessor(), "m.room.encrypted", content).isEmpty());
        assertTrue(process(new MatrixSyncProcessor(), "m.reaction", content).isEmpty());
    }

    private static MatrixRoomMessage message(JsonObject content) {
        return process(new MatrixSyncProcessor(), "m.room.message", content).getFirst();
    }

    private static List<MatrixRoomMessage> process(MatrixSyncProcessor processor,
                                                   String eventType, JsonObject content) {
        JsonObject batch = json("""
                {"next_batch":"cursor","rooms":{"join":{"!room:example.org":{
                  "timeline":{"events":[{"type":"m.room.message","event_id":"$event",
                    "sender":"@alice:example.org"}]}
                }}}}
                """);
        JsonObject event = batch.getAsJsonObject("rooms").getAsJsonObject("join")
                .getAsJsonObject("!room:example.org").getAsJsonObject("timeline")
                .getAsJsonArray("events").get(0).getAsJsonObject();
        event.addProperty("type", eventType);
        event.add("content", content);
        return processor.process(MatrixSyncProtocol.parse(batch), false);
    }

    private static JsonObject content(String type, String body) {
        JsonObject content = new JsonObject();
        content.addProperty("msgtype", type);
        content.addProperty("body", body);
        return content;
    }

    private static JsonObject html(String type, String body, String html) {
        JsonObject content = content(type, body);
        content.addProperty("format", "org.matrix.custom.html");
        content.addProperty("formatted_body", html);
        return content;
    }

    private static void mentions(JsonObject content, String... users) {
        JsonArray ids = new JsonArray();
        for (String user : users) {
            ids.add(user);
        }
        JsonObject mentions = new JsonObject();
        mentions.add("user_ids", ids);
        content.add("m.mentions", mentions);
    }

    private static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }
}
