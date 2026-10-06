package org.encinet.mik.module.music.rhythm;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.Optional;

/** Owns the all-or-nothing player data representation of rhythm calibration. */
final class RhythmCalibrationStore {
    private static final RhythmCalibrationProfiles UNCALIBRATED =
            new RhythmCalibrationProfiles(new RhythmLatencyProfile(0, 0),
                    new RhythmLatencyProfile(0, 0));

    private final NamespacedKey minecraftJudgementOffsetKey;
    private final NamespacedKey minecraftAnimationOffsetKey;
    private final NamespacedKey plasmoJudgementOffsetKey;
    private final NamespacedKey plasmoAnimationOffsetKey;
    private final NamespacedKey pointerInputDeltaKey;

    RhythmCalibrationStore(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        minecraftJudgementOffsetKey = new NamespacedKey(plugin,
                "rhythm_minecraft_judgement_ms");
        minecraftAnimationOffsetKey = new NamespacedKey(plugin,
                "rhythm_minecraft_animation_ms");
        plasmoJudgementOffsetKey = new NamespacedKey(plugin,
                "rhythm_plasmo_judgement_ms");
        plasmoAnimationOffsetKey = new NamespacedKey(plugin,
                "rhythm_plasmo_animation_ms");
        pointerInputDeltaKey = new NamespacedKey(plugin,
                "rhythm_pointer_input_delta_ms");
    }

    Optional<RhythmCalibrationResult> read(Player player) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        return RhythmCalibrationResult.fromStored(
                data.get(minecraftJudgementOffsetKey, PersistentDataType.INTEGER),
                data.get(minecraftAnimationOffsetKey, PersistentDataType.INTEGER),
                data.get(plasmoJudgementOffsetKey, PersistentDataType.INTEGER),
                data.get(plasmoAnimationOffsetKey, PersistentDataType.INTEGER),
                data.get(pointerInputDeltaKey, PersistentDataType.INTEGER));
    }

    RhythmCalibrationProfiles profiles(Player player) {
        return read(player).map(RhythmCalibrationResult::profiles)
                .orElse(UNCALIBRATED);
    }

    int pointerInputDelta(Player player) {
        return read(player).map(RhythmCalibrationResult::pointerInputDeltaMillis)
                .orElse(0);
    }

    void save(Player player, RhythmCalibrationResult result) {
        Objects.requireNonNull(result, "result");
        PersistentDataContainer data = player.getPersistentDataContainer();
        saveProfile(data, minecraftJudgementOffsetKey,
                minecraftAnimationOffsetKey, result.profiles().minecraft());
        saveProfile(data, plasmoJudgementOffsetKey,
                plasmoAnimationOffsetKey, result.profiles().plasmoVoice());
        data.set(pointerInputDeltaKey, PersistentDataType.INTEGER,
                result.pointerInputDeltaMillis());
    }

    void clear(Player player) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        data.remove(minecraftJudgementOffsetKey);
        data.remove(minecraftAnimationOffsetKey);
        data.remove(plasmoJudgementOffsetKey);
        data.remove(plasmoAnimationOffsetKey);
        data.remove(pointerInputDeltaKey);
    }

    private static void saveProfile(PersistentDataContainer data,
                                    NamespacedKey judgementKey, NamespacedKey animationKey,
                                    RhythmLatencyProfile profile) {
        data.set(judgementKey, PersistentDataType.INTEGER,
                profile.judgementOffsetMillis());
        data.set(animationKey, PersistentDataType.INTEGER,
                profile.animationOffsetMillis());
    }
}
