package org.encinet.mik.module.plot.board;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Remembers which board events have already produced targeted alerts. */
final class PlotBoardAlertLedger {
    private static final long RECENT_WINDOW_MS = 72L * 60 * 60 * 1000;
    private static final long INBOX_WINDOW_MS = 7L * 24 * 60 * 60 * 1000;
    private static final int MAX_SEEN = 2_048;
    private static final int MAX_DELIVERED = 4_096;

    private final Path path;
    private final long startedAt;
    private final Set<String> seen = new LinkedHashSet<>();
    private final Set<String> delivered = new LinkedHashSet<>();

    private PlotBoardAlertLedger(Path path, long startedAt) {
        this.path = path;
        this.startedAt = startedAt;
    }

    static PlotBoardAlertLedger open(Path path, long now) throws IOException {
        if (!Files.isRegularFile(path)) {
            PlotBoardAlertLedger created = new PlotBoardAlertLedger(path, now);
            created.save();
            return created;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
        long started = yaml.getLong("started-at", now);
        PlotBoardAlertLedger ledger = new PlotBoardAlertLedger(path,
                started > 0 && started <= now ? started : now);
        ledger.seen.addAll(yaml.getStringList("seen"));
        ledger.delivered.addAll(yaml.getStringList("delivered"));
        return ledger;
    }

    boolean shouldAlert(String key, long eventAt, long now) {
        return eventAt > startedAt && eventAt <= now
                && now - eventAt <= RECENT_WINDOW_MS && !seen.contains(key);
    }

    void mark(String key) {
        seen.add(key);
        while (seen.size() > MAX_SEEN) seen.remove(seen.iterator().next());
    }

    boolean shouldDeliverToPlayer(String key, String playerId, long eventAt, long now) {
        return eventAt > startedAt && eventAt <= now && now - eventAt <= INBOX_WINDOW_MS
                && !delivered.contains(key + ':' + playerId);
    }

    void markDelivered(String key, String playerId) {
        delivered.add(key + ':' + playerId);
        while (delivered.size() > MAX_DELIVERED) delivered.remove(delivered.iterator().next());
    }

    void save() throws IOException {
        Files.createDirectories(path.getParent());
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("started-at", startedAt);
        yaml.set("seen", List.copyOf(seen));
        yaml.set("delivered", List.copyOf(delivered));
        yaml.save(path.toFile());
    }
}
