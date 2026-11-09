package org.encinet.mik.feature.entertainment.music.ui;

import java.util.function.Function;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.FloatingMenuCommand;
import org.encinet.mik.shared.menu.ui.*;

/** Selection and placement reuse the same cells; placing never appends a column of duplicate buttons. */
record MusicTableSelection<T>(boolean moving, Predicate<T> selected, Function<T, FloatingMenuCommand> activate) {
    static <T> MusicTableSelection<T> selecting(Predicate<T> selected, Function<T, FloatingMenuCommand> activate) {
        return new MusicTableSelection<>(false, selected, activate);
    }
    static <T> MusicTableSelection<T> moving(Predicate<T> selected, Function<T, FloatingMenuCommand> place) {
        return new MusicTableSelection<>(true, selected, place);
    }
    UiNode header(Component help) {
        return Ui.icon(moving ? UiIcons.INSERT_BEFORE : UiIcons.BOX, help).build();
    }
    UiNode marker(T row, Function<String,Component> label) {
        boolean chosen = selected.test(row);
        var action = activate.apply(row);
        if (action == null) return chosen ? Ui.icon(UiIcons.CHECKED_BOX,label.apply("selected")).key("select").build()
                : Ui.text(Component.empty()).key("select").build();
        var icon = chosen ? UiIcons.CHECKED_BOX : moving ? UiIcons.INSERT_BEFORE : UiIcons.BOX;
        return Ui.button(icon,label.apply(chosen ? "deselect" : moving ? "place-before" : "select-toggle"))
                .key("select").selected(chosen).primary(action).build();
    }
    Ui.ElementBuilder control(T row, Component text) {
        var control = Ui.button(text).selected(selected.test(row));
        var action = activate.apply(row);
        if (action != null) control.primary(action);
        else control.disabled(Component.empty());
        return control;
    }
}
