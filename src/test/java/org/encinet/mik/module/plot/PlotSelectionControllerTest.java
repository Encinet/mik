package org.encinet.mik.module.plot;

import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotSelectionControllerTest {
    private final UUID worldId = UUID.randomUUID();
    private final UUID playerId = UUID.randomUUID();
    private final UUID plotId = UUID.randomUUID();
    private final AtomicBoolean permitted = new AtomicBoolean(true);
    private final AtomicBoolean sneaking = new AtomicBoolean();
    private final AtomicBoolean menuOpen = new AtomicBoolean();
    private final AtomicBoolean holdingAxe = new AtomicBoolean(true);
    private final AtomicLong clock = new AtomicLong();
    private final List<UUID> finished = new ArrayList<>();
    private final List<UUID> stopped = new ArrayList<>();
    private final List<UUID> previews = new ArrayList<>();
    private final List<UUID> hints = new ArrayList<>();
    private final List<PlotProblem> problems = new ArrayList<>();
    private final PlotSelectionState selections = new PlotSelectionState();
    private World currentWorld = world(worldId);
    private double horizontal = -0.2;
    private double vertical = 65.8;
    private double forward = 3.9;
    private final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
            new Class<?>[]{Player.class}, (proxy, method, arguments) -> switch (method.getName()) {
                case "getUniqueId" -> playerId;
                case "getWorld" -> currentWorld;
                case "getLocation" -> new Location(currentWorld, horizontal, vertical, forward);
                case "isSneaking" -> sneaking.get();
                default -> throw new AssertionError(method.getName());
            });
    private final PlotSelectionController controller = new PlotSelectionController(selections,
            (viewer, target) -> permitted.get(), (viewer, target) -> previews.add(target),
            viewer -> hints.add(viewer.getUniqueId()), viewer -> stopped.add(viewer.getUniqueId()),
            (viewer, target) -> finished.add(target), (viewer, error) -> problems.add(error),
            clock::get, viewer -> menuOpen.get(), viewer -> holdingAxe.get());

    @Test
    void creationCanPickAndReturnToThePanelBeforeAPlotExists() {
        controller.start(player, null);
        assertTrue(controller.ownsPreview(playerId, null));
        assertFalse(controller.ownsPreview(playerId, plotId));
        assertEquals(PlotSelectionController.Mode.PICKING, controller.mode(playerId));
        controller.onInteract(click(Action.LEFT_CLICK_BLOCK, block(1, 64, 2), EquipmentSlot.HAND));
        controller.onInteract(click(Action.RIGHT_CLICK_BLOCK, block(3, 67, 4), EquipmentSlot.HAND));
        assertEquals(new PlotPosition(worldId, 1, 64, 2), selections.points(playerId, null).first());
        controller.keepProject(playerId, null);
        assertEquals(PlotSelectionController.Mode.PREVIEW, controller.mode(playerId));
        sneaking.set(true);
        controller.onSwapHands(new PlayerSwapHandItemsEvent(player, null, null));
        assertEquals(1, finished.size());
        assertNull(finished.getFirst());
        controller.keepProject(playerId, plotId);
        assertNull(controller.mode(playerId));
        assertFalse(controller.ownsPreview(playerId, null));
    }

    @Test
    void recordsClickedBlocksAndConsumesBothMiningAndItemUse() {
        controller.start(player, plotId);
        PlayerInteractEvent first = click(Action.LEFT_CLICK_BLOCK, block(1, 64, 2), EquipmentSlot.HAND);
        PlayerInteractEvent second = click(Action.RIGHT_CLICK_BLOCK, block(3, 67, 4), EquipmentSlot.HAND);
        controller.onInteract(first);
        controller.onInteract(second);
        assertEquals(new PlotPosition(worldId, 1, 64, 2), selections.first(playerId));
        assertEquals(new PlotPosition(worldId, 3, 67, 4), selections.second(playerId));
        for (PlayerInteractEvent event : List.of(first, second)) {
            assertEquals(Event.Result.DENY, event.useInteractedBlock());
            assertEquals(Event.Result.DENY, event.useItemInHand());
        }
    }

    @Test
    void ignoresOffhandAndAirWithoutDuplicatingTheSecondPoint() {
        controller.start(player, plotId);
        PlayerInteractEvent offhand = click(Action.RIGHT_CLICK_BLOCK, block(1, 64, 2), EquipmentSlot.OFF_HAND);
        controller.onInteract(offhand);
        controller.onInteract(click(Action.RIGHT_CLICK_AIR, null, EquipmentSlot.HAND));
        assertNull(selections.second(playerId));
        assertEquals(Event.Result.DENY, offhand.useItemInHand());
    }

    @Test
    void preventsSurvivalDamageAndCreativeInstantBreaksWhilePicking() {
        controller.start(player, plotId);
        Block block = block(1, 64, 2);
        BlockDamageEvent damage = new BlockDamageEvent(player, block, BlockFace.NORTH, null, true);
        BlockBreakEvent breaking = new BlockBreakEvent(block, player);
        controller.onDamage(damage);
        controller.onBreak(breaking);
        assertTrue(damage.isCancelled());
        assertTrue(breaking.isCancelled());
        assertNull(selections.first(playerId));
    }

    @Test
    void sneakRightClickReturnsToTheCorrectProjectAndKeepsPreviewWithoutCapturingClicks() {
        controller.start(player, plotId);
        controller.onInteract(click(Action.LEFT_CLICK_BLOCK, block(1, 64, 2), EquipmentSlot.HAND));
        sneaking.set(true);
        controller.onInteract(click(Action.RIGHT_CLICK_AIR, null, EquipmentSlot.HAND));
        assertEquals(List.of(plotId), finished);
        assertEquals(PlotSelectionController.Mode.PREVIEW, controller.mode(playerId));
        previews.clear();
        controller.refresh(player);
        assertEquals(List.of(plotId), previews);
        assertEquals(new PlotPosition(worldId, 1, 64, 2), selections.first(playerId));
        PlayerInteractEvent normal = click(Action.LEFT_CLICK_BLOCK, block(3, 64, 2), EquipmentSlot.HAND);
        controller.onInteract(normal);
        assertFalse(normal.isCancelled());
        assertEquals(new PlotPosition(worldId, 1, 64, 2), selections.first(playerId));
    }

    @Test
    void stopsImmediatelyWhenManagementPermissionIsRevoked() {
        controller.start(player, plotId);
        permitted.set(false);
        controller.onInteract(click(Action.LEFT_CLICK_BLOCK, block(1, 64, 2), EquipmentSlot.HAND));
        assertNull(selections.first(playerId));
        assertEquals(List.of(playerId), stopped);
        permitted.set(true);
        controller.onInteract(click(Action.LEFT_CLICK_BLOCK, block(1, 64, 2), EquipmentSlot.HAND));
        assertNull(selections.first(playerId));
    }

    @Test
    void timesOutWithoutErasingTheSelectionDraft() {
        controller.start(player, plotId);
        controller.onInteract(click(Action.LEFT_CLICK_BLOCK, block(1, 64, 2), EquipmentSlot.HAND));
        clock.set(Duration.ofMinutes(20).toNanos());
        PlayerInteractEvent expired = click(Action.RIGHT_CLICK_BLOCK, block(3, 64, 4), EquipmentSlot.HAND);
        controller.onInteract(expired);
        assertFalse(expired.isCancelled());
        assertNull(selections.second(playerId));
        assertEquals(new PlotPosition(worldId, 1, 64, 2), selections.first(playerId));
        assertEquals(List.of(playerId), stopped);
    }

    @Test
    void cannotWriteIntoAnotherProjectWhenTheActiveDraftChanges() {
        controller.start(player, plotId);
        UUID otherPlot = UUID.randomUUID();
        selections.bind(playerId, otherPlot, worldId);
        controller.onInteract(click(Action.LEFT_CLICK_BLOCK, block(1, 64, 2), EquipmentSlot.HAND));
        assertNull(selections.points(playerId, plotId).first());
        assertNull(selections.points(playerId, otherPlot).first());
        assertNull(controller.mode(playerId));
    }

    @Test
    void releasesTheModeOnWorldChangeAndQuit() {
        controller.start(player, plotId);
        World previous = currentWorld;
        currentWorld = world(UUID.randomUUID());
        controller.onWorldChange(new PlayerChangedWorldEvent(player, previous));
        assertEquals(List.of(playerId), stopped);
        currentWorld = previous;
        controller.onInteract(click(Action.LEFT_CLICK_BLOCK, block(1, 64, 2), EquipmentSlot.HAND));
        assertNull(selections.first(playerId));
        controller.start(player, plotId);
        controller.onQuit(new PlayerQuitEvent(player, net.kyori.adventure.text.Component.empty(),
                PlayerQuitEvent.QuitReason.DISCONNECTED));
        controller.onInteract(click(Action.LEFT_CLICK_BLOCK, block(1, 64, 2), EquipmentSlot.HAND));
        assertNull(selections.first(playerId));
    }

    @Test
    void movingThroughThePlotKeepsTheSessionAliveBeyondItsOpeningTime() {
        controller.start(player, plotId);
        for (int minute : List.of(15, 30, 45, 60)) {
            clock.set(Duration.ofMinutes(minute).toNanos());
            controller.onMove(new PlayerMoveEvent(player,
                    new Location(currentWorld, minute - 1, 64, 0), new Location(currentWorld, minute, 64, 0)));
            controller.refresh(player);
        }
        assertEquals(plotId, controller.plotId(playerId));
        assertTrue(stopped.isEmpty());
        controller.onInteract(click(Action.LEFT_CLICK_BLOCK, block(60, 64, 0), EquipmentSlot.HAND));
        assertEquals(new PlotPosition(worldId, 60, 64, 0), selections.first(playerId));
    }

    @Test
    void cancelledMovementAndCameraTurnsDoNotResetIdleTimeout() {
        controller.start(player, plotId);
        clock.set(Duration.ofMinutes(19).toNanos());
        PlayerMoveEvent cancelled = new PlayerMoveEvent(player,
                new Location(currentWorld, 0, 64, 0), new Location(currentWorld, 1, 64, 0));
        cancelled.setCancelled(true);
        controller.onMove(cancelled);
        controller.onMove(new PlayerMoveEvent(player, new Location(currentWorld, 0, 64, 0),
                new Location(currentWorld, 0, 64, 0, 90, 0)));
        clock.set(Duration.ofMinutes(20).toNanos());
        controller.refresh(player);
        assertNull(controller.plotId(playerId));
        assertEquals(List.of(playerId), stopped);
    }

    @Test
    void lateMovementCannotReviveAnExpiredSession() {
        controller.start(player, plotId);
        clock.set(Duration.ofMinutes(21).toNanos());
        controller.onMove(new PlayerMoveEvent(player,
                new Location(currentWorld, 0, 64, 0), new Location(currentWorld, 1, 64, 0)));
        controller.refresh(player);
        assertNull(controller.mode(playerId));
        assertEquals(List.of(playerId), stopped);
    }

    @Test
    void commandsWorkInPreviewWithoutCapturingOrdinaryClicksOrMining() {
        controller.start(player, plotId, PlotSelectionController.Mode.PREVIEW);
        PlayerInteractEvent normal = click(Action.RIGHT_CLICK_BLOCK, block(1, 64, 2), EquipmentSlot.HAND);
        controller.onInteract(normal);
        BlockBreakEvent breaking = new BlockBreakEvent(block(1, 64, 2), player);
        controller.onBreak(breaking);
        BlockDamageEvent damage = new BlockDamageEvent(player, block(1, 64, 2), BlockFace.NORTH, null, true);
        controller.onDamage(damage);
        PlayerCommandPreprocessEvent worldEdit = command("//pos1");
        controller.onCommand(worldEdit);
        assertFalse(normal.isCancelled());
        assertFalse(breaking.isCancelled());
        assertFalse(damage.isCancelled());
        assertTrue(worldEdit.isCancelled());
        assertEquals(new PlotPosition(worldId, -1, 65, 3), selections.first(playerId));
        assertEquals(PlotSelectionController.Mode.PREVIEW, controller.mode(playerId));
    }

    @Test
    void normalBuildingActivityAlsoKeepsPreviewAlive() {
        controller.start(player, plotId, PlotSelectionController.Mode.PREVIEW);
        clock.set(Duration.ofMinutes(19).toNanos());
        controller.onInteract(click(Action.RIGHT_CLICK_BLOCK, block(1, 64, 2), EquipmentSlot.HAND));
        clock.set(Duration.ofMinutes(21).toNanos());
        controller.refresh(player);
        assertEquals(plotId, controller.plotId(playerId));
        assertTrue(stopped.isEmpty());
    }

    @Test
    void openingMenusSuspendsPickingButKeepsBordersAndDoesNotStealMenuHints() {
        controller.start(player, plotId);
        menuOpen.set(true);
        previews.clear();
        hints.clear();
        controller.refresh(player);
        assertEquals(List.of(plotId), previews);
        assertTrue(hints.isEmpty());
        PlayerInteractEvent menuClick = click(Action.LEFT_CLICK_BLOCK, block(1, 64, 2), EquipmentSlot.HAND);
        controller.onInteract(menuClick);
        assertFalse(menuClick.isCancelled());
        assertNull(selections.first(playerId));
        menuOpen.set(false);
        controller.onInteract(click(Action.LEFT_CLICK_BLOCK, block(1, 64, 2), EquipmentSlot.HAND));
        assertEquals(new PlotPosition(worldId, 1, 64, 2), selections.first(playerId));
    }

    @Test
    void keepingTheSameProjectPausesPickingAndKeepsPreviewWhileSwitchingStopsIt() {
        controller.start(player, plotId);
        controller.keepProject(playerId, plotId);
        assertEquals(plotId, controller.plotId(playerId));
        assertEquals(PlotSelectionController.Mode.PREVIEW, controller.mode(playerId));
        controller.keepProject(playerId, UUID.randomUUID());
        assertNull(controller.plotId(playerId));
    }

    @Test
    void refreshingPreviewDoesNotRebindAnotherProjectsDraft() {
        controller.start(player, plotId);
        UUID otherPlot = UUID.randomUUID();
        selections.bind(playerId, otherPlot, worldId);
        previews.clear();
        controller.refresh(player);
        assertTrue(previews.isEmpty());
        assertEquals(otherPlot, selections.currentPlot(playerId));
        assertNull(controller.mode(playerId));
    }

    @Test
    void childPickingKeepsItsIntentAndCannotOverwriteParentDraft() {
        selections.bind(playerId, plotId, worldId);
        selections.mark(playerId, true, new PlotPosition(worldId, 20, 64, 2));
        PlotEditorContext childContext = PlotEditorContext.subPlot(plotId);
        selections.bindContext(playerId, childContext, worldId);
        controller.start(player, plotId);
        controller.onInteract(click(Action.LEFT_CLICK_BLOCK, block(1, 64, 2), EquipmentSlot.HAND));
        assertEquals(childContext, selections.context(playerId));
        assertEquals(new PlotPosition(worldId, 1, 64, 2), selections.first(playerId));
        assertEquals(new PlotPosition(worldId, 20, 64, 2),
                selections.draftFor(playerId, PlotEditorContext.area(plotId)).base().points().first());
        selections.bind(playerId, plotId, worldId);
        PlayerInteractEvent outdated = click(Action.RIGHT_CLICK_BLOCK, block(3, 64, 4), EquipmentSlot.HAND);
        controller.onInteract(outdated);
        assertFalse(outdated.isCancelled());
        assertNull(selections.second(playerId));
        assertNull(controller.mode(playerId));
    }

    @Test
    void sneakSwapReturnsToPanelWithoutEndingPreviewAndResumeCapturesBlocksAgain() {
        controller.start(player, plotId, PlotSelectionController.Mode.PREVIEW);
        sneaking.set(true);
        PlayerSwapHandItemsEvent shortcut = new PlayerSwapHandItemsEvent(player, null, null);
        controller.onSwapHands(shortcut);
        assertTrue(shortcut.isCancelled());
        assertEquals(List.of(plotId), finished);
        assertEquals(PlotSelectionController.Mode.PREVIEW, controller.mode(playerId));
        sneaking.set(false);
        controller.start(player, plotId);
        controller.onInteract(click(Action.LEFT_CLICK_BLOCK, block(1, 64, 2), EquipmentSlot.HAND));
        assertEquals(new PlotPosition(worldId, 1, 64, 2), selections.first(playerId));
    }

    @Test
    void swapShortcutLeavesOrdinarySwapsAndAlreadyClaimedInputsAlone() {
        controller.start(player, plotId);
        PlayerSwapHandItemsEvent ordinary = new PlayerSwapHandItemsEvent(player, null, null);
        controller.onSwapHands(ordinary);
        assertFalse(ordinary.isCancelled());
        sneaking.set(true);
        PlayerSwapHandItemsEvent claimed = new PlayerSwapHandItemsEvent(player, null, null);
        claimed.setCancelled(true);
        controller.onSwapHands(claimed);
        assertTrue(finished.isEmpty());
        menuOpen.set(true);
        PlayerSwapHandItemsEvent inMenu = new PlayerSwapHandItemsEvent(player, null, null);
        controller.onSwapHands(inMenu);
        assertFalse(inMenu.isCancelled());
        assertTrue(finished.isEmpty());
    }

    @Test
    void permissionLossAlsoStopsPreviewOnlySessions() {
        controller.start(player, plotId, PlotSelectionController.Mode.PREVIEW);
        permitted.set(false);
        previews.clear();
        controller.refresh(player);
        assertTrue(previews.isEmpty());
        assertNull(controller.plotId(playerId));
        assertEquals(List.of(playerId), stopped);
    }

    @Test
    void realDeathStopsEditorButCancelledDeathDoesNotAndNeitherErasesDraft() {
        controller.start(player, plotId);
        controller.onInteract(click(Action.LEFT_CLICK_BLOCK, block(1, 64, 2), EquipmentSlot.HAND));
        PlayerDeathEvent cancelled = new PlayerDeathEvent(player, null, List.of(), 0, Component.empty(), false);
        cancelled.setCancelled(true);
        controller.onDeath(cancelled);
        assertEquals(plotId, controller.plotId(playerId));
        controller.onDeath(new PlayerDeathEvent(player, null, List.of(), 0, Component.empty(), false));
        assertNull(controller.plotId(playerId));
        assertEquals(List.of(playerId), stopped);
        assertEquals(new PlotPosition(worldId, 1, 64, 2), selections.first(playerId));
    }

    @Test
    void doesNotActivateWithoutPermissionOrConsumePressurePlateEvents() {
        permitted.set(false);
        controller.start(player, plotId);
        PlayerInteractEvent normal = click(Action.LEFT_CLICK_BLOCK, block(1, 64, 2), EquipmentSlot.HAND);
        controller.onInteract(normal);
        assertFalse(normal.isCancelled());
        permitted.set(true);
        controller.start(player, plotId);
        PlayerInteractEvent pressure = click(Action.PHYSICAL, block(1, 64, 2), EquipmentSlot.HAND);
        controller.onInteract(pressure);
        assertFalse(pressure.isCancelled());
    }

    @Test
    void interceptsBareWorldEditPositionCommandsOnlyDuringPlotSelection() {
        controller.start(player, plotId);
        PlayerCommandPreprocessEvent first = command("//pos1");
        controller.onCommand(first);
        assertTrue(first.isCancelled());
        assertEquals(new PlotPosition(worldId, -1, 65, 3), selections.first(playerId));
        horizontal = 5.7;
        vertical = 70;
        forward = -4.2;
        PlayerCommandPreprocessEvent second = command("//POS2  ");
        controller.onCommand(second);
        assertTrue(second.isCancelled());
        assertEquals(new PlotPosition(worldId, 5, 70, -5), selections.second(playerId));
    }

    @Test
    void leavesCommandsUntouchedOutsideEditingAndDoesNotStealOtherWorldEditCommands() {
        PlayerCommandPreprocessEvent normal = command("//pos1");
        controller.onCommand(normal);
        assertFalse(normal.isCancelled());
        controller.start(player, plotId);
        for (String command : List.of("//pos10", "/pos1", "//hpos1", "//set stone")) {
            PlayerCommandPreprocessEvent delegated = command(command);
            controller.onCommand(delegated);
            assertFalse(delegated.isCancelled(), command);
        }
        assertNull(selections.first(playerId));
        assertNull(selections.second(playerId));
    }

    @Test
    void explicitCoordinatesWorkWithMenusOpenAndWithoutHoldingTheSelectionTool() {
        controller.start(player, plotId, PlotSelectionController.Mode.PREVIEW);
        menuOpen.set(true);
        holdingAxe.set(false);
        PlayerCommandPreprocessEvent first = command("//pos1 -8,64,0");
        PlayerCommandPreprocessEvent second = command("//pos2 3,67,11");
        controller.onCommand(first);
        controller.onCommand(second);
        assertTrue(first.isCancelled());
        assertTrue(second.isCancelled());
        assertEquals(new PlotPosition(worldId, -8, 64, 0), selections.first(playerId));
        assertEquals(new PlotPosition(worldId, 3, 67, 11), selections.second(playerId));
        assertTrue(problems.isEmpty());
        assertEquals(PlotSelectionController.Mode.PREVIEW, controller.mode(playerId));
    }

    @Test
    void invalidCoordinatesAreConsumedWithoutChangingTheSelectionOrDelegatingToWorldEdit() {
        controller.start(player, plotId);
        for (String input : List.of("//pos1 extra", "//pos2 1,2", "//pos1 1,320,3",
                "//pos2 1,-65,3", "//pos1 1,64,3 extra")) {
            PlayerCommandPreprocessEvent event = command(input);
            controller.onCommand(event);
            assertTrue(event.isCancelled());
        }
        assertEquals(5, problems.size());
        assertNull(selections.first(playerId));
        assertNull(selections.second(playerId));
    }

    @Test
    void ordinaryToolsAndEmptyHandsRemainUsableDuringSelection() {
        controller.start(player, plotId);
        holdingAxe.set(false);
        PlayerInteractEvent click = click(Action.RIGHT_CLICK_BLOCK, block(1, 64, 2), EquipmentSlot.HAND);
        BlockBreakEvent breaking = new BlockBreakEvent(block(1, 64, 2), player);
        controller.onInteract(click);
        controller.onBreak(breaking);
        assertFalse(click.isCancelled());
        assertFalse(breaking.isCancelled());
        assertNull(selections.first(playerId));
        PlayerCommandPreprocessEvent command = command("//pos1");
        controller.onCommand(command);
        assertTrue(command.isCancelled());
        assertEquals(new PlotPosition(worldId, -1, 65, 3), selections.first(playerId));
    }

    @Test
    void respectsEarlierCommandDenialsAndStopsInterceptingAfterExitOrPermissionLoss() {
        controller.start(player, plotId);
        PlayerCommandPreprocessEvent denied = command("//pos1");
        denied.setCancelled(true);
        controller.onCommand(denied);
        assertNull(selections.first(playerId));
        permitted.set(false);
        PlayerCommandPreprocessEvent revoked = command("//pos1");
        controller.onCommand(revoked);
        assertFalse(revoked.isCancelled());
        assertNull(selections.first(playerId));
        permitted.set(true);
        controller.start(player, plotId);
        controller.stop(playerId);
        PlayerCommandPreprocessEvent exited = command("//pos2");
        controller.onCommand(exited);
        assertFalse(exited.isCancelled());
    }

    private PlayerInteractEvent click(Action action, Block block, EquipmentSlot hand) {
        return new PlayerInteractEvent(player, action, null, block, BlockFace.NORTH, hand);
    }

    private PlayerCommandPreprocessEvent command(String input) {
        return new PlayerCommandPreprocessEvent(player, input, Set.of());
    }

    private Block block(int horizontal, int vertical, int forward) {
        return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[]{Block.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getWorld" -> currentWorld;
                    case "getX" -> horizontal;
                    case "getY" -> vertical;
                    case "getZ" -> forward;
                    default -> throw new AssertionError(method.getName());
                });
    }

    private static World world(UUID id) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                        case "getUID" -> id;
                        case "getMinHeight" -> -64;
                        case "getMaxHeight" -> 320;
                        default -> throw new AssertionError(method.getName());
                    });
    }
}
