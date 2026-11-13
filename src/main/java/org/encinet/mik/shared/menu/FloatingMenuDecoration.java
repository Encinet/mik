package org.encinet.mik.shared.menu;

import org.encinet.mik.shared.menu.ui.UiColor;
import org.encinet.mik.shared.menu.ui.UiColorRole;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * A non-interactive scene node rendered alongside menu controls.
 *
 * <p>Decorations deliberately have no trigger, focus, or hitbox. They are
 * intended for contextual information, section furniture, and ambient item or
 * block displays without pretending those objects are menu buttons.</p>
 */
public record FloatingMenuDecoration(
        String id,
        FloatingMenuPlacement placement,
        Content content,
        Motion motion,
        Transition transition
) {
    public FloatingMenuDecoration {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Decoration id must not be blank");
        }
        placement = Objects.requireNonNull(placement, "placement");
        content = Objects.requireNonNull(content, "content");
        motion = Objects.requireNonNull(motion, "motion");
        transition = Objects.requireNonNull(transition, "transition");
    }

    public FloatingMenuDecoration(String id, FloatingMenuPlacement placement,
                                  Content content, Motion motion) {
        this(id, placement, content, motion, Transition.SMOOTH);
    }

    public FloatingMenuDecoration(String id, FloatingMenuPose pose,
                                  Content content, Motion motion) {
        this(id, FloatingMenuPlacement.local(pose), content, motion, Transition.SMOOTH);
    }

    public FloatingMenuDecoration(String id, FloatingMenuPoint point,
                                  Content content, Motion motion) {
        this(id, FloatingMenuPlacement.local(point), content, motion, Transition.SMOOTH);
    }

    public static FloatingMenuDecoration text(String id, FloatingMenuPoint point,
                                               Component text) {
        return text(id, FloatingMenuPose.at(point), text, UiColor.themed(UiColorRole.WINDOW), 3.2F, 2.4F, 0.72F,
                Alignment.CENTER);
    }

    public static FloatingMenuDecoration text(String id, FloatingMenuPose pose,
                                               Component text) {
        return text(id, pose, text, UiColor.themed(UiColorRole.WINDOW), 3.2F, 2.4F, 0.72F,
                Alignment.CENTER);
    }

    public static FloatingMenuDecoration text(String id, FloatingMenuPoint point,
                                               Component text, int background,
                                               float displayWidth, float displayHeight) {
        return text(id, FloatingMenuPose.at(point), text,
                background, displayWidth, displayHeight, 0.72F,
                Alignment.CENTER);
    }

    public static FloatingMenuDecoration text(String id, FloatingMenuPoint point,
                                               Component text, int background,
                                               float displayWidth, float displayHeight,
                                               float scale) {
        return text(id, FloatingMenuPose.at(point), text,
                background, displayWidth, displayHeight, scale,
                Alignment.CENTER);
    }

    public static FloatingMenuDecoration text(String id, FloatingMenuPoint point,
                                               Component text, int background,
                                               float displayWidth, float displayHeight,
                                               float scale, Alignment alignment) {
        return text(id, FloatingMenuPose.at(point), text, background,
                displayWidth, displayHeight, scale, alignment);
    }

    public static FloatingMenuDecoration text(String id, FloatingMenuPose pose,
                                               Component text, int background,
                                               float displayWidth, float displayHeight,
                                               float scale, Alignment alignment) {
        return text(id, pose, text, UiColor.fromArgb(background),
                displayWidth, displayHeight, scale, alignment);
    }

    private static FloatingMenuDecoration text(String id, FloatingMenuPose pose,
                                                Component text, UiColor background,
                                                float displayWidth, float displayHeight,
                                                float scale, Alignment alignment) {
        return new FloatingMenuDecoration(id, FloatingMenuPlacement.local(pose),
                new Text(Objects.requireNonNull(text, "text"), background,
                        displayWidth, displayHeight, scale,
                        Objects.requireNonNull(alignment, "alignment"), true), Motion.NONE);
    }

    public static FloatingMenuDecoration item(String id, FloatingMenuPoint point,
                                               ItemStack item, float scale, Motion motion) {
        return item(id, FloatingMenuPose.at(point), item, scale, motion);
    }

    public static FloatingMenuDecoration item(String id, FloatingMenuPose pose,
                                               ItemStack item, float scale, Motion motion) {
        return new FloatingMenuDecoration(id, FloatingMenuPlacement.local(pose),
                new Visual(Objects.requireNonNull(item, "item"), false, scale), motion);
    }

    public static FloatingMenuDecoration block(String id, FloatingMenuPoint point,
                                                ItemStack item, float scale, Motion motion) {
        return block(id, FloatingMenuPose.at(point), item, scale, motion);
    }

    public static FloatingMenuDecoration block(String id, FloatingMenuPose pose,
                                                ItemStack item, float scale, Motion motion) {
        return new FloatingMenuDecoration(id, FloatingMenuPlacement.local(pose),
                new Visual(Objects.requireNonNull(item, "item"), true, scale), motion);
    }

    public static FloatingMenuDecoration worldText(String id, Location location,
                                                    double yawDegrees, double pitchDegrees,
                                                    Component text, int background,
                                                    float displayWidth, float displayHeight,
                                                    float scale, Alignment alignment) {
        return worldText(id, location, yawDegrees, pitchDegrees, text, background,
                displayWidth, displayHeight, scale, alignment, true);
    }

    public static FloatingMenuDecoration worldText(String id, Location location,
                                                    double yawDegrees, double pitchDegrees,
                                                    Component text, int background,
                                                    float displayWidth, float displayHeight,
                                                    float scale, Alignment alignment,
                                                    boolean seeThrough) {
        return new FloatingMenuDecoration(id,
                FloatingMenuPlacement.world(location, yawDegrees, pitchDegrees),
                new Text(Objects.requireNonNull(text, "text"), background,
                        displayWidth, displayHeight, scale,
                        Objects.requireNonNull(alignment, "alignment"), seeThrough), Motion.NONE);
    }

    public static FloatingMenuDecoration worldItem(String id, Location location,
                                                    double yawDegrees, double pitchDegrees,
                                                    ItemStack item, float scale, Motion motion) {
        return new FloatingMenuDecoration(id,
                FloatingMenuPlacement.world(location, yawDegrees, pitchDegrees),
                new Visual(Objects.requireNonNull(item, "item"), false, scale), motion);
    }

    public static FloatingMenuDecoration worldBlock(String id, Location location,
                                                     double yawDegrees, double pitchDegrees,
                                                     ItemStack item, float scale, Motion motion) {
        return new FloatingMenuDecoration(id,
                FloatingMenuPlacement.world(location, yawDegrees, pitchDegrees),
                new Visual(Objects.requireNonNull(item, "item"), true, scale), motion);
    }

    public boolean worldAnchored() {
        return placement instanceof FloatingMenuPlacement.World;
    }

    /** Makes changing local coordinates track their definition without smoothing lag. */
    public FloatingMenuDecoration tracking() {
        return transition == Transition.TRACKING ? this
                : new FloatingMenuDecoration(id, placement, content, motion,
                        Transition.TRACKING);
    }

    /** World labels turn on the client without waiting for a server-side scene refresh. */
    public FloatingMenuDecoration billboard() {
        if (!worldAnchored() || !(content instanceof Text text))
            throw new IllegalStateException("Billboards require world text");
        return new FloatingMenuDecoration(id,placement,new Text(text.text(),text.backgroundColor(),text.displayWidth(),
                text.displayHeight(),text.scale(),text.alignment(),text.seeThrough(),true),motion,transition);
    }

    /** A client-only glowing copy inside an existing block; dimensions are centered on the world position. */
    public static FloatingMenuDecoration worldOutline(String id, Location center, org.bukkit.Material material,
                                                       float size, int color) {
        return new FloatingMenuDecoration(id,FloatingMenuPlacement.world(center,0,0),
                new BlockOutline(material,size,color),Motion.NONE).tracking();
    }

    public static FloatingMenuDecoration volume(String id, FloatingMenuPose pose, BlockData block,
                                                 float width, float height, float depth) {
        return new FloatingMenuDecoration(id, FloatingMenuPlacement.local(pose),
                new BlockVolume(block, width, height, depth), Motion.NONE).tracking();
    }

    public sealed interface Content permits Text, Visual, BlockVolume, BlockOutline, Surface, Image, RangePart, IconPart {
    }

    /** Stable local paint identity; the owning control supplies transforms, state and input. */
    public record IconPart(String source, org.encinet.mik.shared.menu.ui.UiIcon icon, int part, Layer layer,
                           double size, UiColor tint, double alpha) implements Content {
        public enum Layer { FEATHER, CORE }
        public IconPart {
            Objects.requireNonNull(source, "source"); Objects.requireNonNull(icon, "icon"); Objects.requireNonNull(tint, "tint");
            Objects.requireNonNull(layer, "layer");
            if (part < 0 || part >= icon.partCount() || !Double.isFinite(size) || size <= 0
                    || !Double.isFinite(alpha) || alpha < 0 || alpha > 1)
                throw new IllegalArgumentException("Invalid icon paint part");
        }
    }

    /** Parts share one semantic range target and are updated through the ordinary menu lifecycle. */
    public record RangePart(String source, org.encinet.mik.shared.menu.ui.UiRange range,
                            org.encinet.mik.shared.menu.ui.UiRange.Geometry geometry, Part part, double alpha) implements Content {
        public enum Part { TRACK, FILL, THUMB, PREVIEW, VALUE, MAXIMUM }
        public RangePart {
            Objects.requireNonNull(source, "source"); Objects.requireNonNull(range, "range");
            Objects.requireNonNull(geometry, "geometry"); Objects.requireNonNull(part, "part");
            if (!Double.isFinite(alpha) || alpha < 0 || alpha > 1) throw new IllegalArgumentException("Invalid range opacity");
        }
    }

    /** Proportional image quad. The runtime sends its texture through Axiom, with text as a client fallback. */
    public record Image(org.encinet.mik.shared.menu.image.UiImage source, Component alternative,
                        float width, float height, float opacity) implements Content {
        public Image {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(alternative, "alternative");
            if (!positiveFinite(width) || !positiveFinite(height) || !Float.isFinite(opacity) || opacity < 0 || opacity > 1)
                throw new IllegalArgumentException("Invalid image bounds or opacity");
        }
    }

    public record Surface(float width, float height, int background) implements Content {
        public Surface {
            if (!positiveFinite(width) || !positiveFinite(height)) throw new IllegalArgumentException("Invalid surface size");
        }
    }

    public record BlockVolume(BlockData block, float width, float height, float depth) implements Content {
        public BlockVolume {
            block = Objects.requireNonNull(block, "block").clone();
            if (!positiveFinite(width) || !positiveFinite(height) || !positiveFinite(depth))
                throw new IllegalArgumentException("Volume bounds must be positive and finite");
        }

        @Override public BlockData block() { return block.clone(); }
    }

    public record BlockOutline(org.bukkit.Material material, float size, int color) implements Content {
        public BlockOutline {
            Objects.requireNonNull(material);
            // Resolve block data in the renderer, keeping scene construction independent of native registries.
            if (!positiveFinite(size) || color < 0 || color > 0xFFFFFF)
                throw new IllegalArgumentException("Invalid block outline");
        }
    }

    public record Text(Component text, UiColor backgroundColor, float displayWidth,
                       float displayHeight, float scale, Alignment alignment,
                       boolean seeThrough, boolean billboard) implements Content {
        public Text(Component text, UiColor backgroundColor, float displayWidth, float displayHeight,
                    float scale, Alignment alignment, boolean seeThrough) {
            this(text,backgroundColor,displayWidth,displayHeight,scale,alignment,seeThrough,false);
        }
        public Text(Component text, int background, float displayWidth, float displayHeight,
                    float scale, Alignment alignment, boolean seeThrough) {
            this(text, UiColor.fromArgb(background),
                    displayWidth, displayHeight, scale, alignment, seeThrough);
        }
        public Text {
            Objects.requireNonNull(backgroundColor, "backgroundColor");
            text = Objects.requireNonNull(text, "text");
            alignment = Objects.requireNonNull(alignment, "alignment");
            if (!positiveFinite(displayWidth) || !positiveFinite(displayHeight)
                    || !positiveFinite(scale)) {
                throw new IllegalArgumentException("Decoration display bounds must be positive");
            }
        }
    }

    public record Visual(ItemStack item, boolean block, float scale) implements Content {
        public Visual {
            item = Objects.requireNonNull(item, "item").clone();
            if (block && !item.getType().isBlock()) {
                throw new IllegalArgumentException("Block decoration requires a block item");
            }
            if (!positiveFinite(scale)) {
                throw new IllegalArgumentException("Decoration scale must be positive");
            }
        }

        @Override
        public ItemStack item() {
            return item.clone();
        }
    }

    public enum Motion {
        NONE(false, false),
        BOB(true, false),
        SPIN(false, true),
        BOB_AND_SPIN(true, true);

        private final boolean bob;
        private final boolean spin;

        Motion(boolean bob, boolean spin) {
            this.bob = bob;
            this.spin = spin;
        }

        public boolean bobs() {
            return bob;
        }

        public boolean spins() {
            return spin;
        }
    }

    public enum Alignment {
        LEFT,
        CENTER,
        RIGHT
    }

    public enum Transition {
        /** Soft reconciliation for ordinary UI layout changes. */
        SMOOTH,

        /** Exact per-tick tracking for time-critical animated content. */
        TRACKING
    }

    private static boolean positiveFinite(float value) {
        return Float.isFinite(value) && value > 0.0F;
    }
}
