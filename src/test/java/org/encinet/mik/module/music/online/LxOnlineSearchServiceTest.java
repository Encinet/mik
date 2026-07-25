package org.encinet.mik.module.music.online;

import com.google.gson.JsonParser;
import org.encinet.mik.module.music.online.LxMusicTrackMapper;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LxOnlineSearchServiceTest {

    @Test
    void normalizesAllSupportedLxPlatformSearchResponses() throws Exception {
        Map<String, String> responses = Map.of(
                "search.kuwo.cn", """
                        {"TOTAL":"1","abslist":[{"MUSICRID":"MUSIC_1","SONGNAME":"Kuwo Song",
                        "ARTIST":"Kuwo Artist","ALBUM":"Kuwo Album","DURATION":"61",
                        "N_MINFO":"level:p,bitrate:320,format:mp3,size:3Mb"}]}""",
                "songsearch.kugou.com", """
                        {"data":{"total":1,"lists":[{"Audioid":"2","FileHash":"kg-hash",
                        "SongName":"Kugou Song","SingerName":"Kugou Artist","AlbumName":"Kugou Album",
                        "Duration":62,"FileSize":100,"HQFileSize":200,"HQFileHash":"kg-hq"}]}}""",
                "c.y.qq.com", """
                        {"data":{"song":{"totalnum":1,"list":[{"mid":"tx-mid","title":"Tencent Song",
                        "interval":63,"singer":[{"name":"Tencent Artist"}],
                        "album":{"mid":"tx-album","name":"Tencent Album"},
                        "file":{"media_mid":"tx-media","size_320mp3":300}}]}}}""",
                "music.163.com", """
                        {"result":{"songCount":1,"songs":[{"id":4,"name":"Netease Song","duration":64000,
                        "artists":[{"name":"Netease Artist"}],
                        "album":{"id":5,"name":"Netease Album"}}]}}""",
                "jadeite.migu.cn", """
                        {"songResultData":{"totalCount":1,"resultList":[[{"songId":"6",
                        "copyrightId":"mg-copyright","name":"Migu Song","duration":65,
                        "singerList":[{"name":"Migu Artist"}],"album":"Migu Album",
                        "audioFormats":[{"formatType":"HQ","asize":400}]}]]}}"""
        );
        LxOnlineSearchService service = new LxOnlineSearchService(Duration.ofSeconds(1), request -> {
            String response = responses.get(request.uri().getHost());
            assertTrue(response != null, () -> "Unexpected host: " + request.uri());
            return JsonParser.parseString(response);
        });
        LxMusicTrackMapper mapper = new LxMusicTrackMapper();

        try {
            assertTrack(mapper, service, "kw", "lx:kw:1", "Kuwo Song", "01:01");
            assertTrack(mapper, service, "kg", "lx:kg:kg-hash", "Kugou Song", "01:02");
            assertTrack(mapper, service, "tx", "lx:tx:tx-mid", "Tencent Song", "01:03");
            assertTrack(mapper, service, "wy", "lx:wy:4", "Netease Song", "01:04");
            assertTrack(mapper, service, "mg", "lx:mg:6", "Migu Song", "01:05");
        } finally {
            service.close();
        }
    }

    private static void assertTrack(LxMusicTrackMapper mapper, LxOnlineSearchService service,
                                    String source, String expectedId, String expectedName,
                                    String expectedDuration) throws Exception {
        LxOnlineSearchService.SearchResult result = service.search(source, "keyword", 1, 30)
                .get(2, TimeUnit.SECONDS);

        assertEquals(1, result.total());
        assertEquals(1, result.tracks().size());
        MusicTrack track = mapper.parse(result.tracks().get(0), source, "test-source");
        assertEquals(expectedId, track.id());
        assertEquals(expectedName, track.details().title());
        assertEquals(sourceName(source), track.details().artist());
        assertEquals(parseDuration(expectedDuration), track.details().audio().duration());
        assertEquals("test-source", ((TrackTarget.Lx) track.target()).providerId());
    }

    private static Duration parseDuration(String value) {
        String[] parts = value.split(":");
        return Duration.ofSeconds(Long.parseLong(parts[0]) * 60 + Long.parseLong(parts[1]));
    }

    private static String sourceName(String source) {
        return switch (source) {
            case "kw" -> "Kuwo Artist";
            case "kg" -> "Kugou Artist";
            case "tx" -> "Tencent Artist";
            case "wy" -> "Netease Artist";
            case "mg" -> "Migu Artist";
            default -> throw new IllegalArgumentException(source);
        };
    }
}
