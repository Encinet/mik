package org.encinet.mik.shared.menu.ui;

/** Unit quaternion. Euler conversion is only used at the display protocol boundary. */
public record UiRotation(double x, double y, double z, double w) {
    public static final UiRotation IDENTITY = new UiRotation(0, 0, 0, 1);
    public UiRotation {
        double length = Math.hypot(Math.hypot(x, y), Math.hypot(z, w));
        if (!Double.isFinite(length) || length < 1e-12) throw new IllegalArgumentException("Invalid spatial rotation");
        double sign = w < 0 ? -1 : 1;
        x = x / length * sign; y = y / length * sign; z = z / length * sign; w = w / length * sign;
        if (x == 0) x = 0; if (y == 0) y = 0; if (z == 0) z = 0; if (w == 0) w = 0;
    }

    /** Minecraft yaw, pitch, then local roll; positive forward points out of the reading surface. */
    public static UiRotation degrees(double yaw, double pitch, double roll) {
        if (!Double.isFinite(yaw) || !Double.isFinite(pitch) || !Double.isFinite(roll))
            throw new IllegalArgumentException("Rotation angles must be finite");
        double y = Math.toRadians(-yaw) / 2, x = Math.toRadians(pitch) / 2, z = Math.toRadians(roll) / 2;
        return new UiRotation(0, Math.sin(y), 0, Math.cos(y))
                .compose(new UiRotation(Math.sin(x), 0, 0, Math.cos(x)))
                .compose(new UiRotation(0, 0, Math.sin(z), Math.cos(z)));
    }

    /** Applies the child rotation first, then this parent rotation. */
    public UiRotation compose(UiRotation child) {
        return new UiRotation(w * child.x + x * child.w + y * child.z - z * child.y,
                w * child.y - x * child.z + y * child.w + z * child.x,
                w * child.z + x * child.y - y * child.x + z * child.w,
                w * child.w - x * child.x - y * child.y - z * child.z);
    }
    public UiRotation inverse() { return new UiRotation(-x, -y, -z, w); }
    public UiVector3 apply(UiVector3 vector) {
        UiVector3 axis = new UiVector3(x, y, z);
        UiVector3 cross = axis.cross(vector).multiply(2);
        return vector.add(cross.multiply(w)).add(axis.cross(cross));
    }
    public UiRotation interpolate(UiRotation target, double progress) {
        if (!Double.isFinite(progress) || progress < 0 || progress > 1) throw new IllegalArgumentException("Invalid rotation progress");
        if (progress == 0) return this;
        if (progress == 1) return target;
        double dot = x * target.x + y * target.y + z * target.z + w * target.w;
        double sign = dot < 0 ? -1 : 1;
        dot = Math.clamp(Math.abs(dot), 0, 1);
        double start = 1 - progress, end = progress;
        if (dot < .9995) {
            double angle = Math.acos(dot), denominator = Math.sin(angle);
            start = Math.sin((1 - progress) * angle) / denominator;
            end = Math.sin(progress * angle) / denominator;
        }
        return new UiRotation(x * start + target.x * end * sign, y * start + target.y * end * sign,
                z * start + target.z * end * sign, w * start + target.w * end * sign);
    }
    public Angles angles() {
        UiVector3 normal = apply(new UiVector3(0, 0, 1)), right = apply(new UiVector3(1, 0, 0));
        double horizontal = Math.hypot(normal.right(), normal.forward());
        double yaw = horizontal < 1e-10 ? 0 : Math.atan2(-normal.right(), normal.forward());
        double pitch = Math.atan2(-normal.up(), horizontal);
        UiVector3 baseRight = new UiVector3(Math.cos(yaw), 0, Math.sin(yaw));
        UiVector3 baseUp = new UiVector3(-Math.sin(yaw) * Math.sin(pitch), Math.cos(pitch), Math.cos(yaw) * Math.sin(pitch));
        double roll = Math.atan2(right.dot(baseUp), right.dot(baseRight));
        return new Angles(Math.toDegrees(yaw), Math.toDegrees(pitch), Math.toDegrees(roll));
    }
    public record Angles(double yaw, double pitch, double roll) {
        public Angles { if (yaw == 0) yaw = 0; if (pitch == 0) pitch = 0; if (roll == 0) roll = 0; }
    }
}
