package org.encinet.mik.feature.world.plot;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class PlotAtmosphereMenuTest {
    @Test void timeAndWeatherKeepTheirPagesAndWorldValuesUntilAnExplicitChoice() throws Exception {
        var settings = List.of(PlotAtmosphere.WORLD, new PlotAtmosphere(6000, PlotAtmosphere.Weather.CLEAR),
                new PlotAtmosphere(3456, PlotAtmosphere.Weather.RAIN));
        for (var locale : Language.values()) {
            var language = language(locale);
            for (var setting : settings) for (boolean subPlot : new boolean[]{false, true})
                for (double font : new double[]{.7, 1.8}) for (double scale : new double[]{.7, 1.3}) for (int fov : new int[]{30, 110}) {
                    var state = new AtomicReference<>(PlotAtmosphereMenu.State.start(new UUID(0, 1)));
                    var times = new ArrayList<Integer>(); var weather = new ArrayList<PlotAtmosphere.Weather>(); var custom = new AtomicInteger();
                    var actions = new PlotAtmosphereMenu.Actions(page -> state.updateAndGet(current -> current.read(page)),
                            section -> state.updateAndGet(current -> current.view(section)), times::add, weather::add, custom::incrementAndGet);
                    int timePage = 0;
                    for (var section : PlotAtmosphereMenu.Section.values()) {
                        var seen = new HashSet<String>(); int selected = 0;
                        for (int pass = 0; pass < 6; pass++) {
                            var scene = PlotAtmosphereMenu.compose(language, locale, "庭院项目", subPlot, setting, state.get(), actions).build();
                            var frame = SceneGeometryTestSupport.readableFrame(scene, font, new FloatingMenuFieldOfView(fov), scale);
                            assertDisjoint(frame); assertNotNull(element(frame, "back"));
                            assertTrue(times.isEmpty()); assertTrue(weather.isEmpty()); assertEquals(0, custom.get());
                            for (var tab : PlotAtmosphereMenu.Section.values())
                                assertEquals(tab == section, element(frame, "section:" + tab).node().input().selected());
                            for (var item : frame.elements()) if (item.path().startsWith(frame.readingRegion().path() + "/items/")) {
                                assertTrue(seen.add(item.node().key().value()));
                                if (item.node().input().selected()) selected++;
                                assertFullTextAvailable(entry(scene,item.node().key().value()).label(),item);
                            }
                            var page = frame.pagination().getFirst();
                            if (!page.page().hasNext()) {
                                if (section == PlotAtmosphereMenu.Section.TIME) {
                                    timePage = state.get().page();
                                    entry(scene, frame, "section:WEATHER").trigger(FloatingMenuInteraction.PRIMARY)
                                            .execute(null, null, FloatingMenuInteraction.PRIMARY);
                                    assertEquals(timePage, state.get().timePage()); assertEquals(0, state.get().weatherPage());
                                } else {
                                    entry(scene, frame, "weather:2").trigger(FloatingMenuInteraction.PRIMARY)
                                            .execute(null, null, FloatingMenuInteraction.PRIMARY);
                                    assertEquals(List.of(PlotAtmosphere.Weather.RAIN), weather);
                                    entry(scene, frame, "section:TIME").trigger(FloatingMenuInteraction.PRIMARY)
                                            .execute(null, null, FloatingMenuInteraction.PRIMARY);
                                    assertEquals(timePage, state.get().page());
                                }
                                break;
                            }
                            page.action(FloatingMenuInteraction.SCROLL_DOWN).execute(null, null, FloatingMenuInteraction.SCROLL_DOWN);
                        }
                        assertEquals(section == PlotAtmosphereMenu.Section.TIME ? 6 : 3, seen.size(), locale + " · " + section);
                        assertEquals(1, selected, locale + " · " + section);
                    }
                    assertTrue(times.isEmpty()); assertEquals(0, custom.get());
                }
        }
    }

    @Test void invalidClockKeepsTheDraftAndPermissionIsRecheckedBeforeSubmission() throws Exception {
        for (var locale : Language.values()) {
            var allowed = new AtomicBoolean(true); var submitted = new ArrayList<Integer>(); var denied = Component.text("Permission changed");
            var form = PlotAtmosphereMenu.timeInput(language(locale), locale, "25:99", _ -> allowed.get() ? null : denied,
                    (_, ticks) -> submitted.add(ticks));
            for (String clock : List.of("24:00", "12:61", "abc", "1")) {
                assertNotNull(form.validation().apply(null, new MenuInputForm.Values(Map.of("value", clock))));
                assertEquals("25:99", form.fields().getFirst().initial()); assertTrue(submitted.isEmpty());
            }
            var noon = new MenuInputForm.Values(Map.of("value", "12:00"));
            assertNull(form.validation().apply(null, noon));
            allowed.set(false);
            assertEquals(denied, form.validation().apply(null, noon));
            form.onSubmit().accept(null, noon); assertTrue(submitted.isEmpty());
            allowed.set(true); form.onSubmit().accept(null, noon); assertEquals(List.of(6000), submitted);
        }
    }

    @Test void anotherPlotCannotReuseTheTimeControlsInputIdentity() throws Exception {
        var language = language(Language.EN_US); var paths = new HashSet<String>();
        for (var id : List.of(new UUID(0, 1), new UUID(0, 2))) {
            var state = PlotAtmosphereMenu.State.start(id);
            var actions = new PlotAtmosphereMenu.Actions(_ -> { }, _ -> { }, _ -> { }, _ -> { }, () -> { });
            var scene = PlotAtmosphereMenu.compose(language, Language.EN_US, "Plot", false, PlotAtmosphere.WORLD, state, actions).build();
            assertEquals(PlotAtmosphereMenu.SCREEN_ID, scene.screenId(), "Navigation must match the registered screen owner");
            assertTrue(paths.add(entry(scene, "time:0").id()));
        }
    }

    private static LanguageService language(Language locale) throws Exception {
        var bundle = FluentTestSupport.bundle(locale);
        return new LanguageService(null) {
            @Override public String t(Language selected, Message message, Object... args) { return FluentTestSupport.text(bundle, message, args); }
        };
    }
}
