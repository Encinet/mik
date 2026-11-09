package org.encinet.mik.feature.world.plot;

import static org.encinet.mik.feature.world.plot.PlotDestinationTestSupport.*;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlotDestinationMenusTest {
    @TempDir Path directory;

    @Test void titleDraftPreviewDoesNotSaveAndItsScreenKeepsTheCentreClear() throws Exception {
        try (var store = new PlotRepository(directory.resolve("title-editor.db"))) {
            store.open(); var registry = new PlotRegistry(store);
            UUID owner = UUID.randomUUID();
            var plot = new Plot(UUID.randomUUID(),UUID.randomUUID(),owner,"Park",0,
                    Set.of(new PlotGeometry.Cell(0,16,0)),Map.of(),Map.of(),null);
            registry.put(plot);
            var point = new PlotDestination(UUID.randomUUID(),plot.id(),"Gate","Description",false,1,65,1,0,0,"","");
            registry.saveDestination(point);
            var titles = new java.util.ArrayList<net.kyori.adventure.title.Title>();
            var player = proxy(Player.class,(name,args) -> switch (name) {
                case "getUniqueId" -> owner;
                case "showTitle" -> { titles.add((net.kyori.adventure.title.Title)args[0]); yield null; }
                default -> null;
            });
            var controller = new PlotDestinationController(null,language(),registry,Runnable::run,_ -> null);
            var menus = new PlotDestinationMenus(null,language(),registry,controller);
            var empty = menus.titleEditor(player,plot,point,point);
            assertFalse(entry(empty,"preview").enabled()); assertFalse(entry(empty,"save").enabled());
            var draft = point.titles("<gold>Welcome</gold>","<italic>Enjoy</italic>");
            var scene = menus.titleEditor(player,plot,point,draft);
            assertTrue(entry(scene,"preview").enabled()); assertTrue(entry(scene,"save").enabled());
            assertFalse(PlainTextComponentSerializer.plainText().serialize(entry(scene,"title").label()).contains("<gold>"));
            var children = new java.util.ArrayList<MenuScene>();
            var handle = proxy(FloatingMenuHandle.class,(name,args) -> { if (name.equals("openChild")) children.add((MenuScene)args[0]); return null; });
            entry(scene,"preview").trigger(FloatingMenuInteraction.PRIMARY).execute(player,handle,FloatingMenuInteraction.PRIMARY);
            assertEquals(1,titles.size()); assertEquals(1,children.size()); assertEquals(point,registry.destination(point.id()));
            assertEquals(1,children.getFirst().entries().size()); // Only Back occupies the preview's footer.
            for (var scale : FloatingMenuTextScale.values()) {
                var frame = SceneGeometryTestSupport.measure(children.getFirst(), scale.factor());
                assertDisjoint(frame); assertTrue(element(frame,"back").y() >= 240);
            }
            registry.saveDestination(point.details("Changed", "Description"));
            var stale = menus.titleEditor(player,plot,point,draft);
            assertFalse(entry(stale,"save").enabled()); assertFalse(entry(stale,"preview").enabled());
            assertFalse(entry(stale,"title").enabled());
            controller.close();
        }
    }

    @Test void destinationManagementAndTitleEditorFitAllLanguagesAndTextSizes() throws Exception {
        try (var store = new PlotRepository(directory.resolve("title-layout.db"))) {
            store.open(); var registry = new PlotRegistry(store);
            UUID owner = UUID.randomUUID();
            var plot = new Plot(UUID.randomUUID(),UUID.randomUUID(),owner,"庭院与地下铁路连接中心 A long plot name",0,
                    Set.of(new PlotGeometry.Cell(0,16,0)),Map.of(),Map.of(),null);
            registry.put(plot);
            var point = new PlotDestination(UUID.randomUUID(),plot.id(),"传送点名字 A long destination name", "多行介绍\n".repeat(80),
                    false,1,65,1,0,0,"","");
            registry.saveDestination(point);
            var player = proxy(Player.class,(name,_) -> switch (name) {
                case "getUniqueId" -> owner;
                case "getLocation" -> new org.bukkit.Location(null,1,65,1);
                default -> null;
            });
            for (var locale : org.encinet.mik.shared.i18n.Language.values()) {
                var language = language(locale);
                var controller = new PlotDestinationController(null,language,registry,Runnable::run,_ -> null);
                var menus = new PlotDestinationMenus(null,language,registry,controller);
                var draft = point.titles("<gradient:red:gold>"+"庭".repeat(32)+"</gradient>","<bold>"+"院".repeat(64)+"</bold>");
                for (var scale : FloatingMenuTextScale.values()) {
                    var detail = menus.detail(player,plot,point);
                    var editor = menus.titleEditor(player,plot,point,draft);
                    assertEquals("plot-destination", detail.screenId());
                    assertEquals("plot-destination-titles", editor.screenId());
                    assertDisjoint(assertDoesNotThrow(() -> SceneGeometryTestSupport.measure(detail,scale.factor()),locale+" "+scale+" detail"));
                    assertDisjoint(assertDoesNotThrow(() -> SceneGeometryTestSupport.measure(editor,scale.factor()),locale+" "+scale+" title editor"));
                    assertNotNull(entry(detail,"description").tooltip());
                }
                controller.close();
            }
        }
    }

    @Test void publicPopoverPagesPointsAndTracksVisibilityChangesWithoutLeavingItsHost() throws Exception {
        try (var store = new PlotRepository(directory.resolve("public-list.db"))) {
            store.open(); var registry = new PlotRegistry(store);
            var plot = new Plot(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"Park",0,
                    Set.of(new PlotGeometry.Cell(0,16,0)),Map.of(),Map.of(),null);
            registry.put(plot);
            var first = new PlotDestination(UUID.randomUUID(),plot.id(),"A gate","Hello",true,1,65,1,0,0, "", "");
            registry.saveDestination(first);
            for (int index=0; index<30; index++) registry.saveDestination(new PlotDestination(UUID.randomUUID(),plot.id(),"Point "+index,
                    "Introduction\n"+"多行介绍\n".repeat(60),true,1,65,1,0,0, "", ""));
            var hidden = new PlotDestination(UUID.randomUUID(),plot.id(),"Hidden","Secret",false,1,65,1,0,0, "", "");
            registry.saveDestination(hidden);
            var loads = new AtomicInteger();
            var targetWorld = proxy(org.bukkit.World.class, (name,_) -> {
                if (name.equals("getChunkAtAsync")) { loads.incrementAndGet(); return new java.util.concurrent.CompletableFuture<>(); }
                return null;
            });
            var controller = new PlotDestinationController(null,language(),registry,Runnable::run,_ -> targetWorld);
            var menus = new PlotDestinationMenus(null,language(),registry,controller);
            var redraws = new AtomicInteger();
            UUID viewerId = UUID.randomUUID();
            var player = proxy(Player.class,(name,_) -> name.equals("getUniqueId") ? viewerId : null);
            var opened = new java.util.ArrayList<UUID>();
            var panel = menus.publicPanel(player,redraws::incrementAndGet,(_,id) -> opened.add(id));
            var scene = panel.render();
            var firstFrame = SceneGeometryTestSupport.measure(scene,1);
            var firstPage = firstFrame.pagination().getFirst().page();
            assertTrue(firstPage.hasNext());
            assertEquals(firstPage.toIndex() - firstPage.fromIndex(),firstFrame.elements().stream()
                    .filter(value -> value.node().key().value().startsWith("point:")).count());
            assertNotNull(entry(scene,firstFrame,"point:"+first.id())); assertNull(entry(scene,firstFrame,"point:"+hidden.id()));
            String label = PlainTextComponentSerializer.plainText().serialize(entry(scene,firstFrame,"point:"+first.id()).tooltip().text());
            assertTrue(label.contains("A gate")); assertTrue(label.contains("Park")); assertTrue(label.contains("Hello"));
            var closes = new AtomicInteger();
            var host = proxy(FloatingMenuHandle.class,(name,_) -> { if (name.equals("close")) closes.incrementAndGet(); return null; });
            entry(scene,firstFrame,"read:"+first.id()).trigger(FloatingMenuInteraction.PRIMARY).execute(player,host,FloatingMenuInteraction.PRIMARY);
            assertEquals(java.util.List.of(first.id()),opened); assertEquals(0,loads.get()); assertEquals(0,closes.get());
            entry(scene,firstFrame,"point:"+first.id()).trigger(FloatingMenuInteraction.PRIMARY).execute(player,host,FloatingMenuInteraction.PRIMARY);
            assertEquals(1,loads.get()); assertEquals(1,closes.get());
            for (var scale : FloatingMenuTextScale.values()) assertDisjoint(SceneGeometryTestSupport.measure(scene,scale.factor()));
            firstFrame.pagination().getFirst().selectPage().accept(firstPage.next().index());
            assertEquals(1,redraws.get());
            var secondPage = panel.render();
            var secondFrame = SceneGeometryTestSupport.measure(secondPage,1);
            assertNull(entry(secondPage,secondFrame,"point:"+first.id())); assertNotNull(entry(secondPage,secondFrame,"previous"));
            firstFrame.pagination().getFirst().selectPage().accept(0);
            assertEquals(1,redraws.get(),"A superseded page must not replace the current page");
            for (var point : registry.publicDestinations()) registry.saveDestination(point.published(false));
            var empty = panel.render();
            var emptyFrame = SceneGeometryTestSupport.measure(empty,1);
            assertNotNull(entry(empty,emptyFrame,"empty")); assertNull(entry(empty,emptyFrame,"previous"));
            panel.close();
            secondFrame.pagination().getFirst().selectPage().accept(0);
            entry(scene,firstFrame,"point:"+first.id()).trigger(FloatingMenuInteraction.PRIMARY).execute(player,host,FloatingMenuInteraction.PRIMARY);
            assertEquals(1,redraws.get()); assertEquals(1,loads.get());
            entry(scene,firstFrame,"read:"+first.id()).trigger(FloatingMenuInteraction.PRIMARY).execute(player,host,FloatingMenuInteraction.PRIMARY);
            assertEquals(java.util.List.of(first.id()),opened);
            controller.close();
        }
    }
    @Test void ownersClickThePointToTravelAndUseASeparateManagementAction() throws Exception {
        try (var store = new PlotRepository(directory.resolve("managed-list.db"))) {
            store.open(); var registry = new PlotRegistry(store);
            UUID owner = UUID.randomUUID();
            var plot = new Plot(UUID.randomUUID(),UUID.randomUUID(),owner,"Park",0,
                    Set.of(new PlotGeometry.Cell(0,16,0)),Map.of(),Map.of(),null);
            registry.put(plot);
            var first = new PlotDestination(UUID.randomUUID(),plot.id(),"A gate","Description",false,1,65,1,0,0, "", "");
            registry.saveDestination(first);
            for (int index=0; index<3; index++) registry.saveDestination(new PlotDestination(UUID.randomUUID(),plot.id(),"Point "+index,
                    "Long description\n"+"多行介绍\n".repeat(60),false,1,65,1,0,0, "", ""));
            var loads = new AtomicInteger();
            var world = proxy(org.bukkit.World.class,(name,_) -> {
                if (name.equals("getChunkAtAsync")) { loads.incrementAndGet(); return new java.util.concurrent.CompletableFuture<>(); }
                return null;
            });
            var player = proxy(Player.class,(name,_) -> switch (name) {
                case "getUniqueId" -> owner;
                case "getLocation" -> new org.bukkit.Location(null,1,65,1);
                default -> null;
            });
            var controller = new PlotDestinationController(null,language(),registry,Runnable::run,_ -> world);
            var menus = new PlotDestinationMenus(null,language(),registry,controller);
            var scene = menus.listing(player,plot,0);
            var frame = SceneGeometryTestSupport.measure(scene,1);
            assertNotNull(entry(scene,frame,"manage:"+first.id()));
            var page = frame.pagination().getFirst().page();
            assertEquals(page.toIndex() - page.fromIndex(),frame.elements().stream()
                    .filter(value -> value.node().key().value().startsWith("manage:")).count());
            var closes = new AtomicInteger();
            var handle = proxy(FloatingMenuHandle.class,(name,_) -> { if (name.equals("close")) closes.incrementAndGet(); return null; });
            entry(scene,frame,"point:"+first.id()).trigger(FloatingMenuInteraction.PRIMARY).execute(player,handle,FloatingMenuInteraction.PRIMARY);
            assertEquals(1,loads.get()); assertEquals(1,closes.get());
            for (var scale : FloatingMenuTextScale.values()) assertDisjoint(SceneGeometryTestSupport.measure(scene,scale.factor()));
            controller.close();
        }
    }

    @Test void visitorsCanReadEveryCharacterWithoutManagementActionsAndPointChangesHaveDistinctBindings() throws Exception {
        try (var store = new PlotRepository(directory.resolve("public-reading.db"))) {
            store.open(); var registry = new PlotRegistry(store);
            var plot = new Plot(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"Park",0,
                    Set.of(new PlotGeometry.Cell(0,16,0)),Map.of(),Map.of(),null);
            registry.put(plot);
            String description = "甲乙丙丁".repeat(128);
            var point = new PlotDestination(UUID.randomUUID(),plot.id(),"Gate",description,true,1,65,1,0,0,"","");
            registry.saveDestination(point);
            UUID visitor = UUID.randomUUID();
            var player = proxy(Player.class,(name,_) -> name.equals("getUniqueId") ? visitor : null);
            var controller = new PlotDestinationController(null,language(),registry,Runnable::run,_ -> null);
            var menus = new PlotDestinationMenus(null,language(),registry,controller);
            for (double font : new double[]{.7,1.8}) {
                var read = new java.util.concurrent.atomic.AtomicReference<>(org.encinet.mik.shared.menu.ui.UiReadingPosition.START);
                var text = new StringBuilder();
                boolean complete = false;
                for (int pass = 0; pass < 100; pass++) {
                    var scene = menus.information(player,plot,point,read.get(),read::set);
                    assertNull(entry(scene,"edit")); assertNull(entry(scene,"delete")); assertNull(entry(scene,"public"));
                    var frame = SceneGeometryTestSupport.readableFrame(scene,font,new FloatingMenuFieldOfView(30),1.3);
                    assertDisjoint(frame); assertNotNull(element(frame,"teleport"));
                    for (var item : frame.elements()) if (item.node().key().value().equals("description"))
                        text.append(PlainTextComponentSerializer.plainText().serialize(item.node().content().label()));
                    var page = frame.pagination().getFirst();
                    if (!page.page().hasNext()) { complete = true; break; }
                    page.selectPage().accept(page.page().next().index());
                }
                assertTrue(complete); assertEquals(description,text.toString());
            }
            var another = new PlotDestination(UUID.randomUUID(),plot.id(),"Another","",true,2,65,2,0,0,"","");
            var first = menus.information(player,plot,point,org.encinet.mik.shared.menu.ui.UiReadingPosition.START,_ -> {});
            var second = menus.information(player,plot,another,org.encinet.mik.shared.menu.ui.UiReadingPosition.START,_ -> {});
            assertEquals(first.screenId(), second.screenId(), "One screen owns navigation while each destination owns its inputs");
            assertNotEquals(entry(first,"teleport").id(),entry(second,"teleport").id());
            controller.close();
        }
    }

}
