package org.encinet.mik.feature.entertainment.music.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.encinet.mik.feature.entertainment.music.jukebox.DeviceTarget;
import org.encinet.mik.feature.entertainment.music.jukebox.JukeboxExperienceMode;
import org.encinet.mik.feature.entertainment.music.jukebox.JukeboxSettingsStore;
import org.encinet.mik.feature.entertainment.music.jukebox.MusicDevice;
import org.encinet.mik.feature.entertainment.music.playlist.Playlist;
import org.encinet.mik.feature.entertainment.music.storage.MusicState;
import org.encinet.mik.feature.entertainment.music.storage.MusicStore;

/** Device identity, personal records and live plot authorization; no playback progression. */
public final class MusicDeviceRegistry {
    private final MusicStore store;
    private final JukeboxSettingsStore settings;
    private final BiPredicate<Player, Location> permission;
    private final Function<UUID, World> worlds;
    private final NamespacedKey identity = new NamespacedKey("mik", "music_device_id");
    public MusicDeviceRegistry(
            MusicStore store, JukeboxSettingsStore settings, BiPredicate<Player, Location> permission) {
        this(store, settings, permission, Bukkit::getWorld);
    }
    MusicDeviceRegistry(MusicStore store, JukeboxSettingsStore settings,
            BiPredicate<Player, Location> permission, Function<UUID, World> worlds) {
        this.store = store;
        this.settings = settings;
        this.permission = permission;
        this.worlds = worlds;
    }
    public MusicState snapshot() {
        return store.snapshot();
    }
    public Location location(MusicDevice device) {
        World world = worlds.apply(device.world());
        return world == null ? null : new Location(world, device.x(), device.y(), device.z());
    }
    public Jukebox block(MusicDevice device) {
        Location l = location(device);
        if (l == null || !l.isChunkLoaded() || l.getBlock().getType() != Material.JUKEBOX
                || !(l.getBlock().getState() instanceof Jukebox box))
            return null;
        return device.id().toString().equals(
                       box.getPersistentDataContainer().get(identity, PersistentDataType.STRING))
                ? box
                : null;
    }
    public boolean online(MusicDevice device) {
        return block(device) != null;
    }
    public boolean local(Player player, MusicDevice device) {
        Location l = location(device);
        return l != null && l.getWorld().equals(player.getWorld())
                && player.getLocation().distanceSquared(l.clone().add(.5, .5, .5)) <= 64;
    }
    public boolean bound(UUID player, UUID target) {
        return store.snapshot().bindings().containsKey(MusicState.key(player, target));
    }
    public CompletableFuture<MusicDevice> discover(Jukebox box) {
        String encoded = box.getPersistentDataContainer().get(identity, PersistentDataType.STRING);
        UUID id;
        try {
            id = UUID.fromString(encoded);
        } catch (Exception invalid) {
            id = UUID.randomUUID();
        }
        MusicDevice existing = store.snapshot().devices().get(id);
        Location l = box.getLocation();
        if (existing != null
                && (!existing.world().equals(l.getWorld().getUID()) || existing.x() != l.getBlockX()
                        || existing.y() != l.getBlockY() || existing.z() != l.getBlockZ())) {
            id = UUID.randomUUID();
            existing = null;
        }
        box.getPersistentDataContainer().set(identity, PersistentDataType.STRING, id.toString());
        box.update(false, false);
        MusicDevice device = existing == null
                ? new MusicDevice(id, l.getWorld().getUID(), l.getWorld().getName(), l.getBlockX(),
                          l.getBlockY(), l.getBlockZ(),
                          "♪ " + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ(), 100, 64, null,
                          System.currentTimeMillis())
                : existing;
        return store.transact(e -> {
            e.devices.put(device.id(), device);
            return device;
        });
    }
    public List<DeviceTarget> targets(Player player) {
        MusicState state = store.snapshot();
        List<DeviceTarget> result = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (MusicDevice d : state.devices()
                        .values()
                        .stream()
                        .sorted(Comparator.comparing(MusicDevice::name).thenComparing(MusicDevice::id))
                        .toList()) {
            if (!bound(player.getUniqueId(), d.id()) && (d.group() == null || !bound(player.getUniqueId(), d.group())) && !local(player, d))
                continue;
            if (d.group() == null)
                result.add(new DeviceTarget(d.id(), alias(player, d), List.of(d)));
            else if (seen.add(d.group())) {
                MusicState.Group g = state.groups().get(d.group());
                if (g != null) {
                    DeviceTarget target = target(g.id());
                    result.add(new DeviceTarget(target.id(), name(player, target.id()), target.members()));
                }
            }
        }
        return List.copyOf(result);
    }
    public String alias(Player player, MusicDevice d) {
        MusicState.Binding b = store.snapshot().bindings().get(MusicState.key(player.getUniqueId(), d.id()));
        return b == null || b.alias().isBlank() ? d.name() : b.alias();
    }
    public String name(Player player, UUID id) {
        var target = target(id);
        var binding = store.snapshot().bindings().get(MusicState.key(player.getUniqueId(), id));
        return binding == null || binding.alias().isBlank() ? target.name() : binding.alias();
    }
    public CompletableFuture<Void> alias(UUID actor, UUID id, String name) {
        String clean = Playlist.text(name, 64);
        target(id);
        return store.transact(editor -> {
            String key = MusicState.key(actor, id);
            if (!editor.bindings.containsKey(key)) throw new MusicProblem(MusicProblem.Code.FORBIDDEN);
            editor.bindings.put(key, new MusicState.Binding(actor, id, clean));
            return null;
        });
    }
    public DeviceTarget target(UUID id) {
        MusicState s = store.snapshot();
        MusicState.Group group = s.groups().get(id);
        if (group != null)
            return new DeviceTarget(id, group.name(),
                    group.members().stream().map(s.devices()::get).filter(Objects::nonNull).toList());
        MusicDevice d = s.devices().get(id);
        if (d == null)
            throw new MusicProblem(MusicProblem.Code.NOT_FOUND);
        return new DeviceTarget(id, d.name(), List.of(d));
    }

    /** A grouped member remains addressable for output settings, but cannot own an independent playback program. */
    public DeviceTarget playbackTarget(UUID id) {
        DeviceTarget target = target(id);
        if (target.members().stream().anyMatch(device -> device.group() != null && !device.group().equals(id)))
            throw new MusicProblem(MusicProblem.Code.CONFLICT);
        return target;
    }
    public void authorize(Player player, DeviceTarget target) {
        if (!store.ready())
            throw new MusicProblem(MusicProblem.Code.UNAVAILABLE);
        if (target.members().isEmpty())
            throw new MusicProblem(MusicProblem.Code.NOT_FOUND);
        if (target.members().stream().noneMatch(this::online))
            throw new MusicProblem(MusicProblem.Code.OFFLINE);
        for (MusicDevice d : target.members()) {
            authorizeAccess(player,d,target.id());
            Jukebox box = block(d);
            if (box != null && settings.readExperienceMode(box) == JukeboxExperienceMode.RHYTHM
                    && target.members().size() == 1)
                throw new MusicProblem(MusicProblem.Code.BUSY);
        }
    }

    /** A visit addresses one physical device and can load its chunk after record and plot authorization. */
    public MusicDevice authorizeVisit(Player player, UUID id) {
        if (!store.ready()) throw new MusicProblem(MusicProblem.Code.UNAVAILABLE);
        MusicDevice device = snapshot().devices().get(id);
        if (device == null) throw new MusicProblem(MusicProblem.Code.NOT_FOUND);
        authorizeAccess(player,device,id);
        return device;
    }

    private void authorizeAccess(Player player, MusicDevice device, UUID target) {
        Location at = location(device);
        if (at == null) throw new MusicProblem(MusicProblem.Code.OFFLINE);
        if (!bound(player.getUniqueId(),target) && !bound(player.getUniqueId(),device.id())
                && (device.group() == null || !bound(player.getUniqueId(),device.group())) && !local(player,device))
            throw new MusicProblem(MusicProblem.Code.FORBIDDEN);
        if (!permission.test(player,at)) throw new MusicProblem(MusicProblem.Code.FORBIDDEN);
    }
    public MusicProblem.Code controlFailure(Player player, UUID id) {
        try { authorize(player, target(id)); return null; }
        catch (MusicProblem problem) { return problem.code(); }
    }
    public boolean mayControl(Player player, UUID id) { return controlFailure(player, id) == null; }
    public boolean mayBind(Player player, UUID id) {
        try { authorizeBinding(player, target(id)); return true; }
        catch (MusicProblem problem) { return false; }
    }
    private void authorizeBinding(Player player, DeviceTarget target) {
        boolean onsite = target.members().stream().anyMatch(d -> local(player, d));
        if (!onsite)
            throw new MusicProblem(MusicProblem.Code.FORBIDDEN);
        for (MusicDevice d : target.members())
            if (block(d) == null || !permission.test(player, location(d)))
                throw new MusicProblem(MusicProblem.Code.FORBIDDEN);
    }
    public CompletableFuture<Void> bind(Player player, UUID id, String alias) {
        authorizeBinding(player, target(id));
        UUID actor = player.getUniqueId();
        return store.transact(e -> {
            e.bindings.put(MusicState.key(actor, id), new MusicState.Binding(actor, id, alias));
            return null;
        });
    }
    public CompletableFuture<Void> unbind(UUID actor, UUID id) {
        target(id);
        return store.transact(e -> {
            e.bindings.remove(MusicState.key(actor, id));
            return null;
        });
    }
}
