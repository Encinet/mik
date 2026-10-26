package org.encinet.mik.feature.player.guidance;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.encinet.mik.shared.i18n.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class TipEventsTest {
    private final Thread mainThread = Thread.currentThread();
    private final UUID id = UUID.randomUUID();
    private final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
            new Class<?>[]{World.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getUID" -> id;
                case "equals" -> proxy == args[0];
                case "hashCode" -> id.hashCode();
                default -> throw new AssertionError(method.getName());
            });
    private final AtomicReference<Location> position = new AtomicReference<>(new Location(world, 0, 64, 0));
    private final Player player = player();
    private final AtomicReference<Player> current = new AtomicReference<>(player);
    private final Queue<Runnable> mainTasks = new ConcurrentLinkedQueue<>();
    private final List<RuntimeException> errors = new ArrayList<>();
    private final AtomicInteger draws = new AtomicInteger();
    private final TipTestScope scope = new TipTestScope(() -> { draws.incrementAndGet(); return 0; });
    private final TipTestScope.EventHandlers handlers = new TipTestScope.EventHandlers();
    private final TipEvents events = new TipEvents(handlers, action -> {
        mainTasks.add(action);
        return () -> mainTasks.remove(action);
    }, viewer -> viewer == current.get(), errors::add);

    @AfterEach
    void close() {
        events.close();
        scope.close();
    }

    @Test
    void asyncChatOnlyMatchesTextUntilTheMainThreadDispatch() {
        AtomicInteger checks = new AtomicInteger();
        Tip<?> tip = scope.define("chat", Message.HOME_TIP_CREATE_MM)
                .on(chatTrigger().filter(event -> plain(event.message()).contains("home")))
                .when(viewer -> { checks.incrementAndGet(); return true; }).build();
        events.register(this, tip);

        CompletableFuture.runAsync(() -> handlers.fire(TipTestScope.chatEvent(player, "how do I set a home?"))).join();
        assertEquals(0, checks.get());
        assertEquals(1, mainTasks.size());
        flush();
        assertEquals(0, checks.get(), "operation callbacks can still update feature state after dispatch");
        assertEquals(List.of("home-tip-create-mm"), scope.collect(player, 2000).stream().map(TipEventsTest::plain).toList());
        assertEquals(1, checks.get(), "inspect current feature state at delivery");
        assertTrue(errors.isEmpty());
    }

    @Test
    void aTeleportCompletionCallbackCanMakeTheScenarioRelevantAfterDispatch() {
        AtomicBoolean historyReady = new AtomicBoolean();
        events.register(this, scope.define("return", Message.BACK_TIP_UNDO_MM).on(TipTriggers.teleport(0.1))
                .when(viewer -> historyReady.get()).build());
        PlayerTeleportEvent teleport = teleport();
        handlers.fire(teleport);
        position.set(teleport.getTo());
        flush();

        // The teleport's future may publish its final return history later in the same tick.
        historyReady.set(true);
        assertEquals(List.of("back-tip-undo-mm"), scope.collect(player, 2000).stream().map(TipEventsTest::plain).toList());
    }

    @Test
    void explicitChatWinsOverRandomTeleportAndRepeatedEventsShareOneDispatch() {
        Tip<?> tip = scope.define("combined", Message.HOME_TIP_CREATE_MM)
                .on(chatTrigger().filter(event -> plain(event.message()).equals("home"))).on(TipTriggers.teleport(0.1)).build();
        events.register(this, tip);
        handlers.fire(TipTestScope.chatEvent(player, "home"));
        handlers.fire(TipTestScope.chatEvent(player, "home"));
        PlayerTeleportEvent teleport = teleport();
        handlers.fire(teleport);
        position.set(teleport.getTo());

        assertEquals(1, mainTasks.size());
        flush();
        assertEquals(0, draws.get(), "an explicit request must not be weakened by the random opportunity");
        assertEquals(1, scope.collect(player, 2000).size());
    }

    @Test
    void cancelledOrUnfinishedTeleportsDoNotSampleRules() {
        Tip<?> tip = scope.define("travel", Message.BACK_TIP_RETURN_MM).on(TipTriggers.teleport(0.1)).build();
        events.register(this, tip);
        PlayerTeleportEvent cancelled = teleport();
        handlers.fire(cancelled);
        cancelled.setCancelled(true);
        position.set(cancelled.getTo());
        flush();
        assertEquals(0, draws.get());

        handlers.fire(teleport());
        flush();
        assertEquals(0, draws.get(), "a failed arrival must not create an opportunity");

        PlayerTeleportEvent redirected = teleport();
        handlers.fire(redirected);
        redirected.setTo(redirected.getFrom());
        flush();
        assertEquals(0, draws.get(), "a final destination in the original block is not a teleport opportunity");
        assertTrue(scope.collect(player, 2000).isEmpty());
    }

    @Test
    void successiveTeleportsInOneTickUseTheFinalArrivalAndSampleOnce() {
        events.register(this, scope.define("travel", Message.BACK_TIP_RETURN_MM).on(TipTriggers.teleport(0.1)).build());
        PlayerTeleportEvent first = teleport();
        handlers.fire(first);
        position.set(first.getTo());
        PlayerTeleportEvent last = teleport();
        handlers.fire(last);
        position.set(last.getTo());

        flush();
        assertEquals(1, draws.get());
        assertEquals(1, scope.collect(player, 2000).size());
    }

    @Test
    void coalescedChatCountsMessagesButSamplesOnceAndSkipsIrrelevantPlayers() {
        AtomicBoolean permitted = new AtomicBoolean(false);
        var rule = scope.define("density", Message.HOME_TIP_CREATE_MM)
                .on(TipTriggers.chatMentions(Pattern.compile("home")))
                .when(viewer -> permitted.get()).build();
        events.register(this, rule);
        handlers.fire(TipTestScope.chatEvent(player, "home"));
        flush();
        assertTrue(scope.snapshot(id).history().isEmpty(), "irrelevant matches must not create or change density records");
        permitted.set(true);
        for (int i = 0; i < 10; i++) handlers.fire(TipTestScope.chatEvent(player, "home home"));
        assertEquals(1, mainTasks.size());
        flush();
        assertEquals(10, scope.snapshot(id).history().get("density").pressure(),
                "each message contributes once even if it contains several keywords");
        assertEquals(0, draws.get(), "the first batch is certain and does not need a random draw");
        assertEquals(1, scope.collect(player, 2000).size());
    }

    @Test
    void closingOneOwnerRemovesOnlyItsQueuedRegistrations() {
        Object first = new Object();
        Object second = new Object();
        events.register(first, scope.define("first", Message.HOME_TIP_CREATE_MM).on(TipTrigger.event(PlayerJoinEvent.class)).build());
        events.register(second, scope.define("second", Message.BACK_TIP_RETURN_MM).on(TipTrigger.event(PlayerJoinEvent.class)).build());
        assertEquals(Set.of(PlayerJoinEvent.class), handlers.callbacks.keySet(), "share the native subscription by event type");
        handlers.fire(new PlayerJoinEvent(player, Component.empty()));
        events.close(first);

        assertTrue(handlers.callbacks.containsKey(PlayerJoinEvent.class));
        assertEquals(1, mainTasks.size());
        flush();
        assertEquals(List.of("back-tip-return-mm"), scope.collect(player, 2000).stream().map(TipEventsTest::plain).toList());
        handlers.fire(new PlayerJoinEvent(player, Component.empty()));
        events.close(second);
        assertTrue(handlers.callbacks.isEmpty(), "unregister the native listener when its last owner closes");
        assertTrue(mainTasks.isEmpty(), "the last subscription closing must cancel its queued task");
    }

    @Test
    void queuedEventsCannotLeakIntoAReplacementPlayerSession() {
        events.register(this, scope.define("session", Message.HOME_TIP_CREATE_MM).on(TipTrigger.event(PlayerJoinEvent.class)).build());
        handlers.fire(new PlayerJoinEvent(player, Component.empty()));
        Player replacement = player();
        current.set(replacement);
        flush();
        assertTrue(scope.collect(replacement, 2000).isEmpty());

        handlers.fire(new PlayerJoinEvent(replacement, Component.empty()));
        flush();
        assertEquals(1, scope.collect(replacement, 4000).size());
    }

    @Test
    void matcherFailuresDoNotBlockIndependentDefinitions() {
        events.register(this, scope.define("matcher", Message.BACK_TIP_RETURN_MM)
                .on(chatTrigger().filter(event -> { throw new IllegalStateException("bad text matcher"); })).build());
        events.register(this, scope.define("useful", Message.HOME_TIP_CREATE_MM).on(chatTrigger()).build());
        handlers.fire(TipTestScope.chatEvent(player, "home"));
        flush();

        assertEquals(List.of("home-tip-create-mm"), scope.collect(player, 2000).stream().map(TipEventsTest::plain).toList());
        assertEquals(1, errors.size());
        assertTrue(errors.getFirst().getMessage().contains("matcher"));
    }

    @Test
    void aBlockEventCanSelectItsPlayerAndFilterItsOwnEventData() {
        TipTrigger<BlockBreakEvent> ores = TipTrigger.event(BlockBreakEvent.class, BlockBreakEvent::getPlayer)
                .filter(event -> event.getBlock().getType() == Material.DIAMOND_ORE);
        events.register(this, scope.define("ore", Message.HOME_TIP_CREATE_MM).on(ores).build());
        events.register(this, scope.define("no-recipient", Message.BACK_TIP_RETURN_MM)
                .on(TipTrigger.event(BlockBreakEvent.class, event -> null)).build());

        handlers.fire(new BlockBreakEvent(block(Material.STONE), player));
        assertTrue(mainTasks.isEmpty());
        BlockBreakEvent cancelled = new BlockBreakEvent(block(Material.DIAMOND_ORE), player);
        cancelled.setCancelled(true);
        handlers.fire(cancelled);
        assertTrue(mainTasks.isEmpty());

        handlers.fire(new BlockBreakEvent(block(Material.DIAMOND_ORE), player));
        flush();
        assertEquals(List.of("home-tip-create-mm"), scope.collect(player, 2000).stream().map(TipEventsTest::plain).toList());
        assertTrue(errors.isEmpty());
    }

    @Test
    void asyncEventsCaptureTypedEventDataImmediatelyAndVerifyBukkitStateOnMain() {
        AtomicInteger captures = new AtomicInteger();
        Tip<String> tip = scope.<String>define("message-context")
                .on(chatTrigger().verify(event -> {
                    event.getPlayer().getLocation(); // The fake player enforces the main-thread boundary.
                    return true;
                }), event -> {
                    assertNotSame(mainThread, Thread.currentThread());
                    captures.incrementAndGet();
                    return plain(event.message());
                })
                .resolve((viewer, text, language) -> java.util.Optional.of(new TipContent("message", Component.text(text))))
                .build();
        events.register(this, tip);
        AsyncChatEvent event = TipTestScope.chatEvent(player, "original context");
        CompletableFuture.runAsync(() -> handlers.fire(event)).join();
        assertEquals(1, captures.get());
        event.message(Component.text("changed before main dispatch"));
        flush();
        assertEquals(1, captures.get());

        event.message(Component.text("changed after dispatch"));
        assertEquals("original context", plain(scope.collect(player, 2000).getFirst()));
    }

    @Test
    void cancelledExplicitRequestsFallBackToAValidRandomOpportunityForTheSameTip() {
        events.register(this, scope.define("fallback", Message.HOME_TIP_CREATE_MM)
                .on(chatTrigger()).on(TipTriggers.teleport(0.1)).build());
        AsyncChatEvent explicit = TipTestScope.chatEvent(player, "home");
        handlers.fire(explicit);
        PlayerTeleportEvent teleport = teleport();
        handlers.fire(teleport);
        position.set(teleport.getTo());
        explicit.setCancelled(true);

        flush();
        assertEquals(1, draws.get());
        assertEquals(1, scope.collect(player, 2000).size());
    }

    @Test
    void equallyLikelyTriggersChooseTheLatestContextAndPublishOnlyOnce() {
        events.register(this, scope.<String>define("latest-context")
                .on(chatTrigger(), event -> "chat")
                .on(TipTrigger.event(PlayerJoinEvent.class), event -> "join")
                .resolve((viewer, context, language) -> java.util.Optional.of(new TipContent("context", Component.text(context))))
                .build());
        handlers.fire(TipTestScope.chatEvent(player, "home"));
        handlers.fire(new PlayerJoinEvent(player, Component.empty()));
        flush();

        assertEquals(List.of("join"), scope.collect(player, 2000).stream().map(TipEventsTest::plain).toList());
        assertTrue(scope.collect(player, 3000).isEmpty());
    }

    @Test
    void verificationAndContextFailuresAreIsolatedFromOtherTips() {
        events.register(this, scope.define("bad-verification", Message.BACK_TIP_RETURN_MM)
                .on(chatTrigger().verify(event -> { throw new IllegalStateException("verify"); })).build());
        events.register(this, scope.define("bad-context", Message.BACK_TIP_UNDO_MM)
                .on(chatTrigger(), event -> { throw new IllegalStateException("context"); }).build());
        events.register(this, scope.define("working", Message.HOME_TIP_CREATE_MM).on(chatTrigger()).build());
        handlers.fire(TipTestScope.chatEvent(player, "home"));
        flush();

        assertEquals(List.of("home-tip-create-mm"), scope.collect(player, 2000).stream().map(TipEventsTest::plain).toList());
        assertEquals(Set.of("Tip event trigger failed: bad-verification", "Tip event trigger failed: bad-context"),
                errors.stream().map(Throwable::getMessage).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void rawExecutorsIgnoreSiblingEventsFromASharedHandlerList() {
        events.register(this, scope.define("join-only", Message.HOME_TIP_CREATE_MM)
                .on(TipTrigger.event(PlayerJoinEvent.class)).build());
        handlers.callbacks.get(PlayerJoinEvent.class).accept(teleport());

        assertTrue(mainTasks.isEmpty());
        assertTrue(errors.isEmpty());
    }

    @Test
    void closingTheTransportUnregistersAllEventTypesAndCancelsQueuedWork() {
        events.register(this, scope.define("closing", Message.HOME_TIP_CREATE_MM)
                .on(chatTrigger()).on(TipTrigger.event(PlayerJoinEvent.class)).build());
        var oldCallback = handlers.callbacks.get(AsyncChatEvent.class);
        handlers.fire(TipTestScope.chatEvent(player, "home"));
        events.close();
        oldCallback.accept(TipTestScope.chatEvent(player, "too late"));

        assertTrue(handlers.callbacks.isEmpty());
        assertTrue(mainTasks.isEmpty());
        assertTrue(scope.collect(player, 2000).isEmpty());
    }

    private Block block(Material material) {
        return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[]{Block.class},
                (proxy, method, args) -> {
                    assertSame(mainThread, Thread.currentThread());
                    if (method.getName().equals("getType")) return material;
                    throw new AssertionError(method.getName());
                });
    }

    private static TipTrigger<AsyncChatEvent> chatTrigger() {
        return TipTrigger.event(AsyncChatEvent.class);
    }

    private PlayerTeleportEvent teleport() {
        Location destination = position.get().clone().add(100, 0, 100);
        return new PlayerTeleportEvent(player, position.get().clone(), destination, PlayerTeleportEvent.TeleportCause.COMMAND);
    }

    private void flush() {
        Runnable task;
        while ((task = mainTasks.poll()) != null) task.run();
    }

    private Player player() {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    assertSame(mainThread, Thread.currentThread(), "Bukkit player state must stay on the main thread");
                    return switch (method.getName()) {
                        case "getUniqueId" -> id;
                        case "getLocation" -> position.get().clone();
                        default -> throw new AssertionError(method.getName());
                    };
                });
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
