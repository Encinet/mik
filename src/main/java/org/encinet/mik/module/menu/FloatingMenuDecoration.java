package org.encinet.mik.module.menu;

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
        return text(id, FloatingMenuPose.at(point), text, 0xB8121720, 3.2F, 2.4F, 0.72F,
                Alignment.CENTER);
    }

    public static FloatingMenuDecoration text(String id, FloatingMenuPose pose,
                                               Component text) {
        return text(id, pose, text, 0xB8121720, 3.2F, 2.4F, 0.72F,
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

    public static FloatingMenuDecoration volume(String id, FloatingMenuPose pose, BlockData block,
                                                 float width, float height, float depth) {
        return new FloatingMenuDecoration(id, FloatingMenuPlacement.local(pose),
                new BlockVolume(block, width, height, depth), Motion.NONE).tracking();
    }

    public sealed interface Content permits Text, Visual, BlockVolume {
    }

    public record BlockVolume(BlockData block, float width, float height, float depth) implements Content {
        public BlockVolume {
            block = Objects.requireNonNull(block, "block").clone();
            if (!positiveFinite(width) || !positiveFinite(height) || !positiveFinite(depth))
                throw new IllegalArgumentException("Volume bounds must be positive and finite");
        }

        @Override public BlockData block() { return block.clone(); }
    }

    public record Text(Component text, int background, float displayWidth,
                       float displayHeight, float scale, Alignment alignment,
                       boolean seeThrough) implements Content {
        public Text {
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
