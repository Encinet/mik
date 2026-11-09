package org.encinet.mik.feature.entertainment.music.ui;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MusicGroupContentTest {
    @Test void allMembersAndLocalActionsRemainReachableInEveryLanguageAndReadingBoundary() throws Exception {
        for (var locale : Language.values()) {
            var bundle = FluentTestSupport.bundle(locale);
            var label = new java.util.function.Function<String,Component>() {
                public Component apply(String key) { return Component.text(FluentTestSupport.text(bundle,Message.MUSIC_SPACE_LABEL,key)); }
            };
            for (double font : new double[]{.7,1.8}) for (double scale : new double[]{.7,1.3}) for (int fov : new int[]{30,110}) {
                var position = new AtomicReference<>(MusicSourcePosition.START); var seen = new HashSet<UUID>();
                var invoked = new ArrayList<String>();
                var cards = new ArrayList<UiNode>();
                for (int index = 1; index <= 16; index++) {
                    var id = new UUID(0,index); var actions = new ArrayList<UiNode>();
                    for (String key : List.of("sound","remove-member")) actions.add(Ui.button(label.apply(key)).key(key)
                            .primary((_,_) -> invoked.add(id + ":" + key)).build());
                    cards.add(MusicGroupContent.member(id,Component.text("Living room 客厅 · " + index),label.apply("available"),
                            Component.text("Front right · " + index + " blocks"),Component.text("world · X -29999999 · Y -64 · Z 29999999"),
                            Ui.button(label.apply("visit-device")).key("visit").noWrap().primary((_,_) -> invoked.add(id + ":visit")).build(),actions));
                }
                for (int pass = 0; pass < cards.size(); pass++) {
                    var content = MusicGroupContent.members(cards,label.apply("empty"),position.get(),position::set);
                    var frame = measure(content,font,fov,scale); assertDisjoint(frame); assertTrue(invoked.isEmpty());
                    for (var item : frame.elements()) if (item.node().key().value().equals("name")) {
                        UUID id = identity(item); assertTrue(seen.add(id),locale + " repeated member " + id);
                        for (String key : List.of("visit","sound","remove-member")) {
                            var action = frame.elements().stream().filter(candidate -> candidate.node().key().value().equals(key)
                                    && identity(candidate).equals(id)).findFirst().orElseThrow();
                            action.node().input().actions().get(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
                            assertEquals(List.of(id + ":" + key),invoked); invoked.clear();
                        }
                    }
                    assertNotNull(element(frame,"add-members")); assertNotNull(element(frame,"close-panel"));
                    var page = frame.pagination().getFirst();
                    if (!page.page().hasNext()) break;
                    page.selectPage().accept(page.page().index() + 1);
                }
                assertEquals(cards.size(),seen.size(),locale + " " + font + " " + fov + " " + scale);
                var bookmark = position.get(); Collections.reverse(cards);
                var restored = MusicGroupContent.members(cards,label.apply("empty"),bookmark,_ -> {}); cards.clear();
                assertTrue(measure(restored,font,fov,scale).elements().stream().filter(item -> item.node().key().value().equals("name"))
                        .anyMatch(item -> UiKey.item(identity(item).toString()).equals(bookmark.anchor())));
            }
        }
    }

    @Test void oversizedNamesAndLocationsUseAtomicCompactCardsWithCompleteHelpAndActions() {
        var name = Component.text("Very long room name 客厅 ".repeat(60));
        var coordinates = Component.text("Very long world 世界 ".repeat(60) + " · X -29999999 · Y -64 · Z 29999999");
        var card = MusicGroupContent.member(new UUID(0,1),name,Component.text("Available"),coordinates,coordinates,
                Ui.button(Component.text("Visit")).key("visit").noWrap().build(),
                List.of(Ui.button(Component.text("Sound")).key("sound").build(),Ui.button(Component.text("Remove")).key("remove-member").build()));
        var frame = measure(MusicGroupContent.members(List.of(card),Component.text("Empty"),MusicSourcePosition.START,_ -> {}),1.8,30,1.3);
        assertDisjoint(frame);
        assertEquals(name.append(Component.text(" · ")).append(Component.text("Available")),element(frame,"name").node().input().tooltip().orElseThrow().text());
        for (String key : List.of("visit","sound","remove-member")) assertNotNull(element(frame,key));
        var empty = measure(MusicGroupContent.members(List.of(),Component.text("No members"),MusicSourcePosition.START,_ -> fail()),1.8,30,1.3);
        assertNotNull(element(empty,"empty")); assertTrue(empty.pagination().isEmpty()); assertDisjoint(empty);
    }

    private static UUID identity(UiFrame.Element item) {
        var match = java.util.regex.Pattern.compile("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}").matcher(item.path());
        UUID id = null; while (match.find()) id = UUID.fromString(match.group());
        return Objects.requireNonNull(id,item.path());
    }
    private static UiFrame measure(UiNode content, double font, int fov, double scale) {
        var task = MusicWorkspace.column("member-task").child(MusicWorkspaceComposition.panelHeading(Component.text("Group"),
                        Ui.button(UiIcons.CROSS,Component.text("Close")).key("close-panel").build()))
                .child(Ui.button(Component.text("Add members")).key("add-members").build()).child(content).build();
        return SceneGeometryTestSupport.readableFrame(MusicSceneTestSupport.auxiliary(task,(int) MusicWorkspaceComposition.VIEWPORT.width()),
                font,new FloatingMenuFieldOfView(fov),scale);
    }
}
