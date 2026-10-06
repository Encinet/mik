package org.encinet.mik.module.vehicle;

final class VehicleTransmission {
    enum Mode { AUTOMATIC, MANUAL }
    enum Selector { P, R, N, D }
    Mode mode = Mode.AUTOMATIC;
    Selector selector = Selector.P;
    int gear;
    int targetGear;
    double shiftRemaining;
    double gearAge;
    double rpm = 800;
    String warning = "";

    boolean select(Selector next, double speed, VehicleDefinition.Engine profile) {
        warning = "";
        if ((next == Selector.P || next == Selector.R || selector == Selector.R && next == Selector.D)
                && Math.abs(speed) > 0.5) { warning = "STOP_FIRST"; return false; }
        if (!request(next == Selector.R ? -1 : next == Selector.D ? Math.max(1, gear) : 0, speed, profile)) return false;
        selector = next;
        return true;
    }

    boolean request(int next, double speed, VehicleDefinition.Engine profile) {
        warning = "";
        if (next < -1 || next > profile.ratios().size()) { warning = "GEAR_LIMIT"; return false; }
        if (shiftRemaining > 0) { warning = "SHIFTING"; return false; }
        if (next < 0 && speed > 0.5 || next > 0 && speed < -0.5) { warning = "STOP_FIRST"; return false; }
        double predicted = Math.abs(speed / profile.wheelRadius() * profile.ratio(next)) * 60 / (2 * Math.PI);
        if (next != 0 && predicted > profile.redlineRpm() * 0.98) { warning = "OVERREV"; return false; }
        if (next == gear) { targetGear = next; return true; }
        targetGear = next;
        shiftRemaining = profile.shiftSeconds();
        gearAge = 0;
        return true;
    }

    void toggle() {
        setMode(mode == Mode.AUTOMATIC ? Mode.MANUAL : Mode.AUTOMATIC);
    }

    void setMode(Mode next) { mode = java.util.Objects.requireNonNull(next); warning = ""; }

    void stepGear(int direction, double speed, VehicleDefinition.Engine profile) {
        if (mode != Mode.MANUAL) { warning = "MANUAL_ONLY"; return; }
        int base = shiftRemaining > 0 ? targetGear : gear;
        if (request(base + Integer.signum(direction), speed, profile))
            selector = targetGear < 0 ? Selector.R : targetGear == 0 ? Selector.N : Selector.D;
    }

    double driveForce(VehicleDefinition.Engine profile, double speed, double throttle, boolean running, double seconds) {
        gearAge += seconds;
        if (shiftRemaining > 0) {
            shiftRemaining = Math.max(0, shiftRemaining - seconds);
            if (shiftRemaining == 0) { gear = targetGear; warning = ""; }
        }
        double coupledRpm = Math.abs(speed / profile.wheelRadius() * profile.ratio(gear)) * 60 / (2 * Math.PI);
        if (mode == Mode.AUTOMATIC && selector == Selector.D && shiftRemaining == 0 && gearAge > 0.7) {
            if (gear == 0) request(1, speed, profile);
            else if (gear < profile.ratios().size() && coupledRpm > profile.redlineRpm() * 0.8)
                request(gear + 1, speed, profile);
            else if (gear > 1 && coupledRpm < profile.redlineRpm() * 0.32)
                request(gear - 1, speed, profile);
        }
        double radians = rpm * (2 * Math.PI / 60);
        double idle = profile.idleRpm() * (2 * Math.PI / 60);
        double ratio = profile.ratio(gear);
        double shaft = Math.abs(speed / profile.wheelRadius() * ratio);
        double clutch = running && gear != 0 && shiftRemaining == 0
                ? Math.min(1, gearAge / 0.25) * (shaft < idle ? throttle * 0.65 : 1) : 0;
        double coupling = Math.clamp((radians - shaft) * profile.inertia() * 12 * clutch,
                -profile.peakTorque() * 1.5, profile.peakTorque() * 1.5);
        double relativeRpm = rpm / profile.redlineRpm();
        double torque = profile.peakTorque() * Math.clamp(1 - Math.pow((relativeRpm - 0.55) * 1.3, 2), 0.35, 1);
        double combustion = running && rpm < profile.redlineRpm() ? torque * throttle : 0;
        double governor = running ? Math.clamp((idle - radians) * profile.inertia() * 20, 0, profile.peakTorque()) : 0;
        double drag = radians * profile.inertia() * (running ? 0.35 : 2.5);
        radians += (combustion + governor - drag - coupling) / profile.inertia() * seconds;
        rpm = Math.clamp(radians * 60 / (2 * Math.PI), 0, profile.redlineRpm() * 1.03);
        return coupling * ratio * 0.9 / profile.wheelRadius();
    }

    String label() {
        String actual = selector == Selector.P ? "P" : gear < 0 ? "R" : gear == 0 ? "N"
                : (mode == Mode.MANUAL ? "M" : "D") + gear;
        return shiftRemaining > 0 ? actual + "→" + (targetGear < 0 ? "R" : targetGear == 0 ? "N" : targetGear) : actual;
    }
}
