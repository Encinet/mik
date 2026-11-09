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

class MusicTaskEntriesTest {
    @Test void everyHistoryEntryUsesItsOwnActionAndOnlyVisibleRowsBuildCommands() {
        var source = rows(300, Component.text("Playlist"));
        for (double font : new double[]{.7, 1.8}) for (int fov : new int[]{30, 110}) {
            var position = new AtomicReference<>(MusicSourcePosition.START);
            var seen = new HashSet<String>(); var invoked = new ArrayList<Integer>();
            for (int pass = 0; pass < source.size(); pass++) {
                var constructed = new HashSet<Integer>();
                var content = MusicTaskEntries.content("history", source, Component.text("Empty"), position.get(), position::set,
                        value -> { constructed.add(value); return (_,_) -> invoked.add(value); });
                var frame = measure(content, font, fov, 1.3); assertDisjoint(frame);
                assertTrue(invoked.isEmpty()); assertTrue(constructed.size() <= UiWindowedList.MAX_VISIBLE_ITEMS);
                for (var item : frame.elements()) if (item.node().key().value().startsWith("entry:")) {
                    String key = item.node().key().value(); assertTrue(seen.add(key));
                    int index = Integer.parseInt(key.substring("entry:".length()));
                    assertEquals(source.get(index).help(), item.node().input().tooltip().orElseThrow().text());
                    item.node().input().actions().get(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
                    assertEquals(List.of(index), invoked); invoked.clear();
                }
                var page = frame.pagination().getFirst();
                if (!page.page().hasNext()) break;
                page.selectPage().accept(page.page().index() + 1);
                assertEquals(page.page().toIndex(), position.get().index());
            }
            assertEquals(source.size(), seen.size());
            var reordered = new ArrayList<>(source); Collections.reverse(reordered);
            var bookmark = position.get();
            var content = MusicTaskEntries.content("history", reordered, Component.text("Empty"), bookmark, _ -> {}, _ -> (_,_) -> {});
            reordered.clear();
            assertTrue(measure(content,font,fov,1.3).elements().stream().anyMatch(item -> item.node().key().equals(bookmark.anchor())));
        }
    }

    @Test void previewsRemainReadOnlyAndKeepDuplicateValuesAtAllLanguageAndReadingBoundaries() throws Exception {
        for (var locale : Language.values()) {
            var bundle = FluentTestSupport.bundle(locale);
            java.util.function.Function<String,Component> labels = key -> Component.text(FluentTestSupport.text(bundle,Message.MUSIC_SPACE_LABEL,key));
            var label = labels.apply("import-preview");
            // Imported tracks can occur repeatedly; occurrence keys, rather than track values, own rows.
            var rows = rows(7,label).stream().map(row -> new MusicTaskEntries.Row<>(row.key(),0,row.label(),row.help(),false,null)).toList();
            for (double font : new double[]{.7,1.8}) for (double scale : new double[]{.7,1.3}) for (int fov : new int[]{30,110}) {
                var position = new AtomicReference<>(MusicSourcePosition.START); var seen = new HashSet<UiKey>();
                for (int pass = 0; pass < rows.size(); pass++) {
                    var frame = measure(MusicTaskEntries.content("preview",rows,label,position.get(),position::set,null),labels,font,fov,scale);
                    assertDisjoint(frame);
                    for (String key : List.of("import-name","import-new","import-view","retry-import","close-panel"))
                        assertTrue(frame.elements().stream().anyMatch(item -> item.path().contains("/" + key)),locale + " missing " + key);
                    for (var item : frame.elements()) if (item.node().key().value().startsWith("entry:")) {
                        assertTrue(seen.add(item.node().key()), locale.toString());
                        assertFalse(item.node().input().interactive()); assertTrue(item.node().input().actions().isEmpty());
                    }
                    var page = frame.pagination().getFirst();
                    if (!page.page().hasNext()) break;
                    page.selectPage().accept(page.page().index() + 1);
                }
                assertEquals(rows.size(),seen.size(),locale + " " + font + " " + fov + " " + scale);
            }
        }
    }

    @Test void sourceSelectionAndBusyRestrictionsSurviveMeasurementAndEmptyListsDoNotBuildActions() {
        var help = Component.text("World and full source title");
        var selected = new MusicTaskEntries.Row<>(UiKey.item("entry:0"),0,Component.text("Source"),help,true,Component.text("Saving"));
        var frame = measure(MusicTaskEntries.content("sources",List.of(selected),help,MusicSourcePosition.START,_ -> fail(),
                _ -> (_,_) -> fail("Measurement must not submit")),1.8,30,1.3);
        var item = element(frame,"entry:0");
        assertTrue(item.node().input().selected()); assertEquals(Component.text("Saving"),item.node().input().disabledReason().orElseThrow());
        var empty = measure(MusicTaskEntries.content("history",List.of(),help,MusicSourcePosition.START,_ -> fail(),
                _ -> { fail("Empty source cannot build commands"); return null; }),1.8,30,1.3);
        assertTrue(empty.pagination().isEmpty()); assertNotNull(element(empty,"empty")); assertDisjoint(empty);
    }

    private static List<MusicTaskEntries.Row<Integer>> rows(int count, Component title) {
        var rows = new ArrayList<MusicTaskEntries.Row<Integer>>();
        for (int index = 0; index < count; index++) {
            var name = title.append(Component.text(" " + index + " · 完整名称"));
            rows.add(new MusicTaskEntries.Row<>(UiKey.item("entry:" + index),index,name,name,false,null));
        }
        return rows;
    }
    private static UiFrame measure(UiNode content, double font, int fov, double scale) {
        return measure(content,Component::text,font,fov,scale);
    }
    private static UiFrame measure(UiNode content, java.util.function.Function<String,Component> label, double font, int fov, double scale) {
        var task = MusicWorkspace.column("import-task").child(MusicWorkspaceComposition.panelHeading(label.apply("import"),
                        Ui.button(UiIcons.CROSS,label.apply("close-panel")).key("close-panel").build()))
                .child(Ui.form().key("import-name").field("value",label.apply("name"),
                        Ui.textField("My imported playlist 名称").key("value").maxLines(1).build()).build())
                .child(Ui.text(label.apply("partial-import")).key("partial").build())
                .child(content).child(MusicWorkspace.tools("task-actions").child(Ui.button(label.apply("import-new"))
                        .key("import-new").build()).child(Ui.button(label.apply("import-merge")).key("import-view").build())
                        .child(Ui.button(label.apply("retry-import")).key("retry-import").build()).build()).build();
        return SceneGeometryTestSupport.readableFrame(MusicSceneTestSupport.auxiliary(UiTemplates.reading(task,content.key(),UiLength.ui(8)),(int) MusicWorkspaceComposition.VIEWPORT.width()),
                font,new FloatingMenuFieldOfView(fov),scale);
    }
}
