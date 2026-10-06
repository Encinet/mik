package org.encinet.mik.module.menu.runtime;

import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuAnimation;
import org.encinet.mik.module.menu.FloatingMenuNodeRole;
import org.encinet.mik.module.menu.FloatingMenuSize;
import org.encinet.mik.module.menu.FloatingMenuTextWidth;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Objects;

/** Shared text wrapping and spatial-footprint model used by layout and rendering. */
final class FloatingMenuNodeSizing {
    static final float TEXT_SCALE = 0.92F;

    private static final double TEXT_PIXEL_SIZE = 0.025 * TEXT_SCALE;
    private static final int LINE_HEIGHT_PIXELS = 10;
    private static final double HORIZONTAL_PADDING = 0.18;
    private static final double VERTICAL_PADDING = 0.10;

    private FloatingMenuNodeSizing() {
    }

    static Measurement measure(FloatingMenuDefinition.Entry entry) {
        return measure(entry, 1.0);
    }

    static Measurement measure(FloatingMenuDefinition.Entry entry,
                               double typographyFactor) {
        return measure(entry, typographyFactor, FloatingMenuAnimation.DEFAULT.idleAmplitude());
    }

    static Measurement measure(FloatingMenuDefinition.Entry entry,
                               double typographyFactor, double idleAmplitude) {
        Objects.requireNonNull(entry, "entry");
        if (!Double.isFinite(typographyFactor) || typographyFactor <= 0.0) {
            throw new IllegalArgumentException("Typography factor must be positive and finite");
        }
        TextLayout text = measureText(entry.label(), entry.role(), entry.interactive(),
                entry.textWidth());
        FloatingMenuSize renderedText = scaled(text.size(), typographyFactor);
        FloatingMenuSize footprint = switch (entry.style()) {
            case TEXT -> renderedText;
            case ITEM -> FloatingMenuNodeGeometry.visualFootprint(renderedText, 0.58);
            case BLOCK -> FloatingMenuNodeGeometry.blockFootprint(renderedText, idleAmplitude);
        };
        return new Measurement(text, footprint);
    }

    private static FloatingMenuSize scaled(FloatingMenuSize size, double factor) {
        return new FloatingMenuSize(size.width() * factor, size.height() * factor);
    }

    static TextLayout measureText(Component component, FloatingMenuNodeRole role,
                                  boolean interactive) {
        return measureText(component, role, interactive, FloatingMenuTextWidth.AUTO);
    }

    static TextLayout measureText(Component component, FloatingMenuNodeRole role,
                                  boolean interactive, FloatingMenuTextWidth textWidth) {
        String plain = PlainTextComponentSerializer.plainText().serialize(
                Objects.requireNonNull(component, "component"));
        int automaticWidth = switch (Objects.requireNonNull(role, "role")) {
            case INFORMATION -> 160;
            case CONTROL -> 128;
            case NAVIGATION -> 104;
            case ITEM, BLOCK -> 112;
        };
        int lineWidth = Objects.requireNonNull(textWidth, "textWidth")
                .resolve(automaticWidth);
        WrappedText wrapped = wrap(plain, lineWidth);
        double width = wrapped.widthPixels() * TEXT_PIXEL_SIZE + HORIZONTAL_PADDING;
        double height = wrapped.lines() * LINE_HEIGHT_PIXELS * TEXT_PIXEL_SIZE
                + VERTICAL_PADDING;
        double minimumWidth = interactive ? 0.62 : 0.34;
        double minimumHeight = interactive ? 0.42 : 0.28;
        FloatingMenuSize size = new FloatingMenuSize(
                Math.max(minimumWidth, width), Math.max(minimumHeight, height));
        return new TextLayout(lineWidth, wrapped.widthPixels(), wrapped.lines(), size);
    }

    private static WrappedText wrap(String text, int lineWidth) {
        int lines = 1;
        int currentWidth = 0;
        int maximumWidth = 0;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '\n') {
                maximumWidth = Math.max(maximumWidth, currentWidth);
                currentWidth = 0;
                lines++;
                continue;
            }
            int glyphWidth = glyphWidth(codePoint);
            if (currentWidth > 0 && currentWidth + glyphWidth > lineWidth) {
                maximumWidth = Math.max(maximumWidth, currentWidth);
                currentWidth = glyphWidth;
                lines++;
            } else {
                currentWidth += glyphWidth;
            }
        }
        maximumWidth = Math.max(maximumWidth, currentWidth);
        return new WrappedText(Math.max(1, maximumWidth), lines);
    }

    private static int glyphWidth(int codePoint) {
        int type = Character.getType(codePoint);
        if (type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK) {
            return 0;
        }
        if (codePoint == ' ') return 4;
        if (codePoint == '\t') return 8;
        if (codePoint < 128) {
            if ("il.,:;!|'`".indexOf(codePoint) >= 0) return 2;
            if ("[](){}tfrI".indexOf(codePoint) >= 0) return 4;
            if ("mwMW@#%&".indexOf(codePoint) >= 0) return 7;
            return 6;
        }
        return 9;
    }

    record Measurement(TextLayout text, FloatingMenuSize footprint) {
        Measurement {
            text = Objects.requireNonNull(text, "text");
            footprint = Objects.requireNonNull(footprint, "footprint");
        }
    }

    record TextLayout(int lineWidthPixels, int renderedWidthPixels,
                      int lines, FloatingMenuSize size) {
        TextLayout {
            if (lineWidthPixels < 1 || renderedWidthPixels < 1 || lines < 1) {
                throw new IllegalArgumentException("Invalid measured text");
            }
            size = Objects.requireNonNull(size, "size");
        }

        float displayWidth() {
            return (float) Math.max(1.0, size.width() / TEXT_SCALE + 0.35);
        }

        float displayHeight() {
            return (float) Math.max(0.8, size.height() / TEXT_SCALE + 0.25);
        }

        double renderedHeight() {
            // Vanilla positions the block by (lineCount * lineHeight - 1) pixels.
            return (lines * LINE_HEIGHT_PIXELS - 1) * TEXT_PIXEL_SIZE;
        }
    }

    private record WrappedText(int widthPixels, int lines) {
    }
}
