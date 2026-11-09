package org.encinet.mik.integration.axiom;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.net.URI;
import java.util.Objects;

/** Immutable snapshot of an Axiom image annotation in world coordinates. */
public record AxiomImage(URI url, Vector3fc position, Quaternionfc rotation, float width, float opacity) {
    public AxiomImage {
        Objects.requireNonNull(url, "url");
        if (!("https".equalsIgnoreCase(url.getScheme()) || "http".equalsIgnoreCase(url.getScheme()))
                || url.getHost() == null || url.toASCIIString().length() > 8192)
            throw new IllegalArgumentException("Invalid Axiom image URL");
        if (!Objects.requireNonNull(position, "position").isFinite()
                || !Objects.requireNonNull(rotation, "rotation").isFinite()
                || !Float.isFinite(rotation.lengthSquared()) || rotation.lengthSquared() < 1e-12f
                || !Float.isFinite(width) || width <= 0
                || !Float.isFinite(opacity) || opacity < 0 || opacity > 1)
            throw new IllegalArgumentException("Invalid Axiom image geometry or opacity");
        position = new Vector3f(position);
        var normalized = new Quaternionf(rotation).normalize();
        if (normalized.w < 0) normalized.set(-normalized.x, -normalized.y, -normalized.z, -normalized.w);
        rotation = normalized;
    }

    @Override public Vector3fc position() { return new Vector3f(position); }
    @Override public Quaternionfc rotation() { return new Quaternionf(rotation); }

    boolean sameTexture(AxiomImage other) {
        return url.equals(other.url) && width == other.width && opacity == other.opacity;
    }
}
