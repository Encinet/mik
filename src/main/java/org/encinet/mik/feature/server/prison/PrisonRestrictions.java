package org.encinet.mik.feature.server.prison;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.world.GenericGameEvent;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Owns the temporary player settings and guards actions that can originate on the server. */
final class PrisonRestrictions implements Listener {
    private final Predicate<UUID> confined;
    private final LanguageService language;
    private final Map<Player, Settings> previous = new HashMap<>();
    private Consumer<Player> entering = _ -> { };
    // These synchronous changes only run while taking over a session or restoring it at quit.
    private Player changingMode;

    PrisonRestrictions(Predicate<UUID> confined, LanguageService language) {
        this.confined = confined;
        this.language = language;
    }

    void onEnter(Consumer<Player> action) { entering = action; }

    boolean enforce(Player player) {
        if (!restricted(player)) {
            release(player, false);
            return false;
        }
        if (!previous.containsKey(player)) {
            changingMode = player;
            try {
                entering.accept(player);
            } finally {
                changingMode = null;
            }
            previous.put(player, new Settings(player.getGameMode(), player.getAllowFlight(), player.isCollidable()));
            player.closeInventory();
            player.closeDialog();
            player.updateCommands();
        }
        if (player.getGameMode() != GameMode.ADVENTURE) player.setGameMode(GameMode.ADVENTURE);
        if (player.isFlying()) player.setFlying(false);
        if (player.getAllowFlight()) player.setAllowFlight(false);
        if (player.isGliding()) player.setGliding(false);
        if (player.isRiptiding()) player.setRiptiding(false);
        if (player.hasActiveItem()) player.clearActiveItem();
        if (player.isInsideVehicle()) player.leaveVehicle();
        for (Entity passenger : player.getPassengers()) passenger.leaveVehicle();
        if (player.isCollidable()) player.setCollidable(false);
        return true;
    }

    private void release(Player player, boolean quitting) {
        Settings saved = previous.remove(player);
        if (saved == null) return;
        changingMode = player;
        try {
            if (player.getGameMode() != saved.mode()) player.setGameMode(saved.mode());
            player.setCollidable(saved.collidable());
            if (player.getGameMode() == saved.mode()
                    && (!quitting || saved.mode() == GameMode.CREATIVE || saved.mode() == GameMode.SPECTATOR)) {
                player.setAllowFlight(saved.allowFlight());
            }
        } finally {
            changingMode = null;
        }
        if (!quitting) player.updateCommands();
    }

    void close() {
        for (Player player : previous.keySet().toArray(Player[]::new)) release(player, false);
    }

    private boolean restricted(Entity entity) {
        return entity instanceof Player player && confined.test(player.getUniqueId());
    }

    private void deny(Cancellable event, Entity actor) {
        if (restricted(actor)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) { enforce(event.getPlayer()); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPostRespawn(PlayerPostRespawnEvent event) { enforce(event.getPlayer()); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        // Paper calls PlayerQuitEvent before saving player data.
        release(event.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        if (event.getPlayer() != changingMode && event.getNewGameMode() != GameMode.ADVENTURE)
            deny(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        var player = event.getPlayer();
        if (!restricted(player)) return;
        event.setCancelled(true);
        player.sendMessage(language.text(player, Message.PRISON_COMMAND_BLOCKED, NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandSend(PlayerCommandSendEvent event) {
        if (restricted(event.getPlayer())) event.getCommands().clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlight(PlayerToggleFlightEvent event) { if (event.isFlying()) deny(event, event.getPlayer()); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) { if (event.isGliding()) deny(event, event.getEntity()); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) { deny(event, event.getPlayer()); }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) { deny(event, event.getPlayer()); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) { deny(event, event.getEntity()); }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) { deny(event, event.getWhoClicked()); }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) { deny(event, event.getWhoClicked()); }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) { deny(event, event.getPlayer()); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) { deny(event, event.getPlayer()); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) { deny(event, event.getPlayer()); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) { deny(event, event.getPlayer()); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (restricted(event.getEntity()) || restricted(event.getMount())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (restricted(event.getEntity()) || restricted(event.getDamageSource().getCausingEntity()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCombust(EntityCombustByEntityEvent event) {
        Entity source = event.getCombuster();
        if (restricted(source) || source instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player && restricted(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectile(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player) deny(event, player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) { deny(event, event.getTarget()); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameEvent(GenericGameEvent event) { deny(event, event.getEntity()); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventory(InventoryOpenEvent event) { deny(event, event.getPlayer()); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) { deny(event, event.getEntity()); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExperience(PlayerPickupExperienceEvent event) { deny(event, event.getPlayer()); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) { deny(event, event.getEntity()); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAdvancement(PlayerAdvancementCriterionGrantEvent event) { deny(event, event.getPlayer()); }

    private record Settings(GameMode mode, boolean allowFlight, boolean collidable) { }
}
