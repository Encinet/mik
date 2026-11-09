package org.encinet.mik.feature.world.vehicle;

import org.bukkit.Location;

/** Presentation transform only; authored geometry and physics keep their actual units. */
record VehicleEditorStage(Location origin, double scale) {
    VehicleEditorStage {
        origin = origin.clone();
        if (!Double.isFinite(scale) || scale < 0.02 || scale > 2) throw new IllegalArgumentException("Preview scale must be between 0.02 and 2");
    }
    @Override public Location origin() { return origin.clone(); }
    static VehicleEditorStage fit(VehicleModelDraft draft, Location viewer) {
        var half = bounds(draft).halfSize();
        double scale = Math.clamp(2.8 / (2 * Math.max(half.coordinateX(), Math.max(half.coordinateY(), half.coordinateZ()))), 0.02, 0.6);
        return place(draft, viewer, scale);
    }
    static VehicleEditorStage place(VehicleModelDraft draft, Location viewer, double scale) {
        var heading = VehicleModel.orientation(viewer.getYaw());
        var forward = new VehicleVector(0, 0, 3.5).rotate(heading);
        var offset = bounds(draft).center().multiply(scale).rotate(heading);
        Location displayed = viewer.clone().add(forward.coordinateX() - offset.coordinateX(), 1.4 - offset.coordinateY(), forward.coordinateZ() - offset.coordinateZ());
        displayed.setPitch(0);
        return new VehicleEditorStage(VehicleEditorGeometry.anchored(displayed, draft.frameTurns()), scale);
    }
    VehicleEditorStage withScale(VehicleModelDraft draft, double nextScale) {
        var center = bounds(draft).center();
        var offset = center.multiply(nextScale).rotate(VehicleModel.orientation(oriented(draft).getYaw()));
        Location next = world(draft, center).subtract(offset.coordinateX(), offset.coordinateY(), offset.coordinateZ());
        return new VehicleEditorStage(VehicleEditorGeometry.anchored(next, draft.frameTurns()), nextScale);
    }
    private static VehicleDefinition.Collider bounds(VehicleModelDraft draft) {
        try { return VehicleModel.bounds(draft.parts()); }
        catch (IllegalArgumentException ignored) { return new VehicleDefinition.Collider(VehicleVector.ZERO, new VehicleVector(2, 1, 2)); }
    }
    Location oriented(VehicleModelDraft draft) { return VehicleEditorGeometry.oriented(origin, draft.frameTurns()); }
    VehicleVector local(VehicleModelDraft draft, Location point) { return VehicleEditorGeometry.local(oriented(draft), point).multiply(1 / scale); }
    Location world(VehicleModelDraft draft, VehicleVector point) { return VehicleEditorGeometry.world(oriented(draft), point.multiply(scale)); }
}
