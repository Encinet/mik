package org.encinet.mik.feature.entertainment.music.ui;

import java.net.URI;
import java.util.Locale;
import org.encinet.mik.feature.entertainment.music.application.MusicProblem;

/** Detects supported playlist hosts without opening arbitrary URLs or guessing a source from an ID. */
final class MusicPlaylistLink {
    static String source(String value) {
        String host;
        try {
            URI uri = URI.create(value.strip());
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) || uri.getUserInfo() != null)
                throw new IllegalArgumentException();
            host = uri.getHost().toLowerCase(Locale.ROOT);
        } catch (RuntimeException error) { throw new MusicProblem(MusicProblem.Code.INVALID); }
        if (matches(host, "163.com")) return "wy";
        if (matches(host, "qq.com")) return "tx";
        if (matches(host, "kugou.com")) return "kg";
        if (matches(host, "kuwo.cn")) return "kw";
        if (matches(host, "migu.cn") || matches(host, "migu.com")) return "mg";
        throw new MusicProblem(MusicProblem.Code.UNSUPPORTED);
    }
    private static boolean matches(String host, String domain) { return host.equals(domain) || host.endsWith("." + domain); }
    private MusicPlaylistLink() { }
}
