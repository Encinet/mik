package org.encinet.mik.shared.menu.runtime.render;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Native audio boundary, separate from host selection and input dispatch. */
public final class MenuSoundFeedback {
    public void play(Player player, float volume, float pitch) {
        if (volume > 0) player.playSound(player.getLocation(),Sound.UI_BUTTON_CLICK,volume,pitch);
    }
}
