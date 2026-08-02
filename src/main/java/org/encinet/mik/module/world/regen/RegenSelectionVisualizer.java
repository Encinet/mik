package org.encinet.mik.module.world.regen;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Player-private particle visualization for a stored regeneration selection and preview risks. */
final class RegenSelectionVisualizer implements AutoCloseable {

    static final long NO_EXPIRY = Long.MAX_VALUE;

    private static final long PERIOD_TICKS = 10L;
    private static final int MAX_OUTLINE_POINTS = 640;
    private static final int OUTLINE_PARTICLES_PER_FRAME = 36;
    private static final int CHANGE_PARTICLES_PER_FRAME = 18;
    private static final int RISK_PARTICLES_PER_FRAME = 10;
    private static final double MAX_DISTANCE_SQUARED = 96.0D * 96.0D;
    private static final Particle.DustOptions OUTLINE =
            new Particle.DustOptions(Color.fromRGB(54, 210, 255), 1.05F);
    private static final Particle.DustOptions CHANGE =
            new Particle.DustOptions(Color.fromRGB(255, 166, 45), 1.15F);
    private static final Particle.DustOptions RISK =
            new Particle.DustOptions(Color.fromRGB(255, 55, 70), 1.55F);

    private final JavaPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private BukkitTask task;

    RegenSelectionVisualizer(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void start() {
        requireServerThread();
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, PERIOD_TICKS);
        }
    }

    void show(Player player, RegenSelection selection, RegenPreviewSamples samples, long expiresAtNanos) {
        requireServerThread();
        sessions.put(player.getUniqueId(), new Session(
                selection.world().getUID(),
                RegenSelectionOutline.sample(selection, MAX_OUTLINE_POINTS),
                samples.changedBlocks(),
                samples.atRiskBlockEntities(),
                expiresAtNanos));
    }

    void hide(UUID playerId) {
        requireServerThread();
        sessions.remove(playerId);
    }

    boolean visible(UUID playerId) {
        requireServerThread();
        return sessions.containsKey(playerId);
    }

    @Override
    public void close() {
        requireServerThread();
        sessions.clear();
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        requireServerThread();
        long now = System.nanoTime();
        sessions.entrySet().removeIf(entry -> {
            Player player = Bukkit.getPlayer(entry.getKey());
            Session session = entry.getValue();
            if (player == null || !player.isOnline() || now > session.expiresAtNanos) {
                return true;
            }
            if (!player.getWorld().getUID().equals(session.worldId)) {
                return false;
            }
            session.outlineCursor = render(player, session.outline, session.outlineCursor,
                    OUTLINE_PARTICLES_PER_FRAME, OUTLINE);
            session.changeCursor = render(player, session.changes, session.changeCursor,
                    CHANGE_PARTICLES_PER_FRAME, CHANGE);
            session.riskCursor = render(player, session.risks, session.riskCursor,
                    RISK_PARTICLES_PER_FRAME, RISK);
            return false;
        });
    }

    private static int render(
            Player player,
            List<RegenPreviewPoint> points,
            int cursor,
            int budget,
            Particle.DustOptions options
    ) {
        if (points.isEmpty()) {
            return 0;
        }
        int scanned = 0;
        int rendered = 0;
        int index = Math.floorMod(cursor, points.size());
        while (scanned < points.size() && rendered < budget) {
            RegenPreviewPoint point = points.get(index);
            index = (index + 1) % points.size();
            scanned++;
            double x = point.x() + 0.5D;
            double y = point.y() + 0.5D;
            double z = point.z() + 0.5D;
            double deltaX = x - player.getX();
            double deltaY = y - player.getY();
            double deltaZ = z - player.getZ();
            if (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ > MAX_DISTANCE_SQUARED) {
                continue;
            }
            player.spawnParticle(Particle.DUST, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D, options);
            rendered++;
        }
        return index;
    }

    private static void requireServerThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Selection visualization may only run on the server thread");
        }
    }

    private static final class Session {

        private final UUID worldId;
        private final List<RegenPreviewPoint> outline;
        private final List<RegenPreviewPoint> changes;
        private final List<RegenPreviewPoint> risks;
        private final long expiresAtNanos;
        private int outlineCursor;
        private int changeCursor;
        private int riskCursor;

        private Session(
                UUID worldId,
                List<RegenPreviewPoint> outline,
                List<RegenPreviewPoint> changes,
                List<RegenPreviewPoint> risks,
                long expiresAtNanos
        ) {
            this.worldId = worldId;
            this.outline = List.copyOf(outline);
            this.changes = List.copyOf(changes);
            this.risks = List.copyOf(risks);
            this.expiresAtNanos = expiresAtNanos;
        }
    }
}
