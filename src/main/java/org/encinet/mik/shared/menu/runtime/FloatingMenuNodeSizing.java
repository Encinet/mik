package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.FloatingMenuAnimation;
import org.encinet.mik.shared.menu.ui.UiRole;
import org.encinet.mik.shared.menu.ui.UiTextMetrics;
import org.encinet.mik.shared.menu.ui.UiTheme;
import org.encinet.mik.shared.menu.ui.UiBaseline;
import org.encinet.mik.shared.menu.ui.UiFontMetrics;
import org.encinet.mik.shared.menu.UiSceneContent;
import org.encinet.mik.shared.menu.FloatingMenuSize;
import org.encinet.mik.shared.menu.FloatingMenuTextWidth;

import net.kyori.adventure.text.Component;

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

    static Measurement measure(MenuScene.Entry entry) {
        return measure(entry, 1.0);
    }

    static Measurement measure(MenuScene.Entry entry,
                               double typographyFactor) {
        return measure(entry, typographyFactor, FloatingMenuAnimation.DEFAULT.idleAmplitude());
    }

    static Measurement measure(MenuScene.Entry entry,
                               double typographyFactor, double idleAmplitude) {
        return measure(entry, typographyFactor, idleAmplitude, 0);
    }

    static Measurement measure(MenuScene.Entry entry,
                               double typographyFactor, double idleAmplitude, int widthPixels) {
        Objects.requireNonNull(entry, "entry");
        if (!Double.isFinite(typographyFactor) || typographyFactor <= 0.0) {
            throw new IllegalArgumentException("Typography factor must be positive and finite");
        }
        TextLayout text = widthPixels > 0
                ? measureText(entry.label(), entry.interactive(), widthPixels)
                : measureText(entry.label(), entry.role(), entry.interactive(), entry.textWidth());
        return new Measurement(text, footprint(entry.style(), text, typographyFactor, idleAmplitude));
    }

    static FloatingMenuSize footprint(org.encinet.mik.shared.menu.FloatingMenuElementStyle style, TextLayout text,
                                      double typographyFactor, double idleAmplitude) {
        FloatingMenuSize renderedText = scaled(text.size(), typographyFactor);
        return FloatingMenuNodeGeometry.footprint(style, renderedText, idleAmplitude);
    }

    private static FloatingMenuSize scaled(FloatingMenuSize size, double factor) {
        return new FloatingMenuSize(size.width() * factor, size.height() * factor);
    }

    static TextLayout measureText(Component component, UiRole role,
                                  boolean interactive) {
        return measureText(component, role, interactive, FloatingMenuTextWidth.AUTO);
    }

    static TextLayout measureText(Component component, UiRole role,
                                  boolean interactive, FloatingMenuTextWidth textWidth) {
        int automaticWidth = switch (Objects.requireNonNull(role, "role")) {
            case TEXT, HEADING, STATUS -> 160;
            case ACTION, CHOICE -> 128;
            case NAVIGATION -> 104;
        };
        int lineWidth = Objects.requireNonNull(textWidth, "textWidth")
                .resolve(automaticWidth);
        return measureText(component, interactive, lineWidth);
    }

    static TextLayout measureText(Component component, boolean interactive, int lineWidth) {
        UiTextMetrics.Extent wrapped = UiTextMetrics.measure(component, lineWidth);
        double width = wrapped.widthPixels() * TEXT_PIXEL_SIZE + HORIZONTAL_PADDING;
        double minimumWidth = interactive ? 0.62 : 0.34;
        double minimumHeight = interactive ? UiTheme.CONTROL_MIN_HEIGHT.value()*UiSceneContent.WORLD_PER_UI : 0.28;
        FloatingMenuSize size = new FloatingMenuSize(Math.max(minimumWidth, width),
                Math.max(minimumHeight, wrapped.lines() * LINE_HEIGHT_PIXELS * TEXT_PIXEL_SIZE + VERTICAL_PADDING));
        return new TextLayout(lineWidth, wrapped.widthPixels(), wrapped.lines(), size);
    }

    static int textWidthPixels(double availableWidth, double textScale) {
        // Match the compiler's UI-space fit tolerance at exact glyph boundaries.
        // A one-ulp contraction must not turn every explicit line into two lines.
        double tolerance = 1e-6 * UiSceneContent.WORLD_PER_UI;
        return (int) Math.floor(((availableWidth + tolerance) / textScale - HORIZONTAL_PADDING) / TEXT_PIXEL_SIZE);
    }

    static UiBaseline baseline(TextLayout text, double textScale, double heightUi) {
        double pixel = TEXT_PIXEL_SIZE * textScale / UiSceneContent.WORLD_PER_UI;
        double top = (heightUi - text.renderedHeight() * textScale / UiSceneContent.WORLD_PER_UI) / 2;
        double first = top + UiFontMetrics.ASCENT_ESTIMATE * pixel;
        return new UiBaseline(first, first + (text.lines() - 1) * LINE_HEIGHT_PIXELS * pixel);
    }

    static int textLines(double availableHeight, double textScale) {
        return (int) Math.floor((availableHeight / textScale - VERTICAL_PADDING) / (LINE_HEIGHT_PIXELS * TEXT_PIXEL_SIZE));
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

        double contentWidth() {
            return renderedWidthPixels * TEXT_PIXEL_SIZE;
        }

        float displayHeight() {
            return (float) Math.max(0.8, size.height() / TEXT_SCALE + 0.25);
        }

        double renderedHeight() {
            // Vanilla positions the block by (lineCount * lineHeight - 1) pixels.
            return (lines * LINE_HEIGHT_PIXELS - 1) * TEXT_PIXEL_SIZE;
        }
    }

}
