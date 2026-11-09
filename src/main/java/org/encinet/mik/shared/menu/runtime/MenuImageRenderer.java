package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.encinet.mik.integration.axiom.AxiomImage;
import org.encinet.mik.integration.axiom.AxiomImageService;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.ui.UiRotation;
import org.encinet.mik.shared.menu.ui.UiTextMetrics;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Adapts menu image geometry to annotations and the ordinary-client text alternative. */
final class MenuImageRenderer {
    private final VirtualMenuEntityRenderer entities;
    private final AxiomImageService annotations;

    MenuImageRenderer(VirtualMenuEntityRenderer entities, AxiomImageService annotations) {
        this.entities = entities;
        this.annotations = annotations;
    }

    void fallback(Player player, int entityId, FloatingMenuDecoration.Image image,
                  double coordinateScale, double typographyScale, UiRotation rotation) {
        var fallback = fallback(image, typographyScale);
        entities.text(player, entityId, annotations.supported(player) ? Component.empty() : fallback.label(), 0,
                image.width(), image.height(), fallback.layout().lineWidthPixels(),
                (float) (FloatingMenuNodeSizing.TEXT_SCALE * fallback.scale() * coordinateScale),
                FloatingMenuDecoration.Alignment.CENTER, false, 1, image.opacity(), rotation);
    }

    double fallbackAnchorDown(Player player, FloatingMenuDecoration.Image image, double coordinateScale, double typographyScale) {
        if (annotations.supported(player)) return 0;
        var fallback = fallback(image, typographyScale);
        return FloatingMenuNodeGeometry.textAnchorDown(fallback.layout().renderedHeight(), coordinateScale * fallback.scale());
    }

    static AxiomImage annotation(FloatingMenuDecoration.Image image, Location at, UiRotation rotation, double scale) {
        return new AxiomImage(image.source().url(), new Vector3f((float) at.getX(), (float) at.getY(), (float) at.getZ()),
                new Quaternionf((float) rotation.x(), (float) rotation.y(), (float) rotation.z(), (float) rotation.w()),
                (float) (image.width() * scale), image.opacity());
    }

    private static Fallback fallback(FloatingMenuDecoration.Image image, double typographyScale) {
        double scale = Math.min(typographyScale, Math.min(image.width() / .34, image.height() / .28));
        int pixels = Math.max(1, FloatingMenuNodeSizing.textWidthPixels(image.width(), scale));
        int lines = Math.max(1, FloatingMenuNodeSizing.textLines(image.height(), scale));
        Component label = UiTextMetrics.fitLines(image.alternative(), pixels, lines);
        return new Fallback(label, FloatingMenuNodeSizing.measureText(label, false, pixels), scale);
    }

    private record Fallback(Component label, FloatingMenuNodeSizing.TextLayout layout, double scale) { }
}
