package org.encinet.mik.feature.world.plot;

import static org.encinet.mik.feature.world.plot.PlotDestinationTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;
import org.encinet.mik.shared.i18n.Message;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class PlotDestinationControllerTest {
    @TempDir Path directory;
    final UUID worldId = UUID.randomUUID(), owner = UUID.randomUUID(), guest = UUID.randomUUID();
    final ArrayDeque<Runnable> callbacks = new ArrayDeque<>();
    final List<CompletableFuture<Chunk>> loads = new ArrayList<>();
    final List<Location> teleports = new ArrayList<>();
    final List<net.kyori.adventure.title.Title> titles = new ArrayList<>();
    PlotRepository store;
    PlotRegistry registry;
    PlotDestinationController controller;
    Plot plot;
    PlotDestination point;
    Player player;
    World world;
    UUID actor;
    int blockReads;
    boolean loaded, safe = true, online = true, border = true, teleportSucceeds = true, redirected;

    @BeforeEach void open() throws Exception {
        store = new PlotRepository(directory.resolve("travel.db")); store.open(); registry = new PlotRegistry(store);
        plot = new Plot(UUID.randomUUID(), worldId, owner, "Park", 0, Set.of(new PlotGeometry.Cell(0,16,0), new PlotGeometry.Cell(1,16,0)), Map.of(), Map.of(), null);
        registry.put(plot);
        point = new PlotDestination(UUID.randomUUID(), plot.id(), "Gate", "Welcome", true, 1.5,65,1.5,90,10, "", "");
        registry.saveDestination(point);
        actor = guest;
        var worldBorder = proxy(WorldBorder.class, (name,_) -> name.equals("isInside") ? border : null);
        world = proxy(World.class, (name,args) -> switch (name) {
            case "getUID" -> worldId;
            case "getMinHeight" -> -64;
            case "getMaxHeight" -> 320;
            case "getWorldBorder" -> worldBorder;
            case "getChunkAtAsync" -> { var future = new CompletableFuture<Chunk>(); loads.add(future); yield future; }
            case "getBlockAt" -> {
                assertTrue(loaded, "Block safety must be read after asynchronous chunk loading"); blockReads++;
                boolean floor = ((int) args[1]) == 64;
                Material material = floor ? (safe ? Material.STONE : Material.MAGMA_BLOCK) : Material.AIR;
                VoxelShape collision = proxy(VoxelShape.class, (method,_) -> method.equals("getBoundingBoxes")
                        ? floor ? List.of(new BoundingBox(0,0,0,1,1,1)) : List.of() : null);
                yield proxy(Block.class, (method,_) -> switch (method) {
                    case "getType" -> material;
                    case "isPassable" -> !floor;
                    case "getCollisionShape" -> collision;
                    default -> null;
                });
            }
            default -> null;
        });
        player = proxy(Player.class, (name,args) -> switch (name) {
            case "getUniqueId" -> actor;
            case "getWorld" -> world;
            case "isOnline" -> online;
            case "getLocation" -> new Location(world,redirected ? 40 : 1.5,65,1.5,90,10);
            case "teleport" -> { teleports.add(((Location)args[0]).clone()); yield teleportSucceeds; }
            case "showTitle" -> { titles.add((net.kyori.adventure.title.Title)args[0]); yield null; }
            default -> null;
        });
        controller = new PlotDestinationController(null,language(),registry,callbacks::add,id -> worldId.equals(id) ? world : null);
    }
    @AfterEach void close() throws Exception { controller.close(); store.close(); }
    private void load(int index) {
        loaded = true; loads.get(index).complete(proxy(Chunk.class,(_,_) -> null));
        while (!callbacks.isEmpty()) callbacks.remove().run();
    }

    @Test void publicClickLoadsAsynchronouslyAndTeleportsToExactPositionAndOrientation() {
        controller.teleport(player,point.id());
        assertEquals(0,blockReads); assertTrue(teleports.isEmpty());
        load(0);
        assertEquals(1,teleports.size());
        assertEquals(1.5,teleports.getFirst().getX()); assertEquals(90,teleports.getFirst().getYaw()); assertEquals(10,teleports.getFirst().getPitch());
    }

    @Test void previewsUnsavedFormattingWithoutMovingOrWritingAndChecksCurrentManagement() throws Exception {
        actor = owner;
        var draft = point.titles("<gold>Welcome</gold>", "<italic>Enjoy</italic>");
        controller.preview(player, point, draft);
        assertEquals(1,titles.size());
        assertEquals(PlotDestinationTitles.render(draft.title()),titles.getFirst().title());
        assertEquals(PlotDestinationTitles.render(draft.subtitle()),titles.getFirst().subtitle());
        assertTrue(loads.isEmpty()); assertTrue(teleports.isEmpty());
        assertEquals(point,registry.destination(point.id()));
        actor = guest;
        assertThrows(PlotProblem.class, () -> controller.preview(player, point, draft));
        actor = owner;
        final boolean[] saved = {false};
        controller.titles(player,point,draft.title(),draft.subtitle(),() -> saved[0] = true);
        assertTrue(saved[0]); assertEquals(draft,registry.destination(point.id()));
        assertEquals(draft,store.loadDestinations().get(point.id()));
        assertThrows(PlotProblem.class, () -> controller.preview(player,point,draft));
    }

    @Test void titlesAppearOnlyAfterSuccessfulArrivalAndSubtitleCanBeUsedAlone() throws Exception {
        point = point.titles("", "<aqua>Welcome</aqua>"); registry.saveDestination(point);
        controller.teleport(player,point.id()); assertTrue(titles.isEmpty()); load(0);
        assertEquals(1,titles.size()); assertEquals(net.kyori.adventure.text.Component.empty(), titles.getFirst().title());
        assertEquals(PlotDestinationTitles.render(point.subtitle()),titles.getFirst().subtitle());
        teleportSucceeds = false; controller.teleport(player,point.id()); load(1); assertEquals(1,titles.size());
        teleportSucceeds = true; redirected = true; controller.teleport(player,point.id()); load(2); assertEquals(1,titles.size());
        redirected = false;
        registry.saveDestination(point.titles("", "")); controller.teleport(player,point.id()); load(3); assertEquals(1,titles.size());
    }

    @Test void otherEditsAndRelocationPreserveBothTitleSources() throws Exception {
        actor = owner; loaded = true;
        point = point.titles("<gold>Gate</gold>", "<bold>Enjoy</bold>"); registry.saveDestination(point);
        controller.edit(player,point,"New gate","Description");
        var renamed = registry.destination(point.id());
        controller.publish(player,renamed);
        controller.relocate(player,registry.destination(point.id()));
        var moved = registry.destination(point.id());
        assertEquals(point.title(),moved.title()); assertEquals(point.subtitle(),moved.subtitle());
    }

    @Test void closingPublicAccessWhileLoadingPreventsOutsiderTeleport() throws Exception {
        controller.teleport(player,point.id()); registry.saveDestination(point.published(false)); load(0);
        assertTrue(teleports.isEmpty()); assertEquals(0,blockReads);
        assertEquals(Message.PLOT_DESTINATION_ERROR_PRIVATE, assertThrows(PlotProblem.class,
                () -> controller.teleport(player,point.id())).message());
    }

    @Test void deletionOrUnsafeFloorWhileLoadingPreventsTeleport() throws Exception {
        controller.teleport(player,point.id()); registry.deleteDestination(point.id()); load(0); assertTrue(teleports.isEmpty());
        registry.saveDestination(point); safe = false;
        controller.teleport(player,point.id()); load(1); assertTrue(teleports.isEmpty());
    }

    @Test void changedPointAndChangedEffectiveSubplotAreRejectedBeforeTeleport() throws Exception {
        controller.teleport(player,point.id()); registry.saveDestination(point.details("Renamed", "Changed")); load(0); assertTrue(teleports.isEmpty());
        registry.saveDestination(point);
        controller.teleport(player,point.id());
        // Simulates a changed effective owner after the request began.
        registry.put(new Plot(UUID.randomUUID(),worldId,owner,"Room",0,
                Set.of(new PlotGeometry.Cell(0,16,0)),Map.of(),Map.of(),plot.id()));
        load(1); assertTrue(teleports.isEmpty());
    }

    @Test void latestRequestWinsAndQuitOrShutdownDiscardsOutstandingLoads() throws Exception {
        var second = new PlotDestination(UUID.randomUUID(),plot.id(),"Other gate","",true,2.5,65,1.5,180,0, "", "");
        registry.saveDestination(second);
        controller.teleport(player,point.id()); controller.teleport(player,second.id());
        load(1); load(0);
        assertEquals(1,teleports.size()); assertEquals(2.5,teleports.getFirst().getX());
        controller.teleport(player,point.id()); controller.forget(actor); load(2); assertEquals(1,teleports.size());
        controller.teleport(player,point.id()); controller.close(); load(3); assertEquals(1,teleports.size());
        assertThrows(PlotProblem.class, () -> controller.teleport(player,point.id()));
        assertEquals(4,loads.size());
    }

    @Test void privateAccessTracksCurrentMembershipAndDoesNotInheritParentMembers() throws Exception {
        var restricted = point.published(false); registry.saveDestination(restricted);
        assertFalse(controller.canUse(restricted,guest,false)); assertTrue(controller.canUse(restricted,owner,false)); assertTrue(controller.canUse(restricted,guest,true));
        var edits = new PlotEdits(registry); edits.invite(plot,owner,false,guest,Plot.Role.COLLABORATOR);
        assertTrue(controller.canUse(restricted,guest,false));
        actor = guest; controller.teleport(player,restricted.id()); edits.removeMember(plot,owner,false,guest); load(0); assertTrue(teleports.isEmpty());
        Plot child = registry.createSubPlot(plot.id(),UUID.randomUUID(),"Room",new PlotSelectionShape(worldId,null,Set.of(new PlotGeometry.Cell(1,16,0))));
        var childPoint = new PlotDestination(UUID.randomUUID(),child.id(),"Door","",false,5,65,1,0,0, "", "");
        edits.invite(plot,owner,false,guest,Plot.Role.COLLABORATOR);
        assertFalse(controller.canUse(childPoint,guest,false)); assertTrue(controller.canUse(childPoint,owner,false));
    }

    @Test void managementRequiresCurrentPermissionAndRejectsStaleEdits() throws Exception {
        assertThrows(PlotProblem.class, () -> controller.edit(player,point,"New", "Description"));
        actor = owner;
        controller.edit(player,point,"New", "Description");
        var changed = registry.destination(point.id()); assertEquals("New",changed.name());
        assertThrows(PlotProblem.class, () -> controller.publish(player,point));
        controller.publish(player,changed); assertFalse(registry.destination(point.id()).publicAccess());
        loaded = true; controller.create(player,plot.id(),"Second","Plain description");
        assertEquals(2,registry.destinations(plot.id()).size());
        assertFalse(registry.destinations(plot.id()).stream().filter(value -> value.name().equals("Second")).findFirst().orElseThrow().publicAccess());
        var edits = new PlotEdits(registry); edits.flag(plot,owner,false,"owner.manage_settings",false);
        assertThrows(PlotProblem.class, () -> controller.delete(player,registry.destination(point.id())));
    }
}
