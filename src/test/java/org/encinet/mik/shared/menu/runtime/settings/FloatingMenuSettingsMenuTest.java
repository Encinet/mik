package org.encinet.mik.shared.menu.runtime.settings;

import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.runtime.FloatingMenuTestWorld;
import org.encinet.mik.shared.menu.runtime.MenuAnchorTestSupport;
import org.encinet.mik.shared.menu.runtime.MenuWorkspaceTestSupport;
import org.encinet.mik.shared.menu.runtime.layout.SceneGeometry;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.encinet.mik.shell.menu.SettingsMenuTestSupport;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class FloatingMenuSettingsMenuTest {
    private static final class Harness {
        final AtomicReference<FloatingMenuPreferences> preferences;
        final AtomicInteger writes = new AtomicInteger(), edits = new AtomicInteger(), fov = new AtomicInteger(), resets = new AtomicInteger();
        UiReadingPosition position = UiReadingPosition.START;
        Harness(FloatingMenuPreferences selected) {
            preferences = new AtomicReference<>(selected);
        }
        MenuScene render(Function<Message,String> text) {
            return render(text,false);
        }
        MenuScene render(Function<Message,String> text, boolean standalone) {
            return FloatingMenuSettingsMenu.definition(preferences.get(),text,new FloatingMenuSettingsMenu.Actions(
                    (_,change) -> { preferences.updateAndGet(change); writes.incrementAndGet(); },
                    _ -> {},_ -> {},_ -> edits.incrementAndGet(),_ -> fov.incrementAndGet(),_ -> {
                        preferences.updateAndGet(current -> current.withLayout(FloatingMenuScale.DEFAULT).withText(FloatingMenuTextScale.NORMAL)); resets.incrementAndGet();
                    }),standalone,position,next -> position = next);
        }
        MenuScene render() { return render(Message::key); }
    }

    @Test void thePreferenceGroupNeverShrinksWhenItsSizeControlsIncrease() throws Exception {
        var bundle = FluentTestSupport.bundle(Language.EN_US);
        var world = new FloatingMenuTestWorld();
        var eye = new Location(world.world(), 0, 65, 0);
        for (int degrees : new int[]{70, 90, 110}) {
            var fov = new FloatingMenuFieldOfView(degrees);
            double previous = 0;
            for (var layout : FloatingMenuScale.PRESETS) {
                var preferences = new FloatingMenuPreferences(layout, FloatingMenuTextScale.NORMAL, fov);
                var definition = new Harness(preferences).render(message -> FluentTestSupport.text(bundle, message));
                var geometry = SceneGeometry.measure(definition, preferences.typographyFactor(), fov, layout.factor());
                var anchor = MenuAnchorTestSupport.front(eye, new Vector(0, 0, 1), geometry.bounds(), layout.factor(), fov);
                var pose = geometry.poses().get(element(geometry.frame(),"preview").path());
                double distance = Math.hypot(pose.right() * anchor.spatialScale(),
                        anchor.distance() - pose.forward() * anchor.spatialScale());
                double apparentText = preferences.typographyFactor() * anchor.spatialScale() / distance
                        / Math.tan(Math.toRadians(degrees / 2.0));
                assertTrue(apparentText + 1.0E-9 >= previous,
                        "FOV " + degrees + " / menu " + layout.percent() + " / before " + previous + " / after " + apparentText);
                previous = apparentText;
            }
        }
        java.lang.ref.Reference.reachabilityFence(world);
    }

    @Test void allPreferencesAndEveryColorAndFovPageFitTheStandaloneAndEmbeddedHostsInEveryLanguageAndTextSize() throws Exception {
        var custom = UiCustomTheme.copyOf(UiPalette.FOREST);
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            Function<Message,String> text = message -> FluentTestSupport.text(bundle,message);
            var textSizes = Stream.concat(FloatingMenuTextScale.PRESETS.stream(),
                    Stream.of(FloatingMenuTextScale.MINIMUM,FloatingMenuTextScale.MAXIMUM)).toList();
            for (var typography : textSizes) for (boolean standalone : List.of(true,false)) {
                var selected = new FloatingMenuPreferences(FloatingMenuScale.MAXIMUM,typography,new FloatingMenuFieldOfView(30),true,custom);
                var owner = new Harness(selected);
                String context = language + " / " + selected + " / standalone=" + standalone;
                var shown = new HashSet<String>();
                preferencePages(() -> assertDoesNotThrow(() -> hostedFrame(owner.render(text,standalone),text,selected,standalone),context),
                        frame -> {
                            assertNotNull(element(frame,"field-of-view"));
                            assertNotNull(element(frame,"reset-scale"));
                            rememberControls(frame,shown);
                        });
                assertPreferenceControls(shown,standalone);
                var colorPage = new AtomicInteger();
                allPages(() -> hostedFrame(FloatingMenuThemeEditor.definition(custom,text,colorPage.get(),colorPage::set,(_,_) -> {},_ -> {}),
                                text,selected,standalone),"colors",context,frame -> {
                            assertNotNull(element(frame,"back"));
                            assertColumn(frame.elements().stream().filter(element -> element.node().key().value().startsWith("color:")).toList());
                        });
                var fovPage = new AtomicInteger();
                allPages(() -> hostedFrame(FloatingMenuSettingsMenu.fieldOfViewDefinition(selected,text,fovPage.get(),fovPage::set,
                                (_,_) -> {},_ -> {},_ -> {}),text,selected,standalone),"presets",context,frame -> {
                            assertNotNull(element(frame,"fov-custom")); assertNotNull(element(frame,"back"));
                        });
            }
        }
    }

    @Test void allPercentageChoicesAreReachableAtBothFovAndSizeExtremesAndTheirActionsRetainTheNumericTarget() {
        for (var scale : List.of(FloatingMenuScale.MINIMUM,FloatingMenuScale.MAXIMUM)) for (var typography : List.of(FloatingMenuTextScale.MINIMUM,FloatingMenuTextScale.MAXIMUM))
            for (int degrees : new int[]{30,110}) for (String prefix : List.of("scale:","text-scale:")) {
                var snapshot = new FloatingMenuPreferences(scale,typography,new FloatingMenuFieldOfView(degrees));
                var owner = new Harness(snapshot);
                var seen = new HashSet<String>();
                preferencePages(() -> SceneGeometryTestSupport.readableFrame(owner.render(),typography.factor(),snapshot.fieldOfView(),scale.factor()),frame -> {
                    assertDisjoint(frame);
                    for (var row : frame.elements()) if (row.node().key().value().startsWith(prefix)) {
                        String key = row.node().key().value();
                        assertTrue(seen.add(key));
                        row.node().input().actions().get(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
                        int percent = Integer.parseInt(key.substring(prefix.length()));
                        assertEquals(prefix.equals("scale:") ? percent : snapshot.layout().percent(),owner.preferences.get().layout().percent());
                        assertEquals(prefix.equals("text-scale:") ? percent : snapshot.text().percent(),owner.preferences.get().text().percent());
                        owner.preferences.set(snapshot);
                    }
                });
                assertEquals(prefix.equals("scale:") ? FloatingMenuScale.PRESETS.size() : FloatingMenuTextScale.PRESETS.size(),seen.size());
            }
    }

    @Test void allPresetFovsAndIndependentSizeCombinationsKeepTheSettingsControlsWithinTheirLayout() throws Exception {
        var bundle = FluentTestSupport.bundle(Language.EN_US);
        Function<Message,String> text = message -> FluentTestSupport.text(bundle, message);
        for (var fov : FloatingMenuFieldOfView.PRESETS) for (var layout : FloatingMenuScale.PRESETS)
            for (var typography : FloatingMenuTextScale.PRESETS) for (boolean standalone : List.of(true,false)) {
                var selected = new FloatingMenuPreferences(layout, typography, fov);
                var owner = new Harness(selected);
                var shown = new HashSet<String>();
                preferencePages(() -> assertDoesNotThrow(() -> hostedFrame(owner.render(text,standalone),text,selected,standalone),
                        selected + " / standalone=" + standalone),frame -> rememberControls(frame,shown));
                assertPreferenceControls(shown,standalone);
            }
    }

    @Test void allDimensionsAreVisibleTogetherAndChoicesOnlyChangeTheirOwnDimension() {
        var owner = new Harness(FloatingMenuPreferences.DEFAULT);
        assertEquals(0,owner.writes.get());
        invoke(owner.render(),"text-scale:180",FloatingMenuInteraction.PRIMARY);
        assertEquals(180,owner.preferences.get().text().percent());
        assertEquals(FloatingMenuScale.DEFAULT,owner.preferences.get().layout());
        invoke(owner.render(),"scale:54",FloatingMenuInteraction.PRIMARY);
        assertEquals(FloatingMenuScale.COMPACT,owner.preferences.get().layout());
        assertEquals(180,owner.preferences.get().text().percent());
        assertNotNull(control(owner.render(),"text-scale:180"));
        assertNotNull(control(owner.render(),"theme:custom"));
        assertNotNull(control(owner.render(),"reduced-motion"));
        assertTrue(owner.render().triggers().isEmpty());
    }

    @Test void wheelActionsUseTheCurrentPreferencesAndStopAtEachDimensionLimit() {
        var owner = new Harness(FloatingMenuPreferences.DEFAULT);
        var old = owner.render();
        owner.preferences.set(owner.preferences.get().withLayout(new FloatingMenuScale(FloatingMenuScale.MAX_PERCENT - 1)));
        invoke(old,"scale:100",FloatingMenuInteraction.SCROLL_UP);
        assertEquals(FloatingMenuScale.MAXIMUM,owner.preferences.get().layout());
        invoke(old,"layout-custom",FloatingMenuInteraction.SCROLL_UP);
        assertEquals(FloatingMenuScale.MAXIMUM,owner.preferences.get().layout());
        var text = owner.render();
        owner.preferences.set(owner.preferences.get().withText(FloatingMenuTextScale.MINIMUM));
        invoke(text,"text-scale:100",FloatingMenuInteraction.SCROLL_DOWN);
        assertEquals(FloatingMenuTextScale.MINIMUM,owner.preferences.get().text());
        assertEquals(FloatingMenuScale.MAXIMUM,owner.preferences.get().layout());
    }

    @Test void choosingCustomCopiesTheCurrentPaletteAndPreservesTheCurrentCustomValues() {
        var owner = new Harness(FloatingMenuPreferences.DEFAULT.withTheme(UiPalette.WARM));
        var original = owner.render();
        owner.preferences.set(owner.preferences.get().withTheme(UiPalette.FOREST));
        invoke(original,"theme:custom",FloatingMenuInteraction.PRIMARY);
        var copied = assertInstanceOf(UiCustomTheme.class,owner.preferences.get().theme());
        for (var role : UiColorRole.values()) assertEquals(UiPalette.FOREST.argb(role),copied.argb(role));
        var edited = copied.withColor(UiColorRole.WINDOW,0x123456,.37);
        owner.preferences.set(owner.preferences.get().withTheme(edited));
        invoke(original,"theme:custom",FloatingMenuInteraction.PRIMARY);
        assertEquals(edited,owner.preferences.get().theme());
        invoke(owner.render(),"theme:warm",FloatingMenuInteraction.PRIMARY);
        assertEquals(UiPalette.WARM,owner.preferences.get().theme());
    }

    @Test void resettingOnlyChangesSizesAndPreservesThemeMotionAndFov() {
        var before = new FloatingMenuPreferences(FloatingMenuScale.MEDIUM,FloatingMenuTextScale.MAXIMUM,new FloatingMenuFieldOfView(91),true,UiPalette.FOREST);
        var owner = new Harness(before);
        invoke(owner.render(),"reset-scale",FloatingMenuInteraction.PRIMARY);
        assertEquals(1,owner.resets.get());
        assertEquals(before.withLayout(FloatingMenuScale.DEFAULT).withText(FloatingMenuTextScale.NORMAL),owner.preferences.get());
        invoke(owner.render(),"reduced-motion",FloatingMenuInteraction.PRIMARY);
        assertFalse(owner.preferences.get().reducedMotion());
    }

    @Test void fovPresetsStepsAndNumericWheelActionsUseTheCurrentValueWithoutChangingThePage() {
        var preferences = new AtomicReference<>(FloatingMenuPreferences.DEFAULT.withFieldOfView(new FloatingMenuFieldOfView(73)));
        var page = new AtomicInteger();
        var custom = new AtomicInteger();
        var back = new AtomicInteger();
        Supplier<MenuScene> render = () -> FloatingMenuSettingsMenu.fieldOfViewDefinition(preferences.get(),Message::key,page.get(),page::set,
                (_,change) -> preferences.updateAndGet(change),_ -> custom.incrementAndGet(),_ -> back.incrementAndGet());
        var original = render.get();
        invoke(original,"fov-increase",FloatingMenuInteraction.PRIMARY); assertEquals(78,preferences.get().fieldOfView().degrees());
        invoke(original,"fov-decrease",FloatingMenuInteraction.PRIMARY); assertEquals(73,preferences.get().fieldOfView().degrees());
        invoke(original,"fov:30",FloatingMenuInteraction.SCROLL_UP); assertEquals(78,preferences.get().fieldOfView().degrees());
        assertEquals(0,page.get());
        invoke(original,"fov:30",FloatingMenuInteraction.PRIMARY); assertEquals(30,preferences.get().fieldOfView().degrees());
        invoke(original,"fov-decrease",FloatingMenuInteraction.PRIMARY); assertEquals(30,preferences.get().fieldOfView().degrees());
        invoke(original,"fov-custom",FloatingMenuInteraction.PRIMARY); invoke(original,"back",FloatingMenuInteraction.PRIMARY);
        assertEquals(1,custom.get()); assertEquals(1,back.get());
    }

    @Test void thePanelRejectsRetiredEditorCallbacksAndClosedReads() {
        var preferences = new AtomicReference<>(FloatingMenuPreferences.DEFAULT);
        var reads = new AtomicInteger(); var redraws = new AtomicInteger();
        var language = new LanguageService(null) {
            @Override public String t(Player player, Message key, Object... args) { return key.key(); }
        };
        var panel = new FloatingMenuSettingsPanel(null,language,() -> { reads.incrementAndGet(); return preferences.get(); },preferences::set,redraws::incrementAndGet);
        var opening = panel.render();
        var oldLayout = control(opening,"scale:54").trigger(FloatingMenuInteraction.PRIMARY);
        invoke(opening,"field-of-view",FloatingMenuInteraction.PRIMARY);
        oldLayout.execute(null,null,FloatingMenuInteraction.PRIMARY);
        assertEquals(FloatingMenuPreferences.DEFAULT,preferences.get());
        invoke(panel.render(),"back",FloatingMenuInteraction.PRIMARY);
        invoke(panel.render(),"theme:custom",FloatingMenuInteraction.PRIMARY);
        var main = panel.render();
        var retiredTheme = control(main,"theme:warm").trigger(FloatingMenuInteraction.PRIMARY);
        invoke(main,"edit-theme",FloatingMenuInteraction.PRIMARY);
        var colors = panel.render();
        var retiredColor = entry(colors,"color:window").trigger(FloatingMenuInteraction.PRIMARY);
        var oldBack = entry(colors,"back").trigger(FloatingMenuInteraction.PRIMARY);
        retiredTheme.execute(null,null,FloatingMenuInteraction.PRIMARY);
        assertInstanceOf(UiCustomTheme.class,preferences.get().theme());
        invoke(colors,"back",FloatingMenuInteraction.PRIMARY);
        assertTrue(control(panel.render(),"theme:custom").selected());
        oldBack.execute(null,null,FloatingMenuInteraction.PRIMARY);
        retiredColor.execute(null,null,FloatingMenuInteraction.PRIMARY);
        assertEquals("interface-scale",panel.render().screenId());
        invoke(panel.render(),"edit-theme",FloatingMenuInteraction.PRIMARY);
        preferences.set(preferences.get().withTheme(UiPalette.WARM));
        assertEquals("interface-scale",panel.render().screenId(),"a removed custom theme invalidates its editor");
        retiredColor.execute(null,null,FloatingMenuInteraction.PRIMARY);
        panel.close(); int before = reads.get();
        assertTrue(panel.render().entries().isEmpty()); assertEquals(before,reads.get());
        oldLayout.execute(null,null,FloatingMenuInteraction.PRIMARY); assertEquals(before,reads.get());
    }

    private static final class FormHost implements FloatingMenuSettingsPanel.FormFactory {
        MenuInputForm form;
        Runnable finished;
        int closes;
        String focused;
        @Override public MenuFormSession open(Player player, MenuInputForm form, Runnable changed, Runnable finished) {
            this.form = form; this.finished = finished;
            return new MenuFormSession() {
                @Override public UiNode content(Actions actions) { return Ui.column().build(); }
                @Override public void focus(String field) { focused = field; }
                @Override public void edit(String field) { fail("Opening settings must only reveal the field"); }
                @Override public void close() { closes++; finished.run(); }
            };
        }
        void submit(java.util.Map<String,String> values) {
            form.onSubmit().accept(null, new MenuInputForm.Values(values));
            finished.run();
        }
    }

    @Test void formSubmissionsReturnToTheirSettingsPageAndUseTheLatestIndependentPreferences() {
        var preferences = new AtomicReference<>(FloatingMenuPreferences.DEFAULT.withTheme(UiCustomTheme.copyOf(UiPalette.WARM)));
        var writes = new AtomicInteger(); var forms = new FormHost();
        var language = new LanguageService(null) {
            @Override public String t(Player player, Message key, Object... args) { return key.key(); }
        };
        var panel = new FloatingMenuSettingsPanel(null,language,preferences::get,value -> { preferences.set(value); writes.incrementAndGet(); },() -> {},forms);
        invoke(panel.render(),"edit-theme",FloatingMenuInteraction.PRIMARY);
        invoke(panel.render(),"color:window",FloatingMenuInteraction.PRIMARY);
        assertEquals("color",forms.focused);
        var before = assertInstanceOf(UiCustomTheme.class,preferences.get().theme());
        preferences.set(preferences.get().withText(FloatingMenuTextScale.MAXIMUM).withTheme(before.withColor(UiColorRole.TOOLBAR,0xABCDEF,.61)));
        forms.submit(java.util.Map.of("color","#123456","opacity","37%"));
        assertEquals("interface-theme-editor",panel.render().screenId());
        var changed = assertInstanceOf(UiCustomTheme.class,preferences.get().theme());
        assertEquals(0x123456,changed.rgb(UiColorRole.WINDOW)); assertEquals(.37,changed.alpha(UiColorRole.WINDOW));
        assertEquals(0xABCDEF,changed.rgb(UiColorRole.TOOLBAR)); assertEquals(.61,changed.alpha(UiColorRole.TOOLBAR));
        assertEquals(FloatingMenuTextScale.MAXIMUM,preferences.get().text());
        forms.submit(java.util.Map.of("color","#000000","opacity","10")); assertEquals(1,writes.get());
        invoke(panel.render(),"back",FloatingMenuInteraction.PRIMARY);
        invoke(panel.render(),"field-of-view",FloatingMenuInteraction.PRIMARY);
        invoke(panel.render(),"fov-custom",FloatingMenuInteraction.PRIMARY);
        assertEquals("degrees",forms.focused);
        assertNotNull(forms.form.validation().apply(null,new MenuInputForm.Values(java.util.Map.of("degrees","111"))));
        forms.submit(java.util.Map.of("degrees","91"));
        assertEquals("interface-fov",panel.render().screenId());
        assertEquals(91,preferences.get().fieldOfView().degrees()); assertEquals(2,writes.get());
        assertEquals(FloatingMenuTextScale.MAXIMUM,preferences.get().text());
        panel.close();
    }

    @Test void exactScaleFormsValidateTheirOwnBoundsAndOnlyApplyConfirmedValues() {
        var preferences = new AtomicReference<>(FloatingMenuPreferences.DEFAULT);
        var writes = new AtomicInteger();
        var forms = new FormHost();
        var language = new LanguageService(null) {
            @Override public String t(Player player, Message key, Object... args) {
                if (key == Message.INTERFACE_SCALE_INVALID) return args[0] + "–" + args[1];
                return key.key();
            }
        };
        try (var panel = new FloatingMenuSettingsPanel(null,language,preferences::get,value -> {
            preferences.set(value);
            writes.incrementAndGet();
        },() -> {},forms)) {
            invoke(panel.render(),"layout-custom",FloatingMenuInteraction.PRIMARY);
            assertEquals("percent",forms.focused);
            assertEquals("100",forms.form.fields().getFirst().initial());
            assertPercentageRange(forms.form,FloatingMenuScale.MIN_PERCENT,FloatingMenuScale.MAX_PERCENT);
            assertEquals(0,writes.get());
            forms.submit(java.util.Map.of("percent"," 112% "));
            assertEquals("interface-scale",panel.render().screenId());
            assertEquals(new FloatingMenuScale(112),preferences.get().layout());
            assertEquals(FloatingMenuTextScale.NORMAL,preferences.get().text());
            assertTrue(control(panel.render(),"layout-custom").selected());
            assertFalse(control(panel.render(),"scale:100").selected());

            invoke(panel.render(),"text-custom",FloatingMenuInteraction.PRIMARY);
            assertPercentageRange(forms.form,FloatingMenuTextScale.MIN_PERCENT,FloatingMenuTextScale.MAX_PERCENT);
            forms.finished.run();
            forms.submit(java.util.Map.of("percent","１９３％"));
            assertEquals(1,writes.get(),"cancelled forms cannot commit");

            invoke(panel.render(),"text-custom",FloatingMenuInteraction.PRIMARY);
            forms.submit(java.util.Map.of("percent","１９３％"));
            assertEquals(new FloatingMenuTextScale(193),preferences.get().text());
            assertEquals(new FloatingMenuScale(112),preferences.get().layout());
            assertEquals(2,writes.get());
            assertTrue(control(panel.render(),"text-custom").selected());
        }
    }

    private static void assertPercentageRange(MenuInputForm form,int min,int max) {
        for (int amount : new int[]{min,max}) {
            var values = new MenuInputForm.Values(java.util.Map.of("percent",Integer.toString(amount)));
            assertNull(form.validation().apply(null,values));
        }
        for (String raw : List.of(Integer.toString(min - 1),Integer.toString(max + 1),"", "NaN", "80.5", "2147483648")) {
            var values = new MenuInputForm.Values(java.util.Map.of("percent",raw));
            assertEquals(net.kyori.adventure.text.Component.text(min + "–" + max,net.kyori.adventure.text.format.NamedTextColor.RED),
                    form.validation().apply(null,values),raw);
        }
    }

    @Test void ownerSuspensionAndClosingInvalidateNativeResultsAndRetireTheirSessionOnce() {
        for (var state : List.of(FloatingMenuState.SUSPENDED,FloatingMenuState.CLOSING,FloatingMenuState.CLOSED)) {
            var preferences = new AtomicReference<>(FloatingMenuPreferences.DEFAULT); var forms = new FormHost();
            var language = new LanguageService(null) {
                @Override public String t(Player player, Message key, Object... args) { return key.key(); }
            };
            var panel = new FloatingMenuSettingsPanel(null,language,preferences::get,_ -> fail("Retired input wrote preferences"),() -> {},forms);
            invoke(panel.render(),"field-of-view",FloatingMenuInteraction.PRIMARY);
            invoke(panel.render(),"fov-custom",FloatingMenuInteraction.PRIMARY);
            panel.render().lifecycle().changed(null,null,FloatingMenuState.ACTIVE,state,FloatingMenuCloseReason.BACK);
            forms.submit(java.util.Map.of("degrees","91"));
            assertEquals(FloatingMenuPreferences.DEFAULT,preferences.get()); assertEquals(1,forms.closes);
            panel.close(); assertEquals(1,forms.closes);
        }
    }

    private static UiFrame hostedFrame(MenuScene scene, Function<Message,String> text, FloatingMenuPreferences selected, boolean standalone) {
        var authored = standalone ? scene : SettingsMenuTestSupport.frame(scene,text,"INTERFACE");
        try (var host = new MenuWorkspaceTestSupport(UUID.randomUUID(),authored)) {
            return SceneGeometry.measure(host.composed(),selected.typographyFactor(),selected.fieldOfView(),
                    selected.layout().factor(),selected.theme()).frame();
        }
    }

    private static void rememberControls(UiFrame frame, java.util.Set<String> shown) {
        assertDisjoint(frame);
        frame.elements().forEach(row -> shown.add(row.node().key().value()));
    }

    private static void assertPreferenceControls(java.util.Set<String> shown, boolean standalone) {
        for (var layout : FloatingMenuScale.PRESETS) assertTrue(shown.contains("scale:" + layout.percent()));
        for (var text : FloatingMenuTextScale.PRESETS) assertTrue(shown.contains("text-scale:" + text.percent()));
        for (var palette : UiPalette.values()) assertTrue(shown.contains("theme:" + palette.id()));
        for (String key : List.of("preview","layout-custom","text-custom","field-of-view","reset-scale","theme:custom","reduced-motion",standalone ? "back" : "category:interface"))
            assertTrue(shown.contains(key),key);
    }

    private static void preferencePages(Supplier<UiFrame> render, java.util.function.Consumer<UiFrame> check) {
        int count = 1;
        for (int index = 0; index < count; index++) {
            var frame = render.get();
            check.accept(frame);
            var pager = frame.pagination().stream().filter(value -> value.readingPosition().flatMap(UiReadingPosition::block)
                    .map(UiKey::value).filter(key -> List.of("layout","text","theme","motion").contains(key)).isPresent()).findFirst();
            if (pager.isEmpty()) return;
            var page = pager.get().page();
            assertEquals(index,page.index());
            count = page.count();
            if (page.hasNext()) pager.get().selectPage().accept(page.next().index());
        }
    }

    private static void allPages(Supplier<UiFrame> render, String collection, String context, java.util.function.Consumer<UiFrame> check) {
        int count = 1;
        for (int index = 0; index < count; index++) {
            var frame = assertDoesNotThrow(render::get,context + " / " + collection + " / page " + index);
            assertDisjoint(frame); check.accept(frame);
            var paging = paging(frame,collection); count = paging.page().count();
            assertEquals(index,paging.page().index());
            if (paging.page().hasNext()) paging.selectPage().accept(paging.page().next().index());
        }
    }
    private static UiFrame.Pagination paging(UiFrame frame, String key) {
        return frame.pagination().stream().filter(value -> value.path().endsWith("/" + key)).findFirst().orElseThrow();
    }
    private static void invoke(MenuScene scene, String key, FloatingMenuInteraction interaction) {
        var target = control(scene,key); assertNotNull(target,key); target.trigger(interaction).execute(null,null,interaction);
    }
    private static MenuScene.Entry control(MenuScene scene, String key) {
        return entry(scene,SceneGeometryTestSupport.measure(scene,1),key);
    }
}
