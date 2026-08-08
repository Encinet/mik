/**
 * Player-facing rhythm gameplay and its shared chart/judgement model.
 *
 * <p>{@link org.encinet.mik.module.music.rhythm.RhythmGameService} is the Bukkit
 * and menu adapter. Mode-specific mutable behavior belongs to focused runtime
 * components under {@code mode.falling}, {@code mode.radial} and
 * {@code mode.spatial}; mouse-driven modes share timestamp capture and ray
 * selection through {@code input}. Source decoding belongs to {@code analysis},
 * calibration audio transport belongs to {@code calibration}, and
 * jukebox/song-clock ownership belongs to {@code playback}.</p>
 */
package org.encinet.mik.module.music.rhythm;
