package org.encinet.mik.feature.entertainment.music.disc;

import org.bukkit.inventory.ItemStack;
import org.encinet.mik.feature.entertainment.music.catalog.MusicLibrary;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.catalog.TrackTarget;
import org.encinet.mik.feature.entertainment.music.catalog.TrackDetails;
import org.encinet.mik.feature.entertainment.music.catalog.AudioProperties;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.time.Duration;

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
        if (disc == null || disc.getType().isAir()) return null;
        if (!MusicDiscKeys.isCustomDisc(disc)) {
            var playable = disc.getData(DataComponentTypes.JUKEBOX_PLAYABLE);
            if (playable == null) return null;
            var song = playable.jukeboxSong();
            var sound = org.bukkit.Registry.SOUNDS.getKey(song.getSound());
            if (sound == null) return null;
            return new MusicTrack("record:"+song.key().asString(),
                    new TrackDetails(song.key().value().replace('_',' '),null,null,"record",
                            new AudioProperties(null,null,Duration.ofMillis(Math.round(song.getLengthInSeconds()*1000D)))),
                    new TrackTarget.NativeRecord(sound.asString()));
        }
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
