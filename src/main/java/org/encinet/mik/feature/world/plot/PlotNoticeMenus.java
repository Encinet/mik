package org.encinet.mik.feature.world.plot;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;

/** Owns standalone notice reading and scoped redraws; popup readers reuse the same content recipe. */
final class PlotNoticeMenus implements AutoCloseable {
    private record State(UUID plotId, UiReadingPosition position) { }
    private final Function<UUID, PlotNoticeBoard> current;
    private final BiConsumer<Player, UUID> fallback;
    private final LanguageService language;
    private final FloatingMenuScreen<State> screen = new FloatingMenuScreen<>("plot-notice", this::render);

    PlotNoticeMenus(Function<UUID, PlotNoticeBoard> current, BiConsumer<Player, UUID> fallback, LanguageService language) {
        this.current = current; this.fallback = fallback; this.language = language;
    }

    void open(Player player, UUID plotId) {
        var notice = current.apply(plotId);
        if (notice == null || notice.body().isBlank()) { fallback.accept(player, plotId); return; }
        var position = screen.state(player).filter(state -> state.plotId().equals(plotId))
                .map(State::position).orElse(UiReadingPosition.START);
        screen.open(player, new State(plotId, position));
    }

    private MenuScene render(FloatingMenuContext<State> context) {
        var state = context.state();
        var notice = current.apply(state.plotId());
        UiNode reading = notice == null || notice.body().isBlank()
                ? Ui.text(language.text(context.player(), Message.PLOT_NOTICE_CLEARED, NamedTextColor.WHITE)).key("empty").build()
                : PlotNoticeContent.reading(notice, state.position(), next -> context.update(latest ->
                        latest.plotId().equals(state.plotId()) ? new State(latest.plotId(), next) : latest));
        var menu = PlotMenuLayouts.screen("plot-notice", FloatingMenuAppearance.DEFAULT, PlotMenuLayouts.noticeBoard(reading))
                .refreshWhenChanged(20, _ -> current.apply(state.plotId()), (viewer, _) -> {
                    var fresh = current.apply(state.plotId());
                    if (fresh == null || fresh.body().isBlank()) fallback.accept(viewer, state.plotId());
                    else context.redraw();
                });
        menu.back(language.text(context.player(), Message.BACK, NamedTextColor.GREEN)).region("navigation").keepAccessible();
        return menu.build();
    }

    void forget(Player player) { screen.forget(player); }
    @Override public void close() { screen.forgetAll(); }
}
