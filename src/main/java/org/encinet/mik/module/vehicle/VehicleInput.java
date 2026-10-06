package org.encinet.mik.module.vehicle;

record VehicleInput(double forward, double sideways, boolean jump, boolean occupied) {
    static final VehicleInput EMPTY = new VehicleInput(0, 0, false, false);

    VehicleInput {
        if (!Double.isFinite(forward) || !Double.isFinite(sideways)) throw new IllegalArgumentException("Invalid input");
        forward = Math.clamp(forward, -1, 1);
        sideways = Math.clamp(sideways, -1, 1);
    }
}
