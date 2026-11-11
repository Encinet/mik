package org.encinet.mik.feature.community.social.platform.matrix.sync;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatrixSyncProcessorTest {

    @Test
    void parsesMembersMentionsRepliesThreadsAndStripsReplyFallback() {
        MatrixSyncProcessor processor = new MatrixSyncProcessor();
        MatrixSyncProtocol.Batch first = MatrixSyncProtocol.parse(JsonParser.parseString("""
                {
                  "next_batch":"first",
                  "rooms":{"join":{"!room:example.org":{
                    "state":{"events":[
                      {"type":"m.room.member","state_key":"@alice:example.org",
                       "content":{"membership":"join","displayname":"Alice"}},
                      {"type":"m.room.member","state_key":"@bob:example.org",
                       "content":{"membership":"join","displayname":"Bob"}}
                    ]},
                    "timeline":{"events":[
                      {"type":"m.room.message","event_id":"$original",
                       "sender":"@bob:example.org",
                       "content":{"msgtype":"m.text","body":"hello"}}
                    ]}
                  }}}
                }
                """).getAsJsonObject());
        processor.process(first, true);

        MatrixSyncProtocol.Batch second = MatrixSyncProtocol.parse(JsonParser.parseString("""
                {
                  "next_batch":"second",
                  "rooms":{"join":{"!room:example.org":{"timeline":{"events":[
                    {"type":"m.room.message","event_id":"$command",
                     "sender":"@alice:example.org","content":{
                       "msgtype":"m.text",
                       "body":"> <@bob:example.org> hello\n> quoted\n\n!profile",
                       "m.mentions":{"user_ids":["@bob:example.org"]},
                       "m.relates_to":{"rel_type":"m.thread","event_id":"$root",
                         "m.in_reply_to":{"event_id":"$original"}}
                     }},
                    {"type":"m.room.message","event_id":"$edit",
                     "sender":"@alice:example.org","content":{
                       "msgtype":"m.text","body":"!status",
                       "m.relates_to":{"rel_type":"m.replace","event_id":"$command"}
                     }},
                    {"type":"m.room.message","event_id":"$image",
                     "sender":"@alice:example.org",
                     "content":{"msgtype":"m.image","body":"image.png"}}
                  ]}}}}
                }
                """).getAsJsonObject());

        List<MatrixRoomMessage> messages = processor.process(second, false);

        assertEquals(2, messages.size());
        assertEquals("[image]", messages.get(1).body());
        assertEquals("", messages.get(1).commandBody());
        MatrixRoomMessage message = messages.getFirst();
        assertEquals("$command", message.eventId());
        assertEquals("!room:example.org", message.roomId());
        assertEquals("@alice:example.org", message.sender().userId());
        assertEquals("Alice", message.sender().displayName());
        assertEquals("!profile", message.body());
        assertEquals(List.of(), message.mentions());
        assertEquals("Bob", message.repliedAuthor().orElseThrow().displayName());
        assertEquals("$root", message.threadRootEventId().orElseThrow());
        assertEquals("second", second.nextBatch());
    }

    @Test
    void malformedSyncStructuresAreRejectedAndEmptyBatchesAreAllowed() {
        MatrixSyncProtocol.Batch batch = MatrixSyncProtocol.parse(
                JsonParser.parseString("{\"next_batch\":\"cursor\",\"rooms\":{}}")
                        .getAsJsonObject());

        assertEquals(List.of(), new MatrixSyncProcessor().process(batch, false));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> MatrixSyncProtocol.parse(JsonParser.parseString("{}")
                        .getAsJsonObject()));
        for (String invalid : List.of(
                "{\"rooms\":[]}",
                "{\"rooms\":{\"join\":null}}",
                "{\"rooms\":{\"join\":{\"!room:example.org\":{\"state_after\":[]}}}}",
                "{\"rooms\":{\"join\":{\"!room:example.org\":{\"state\":{\"events\":{}}}}}}",
                "{\"rooms\":{\"join\":{\"!room:example.org\":{\"state_after\":{\"events\":[null]}}}}}",
                "{\"rooms\":{\"join\":{\"!room:example.org\":{\"state_after\":{\"events\":[{}]}}}}}",
                "{\"rooms\":{\"join\":{\"!room:example.org\":{\"timeline\":{\"events\":[42]}}}}}")) {
            var response = JsonParser.parseString(invalid).getAsJsonObject();
            response.addProperty("next_batch", "next");
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> MatrixSyncProtocol.parse(response));
        }
    }

    @Test
    void customHtmlProvidesExactAuthenticatedMentionRanges() {
        MatrixSyncProtocol.Batch batch = MatrixSyncProtocol.parse(
                JsonParser.parseString("""
                        {
                          "next_batch":"next",
                          "rooms":{"join":{"!room:example.org":{
                            "state":{"events":[
                              {"type":"m.room.member","state_key":"@bob:example.org",
                               "content":{"membership":"join","displayname":"Bob"}}
                            ]},
                            "timeline":{"events":[{
                              "type":"m.room.message","event_id":"$mention",
                              "sender":"@alice:example.org","content":{
                                "msgtype":"m.text",
                                "body":"hello @Bob and @Forged",
                                "format":"org.matrix.custom.html",
                                "formatted_body":"<mx-reply>old reply</mx-reply><p>hello <a href=\\\"https://matrix.to/#/@bob:example.org\\\">@Bob</a> and <a href=\\\"https://matrix.to/#/@forged:example.org\\\">@Forged</a></p>",
                                "m.mentions":{"user_ids":["@bob:example.org"]},
                                "m.relates_to":{"m.in_reply_to":{"event_id":"$old"}}
                              }
                            }]}
                          }}}
                        }
                        """).getAsJsonObject());

        MatrixRoomMessage message = new MatrixSyncProcessor()
                .process(batch, false).getFirst();

        assertEquals("hello @Bob and @Forged", message.body());
        assertEquals(1, message.mentions().size());
        assertEquals("@bob:example.org",
                message.mentions().getFirst().member().userId());
        assertEquals(6, message.mentions().getFirst().start());
        assertEquals(10, message.mentions().getFirst().end());
    }

    @Test
    void memberDirectoryIncludesOnlyJoinedUsers() {
        MatrixMemberDirectory directory = new MatrixMemberDirectory();
        com.google.gson.JsonObject invite = JsonParser.parseString(
                "{\"membership\":\"invite\",\"displayname\":\"Invited\"}")
                .getAsJsonObject();
        com.google.gson.JsonObject join = JsonParser.parseString(
                "{\"membership\":\"join\",\"displayname\":\"Joined\"}")
                .getAsJsonObject();

        directory.applyMember("!room:example.org", new MatrixSyncProtocol.Event(
                "", "m.room.member", "", "@user:example.org", invite));
        assertFalse(directory.isJoined(
                "!room:example.org", "@user:example.org"));
        directory.applyMember("!room:example.org", new MatrixSyncProtocol.Event(
                "", "m.room.member", "", "@user:example.org", join));
        assertTrue(directory.isJoined(
                "!room:example.org", "@user:example.org"));
        for (String invalid : java.util.List.of("{}", "{\"membership\":true}", "{\"membership\":\"unknown\"}")) {
            directory.applyMember("!room:example.org", new MatrixSyncProtocol.Event(
                    "", "m.room.member", "", "@user:example.org", JsonParser.parseString(invalid).getAsJsonObject()));
            assertTrue(directory.isJoined("!room:example.org", "@user:example.org"));
        }
    }

    @Test
    void stateAfterIsAuthoritativeEvenWhenEmptyAndTimelineContainsOtherMembership() {
        var members = new MatrixMemberDirectory();
        members.replaceJoined("!room:example.org", java.util.Map.of("@alice:example.org", "Alice"));
        var processor = new MatrixSyncProcessor(members);
        var response = JsonParser.parseString("""
                {"next_batch":"one","rooms":{"join":{"!room:example.org":{
                  "state_after":{"events":[]},
                  "timeline":{"events":[
                    {"type":"m.room.member","state_key":"@alice:example.org","content":{"membership":"leave"}},
                    {"type":"m.room.message","event_id":"$live","sender":"@alice:example.org",
                     "content":{"msgtype":"m.text","body":"hello"}}
                  ]}
                }}}}
                """).getAsJsonObject();
        assertEquals(1, processor.process(MatrixSyncProtocol.parse(response), false).size());
        assertTrue(members.isJoined("!room:example.org", "@alice:example.org"));
        response.getAsJsonObject("rooms").getAsJsonObject("join").getAsJsonObject("!room:example.org")
                .getAsJsonObject("state_after").add("events", JsonParser.parseString("""
                        [{"type":"m.room.member","state_key":"@alice:example.org","content":{"membership":"leave"}}]
                        """));
        processor.process(MatrixSyncProtocol.parse(response), false);
        assertFalse(members.isJoined("!room:example.org", "@alice:example.org"));
    }
}
