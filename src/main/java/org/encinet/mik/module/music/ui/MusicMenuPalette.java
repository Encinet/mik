package org.encinet.mik.module.music.ui;

import net.kyori.adventure.text.format.TextColor;
import org.encinet.mik.module.menu.FloatingMenuAppearance;
import org.encinet.mik.module.menu.FloatingMenuElementState;
import org.encinet.mik.module.music.jukebox.JukeboxExperienceMode;

/** Shared visual accent for jukebox controls, rhythm screens, and block-face status. */
public final class MusicMenuPalette {
    public static final TextColor MUSIC = FloatingMenuAppearance.MUSIC_ACCENT;
    public static final TextColor RHYTHM = FloatingMenuAppearance.RHYTHM_ACCENT;

    private MusicMenuPalette() { }

    public static TextColor accent(JukeboxExperienceMode mode) {
        return mode == JukeboxExperienceMode.RHYTHM ? RHYTHM : MUSIC;
    }

    public static FloatingMenuAppearance appearance(JukeboxExperienceMode mode) {
        return mode == JukeboxExperienceMode.RHYTHM
                ? FloatingMenuAppearance.RHYTHM : FloatingMenuAppearance.MUSIC;
    }

    /** A softer version of the matching menu surface for a block-face label. */
    public static int ambientBackground(JukeboxExperienceMode mode) {
        return 0xD0000000 | (appearance(mode)
                .elementBackground(FloatingMenuElementState.NORMAL) & 0x00FFFFFF);
    }
}
