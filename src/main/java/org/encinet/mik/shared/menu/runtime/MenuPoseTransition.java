package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.FloatingMenuEasing;
import org.encinet.mik.shared.menu.FloatingMenuPose;
import org.encinet.mik.shared.menu.runtime.motion.MenuTransition;

/** Layout interpolation retains one source and one target, independently of current business content. */
final class MenuPoseTransition {
    enum Path { LINEAR, RING, ORBIT }
    private final MenuTransition progress = new MenuTransition(1);
    private FloatingMenuPose source = FloatingMenuPose.ORIGIN;
    private FloatingMenuPose target = FloatingMenuPose.ORIGIN;
    private Path path = Path.LINEAR;

    void snap(FloatingMenuPose pose, long tick) {
        java.util.Objects.requireNonNull(pose);
        progress.snap(1, tick);
        source = target = pose;
    }

    FloatingMenuPose advance(FloatingMenuPose next, long tick, int ticks, FloatingMenuEasing easing, Path nextPath) {
        java.util.Objects.requireNonNull(next);
        java.util.Objects.requireNonNull(nextPath);
        java.util.Objects.requireNonNull(easing);
        if (ticks < 0) throw new IllegalArgumentException("Transition duration cannot be negative");
        FloatingMenuPose current = sample(tick);
        if (ticks == 0) { snap(next, tick); return next; }
        if (!next.equals(target) || nextPath != path) {
            source = current;
            target = next;
            path = nextPath;
            progress.snap(0, tick);
            progress.to(1, tick, ticks, easing);
        }
        return sample(tick);
    }

    Path path() { return path; }

    FloatingMenuPose sample(long tick) {
        double fraction = progress.value(tick);
        if (fraction <= 0) return source;
        if (fraction >= 1) return target;
        var pose = path == Path.ORBIT ? FloatingMenuMotion.approachOrbit(source, target, fraction)
                : FloatingMenuMotion.approachPose(source, target, fraction);
        if (path != Path.RING) return pose;
        return new FloatingMenuPose(FloatingMenuMotion.approachRing(source.point(), target.point(), fraction),
                pose.yawDegrees(), pose.pitchDegrees(), pose.rollDegrees(), pose.scale());
    }
}
