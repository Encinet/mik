package org.encinet.mik.module.plot;

import org.encinet.mik.module.i18n.Message;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class PlotSelectionState {
    private static final int MAX_DRAFTS = 8;
    private static final Points EMPTY = new Points(null, null);
    private static final int MAX_HISTORY = 16;
    private final Map<UUID, LinkedHashMap<PlotEditorContext, Draft>> drafts = new HashMap<>();
    private final Map<UUID, PlotEditorContext> active = new HashMap<>();
    private long revision;

    record Base(Points points, PlotSelectionShape shape, RegionEdit edit) {
        Base(Points points, PlotSelectionShape shape) {
            this(points, shape, null);
        }

        boolean in(UUID world) {
            return points.in(world) && (shape == null || shape.world().equals(world));
        }

        PlotSelectionShape candidate() {
            if (edit != null) return edit.candidate();
            if (points.equals(EMPTY)) return shape;
            if (points.first() == null || points.second() == null) return null;
            return PlotSelectionShape.of(points.first(), points.second());
        }

        Base withPoints(Points next) {
            if (edit == null) return new Base(next, shape);
            PlotSelectionShape candidate = next.first() == null || next.second() == null ? null
                    : PlotSelectionRegions.replace(shape, edit.region(), new PlotSelection(next.first(), next.second()));
            return new Base(next, shape, new RegionEdit(edit.original(), edit.region(), candidate));
        }
    }

    record RegionEdit(Base original, PlotSelectionRegions.Region region, PlotSelectionShape candidate) { }

    record Draft(Base base, List<Base> history, List<Base> future, long revision) { }
    record Submission(PlotSelectionShape shape, long revision, PlotEditorContext context) { }

    record Points(PlotPosition first, PlotPosition second) {
        boolean in(UUID world) {
            return (first == null || first.world().equals(world))
                    && (second == null || second.world().equals(world));
        }
    }

    void bind(UUID playerId, UUID plotId, UUID world) {
        bindContext(playerId, PlotEditorContext.area(plotId), world);
    }

    void resume(UUID playerId, UUID plotId, UUID world) {
        bindContext(playerId, context(playerId, plotId), world);
    }

    void bindContext(UUID playerId, PlotEditorContext context, UUID world) {
        LinkedHashMap<PlotEditorContext, Draft> selections = drafts.computeIfAbsent(playerId,
                ignored -> new LinkedHashMap<>(8, 0.75F, true));
        Draft draft = selections.get(context);
        if (draft == null) {
            Draft unbound = context.subPlot() ? null : selections.remove(PlotEditorContext.area(null));
            draft = unbound != null && unbound.base().in(world) ? unbound : emptyDraft();
        }
        selections.put(context, draft.base().in(world) ? draft : emptyDraft());
        active.put(playerId, context);
        trim(selections);
    }

    UUID currentPlot(UUID playerId) {
        return context(playerId).plotId();
    }

    PlotEditorContext context(UUID playerId) {
        return active.getOrDefault(playerId, PlotEditorContext.area(null));
    }

    PlotEditorContext context(UUID playerId, UUID plotId) {
        PlotEditorContext current = context(playerId);
        return Objects.equals(current.plotId(), plotId) ? current : PlotEditorContext.area(plotId);
    }

    void unbind(UUID playerId) {
        active.remove(playerId);
    }

    Points points(UUID playerId) {
        return draftFor(playerId, context(playerId)).base().points();
    }

    Points points(UUID playerId, UUID plotId) {
        return draft(playerId, plotId).base().points();
    }

    private Draft emptyDraft() {
        return new Draft(new Base(EMPTY, null), List.of(), List.of(), 0);
    }

    Draft draft(UUID playerId, UUID plotId) {
        return draftFor(playerId, context(playerId, plotId));
    }

    Draft draftFor(UUID playerId, PlotEditorContext context) {
        Map<PlotEditorContext, Draft> selections = drafts.get(playerId);
        return selections == null ? emptyDraft() : selections.getOrDefault(context, emptyDraft());
    }

    PlotPosition first(UUID playerId) {
        return points(playerId).first();
    }

    PlotPosition second(UUID playerId) {
        return points(playerId).second();
    }

    void mark(UUID playerId, boolean first, PlotPosition point) {
        Points previous = points(playerId);
        Base base = draftFor(playerId, context(playerId)).base();
        boolean compatible = base.in(point.world());
        if (!compatible) previous = EMPTY;
        Points next = new Points(first ? point : previous.first(), first ? previous.second() : point);
        if (compatible && next.equals(previous)) return;
        save(playerId, compatible ? base.withPoints(next) : new Base(next, null), compatible);
    }

    void set(UUID playerId, PlotSelection selection) {
        Base base = draftFor(playerId, context(playerId)).base();
        Points points = new Points(selection.first(), selection.second());
        save(playerId, base.edit() == null ? new Base(points, null) : base.withPoints(points), true);
    }

    private void save(UUID playerId, Base base, boolean remember) {
        Draft previous = draftFor(playerId, context(playerId));
        if (previous.base().equals(base)) return;
        List<Base> history = new ArrayList<>(remember ? previous.history() : List.of());
        if (remember) history.add(previous.base());
        if (history.size() > MAX_HISTORY) history.removeFirst();
        store(playerId, new Draft(base, List.copyOf(history), List.of(), ++revision));
    }

    private void store(UUID playerId, Draft draft) {
        LinkedHashMap<PlotEditorContext, Draft> selections = drafts.computeIfAbsent(playerId,
                ignored -> new LinkedHashMap<>(8, 0.75F, true));
        selections.put(context(playerId), draft);
        trim(selections);
    }

    private static void trim(LinkedHashMap<PlotEditorContext, Draft> selections) {
        if (selections.size() > MAX_DRAFTS) selections.remove(selections.keySet().iterator().next());
    }

    PlotSelection selection(UUID playerId, UUID world) {
        Points points = points(playerId);
        if (points.first() == null || points.second() == null || !points.in(world))
            throw new PlotProblem(Message.PLOT_ERROR_SELECTION);
        return new PlotSelection(points.first(), points.second());
    }

    Submission submission(UUID playerId, UUID world) {
        return submission(playerId, currentPlot(playerId), world);
    }

    Submission submission(UUID playerId, UUID plotId, UUID world) {
        Draft draft = draft(playerId, plotId);
        if (!draft.base().in(world)) throw new PlotProblem(Message.PLOT_ERROR_SELECTION);
        requireFinished(draft.base());
        PlotSelectionShape shape = draft.base().candidate();
        if (shape == null || shape.empty()) throw new PlotProblem(Message.PLOT_ERROR_SELECTION);
        return new Submission(shape, draft.revision(), context(playerId, plotId));
    }

    void requireSubmission(UUID playerId, UUID world, Submission submitted) {
        if (!context(playerId).equals(submitted.context())
                || !submission(playerId, world).equals(submitted))
            throw new PlotProblem(Message.PLOT_ERROR_SELECTION_CHANGED);
    }

    void apply(UUID playerId, UUID world, PlotSelectionShape.Operation operation) {
        requireFinished(draftFor(playerId, context(playerId)).base());
        PlotSelection brush = selection(playerId, world);
        PlotSelectionShape shape = draftFor(playerId, context(playerId)).base().shape();
        if (shape == null && operation == PlotSelectionShape.Operation.SUBTRACT)
            throw new PlotProblem(Message.PLOT_ERROR_SELECTION);
        if (shape == null) shape = new PlotSelectionShape(world, null, Set.of());
        save(playerId, new Base(EMPTY, shape.apply(brush, operation)), true);
    }

    void fit(UUID playerId, UUID world, Set<PlotGeometry.Cell> cells) {
        requireFinished(draftFor(playerId, context(playerId)).base());
        save(playerId, new Base(EMPTY, new PlotSelectionShape(world, null, cells)), true);
    }

    void initialize(UUID playerId, UUID world, Set<PlotGeometry.Cell> cells) {
        if (context(playerId).subPlot()) return;
        Draft draft = draftFor(playerId, context(playerId));
        if (draft.base().shape() != null || !draft.base().points().equals(EMPTY) || !draft.history().isEmpty()) return;
        store(playerId, new Draft(new Base(EMPTY, new PlotSelectionShape(world, null, cells)),
                List.of(), List.of(), ++revision));
    }

    boolean undo(UUID playerId) {
        Draft draft = draftFor(playerId, context(playerId));
        if (draft.history().isEmpty()) return false;
        List<Base> history = new ArrayList<>(draft.history());
        Base previous = history.removeLast();
        List<Base> future = new ArrayList<>(draft.future());
        future.add(draft.base());
        store(playerId, new Draft(previous, List.copyOf(history), List.copyOf(future), ++revision));
        return true;
    }

    boolean redo(UUID playerId) {
        Draft draft = draftFor(playerId, context(playerId));
        if (draft.future().isEmpty()) return false;
        List<Base> future = new ArrayList<>(draft.future());
        Base next = future.removeLast();
        List<Base> history = new ArrayList<>(draft.history());
        history.add(draft.base());
        if (history.size() > MAX_HISTORY) history.removeFirst();
        store(playerId, new Draft(next, List.copyOf(history), List.copyOf(future), ++revision));
        return true;
    }

    void discardBrush(UUID playerId) {
        Base base = draftFor(playerId, context(playerId)).base();
        save(playerId, base.withPoints(EMPTY), true);
    }

    private static void requireFinished(Base base) {
        if (base.edit() != null) throw new PlotProblem(Message.PLOT_REGION_FINISH_FIRST);
    }

    Draft requireRegion(UUID playerId, UUID world, PlotSelectionRegions.Region region, long expectedRevision) {
        Draft current = requireRevision(playerId, world, expectedRevision);
        requireFinished(current.base());
        if (region == null || !PlotSelectionRegions.decompose(current.base().candidate()).contains(region))
            throw new PlotProblem(Message.PLOT_REGION_CHANGED);
        return current;
    }

    Draft requireRevision(UUID playerId, UUID world, long expectedRevision) {
        Draft current = draftFor(playerId, context(playerId));
        if (current.revision() != expectedRevision || !current.base().in(world))
            throw new PlotProblem(Message.PLOT_REGION_CHANGED);
        return current;
    }

    void deleteRegion(UUID playerId, UUID world, PlotSelectionRegions.Region region, long expectedRevision) {
        Draft current = requireRegion(playerId, world, region, expectedRevision);
        save(playerId, new Base(EMPTY, PlotSelectionRegions.remove(current.base().candidate(), region)), true);
    }

    void beginRegionEdit(UUID playerId, UUID world, PlotSelectionRegions.Region region, long expectedRevision) {
        Draft current = requireRegion(playerId, world, region, expectedRevision);
        PlotSelection selected = region.selection(world);
        PlotSelectionShape source = current.base().candidate();
        save(playerId, new Base(new Points(selected.first(), selected.second()), source,
                new RegionEdit(current.base(), region, source)), true);
    }

    void finishRegionEdit(UUID playerId) {
        Base base = draftFor(playerId, context(playerId)).base();
        if (base.edit() == null) throw new PlotProblem(Message.PLOT_REGION_CHANGED);
        if (base.candidate() == null || base.candidate().empty())
            throw new PlotProblem(Message.PLOT_ERROR_SELECTION);
        save(playerId, new Base(EMPTY, base.candidate()), true);
    }

    void cancelRegionEdit(UUID playerId) {
        Base base = draftFor(playerId, context(playerId)).base();
        if (base.edit() == null) throw new PlotProblem(Message.PLOT_REGION_CHANGED);
        save(playerId, base.edit().original(), true);
    }

    void clear(UUID playerId) {
        requireFinished(draftFor(playerId, context(playerId)).base());
        save(playerId, new Base(EMPTY, null), true);
    }

    void clearIfMatches(UUID playerId, UUID plotId, PlotSelection submitted) {
        Map<PlotEditorContext, Draft> selections = drafts.get(playerId);
        if (selections != null && Objects.equals(draft(playerId, plotId).base(),
                new Base(new Points(submitted.first(), submitted.second()), null)))
            selections.remove(context(playerId, plotId));
    }

    boolean clearIfMatches(UUID playerId, UUID plotId, Submission submitted) {
        Map<PlotEditorContext, Draft> selections = drafts.get(playerId);
        if (selections != null && Objects.equals(plotId, submitted.context().plotId())
                && draftFor(playerId, submitted.context()).revision() == submitted.revision()) {
            selections.remove(submitted.context());
            return true;
        }
        return false;
    }

    void removePlot(UUID plotId) {
        drafts.values().forEach(selections -> selections.keySet().removeIf(context -> plotId.equals(context.plotId())));
        active.entrySet().removeIf(entry -> plotId.equals(entry.getValue().plotId()));
    }

    void forget(UUID playerId) {
        drafts.remove(playerId);
        active.remove(playerId);
    }

    void clearAll() {
        drafts.clear();
        active.clear();
    }
}
