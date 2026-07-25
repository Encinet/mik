package org.encinet.mik.module.music.disc;

import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.AudioProperties;
import org.encinet.mik.module.music.catalog.TrackDetails;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class MusicDiscResolverTest {

    private static final MusicDiscSigner SIGNER = new MusicDiscSigner(new byte[32]);

    @Test
    void returnsCatalogTrackWhenTheDiscHasNoValidSnapshot() {
        MusicTrack catalogTrack = localTrack("local:song");
        MusicDiscResolver resolver = new MusicDiscResolver(
                Map.of(catalogTrack.id(), catalogTrack)::get, SIGNER);

        MusicTrack resolved = resolver.resolve(catalogTrack.id(), "not-json", "invalid");

        assertSame(catalogTrack, resolved);
    }

    @Test
    void onlineDiscSnapshotOverridesAConflictingCatalogTarget() {
        MusicTrack catalogTrack = onlineTrack("lx:kw:7");
        MusicTrack discTrack = new MusicTrack(catalogTrack.id(),
                new TrackDetails("Search result", null, null, "LX/KW", AudioProperties.EMPTY),
                new TrackTarget.Lx("kw", "7", List.of("320k"),
                        "{\"name\":\"Search result\",\"source\":\"kw\",\"songmid\":\"7\"}",
                        "search-provider.js"));
        MusicDiscResolver resolver = new MusicDiscResolver(
                Map.of(catalogTrack.id(), catalogTrack)::get, SIGNER);

        String snapshot = MusicDiscSnapshot.serialize(discTrack);

        MusicTrack resolved = resolver.resolve(
                discTrack.id(), snapshot, SIGNER.sign(snapshot));

        assertEquals("search-provider.js",
                ((TrackTarget.Lx) resolved.target()).providerId());
    }

    @Test
    void restoresOnlineTrackSnapshotWithoutGlobalRegistration() {
        MusicTrack onlineTrack = onlineTrack("lx:kw:7");
        MusicDiscResolver resolver = new MusicDiscResolver(
                ignored -> null, SIGNER);

        String snapshot = MusicDiscSnapshot.serialize(onlineTrack);

        MusicTrack resolved = resolver.resolve(
                onlineTrack.id(), snapshot, SIGNER.sign(snapshot));

        assertEquals(onlineTrack.id(), resolved.id());
    }

    @Test
    void rejectsMissingOrMismatchedDiscData() {
        MusicTrack onlineTrack = onlineTrack("lx:kw:7");
        MusicDiscResolver resolver = new MusicDiscResolver(
                ignored -> null, SIGNER);

        String snapshot = MusicDiscSnapshot.serialize(onlineTrack);

        assertNull(resolver.resolve(null, snapshot, SIGNER.sign(snapshot)));
        assertNull(resolver.resolve("lx:kw:other", snapshot, SIGNER.sign(snapshot)));
        assertNull(resolver.resolve(onlineTrack.id(), "not-json", "invalid"));
        assertNull(resolver.resolve(onlineTrack.id(), snapshot, null));
        assertNull(resolver.resolve(onlineTrack.id(), snapshot + " ", SIGNER.sign(snapshot)));
    }

    private static MusicTrack localTrack(String id) {
        return new MusicTrack(id,
                new TrackDetails("Local", null, null, "MP3", AudioProperties.EMPTY),
                new TrackTarget.LocalFile(Path.of("song.mp3")));
    }

    private static MusicTrack onlineTrack(String id) {
        return new MusicTrack(id,
                new TrackDetails("Online", null, null, "LX/KW", AudioProperties.EMPTY),
                new TrackTarget.Lx("kw", "7", List.of("320k"),
                        "{\"name\":\"Online\",\"source\":\"kw\",\"songmid\":\"7\"}"));
    }
}
