package org.encinet.mik.feature.entertainment.music.rhythm;

import java.util.function.BiConsumer;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.menu.FloatingMenuAppearance;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.ui.*;

/** Setup is a selection followed by an explicit start, with the task's controls below its content. */
final class RhythmSetupMenu {
    private RhythmSetupMenu() { }

    static UiNode choices(String source, int page, java.util.function.IntConsumer selectPage) {
        return layout(UiTemplates.pagedActions(source,1,page,selectPage).withCompactModels()
                .withSizing(UiSizing.width(UiLength.ZERO,UiLength.ui(480))),UiTemplates.title("summary"));
    }

    private static UiNode layout(UiNode choices, UiNode summary) {
        var reading = UiTemplates.focus(Ui.column().key("setup-content").gap(UiLength.ui(12))
                .child(summary).child(choices).build(),choices.key());
        return UiTemplates.sheet(UiTemplates.window("setup",reading),
                UiTemplates.toolbar("tools", UiTemplates.menu(UiTemplates.actions("actions", 1),
                        UiTemplates.navigation("navigation"))));
    }

    static MenuScene.Builder difficulty(Component track, RhythmDifficulty selected, int page, java.util.function.IntConsumer selectPage,
                                        Function<RhythmDifficulty, Component> label, Component disabledReason,
                                        BiConsumer<Player, RhythmDifficulty> choose, BiConsumer<Player, RhythmDifficulty> start,
                                        Component startLabel, Component backLabel) {
        var options = new java.util.ArrayList<UiNode>();
        for (var difficulty : RhythmDifficulty.values()) {
            var option = Ui.button(label.apply(difficulty)).key("difficulty:" + difficulty).singleLine()
                    .selected(difficulty == selected).primary((player,_) -> choose.accept(player,difficulty));
            if (disabledReason != null) option.disabled(disabledReason);
            options.add(option.build());
        }
        var choices = Ui.pagedFlow(1).key("difficulty").items(options).page(page,selectPage)
                .sizing(UiSizing.width(UiLength.ZERO,UiLength.ui(480))).build();
        var menu = MenuScene.screen("jukebox-rhythm-difficulty").appearance(FloatingMenuAppearance.DEFAULT).stableAnchor()
                .content(layout(choices,Ui.heading(track).key("track").singleLine().style(UiTheme.TITLE).build()));
        var begin = menu.control("start", startLabel).region("actions")
                .primary((player, _) -> start.accept(player, selected));
        if (disabledReason != null) begin.disabled(disabledReason);
        menu.back(backLabel).region("navigation");
        return menu;
    }

    static MenuScene.Builder unavailable(String screen, Component status) {
        var menu = MenuScene.screen(screen).appearance(FloatingMenuAppearance.DEFAULT).stableAnchor()
                .content(UiTemplates.sheet(UiTemplates.window("status", UiTemplates.information("status")),
                        UiTemplates.toolbar("tools", UiTemplates.navigation("navigation"))));
        menu.information("unavailable", status).region("status");
        return menu;
    }
}
