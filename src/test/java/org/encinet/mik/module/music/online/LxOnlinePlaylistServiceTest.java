package org.encinet.mik.module.music.online;

import com.google.gson.JsonParser;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LxOnlinePlaylistServiceTest {

    @Test
    void importsAndNormalizesAllSupportedPlatformPlaylists() throws Exception {
        LxOnlineSearchService service = new LxOnlineSearchService(Duration.ofSeconds(1), request -> {
            String host = request.uri().getHost();
            String response = switch (host) {
                case "nplserver.kuwo.cn" -> """
                        {"result":"ok","title":"Kuwo List","total":1,"musiclist":[
                        {"id":"1","name":"Kuwo Song","artist":"Kuwo Artist","album":"Album",
                        "duration":"61","N_MINFO":"level:p,bitrate:320,format:mp3,size:3Mb"}]}
                        """;
                case "c.y.qq.com" -> """
                        {"code":0,"subcode":0,"cdlist":[{"dissname":"Tencent List","songnum":1,
                        "songlist":[{"songmid":"tx-mid","songid":2,"songname":"Tencent Song",
                        "interval":62,"singer":[{"name":"Tencent Artist"}],"albumname":"Album",
                        "albummid":"album-mid","strMediaMid":"media-mid","size320":200}]}]}
                        """;
                case "music.163.com" -> """
                        {"code":200,"result":{"name":"NetEase List","trackCount":1,"tracks":[
                        {"id":3,"name":"NetEase Song","duration":63000,
                        "artists":[{"name":"NetEase Artist"}],"album":{"id":4,"name":"Album"},
                        "hMusic":{"size":300}}]}}
                        """;
                case "app.c.nf.migu.cn" -> """
                        {"code":"000000","data":{"totalCount":1,"songList":[
                        {"songId":"5","copyrightId":"copyright","songName":"Migu Song",
                        "duration":64,"singerList":[{"name":"Migu Artist"}],"album":"Album",
                        "audioFormats":[{"formatType":"HQ","asize":400}]}]}}
                        """;
                case "c.musicapp.migu.cn" -> """
                        {"code":"000000","data":{"title":"Migu List"}}
                        """;
                default -> throw new AssertionError("Unexpected request: " + request.uri());
            };
            return JsonParser.parseString(response);
        }, request -> """
                <script>
                var data=[{"audio_id":6,"songname":"Kugou Song","singername":"Kugou Artist",
                "album_name":"Album","duration":65000,"hash":"kg-hash","filesize":100,
                "hash_320":"kg-hq","filesize_320":500}];
                var specialInfo={"name":"Kugou List"};
                </script>
                """);

        try {
            assertPlaylist(service, "kw", "1", "Kuwo List", "lx:kw:1", "Kuwo Song");
            assertPlaylist(service, "kg", "2", "Kugou List", "lx:kg:kg-hash", "Kugou Song");
            assertPlaylist(service, "tx", "3", "Tencent List", "lx:tx:tx-mid", "Tencent Song");
            assertPlaylist(service, "wy", "4", "NetEase List", "lx:wy:3", "NetEase Song");
            assertPlaylist(service, "mg", "5", "Migu List", "lx:mg:5", "Migu Song");
        } finally {
            service.close();
        }
    }

    @Test
    void extractsIdsOnlyFromMatchingPlatformUrls() {
        assertEquals("11", LxOnlineSearchService.parsePlaylistId(
                "kw", "https://www.kuwo.cn/playlist_detail/11?from=share"));
        assertEquals("12", LxOnlineSearchService.parsePlaylistId(
                "kg", "https://www.kugou.com/yy/special/single/12.html"));
        assertEquals("13", LxOnlineSearchService.parsePlaylistId(
                "tx", "https://y.qq.com/n/ryqq/playlist/13"));
        assertEquals("14", LxOnlineSearchService.parsePlaylistId(
                "wy", "https://music.163.com/#/playlist?id=14"));
        assertEquals("15", LxOnlineSearchService.parsePlaylistId(
                "mg", "https://music.migu.cn/v5/#/playlist?playlistId=15"));
        assertThrows(IllegalArgumentException.class, () ->
                LxOnlineSearchService.parsePlaylistId(
                        "wy", "https://evil.example/?id=14"));
        assertThrows(IllegalArgumentException.class, () ->
                LxOnlineSearchService.parsePlaylistId(
                        "tx", "https://qq.com.evil.example/playlist/13"));
    }

    private static void assertPlaylist(LxOnlineSearchService service, String source,
                                       String reference, String expectedName,
                                       String expectedTrackId, String expectedTitle) throws Exception {
        LxOnlineSearchService.PlaylistResult result = service.playlist(source, reference)
                .get(2, TimeUnit.SECONDS);
        assertEquals(expectedName, result.name());
        assertEquals(1, result.tracks().size());
        MusicTrack track = new LxMusicTrackMapper().parse(
                result.tracks().get(0), source, "provider");
        assertEquals(expectedTrackId, track.id());
        assertEquals(expectedTitle, track.details().title());
        assertEquals("provider", ((TrackTarget.Lx) track.target()).providerId());
    }
}
