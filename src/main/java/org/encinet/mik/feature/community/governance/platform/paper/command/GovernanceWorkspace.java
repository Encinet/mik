package org.encinet.mik.feature.community.governance.platform.paper.command;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.BiFunction;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.community.governance.platform.paper.GovernanceText;
import org.encinet.mik.feature.community.governance.roster.StaffDirectory;
import org.encinet.mik.feature.community.governance.roster.StaffMember;
import org.encinet.mik.feature.player.security.PasswordAccess;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.encinet.mik.shared.role.PlayerRole;
import org.encinet.mik.shared.role.RolePermissions;
import org.encinet.mik.shared.util.PlayerNames;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernanceWorkspaceState.*;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernanceWorkspaceAction.*;

/** Owns requests and navigation; UI declarations never perform storage or permission-provider reads. */
final class GovernanceWorkspace implements AutoCloseable {
    private final JavaPlugin plugin;
    private final GovernanceCommandContext context;
    private final GovernanceText text;
    private final StaffDirectory directory;
    private final GovernanceWorkspaceQuery query;
    private final VotingCommandHandler votes;
    private final RemovalCommandHandler removals;
    private final PasswordAccess passwords;
    private final BiFunction<Player, Runnable, MenuPanel> identity;
    private final Consumer<Player> players;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private volatile boolean closed;

    GovernanceWorkspace(JavaPlugin plugin, GovernanceCommandContext context, GovernanceText text, StaffDirectory directory,
                        GovernanceWorkspaceQuery query, VotingCommandHandler votes, RemovalCommandHandler removals,
                        PasswordAccess passwords, BiFunction<Player, Runnable, MenuPanel> identity, Consumer<Player> players) {
        this.plugin = plugin; this.context = context; this.text = text; this.directory = directory;
        this.query = query; this.votes = votes; this.removals = removals; this.passwords = passwords;
        this.identity = identity; this.players = players;
    }

    void open(Player player) {
        open(player, null);
    }

    void openMatters(Player player) {
        open(player, Section.MATTERS);
    }

    private void open(Player player, Section section) {
        if (closed) return;
        Session existing = sessions.get(player.getUniqueId());
        if (existing != null && section != null) {
            existing.state.section(section);
            existing.state.filter(Filter.ONGOING);
            existing.state.clearSubject();
        }
        if (existing != null && existing.current()) { existing.reload(); existing.redraw(); return; }
        if (existing != null && existing.handle != null && existing.handle.state() == FloatingMenuState.SUSPENDED) {
            existing.restore(); return;
        }
        Session previous = sessions.remove(player.getUniqueId());
        if (previous != null) previous.release();
        var state = new GovernanceWorkspaceState();
        if (section != null) state.section(section);
        present(player, state);
    }

    private void present(Player player, GovernanceWorkspaceState state) {
        var session = new Session(player, state);
        sessions.put(player.getUniqueId(), session);
        session.handle = FloatingMenus.present(player, session.scene());
        session.reload();
    }

    @Override public void close() {
        closed = true;
        List.copyOf(sessions.values()).forEach(Session::release);
        sessions.clear();
    }

    private final class Session {
        final Player player;
        final GovernanceWorkspaceState state;
        GovernanceWorkspaceData data;
        FloatingMenuHandle handle;
        MenuQuerySession search;
        boolean failed, requesting;
        volatile boolean released;
        long generation;
        private record HistoryScope(UUID subject,int offset) { }
        java.util.Optional<HistoryScope> loadedHistory = java.util.Optional.empty();

        Session(Player player, GovernanceWorkspaceState state) { this.player = player; this.state = state; }

        boolean current() {
            return !released && player.isOnline() && sessions.get(player.getUniqueId()) == this && handle != null
                    && FloatingMenus.current(player).map(active -> active.id().equals(handle.id())).orElse(false);
        }

        void reload() {
            if (!current()) return;
            long request = ++generation;
            requesting = true;
            var known = context.snapshot(player);
            UUID subject = state.subjectFilter;
            var historyAnchor = state.section == Section.MATTERS && state.filter == Filter.COMPLETED
                    ? java.util.OptionalInt.of(state.matterAnchor) : java.util.OptionalInt.empty();
            UUID related = state.popup != null && (state.popup.intent() == Intent.DETAILS || state.popup.returnToDetails()) ? state.popup.subject() : null;
            directory.members().thenApplyAsync(staff -> staff.stream().map(member -> new StaffMember(member.id(),
                    PlayerNames.resolve(member.id(), member.name()), member.role(), member.subjectToRemoval()))
                    .sorted(Comparator.comparing(StaffMember::role).thenComparing(StaffMember::name, String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(StaffMember::id)).toList(), Bukkit.getScheduler().getMainThreadExecutor(plugin))
                    .whenComplete((staff, error) -> {
                if (closed || released) return;
                if (error != null) {
                    plugin.getLogger().log(Level.WARNING, "Could not load public governance staff", GovernanceCommandContext.unwrap(error));
                    context.onMain(() -> failed(request));
                    return;
                }
                context.submit(player, () -> {
                    if (closed || released) return;
                    context.ensureKnown(known);
                    var loaded = query.load(known, staff, subject, historyAnchor, related);
                    context.onMain(() -> {
                        if (!current() || request != generation) return;
                        requesting = false; failed = false; state.busy = false; data = loaded; state.detailRevision++;
                        loadedHistory = historyAnchor.isPresent() ? java.util.Optional.of(new HistoryScope(subject,loaded.historyOffset())) : java.util.Optional.empty();
                        state.reconcileStaff(loaded.staff());
                        state.reconcileMatters(loaded);
                        redraw();
                    });
                }, () -> failed(request));
            });
        }

        void failed(long request) {
            if (!current() || generation != request) return;
            requesting = false; failed = true; state.busy = false; redraw();
        }

        void redraw() { if (current()) handle.update(scene()); }

        boolean historyReady() {
            if (state.section != Section.MATTERS || state.filter != Filter.COMPLETED) return true;
            return loadedHistory.filter(scope -> Objects.equals(scope.subject(),state.subjectFilter)
                    && scope.offset() == state.matterAnchor / GovernanceMatterWindow.HISTORY_BATCH_SIZE * GovernanceMatterWindow.HISTORY_BATCH_SIZE).isPresent();
        }

        MenuScene scene() {
            var viewer = new GovernanceWorkspaceScenes.Viewer(player.getUniqueId(), PlayerRole.resolve(player), passwords.hasPassword(player.getUniqueId()));
            if (viewer.staff() && state.popup != null
                    && (state.popup.intent() == Intent.APPLICATION || state.popup.intent() == Intent.WITHDRAW)) state.popup = null;
            UiNode searchContent = null;
            if (state.section == Section.TEAM && data != null && (data.staff().size() > 5 || !state.query.isBlank())) {
                if (search == null) search = FloatingMenus.queryInput(player,
                        net.kyori.adventure.text.Component.text(text.t(text.language(player), Message.GOVERNANCE_MENU_STAFF_SEARCH)), 64,
                        value -> { state.search(value); reload(); redraw(); }, this::redraw);
                searchContent = search.content(state.query);
            }
            GovernanceWorkspaceData displayed = historyReady() ? data : null;
            return new GovernanceWorkspaceScenes(text, text.language(player), viewer, state, displayed, this::act, id -> {
                Player target = Bukkit.getPlayer(id);
                return target != null && target.isOnline() && player.canSee(target);
            }).render(searchContent, failed).toBuilder()
                    .loading(displayed == null && !failed)
                    .refreshEvery(100, (_,_) -> { if (!state.busy && !requesting) reload(); })
                    .lifecycle((p,h,previous,next,_) -> {
                        if (next == FloatingMenuState.SUSPENDED) { generation++; requesting = false; closeSearch(); }
                        if (next == FloatingMenuState.ACTIVE && previous == FloatingMenuState.SUSPENDED) { handle = h; reload(); }
                        if (next == FloatingMenuState.CLOSED) { release(); sessions.remove(p.getUniqueId(), this); }
                    }).build();
        }

        void act(GovernanceWorkspaceAction action) {
            if (!current() || state.busy && action != Command.DISMISS_POPUP && action != Command.CLOSE
                    && !(action instanceof ReadMatter) && !(action instanceof ReadPopup)) return;
            if (!historyReady() && (action instanceof SelectMatter || action instanceof ReadMatter || action instanceof ReadMatterWindow)) return;
            switch (action) {
                case Navigate navigation -> {
                    if (state.section == navigation.section()) return;
                    closeSearch(); state.section(navigation.section()); reload();
                }
                case FilterMatters filter -> { state.filter(filter.filter()); reload(); }
                case StaffPage page -> { state.staffPage = Math.max(0, page.index()); state.popup = null; }
                case ReadMatterWindow window -> {
                    if (!state.readWindow(window.revision(),window.source(),window.sourceIndex(),data)) return;
                    if (!historyReady()) reload();
                }
                case ReadPopup reading -> {
                    if (!state.readPopup(reading.revision(),reading.source(),reading.position())) return;
                }
                case SelectStaff staff -> {
                    if (data == null || data.staff(staff.id()) == null) return;
                    closeSearch();
                    state.popup = new Popup(staff.source(), staff.id(), Intent.DETAILS, false);
                    // Do not momentarily display the previous person's records while the new read is pending.
                    data = new GovernanceWorkspaceData(data.staff(), data.openVotes(), data.petitions(), data.history(), data.historyTotal(),
                            data.historyOffset(), List.of(), data.active(), data.participation(), data.pendingVotes(), data.awaitingAppointment());
                    reload();
                }
                case SelectMatter matter -> {
                    if (data == null) return;
                    if (state.section != Section.MATTERS) {
                        closeSearch(); state.section(Section.MATTERS); state.filter(Filter.ONGOING); state.clearSubject();
                    }
                    var window = GovernanceMatterWindow.of(state,data);
                    int selected = -1;
                    for (int index = 0; index < window.items().size(); index++) if (window.items().get(index).id().equals(matter.id())) { selected = index; break; }
                    if (selected < 0) return;
                    state.matterAnchor = window.offset() + selected;
                    if (!Objects.equals(state.selectedMatter,matter.id())) state.resetMatterReading();
                    state.selectedMatter = matter.id(); state.popup = null;
                }
                case ReadMatter reading -> {
                    if (!state.readMatter(reading.revision(),reading.id(),reading.position(),data)) return;
                }
                case OpenPopup popup -> {
                    closeSearch();
                    state.popup = state.popup != null && state.popup.source().equals(popup.source())
                            ? state.popup.confirm(popup.intent()) : new Popup(popup.source(), popup.subject(), popup.intent(), false);
                }
                case Related related -> { closeSearch(); state.related(related.id(), related.name()); reload(); }
                case Cast ballot -> perform(completed -> votes.vote(player, ballot.id(), ballot.choice().name(), completed));
                case Command command -> {
                    switch (command) {
                        case REFRESH -> reload();
                        case CLOSE -> handle.close();
                        case IDENTITY -> {
                            state.popup = null; closeSearch(); redraw();
                            handle.openPopover(UiKey.named("identity"), changed -> identity.apply(player, changed));
                        }
                        case PLAYERS -> { state.popup = null; closeSearch(); redraw(); players.accept(player); }
                        case PASSWORD -> { closeSearch(); passwords.open(player, this::restore); }
                        case DISMISS_POPUP -> state.popup = null;
                        case CANCEL_CONFIRM -> state.cancelConfirmation();
                        case CLEAR_FILTER -> { state.clearSubject(); reload(); }
                        case PERFORM -> confirm();
                    }
                }
            }
            redraw();
        }

        void confirm() {
            Popup popup = state.popup;
            if (popup == null || popup.intent() == Intent.DETAILS) return;
            if (popup.intent() == Intent.APPLICATION && (data == null || !data.canApply(player.getUniqueId())
                    || !passwords.hasPassword(player.getUniqueId()) || RolePermissions.canModerate(player))) {
                redraw(); return;
            }
            if (popup.intent() == Intent.WITHDRAW && (data == null || !data.nominated(player.getUniqueId()))) {
                redraw(); return;
            }
            perform(completed -> {
                switch (popup.intent()) {
                    case APPLICATION -> votes.nominate(player, completed);
                    case WITHDRAW -> votes.withdraw(player, completed);
                    case SPONSOR -> removals.sponsorRemoval(player, popup.subject(), completed);
                    case RESIGN -> removals.resign(player, completed);
                    case DETAILS -> throw new IllegalStateException();
                }
            });
        }

        void perform(Consumer<Runnable> operation) {
            state.busy = true; generation++; requesting = false; redraw();
            try {
                operation.accept(() -> {
                    if (released) return;
                    state.cancelConfirmation();
                    // Keep controls blocked until the authoritative state has been read back.
                    reload(); redraw();
                });
            } catch (RuntimeException error) {
                state.busy = false;
                context.reportFailure(player, error);
                redraw();
            }
        }

        void restore() {
            if (closed || !player.isOnline()) return;
            if (released) {
                var previous = sessions.remove(player.getUniqueId());
                if (previous != null) previous.release();
                present(player, state.navigationCopy());
                return;
            }
            handle = FloatingMenus.present(player, scene());
            reload();
        }

        void closeSearch() { if (search != null) { var input = search; search = null; input.close(); } }
        void release() { released = true; generation++; closeSearch(); }
    }
}
