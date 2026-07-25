package org.encinet.mik.module.music.disc;

import org.bukkit.inventory.ItemStack;
import org.encinet.mik.module.music.catalog.MusicLibrary;
import org.encinet.mik.module.music.catalog.MusicTrack;

import java.util.Objects;
import java.util.function.Function;

/** Resolves a disc to a catalog track or its restart-safe online snapshot. */
public final class MusicDiscResolver {

    private final Function<String, MusicTrack> trackLookup;
    private final MusicDiscSigner signer;

    public MusicDiscResolver(MusicLibrary catalog, MusicDiscSigner signer) {
        this(catalog::track, signer);
    }

    MusicDiscResolver(Function<String, MusicTrack> trackLookup, MusicDiscSigner signer) {
        this.trackLookup = Objects.requireNonNull(trackLookup, "trackLookup");
        this.signer = Objects.requireNonNull(signer, "signer");
    }

    public MusicTrack resolve(ItemStack disc) {
        return resolve(MusicDiscKeys.trackId(disc), MusicDiscKeys.trackData(disc),
                MusicDiscKeys.trackSignature(disc));
    }

    MusicTrack resolve(String trackId, String trackData, String trackSignature) {
        if (trackId == null) {
            return null;
        }
        MusicTrack restored = signer.verify(trackData, trackSignature)
                ? MusicDiscSnapshot.deserialize(trackId, trackData) : null;
        if (restored != null) {
            return restored;
        }
        MusicTrack catalogTrack = trackLookup.apply(trackId);
        if (catalogTrack != null) {
            return catalogTrack;
        }
        return null;
    }
}
