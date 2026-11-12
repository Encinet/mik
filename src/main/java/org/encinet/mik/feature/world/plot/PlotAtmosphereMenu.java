package org.encinet.mik.feature.world.plot;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;

/** Time and weather have separate browsing state; changing the viewed section never changes world settings. */
final class PlotAtmosphereMenu implements AutoCloseable {
    static final String SCREEN_ID = "plot-atmosphere";
    enum Section {
        TIME(Message.PLOT_MENU_TIME), WEATHER(Message.PLOT_MENU_WEATHER);
        final Message label;
        Section(Message label) { this.label = label; }
    }
    record State(UUID plotId, Section section, int timePage, int weatherPage) {
        State {
            Objects.requireNonNull(plotId); Objects.requireNonNull(section);
            if (timePage < 0 || weatherPage < 0) throw new IllegalArgumentException("Negative atmosphere reading page");
        }
        static State start(UUID plot) { return new State(plot, Section.TIME, 0, 0); }
        State view(Section next) { return new State(plotId, next, timePage, weatherPage); }
        State read(int next) { return new State(plotId, section, section == Section.TIME ? next : timePage, section == Section.WEATHER ? next : weatherPage); }
        int page() { return section == Section.TIME ? timePage : weatherPage; }
    }
    record Actions(IntConsumer read, Consumer<Section> view, Consumer<Integer> time,
                   Consumer<PlotAtmosphere.Weather> weather, Runnable custom) { }
    private final FloatingMenuScreen<State> screen;
    private boolean closed;

    PlotAtmosphereMenu(Function<FloatingMenuContext<State>, MenuScene.Builder> render) {
        screen = new FloatingMenuScreen<>(SCREEN_ID, context -> render.apply(context).build());
    }

    void open(Player player, UUID plotId) {
        if (closed) return;
        var previous = screen.state(player).filter(state -> state.plotId().equals(plotId)).orElseGet(() -> State.start(plotId));
        screen.open(player, new State(plotId, previous.section(), previous.timePage(), previous.weatherPage()));
    }

    boolean current(Player player, State source) {
        return !closed && player.isOnline() && screen.flow(player).filter(flow -> flow.state() == source
                && flow.handle().state() != FloatingMenuState.CLOSING && flow.handle().state() != FloatingMenuState.CLOSED).isPresent();
    }

    void view(FloatingMenuContext<State> context, Section next) { if (current(context.player(), context.state())) context.setState(context.state().view(next)); }
    void page(FloatingMenuContext<State> context, int next) { if (current(context.player(), context.state())) context.setState(context.state().read(next)); }
    void redraw(FloatingMenuContext<State> context) { if (current(context.player(), context.state())) context.redraw(); }
    void forget(Player player) { screen.forget(player); }
    @Override public void close() { closed = true; screen.forgetAll(); }

    static MenuScene.Builder compose(LanguageService language, Language locale, String plotName, boolean subPlot,
                                     PlotAtmosphere setting, State state, Actions actions) {
        var menu = PlotMenuLayouts.screen("plot-atmosphere:" + state.plotId(), FloatingMenuAppearance.DEFAULT,
                PlotMenuLayouts.atmosphere(state.page(), actions.read()));
        menu.information("context", Component.text(plotName, NamedTextColor.AQUA)).region("context");
        menu.information("heading", language.text(locale, Message.PLOT_MENU_ATMOSPHERE, NamedTextColor.GOLD)).region("heading");
        for (var section : Section.values()) menu.navigation("section:" + section, language.text(locale, section.label, NamedTextColor.AQUA))
                .region("sections").selected(state.section() == section).primary((_, _) -> actions.view().accept(section));
        if (state.section() == Section.TIME) {
            Integer[] choices = {null, 0, 6_000, 12_000, 18_000};
            for (int index = 0; index < choices.length; index++) {
                Integer ticks = choices[index];
                Component label = ticks == null ? language.text(locale, subPlot ? Message.PLOT_MENU_FOLLOW_PARENT : Message.PLOT_MENU_FOLLOW_WORLD, NamedTextColor.AQUA)
                        : Component.text(PlotAtmosphere.clock(ticks), NamedTextColor.AQUA);
                menu.control("time:" + index, label).selected(Objects.equals(setting.timeTicks(), ticks)).region("options")
                        .primary((_, _) -> actions.time().accept(ticks));
            }
            boolean custom = setting.timeTicks() != null && java.util.Arrays.stream(choices).noneMatch(setting.timeTicks()::equals);
            Component label = language.text(locale, Message.PLOT_MENU_CUSTOM_TIME, NamedTextColor.AQUA)
                    .append(Component.text(custom ? " · " + PlotAtmosphere.clock(setting.timeTicks()) : ""));
            menu.control("time:custom", label).selected(custom).region("options").primary((_, _) -> actions.custom().run());
        } else {
            PlotAtmosphere.Weather[] choices = {null, PlotAtmosphere.Weather.CLEAR, PlotAtmosphere.Weather.RAIN};
            Message[] labels = {subPlot ? Message.PLOT_MENU_FOLLOW_PARENT : Message.PLOT_MENU_FOLLOW_WORLD, Message.PLOT_MENU_CLEAR, Message.PLOT_MENU_RAIN};
            for (int index = 0; index < choices.length; index++) {
                var weather = choices[index];
                menu.control("weather:" + index, language.text(locale, labels[index], NamedTextColor.AQUA)).selected(setting.weather() == weather)
                        .region("options").primary((_, _) -> actions.weather().accept(weather));
            }
        }
        menu.back(language.text(locale, Message.BACK, NamedTextColor.GREEN)).region("navigation").keepAccessible();
        return menu.screen(SCREEN_ID);
    }

    static MenuInputForm timeInput(LanguageService language, Language locale, String initial, Function<Player, Component> mayEdit,
                                   BiConsumer<Player, Integer> submit) {
        return MenuInputForm.builder(language.text(locale, Message.PLOT_MENU_CUSTOM_TIME, NamedTextColor.GOLD))
                .field(MenuInputForm.Field.text("value", language.text(locale, Message.PLOT_MENU_TIME_INPUT, NamedTextColor.WHITE), initial, 5))
                .validate((player, values) -> {
                    Component failure = mayEdit.apply(player);
                    if (failure != null) return failure;
                    try { PlotAtmosphere.parseClock(values.text("value").strip()); return null; }
                    catch (PlotProblem invalid) { return language.text(locale, invalid.message(), NamedTextColor.RED, invalid.args()); }
                })
                .cancel(language.text(locale, Message.BACK, NamedTextColor.WHITE))
                .submit(language.text(locale, Message.PLOT_MENU_SUBMIT, NamedTextColor.GREEN), (player, values) -> {
                    if (mayEdit.apply(player) == null) submit.accept(player, PlotAtmosphere.parseClock(values.text("value").strip()));
                }).build();
    }
}
