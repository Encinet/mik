package org.encinet.mik.feature.server.monitor;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Chunk;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.role.PlayerRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MonitorModuleTest {
    private static final LanguageService LANGUAGES = new LanguageService(null) {
        @Override public Language language(Player player) { return Language.EN_US; }
        @Override public String t(Language language, Message message, Object... args) {
            return message.key() + (args.length == 0 ? "" : " " + Arrays.toString(args));
        }
    };

    @ParameterizedTest
    @EnumSource(PlayerRole.class)
    void onlyStaffCanStartMonitorEvenWithoutGroupInheritance(PlayerRole role) throws Exception {
        Fixture f = new Fixture();
        f.observer.role = role;
        if (role == PlayerRole.MODERATOR || role == PlayerRole.CUSTODIAN) {
            f.run("monitor");
            assertTrue(f.module.isMonitoring(f.observer.player));
            assertEquals(GameMode.SPECTATOR, f.observer.mode);
        } else {
            assertThrows(CommandSyntaxException.class, () -> f.run("monitor"));
            assertFalse(f.module.isMonitoring(f.observer.player));
            assertEquals(GameMode.CREATIVE, f.observer.mode);
        }
    }

    @Test
    void hidesFromMembersAndSpectatorsButKeepsStaffVisibleAndRestoresOnExit() throws Exception {
        Fixture f = new Fixture();
        State member = f.add("Member");
        State spectator = f.add("Spectator");
        spectator.mode = GameMode.SPECTATOR;
        State staff = f.add("OtherStaff");
        staff.role = PlayerRole.CUSTODIAN;
        f.run("monitor");
        verify(member.player).hidePlayer(any(), eq(f.observer.player));
        verify(f.tab).showTab(member.player, f.observer.player);
        assertTrue(f.module.retainsTabEntry(member.player, f.observer.player));
        verify(spectator.player).hidePlayer(any(), eq(f.observer.player));
        verify(staff.player, never()).hidePlayer(any(), any());
        verify(f.observer.player, never()).hidePlayer(any(), any());
        f.module.tick();
        verify(member.player, times(1)).hidePlayer(any(), eq(f.observer.player));
        f.run("monitor stop");
        verify(member.player).showPlayer(any(), eq(f.observer.player));
        assertFalse(f.module.retainsTabEntry(member.player, f.observer.player));
        verify(f.observation).close();
        verify(spectator.player).showPlayer(any(), eq(f.observer.player));
        verify(staff.player, never()).showPlayer(any(), any());
    }

    @Test
    void preexistingHiddenPlayersDoNotGainATabEntry() throws Exception {
        Fixture f = new Fixture();
        State member = f.add("Member");
        when(member.player.canSee(f.observer.player)).thenReturn(false);
        f.run("monitor");
        verify(member.player).hidePlayer(any(), eq(f.observer.player));
        verify(f.tab, never()).showTab(member.player, f.observer.player);
        assertFalse(f.module.retainsTabEntry(member.player, f.observer.player));
    }

    @Test
    void joinReconnectAndRoleChangesUpdateVisibility() throws Exception {
        Fixture f = new Fixture();
        f.run("monitor");
        State joined = f.add("Joined");
        f.module.onJoin(new PlayerJoinEvent(joined.player, Component.empty()));
        verify(joined.player).hidePlayer(any(), eq(f.observer.player));
        joined.role = PlayerRole.MODERATOR;
        f.module.tick();
        verify(joined.player).showPlayer(any(), eq(f.observer.player));
        joined.role = PlayerRole.MEMBER;
        f.module.tick();
        verify(joined.player, times(2)).hidePlayer(any(), eq(f.observer.player));
        f.module.onQuit(new PlayerQuitEvent(joined.player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        f.module.onJoin(new PlayerJoinEvent(joined.player, Component.empty()));
        verify(joined.player, times(3)).hidePlayer(any(), eq(f.observer.player));
    }

    @ParameterizedTest
    @ValueSource(strings = {"quit", "disable"})
    void failedCleanupStillReleasesVisibility(String action) throws Exception {
        Fixture f = new Fixture();
        State member = f.add("Member");
        f.run("monitor");
        f.observer.teleportAllowed = false;
        if (action.equals("disable")) assertThrows(RuntimeException.class, f.module::disable);
        else f.module.onQuit(new PlayerQuitEvent(f.observer.player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        verify(member.player).showPlayer(any(), eq(f.observer.player));
        assertFalse(f.module.isMonitoring(f.observer.player));
    }

    @Test
    void rejectedReturnKeepsTheObserverHiddenUntilReturnSucceeds() throws Exception {
        Fixture f = new Fixture();
        State member = f.add("Member");
        f.run("monitor");
        f.observer.teleportAllowed = false;
        f.run("monitor stop");
        verify(member.player, never()).showPlayer(any(), any());
        verify(f.observation, never()).close();
        f.observer.teleportAllowed = true;
        f.run("monitor stop");
        verify(member.player).showPlayer(any(), eq(f.observer.player));
    }

    @Test
    void manualAfkEndsMonitoringAndReleasesObservationAfterReturning() throws Exception {
        Fixture f = new Fixture();
        f.add("Target");
        f.run("monitor");
        verify(f.afk).beginPassiveObservation(f.observer.player);
        when(f.afk.isAfk(f.observer.player.getUniqueId())).thenReturn(true);
        f.module.tick();
        assertFalse(f.module.isMonitoring(f.observer.player));
        assertEquals(f.origin, f.observer.location);
        verify(f.observation).close();
        verify(f.afk, never()).recordTrustedActivity(any());
    }

    @ParameterizedTest
    @EnumSource(PlayerRole.class)
    void onlyStaffSeeTheMonitorBadgeAndItDisappearsOnExit(PlayerRole role) throws Exception {
        Fixture f = new Fixture();
        State viewer = f.add("Viewer");
        viewer.role = role;
        List<UUID> refreshed = new ArrayList<>();
        f.module.onTabChange(refreshed::add);
        assertEquals(Component.empty(), f.module.tabBadge(viewer.player, f.observer.player));
        f.run("monitor");
        String badge = PlainTextComponentSerializer.plainText().serialize(f.module.tabBadge(viewer.player, f.observer.player));
        assertEquals(role == PlayerRole.MODERATOR || role == PlayerRole.CUSTODIAN ? " [player-monitor-label]" : "", badge);
        assertEquals(List.of(f.observer.player.getUniqueId()), refreshed);
        f.run("monitor stop");
        assertEquals(Component.empty(), f.module.tabBadge(viewer.player, f.observer.player));
        assertEquals(List.of(f.observer.player.getUniqueId(), f.observer.player.getUniqueId()), refreshed);
    }

    @Test
    void namedTargetsStayPinnedAcrossRotationAndAfkAndCanBeChangedWithoutLosingTheOrigin() throws Exception {
        Fixture f = new Fixture();
        State first = f.add("First");
        f.add("Second");
        when(f.afk.isIdleAfk(first.player.getUniqueId())).thenReturn(true);
        f.run("monitor first");
        assertEquals("monitor-watching [First]", f.observer.actionBar);
        f.now += Duration.ofMinutes(2).toNanos();
        f.module.tick();
        assertEquals("monitor-watching [First]", f.observer.actionBar);
        f.run("monitor Second");
        assertEquals("monitor-watching [Second]", f.observer.actionBar);
        f.run("monitor stop");
        assertEquals(f.origin, f.observer.location);
        verify(f.afk, times(1)).beginPassiveObservation(f.observer.player);
        verify(f.observation, times(1)).close();
    }

    @Test
    void aPinnedOfflineTargetWaitsForTheSameUuidInsteadOfSwitchingToOtherPlayers() throws Exception {
        Fixture f = new Fixture();
        State target = f.add("Target");
        f.add("Other");
        f.run("monitor Target");
        target.online = false;
        f.module.tick();
        assertEquals("monitor-waiting", f.observer.actionBar);
        State rejoined = f.add("Target");
        UUID targetId = target.player.getUniqueId();
        when(rejoined.player.getUniqueId()).thenReturn(targetId);
        f.module.tick();
        assertEquals("monitor-watching [Target]", f.observer.actionBar);
    }

    @ParameterizedTest
    @ValueSource(strings = {"self", "offline", "hidden", "dead", "spectator"})
    void invalidNamedTargetsNeverEnterOrReplaceTheCurrentSession(String reason) throws Exception {
        Fixture f = new Fixture();
        State target = f.add("Invalid");
        String name = "Invalid";
        switch (reason) {
            case "self" -> name = "Staff";
            case "offline" -> target.online = false;
            case "hidden" -> when(f.observer.player.canSee(target.player)).thenReturn(false);
            case "dead" -> target.dead = true;
            case "spectator" -> target.mode = GameMode.SPECTATOR;
            default -> throw new AssertionError(reason);
        }
        assertEquals(0, f.run("monitor " + name));
        assertFalse(f.module.isMonitoring(f.observer.player));
        f.add("Valid");
        f.run("monitor Valid");
        assertEquals(0, f.run("monitor " + name));
        assertEquals("monitor-watching [Valid]", f.observer.actionBar);
    }

    @Test
    void namedTargetSuggestionsUseTheSameVisibilityRulesAndIncludeAfkPlayers() {
        Fixture f = new Fixture();
        State target = f.add("Target");
        State hidden = f.add("TopSecret");
        when(f.observer.player.canSee(hidden.player)).thenReturn(false);
        when(f.afk.isIdleAfk(target.player.getUniqueId())).thenReturn(true);
        var suggestions = f.commands.getCompletionSuggestions(f.commands.parse("monitor T", f.source)).join();
        assertEquals(List.of("Target"), suggestions.getList().stream().map(com.mojang.brigadier.suggestion.Suggestion::getText).toList());
    }

    @Test
    void consoleCannotStartAPlayerCamera() {
        Fixture f = new Fixture();
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(mock(CommandSender.class));
        assertThrows(CommandSyntaxException.class, () -> f.commands.execute("monitor", source));
    }

    @Test
    void permissionIsRecheckedAfterParsingAndDuringMonitoring() throws Exception {
        Fixture f = new Fixture();
        var parsed = f.commands.parse("monitor", f.source);
        f.observer.role = PlayerRole.MEMBER;
        assertEquals(0, f.commands.execute(parsed));
        assertFalse(f.module.isMonitoring(f.observer.player));

        f.observer.role = PlayerRole.CUSTODIAN;
        f.add("Target");
        f.run("monitor");
        f.observer.role = PlayerRole.MEMBER;
        f.module.tick();
        assertFalse(f.module.isMonitoring(f.observer.player));
        assertEquals(f.origin, f.observer.location);
        assertEquals(GameMode.CREATIVE, f.observer.mode);
    }

    @Test
    void followsFromTheSideAndAboveWithoutTakingOverTheTargetsCamera() throws Exception {
        Fixture f = new Fixture();
        State target = f.add("Target");
        f.run("monitor");
        assertTrue(f.observer.location.getX() > target.location.getX());
        assertTrue(f.observer.location.getZ() < target.location.getZ());
        assertTrue(Math.abs(f.observer.location.getX() - target.location.getX())
                > Math.abs(f.observer.location.getZ() - target.location.getZ()));
        assertTrue(f.observer.player.getEyeLocation().getY() > target.player.getEyeLocation().getY());
        assertTrue(f.observer.location.getDirection().getZ() > 0);
        assertTrue(f.observer.location.getDirection().getY() < 0);
        verify(f.observer.player, never()).setSpectatorTarget(target.player);

        target.location = new Location(f.world, 50, 90, 60, 90, 0);
        f.module.tick();
        assertTrue(f.observer.location.distance(target.location) < 6);
        assertTrue(f.observer.location.getX() > target.location.getX());
        assertTrue(f.observer.causes.stream().allMatch(cause -> cause == TeleportCause.SPECTATE));
    }

    @Test
    void aStationaryViewDoesNotKeepSendingIdenticalTeleports() throws Exception {
        Fixture f = new Fixture();
        f.add("Target");
        f.run("monitor");
        for (int index = 0; index < 20; index++) f.module.tick();
        assertEquals(1, f.observer.moves.size());
        assertEquals("monitor-watching [Target]", f.observer.actionBar);
    }

    @Test
    void followsTargetAcrossWorldsUsingTheCurrentLocation() throws Exception {
        Fixture f = new Fixture();
        State target = f.add("Target");
        f.run("monitor");
        World other = world();
        target.location = new Location(other, 0, 70, 0);
        f.module.tick();
        assertSame(other, f.observer.location.getWorld());
        assertTrue(f.observer.location.getY() > target.location.getY());
    }

    @Test
    void randomRoundsVisitEveryoneWithoutAnImmediateRepeat() throws Exception {
        Fixture f = new Fixture();
        f.add("A");
        f.add("B");
        f.add("C");
        f.run("monitor");
        String previous = null;
        for (int round = 0; round < 20; round++) {
            Set<String> seen = new HashSet<>();
            for (int index = 0; index < 3; index++) {
                String current = f.observer.actionBar;
                assertNotEquals(previous, current);
                assertTrue(seen.add(current), "each player must be visited once per round");
                previous = current;
                f.now += Duration.ofSeconds(30).toNanos();
                f.module.tick();
            }
            assertEquals(3, seen.size());
        }
    }

    @Test
    void automaticallyRotatesAtThirtySecondsAndKeepsSingleTarget() throws Exception {
        Fixture f = new Fixture();
        f.add("A");
        f.add("B");
        f.run("monitor");
        String first = f.observer.actionBar;
        f.now = Duration.ofSeconds(30).toNanos() - 1;
        f.module.tick();
        assertEquals(first, f.observer.actionBar);
        f.now++;
        f.module.tick();
        assertNotEquals(first, f.observer.actionBar);

        Fixture single = new Fixture();
        single.add("Only");
        single.run("monitor");
        single.now = Duration.ofMinutes(1).toNanos();
        single.module.tick();
        assertEquals("monitor-watching [Only]", single.observer.actionBar);
    }

    @Test
    void skipsSelfDeadOfflineHiddenAndSpectatorPlayersAndWaitsForJoins() throws Exception {
        Fixture f = new Fixture();
        f.add("Dead").dead = true;
        f.add("Offline").online = false;
        f.add("Spectator").mode = GameMode.SPECTATOR;
        State hidden = f.add("Hidden");
        when(f.observer.player.canSee(hidden.player)).thenReturn(false);
        f.run("monitor");
        assertEquals("monitor-waiting", f.observer.actionBar);
        assertTrue(f.observer.moves.isEmpty());

        f.add("Joined");
        f.module.tick();
        assertEquals("monitor-watching [Joined]", f.observer.actionBar);
    }

    @Test
    void manualAfkLabelsWithoutSustainedIdleCannotEscapeMonitoring() throws Exception {
        Fixture f = new Fixture();
        State target = f.add("Target");
        when(f.afk.isAfk(target.player.getUniqueId())).thenReturn(true);
        when(f.afk.isIdleAfk(target.player.getUniqueId())).thenReturn(false);
        f.run("monitor");
        assertEquals("monitor-watching [Target]", f.observer.actionBar);
        f.module.tick();
        assertEquals("monitor-watching [Target]", f.observer.actionBar);
    }

    @Test
    void waitsWhenEveryoneIsIdleAfkAndResumesWhenAPlayerReturns() throws Exception {
        Fixture f = new Fixture();
        State target = f.add("Target");
        when(f.afk.isIdleAfk(target.player.getUniqueId())).thenReturn(true);
        f.run("monitor");
        assertEquals("monitor-waiting", f.observer.actionBar);
        assertTrue(f.observer.moves.isEmpty());
        when(f.afk.isIdleAfk(target.player.getUniqueId())).thenReturn(false);
        f.module.tick();
        assertEquals("monitor-watching [Target]", f.observer.actionBar);
        when(f.afk.isIdleAfk(target.player.getUniqueId())).thenReturn(true);
        f.module.tick();
        assertEquals("monitor-waiting", f.observer.actionBar);
    }

    @ParameterizedTest
    @ValueSource(strings = {"offline", "dead", "spectator", "hidden", "afk"})
    void invalidTargetIsReplacedBeforeTheRotationDeadline(String reason) throws Exception {
        Fixture f = new Fixture();
        State target = f.add("Target");
        f.run("monitor");
        f.add("Replacement");
        switch (reason) {
            case "offline" -> target.online = false;
            case "dead" -> target.dead = true;
            case "spectator" -> target.mode = GameMode.SPECTATOR;
            case "hidden" -> when(f.observer.player.canSee(target.player)).thenReturn(false);
            case "afk" -> when(f.afk.isIdleAfk(target.player.getUniqueId())).thenReturn(true);
            default -> throw new AssertionError(reason);
        }
        f.module.tick();
        assertEquals("monitor-watching [Replacement]", f.observer.actionBar);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unloaded", "border"})
    void usesNativeSpectatingWhenNoLoadedViewFitsInsideTheBorder(String reason) throws Exception {
        Fixture f = new Fixture();
        State target = f.add("Target");
        if (reason.equals("unloaded")) when(f.world.isChunkLoaded(0, -1)).thenReturn(false);
        else when(f.world.getWorldBorder().isInside(any(Location.class))).thenReturn(false);
        f.run("monitor");
        assertSame(target.player, f.observer.spectatorTarget);
        assertTrue(f.observer.moves.isEmpty());
        verify(f.world, never()).getChunkAtAsync(anyInt(), anyInt());
    }

    @Test
    void tightSpacesUseNativeSpectatingAndRecoverAfterSustainedClearance() throws Exception {
        Fixture f = new Fixture();
        State target = f.add("Target");
        blockAllViews(f.world);
        f.run("monitor");
        assertSame(target.player, f.observer.spectatorTarget);
        assertTrue(f.observer.moves.isEmpty());
        assertTrue(f.module.isMonitoring(f.observer.player));

        when(f.world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(),
                eq(FluidCollisionMode.NEVER), eq(true))).thenReturn(null);
        for (int index = 0; index < 19; index++) f.module.tick();
        assertSame(target.player, f.observer.spectatorTarget);
        f.module.tick();
        assertNull(f.observer.spectatorTarget);
        assertFalse(f.observer.moves.isEmpty());
        assertTrue(f.module.isMonitoring(f.observer.player));
    }

    @Test
    void occludingWallsKeepTheSessionInThirdPersonWithItsEyeInsideTheWall() throws Exception {
        Fixture f = new Fixture();
        f.add("Target");
        blockAllViews(f.world);
        f.solidAt = _ -> true;
        f.run("monitor");
        assertNull(f.observer.spectatorTarget);
        assertFalse(f.observer.moves.isEmpty());
        assertTrue(f.solidAt.test(f.observer.player.getEyeLocation()));
        assertTrue(f.module.isMonitoring(f.observer.player));
    }

    @Test
    void cancelledNativeDetachDoesNotTeleportAndReturnCanBeRetried() throws Exception {
        Fixture f = new Fixture();
        State target = f.add("Target");
        blockAllViews(f.world);
        f.run("monitor");
        f.observer.detachAllowed = false;
        when(f.world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(),
                eq(FluidCollisionMode.NEVER), eq(true))).thenReturn(null);
        for (int index = 0; index < 20; index++) f.module.tick();
        assertSame(target.player, f.observer.spectatorTarget);
        assertTrue(f.observer.moves.isEmpty());
        f.run("monitor stop");
        assertTrue(f.module.isMonitoring(f.observer.player));
        assertTrue(f.observer.messages.contains("monitor-return-failed"));
        f.observer.detachAllowed = true;
        f.run("monitor stop");
        assertFalse(f.module.isMonitoring(f.observer.player));
        assertEquals(f.origin, f.observer.location);
    }

    private static void blockAllViews(World world) {
        when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(),
                eq(FluidCollisionMode.NEVER), eq(true))).thenAnswer(call -> new RayTraceResult(
                        ((Location) call.getArgument(0)).toVector().add(((Vector) call.getArgument(1)).clone().multiply(0.5))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"monitor", "monitor stop"})
    void exitRestoresTheOriginalLocationModeAndCustomFlightState(String command) throws Exception {
        Fixture f = new Fixture();
        f.observer.mode = GameMode.SURVIVAL;
        f.observer.allowFlight = true;
        f.observer.flying = true;
        f.add("Target");
        f.run("monitor");
        f.run(command);
        assertFalse(f.module.isMonitoring(f.observer.player));
        assertEquals(f.origin, f.observer.location);
        assertEquals(GameMode.SURVIVAL, f.observer.mode);
        assertTrue(f.observer.allowFlight);
        assertTrue(f.observer.flying);
        int moves = f.observer.moves.size();
        f.module.tick();
        assertEquals(moves, f.observer.moves.size());
    }

    @Test
    void manualGameModeChangeEndsPatrolAndPreservesTheChosenMode() throws Exception {
        Fixture f = new Fixture();
        f.add("Target");
        f.run("monitor");
        f.observer.mode = GameMode.ADVENTURE;
        f.module.tick();
        assertFalse(f.module.isMonitoring(f.observer.player));
        assertEquals(GameMode.ADVENTURE, f.observer.mode);
        assertEquals(f.origin, f.observer.location);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dead", "vehicle", "mode-denied"})
    void failedEntryNeverExemptsThePlayerOrTeleportsThem(String reason) throws Exception {
        Fixture f = new Fixture();
        f.add("Target");
        switch (reason) {
            case "dead" -> f.observer.dead = true;
            case "vehicle" -> f.observer.vehicle = true;
            case "mode-denied" -> f.observer.modeAllowed = false;
            default -> throw new AssertionError(reason);
        }
        assertEquals(0, f.run("monitor"));
        assertFalse(f.module.isMonitoring(f.observer.player));
        assertEquals(GameMode.CREATIVE, f.observer.mode);
        assertTrue(f.observer.moves.isEmpty());
    }

    @Test
    void unloadedReturnWaitsForChunkLoadingThenRestoresOnTheMainThread() throws Exception {
        Fixture f = new Fixture();
        f.add("Target");
        f.run("monitor");
        f.unloadOrigin();
        f.run("monitor stop");
        assertTrue(f.module.isMonitoring(f.observer.player));
        assertEquals(GameMode.SPECTATOR, f.observer.mode);
        int moves = f.observer.moves.size();
        f.module.tick();
        f.run("monitor");
        assertEquals(moves, f.observer.moves.size());

        f.loaded.complete(mock(Chunk.class));
        assertTrue(f.module.isMonitoring(f.observer.player), "callback must wait for the main-thread queue");
        f.flush();
        assertFalse(f.module.isMonitoring(f.observer.player));
        assertEquals(f.origin, f.observer.location);
        assertEquals(GameMode.CREATIVE, f.observer.mode);
    }

    @ParameterizedTest
    @ValueSource(strings = {"teleport", "chunk-load", "game-mode"})
    void failedReturnStaysInSpectatorAndCanBeRetried(String reason) throws Exception {
        Fixture f = new Fixture();
        f.add("Target");
        f.run("monitor");
        if (reason.equals("teleport")) f.observer.teleportAllowed = false;
        else if (reason.equals("game-mode")) f.observer.modeAllowed = false;
        else f.unloadOrigin();
        f.run("monitor stop");
        if (reason.equals("chunk-load")) {
            f.loaded.completeExceptionally(new IllegalStateException("load failed"));
            f.flush();
        }
        assertTrue(f.module.isMonitoring(f.observer.player));
        assertEquals(GameMode.SPECTATOR, f.observer.mode);
        assertTrue(f.observer.messages.contains("monitor-return-failed"));
        f.observer.teleportAllowed = true;
        f.observer.modeAllowed = true;
        when(f.world.isChunkLoaded(62, 62)).thenReturn(true);
        f.run("monitor stop");
        assertFalse(f.module.isMonitoring(f.observer.player));
        assertEquals(f.origin, f.observer.location);
    }

    @ParameterizedTest
    @ValueSource(strings = {"quit", "disable", "new-session"})
    void cleanupRestoresThePlayerAndStaleChunkLoadsCannotMoveThem(String action) throws Exception {
        Fixture f = new Fixture();
        f.add("Target");
        f.run("monitor");
        f.unloadOrigin();
        f.run("monitor stop");
        if (action.equals("disable")) f.module.disable();
        else f.module.onQuit(new PlayerQuitEvent(f.observer.player,
                Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        assertFalse(f.module.isMonitoring(f.observer.player));
        assertEquals(f.origin, f.observer.location);
        assertEquals(GameMode.CREATIVE, f.observer.mode);
        if (action.equals("new-session")) f.run("monitor");
        int moves = f.observer.moves.size();
        f.loaded.complete(mock(Chunk.class));
        f.flush();
        assertEquals(moves, f.observer.moves.size());
        if (action.equals("disable")) verify(f.timer).cancel();
    }

    private static World world() {
        World world = mock(World.class);
        WorldBorder border = mock(WorldBorder.class);
        when(border.isInside(any(Location.class))).thenReturn(true);
        when(world.getWorldBorder()).thenReturn(border);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        return world;
    }

    private static final class Fixture {
        final World world = world();
        final List<Player> online = new ArrayList<>();
        final State observer = new State("Staff", world);
        final Location origin = new Location(world, 1000, 80, 1000, 22, 15);
        final CommandDispatcher<CommandSourceStack> commands = new CommandDispatcher<>();
        final CommandSourceStack source = mock(CommandSourceStack.class);
        final Queue<Runnable> mainTasks = new ArrayDeque<>();
        final CompletableFuture<Chunk> loaded = new CompletableFuture<>();
        final BukkitTask timer = mock(BukkitTask.class);
        final org.encinet.mik.feature.player.afk.AfkService afk = mock(org.encinet.mik.feature.player.afk.AfkService.class);
        final org.encinet.mik.feature.player.afk.AfkActivityService.ActivityLease observation =
                mock(org.encinet.mik.feature.player.afk.AfkActivityService.ActivityLease.class);
        final MonitorPlayerPackets tab = mock(MonitorPlayerPackets.class);
        final MonitorCameraView view = mock(MonitorCameraView.class);
        final MonitorModule module;
        Predicate<Location> solidAt = _ -> false;
        long now;

        Fixture() {
            JavaPlugin plugin = mock(JavaPlugin.class);
            Server server = mock(Server.class);
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            PluginManager manager = mock(PluginManager.class);
            when(plugin.getServer()).thenReturn(server);
            when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
            when(server.getScheduler()).thenReturn(scheduler);
            when(server.getPluginManager()).thenReturn(manager);
            when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L))).thenReturn(timer);
            observer.role = PlayerRole.MODERATOR;
            observer.mode = GameMode.CREATIVE;
            observer.location = origin.clone();
            online.add(observer.player);
            when(source.getSender()).thenReturn(observer.player);
            when(afk.beginPassiveObservation(observer.player)).thenReturn(observation);
            module = new MonitorModule(plugin, LANGUAGES, afk, () -> online, () -> now, mainTasks::add,
                    () -> new MonitorCamera(eye -> solidAt.test(eye)), tab, () -> view);
            module.enable();
            commands.register(module.command());
        }

        State add(String name) {
            State state = new State(name, world);
            online.add(state.player);
            return state;
        }

        int run(String command) throws CommandSyntaxException { return commands.execute(command, source); }

        void unloadOrigin() {
            when(world.isChunkLoaded(62, 62)).thenReturn(false);
            when(world.getChunkAtAsync(62, 62)).thenReturn(loaded);
        }

        void flush() {
            Runnable action;
            while ((action = mainTasks.poll()) != null) action.run();
        }
    }

    private static final class State {
        final Player player = mock(Player.class);
        final List<Location> moves = new ArrayList<>();
        final List<TeleportCause> causes = new ArrayList<>();
        final List<String> messages = new ArrayList<>();
        GameMode mode = GameMode.SURVIVAL;
        PlayerRole role = PlayerRole.MEMBER;
        Location location;
        boolean online = true;
        boolean dead;
        boolean vehicle;
        boolean allowFlight;
        boolean flying;
        boolean modeAllowed = true;
        boolean teleportAllowed = true;
        boolean detachAllowed = true;
        Entity spectatorTarget;
        String actionBar;

        State(String name, World world) {
            location = new Location(world, 0, 64, 0);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.getName()).thenReturn(name);
            when(player.getLocation()).thenAnswer(_ -> location.clone());
            when(player.getEyeLocation()).thenAnswer(_ -> location.clone().add(0, 1.62, 0));
            when(player.getEyeHeight()).thenReturn(1.62);
            when(player.getSpectatorTarget()).thenAnswer(_ -> spectatorTarget);
            doAnswer(call -> {
                Entity requested = call.getArgument(0);
                if (requested != null || detachAllowed) spectatorTarget = requested;
                return null;
            }).when(player).setSpectatorTarget(nullable(Entity.class));
            when(player.getGameMode()).thenAnswer(_ -> mode);
            when(player.hasPermission(anyString())).thenAnswer(call -> role.permission().equals(call.getArgument(0)));
            when(player.isOnline()).thenAnswer(_ -> online);
            when(player.isDead()).thenAnswer(_ -> dead);
            when(player.isInsideVehicle()).thenAnswer(_ -> vehicle);
            when(player.getAllowFlight()).thenAnswer(_ -> allowFlight);
            when(player.isFlying()).thenAnswer(_ -> flying);
            when(player.canSee(any(Player.class))).thenReturn(true);
            doAnswer(call -> {
                if (modeAllowed) mode = call.getArgument(0);
                return null;
            }).when(player).setGameMode(any(GameMode.class));
            doAnswer(call -> { allowFlight = call.getArgument(0); return null; }).when(player).setAllowFlight(anyBoolean());
            doAnswer(call -> { flying = call.getArgument(0); return null; }).when(player).setFlying(anyBoolean());
            when(player.teleport(any(Location.class), any(TeleportCause.class))).thenAnswer(call -> {
                if (!teleportAllowed) return false;
                location = ((Location) call.getArgument(0)).clone();
                moves.add(location);
                causes.add(call.getArgument(1));
                return true;
            });
            doAnswer(call -> {
                messages.add(PlainTextComponentSerializer.plainText().serialize(call.getArgument(0)));
                return null;
            }).when(player).sendMessage(any(Component.class));
            doAnswer(call -> {
                actionBar = PlainTextComponentSerializer.plainText().serialize(call.getArgument(0));
                return null;
            }).when(player).sendActionBar(any(Component.class));
        }
    }
}
