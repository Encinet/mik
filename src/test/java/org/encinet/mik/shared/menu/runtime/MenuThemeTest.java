package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.image.UiImage;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MenuThemeTest {
    @Test void customSnapshotsKeepAllColorsAndOpacityAndRenderTheirOwnValues() {
        var original = FloatingMenuPreferences.DEFAULT.withTheme(UiPalette.WARM);
        var custom = original.customizeTheme();
        var snapshot = assertInstanceOf(UiCustomTheme.class, custom.theme());
        for (var role : UiColorRole.values()) {
            assertEquals(UiPalette.WARM.rgb(role), snapshot.rgb(role));
            assertEquals(UiPalette.WARM.alpha(role), snapshot.alpha(role));
        }
        var changed = snapshot.withColor(UiColorRole.WINDOW, 0x123456, .37)
                .withColor(UiColorRole.INPUT, 0x234567, .23);
        assertEquals(UiPalette.WARM.rgb(UiColorRole.WINDOW), snapshot.rgb(UiColorRole.WINDOW));
        var prefs = custom.withTheme(changed).withLayout(FloatingMenuScale.LARGE).withReducedMotion(true);
        assertSame(prefs, prefs.customizeTheme());
        assertEquals(UiCustomTheme.copyOf(UiPalette.FOREST), prefs.withTheme(UiPalette.FOREST).customizeTheme().theme());
        assertThrows(UnsupportedOperationException.class, () -> changed.colors().clear());
        var geometry = SceneGeometry.measure(page(), 1, FloatingMenuFieldOfView.DEFAULT, 1, changed);
        assertTrue(surfaces(geometry).contains(changed.argb(UiColorRole.WINDOW)));
        assertTrue(surfaces(geometry).contains(changed.argb(UiColorRole.INPUT)));
        assertEquals(.37, UiTheme.WINDOW.backgroundAlpha(changed));
        assertEquals(0, UiTheme.WINDOW.withBackgroundAlpha(0).backgroundArgb(changed) >>> 24);
        assertEquals(Math.round(.23 * 255), FloatingMenuAppearance.DEFAULT.elementBackground(
                FloatingMenuElementState.HOVERED, changed, UiTheme.INPUT) >>> 24);
    }

    @Test void editorValidatesHexColorsAndOpacityAndSubmitsOnlyTheChosenColor() {
        var custom = UiCustomTheme.copyOf(UiPalette.WARM);
        var result = new java.util.concurrent.atomic.AtomicReference<UiColor.Literal>();
        var form = FloatingMenuThemeEditor.input(custom, UiColorRole.WINDOW, org.encinet.mik.shared.i18n.Message::key,
                (_, color) -> result.set(color));
        var values = new MenuInputForm.Values(Map.of("color", "#123456", "opacity", "37%"));
        assertNull(form.validation().apply(null, values));
        assertNull(result.get());
        form.onSubmit().accept(null, values);
        assertEquals(new UiColor.Literal(0x123456, .37), result.get());
        for (String opacity : List.of("-1", "101", "NaN", "Infinity", "invalid"))
            assertNotNull(form.validation().apply(null, new MenuInputForm.Values(Map.of("color", "#123456", "opacity", opacity))));
        assertNotNull(form.validation().apply(null, new MenuInputForm.Values(Map.of("color", "#12345", "opacity", "37"))));
        assertEquals(1, FloatingMenuThemeEditor.input(custom, UiColorRole.ACCENT, org.encinet.mik.shared.i18n.Message::key,
                (_, _) -> {}).fields().size());
    }

    @Test void sharedTextColorsRemainReadableOnMaterialsCompositedOverAWhiteWorldBackground() {
        var foregrounds = List.of(UiColorRole.TEXT, UiColorRole.ACCENT, UiColorRole.SUCCESS,
                UiColorRole.WARNING, UiColorRole.DANGER, UiColorRole.INFO,
                UiColorRole.MUSIC_ACCENT, UiColorRole.RHYTHM_ACCENT);
        var materials = List.of(UiColorRole.WINDOW, UiColorRole.SIDEBAR, UiColorRole.TOOLBAR,
                UiColorRole.INPUT, UiColorRole.TOOLTIP, UiColorRole.BUTTON, UiColorRole.BUTTON_HOVERED,
                UiColorRole.BUTTON_SELECTED, UiColorRole.BUTTON_PRESSED, UiColorRole.BUTTON_DISABLED);
        for (var theme : UiPalette.values()) for (var material : materials) for (var foreground : foregrounds) {
            int surface = theme.argb(material);
            double alpha = (surface >>> 24) / 255.0;
            double background = luminance(surface, alpha);
            double text = luminance(theme.rgb(foreground), 1);
            double contrast = (text + .05) / (background + .05);
            assertTrue(contrast >= 4.5, theme + " " + foreground + " over " + material + ": " + contrast);
        }
    }

    @Test void themedInputsKeepAllInteractionStatesWhileLiteralAndTransparentOverridesRemainExact() {
        var appearance = FloatingMenuAppearance.DEFAULT;
        var fixed = UiStyle.CONTROL.withBackground(UiColor.literal(0x112233, .5));
        var transparent = UiTheme.INPUT.withBackgroundAlpha(0);
        for (var theme : UiPalette.values()) {
            var colors = new java.util.HashSet<Integer>();
            for (var state : FloatingMenuElementState.values()) {
                assertTrue(colors.add(appearance.elementBackground(state, theme, UiTheme.INPUT)));
                assertEquals(fixed.backgroundArgb(theme), appearance.elementBackground(state, theme, fixed));
                assertEquals(0, appearance.elementBackground(state, theme, transparent) >>> 24);
                assertEquals(appearance.elementBackground(state, theme), appearance.elementBackground(state, theme, UiStyle.CONTROL));
            }
            assertEquals(theme.argb(UiColorRole.INPUT), appearance.elementBackground(FloatingMenuElementState.NORMAL, theme, UiTheme.INPUT));
        }
    }

    @Test void everyButtonStateUsesTheSelectedPaletteAndTheDefaultHasNoBlueTint() {
        for (UiPalette theme : UiPalette.values()) {
            assertEquals(theme, UiPalette.fromId(theme.id()));
            var colors = new java.util.HashSet<Integer>();
            for (FloatingMenuElementState state : FloatingMenuElementState.values()) {
                int color = FloatingMenuAppearance.DEFAULT.elementBackground(state, theme);
                assertTrue(colors.add(color), "Button states must remain distinguishable");
                assertTrue((color >>> 24) > 0 && (color >>> 24) < 255);
                if (theme == UiPalette.DEFAULT) {
                    assertEquals(color & 255, (color >>> 8) & 255);
                    assertEquals(color & 255, (color >>> 16) & 255);
                }
                assertEquals(0, FloatingMenuAppearance.NO_BACKGROUNDS.elementBackground(state, theme));
            }
        }
        assertEquals(UiPalette.DEFAULT, UiPalette.fromId(null));
        assertEquals(UiPalette.DEFAULT, UiPalette.fromId("unknown"));
    }

    @Test void panelsImagesAndCompanionLayersResolveTheSamePlayerThemeWithoutChangingLayoutOrActions() {
        var scene = page();
        SceneGeometry previous = null;
        for (UiPalette theme : UiPalette.values()) {
            var geometry = SceneGeometry.measure(scene, 1, FloatingMenuFieldOfView.DEFAULT, 1, theme);
            assertNotNull(geometry.popover());
            assertTrue(surfaces(geometry).contains(theme.argb(UiColorRole.WINDOW)));
            assertTrue(surfaces(geometry).contains(theme.argb(UiColorRole.TOOLBAR)));
            assertTrue(surfaces(geometry).contains(theme.argb(UiColorRole.INPUT)), "Image backgrounds use the same palette");
            assertTrue(surfaces(geometry.popover().content()).contains(theme.argb(UiColorRole.WINDOW)));
            if (previous != null) {
                assertEquals(previous.frame(), geometry.frame(), "Theme changes must not resize or move controls");
                assertEquals(previous.definition().entries().keySet(), geometry.definition().entries().keySet());
                assertNotEquals(surfaces(previous), surfaces(geometry));
            }
            previous = geometry;
        }
    }

    @Test void changingThemeDuringLoadingRepaintsTheRetainedPageAndKeepsItsPanelReservations() {
        var state = new MenuLayoutState();
        var first = state.measure(page(), 1, FloatingMenuFieldOfView.DEFAULT, 1, UiPalette.CHARCOAL);
        var loading = MenuScene.screen("theme-test").loading(true).content(Ui.text(Component.text("Loading")).build()).build();
        var changed = state.measure(loading, 1, FloatingMenuFieldOfView.DEFAULT, 1, UiPalette.WARM);
        assertEquals(first.frame(), changed.frame());
        assertNotEquals(surfaces(first), surfaces(changed));
        assertSame(changed, state.measure(loading, 1, FloatingMenuFieldOfView.DEFAULT, 1, UiPalette.WARM));
    }

    @Test void literalColorsAndExplicitTransparencySurviveThemeChanges() {
        var custom = UiColor.literal(0x123456, .42);
        var transparent = UiColor.themed(UiColorRole.WINDOW).withAlpha(0);
        var appearance = FloatingMenuAppearance.builder().allBackgrounds(custom.argb()).build();
        for (UiPalette theme : UiPalette.values()) {
            assertEquals(custom.argb(), custom.argb(theme));
            assertEquals(custom.argb(), appearance.titleBackground(theme));
            for (var state : FloatingMenuElementState.values()) assertEquals(custom.argb(), appearance.elementBackground(state, theme));
            assertEquals(0, transparent.argb(theme) >>> 24);
            assertEquals(UiColorRole.WINDOW, assertInstanceOf(UiColor.Themed.class, transparent).role());
            assertEquals(theme.argb(UiColorRole.TOOLTIP), UiTheme.TOOLTIP.backgroundArgb(theme));
        }
        var decoration = (FloatingMenuDecoration.Text) FloatingMenuDecoration.text("caption", FloatingMenuPoint.ORIGIN, Component.text("Caption")).content();
        assertEquals(UiColorRole.WINDOW, assertInstanceOf(UiColor.Themed.class, decoration.backgroundColor()).role());
    }

    private MenuScene page() {
        var image = Ui.image(UiImage.remote("https://example.org/cover.png", 64, 64), Component.text("Sample"))
                .key("image").size(UiSize.square(UiLength.ui(24))).style(UiTheme.INPUT).build();
        return MenuScene.screen("theme-test").content(UiTemplates.sheet(
                UiTemplates.window("window", Ui.column().child(Ui.button(Component.text("Details")).key("source").primary((_,_) -> {}).build()).child(image).build()),
                UiTemplates.toolbar("tools", Ui.text(Component.text("Tools")).build())))
                .popover(MenuPopover.attached(UiKey.named("source"), Component.text("Details"), Ui.text(Component.text("Details")).build(), Component.text("Close"))).build();
    }

    private List<Integer> surfaces(SceneGeometry geometry) {
        return geometry.generatedDecorations().values().stream().map(FloatingMenuDecoration::content)
                .filter(FloatingMenuDecoration.Surface.class::isInstance).map(FloatingMenuDecoration.Surface.class::cast)
                .map(FloatingMenuDecoration.Surface::background).toList();
    }

    private static double luminance(int rgb, double alpha) {
        double result = 0;
        int[] shifts = {16, 8, 0};
        double[] weights = {.2126, .7152, .0722};
        for (int index = 0; index < shifts.length; index++) {
            double channel = (alpha * ((rgb >>> shifts[index]) & 255) + (1 - alpha) * 255) / 255;
            double linear = channel <= .04045 ? channel / 12.92 : Math.pow((channel + .055) / 1.055, 2.4);
            result += weights[index] * linear;
        }
        return result;
    }
}
