package org.encinet.mik.module.communication.tip;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** A delivery context with its own anti-spam and repeat policy. */
public enum TipScene {
    MANUAL("manual", 0L, 0L, 0L),
    PERIODIC("periodic", TimeUnit.MINUTES.toMillis(14),
            TimeUnit.MINUTES.toMillis(2), TimeUnit.HOURS.toMillis(6)),
    JOIN("join", TimeUnit.HOURS.toMillis(6),
            TimeUnit.MINUTES.toMillis(2), TimeUnit.DAYS.toMillis(3)),
    CHAT("chat", TimeUnit.MINUTES.toMillis(3),
            TimeUnit.SECONDS.toMillis(30), TimeUnit.HOURS.toMillis(6)),
    RESPAWN("respawn", TimeUnit.MINUTES.toMillis(20),
            TimeUnit.MINUTES.toMillis(2), TimeUnit.HOURS.toMillis(12)),
    WORLD_CHANGE("world-change", TimeUnit.MINUTES.toMillis(45),
            TimeUnit.MINUTES.toMillis(2), TimeUnit.DAYS.toMillis(1));

    private final String id;
    private final long sceneCooldownMillis;
    private final long globalCooldownMillis;
    private final long repeatCooldownMillis;

    TipScene(String id, long sceneCooldownMillis,
             long globalCooldownMillis, long repeatCooldownMillis) {
        this.id = id;
        this.sceneCooldownMillis = sceneCooldownMillis;
        this.globalCooldownMillis = globalCooldownMillis;
        this.repeatCooldownMillis = repeatCooldownMillis;
    }

    public String id() {
        return id;
    }

    public long sceneCooldownMillis() {
        return sceneCooldownMillis;
    }

    public long globalCooldownMillis() {
        return globalCooldownMillis;
    }

    public long repeatCooldownMillis() {
        return repeatCooldownMillis;
    }

    public static Optional<TipScene> fromId(String id) {
        if (id == null) return Optional.empty();
        return Arrays.stream(values())
                .filter(scene -> scene.id.equalsIgnoreCase(id.strip()))
                .findFirst();
    }
}
