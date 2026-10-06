package org.encinet.mik.module.plot;

import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.plot.PlotGeometry.Cell;

import java.math.BigInteger;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Validates and persists edits to existing plots without depending on menus or players. */
final class PlotEdits {
    private final PlotRegistry registry;

    PlotEdits(PlotRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    PlotEdits validationSnapshot() { return new PlotEdits(registry.snapshot()); }

    <Result> CompletableFuture<Result> writeAsync(Mutation<Result> operation) throws SQLException {
        return registry.writeAsync(staged -> operation.apply(new PlotEdits(staged)));
    }

    <Result> CompletableFuture<Result> writeBehind(Mutation<Result> operation) throws SQLException {
        return registry.writeBehind(staged -> operation.apply(new PlotEdits(staged)));
    }

    interface Mutation<Result> { Result apply(PlotEdits edits) throws SQLException; }

    Plot managed(UUID actor, boolean staff, String prefix) {
        Plot plot = find(prefix);
        if (!registry.canManage(plot, actor, staff))
            throw new PlotProblem(Message.PLOT_ERROR_OWNER_ONLY);
        return plot;
    }

    Plot requirePermission(Plot plot, UUID actor, boolean staff, PlotPermission permission) {
        plot = current(plot);
        if (!registry.permitted(plot, actor, staff, permission))
            throw new PlotProblem(Message.PLOT_ERROR_OWNER_ONLY);
        return plot;
    }

    Plot releasable(UUID actor, boolean staff, String prefix) {
        Plot plot = find(prefix);
        if (!registry.canRelease(plot, actor, staff))
            throw new PlotProblem(Message.PLOT_ERROR_RELEASE_OWNER_ONLY);
        return plot;
    }

    private Plot find(String prefix) {
        List<Plot> matches = registry.all().stream()
                .filter(plot -> plot.id().toString().startsWith(prefix)).toList();
        if (matches.size() != 1) throw new PlotProblem(Message.PLOT_ERROR_ID);
        return matches.getFirst();
    }

    private Plot current(Plot plot) {
        Plot latest = registry.byId(plot.id());
        if (latest == null) throw new PlotProblem(Message.PLOT_ERROR_ID);
        return latest;
    }

    Set<Cell> creatableCells(UUID world, PlotSelectionShape shape) {
        validateCreatable(world, shape);
        return shape.alignedCells();
    }

    void validateCreatable(UUID world, PlotSelectionShape shape) {
        requireSelection(world, shape);
        if (registry.overlapsSelection(world, null, shape)) throw new PlotProblem(Message.PLOT_ERROR_OVERLAP);
    }

    Plot create(UUID owner, String name, UUID world, PlotSelectionShape shape) throws SQLException {
        registry.requireGeometry();
        validName(name);
        Set<Cell> cells = creatableCells(world, shape);
        Plot plot = new Plot(UUID.randomUUID(), world, owner, name, false, System.currentTimeMillis(),
                cells, Map.of(), Map.of(), null);
        registry.put(plot);
        return plot;
    }

    BigInteger resizedCellCount(Plot plot, PlotSelectionShape shape) {
        plot = current(plot);
        if (plot.subPlot()) return BigInteger.valueOf(resizedCells(plot, shape).size());
        validateResize(plot, shape);
        return shape.alignedCellCount();
    }

    BigInteger subPlotCellCount(Plot parent, PlotSelectionShape shape) {
        parent = current(parent);
        if (parent.subPlot()) throw new PlotProblem(Message.PLOT_ERROR_SUBPLOT_DEPTH);
        requireSelection(parent.world(), shape);
        return BigInteger.valueOf(registry.creatableSubPlotCells(parent, shape).size());
    }

    Set<Cell> resizedCells(Plot plot, PlotSelectionShape shape) {
        plot = current(plot);
        if (plot.subPlot()) return registry.resizedSubPlotCells(plot, shape);
        validateResize(plot, shape);
        return shape.alignedCells();
    }

    void validateResize(Plot plot, PlotSelectionShape shape) {
        plot = current(plot);
        if (plot.subPlot()) {
            registry.resizedSubPlotCells(plot, shape);
            return;
        }
        requireSelection(plot.world(), shape);
        if (registry.childrenOf(plot.id()).stream().anyMatch(child -> child.cells().stream().anyMatch(cell -> !shape.contains(cell))))
            throw new PlotProblem(Message.PLOT_ERROR_SUBPLOT_PARENT);
        PlotArrival arrival = registry.arrival(plot.id());
        if (arrival != null && !shape.contains(Cell.at(arrival.blockX(), arrival.blockY(), arrival.blockZ())))
            throw new PlotProblem(Message.PLOT_ERROR_SUBPLOT_OWN_ARRIVAL);
        if (registry.overlapsSelection(plot.world(), plot.id(), shape)) throw new PlotProblem(Message.PLOT_ERROR_OVERLAP);
    }

    void resize(Plot plot, UUID actor, boolean staff, PlotSelectionShape shape) throws SQLException {
        registry.requireGeometry();
        Plot latest = current(plot);
        requirePermission(latest, actor, staff, PlotPermission.MANAGE_AREA);
        if (!plot.cells().equals(latest.cells())) throw new PlotProblem(Message.PLOT_AREA_CHANGED);
        plot = latest;
        Set<Cell> cells = resizedCells(plot, shape);
        registry.put(new Plot(plot.id(), plot.world(), plot.owner(), plot.name(), plot.publicProject(),
                plot.createdAt(), cells, plot.members(), plot.flags(), plot.parentId()));
    }

    private static void requireSelection(UUID world, PlotSelectionShape shape) {
        if (!world.equals(shape.world()) || shape.empty()) throw new PlotProblem(Message.PLOT_ERROR_SELECTION);
        if (shape.composite() && !PlotGeometry.connected(shape.cells())) throw new PlotProblem(Message.PLOT_ERROR_AREA_DISCONNECTED);
    }

    PlotAccessPlan planAccess(Plot plot, PlotAccessRequest request) {
        plot = current(plot);
        return PlotAccessPlan.prepare(plot, registry.parentOf(plot), registry.childrenOf(plot.id()), request);
    }

    void authorizeAccess(PlotAccessPlan plan, UUID actor, boolean staff) {
        Plot plot = current(plan.before());
        if (!plan.current(registry)) throw new PlotProblem(Message.PLOT_ERROR_PERMISSION_CHANGED);
        boolean membership = plan.request() instanceof PlotAccessRequest.Member
                || plan.request() instanceof PlotAccessRequest.Remove;
        requirePermission(plot, actor, staff, membership ? PlotPermission.MANAGE_MEMBERS
                : PlotPermission.MANAGE_PERMISSIONS);
        if (membership) {
            UUID target = plan.request().subject().player();
            Plot parent = registry.parentOf(plot);
            boolean management = PlotPermission.Category.MANAGEMENT.permissions().stream().anyMatch(permission ->
                    memberManagement(plan.before(), parent, target, permission)
                            || memberManagement(plan.after(), parent, target, permission));
            if (management && !registry.permitted(plot, actor, staff, PlotPermission.MANAGE_PERMISSIONS))
                throw new PlotProblem(Message.PLOT_ERROR_ADMIN_OWNER_ONLY);
        } else if ((plan.request().subject() == null
                || plan.request().subject().group() == PlotAccessPolicy.Group.OWNER)
                && !registry.recoveryAuthority(plot, actor, staff))
            throw new PlotProblem(Message.PLOT_ERROR_PERMISSION_OWNER_SCOPE);
        if (registry.recoveryAuthority(plot, actor, staff)) return;
        for (PlotAccessPlan.Effect effect : plan.effects()) {
            if (effect.permission().category() == PlotPermission.Category.MANAGEMENT && effect.after()
                    && !registry.permitted(plot, actor, staff, effect.permission()))
                throw new PlotProblem(Message.PLOT_ERROR_PERMISSION_GRANT);
        }
        for (PlotPermission permission : PlotPermission.Category.MANAGEMENT.permissions()) {
            PlotAccessPolicy.Subject subject = plan.request().subject();
            if (subject != null && Boolean.TRUE.equals(plan.after().flags().get(subject.key(permission.key())))
                    && !Boolean.TRUE.equals(plan.before().flags().get(subject.key(permission.key())))
                    && !registry.permitted(plot, actor, staff, permission))
                throw new PlotProblem(Message.PLOT_ERROR_PERMISSION_GRANT);
        }
    }

    private static boolean memberManagement(Plot plot, Plot parent, UUID player, PlotPermission permission) {
        return plot.members().containsKey(player) && plot.allows(parent, permission.key(), player, false, false);
    }

    void applyAccess(PlotAccessPlan plan, UUID actor, boolean staff) throws SQLException {
        if (!plan.current(registry)) throw new PlotProblem(Message.PLOT_ERROR_PERMISSION_CHANGED);
        PlotAccessPlan expected = planAccess(plan.before(), plan.request());
        if (!expected.after().equals(plan.after())) throw new PlotProblem(Message.PLOT_ERROR_PERMISSION_CHANGED);
        authorizeAccess(expected, actor, staff);
        if (!plan.before().equals(plan.after())) registry.put(expected.after());
    }

    void invite(Plot plot, UUID actor, boolean staff, UUID guest, Plot.Role role) throws SQLException {
        plot = current(plot);
        invite(plot, actor, staff, guest, role, PlotAccessPolicy.personalOverrides(plot, guest));
    }

    void invite(Plot plot, UUID actor, boolean staff, UUID guest, Plot.Role role,
                Map<PlotPermission, Boolean> overrides) throws SQLException {
        applyAccess(planAccess(plot, new PlotAccessRequest.Member(guest, role, overrides)), actor, staff);
    }

    void removeMember(Plot plot, UUID actor, boolean staff, UUID guest) throws SQLException {
        applyAccess(planAccess(plot, new PlotAccessRequest.Remove(guest)), actor, staff);
    }

    void transfer(Plot plot, UUID actor, UUID successor) throws SQLException {
        transfer(plot, actor, false, successor);
    }

    void transfer(Plot plot, UUID actor, boolean staff, UUID successor) throws SQLException {
        plot = current(plot);
        if (!registry.permitted(plot, actor, staff, PlotPermission.TRANSFER))
            throw new PlotProblem(Message.PLOT_ERROR_TRANSFER_OWNER_ONLY);
        if (plot.owner().equals(successor))
            throw new PlotProblem(Message.PLOT_ERROR_TRANSFER_SELF);
        Map<UUID, Plot.Role> members = new HashMap<>(plot.members());
        members.remove(successor);
        members.remove(plot.owner());
        Map<String, Boolean> flags = PlotAccessPolicy.withoutSubject(plot.flags(),
                PlotAccessPolicy.Subject.player(successor), null);
        flags = PlotAccessPolicy.withoutSubject(flags, PlotAccessPolicy.Subject.player(plot.owner()), null);
        registry.put(new Plot(plot.id(), plot.world(), successor, plot.name(),
                plot.publicProject(), plot.createdAt(), plot.cells(), members,
                flags, plot.parentId()));
    }

    void setGroupAccess(Plot plot, UUID actor, boolean staff, PlotAccessPolicy.Group group,
                        String action, Boolean allowed) throws SQLException {
        PlotPermission permission = PlotPermission.fromKey(action);
        if (permission == null) throw new PlotProblem(Message.PLOT_ERROR_FLAG);
        applyAccess(planAccess(plot, new PlotAccessRequest.Permission(PlotAccessPolicy.Subject.group(group),
                permission, allowed)), actor, staff);
    }

    PlotAccessPolicy.Setting cycleGroupAccess(Plot plot, UUID actor, boolean staff,
                                            PlotAccessPolicy.Group group, String action) throws SQLException {
        return cycleAccess(plot, actor, staff, PlotAccessPolicy.Subject.group(group), action);
    }

    void resetAccessToParent(Plot plot, UUID actor, boolean staff) throws SQLException {
        plot = current(plot);
        if (!plot.subPlot()) throw new PlotProblem(Message.PLOT_ERROR_SUBPLOT_PARENT);
        applyAccess(planAccess(plot, new PlotAccessRequest.Reset(null, null)), actor, staff);
    }

    PlotAccessPolicy.Setting cycleAccess(Plot plot, UUID actor, boolean staff,
                                         PlotAccessPolicy.Subject subject, String action) throws SQLException {
        plot = current(plot);
        PlotPermission permission = PlotPermission.fromKey(action);
        if (permission == null) throw new PlotProblem(Message.PLOT_ERROR_FLAG);
        PlotAccessPolicy.Setting next = PlotAccessPolicy.setting(plot.flags(), subject, action).next();
        applyAccess(planAccess(plot, new PlotAccessRequest.Permission(subject, permission, next.value())), actor, staff);
        return next;
    }

    void resetAccess(Plot plot, UUID actor, boolean staff, PlotAccessPolicy.Subject subject,
                     PlotPermission.Category category) throws SQLException {
        applyAccess(planAccess(plot, new PlotAccessRequest.Reset(subject, category)), actor, staff);
    }

    private void setPublic(Plot plot, boolean value) throws SQLException {
        plot = current(plot);
        registry.put(copy(plot, null, null, null, value));
    }

    void setPublic(Plot plot, UUID actor, boolean staff, boolean value) throws SQLException {
        setPublic(requirePermission(plot, actor, staff, PlotPermission.MANAGE_SETTINGS), value);
    }

    void flag(Plot plot, UUID actor, boolean staff, String flag, boolean allowed) throws SQLException {
        int separator = flag.indexOf('.');
        if (separator < 0) throw new PlotProblem(Message.PLOT_ERROR_FLAG);
        PlotAccessPolicy.Group group = PlotAccessPolicy.group(flag.substring(0, separator));
        if (group == null) throw new PlotProblem(Message.PLOT_ERROR_FLAG);
        setGroupAccess(plot, actor, staff, group, flag.substring(separator + 1), allowed);
    }

    private void rename(Plot plot, String name) throws SQLException {
        plot = current(plot);
        validName(name);
        registry.rename(plot.id(), name);
    }

    void rename(Plot plot, UUID actor, boolean staff, String name) throws SQLException {
        rename(requirePermission(plot, actor, staff, PlotPermission.MANAGE_SETTINGS), name);
    }

    private String setNoticeBoard(Plot plot, String body) throws SQLException {
        plot = current(plot);
        String normalized = PlotNoticeText.normalize(body);
        registry.setNoticeBoard(plot.id(), normalized);
        return normalized;
    }

    String setNoticeBoard(Plot plot, UUID actor, boolean staff, String body) throws SQLException {
        return setNoticeBoard(requirePermission(plot, actor, staff, PlotPermission.MANAGE_SETTINGS), body);
    }

    private void setTime(Plot plot, Integer ticks) throws SQLException {
        plot = current(plot);
        registry.setAtmosphere(plot.id(), registry.atmosphere(plot.id()).withTime(ticks));
    }

    void setTime(Plot plot, UUID actor, boolean staff, Integer ticks) throws SQLException {
        setTime(requirePermission(plot, actor, staff, PlotPermission.MANAGE_SETTINGS), ticks);
    }

    private void setWeather(Plot plot, PlotAtmosphere.Weather weather) throws SQLException {
        plot = current(plot);
        registry.setAtmosphere(plot.id(), registry.atmosphere(plot.id()).withWeather(weather));
    }

    void setWeather(Plot plot, UUID actor, boolean staff, PlotAtmosphere.Weather weather) throws SQLException {
        setWeather(requirePermission(plot, actor, staff, PlotPermission.MANAGE_SETTINGS), weather);
    }

    private void delete(Plot plot) throws SQLException {
        registry.remove(current(plot));
    }

    void delete(Plot plot, UUID actor, boolean staff) throws SQLException {
        delete(requirePermission(plot, actor, staff, PlotPermission.DELETE));
    }

    Plot createSubPlot(Plot parent, UUID actor, boolean staff, UUID owner,
                       String name, PlotPosition first, PlotPosition second) throws SQLException {
        return createSubPlot(parent, actor, staff, owner, name, PlotSelectionShape.of(first, second));
    }

    Plot createSubPlot(Plot parent, UUID actor, boolean staff, UUID owner,
                       String name, PlotSelectionShape shape) throws SQLException {
        registry.requireGeometry();
        parent = current(parent);
        if (parent.subPlot()) throw new PlotProblem(Message.PLOT_ERROR_SUBPLOT_DEPTH);
        if (!registry.permitted(parent, actor, staff, PlotPermission.CREATE_SUBPLOT))
            throw new PlotProblem(Message.PLOT_ERROR_SUBPLOT_OWNER);
        validName(name);
        return registry.createSubPlot(parent.id(), owner, name, shape);
    }

    void resizeSubPlot(Plot child, UUID actor, boolean staff,
                       PlotPosition first, PlotPosition second) throws SQLException {
        resizeSubPlot(child, actor, staff, PlotSelectionShape.of(first, second));
    }

    void resizeSubPlot(Plot child, UUID actor, boolean staff, PlotSelectionShape shape) throws SQLException {
        registry.requireGeometry();
        child = current(child);
        if (!child.subPlot()) throw new PlotProblem(Message.PLOT_ERROR_SUBPLOT_PARENT);
        if (!registry.permitted(child, actor, staff, PlotPermission.MANAGE_AREA))
            throw new PlotProblem(Message.PLOT_ERROR_SUBPLOT_OWNER);
        registry.resizeSubPlot(child, shape);
    }

    static void validName(String name) {
        if (name.isBlank() || name.length() > 48
                || name.codePoints().anyMatch(Character::isISOControl))
            throw new PlotProblem(Message.PLOT_ERROR_NAME);
    }

    private static Plot copy(Plot plot, String name, Map<UUID, Plot.Role> members,
                             Map<String, Boolean> flags, Boolean publicProject) {
        return new Plot(plot.id(), plot.world(), plot.owner(), name == null ? plot.name() : name,
                publicProject == null ? plot.publicProject() : publicProject, plot.createdAt(),
                plot.cells(), members == null ? plot.members() : members,
                flags == null ? plot.flags() : flags, plot.parentId());
    }
}
