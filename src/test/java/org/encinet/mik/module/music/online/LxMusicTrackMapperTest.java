package org.encinet.mik.module.music.online;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class LxMusicTrackMapperTest {

    private final LxMusicTrackMapper mapper = new LxMusicTrackMapper();

    @Test
    void preservesCurrentOfficialMusicInfoAndProvidesScriptAliases() {
        MusicTrack track = mapper.parse(JsonParser.parseString("""
                {
                  "id":"tx_42","name":"Current","singer":"Singer","source":"tx",
                  "interval":"03:15","meta":{
                    "songId":"42","albumName":"Album","picUrl":"https://img.example/42.jpg",
                    "albumId":"album-42","qualitys":[{"type":"320k","size":"8M"}],
                    "_qualitys":{"320k":{"size":"8M"}},"strMediaMid":"media-42",
                    "id":4242,"albumMid":"album-mid-42","extensionField":"kept"
                  }
                }
                """));

        TrackTarget.Lx target = assertInstanceOf(TrackTarget.Lx.class,
                track.target());
        JsonObject info = JsonParser.parseString(target.musicInfoJson()).getAsJsonObject();
        JsonObject meta = info.getAsJsonObject("meta");

        assertEquals("tx_42", info.get("id").getAsString());
        assertEquals("42", meta.get("songId").getAsString());
        assertEquals("media-42", meta.get("strMediaMid").getAsString());
        assertEquals(4242, meta.get("id").getAsInt());
        assertEquals("album-mid-42", meta.get("albumMid").getAsString());
        assertEquals("kept", meta.get("extensionField").getAsString());
        assertEquals("42", info.get("songmid").getAsString());
        assertEquals("media-42", info.get("strMediaMid").getAsString());
        assertEquals(4242, info.get("songId").getAsInt());
        assertEquals("320k", info.getAsJsonArray("types").get(0)
                .getAsJsonObject().get("type").getAsString());
    }

    @Test
    void preservesMiguPlaybackFields() {
        MusicTrack track = mapper.parse(JsonParser.parseString("""
                {
                  "id":"mg_88","name":"Migu","singer":"Singer","source":"mg",
                  "meta":{
                    "songId":"88","albumName":"Album","qualitys":[{"type":"flac"}],
                    "_qualitys":{"flac":{"size":"20M"}},"copyrightId":"copyright-88",
                    "lrcUrl":"https://example/lrc","mrcUrl":"https://example/mrc",
                    "trcUrl":"https://example/trc"
                  }
                }
                """));

        TrackTarget.Lx target = assertInstanceOf(TrackTarget.Lx.class,
                track.target());
        JsonObject info = JsonParser.parseString(target.musicInfoJson()).getAsJsonObject();

        assertEquals("copyright-88", info.getAsJsonObject("meta")
                .get("copyrightId").getAsString());
        assertEquals("copyright-88", info.get("copyrightId").getAsString());
        assertEquals("https://example/lrc", info.get("lrcUrl").getAsString());
        assertEquals("https://example/mrc", info.get("mrcUrl").getAsString());
        assertEquals("https://example/trc", info.get("trcUrl").getAsString());
    }

    @Test
    void searchResultCannotOverrideTheRequestedSource() {
        MusicTrack track = mapper.parse(JsonParser.parseString("""
                {
                  "id":"wy_42","name":"Forged Source","source":"wy",
                  "meta":{"songId":" 42 "}
                }
                """), "kw");

        TrackTarget.Lx target = assertInstanceOf(TrackTarget.Lx.class,
                track.target());
        assertEquals("lx:kw:42", track.id());
        assertEquals("kw", target.source());
        assertEquals("42", target.songId());
    }

    @Test
    void readsQualityAliasesFromMetaAndRejectsControlCharactersInIds() {
        MusicTrack track = mapper.parse(JsonParser.parseString("""
                {
                  "id":"kw_7","name":"Aliases","source":"kw",
                  "meta":{"songId":"7","types":[{"type":"320k"}],
                    "_types":{"hires":{"size":"20M"}}}
                }
                """));

        TrackTarget.Lx target = assertInstanceOf(TrackTarget.Lx.class,
                track.target());
        assertEquals(java.util.List.of("320k", "flac24bit"), target.qualities());

        assertNull(mapper.parse(JsonParser.parseString("""
                {"id":"kw_7\\nother","name":"Bad","source":"kw",
                  "meta":{"songId":"7\\nother"}}
                """)));
    }

    @Test
    void canonicalSongIdentityPreventsUntrustedLxIdCollisions() {
        MusicTrack first = mapper.parse(JsonParser.parseString("""
                {"id":"same","name":"First","source":"kw","meta":{"songId":"1"}}
                """));
        MusicTrack second = mapper.parse(JsonParser.parseString("""
                {"id":"same","name":"Second","source":"kw","meta":{"songId":"2"}}
                """));

        assertEquals("lx:kw:1", first.id());
        assertEquals("lx:kw:2", second.id());
    }

    @Test
    void replacesMalformedStructuredMetadataWithValidAliases() {
        MusicTrack track = mapper.parse(JsonParser.parseString("""
                {"id":"kw_9","name":"Malformed metadata","source":"kw",
                 "qualitys":[{"type":"320k"}],"_qualitys":{"320k":{}},
                 "meta":{"songId":"9","qualitys":null,"_qualitys":"bad"}}
                """));

        TrackTarget.Lx target = assertInstanceOf(TrackTarget.Lx.class,
                track.target());
        JsonObject meta = JsonParser.parseString(target.musicInfoJson()).getAsJsonObject()
                .getAsJsonObject("meta");
        assertEquals("320k", meta.getAsJsonArray("qualitys").get(0)
                .getAsJsonObject().get("type").getAsString());
        assertEquals(true, meta.get("_qualitys").isJsonObject());
    }

    @Test
    void normalizesMalformedTypeUrlAndRejectsOversizedKugouFallbackHash() {
        MusicTrack normalized = mapper.parse(JsonParser.parseString("""
                {"id":"kw_9","name":"Type URL","source":"kw","songmid":"9",
                 "typeUrl":"invalid"}
                """));
        JsonObject info = JsonParser.parseString(((TrackTarget.Lx)
                normalized.target()).musicInfoJson()).getAsJsonObject();
        assertEquals(true, info.get("typeUrl").isJsonObject());

        String oversizedHash = "h".repeat(513);
        MusicTrack kugou = mapper.parse(JsonParser.parseString("""
                {"id":"kg_song","name":"Oversized","source":"kg",
                 "meta":{"songId":"song","_qualitys":{"320k":{"hash":"%s"}}}}
                """.formatted(oversizedHash)));
        assertEquals("lx:kg:song", kugou.id());
    }
}
