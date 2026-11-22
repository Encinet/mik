package org.encinet.mik.feature.entertainment.music.disc;

import org.bukkit.inventory.ItemStack;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.catalog.TrackTarget;
import org.encinet.mik.feature.entertainment.music.catalog.TrackDetails;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.time.Duration;

import java.util.Objects;
import java.util.function.Function;

/** Resolves signed custom discs and native jukebox items. */
public final class MusicDiscResolver {

    private final Function<String, MusicTrack> knownTracks;
    private final MusicDiscSigner signer;

    public MusicDiscResolver(Function<String, MusicTrack> knownTracks, MusicDiscSigner signer) {
        this.knownTracks = Objects.requireNonNull(knownTracks, "knownTracks");
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
                    new TrackDetails(song.key().value().replace('_',' '),null,null, Duration.ofMillis(Math.round(song.getLengthInSeconds()*1000D))),
                    new TrackTarget.NativeRecord(sound.asString()));
        }
        return resolve(MusicDiscKeys.trackId(disc), MusicDiscKeys.trackSignature(disc));
    }

    MusicTrack resolve(String trackId, String signature) {
        if (trackId == null) return null;
        final TrackTarget.Managed target;
        try { target = new TrackTarget.Managed(trackId); }
        catch (IllegalArgumentException invalid) { return null; }
        if (!signer.verify(MusicDiscKeys.signedIdentity(trackId), signature)) return null;
        var known = knownTracks.apply(trackId);
        return known != null && target.equals(known.target()) ? known : MusicTrack.reference(target);
    }
}
