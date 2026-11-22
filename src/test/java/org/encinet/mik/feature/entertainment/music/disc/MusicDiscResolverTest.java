package org.encinet.mik.feature.entertainment.music.disc;

import org.encinet.mik.feature.entertainment.music.MusicTestTracks;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.catalog.TrackDetails;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MusicDiscResolverTest {
    private static final MusicDiscSigner SIGNER = new MusicDiscSigner(new byte[32]);
    private static final String ID = MusicTestTracks.id("disc");
    private static final String SIGNATURE = SIGNER.sign(MusicDiscKeys.signedIdentity(ID));

    @Test
    void signedIdentitySurvivesMetadataChanges() {
        var original = MusicTestTracks.managed("disc", new TrackDetails("Old title", null, null, null));
        var renamed = new MusicTrack(ID, new TrackDetails("New title", "Artist", null, null), original.target());
        assertSame(original, new MusicDiscResolver(Map.of(ID, original)::get, SIGNER).resolve(ID, SIGNATURE));
        assertSame(renamed, new MusicDiscResolver(Map.of(ID, renamed)::get, SIGNER).resolve(ID, SIGNATURE));
    }

    @Test
    void signedIdentityDoesNotRequireAPaperCatalogRow() {
        var resolver = new MusicDiscResolver(_ -> null, SIGNER);
        assertEquals(MusicTrack.reference(MusicTestTracks.target("disc")), resolver.resolve(ID, SIGNATURE));
    }

    @Test
    void rejectsMismatchedIdsOldSignaturesAndNoncanonicalIds() {
        var resolver = new MusicDiscResolver(_ -> null, SIGNER);
        assertNull(resolver.resolve(null, SIGNATURE));
        assertNull(resolver.resolve(MusicTestTracks.id("other"), SIGNATURE));
        assertNull(resolver.resolve(ID, null));
        assertNull(resolver.resolve(ID, SIGNER.sign("{\"id\":\"" + ID + "\"}")));
        assertNull(resolver.resolve("lx:wy:123", SIGNER.sign(MusicDiscKeys.signedIdentity("lx:wy:123"))));
    }
}
