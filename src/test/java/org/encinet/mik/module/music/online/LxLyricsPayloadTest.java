package org.encinet.mik.module.music.online;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LxLyricsPayloadTest {

    @Test
    void acceptsPlainStringAndCommonObjectNames() {
        assertEquals("[00:01]Line", LxSourceService.lyricsPayload(
                JsonParser.parseString("\"[00:01]Line\"")).original());

        LxSourceService.LyricsPayload payload = LxSourceService.lyricsPayload(
                JsonParser.parseString("""
                        {"lyric":"original","tlyric":{"lyric":"translated"},
                         "rlyric":"romanized"}
                        """));
        assertEquals("original", payload.original());
        assertEquals("translated", payload.translation());
        assertEquals("romanized", payload.romanization());
    }

    @Test
    void unwrapsNestedDataAndAlternativeNames() {
        LxSourceService.LyricsPayload payload = LxSourceService.lyricsPayload(
                JsonParser.parseString("""
                        {"data":{"lrc":"original","translation":"translated",
                         "roma":{"content":"romanized"}}}
                        """));

        assertEquals("original", payload.original());
        assertEquals("translated", payload.translation());
        assertEquals("romanized", payload.romanization());
        assertTrue(LxSourceService.lyricsPayload(JsonParser.parseString("{}"))
                .isEmpty());
    }
}
