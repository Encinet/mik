package org.encinet.mik.module.music.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.menu.runtime.WorldTextDisplayService;
import org.encinet.mik.module.music.catalog.AudioPropertiesFormatter;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.disc.MusicDiscResolver;
import org.encinet.mik.module.music.jukebox.JukeboxPlaybackService;
import org.encinet.mik.module.music.jukebox.JukeboxPlaybackSnapshot;
import org.encinet.mik.module.music.jukebox.JukeboxExperienceMode;
import org.encinet.mik.module.music.jukebox.JukeboxSettingsStore;
import org.encinet.mik.module.music.jukebox.JukeboxRhythmReadiness;
import org.encinet.mik.module.music.jukebox.PlaybackStatus;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A short, private label on the face of the nearest active jukebox. */
public final class JukeboxAmbientStatusDisplay {
    private static final String LABEL_KEY = "jukebox-playback";
    private static final int RADIUS = JukeboxAccess.CONTROL_DISTANCE_BLOCKS;
    private static final int RADIUS_SQUARED = RADIUS * RADIUS;
    // Reserve 14 font pixels inside the renderer's line width for glyph width
    // differences, the text background and its shadow.
    private static final int CONTENT_WIDTH_PIXELS = WorldTextDisplayService.LINE_WIDTH_PIXELS - 14;
    private static final int PROGRESS_SEGMENTS = 9;
    private static final int PULSE_SEGMENTS = 3;
    private static final int TITLE_SCROLL_SECONDS = 5;
    private static final int METADATA_SWAP_SECONDS = 8;
    private static final int SCROLL_PAUSE_SECONDS = 2;
    private static final TextColor METADATA_COLOR = TextColor.color(0xB8C4CC);
    private static final TextColor PROGRESS_TRACK_COLOR = TextColor.color(0x80919B);
    // The jukebox face is 0.5 blocks from its center. Keep the virtual plane
    // just outside it so the label reads as attached without z-fighting.
    private static final double LABEL_FACE_OFFSET = 0.51;
    private static final double LABEL_HALF_TEXT_WIDTH = WorldTextDisplayService.PANEL_WIDTH / 2.0;
    private static final double LABEL_LINE_HEIGHT = WorldTextDisplayService.LINE_HEIGHT;
    private static final double LABEL_VERTICAL_PADDING = 0.02;
    private static final double LABEL_MAX_HEIGHT = LABEL_VERTICAL_PADDING + 7 * LABEL_LINE_HEIGHT;
    private static final Pattern GRAPHEME = Pattern.compile("\\X");

    private final JavaPlugin plugin;
    private final LanguageService language;
    private final MusicDiscResolver discs;
    private final JukeboxPlaybackService playback;
    private final JukeboxSettingsStore settings;
    private final WorldTextDisplayService labels;
    private BukkitTask task;

    public JukeboxAmbientStatusDisplay(JavaPlugin plugin, LanguageService language,
                                       MusicDiscResolver discs, JukeboxPlaybackService playback,
                                       JukeboxSettingsStore settings,
                                       WorldTextDisplayService labels) {
        this.plugin = plugin;
        this.language = language;
        this.discs = discs;
        this.playback = playback;
        this.settings = java.util.Objects.requireNonNull(settings, "settings");
        this.labels = java.util.Objects.requireNonNull(labels, "labels");
    }

    public void enable() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void disable() {
        if (task != null) task.cancel();
        for (Player player : Bukkit.getOnlinePlayers()) labels.clear(player, LABEL_KEY);
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            VisibleJukebox visible = nearestVisible(player);
            if (visible == null) {
                labels.clear(player, LABEL_KEY);
                continue;
            }
            labels.show(player, LABEL_KEY, visible.label().location(),
                    visible.label().face().yaw(), visible.presentation().text(),
                    visible.presentation().background());
        }
    }

    private VisibleJukebox nearestVisible(Player player) {
        Location at = player.getLocation();
        Location eye = player.getEyeLocation();
        World world = player.getWorld();
        int minX = (at.getBlockX() - RADIUS) >> 4;
        int maxX = (at.getBlockX() + RADIUS) >> 4;
        int minZ = (at.getBlockZ() - RADIUS) >> 4;
        int maxZ = (at.getBlockZ() + RADIUS) >> 4;
        List<NearbyJukebox> candidates = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!world.isChunkLoaded(x, z)) continue;
                for (org.bukkit.block.BlockState state : world.getChunkAt(x, z).getTileEntities(false)) {
                    if (!(state instanceof Jukebox box) || !box.hasRecord()) continue;
                    PlaybackStatus status = playback.status(box.getBlock());
                    if (status == PlaybackStatus.STOPPED && !box.isPlaying()) continue;
                    double current = box.getLocation().add(0.5, 0.5, 0.5).distanceSquared(at);
                    if (current <= RADIUS_SQUARED) {
                        candidates.add(new NearbyJukebox(box, current));
                    }
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(NearbyJukebox::distanceSquared));
        for (NearbyJukebox candidate : candidates) {
            Location center = candidate.jukebox().getLocation().add(0.5, 0.0, 0.5);
            JukeboxPlaybackSnapshot state = playback.snapshot(candidate.jukebox().getBlock());
            Presentation presentation = presentation(player, candidate.jukebox(), state);
            Label label = visibleLabel(center, eye, textHeight(presentation.text()));
            if (label != null) return new VisibleJukebox(label, presentation);
        }
        return null;
    }

    static Label visibleLabel(Location center, Location eye) {
        return visibleLabel(center, eye, LABEL_MAX_HEIGHT);
    }

    static Label visibleLabel(Location center, Location eye, double textHeight) {
        if (center.getWorld() == null || !center.getWorld().equals(eye.getWorld())) return null;
        if (!Double.isFinite(textHeight) || textHeight <= 0.0
                || textHeight > LABEL_MAX_HEIGHT) return null;
        Face primary = faceToward(center, eye);
        Label label = labelOnFace(center, primary, textHeight);
        if (facesViewer(center, eye, primary) && unobstructed(eye, label)) return label;

        double dx = eye.getX() - center.getX();
        double dz = eye.getZ() - center.getZ();
        Face secondary = Math.abs(dx) >= Math.abs(dz)
                ? (dz >= 0 ? new Face(0, 1, 0.0F) : new Face(0, -1, 180.0F))
                : (dx >= 0 ? new Face(1, 0, -90.0F) : new Face(-1, 0, 90.0F));
        label = labelOnFace(center, secondary, textHeight);
        return facesViewer(center, eye, secondary) && unobstructed(eye, label)
                ? label : null;
    }

    private static Label labelOnFace(Location center, Face face, double textHeight) {
        // Client text is bottom-anchored, so center the actual number of lines
        // on the block face and trace the same rectangle for obstructions.
        double bottom = (1.0 - textHeight) * 0.5;
        return new Label(face, center.clone().add(face.dx() * LABEL_FACE_OFFSET,
                bottom, face.dz() * LABEL_FACE_OFFSET), textHeight);
    }

    private static boolean facesViewer(Location center, Location eye, Face face) {
        double offset = (eye.getX() - center.getX()) * face.dx()
                + (eye.getZ() - center.getZ()) * face.dz();
        return offset > LABEL_FACE_OFFSET;
    }

    private static boolean unobstructed(Location eye, Label label) {
        Location anchor = label.location();
        double sidewaysX = label.face().dz() * LABEL_HALF_TEXT_WIDTH;
        double sidewaysZ = label.face().dx() * LABEL_HALF_TEXT_WIDTH;
        if (!clearLine(eye, anchor.clone().add(0, label.height() * 0.5, 0))) {
            return false;
        }
        for (int side : new int[]{-1, 1}) {
            for (double height : new double[]{0.0, label.height()}) {
                if (!clearLine(eye, anchor.clone().add(
                        side * sidewaysX, height, side * sidewaysZ))) return false;
            }
        }
        return true;
    }

    private static boolean clearLine(Location eye, Location target) {
        World world = eye.getWorld();
        if (!chunksLoaded(world, eye, target)) return false;
        Vector direction = target.toVector().subtract(eye.toVector());
        double distance = direction.length();
        return distance < 1.0E-6 || world.rayTraceBlocks(eye,
                direction.multiply(1.0 / distance), distance,
                FluidCollisionMode.NEVER, true) == null;
    }

    private static boolean chunksLoaded(World world, Location eye, Location target) {
        int minX = Math.min(eye.getBlockX(), target.getBlockX()) >> 4;
        int maxX = Math.max(eye.getBlockX(), target.getBlockX()) >> 4;
        int minZ = Math.min(eye.getBlockZ(), target.getBlockZ()) >> 4;
        int maxZ = Math.max(eye.getBlockZ(), target.getBlockZ()) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!world.isChunkLoaded(x, z)) return false;
            }
        }
        return true;
    }

    private Presentation presentation(Player player, Jukebox jukebox,
                                      JukeboxPlaybackSnapshot state) {
        MusicTrack track = discs.resolve(jukebox.getRecord());
        String title = track == null
                ? PlainTextComponentSerializer.plainText().serialize(jukebox.getRecord().effectiveName())
                : track.details().title();
        JukeboxExperienceMode mode = playback.activeExperienceMode(jukebox.getBlock())
                .orElseGet(() -> settings.readExperienceMode(jukebox));
        JukeboxRhythmReadiness readiness = mode == JukeboxExperienceMode.RHYTHM
                ? playback.rhythmReadiness(jukebox.getBlock())
                : JukeboxRhythmReadiness.UNAVAILABLE;
        boolean waiting = readiness == JukeboxRhythmReadiness.WAITING_FOR_PLAYER;
        Message status = waiting ? Message.MUSIC_RHYTHM_WAITING_FOR_PLAYER
                : readiness == JukeboxRhythmReadiness.NO_BEATS
                ? Message.MUSIC_RHYTHM_NO_BEATS
                : state.status() == PlaybackStatus.LOADING
                ? Message.MUSIC_LOADING_TITLE : Message.MUSIC_PLAYING;
        TextColor accent = MusicMenuPalette.accent(mode);
        Duration duration = track == null ? null : track.details().audio().duration();
        String modeLabel = language.t(player, mode == JukeboxExperienceMode.RHYTHM
                ? Message.MUSIC_JUKEBOX_RHYTHM_MODE : Message.MUSIC_JUKEBOX_MUSIC_MODE);
        String stateLine = (mode == JukeboxExperienceMode.RHYTHM ? "✦ " : "♪ ")
                + modeLabel + " · " + language.t(player, status);
        List<String> metadata = new ArrayList<>(3);
        if (track != null) {
            if (track.details().artist() != null)
                metadata.add(language.t(player, Message.MUSIC_ARTIST,
                        track.details().artist()));
            if (track.details().originalAuthor() != null
                    && !track.details().originalAuthor().equals(track.details().artist()))
                metadata.add(language.t(player, Message.MUSIC_ORIGINAL_AUTHOR,
                        track.details().originalAuthor()));
            if (track.details().album() != null)
                metadata.add(language.t(player, Message.MUSIC_ALBUM, track.details().album()));
        }
        Component card = playbackCard(title, stateLine, metadata, state, duration,
                track != null, waiting || readiness == JukeboxRhythmReadiness.NO_BEATS,
                readiness == JukeboxRhythmReadiness.NO_BEATS ? NamedTextColor.RED : accent,
                accent, System.currentTimeMillis() / 1_000L);
        return new Presentation(card, MusicMenuPalette.ambientBackground(mode));
    }

    static Component playbackCard(String title, String stateLine, List<String> metadata,
                                  JukeboxPlaybackSnapshot state, Duration duration,
                                  boolean hasTrackClock, boolean emptyProgress,
                                  TextColor stateColor, TextColor accent,
                                  long animationSecond) {
        Component card = Component.text(titleWindow(title, animationSecond), NamedTextColor.WHITE);
        List<String> visibleMetadata = metadata.size() <= 2 ? metadata : List.of(
                metadata.get(0), metadata.get(1 + (int) Math.floorMod(
                        animationSecond / METADATA_SWAP_SECONDS, metadata.size() - 1)));
        for (String line : visibleMetadata) {
            card = card.append(Component.newline())
                    .append(Component.text(scrollLine(line, animationSecond,
                            CONTENT_WIDTH_PIXELS), METADATA_COLOR));
        }
        card = card.append(Component.newline())
                .append(Component.text(scrollLine(stateLine, animationSecond,
                        CONTENT_WIDTH_PIXELS), stateColor));
        if (hasTrackClock) {
            card = card.append(Component.newline())
                    .append(Component.text("▶ ", accent))
                    .append(Component.text(scrollLine(
                            playbackTimeLabel(state.positionMillis(), duration), animationSecond,
                            CONTENT_WIDTH_PIXELS - glyphWidth("▶ ")), NamedTextColor.WHITE));
        }
        return card.append(Component.newline())
                .append(emptyProgress
                        ? Component.text("▱".repeat(PROGRESS_SEGMENTS), PROGRESS_TRACK_COLOR)
                        : progressBar(state.status(), state.positionMillis(), duration,
                                animationSecond, accent));
    }

    static String playbackTimeLabel(long positionMillis, Duration duration) {
        String total = AudioPropertiesFormatter.duration(duration);
        long position = Math.max(0L, positionMillis);
        if (total != null) {
            long durationMillis;
            try { durationMillis = duration.toMillis(); }
            catch (ArithmeticException ignored) { durationMillis = Long.MAX_VALUE; }
            position = Math.min(position, durationMillis);
        }
        String elapsed = JukeboxControlGui.playbackTime(position);
        return total == null ? elapsed : elapsed + " / " + total;
    }

    static double textHeight(Component content) {
        String plain = PlainTextComponentSerializer.plainText().serialize(content);
        int lines = plain.split("\\n", -1).length;
        return LABEL_VERTICAL_PADDING + lines * LABEL_LINE_HEIGHT;
    }

    static String titleWindow(String raw, long animationSecond) {
        String title = normalizeTitle(raw);
        if (title.isEmpty()) return "♪";
        List<String> graphemes = graphemes(title);
        List<String> lines = new ArrayList<>();
        int start = 0;
        while (start < graphemes.size()) {
            int end = start;
            int width = 0;
            int lastSpace = -1;
            while (end < graphemes.size()) {
                int glyphWidth = glyphWidth(graphemes.get(end));
                if (end > start && width + glyphWidth > CONTENT_WIDTH_PIXELS) break;
                width += glyphWidth;
                if (graphemes.get(end).equals(" ")) lastSpace = end;
                end++;
            }
            if (end < graphemes.size() && lastSpace > start) end = lastSpace;
            lines.add(String.join("", graphemes.subList(start, end)).strip());
            start = end;
            while (start < graphemes.size() && graphemes.get(start).equals(" ")) start++;
        }
        if (lines.size() <= 2) return String.join("\n", lines);
        int first = (int) Math.floorMod(animationSecond / TITLE_SCROLL_SECONDS,
                lines.size() - 1);
        return lines.get(first) + "\n" + lines.get(first + 1);
    }

    static String scrollLine(String raw, long animationSecond, int maxWidth) {
        String line = normalizeTitle(raw);
        if (glyphWidth(line) <= maxWidth) return line;
        List<String> graphemes = graphemes(line);
        int remaining = 0;
        for (String grapheme : graphemes) remaining += glyphWidth(grapheme);
        int lastStart = 0;
        while (lastStart < graphemes.size() - 1 && remaining > maxWidth) {
            remaining -= glyphWidth(graphemes.get(lastStart++));
        }
        long cycle = lastStart + 2L * SCROLL_PAUSE_SECONDS;
        long frame = Math.floorMod(animationSecond, cycle);
        int start = (int) Math.clamp(frame - SCROLL_PAUSE_SECONDS, 0L, lastStart);
        StringBuilder visible = new StringBuilder();
        int width = 0;
        for (int index = start; index < graphemes.size(); index++) {
            String glyph = graphemes.get(index);
            int nextWidth = glyphWidth(glyph);
            if (width + nextWidth > maxWidth) break;
            visible.append(glyph);
            width += nextWidth;
        }
        return visible.toString().strip();
    }

    private static List<String> graphemes(String text) {
        List<String> result = new ArrayList<>();
        Matcher matcher = GRAPHEME.matcher(text);
        while (matcher.find()) {
            String grapheme = matcher.group();
            // A single compound glyph can be wider than the entire side panel.
            result.add(glyphWidth(grapheme) > CONTENT_WIDTH_PIXELS ? "…" : grapheme);
        }
        return result;
    }

    private static String normalizeTitle(String raw) {
        StringBuilder normalized = new StringBuilder();
        boolean pendingSpace = false;
        for (int offset = 0; offset < raw.length();) {
            int codePoint = raw.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)
                    || Character.isISOControl(codePoint)) {
                pendingSpace = normalized.length() > 0;
                continue;
            }
            if (pendingSpace) normalized.append(' ');
            normalized.appendCodePoint(codePoint);
            pendingSpace = false;
        }
        return normalized.toString();
    }

    static int glyphWidth(String grapheme) {
        int width = 0;
        for (int offset = 0; offset < grapheme.length();) {
            int codePoint = grapheme.codePointAt(offset);
            offset += Character.charCount(codePoint);
            int type = Character.getType(codePoint);
            if (type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK || type == Character.FORMAT) continue;
            if (codePoint == ' ') width += 4;
            else if (codePoint < 128 && "il.,:;!|'`".indexOf(codePoint) >= 0) width += 2;
            else if (codePoint < 128 && "[](){}tfrI".indexOf(codePoint) >= 0) width += 4;
            else if (codePoint < 128 && "mwMW@#%&".indexOf(codePoint) >= 0) width += 7;
            else width += codePoint < 128 ? 6 : 9;
        }
        return width;
    }

    static Component progressBar(PlaybackStatus status, long positionMillis,
                                 Duration duration, long animationSecond) {
        return progressBar(status, positionMillis, duration, animationSecond,
                MusicMenuPalette.MUSIC);
    }

    static Component progressBar(PlaybackStatus status, long positionMillis,
                                 Duration duration, long animationSecond,
                                 TextColor accent) {
        int completed;
        if (status == PlaybackStatus.PLAYING && duration != null
                && !duration.isZero() && !duration.isNegative()) {
            long durationMillis;
            try {
                durationMillis = duration.toMillis();
            } catch (ArithmeticException exception) {
                durationMillis = Long.MAX_VALUE;
            }
            completed = (int) Math.floor(Math.clamp(
                    positionMillis / (double) Math.max(1L, durationMillis), 0.0, 1.0)
                    * PROGRESS_SEGMENTS);
            return Component.text("▰".repeat(completed), accent)
                    .append(Component.text("▱".repeat(PROGRESS_SEGMENTS - completed),
                            PROGRESS_TRACK_COLOR));
        }

        int pulseStart = Math.floorMod(animationSecond, PROGRESS_SEGMENTS);
        Component bar = Component.empty();
        for (int segment = 0; segment < PROGRESS_SEGMENTS; segment++) {
            boolean lit = Math.floorMod(segment - pulseStart, PROGRESS_SEGMENTS)
                    < PULSE_SEGMENTS;
            bar = bar.append(Component.text(lit ? "▰" : "▱",
                    lit ? accent : PROGRESS_TRACK_COLOR));
        }
        return bar;
    }

    static Face faceToward(Location center, Location player) {
        double dx = player.getX() - center.getX();
        double dz = player.getZ() - center.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? new Face(1, 0, -90.0F) : new Face(-1, 0, 90.0F);
        }
        return dz >= 0 ? new Face(0, 1, 0.0F) : new Face(0, -1, 180.0F);
    }

    record Face(int dx, int dz, float yaw) { }

    record Label(Face face, Location location, double height) { }

    private record NearbyJukebox(Jukebox jukebox, double distanceSquared) { }

    private record Presentation(Component text, int background) { }

    private record VisibleJukebox(Label label, Presentation presentation) { }
}
