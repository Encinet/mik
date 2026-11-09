package org.encinet.mik.shared.menu.runtime;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
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
        Harness(FloatingMenuPreferences selected) {
            preferences = new AtomicReference<>(selected);
        }
        MenuScene render(Function<Message,String> text) {
            return FloatingMenuSettingsMenu.definition(preferences.get(),text,new FloatingMenuSettingsMenu.Actions(
                    (_,change) -> { preferences.updateAndGet(change); writes.incrementAndGet(); },
                    _ -> edits.incrementAndGet(),_ -> fov.incrementAndGet(),_ -> {
                        preferences.updateAndGet(current -> current.withLayout(FloatingMenuScale.NORMAL).withText(FloatingMenuTextScale.NORMAL)); resets.incrementAndGet();
                    }));
        }
        MenuScene render() { return render(Message::key); }
    }

    @Test void allPreferencesAndEveryColorAndFovPageFitTheStandaloneAndEmbeddedHostsInEveryLanguageAndTextSize() throws Exception {
        var custom = UiCustomTheme.copyOf(UiPalette.FOREST);
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            Function<Message,String> text = message -> FluentTestSupport.text(bundle,message);
            for (var typography : FloatingMenuTextScale.values()) {
                var selected = FloatingMenuPreferences.DEFAULT.withTheme(custom).withText(typography);
                var owner = new Harness(selected);
                allPages(() -> owner.render(text),null,typography.factor(),frame -> {
                    for (String key : List.of("preview","field-of-view","reset-scale","scale:normal","text-scale:100","theme:custom","reduced-motion"))
                        assertNotNull(element(frame,key));
                    assertTrue(frame.pagination().isEmpty());
                },text);
                var colorPage = new AtomicInteger();
                allPages(() -> FloatingMenuThemeEditor.definition(custom,text,colorPage.get(),colorPage::set,(_,_) -> {},_ -> {}),
                        "colors",typography.factor(),frame -> {
                            assertNotNull(element(frame,"back"));
                            assertColumn(frame.elements().stream().filter(element -> element.node().key().value().startsWith("color:")).toList());
                        },text);
                var fovPage = new AtomicInteger();
                allPages(() -> FloatingMenuSettingsMenu.fieldOfViewDefinition(selected,text,fovPage.get(),fovPage::set,
                                (_,_) -> {},_ -> {},_ -> {}),"presets",typography.factor(),frame -> {
                            assertNotNull(element(frame,"fov-custom")); assertNotNull(element(frame,"back"));
                        },text);
            }
        }
    }

    @Test void allPercentageChoicesAreReachableAtBothFovAndSizeExtremesAndTheirActionsRetainTheNumericTarget() {
        for (var scale : List.of(FloatingMenuScale.MINIMUM,FloatingMenuScale.MAXIMUM)) for (var typography : List.of(FloatingMenuTextScale.MINIMUM,FloatingMenuTextScale.MAXIMUM))
            for (int degrees : new int[]{30,110}) for (String prefix : List.of("scale:","text-scale:")) {
                var snapshot = new FloatingMenuPreferences(scale,typography,new FloatingMenuFieldOfView(degrees));
                var owner = new Harness(snapshot);
                var seen = new HashSet<String>();
                var scene = owner.render();
                var frame = SceneGeometryTestSupport.readableFrame(scene,typography.factor(),snapshot.fieldOfView(),scale.factor());
                assertDisjoint(frame);
                assertTrue(frame.pagination().isEmpty());
                for (var row : frame.elements()) if (row.node().key().value().startsWith(prefix)) {
                    String key = row.node().key().value(); assertTrue(seen.add(key));
                    row.node().input().actions().get(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
                    assertEquals(prefix.equals("scale:") ? key.substring(prefix.length()) : snapshot.layout().id(),owner.preferences.get().layout().id());
                    assertEquals(prefix.equals("text-scale:") ? key.substring(prefix.length()) : snapshot.text().id(),owner.preferences.get().text().id());
                    owner.preferences.set(snapshot);
                }
                assertEquals(prefix.equals("scale:") ? FloatingMenuScale.values().length : FloatingMenuTextScale.values().length,seen.size());
            }
    }

    @Test void allDimensionsAreVisibleTogetherAndChoicesOnlyChangeTheirOwnDimension() {
        var owner = new Harness(FloatingMenuPreferences.DEFAULT);
        assertEquals(0,owner.writes.get());
        invoke(owner.render(),"text-scale:180",FloatingMenuInteraction.PRIMARY);
        assertEquals(FloatingMenuTextScale.MAXIMUM,owner.preferences.get().text());
        assertEquals(FloatingMenuScale.NORMAL,owner.preferences.get().layout());
        invoke(owner.render(),"scale:minimum",FloatingMenuInteraction.PRIMARY);
        assertEquals(FloatingMenuScale.MINIMUM,owner.preferences.get().layout());
        assertEquals(FloatingMenuTextScale.MAXIMUM,owner.preferences.get().text());
        assertNotNull(entry(owner.render(),"text-scale:180"));
        assertNotNull(entry(owner.render(),"theme:custom"));
        assertNotNull(entry(owner.render(),"reduced-motion"));
        assertTrue(owner.render().triggers().isEmpty());
    }

    @Test void wheelActionsUseTheCurrentPreferencesAndStopAtEachDimensionLimit() {
        var owner = new Harness(FloatingMenuPreferences.DEFAULT);
        var old = owner.render();
        owner.preferences.set(owner.preferences.get().withLayout(FloatingMenuScale.EXTRA_LARGE));
        invoke(old,"scale:normal",FloatingMenuInteraction.SCROLL_UP);
        assertEquals(FloatingMenuScale.MAXIMUM,owner.preferences.get().layout());
        invoke(old,"scale:normal",FloatingMenuInteraction.SCROLL_UP);
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
        var before = new FloatingMenuPreferences(FloatingMenuScale.MAXIMUM,FloatingMenuTextScale.MAXIMUM,new FloatingMenuFieldOfView(91),true,UiPalette.FOREST);
        var owner = new Harness(before);
        invoke(owner.render(),"reset-scale",FloatingMenuInteraction.PRIMARY);
        assertEquals(1,owner.resets.get());
        assertEquals(before.withLayout(FloatingMenuScale.NORMAL).withText(FloatingMenuTextScale.NORMAL),owner.preferences.get());
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
        var oldLayout = entry(opening,"scale:minimum").trigger(FloatingMenuInteraction.PRIMARY);
        invoke(opening,"field-of-view",FloatingMenuInteraction.PRIMARY);
        oldLayout.execute(null,null,FloatingMenuInteraction.PRIMARY);
        assertEquals(FloatingMenuPreferences.DEFAULT,preferences.get());
        invoke(panel.render(),"back",FloatingMenuInteraction.PRIMARY);
        invoke(panel.render(),"theme:custom",FloatingMenuInteraction.PRIMARY);
        var main = panel.render();
        var retiredTheme = entry(main,"theme:warm").trigger(FloatingMenuInteraction.PRIMARY);
        invoke(main,"edit-theme",FloatingMenuInteraction.PRIMARY);
        var colors = panel.render();
        var retiredColor = entry(colors,"color:window").trigger(FloatingMenuInteraction.PRIMARY);
        var oldBack = entry(colors,"back").trigger(FloatingMenuInteraction.PRIMARY);
        retiredTheme.execute(null,null,FloatingMenuInteraction.PRIMARY);
        assertInstanceOf(UiCustomTheme.class,preferences.get().theme());
        invoke(colors,"back",FloatingMenuInteraction.PRIMARY);
        assertTrue(entry(panel.render(),"theme:custom").selected());
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

    private static final class NativeForm implements FloatingMenuSettingsPanel.FormFactory {
        MenuInputForm form;
        Runnable finished;
        int closes;
        String focused;
        @Override public MenuFormSession open(Player player, MenuInputForm form, Runnable changed, Runnable finished) {
            this.form = form; this.finished = finished;
            return new MenuFormSession() {
                @Override public UiNode content(Actions actions) { return Ui.column().build(); }
                @Override public void focus(String field) { focused = field; }
                @Override public void close() { closes++; finished.run(); }
            };
        }
        void submit(java.util.Map<String,String> values) {
            // Native field editing releases its display before the form delivers the accepted values.
            finished.run(); form.onSubmit().accept(null, new MenuInputForm.Values(values));
        }
    }

    @Test void nativeSubmissionsCommitOnceAfterDisplayRetirementAndUseTheLatestIndependentPreferences() {
        var preferences = new AtomicReference<>(FloatingMenuPreferences.DEFAULT.withTheme(UiCustomTheme.copyOf(UiPalette.WARM)));
        var writes = new AtomicInteger(); var forms = new NativeForm();
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
        assertEquals(91,preferences.get().fieldOfView().degrees()); assertEquals(2,writes.get());
        assertEquals(FloatingMenuTextScale.MAXIMUM,preferences.get().text());
        panel.close();
    }

    @Test void ownerSuspensionAndClosingInvalidateNativeResultsAndRetireTheirSessionOnce() {
        for (var state : List.of(FloatingMenuState.SUSPENDED,FloatingMenuState.CLOSING,FloatingMenuState.CLOSED)) {
            var preferences = new AtomicReference<>(FloatingMenuPreferences.DEFAULT); var forms = new NativeForm();
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

    private static void allPages(Supplier<MenuScene> render, String collection, double typography,
                                 java.util.function.Consumer<UiFrame> check, Function<Message,String> text) {
        int count = 1;
        for (int index = 0; index < count; index++) {
            var scene = render.get(); var frame = SceneGeometryTestSupport.measure(scene,typography);
            var hosted = SettingsMenuTestSupport.frame(scene,text,"INTERFACE");
            var embedded = SceneGeometryTestSupport.measure(hosted,typography);
            assertDisjoint(frame); assertDisjoint(embedded); check.accept(frame);
            if (collection == null) continue;
            var paging = paging(frame,collection); count = paging.page().count();
            assertEquals(index,paging.page().index());
            if (paging.page().hasNext()) paging.selectPage().accept(paging.page().next().index());
        }
    }
    private static UiFrame.Pagination paging(UiFrame frame, String key) {
        return frame.pagination().stream().filter(value -> value.path().endsWith("/" + key)).findFirst().orElseThrow();
    }
    private static void invoke(MenuScene scene, String key, FloatingMenuInteraction interaction) {
        var target = entry(scene,key); assertNotNull(target,key); target.trigger(interaction).execute(null,null,interaction);
    }
}
