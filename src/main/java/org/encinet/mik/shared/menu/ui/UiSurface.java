package org.encinet.mik.shared.menu.ui;

import java.util.List;
import java.util.Objects;

/** A rigid surface whose children share its orientation independently of background opacity. */
public record UiSurface(UiKey key, UiNode content, UiStyle style, UiSizing sizing,
                        UiLength elevation, UiSurfaceFacing facing, boolean stretchContent) implements UiBranch {
    public UiSurface(UiKey key, UiNode content, UiStyle style, UiSizing sizing, UiLength elevation, UiSurfaceFacing facing) {
        this(key, content, style, sizing, elevation, facing, false);
    }
    public UiSurface {
        Objects.requireNonNull(key, "key"); Objects.requireNonNull(content, "content");
        Objects.requireNonNull(style, "style"); Objects.requireNonNull(sizing, "sizing");
        Objects.requireNonNull(elevation, "elevation");
        Objects.requireNonNull(facing, "facing");
        if (elevation.value() > 64) throw new IllegalArgumentException("Surface elevation must not exceed 64 UI units");
    }
    public UiSurface withElevation(UiLength value) { return new UiSurface(key, content, style, sizing, value, facing, stretchContent); }
    public UiSurface withStyle(UiStyle value) { return new UiSurface(key, content, value, sizing, elevation, facing, stretchContent); }
    public UiSurface withSizing(UiSizing value) { return new UiSurface(key, content, style, value, elevation, facing, stretchContent); }
    public UiSurface withFacing(UiSurfaceFacing value) { return new UiSurface(key, content, style, sizing, elevation, value, stretchContent); }
    public UiSurface stretchingContent() { return new UiSurface(key, content, style, sizing, elevation, facing, true); }
    // Keep the reading flow at the top when declared sizing reserves more space than its content.
    @Override public UiLayout layout() { return UiLayouts.column(UiLength.ZERO); }
    @Override public List<UiChild> children() {
        return List.of(UiChild.linear(UiKey.named("content"), content, stretchContent ? LinearSlot.stretch() : LinearSlot.CONTENT));
    }
}
