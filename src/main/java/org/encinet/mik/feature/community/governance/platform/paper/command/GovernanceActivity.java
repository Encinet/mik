package org.encinet.mik.feature.community.governance.platform.paper.command;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.feature.community.governance.GovernanceException;
import org.encinet.mik.feature.community.governance.platform.paper.GovernanceText;
import org.encinet.mik.feature.community.governance.voting.VotingService;
import org.encinet.mik.feature.community.governance.voting.model.VoteChoice;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import static net.kyori.adventure.text.format.NamedTextColor.*;

/** Feature-owned embedded activity. Only visible hosts poll; storage never runs in menu rendering. */
final class GovernanceActivity implements AutoCloseable {
    private static final int POLL_TICKS = 100;
    private final Plugin plugin;
    private final GovernanceCommandContext context;
    private final GovernanceWorkspaceQuery query;
    private final GovernanceText text;
    private final VotingService voting;
    private final RemovalCommandHandler removal;
    private final java.util.function.Consumer<Player> openMatters;
    private final Clock clock;
    private final Set<Panel> panels = new HashSet<>();
    private volatile boolean closed;

    GovernanceActivity(Plugin plugin, GovernanceCommandContext context, GovernanceWorkspaceQuery query,
                       GovernanceText text, VotingService voting, RemovalCommandHandler removal,
                       java.util.function.Consumer<Player> openMatters) {
        this(plugin, context, query, text, voting, removal, openMatters, Clock.systemUTC());
    }

    GovernanceActivity(Plugin plugin, GovernanceCommandContext context, GovernanceWorkspaceQuery query,
                       GovernanceText text, VotingService voting, RemovalCommandHandler removal,
                       java.util.function.Consumer<Player> openMatters, Clock clock) {
        this.plugin = plugin; this.context = context; this.query = query; this.text = text;
        this.voting = voting; this.removal = removal; this.openMatters = openMatters; this.clock = clock;
    }

    MenuPanel panel(Player player, Runnable redraw) {
        if (closed) throw new IllegalStateException("Governance activity is closed");
        var panel = new Panel(player, redraw);
        panels.add(panel);
        return panel;
    }

    @Override public void close() {
        closed = true;
        List.copyOf(panels).forEach(Panel::close);
    }

    private final class Panel implements MenuPanel, GovernanceActivityScenes.Actions {
        private final Player player;
        private final Runnable redraw;
        private final GovernanceActivityState state = new GovernanceActivityState();
        private FloatingMenuHandle handle;
        private BukkitTask polling;
        private CompletableFuture<?> read;
        private Detail detail;
        private Message readFailure, submissionFailure;
        private volatile boolean released;
        private boolean pending, reading;
        private long generation;

        Panel(Player player, Runnable redraw) { this.player = player; this.redraw = redraw; }

        @Override public void attach(FloatingMenuHandle handle) {
            if (released) return;
            this.handle = handle;
            if (visible() && polling == null) {
                load();
                polling = plugin.getServer().getScheduler().runTaskTimer(plugin, this::load, POLL_TICKS, POLL_TICKS);
            }
        }

        private boolean visible() {
            return !closed && !released && player.isOnline() && handle != null
                    && (handle.state() == FloatingMenuState.OPENING || handle.state() == FloatingMenuState.ACTIVE);
        }

        private GovernanceActivityScenes scenes(boolean showFailure) {
            Message failure = showFailure ? submissionFailure != null ? submissionFailure : readFailure : null;
            var language = text.language(player);
            return new GovernanceActivityScenes(text, language, player.getUniqueId(), state,
                    clock.instant(), pending, failure == null ? null : Component.text(text.t(language, failure), RED), this);
        }

        @Override public MenuScene render() {
            return MenuScene.screen("governance-activity").content(scenes(detail == null).content())
                    .lifecycle((_,_,_,next,_) -> {
                        if (next == FloatingMenuState.SUSPENDED) pause();
                        if (next == FloatingMenuState.CLOSING || next == FloatingMenuState.CLOSED) close();
                    }).build();
        }

        @Override public void reload() {
            if (!visible() || pending) return;
            readFailure = null; submissionFailure = null;
            load(); changed();
        }

        private void load() {
            if (!visible() || pending || reading) return;
            reading = true;
            long request = ++generation;
            var known = context.snapshot(player);
            read = context.query(() -> {
                if (released || closed) return null;
                context.ensureKnown(known);
                return query.activity(known, clock.instant());
            }).whenComplete((loaded, error) -> context.onMain(() -> {
                if (released || request != generation) return;
                reading = false; read = null;
                if (!visible()) return;
                if (error == null) { state.update(loaded); readFailure = null; }
                else readFailure = error(error);
                changed();
            }));
        }

        private void changed() {
            if (!visible()) return;
            redraw.run();
            if (detail != null) detail.redraw.run();
        }

        @Override public void open(GovernanceMatter matter, UiKey source, boolean confirm) {
            if (!visible() || pending) return;
            var current = state.data() == null ? null : state.data().find(matter.id());
            if (current == null || !state.current(current, clock.instant())) { reload(); return; }
            if (detail != null && detail.original.id().equals(current.id())) {
                detail.confirming = confirm;
                detail.redraw.run();
                return;
            }
            handle.openPopover(source, redraw -> {
                var companion = new Detail(current, redraw, confirm);
                detail = companion;
                return companion;
            });
        }

        @Override public void cast(GovernanceMatter matter, VoteChoice choice) {
            if (matter.ballot() == null || !matter.ballot().voter() || matter.ballot().choice() == choice) return;
            UUID voter = player.getUniqueId();
            submit(matter, () -> context.query(() -> voting.castBallot(matter.ballot().vote().id(), voter, choice)));
        }

        @Override public void join(GovernanceMatter matter) {
            if (detail == null || !detail.confirming || !detail.original.id().equals(matter.id()) || matter.petition() == null) return;
            submit(matter, () -> removal.joinPetition(player, matter.petition().petition()));
        }

        private void submit(GovernanceMatter matter, Supplier<? extends CompletionStage<?>> operation) {
            if (!visible() || pending || !state.current(matter, clock.instant())) return;
            generation++; reading = false;
            if (read != null) { read.cancel(false); read = null; }
            pending = true; readFailure = null; submissionFailure = null; state.hold();
            var known = context.snapshot(player);
            changed();
            CompletionStage<?> submitted;
            try { submitted = operation.get(); }
            catch (RuntimeException error) { submitted = CompletableFuture.failedFuture(error); }
            submitted.thenCompose(_ -> context.query(() -> query.activity(known, clock.instant())))
                    .whenComplete((loaded, error) -> context.onMain(() -> {
                if (released) return;
                if (error == null) { state.update(loaded); readFailure = null; }
                else submissionFailure = error(error);
                pending = false; state.release();
                if (detail != null) detail.confirming = false;
                changed();
                if (error == null) for (var panel : List.copyOf(panels)) if (panel != this) panel.load();
            }));
        }

        @Override public void all() { if (visible() && !pending) openMatters.accept(player); }

        private Message error(Throwable error) {
            Throwable cause = GovernanceCommandContext.unwrap(error);
            Message message;
            if (cause instanceof GovernanceException domain && domain.code() != null && domain.getCause() == null)
                message = text.error(domain.code());
            else {
                plugin.getLogger().log(Level.WARNING, "Could not update governance activity for " + player.getUniqueId(), cause);
                message = Message.GOVERNANCE_OPERATION_ERROR;
            }
            return message;
        }

        private void pause() {
            generation++; reading = false;
            if (read != null) { read.cancel(false); read = null; }
            if (polling != null) { polling.cancel(); polling = null; }
        }

        @Override public void close() {
            if (released) return;
            released = true; pause();
            if (detail != null) {
                if (handle != null) handle.closePopover();
                if (detail != null) detail.close();
            }
            panels.remove(this);
        }

        private final class Detail implements MenuPanel {
            private final GovernanceMatter original;
            private final Runnable redraw;
            private boolean confirming, dismissed;
            private UiReadingPosition position = UiReadingPosition.START;

            Detail(GovernanceMatter matter, Runnable redraw, boolean confirming) {
                this.original = matter; this.redraw = redraw; this.confirming = confirming;
                state.hold();
            }

            @Override public MenuScene render() {
                if (dismissed || released) return MenuScene.screen("governance-activity-detail-closed").content(Ui.column().build()).build();
                var snapshot = state.data();
                var latest = snapshot.find(original.id());
                var matter = latest == null ? original : latest;
                return MenuScene.screen("governance-activity-detail", scenes(true).proposal(matter))
                        .content(scenes(true).details(matter, confirming, position, next -> {
                            if (dismissed || released || detail != this || state.data() != snapshot) return;
                            position = next; redraw.run();
                        }, () -> {
                            if (pending || dismissed || released || detail != this) return;
                            confirming = false; redraw.run();
                        })).build();
            }

            @Override public UiNode headerActions() {
                if (dismissed || released) return null;
                var refresh = Ui.button(reading ? UiIcons.LOADING : UiIcons.REFRESH,
                        Component.text(text.t(text.language(player), Message.GOVERNANCE_MENU_REFRESH), WHITE))
                        .key("refresh").primary((_,_) -> { if (!dismissed && !released && detail == this) reload(); });
                if (reading || pending) refresh.disabled(Component.text(text.t(text.language(player), Message.GOVERNANCE_MENU_LOADING), YELLOW));
                return refresh.build();
            }

            @Override public void close() {
                if (dismissed) return;
                dismissed = true;
                if (detail == this) detail = null;
                state.release();
                context.onMain(Panel.this::changed);
            }
        }
    }
}
