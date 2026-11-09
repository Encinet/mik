package org.encinet.mik.shared.menu.runtime;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Native audio boundary, separate from host selection and input dispatch. */
final class MenuSoundFeedback {
    void play(Player player, float volume, float pitch) {
        if (volume > 0) player.playSound(player.getLocation(),Sound.UI_BUTTON_CLICK,volume,pitch);
    }
}
