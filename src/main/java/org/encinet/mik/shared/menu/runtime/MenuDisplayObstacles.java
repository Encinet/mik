package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.BoundingBox;
import org.encinet.mik.shared.menu.runtime.space.MenuModelGeometry;
import org.encinet.mik.shared.menu.runtime.space.MenuOcclusionGeometry.Box;
import org.encinet.mik.shared.menu.ui.UiTextMetrics;
import org.joml.Matrix4d;
import org.joml.Vector3d;

/** Display metadata adapter; model transforms and occlusion tests stay in the geometry layer. */
final class MenuDisplayObstacles {
    private MenuDisplayObstacles() { }

    static boolean hasContent(Display display) {
        if (display instanceof BlockDisplay block) return switch (block.getBlock().getMaterial()) {
            case AIR, CAVE_AIR, VOID_AIR -> false;
            default -> true;
        };
        if (display instanceof ItemDisplay item) return !item.getItemStack().isEmpty();
        return display instanceof TextDisplay;
    }

    static Box box(Location eye, Display display) {
        return box(eye, display, Double.POSITIVE_INFINITY);
    }

    static Box box(Location eye, Display display, double maximumDistance) {
        BoundingBox bounds = switch (display) {
            case BlockDisplay _ -> new BoundingBox(0, 0, 0, 1, 1, 1);
            case ItemDisplay _ -> new BoundingBox(-.5, -.5, -.5, .5, .5, .5);
            case TextDisplay text -> textBounds(text);
            default -> null;
        };
        if (bounds == null) return null;
        Location location = display.getLocation();
        var transformation = display.getTransformation();
        var offset = location.toVector().subtract(eye.toVector());
        // DisplayRenderer uses camera rotation for the unlocked billboard axes, before the local transform.
        double yaw = switch (display.getBillboard()) {
            case FIXED, HORIZONTAL -> location.getYaw();
            case VERTICAL, CENTER -> eye.getYaw() - 180;
        };
        double pitch = switch (display.getBillboard()) {
            case FIXED, VERTICAL -> location.getPitch();
            case HORIZONTAL, CENTER -> -eye.getPitch();
        };
        var transform = new Matrix4d().translate(offset.getX(), offset.getY(), offset.getZ())
                .rotateY(-Math.toRadians(yaw)).rotateX(Math.toRadians(pitch))
                .translate(transformation.getTranslation()).rotate(transformation.getLeftRotation())
                .scale(new Vector3d(transformation.getScale())).rotate(transformation.getRightRotation());
        return MenuModelGeometry.transform(bounds, transform, maximumDistance);
    }

    private static BoundingBox textBounds(TextDisplay text) {
        var background = text.getBackgroundColor();
        boolean hasBackground = text.isDefaultBackground() || background != null && background.getAlpha() > 0;
        if (!hasBackground && blank(text.text())) return null;
        // The client discards glyph fragments below 0.1 alpha; the background has its own opacity.
        if (Byte.toUnsignedInt(text.getTextOpacity()) < 26 && !hasBackground) return null;
        var extent = UiTextMetrics.measure(text.text(), Math.max(1, text.getLineWidth()));
        // TextDisplayRenderer: 0.025 world units per pixel, ten-pixel lines, bottom anchored.
        // Reserve the background's extra pixel and a shadow pixel on each side.
        double padding = text.isShadowed() ? 2 : 1;
        double halfWidth = (extent.widthPixels() / 2.0 + padding) * .025;
        double height = (extent.lines() * 10.0 + padding) * .025;
        return new BoundingBox(-halfWidth - .025, -.025 * padding, -.001,
                halfWidth, height, .001);
    }

    private static boolean blank(Component content) {
        return content instanceof TextComponent text && text.content().isBlank()
                && text.children().stream().allMatch(MenuDisplayObstacles::blank);
    }
}
