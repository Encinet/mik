package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** Owns detailed editors and native drafts independently from the persisted interface preferences. */
final class FloatingMenuSettingsPanel implements MenuPanel {
    private enum Mode { PREFERENCES, FOV, THEME }
    @FunctionalInterface
    interface FormFactory {
        MenuFormSession open(Player player, MenuInputForm form, Runnable changed, Runnable finished);
    }
    private final Player player;
    private final LanguageService language;
    private final Supplier<FloatingMenuPreferences> preferences;
    private final Consumer<FloatingMenuPreferences> apply;
    private final Runnable redraw;
    private final FormFactory forms;
    private Mode mode = Mode.PREFERENCES;
    private Object scope = new Object(), inputToken;
    private int fovPage, colorPage;
    private boolean closed;
    private MenuFormSession input;

    FloatingMenuSettingsPanel(Player player, LanguageService language, Supplier<FloatingMenuPreferences> preferences,
                              Consumer<FloatingMenuPreferences> apply, Runnable redraw) {
        this(player, language, preferences, apply, redraw, FloatingMenus::inlineInput);
    }

    FloatingMenuSettingsPanel(Player player, LanguageService language, Supplier<FloatingMenuPreferences> preferences,
                              Consumer<FloatingMenuPreferences> apply, Runnable redraw, FormFactory forms) {
        this.player = player; this.language = language; this.preferences = preferences; this.apply = apply; this.redraw = redraw;
        this.forms = java.util.Objects.requireNonNull(forms);
    }

    @Override public MenuScene render() {
        if (closed) return MenuScene.screen("interface-closed").content(Ui.column().build()).build();
        var selected = preferences.get();
        if (mode == Mode.THEME && !(selected.theme() instanceof UiCustomTheme)) {
            endInput(); mode = Mode.PREFERENCES;
        }
        Object owner = scope;
        MenuScene scene;
        if (input != null) scene = MenuScene.screen("interface-input").content(input.content()).build();
        else if (mode == Mode.THEME) scene = FloatingMenuThemeEditor.definition((UiCustomTheme) selected.theme(),message -> language.t(player,message),
                colorPage,index -> { if (active(owner,Mode.THEME)) { colorPage = index; redraw.run(); } },
                (_,role) -> { if (active(owner,Mode.THEME)) editThemeColor(role); },
                _ -> { if (active(owner,Mode.THEME)) navigate(Mode.PREFERENCES); });
        else if (mode == Mode.FOV) scene = FloatingMenuSettingsMenu.fieldOfViewDefinition(selected,message -> language.t(player,message),fovPage,
                index -> { if (active(owner,Mode.FOV)) { fovPage = index; redraw.run(); } },
                (_,change) -> { if (active(owner,Mode.FOV)) update(change); },
                _ -> { if (active(owner,Mode.FOV)) editFieldOfView(); },
                _ -> { if (active(owner,Mode.FOV)) navigate(Mode.PREFERENCES); });
        else {
            scene = FloatingMenuSettingsMenu.definition(selected,message -> language.t(player,message),new FloatingMenuSettingsMenu.Actions(
                    (_,change) -> { if (active(owner,Mode.PREFERENCES)) update(change); },
                    _ -> { if (active(owner,Mode.PREFERENCES) && preferences.get().theme() instanceof UiCustomTheme) navigate(Mode.THEME); },
                    _ -> { if (active(owner,Mode.PREFERENCES)) navigate(Mode.FOV); },
                    _ -> { if (active(owner,Mode.PREFERENCES)) update(current -> current.withLayout(FloatingMenuScale.NORMAL).withText(FloatingMenuTextScale.NORMAL)); }));
        }
        return scene.toBuilder().lifecycle((_,_,previous,current,_) -> {
            if (current == FloatingMenuState.SUSPENDED || current == FloatingMenuState.CLOSING || current == FloatingMenuState.CLOSED) endInput();
            else if (current == FloatingMenuState.OPENING && previous == FloatingMenuState.SUSPENDED) redraw.run();
        }).build();
    }

    private boolean active(Object owner, Mode expected) { return !closed && scope == owner && mode == expected && input == null; }
    private void navigate(Mode next) { mode = next; endInput(); redraw.run(); }
    private void update(UnaryOperator<FloatingMenuPreferences> change) {
        if (closed) return;
        apply.accept(java.util.Objects.requireNonNull(change.apply(preferences.get()))); redraw.run();
    }

    private void editThemeColor(UiColorRole role) {
        if (closed || input != null || mode != Mode.THEME || !(preferences.get().theme() instanceof UiCustomTheme custom)) return;
        Object token = new Object();
        var form = FloatingMenuThemeEditor.input(custom,role,message -> language.t(player,message),(_,color) -> {
            if (!acceptInput(token,Mode.THEME)) return;
            update(current -> current.theme() instanceof UiCustomTheme theme
                    ? current.withTheme(theme.withColor(role,color.rgb(),color.alpha())) : current);
        });
        openInput(form,token);
    }

    private void editFieldOfView() {
        if (closed || input != null || mode != Mode.FOV) return;
        Object token = new Object();
        var form = MenuInputForm.builder(Component.text(language.t(player,Message.INTERFACE_FOV_TITLE)))
                .field(MenuInputForm.Field.text("degrees",Component.text(language.t(player,Message.INTERFACE_FOV_INPUT)),
                        Integer.toString(preferences.get().fieldOfView().degrees()),3))
                .validate((viewer,values) -> {
                    try { new FloatingMenuFieldOfView(Integer.parseInt(values.text("degrees").strip())); return null; }
                    catch (IllegalArgumentException error) { return Component.text(language.t(viewer,Message.INTERFACE_FOV_INVALID),NamedTextColor.RED); }
                }).cancel(Component.text(language.t(player,Message.BACK)))
                .submit(Component.text(language.t(player,Message.INTERFACE_FOV_APPLY)),(_,values) -> {
                    if (acceptInput(token,Mode.FOV)) update(current -> current.withFieldOfView(
                            new FloatingMenuFieldOfView(Integer.parseInt(values.text("degrees").strip()))));
                }).build();
        openInput(form,token);
    }

    private boolean ownsInput(Object token, Mode expected) { return !closed && inputToken == token && mode == expected; }
    private boolean acceptInput(Object token, Mode expected) {
        if (!ownsInput(token,expected)) return false;
        inputToken = null;
        return true;
    }
    private void openInput(MenuInputForm form, Object token) {
        Mode origin = mode;
        inputToken = token; scope = new Object();
        input = forms.open(player,form,redraw,() -> {
            if (!ownsInput(token,origin)) return;
            // The editor retires its display before delivering submission; the same token may still commit once.
            input = null; scope = new Object(); redraw.run();
        });
        input.focus(form.fields().getFirst().key());
    }
    private void endInput() {
        inputToken = null; scope = new Object();
        MenuFormSession previous = input; input = null;
        if (previous != null) previous.close();
    }
    @Override public void close() { closed = true; endInput(); }
}
