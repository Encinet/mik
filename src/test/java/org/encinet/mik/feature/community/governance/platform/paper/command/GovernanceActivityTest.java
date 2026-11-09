package org.encinet.mik.feature.community.governance.platform.paper.command;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.*;
import org.encinet.mik.feature.community.governance.delivery.DeliveryService;
import org.encinet.mik.feature.community.governance.membership.MembershipService;
import org.encinet.mik.feature.community.governance.persistence.sqlite.SqliteGovernanceRepository;
import org.encinet.mik.feature.community.governance.platform.paper.GovernanceTaskExecutor;
import org.encinet.mik.feature.community.governance.platform.paper.GovernanceText;
import org.encinet.mik.feature.community.governance.removal.RemovalService;
import org.encinet.mik.feature.community.governance.voting.VotingService;
import org.encinet.mik.feature.community.governance.voting.model.*;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.UiSceneTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class GovernanceActivityTest {
    @TempDir Path directory;

    @Test void onlyAttachedVisibleHostsQueryAndClosingInvalidatesLateReads() throws Exception {
        try (var fixture = new Fixture(directory)) {
            var redraws = new AtomicInteger();
            var panel = fixture.activity.panel(fixture.player,redraws::incrementAndGet);
            assertTrue(panel.render().entries().isEmpty());
            assertTrue(fixture.storage.isEmpty(), "rendering cannot query storage");
            panel.attach(fixture.handle);
            assertEquals(1,fixture.storage.size());
            assertEquals(1,fixture.polls.size());
            fixture.runStorage(); fixture.flushMain();
            assertEquals(1,redraws.get());
            assertNotNull(UiSceneTestSupport.entry(panel.render(),"choice:YES"));
            fixture.lifecycle.set(FloatingMenuState.SUSPENDED);
            panel.render().lifecycle().changed(fixture.player,fixture.handle,FloatingMenuState.ACTIVE,FloatingMenuState.SUSPENDED,null);
            assertTrue(fixture.polls.getFirst().cancelled.get());
            fixture.polls.getFirst().task.run();
            assertTrue(fixture.storage.isEmpty());
            fixture.lifecycle.set(FloatingMenuState.ACTIVE); panel.attach(fixture.handle);
            assertEquals(1,fixture.storage.size());
            panel.close();
            assertTrue(fixture.polls.getLast().cancelled.get());
            fixture.runStorage(); fixture.flushMain();
            assertEquals(1,redraws.get(),"a closed host cannot be redrawn by a delayed query");
        }
    }

    @Test void repeatedClicksWriteOnceAndSelectionChangesOnlyAfterAuthoritativeReadback() throws Exception {
        try (var fixture = new Fixture(directory)) {
            var panel = fixture.activity.panel(fixture.player,() -> {});
            panel.attach(fixture.handle); fixture.runStorage(); fixture.flushMain();
            var old = panel.render();
            var cast = UiSceneTestSupport.entry(old,"choice:YES");
            cast.trigger(FloatingMenuInteraction.PRIMARY).execute(fixture.player,fixture.handle,FloatingMenuInteraction.PRIMARY);
            cast.trigger(FloatingMenuInteraction.PRIMARY).execute(fixture.player,fixture.handle,FloatingMenuInteraction.PRIMARY);
            assertEquals(1,fixture.storage.size(),"the second packet must not enqueue another write");
            assertFalse(UiSceneTestSupport.entry(panel.render(),"choice:YES").enabled());
            assertFalse(UiSceneTestSupport.entry(panel.render(),"choice:YES").selected());
            fixture.runStorage();
            assertEquals(VoteChoice.YES,fixture.voting.ballotChoice(fixture.voteId,fixture.viewer).orElseThrow());
            assertEquals(1,fixture.storage.size(),"the write is followed by one authoritative read");
            fixture.runStorage(); fixture.flushMain();
            assertTrue(UiSceneTestSupport.entry(panel.render(),"choice:YES").enabled());
            assertTrue(UiSceneTestSupport.entry(panel.render(),"choice:YES").selected());
            var no = UiSceneTestSupport.entry(panel.render(),"choice:NO");
            no.trigger(FloatingMenuInteraction.PRIMARY).execute(fixture.player,fixture.handle,FloatingMenuInteraction.PRIMARY);
            panel.close(); fixture.runStorage(); fixture.runStorage(); fixture.flushMain();
            assertEquals(VoteChoice.NO,fixture.voting.ballotChoice(fixture.voteId,fixture.viewer).orElseThrow(),"closing the UI does not pretend to revoke an already-submitted vote");
        }
    }

    @Test void failedSubmissionKeepsTheOldChoiceAndOffersLocalRecovery() throws Exception {
        try (var fixture = new Fixture(directory)) {
            var panel = fixture.activity.panel(fixture.player,() -> {});
            panel.attach(fixture.handle); fixture.runStorage(); fixture.flushMain();
            fixture.voting.terminateVote(fixture.voteId,VoteTerminationReason.CANDIDATE_WITHDREW);
            var cast = UiSceneTestSupport.entry(panel.render(),"choice:YES");
            cast.trigger(FloatingMenuInteraction.PRIMARY).execute(fixture.player,fixture.handle,FloatingMenuInteraction.PRIMARY);
            fixture.runStorage(); fixture.flushMain();
            assertTrue(fixture.voting.ballotChoice(fixture.voteId,fixture.viewer).isEmpty());
            assertNotNull(UiSceneTestSupport.entry(panel.render(),"activity-error"));
            assertNotNull(UiSceneTestSupport.entry(panel.render(),"activity-retry"));
            assertTrue(fixture.messages.isEmpty(),"operation errors belong beside the activity, without duplicate chat output");
            fixture.language.set(Language.ZH_CN);
            assertEquals(FluentTestSupport.text(FluentTestSupport.bundle(Language.ZH_CN),Message.GOVERNANCE_ERROR_VOTE_CLOSED),
                    net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(
                            UiSceneTestSupport.entry(panel.render(),"activity-error").label()),
                    "stored failures must be translated in the viewer's current language");
        }
    }

    @Test void overviewUsesTheFrozenVoterRollAndExcludesExpiredUnsettledVotes() throws Exception {
        try (var fixture = new Fixture(directory)) {
            var known = new GovernanceCommandContext.KnownPlayer(fixture.viewer,"Viewer",Fixture.NOW.minus(Duration.ofDays(100)),Fixture.NOW,false);
            var view = fixture.query.activity(known,Fixture.NOW);
            assertFalse(view.activeMember());
            assertEquals(1,view.matters().size());
            assertTrue(view.matters().getFirst().ballot().voter(),"losing current membership must not rewrite an existing frozen voter roll");
            assertTrue(fixture.query.activity(known,Fixture.NOW.plus(Duration.ofDays(4))).matters().isEmpty());
        }
    }

    @Test void workspaceIncludesPersonalProgressAndFindsAnAppointmentOutsideTheVisibleHistory() throws Exception {
        try (var fixture = new Fixture(directory)) {
            fixture.voting.castBallot(fixture.voteId,fixture.viewer,VoteChoice.YES);
            fixture.voting.castBallot(fixture.voteId,fixture.candidate,VoteChoice.YES);
            var closedAt = fixture.voting.vote(fixture.voteId).closesAt();
            fixture.database.finalizeVote(fixture.voteId,VoteStatus.PASSED,VoteFailure.NONE,null,closedAt);
            var known = new GovernanceCommandContext.KnownPlayer(fixture.candidate,"Alex",Fixture.NOW.minus(Duration.ofDays(100)),Fixture.NOW,true);

            var view = fixture.query.load(known,List.of(),fixture.viewer,java.util.OptionalInt.empty(),null);

            assertTrue(view.history().isEmpty(),"the pending appointment cannot depend on the selected history page or subject");
            assertTrue(view.awaitingAppointment());
            assertFalse(view.canApply(fixture.candidate));
            assertEquals(21600,view.participation().secondsLast60Days());
            assertTrue(view.pendingVotes().contains(fixture.voteId));
            var other = new GovernanceCommandContext.KnownPlayer(fixture.viewer,"Viewer",known.firstJoinedAt(),Fixture.NOW,true);
            assertFalse(fixture.query.load(other,List.of(),null,java.util.OptionalInt.empty(),null).awaitingAppointment());
            var action = fixture.database.pendingActions().getFirst();
            fixture.database.markActionApplied(action.id(),closedAt.plusSeconds(1));
            assertFalse(fixture.query.load(known,List.of(),null,java.util.OptionalInt.empty(),null).awaitingAppointment());
        }
    }

    @Test void historyReadsStayBoundedAndUseSourceBatchesOnlyWhenRequested() throws Exception {
        try (var fixture = new Fixture(directory)) {
            fixture.voting.castBallot(fixture.voteId,fixture.viewer,VoteChoice.YES);
            fixture.voting.castBallot(fixture.voteId,fixture.candidate,VoteChoice.YES);
            fixture.database.finalizeVote(fixture.voteId,VoteStatus.PASSED,VoteFailure.NONE,null,fixture.voting.vote(fixture.voteId).closesAt());
            var voters = List.of(fixture.database.player(fixture.viewer).orElseThrow(),fixture.database.player(fixture.candidate).orElseThrow());
            for (int index = 0; index < 205; index++) {
                var vote = fixture.database.createVote(new VoteProposal.Appointment(fixture.candidate,"Alex"),Fixture.NOW.minus(Duration.ofDays(10)),voters);
                fixture.database.castBallot(vote.id(),fixture.viewer,VoteChoice.YES,vote.opensAt().plusSeconds(1));
                fixture.database.castBallot(vote.id(),fixture.candidate,VoteChoice.YES,vote.opensAt().plusSeconds(2));
                fixture.database.finalizeVote(vote.id(),VoteStatus.PASSED,VoteFailure.NONE,null,vote.closesAt().plusSeconds(index));
            }
            var known = new GovernanceCommandContext.KnownPlayer(fixture.viewer,"Viewer",Fixture.NOW.minus(Duration.ofDays(100)),Fixture.NOW,true);
            var skipped = fixture.query.load(known,List.of(),null,OptionalInt.empty(),null);
            assertTrue(skipped.history().isEmpty()); assertEquals(0,skipped.historyTotal());
            var middle = fixture.worker.submit(() -> fixture.query.load(known,List.of(),null,OptionalInt.of(173),null)).get();
            assertEquals(206,middle.historyTotal()); assertEquals(160,middle.historyOffset()); assertEquals(32,middle.history().size());
            var last = fixture.worker.submit(() -> fixture.query.load(known,List.of(),null,OptionalInt.of(Integer.MAX_VALUE),null)).get();
            assertEquals(192,last.historyOffset()); assertEquals(14,last.history().size());
            assertEquals(206,last.historyTotal());
            assertThrows(IllegalArgumentException.class,() -> fixture.query.load(known,List.of(),null,OptionalInt.of(-1),null));
        }
    }

    private static final class Fixture implements AutoCloseable {
        static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");
        final UUID viewer = new UUID(0,99), candidate = new UUID(0,1);
        final Thread main = Thread.currentThread();
        final Queue<GovernanceTaskExecutor.Task> storage = new ConcurrentLinkedQueue<>();
        final Queue<Runnable> mainTasks = new ConcurrentLinkedQueue<>();
        final List<Poll> polls = new ArrayList<>();
        final List<Component> messages = new ArrayList<>();
        final AtomicReference<Language> language = new AtomicReference<>(Language.EN_US);
        final AtomicReference<FloatingMenuState> lifecycle = new AtomicReference<>(FloatingMenuState.ACTIVE);
        final ExecutorService worker = Executors.newSingleThreadExecutor();
        final SqliteGovernanceRepository database;
        final VotingService voting;
        final GovernanceWorkspaceQuery query;
        final GovernanceActivity activity;
        final Player player;
        final FloatingMenuHandle handle;
        final long voteId;

        Fixture(Path directory) throws Exception {
            database = new SqliteGovernanceRepository(directory.resolve(UUID.randomUUID()+".db").toFile()); database.open();
            var clock = Clock.fixed(NOW,ZoneOffset.UTC);
            var membership = new MembershipService(database,clock,(_,_) -> false,(_,_) -> true);
            for (UUID id : List.of(viewer,candidate)) {
                membership.observeLogin(id,id.equals(viewer)?"Viewer":"Alex",NOW.minus(Duration.ofDays(100)),true);
                for (int day=0;day<6;day++) {
                    Instant start=NOW.minus(Duration.ofDays(day)).minus(Duration.ofHours(2));
                    membership.recordEligibleInterval(id,start,start.plus(Duration.ofHours(1)));
                }
            }
            voting = new VotingService(database,membership,clock,new org.encinet.mik.feature.player.security.EstablishedPasswordAccounts());
            voteId = voting.startVote(new VoteProposal.Appointment(candidate,"Alex")).id();
            query = new GovernanceWorkspaceQuery(membership,voting,new RemovalService(database,database,membership,clock),new DeliveryService(database));
            var bundle = FluentTestSupport.bundle(Language.EN_US);
            var chinese = FluentTestSupport.bundle(Language.ZH_CN);
            var languages = new LanguageService(null) {
                @Override public Language language(Player ignored) { return language.get(); }
                @Override public String t(Language selected,Message message,Object... args) { return FluentTestSupport.text(selected == Language.ZH_CN ? chinese : bundle,message,args); }
            };
            player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args) -> switch(method.getName()) {
                case "getUniqueId" -> viewer;
                case "getName" -> "Viewer";
                case "getFirstPlayed" -> NOW.minus(Duration.ofDays(100)).toEpochMilli();
                case "getLastLogin" -> NOW.toEpochMilli();
                case "getPlayer" -> proxy;
                case "isOnline","hasPermission" -> true;
                case "sendMessage" -> { messages.add((Component)args[0]); yield null; }
                case "equals" -> proxy==args[0];
                case "hashCode" -> viewer.hashCode();
                case "toString" -> "Viewer";
                default -> throw new AssertionError(method.getName());
            });
            var scheduler = (BukkitScheduler) Proxy.newProxyInstance(BukkitScheduler.class.getClassLoader(),new Class<?>[]{BukkitScheduler.class},(_,method,args) -> {
                if (method.getName().equals("runTask")) { mainTasks.add((Runnable)args[1]); return null; }
                if (method.getName().equals("runTaskTimer")) {
                    var poll = new Poll((Runnable)args[1]); polls.add(poll);
                    return Proxy.newProxyInstance(BukkitTask.class.getClassLoader(),new Class<?>[]{BukkitTask.class},(_,m,_) -> switch(m.getName()) {
                        case "cancel" -> { poll.cancelled.set(true); yield null; }
                        case "isCancelled" -> poll.cancelled.get();
                        default -> throw new AssertionError(m.getName());
                    });
                }
                throw new AssertionError(method.getName());
            });
            var server = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(),new Class<?>[]{Server.class},(_,method,_) -> {
                if (method.getName().equals("getScheduler")) return scheduler;
                throw new AssertionError(method.getName());
            });
            var plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(),new Class<?>[]{Plugin.class},(_,method,_) -> switch(method.getName()) {
                case "isEnabled" -> true;
                case "getServer" -> server;
                case "getLogger" -> Logger.getAnonymousLogger();
                default -> throw new AssertionError(method.getName());
            });
            var context = new GovernanceCommandContext(plugin,membership,storage::add,new GovernanceText(languages));
            activity = new GovernanceActivity(plugin,context,query,new GovernanceText(languages),voting,null,_ -> {},clock);
            handle = (FloatingMenuHandle) Proxy.newProxyInstance(FloatingMenuHandle.class.getClassLoader(),new Class<?>[]{FloatingMenuHandle.class},(_,method,_) -> {
                if (method.getName().equals("state")) return lifecycle.get();
                throw new AssertionError(method.getName());
            });
        }

        void runStorage() throws Exception {
            var task = storage.remove();
            worker.submit(() -> {
                assertNotSame(main,Thread.currentThread());
                try { task.run(); } catch (Exception error) { throw new CompletionException(error); }
            }).get(10,TimeUnit.SECONDS);
        }
        void flushMain() { while (!mainTasks.isEmpty()) mainTasks.remove().run(); }
        @Override public void close() throws org.encinet.mik.feature.community.governance.GovernanceRepositoryException {
            activity.close();
            worker.shutdownNow();
            try {
                assertTrue(worker.awaitTermination(10, TimeUnit.SECONDS));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while closing governance worker", error);
            } finally {
                database.close();
            }
        }
        private record Poll(Runnable task,AtomicBoolean cancelled) { Poll(Runnable task) { this(task,new AtomicBoolean()); } }
    }
}
