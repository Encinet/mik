package org.encinet.mik.module.pvp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class PvpCombatController implements Listener {

    private static final long AUTO_ENABLE_WINDOW_MILLIS = 8_000L;
    private static final long COMBAT_TAG_DURATION_MILLIS = 15_000L;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final PvpSettingsStore settingsStore;
    private final Map<UUID, PendingAttack> pendingAutoEnable = new ConcurrentHashMap<>();
    private final Map<UUID, Long> combatTaggedUntil = new ConcurrentHashMap<>();

    PvpCombatController(JavaPlugin plugin, LanguageService languageService, PvpSettingsStore settingsStore) {
        this.plugin = plugin;
        this.languageService = languageService;
        this.settingsStore = settingsStore;
    }

    void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupExpiredCombatTags, 20L * 60L, 20L * 60L);
    }

    void onPvpStateSet(UUID playerId, boolean enabled) {
        pendingAutoEnable.remove(playerId);
        if (!enabled) {
            combatTaggedUntil.remove(playerId);
        }
    }

    boolean isCombatTagged(UUID playerId) {
        Long expiresAt = combatTaggedUntil.get(playerId);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt <= System.currentTimeMillis()) {
            combatTaggedUntil.remove(playerId, expiresAt);
            return false;
        }
        return true;
    }

    long combatTagRemainingSeconds(UUID playerId) {
        Long expiresAt = combatTaggedUntil.get(playerId);
        if (expiresAt == null) {
            return 0;
        }
        long remainingMillis = Math.max(0, expiresAt - System.currentTimeMillis());
        return Math.max(1, (remainingMillis + 999L) / 1_000L);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        AttackSource attacker = attackingPlayer(event);
        if (attacker == null) return;

        Entity target = event.getEntity();
        if (target instanceof Player victim) {
            handlePlayerDamage(event, attacker, victim);
            return;
        }

        if (isProtectedMob(attacker, target)) {
            event.setCancelled(true);
            sendActionBar(attacker, Message.PVP_MOB_PROTECTED_ACTIONBAR_MM);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConfirmedPvpDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (event.getFinalDamage() <= 0) return;

        AttackSource attacker = attackingPlayer(event);
        if (attacker == null || attacker.playerId().equals(victim.getUniqueId())) {
            return;
        }
        if (!settingsStore.get(attacker.playerId()).enabled() || !settingsStore.get(victim.getUniqueId()).enabled()) {
            return;
        }

        markCombat(attacker.playerId());
        markCombat(victim.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityCombustByEntity(EntityCombustByEntityEvent event) {
        AttackSource attacker = attackingPlayer(event.getCombuster());
        if (attacker == null) return;

        if (isProtectedMob(attacker, event.getEntity())) {
            event.setCancelled(true);
            sendActionBar(attacker, Message.PVP_MOB_PROTECTED_ACTIONBAR_MM);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityTargetLivingEntity(EntityTargetLivingEntityEvent event) {
        AttackSource attacker = attackingPlayer(event.getEntity());
        if (attacker == null || event.getTarget() == null) return;

        if (isProtectedMob(attacker, event.getTarget())) {
            event.setCancelled(true);
            event.setTarget(null);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        pendingAutoEnable.remove(playerId);
        pendingAutoEnable.entrySet().removeIf(entry -> entry.getValue().targetId().equals(playerId));
        combatTaggedUntil.remove(playerId);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerId = player.getUniqueId();
        combatTaggedUntil.remove(playerId);
        PvpSettings settings = settingsStore.get(playerId);
        if (settings.enableOnDeath() && !settings.enabled()) {
            settingsStore.save(playerId, settings.withEnabled(true));
            pendingAutoEnable.remove(playerId);
            player.sendMessage(mm(player, Message.PVP_ENABLED_ON_DEATH_MM));
        }
    }

    private void handlePlayerDamage(EntityDamageByEntityEvent event, AttackSource attacker, Player victim) {
        if (attacker.playerId().equals(victim.getUniqueId())) {
            return;
        }

        PvpSettings attackerSettings = settingsStore.get(attacker.playerId());
        PvpSettings victimSettings = settingsStore.get(victim.getUniqueId());
        if (!victimSettings.enabled()) {
            event.setCancelled(true);
            sendActionBar(attacker, Message.PVP_TARGET_DISABLED_ACTIONBAR_MM);
            return;
        }

        if (attackerSettings.enabled()) {
            return;
        }

        if (attacker.player() == null) {
            event.setCancelled(true);
            return;
        }

        PendingAttack pending = pendingAutoEnable.get(attacker.playerId());
        long now = System.currentTimeMillis();
        if (pending != null && pending.targetId().equals(victim.getUniqueId()) && pending.expiresAt() >= now) {
            settingsStore.save(attacker.playerId(), attackerSettings.withEnabled(true));
            pendingAutoEnable.remove(attacker.playerId());
            sendActionBar(attacker, Message.PVP_AUTO_ENABLED_ACTIONBAR_MM);
            return;
        }

        pendingAutoEnable.put(attacker.playerId(), new PendingAttack(victim.getUniqueId(), now + AUTO_ENABLE_WINDOW_MILLIS));
        event.setCancelled(true);
        sendActionBar(attacker, Message.PVP_AUTO_ENABLE_WARNING_ACTIONBAR_MM);
    }

    private boolean isProtectedMob(AttackSource attacker, Entity target) {
        if (target instanceof Tameable tameable && tameable.isTamed() && tameable.getOwnerUniqueId() != null) {
            UUID ownerId = tameable.getOwnerUniqueId();
            if (!ownerId.equals(attacker.playerId())) {
                PvpSettings ownerSettings = settingsStore.get(ownerId);
                if (ownerSettings.protectMobs()) {
                    return true;
                }
            }
        }

        for (Entity passenger : target.getPassengers()) {
            if (!(passenger instanceof Player rider)) {
                continue;
            }
            if (rider.getUniqueId().equals(attacker.playerId())) {
                continue;
            }
            PvpSettings riderSettings = settingsStore.get(rider.getUniqueId());
            PvpSettings attackerSettings = settingsStore.get(attacker.playerId());
            boolean mountedDamageAllowed = riderSettings.enabled()
                    && attackerSettings.enabled()
                    && riderSettings.allowMountedMobDamage();
            if (riderSettings.protectMobs() && !mountedDamageAllowed) {
                return true;
            }
        }
        return false;
    }

    private AttackSource attackingPlayer(EntityDamageByEntityEvent event) {
        AttackSource source = attackingPlayer(event.getDamageSource().getCausingEntity());
        if (source != null) {
            return source;
        }
        source = attackingPlayer(event.getDamageSource().getDirectEntity());
        if (source != null) {
            return source;
        }
        return attackingPlayer(event.getDamager());
    }

    private AttackSource attackingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return new AttackSource(player.getUniqueId(), player);
        }
        if (damager instanceof Projectile projectile) {
            return attackingPlayer(projectile.getShooter());
        }
        if (damager instanceof AreaEffectCloud cloud) {
            return attackingPlayer(cloud.getSource());
        }
        if (damager instanceof TNTPrimed tnt) {
            return attackingPlayer(tnt.getSource());
        }
        if (damager instanceof Tameable tameable && tameable.isTamed() && tameable.getOwnerUniqueId() != null) {
            AnimalTamer owner = tameable.getOwner();
            Player onlineOwner = owner instanceof Player player ? player : Bukkit.getPlayer(tameable.getOwnerUniqueId());
            return new AttackSource(tameable.getOwnerUniqueId(), onlineOwner);
        }
        return null;
    }

    private AttackSource attackingPlayer(ProjectileSource source) {
        return source instanceof Entity entity ? attackingPlayer(entity) : null;
    }

    private void sendActionBar(AttackSource source, Message message) {
        if (source.player() != null) {
            source.player().sendActionBar(mm(source.player(), message));
        }
    }

    private void markCombat(UUID playerId) {
        combatTaggedUntil.put(playerId, System.currentTimeMillis() + COMBAT_TAG_DURATION_MILLIS);
    }

    private void cleanupExpiredCombatTags() {
        long now = System.currentTimeMillis();
        combatTaggedUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private Component mm(Player player, Message message, Object... args) {
        return MINI_MESSAGE.deserialize(languageService.t(player, message, args));
    }

    private record PendingAttack(UUID targetId, long expiresAt) {
    }

    private record AttackSource(UUID playerId, Player player) {
    }
}
