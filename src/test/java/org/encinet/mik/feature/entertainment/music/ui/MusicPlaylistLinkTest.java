package org.encinet.mik.feature.entertainment.music.ui;

import org.encinet.mik.feature.entertainment.music.application.MusicProblem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MusicPlaylistLinkTest {
    @Test void sourceComesFromTheRealHostAndIdsDoNotGuessAPlatform() {
        assertEquals("wy", MusicPlaylistLink.source("https://music.163.com/#/playlist?id=123"));
        assertEquals("tx", MusicPlaylistLink.source("https://y.qq.com/n/ryqq/playlist/123"));
        assertEquals("kg", MusicPlaylistLink.source("https://www.kugou.com/yy/special/single/123.html"));
        assertEquals("kw", MusicPlaylistLink.source("https://www.kuwo.cn/playlist_detail/123"));
        assertEquals("mg", MusicPlaylistLink.source("https://music.migu.cn/v3/music/playlist/123"));
        for (String invalid : new String[] {"123", "https://music.163.com.example.org/playlist/123", "file:///tmp/163.com", "https://163.com@evil.test/123"})
            assertThrows(MusicProblem.class, () -> MusicPlaylistLink.source(invalid));
    }
}
