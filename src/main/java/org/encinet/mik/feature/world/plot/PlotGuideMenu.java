package org.encinet.mik.feature.world.plot;

import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;

/** The guide owns its reading position independently of plot selection and editor drafts. */
final class PlotGuideMenu implements AutoCloseable {
    private final FloatingMenuScreen<UiReadingPosition> screen;
    private boolean closed;

    PlotGuideMenu(LanguageService language) {
        screen = new FloatingMenuScreen<>("plot-guide", _ -> UiReadingPosition.START, context -> {
            var locale = language.language(context.player());
            var source = context.state();
            return compose(language, locale, source, next -> {
                if (!closed) context.update(current -> current == source ? next : current);
            }).refreshEvery(20, (player, _) -> {
                if (language.language(player) != locale) context.redraw();
            }).build();
        });
    }

    void open(Player player) { if (!closed) screen.open(player); }
    void forget(Player player) { screen.forget(player); }
    @Override public void close() { closed = true; screen.forgetAll(); }

    static MenuScene.Builder compose(LanguageService language, Language locale, UiReadingPosition position, Consumer<UiReadingPosition> read) {
        var blocks = List.of(Message.PLOT_AREA_HINT, Message.PLOT_MAP_LEGEND, Message.PLOT_SELECTION_SESSION_HINT).stream()
                .map(message -> Ui.text(language.text(locale, message, NamedTextColor.WHITE)).key(message.key())
                        .textWidth(FloatingMenuTextWidth.CONTENT)
                        .alignment(FloatingMenuDecoration.Alignment.LEFT).keepAccessible().build()).toList();
        var reading = Ui.document().key("instructions").blocks(blocks).position(position, read).readingBody()
                .sizing(new UiSizing(UiSize.ZERO, new UiSize(600, 260))).build();
        var menu = PlotMenuLayouts.screen("plot-guide", FloatingMenuAppearance.DEFAULT, PlotMenuLayouts.help(reading));
        menu.information("heading", language.text(locale, Message.PLOT_HELP, NamedTextColor.GOLD)).region("heading").keepAccessible();
        menu.back(language.text(locale, Message.BACK, NamedTextColor.GREEN)).region("navigation").keepAccessible();
        return menu;
    }
}
