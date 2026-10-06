package org.encinet.mik.module.music.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuAppearance;
import org.encinet.mik.module.menu.FloatingMenuFraming;
import org.encinet.mik.module.menu.FloatingMenuLayouts;
import org.encinet.mik.module.menu.FloatingMenuScreen;
import org.encinet.mik.module.menu.FloatingMenuTextWidth;

import java.util.Objects;
import java.util.function.Consumer;

/** Spatial floating-menu confirmation shown before the first latency test. */
public final class RhythmCalibrationPrompt {
    private final LanguageService languageService;
    private final FloatingMenuScreen<PromptState> screen;

    public RhythmCalibrationPrompt(LanguageService languageService) {
        this.languageService = Objects.requireNonNull(
                languageService, "languageService");
        this.screen = new FloatingMenuScreen<>("rhythm-calibration-prompt",
                context -> render(context.player(), context.state()));
    }

    public void show(Player player, Consumer<Player> confirmAction) {
        Objects.requireNonNull(player, "player");
        screen.open(player, new PromptState(confirmAction));
    }

    private FloatingMenuDefinition render(Player player, PromptState state) {
        Component summary = languageService.text(player,
                Message.MUSIC_RHYTHM_CALIBRATION_PROMPT_REASON,
                NamedTextColor.WHITE);
        Component confirm = languageService.text(player,
                        Message.MUSIC_RHYTHM_CALIBRATION_PROMPT_CONFIRM,
                        NamedTextColor.GREEN)
                .decoration(TextDecoration.BOLD, true)
                .append(Component.newline())
                .append(languageService.text(player,
                                Message.MUSIC_RHYTHM_CALIBRATION_PROMPT_CONFIRM_HINT,
                                NamedTextColor.GRAY)
                        .decoration(TextDecoration.BOLD, false));

        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen(
                        "rhythm-calibration-prompt",
                        languageService.text(player,
                                Message.MUSIC_RHYTHM_CALIBRATION_PROMPT_TITLE,
                                MusicMenuPalette.RHYTHM))
                .appearance(FloatingMenuAppearance.RHYTHM)
                .requireSpatialPresentation()
                .stableAnchor()
                .framing(FloatingMenuFraming.COMFORTABLE)
                .layout(FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.information("information"),
                        FloatingMenuLayouts.actions("actions", 2)));
        menu.information("summary", summary)
                .region("information")
                .textWidth(FloatingMenuTextWidth.EXPANDED);
        menu.item("confirm", Material.LIME_CONCRETE, confirm)
                .region("actions")
                .textWidth(FloatingMenuTextWidth.WIDE)
                .primary((confirmed, handle) -> {
                    dismiss(handle);
                    state.confirmAction().accept(confirmed);
                });
        menu.item("later", Material.GRAY_CONCRETE,
                        languageService.text(player,
                                Message.MUSIC_RHYTHM_CALIBRATION_PROMPT_LATER,
                                NamedTextColor.GRAY))
                .region("actions")
                .primary((ignored, handle) -> dismiss(handle));
        return menu.build();
    }

    private static void dismiss(
            org.encinet.mik.module.menu.FloatingMenuHandle handle) {
        if (handle.depth() > 0) handle.back();
        else handle.close();
    }

    private record PromptState(Consumer<Player> confirmAction) {
        private PromptState {
            confirmAction = Objects.requireNonNull(
                    confirmAction, "confirmAction");
        }
    }
}
