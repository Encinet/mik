package org.encinet.mik.feature.player.profile.nametag;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.quickwrite.fluent4j.container.FluentBundle;
import org.encinet.mik.shared.i18n.FluentTestSupport;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.FloatingMenuInteraction;
import org.encinet.mik.shared.menu.FloatingMenuTextWidth;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.UiSceneContent;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.encinet.mik.shell.menu.SettingsMenuTestSupport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class NameTagCompositionTest {
    private enum State { BROWSING, EDITING }

    @Test void stylesFieldControlsAndAllTranslationsFitAtEveryTextPreference() throws IOException {
        for (Language language : Language.values()) {
            FluentBundle bundle = FluentTestSupport.bundle(language);
            for (double scale : List.of(.7, 1.0, 1.4, 1.8)) for (State state : State.values()) {
                MenuScene scene = scene(bundle, state);
                var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.measure(scene, scale),
                        language.id() + " scale=" + scale + " state=" + state);
                assertDisjoint(frame);
                assertFullTextAvailable(entry(scene,"copy-style").label(),element(frame,"copy-style"));
                var hosted = SettingsMenuTestSupport.frame(scene, message -> FluentTestSupport.text(bundle, message), "NAME_TAG");
                var hostedFrame = assertDoesNotThrow(() -> SceneGeometryTestSupport.measure(hosted, scale),
                        language.id() + " settings scale=" + scale + " state=" + state);
                assertDisjoint(hostedFrame);
                assertFullTextAvailable(entry(hosted,"copy-style").label(),element(hostedFrame,"copy-style"));
                if (state == State.EDITING) {
                    assertNotNull(element(frame, "confirm"));
                    assertNotNull(element(frame, "cancel"));
                    assertTrue(entry(scene, "confirm").enabled());
                }
            }
        }
    }

    @Test void clickingAStyleCopiesItDirectlyAndReorderingKeepsItsUuidBinding() throws IOException {
        FluentBundle bundle = FluentTestSupport.bundle(Language.DEFAULT);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var rows = List.of(new NameTagComposition.Row(a, Component.text("A")), new NameTagComposition.Row(b, Component.text("B")));
        var copies = new ArrayList<UUID>();
        MenuScene before = galleryScene(bundle, rows, copies);
        MenuScene after = galleryScene(bundle, rows.reversed(), copies);
        assertEquals(before.entries().keySet(), after.entries().keySet());
        assertTrue(copies.isEmpty());
        trigger(after, a, "copy-style");
        assertEquals(List.of(a), copies);
        trigger(after, b, "copy-style");
        assertEquals(List.of(a, b), copies);
        assertEquals(rows.size(), after.entries().size(), "Each player style has a single copy action");
        assertTrue(UiSceneContent.elements(after.content()).values().stream().noneMatch(node -> node.key().value().equals("inspect")),
                "there is no separate player-name selection control");
    }

    @Test void bothFieldsAndTheDraftConfirmationRemainVisibleAcrossReadingPreferences() throws Exception {
        var bundle = FluentTestSupport.bundle(Language.EN_US);
        for (double typography : new double[]{.7,1.8})
            for (int degrees : new int[]{30,110}) for (double scale : new double[]{.7,1.3}) {
                var scene = scene(bundle,State.EDITING);
                var frame = SceneGeometryTestSupport.readableFrame(scene,typography,
                        new org.encinet.mik.shared.menu.FloatingMenuFieldOfView(degrees),scale);
                assertDisjoint(frame);
                assertTrue(frame.readingRegion().path().contains("/field-editor"));
                assertEquals(NameTagField.values().length,frame.elements().stream().filter(element ->
                        element.node().key().value().equals("preview") && element.node().role().supportsActions()).count());
                assertNotNull(element(frame,"confirm"));
                assertNotNull(element(frame,"cancel"));
                assertTrue(entry(scene,"confirm").enabled());
            }
    }

    private MenuScene galleryScene(FluentBundle bundle, List<NameTagComposition.Row> rows, List<UUID> copies) {
        var scene = MenuScene.screen("fixture").content(NameTagComposition.gallery(rows,
                label(bundle, Message.NAMETAG_COPY_HINT),
                id -> (_, _) -> copies.add(id), 0, _ -> Ui.column().build(), _ -> { })).build();
        return scene.measured(SceneGeometryTestSupport.measure(scene, 1));
    }

    private void trigger(MenuScene scene, UUID id, String action) {
        String path = scene.entries().keySet().stream().filter(key -> key.contains(id.toString())
                && key.endsWith("/" + action)).findFirst().orElseThrow();
        scene.entries().get(path).trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
    }

    private MenuScene scene(FluentBundle bundle, State state) {
        String raw = "<gradient:red:blue>" + "长名字😀".repeat(40) + "</gradient>";
        Component effect = MiniMessage.miniMessage().deserialize(raw);
        var fields = Ui.form().key("fields");
        for (NameTagField field : NameTagField.values()) {
            var input = Ui.textField(raw).preview(_ -> effect).key("value").editing(state == State.EDITING && field == NameTagField.PREFIX)
                    .primary((_, _) -> { });
            var clear = Ui.button(label(bundle, Message.NAMETAG_CLEAR)).key("clear").primary((_, _) -> { });
            if (state == State.EDITING && field == NameTagField.SUFFIX) {
                var reason = label(bundle, Message.NAMETAG_FINISH_FIELD);
                input.disabled(reason);
                clear.disabled(reason);
            }
            fields.field(field.name().toLowerCase(java.util.Locale.ROOT), label(bundle, field == NameTagField.PREFIX
                    ? Message.NAMETAG_LEADING_LABEL : Message.NAMETAG_TRAILING_LABEL),
                    input.actions(clear.build()).build());
        }
        var rows = new ArrayList<NameTagComposition.Row>();
        for (int index = 0; index < 6; index++) rows.add(new NameTagComposition.Row(new UUID(0, index), effect));
        var gallery = NameTagComposition.gallery(rows, label(bundle, Message.NAMETAG_COPY_HINT), _ -> (_, _) -> { }, 0,
                page -> MenuScene.screen("nametag-pages").content(UiTemplates.pagination("pages"))
                        .pagination("pages", page, label(bundle, Message.NAMETAG_PREVIOUS),
                                label(bundle, Message.NAMETAG_PAGE, page.index() + 1, page.count()),
                                label(bundle, Message.NAMETAG_NEXT), _ -> { }).build().content(), _ -> { });
        var menu = MenuScene.screen("fixture").content(NameTagComposition.content(fields.build(), gallery, Ui.button(UiIcons.REFRESH, label(bundle, Message.NAMETAG_REFRESH)).key("refresh").build()));
        menu.information("mine", label(bundle, Message.NAMETAG_MINE)).region("heading");
        menu.information("current", label(bundle, Message.NAMETAG_CURRENT).append(Component.newline()).append(effect)).region("current");
        if (state != State.BROWSING) {
            menu.control("confirm",label(bundle,Message.NAMETAG_CONFIRM)).region("edit-actions").primary((_,_) -> {});
            menu.control("cancel",label(bundle,Message.CANCEL)).region("edit-actions").primary((_,_) -> {});
            menu.information("preview", label(bundle, Message.NAMETAG_PREVIEW_LABEL).append(Component.newline()).append(effect)).region("current");
            menu.information("input-status", label(bundle, Message.NAMETAG_EDIT_PROMPT,
                    FluentTestSupport.text(bundle, Message.NAMETAG_LEADING_LABEL))).region("edit-status").textWidth(FloatingMenuTextWidth.EXPANDED);
            menu.information("notice", label(bundle, Message.NAMETAG_CONFLICT)).region("edit-status").textWidth(FloatingMenuTextWidth.EXPANDED);
        }
        menu.information("players", label(bundle, Message.NAMETAG_PLAYERS)).region("players-heading");
        menu.information("load-failed", label(bundle, Message.NAMETAG_STYLES_FAILED)).region("gallery-status");
        menu.back(label(bundle, Message.BACK_TO_MAIN)).region("navigation");
        return menu.build();
    }

    private Component label(FluentBundle bundle, Message message, Object... args) {
        return Component.text(FluentTestSupport.text(bundle, message, args));
    }
}
