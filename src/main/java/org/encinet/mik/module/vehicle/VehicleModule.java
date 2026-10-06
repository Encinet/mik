package org.encinet.mik.module.vehicle;

import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Input;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.integration.axiom.AxiomGizmoService;
import org.encinet.mik.integration.axiom.AxiomVehicleProtection;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.vehicle.event.VehicleMoveEvent;
import org.encinet.mik.module.vehicle.event.VehicleUseEvent;
import org.joml.Quaterniond;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class VehicleModule implements Listener {
    private record Mount(UUID vehicle, int seat) { }
    record ModelImport(VehicleDefinition definition, Location origin) { }
    private record CapturedSelection(Location origin, List<Display> displays) { }
    private final JavaPlugin plugin;
    private final LanguageService language;
    private final VehicleStore store;
    private final VehicleHud hud;
    private final VehicleModelEditor modelEditor;
    private final VehicleDashboard dashboard;
    private final AxiomGizmoService gizmoService;
    private final AxiomVehicleProtection axiomProtection;
    private final UUID gizmoOwner = UUID.randomUUID();
    private AxiomGizmoService.Scope axiomGizmos;
    private final NamespacedKey entityKey;
    private final VehiclePhysics physics = new VehiclePhysics();
    private final Map<String, VehicleDefinition> definitions = new LinkedHashMap<>();
    private final Map<UUID, VehicleInstance> instances = new LinkedHashMap<>();
    private final Map<UUID, Mount> mounts = new HashMap<>();
    private final Map<UUID, VehicleSelection> selections = new HashMap<>();
    private final Map<UUID, Location> origins = new HashMap<>();
    private final Set<UUID> hudHidden = new LinkedHashSet<>();
    private final Set<UUID> failed = new LinkedHashSet<>();
    private BukkitTask task;
    private boolean enabled;
    private int tick;
    private int maximumActive = 16;
    private int maximumInstances = 256;
    private int maximumPerOwner = 16;
    private double activationDistance = 80;
    private Set<String> disabledWorlds = Set.of();

    public VehicleModule(JavaPlugin plugin, LanguageService language) {
        this(plugin, language, null);
    }

    public VehicleModule(JavaPlugin plugin, LanguageService language, AxiomGizmoService gizmoService) {
        this.plugin = plugin;
        this.language = language;
        this.store = new VehicleStore(plugin.getDataFolder().toPath().resolve("vehicles"));
        this.hud = new VehicleHud(language);
        this.entityKey = new NamespacedKey(plugin, "vehicle_instance");
        this.modelEditor = new VehicleModelEditor(plugin, language, this);
        this.dashboard = new VehicleDashboard(language, this);
        this.gizmoService = gizmoService;
        this.axiomProtection = new AxiomVehicleProtection(plugin, this::excludedSource);
    }

    public void enable() {
        if (enabled) return;
        try {
            Path settingsPath = plugin.getDataFolder().toPath().resolve("vehicles.yml");
            if (!Files.exists(settingsPath)) plugin.saveResource("vehicles.yml", false);
            YamlConfiguration settings = new YamlConfiguration();
            settings.load(settingsPath.toFile());
            maximumActive = boundedInteger(settings, "maximum-active", 1, 64);
            maximumInstances = boundedInteger(settings, "maximum-instances", 1, 512);
            maximumPerOwner = boundedInteger(settings, "maximum-per-owner", 1, maximumInstances);
            activationDistance = settings.getDouble("activation-distance");
            VehicleDefinition.positive(activationDistance, "activation distance", 128);
            disabledWorlds = Set.copyOf(settings.getStringList("disabled-worlds"));
            Map<String, VehicleDefinition> loadedDefinitions = store.loadDefinitions();
            validatePayloads(loadedDefinitions.values());
            Map<UUID, VehicleInstance> loadedInstances = new LinkedHashMap<>();
            for (VehicleInstance.Snapshot snapshot : store.loadInstances()) {
                VehicleDefinition definition = loadedDefinitions.get(snapshot.definition());
                if (definition == null) throw new IOException("Missing definition: " + snapshot.definition());
                if (loadedInstances.put(snapshot.id(), snapshot.restore(definition)) != null)
                    throw new IOException("Duplicate vehicle id");
            }
            definitions.putAll(loadedDefinitions);
            instances.putAll(loadedInstances);
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException exception) {
            throw new IllegalStateException("Cannot load vehicles; existing data has not been overwritten", exception);
        }
        enabled = true;
        if (gizmoService != null) axiomGizmos = gizmoService.scope("vehicles");
        modelEditor.enable();
        axiomProtection.enable();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (World world : plugin.getServer().getWorlds()) for (Entity entity : world.getEntities()) cleanOrphan(entity);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    public void disable() {
        dashboard.close();
        modelEditor.close();
        boolean save = enabled;
        enabled = false;
        if (task != null) { task.cancel(); task = null; }
        HandlerList.unregisterAll(this);
        axiomProtection.close();
        RuntimeException failure = null;
        if (save) {
            try { persist(); }
            catch (IOException exception) { failure = new IllegalStateException("Could not save vehicles", exception); }
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) hud.clear(player);
        for (VehicleInstance instance : instances.values()) {
            try { deactivate(instance); }
            catch (RuntimeException exception) {
                if (failure == null) failure = exception; else failure.addSuppressed(exception);
            }
        }
        mounts.clear();
        selections.clear();
        origins.clear();
        hudHidden.clear();
        failed.clear();
        instances.clear();
        definitions.clear();
        if (axiomGizmos != null) { axiomGizmos.close(); axiomGizmos = null; }
        if (failure != null) throw failure;
    }

    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        new VehicleCommands(this, language).register(manager);
    }

    void requireCommandAccess(Player player, String permission) {
        if (!enabled) throw new IllegalStateException("Vehicle module unavailable");
        if (!player.isOnline() || !player.hasPermission(permission)) throw new IllegalArgumentException(permission + " required");
    }

    void modelCommand(Player player, String... arguments) throws IOException {
        modelEditor.execute(player, arguments);
    }

    void setOrigin(Player player) {
        requireAdmin(player);
        origins.put(player.getUniqueId(), player.getLocation().clone());
        success(player, "Origin set; local +Z follows your horizontal view");
    }

    void listVehicles(Player player) {
        success(player, "Models: " + String.join(", ", definitions.keySet()) + " | Vehicles: "
                + instances.values().stream().filter(instance -> canAccess(player, instance))
                .map(instance -> instance.id + " (" + instance.body.definition.id() + ")").limit(32).toList());
    }

    List<UUID> accessibleVehicleIds(Player player) {
        return instances.values().stream().filter(instance -> canAccess(player, instance)).map(instance -> instance.id).toList();
    }

    List<UUID> selectableDisplayIds(Player player) {
        return player.getNearbyEntities(32, 32, 32).stream()
                .filter(entity -> entity instanceof Display && vehicleId(entity) == null && !modelEditor.isPreview(entity))
                .filter(entity -> entity.getLocation().distanceSquared(player.getLocation()) <= 1024)
                .map(Entity::getUniqueId).toList();
    }

    void inspect(Player player, UUID id) {
        VehicleInstance instance = target(player, id);
        requireAccess(player, instance);
        success(player, instance.id + " " + instance.body.definition.kind() + " | " + instance.body.velocity.length()
                + " b/s | " + instance.body.transmission.label() + " | " + Math.round(instance.body.transmission.rpm)
                + " RPM | fuel=" + instance.body.fuel + " health=" + instance.body.health + " active=" + (instance.entities != null));
    }

    void setAccess(Player player, boolean publicAccess) throws IOException {
        VehicleInstance instance = target(player, null);
        requireOwner(player, instance);
        boolean old = instance.publicAccess;
        instance.publicAccess = publicAccess;
        try { persist(); } catch (IOException exception) { instance.publicAccess = old; throw exception; }
        success(player, publicAccess ? "public" : "private");
    }

    void storageFailure(Player player, IOException exception) {
        plugin.getLogger().log(Level.SEVERE, "Vehicle storage operation failed", exception);
        player.sendMessage(language.text(player, Message.VEHICLE_ERROR, NamedTextColor.RED, "Storage failed; previous data retained"));
    }

    void clearSelection(Player player) {
        requireAdmin(player);
        selections.remove(player.getUniqueId());
        origins.remove(player.getUniqueId());
        success(player, "Selected 0 displays");
    }

    void selectRadius(Player player, double radius) {
        requireAdmin(player);
        VehicleDefinition.positive(radius, "selection radius", 16);
        Set<UUID> replacement = new LinkedHashSet<>(selection(player).ids());
        for (Entity entity : player.getNearbyEntities(radius, radius, radius))
            if (entity instanceof Display && vehicleId(entity) == null && !modelEditor.isPreview(entity)) replacement.add(entity.getUniqueId());
        if (replacement.size() > 128) throw new IllegalArgumentException("Select at most 128 displays");
        selections.put(player.getUniqueId(), VehicleSelection.manual(replacement));
        success(player, "Selected " + replacement.size() + " displays");
    }

    void selectEntity(Player player, UUID id) {
        requireAdmin(player);
        Set<UUID> selected = new LinkedHashSet<>(selection(player).ids());
        Entity entity = plugin.getServer().getEntity(id);
        if (!(entity instanceof Display) || !entity.getWorld().equals(player.getWorld()) || vehicleId(entity) != null || modelEditor.isPreview(entity)
                || entity.getLocation().distanceSquared(player.getLocation()) > 1024)
            throw new IllegalArgumentException("Select an unmanaged display within 32 blocks");
        if (!selected.remove(id)) {
            if (selected.size() >= 128) throw new IllegalArgumentException("Select at most 128 displays");
            selected.add(id);
        }
        selections.put(player.getUniqueId(), VehicleSelection.manual(selected));
        success(player, "Selected " + selected.size() + " displays");
    }

    void selectGroup(Player player, UUID seed) {
        VehicleDisplayGroup group = displayGroup(player, seed, false);
        captureOrigin(player, group.origin());
        selections.put(player.getUniqueId(), VehicleSelection.group(group));
        player.sendMessage(language.text(player, Message.VEHICLE_GROUP_SELECTED, NamedTextColor.GREEN, group.displays().size(), group.root().toString()));
    }

    void selectionInfo(Player player) {
        requireAdmin(player);
        VehicleSelection selection = selection(player);
        Location origin = editorOrigin(player);
        player.sendMessage(language.text(player, Message.VEHICLE_SELECTION_INFO, NamedTextColor.AQUA, selection.ids().size(),
                selection.root() == null ? "—" : selection.root().toString(), origin.getX(), origin.getY(), origin.getZ()));
    }

    private VehicleSelection selection(Player player) { return selections.getOrDefault(player.getUniqueId(), VehicleSelection.manual(Set.of())); }

    private boolean excludedSource(Entity entity) { return vehicleId(entity) != null || modelEditor.isPreview(entity); }

    private VehicleDisplayGroup displayGroup(Player player, UUID seed) {
        return displayGroup(player, seed, true);
    }

    private VehicleDisplayGroup displayGroup(Player player, UUID seed, boolean preferSelection) {
        requireAdmin(player);
        VehicleSelection selected = selection(player);
        if (seed == null && preferSelection && selected.root() != null) return selectedGroup(player, selected);
        Entity entity;
        if (seed != null) entity = plugin.getServer().getEntity(seed);
        else {
            Location eye = player.getEyeLocation();
            var obstruction = player.getWorld().rayTraceBlocks(eye, eye.getDirection(), 32, FluidCollisionMode.NEVER, true);
            double maximum = obstruction == null ? 32 : obstruction.getHitPosition().distance(eye.toVector());
            entity = VehicleDisplayPicker.pick(eye, eye.getDirection(), player.getNearbyEntities(32, 32, 32), this::excludedSource, maximum);
        }
        return VehicleDisplayGroup.inspect(entity, player.getLocation(), this::excludedSource);
    }

    private VehicleDisplayGroup selectedGroup(Player player, VehicleSelection selection) {
        Entity root = plugin.getServer().getEntity(selection.root());
        if (root == null) throw new VehicleImportException(Message.VEHICLE_GROUP_CHANGED);
        VehicleDisplayGroup group = VehicleDisplayGroup.inspect(root, player.getLocation(), this::excludedSource);
        selection.requireUnchanged(group);
        return group;
    }

    ModelImport importGroup(Player player, String id, VehicleDefinition.Kind kind, UUID seed) {
        VehicleDisplayGroup group = displayGroup(player, seed);
        Location origin = captureOrigin(player, group.origin());
        validateCapture(player, origin, group.displays());
        recheckGroup(player, group);
        return new ModelImport(VehicleModel.capture(id, kind, origin, group.displays()), origin.clone());
    }

    List<VehicleDefinition.Part> groupParts(Player player, UUID seed, Location origin) {
        VehicleDisplayGroup group = displayGroup(player, seed);
        validateCapture(player, origin, group.displays());
        recheckGroup(player, group);
        return VehicleModel.captureParts(origin, group.displays());
    }

    private void recheckGroup(Player player, VehicleDisplayGroup group) {
        Entity root = plugin.getServer().getEntity(group.root());
        if (root == null) throw new VehicleImportException(Message.VEHICLE_GROUP_CHANGED);
        VehicleSelection.group(group).requireUnchanged(VehicleDisplayGroup.inspect(root, player.getLocation(), this::excludedSource));
    }

    void capture(Player player, String id, VehicleDefinition.Kind kind) throws IOException {
        requireAdmin(player);
        if (definitions.size() >= 256) throw new IllegalArgumentException("Model limit reached");
        if (definitions.containsKey(id)) throw new IllegalArgumentException("Model already exists; edit it or use a new id");
        CapturedSelection selected = captureSelection(player);
        VehicleDefinition definition = VehicleModel.capture(id, kind, selected.origin(), selected.displays());
        store.saveDefinition(definition);
        definitions.put(definition.id(), definition);
        success(player, "Captured " + definition.id() + "; original entities retained");
    }

    void spawn(Player player, String model, Location requestedLocation) throws IOException {
        requireAdmin(player);
        VehicleDefinition definition = definitions.get(model);
        if (definition == null) throw new IllegalArgumentException("Unknown model");
        if (instances.size() >= maximumInstances || instances.values().stream().filter(instance -> instance.owner.equals(player.getUniqueId())).count() >= maximumPerOwner)
            throw new IllegalArgumentException("Vehicle quota reached");
        Location location = requestedLocation != null ? requestedLocation.clone()
                : player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(4)).add(0, 1, 0);
        location.setYaw(player.getYaw());
        location.setPitch(0);
        VehicleVector origin = vector(location);
        if (Math.abs(origin.coordinateX()) > 30000000 || Math.abs(origin.coordinateZ()) > 30000000) throw new IllegalArgumentException("Outside world bounds");
        UUID id = UUID.randomUUID();
        requireAllowed(player, id, location, VehicleUseEvent.Action.SPAWN);
        VehicleInstance instance = new VehicleInstance(id, player.getWorld().getUID(), player.getUniqueId(),
                new VehicleBody(definition, origin, VehicleModel.orientation(player.getYaw())));
        VehicleWorld world = world(instance);
        if (!world.loaded(origin) || !world.clear(instance.body)) throw new IllegalArgumentException("Spawn intersects terrain, border, unloaded chunks or a vehicle");
        activate(instance);
        instances.put(id, instance);
        refreshGizmos();
        try { persist(); }
        catch (IOException exception) { instances.remove(id); deactivate(instance); throw exception; }
        success(player, id.toString());
    }

    void enter(Player player, VehicleInstance instance, boolean drive) {
        requireAccess(player, instance);
        if (mounts.containsKey(player.getUniqueId()) || player.isInsideVehicle() || player.isDead()) throw new IllegalArgumentException("Already riding or unavailable");
        if (!player.getWorld().getUID().equals(instance.worldId) || vector(player.getLocation()).subtract(instance.body.origin()).length() > 32)
            throw new IllegalArgumentException("Vehicle is too far away");
        requireAllowed(player, instance.id, location(instance), VehicleUseEvent.Action.ENTER);
        activate(instance);
        int seat = instance.entities.firstAvailable(drive);
        if (seat < 0) throw new IllegalArgumentException("No available seat");
        Mount binding = new Mount(instance.id, seat);
        mounts.put(player.getUniqueId(), binding);
        if (!instance.entities.seat(seat).addPassenger(player)) {
            mounts.remove(player.getUniqueId());
            throw new IllegalArgumentException("Mount was rejected");
        }
        if (instance.body.definition.seats().get(seat).driver()) {
            VehicleBody body = instance.body;
            if (body.fuel > 0 && body.health > 0) body.engineRunning = true;
            if (body.definition.kind() == VehicleDefinition.Kind.CAR && body.transmission.selector == VehicleTransmission.Selector.P)
                body.transmission.select(VehicleTransmission.Selector.D, body.localVelocity().coordinateZ(), body.definition.engine());
            player.sendMessage(language.text(player, Message.VEHICLE_CONTROLS, NamedTextColor.AQUA, body.definition.kind().name()));
            if (body.definition.kind() == VehicleDefinition.Kind.CAR && !hudHidden.contains(player.getUniqueId())) {
                try { dashboard.open(player, instance.id, VehicleDashboardMenu.Page.DRIVE); }
                catch (RuntimeException exception) { plugin.getLogger().log(Level.WARNING, "Could not open vehicle dashboard", exception); }
            }
        }
    }

    private void toggleMode(Player player) {
        success(player, drivingControl(player, null, "mode"));
    }

    String drivingControl(Player player, UUID expectedVehicle, String input) {
        VehicleInstance instance = dashboardInstance(player, expectedVehicle);
        return VehicleControls.apply(instance.body, input);
    }

    private VehicleInstance dashboardInstance(Player player, UUID expectedVehicle) {
        if (!enabled || !player.isOnline() || !player.hasPermission("mik.vehicle.use")) throw new IllegalArgumentException("Vehicle unavailable");
        VehicleInstance instance = controlled(player);
        if (expectedVehicle != null && !instance.id.equals(expectedVehicle)) throw new IllegalArgumentException("Driver seat changed; reopen the dashboard");
        requireAccess(player, instance);
        return instance;
    }

    VehicleDashboardMenu.View dashboardView(Player player, UUID expectedVehicle) {
        VehicleInstance instance = dashboardInstance(player, expectedVehicle);
        return new VehicleDashboardMenu.View(instance.id, instance.body.definition.id(), VehicleHud.Frame.of(instance.body),
                instance.entities.passengerCount(), instance.body.definition.seats().size());
    }

    void toggleHud(Player player, Boolean requestedVisibility) {
        boolean visible = requestedVisibility == null ? hudHidden.contains(player.getUniqueId()) : requestedVisibility;
        if (visible) hudHidden.remove(player.getUniqueId()); else hudHidden.add(player.getUniqueId());
        hud.clear(player);
        dashboard.forget(player.getUniqueId());
        Mount mount = mounts.get(player.getUniqueId());
        if (visible && mount != null && instances.get(mount.vehicle()) != null
                && instances.get(mount.vehicle()).body.definition.seats().get(mount.seat()).driver())
            dashboard.open(player, mount.vehicle(), VehicleDashboardMenu.Page.DRIVE);
    }

    void closeDashboard(Player player) { dashboard.forget(player.getUniqueId()); }

    void openDashboard(Player player, VehicleDashboardMenu.Page page) {
        VehicleInstance instance = dashboardInstance(player, null);
        if (page == VehicleDashboardMenu.Page.GEARBOX && instance.body.definition.kind() != VehicleDefinition.Kind.CAR)
            throw new IllegalArgumentException("Cars only");
        hudHidden.remove(player.getUniqueId());
        dashboard.open(player, instance.id, page);
    }

    void service(Player player, boolean fuel) throws IOException {
        VehicleInstance instance = target(player, null);
        requireOwner(player, instance);
        if (instance.body.velocity.length() > 0.5 || instance.body.engineRunning) throw new IllegalArgumentException("Stop vehicle and engine first");
        requireAllowed(player, instance.id, location(instance), VehicleUseEvent.Action.SERVICE);
        ItemStack item = player.getInventory().getItemInMainHand();
        if (fuel ? item.getType() != Material.COAL && item.getType() != Material.CHARCOAL : item.getType() != Material.IRON_INGOT)
            throw new IllegalArgumentException(fuel ? "Hold coal or charcoal" : "Hold an iron ingot");
        double before = fuel ? instance.body.fuel : instance.body.health;
        double maximum = fuel ? instance.body.definition.fuelCapacity() : 100;
        if (before >= maximum) throw new IllegalArgumentException("Already full");
        if (fuel) instance.body.fuel = Math.min(maximum, before + 10); else instance.body.health = Math.min(maximum, before + 10);
        try { persist(); } catch (IOException exception) {
            if (fuel) instance.body.fuel = before; else instance.body.health = before;
            throw exception;
        }
        item.setAmount(item.getAmount() - 1);
        success(player, fuel ? "REFUELED" : "REPAIRED");
    }

    void remove(Player player, VehicleInstance instance) throws IOException {
        requireOwner(player, instance);
        if (instance.entities != null && instance.entities.occupied() || instance.body.velocity.length() > 0.5)
            throw new IllegalArgumentException("Stop and empty vehicle before removal");
        requireAllowed(player, instance.id, location(instance), VehicleUseEvent.Action.REMOVE);
        instances.remove(instance.id);
        try { persist(); } catch (IOException exception) { instances.put(instance.id, instance); throw exception; }
        deactivate(instance);
        failed.remove(instance.id);
        success(player, "REMOVED");
    }

    void reload(Player player) throws IOException {
        requireAdmin(player);
        if (instances.values().stream().anyMatch(instance -> instance.body.velocity.length() > 0.1
                || instance.body.engineRunning || instance.entities != null && instance.entities.occupied()))
            throw new IllegalArgumentException("Stop engines and empty all vehicles before reload");
        Map<String, VehicleDefinition> replacement = store.loadDefinitions();
        validatePayloads(replacement.values());
        Map<UUID, VehicleInstance> restored = new LinkedHashMap<>();
        for (VehicleInstance instance : instances.values()) {
            VehicleDefinition definition = replacement.get(instance.body.definition.id());
            if (definition == null) throw new IllegalArgumentException("Cannot remove a model with existing vehicles");
            restored.put(instance.id, instance.snapshot().restore(definition));
        }
        for (VehicleInstance instance : restored.values()) {
            World available = plugin.getServer().getWorld(instance.worldId);
            if (available != null && world(instance).loaded(instance.body.origin()) && !world(instance, restored.values()).clear(instance.body))
                throw new IllegalArgumentException("Reloaded collision shape intersects terrain or a vehicle: " + instance.id);
        }
        for (VehicleInstance instance : instances.values()) deactivate(instance);
        definitions.clear();
        definitions.putAll(replacement);
        instances.clear();
        instances.putAll(restored);
        failed.clear();
        success(player, "RELOADED");
    }

    private void tick() {
        tick++;
        modelEditor.tick();
        if (tick % 20 == 0) refreshGizmos();
        for (UUID playerId : List.copyOf(mounts.keySet())) {
            Player player = plugin.getServer().getPlayer(playerId);
            Mount binding = mounts.get(playerId);
            VehicleInstance instance = instances.get(binding.vehicle());
            if (player != null && (instance == null || !canAccess(player, instance) || !player.hasPermission("mik.vehicle.use"))) detach(player);
        }
        for (VehicleInstance instance : List.copyOf(instances.values())) {
            try {
                World bukkitWorld = plugin.getServer().getWorld(instance.worldId);
                if (bukkitWorld == null || disabledWorlds.contains(bukkitWorld.getName()) || failed.contains(instance.id)) continue;
                boolean nearby = bukkitWorld.getPlayers().stream().anyMatch(player -> vector(player.getLocation()).subtract(instance.body.origin()).length() < activationDistance);
                if (instance.entities == null) {
                    if (!nearby && instance.body.velocity.length() <= 0.1 || activeCount() >= maximumActive) continue;
                    VehicleWorld candidate = world(instance);
                    if (!candidate.loaded(instance.body.origin()) || instance.body.definition.seats().stream()
                            .anyMatch(seat -> !candidate.loaded(instance.body.point(seat.position())))) continue;
                    activate(instance);
                }
                if (!instance.entities.valid()) { deactivate(instance); continue; }
                Player driver = instance.entities.driver();
                if (driver == null) instance.body.engineRunning = false;
                if (!nearby && !instance.entities.occupied() && instance.body.resting()) { deactivate(instance); continue; }
                VehicleInput input = readInput(driver);
                if (instance.body.resting() && !input.occupied() && tick % 10 != 0) continue;
                VehicleBody body = instance.body;
                VehicleVector before = body.position;
                Quaterniond rotation = new Quaterniond(body.orientation);
                Location from = location(instance);
                VehicleWorld world = world(instance);
                boolean contact = false;
                double radius = body.definition.colliders().stream().mapToDouble(collider ->
                        collider.center().subtract(body.definition.centerOfMass()).length() + collider.halfSize().length()).max().orElse(1);
                int substeps = Math.clamp((int) Math.ceil((body.velocity.length() + body.angularVelocity.length() * radius) * 0.05 / 0.2), 5, 20);
                for (int step = 0; step < substeps; step++) {
                    world.move(body, physics.step(body, input, world, 0.05 / substeps));
                    contact |= body.blocked;
                }
                body.blocked = contact;
                if (!world.loaded(body.origin()) || body.definition.seats().stream().anyMatch(seat -> !world.loaded(body.point(seat.position())))) {
                    body.position = before;
                    body.orientation.set(rotation);
                    body.velocity = VehicleVector.ZERO;
                    body.angularVelocity = VehicleVector.ZERO;
                    body.blocked = true;
                }
                Location to = location(instance);
                if (from.distanceSquared(to) > 1.0E-8 || !rotation.equals(body.orientation)) {
                    VehicleMoveEvent event = new VehicleMoveEvent(instance.id, instance.owner, driver, from, to);
                    plugin.getServer().getPluginManager().callEvent(event);
                    if (event.isCancelled()) {
                        body.position = before;
                        body.orientation.set(rotation);
                        body.velocity = VehicleVector.ZERO;
                        body.angularVelocity = VehicleVector.ZERO;
                        body.blocked = true;
                    }
                }
                instance.entities.synchronize();
                if (driver != null && tick % 2 == 0 && !hudHidden.contains(driver.getUniqueId()))
                    hud.show(driver, VehicleHud.Frame.of(body), body.definition.engine().redlineRpm());
            } catch (RuntimeException exception) {
                failed.add(instance.id);
                instance.body.engineRunning = false;
                instance.body.velocity = VehicleVector.ZERO;
                instance.body.angularVelocity = VehicleVector.ZERO;
                try { deactivate(instance); } catch (RuntimeException cleanup) { exception.addSuppressed(cleanup); }
                plugin.getLogger().log(Level.SEVERE, "Vehicle " + instance.id + " suspended; use /vehicle reload after correcting its definition", exception);
            }
        }
        if (tick % 600 == 0) {
            try { persist(); } catch (IOException exception) { plugin.getLogger().log(Level.SEVERE, "Vehicle checkpoint failed", exception); }
        }
    }

    private VehicleInput readInput(Player player) {
        if (player == null || !player.isOnline() || !player.hasPermission("mik.vehicle.use")) return VehicleInput.EMPTY;
        Input input = player.getCurrentInput();
        return new VehicleInput((input.isForward() ? 1 : 0) - (input.isBackward() ? 1 : 0),
                (input.isRight() ? 1 : 0) - (input.isLeft() ? 1 : 0), input.isJump(), true);
    }

    private void activate(VehicleInstance instance) {
        if (instance.entities != null) return;
        if (failed.contains(instance.id) || activeCount() >= maximumActive) throw new IllegalArgumentException("Active vehicle budget reached or vehicle suspended");
        World world = plugin.getServer().getWorld(instance.worldId);
        if (world == null || disabledWorlds.contains(world.getName())) throw new IllegalArgumentException("World unavailable");
        VehicleWorld environment = world(instance);
        if (!environment.loaded(instance.body.origin()) || instance.body.definition.seats().stream().anyMatch(seat -> !environment.loaded(instance.body.point(seat.position()))))
            throw new IllegalArgumentException("Vehicle chunks are not loaded");
        instance.entities = new VehicleEntityGroup(instance, world, entityKey);
        instance.body.grounded = false;
        instance.body.floating = false;
        refreshGizmos();
    }

    private void deactivate(VehicleInstance instance) {
        if (instance.entities == null) return;
        instance.body.engineRunning = false;
        for (UUID id : List.copyOf(mounts.keySet())) if (mounts.get(id).vehicle().equals(instance.id)) {
            Player player = plugin.getServer().getPlayer(id);
            dashboard.forget(id);
            if (player != null) hud.clear(player);
            mounts.remove(id);
        }
        VehicleEntityGroup group = instance.entities;
        instance.entities = null;
        group.remove();
        refreshGizmos();
    }

    void refreshGizmos() {
        if (!enabled || axiomGizmos == null) return;
        Map<UUID, Set<UUID>> byWorld = new HashMap<>();
        for (VehicleInstance instance : instances.values()) if (instance.entities != null)
            byWorld.computeIfAbsent(instance.worldId, ignored -> new LinkedHashSet<>()).addAll(instance.entities.entityIds());
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            Set<UUID> ignored = new LinkedHashSet<>(byWorld.getOrDefault(viewer.getWorld().getUID(), Set.of()));
            ignored.addAll(modelEditor.previewIds(viewer.getUniqueId()));
            axiomGizmos.synchronize(viewer, gizmoOwner, ignored);
        }
    }

    private VehicleWorld world(VehicleInstance instance) {
        return world(instance, instances.values());
    }

    private VehicleWorld world(VehicleInstance instance, Iterable<VehicleInstance> vehicles) {
        World world = plugin.getServer().getWorld(instance.worldId);
        if (world == null) throw new IllegalArgumentException("World unavailable");
        List<VehicleCollision.Box> obstacles = new ArrayList<>();
        for (VehicleInstance other : vehicles) if (!other.id.equals(instance.id) && other.worldId.equals(instance.worldId)
                && other.body.origin().subtract(instance.body.origin()).length() < 72)
            for (VehicleDefinition.Collider collider : other.body.definition.colliders())
                obstacles.add(VehicleCollision.Box.body(collider, other.body.origin(), other.body.orientation));
        return new VehicleWorld(world, obstacles);
    }

    VehicleInstance target(Player player, UUID id) {
        if (id != null) {
            VehicleInstance instance = instances.get(id);
            if (instance == null) throw new IllegalArgumentException("Unknown vehicle");
            return instance;
        }
        Mount mount = mounts.get(player.getUniqueId());
        if (mount != null && instances.containsKey(mount.vehicle())) return instances.get(mount.vehicle());
        VehicleInstance nearest = null;
        double distance = 32;
        for (VehicleInstance instance : instances.values()) if (instance.worldId.equals(player.getWorld().getUID()) && canAccess(player, instance)) {
            double candidate = instance.body.origin().subtract(vector(player.getLocation())).length();
            if (candidate < distance) { nearest = instance; distance = candidate; }
        }
        if (nearest == null) throw new IllegalArgumentException("No accessible vehicle within 32 blocks");
        return nearest;
    }

    private VehicleInstance controlled(Player player) {
        Mount mount = mounts.get(player.getUniqueId());
        VehicleInstance instance = mount == null ? null : instances.get(mount.vehicle());
        if (instance == null || instance.entities == null || instance.entities.driver() != player
                || !instance.body.definition.seats().get(mount.seat()).driver()) throw new IllegalArgumentException("Driver seat required");
        return instance;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() == EquipmentSlot.HAND && modelEditor.place(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        UUID id = vehicleId(event.getRightClicked());
        if (id == null) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND || !event.getPlayer().hasPermission("mik.vehicle.use") || mounts.containsKey(event.getPlayer().getUniqueId())) return;
        VehicleInstance instance = instances.get(id);
        if (instance == null) return;
        try { enter(event.getPlayer(), instance, !event.getPlayer().isSneaking()); }
        catch (IllegalArgumentException exception) { event.getPlayer().sendMessage(language.text(event.getPlayer(), Message.VEHICLE_ERROR, NamedTextColor.RED, exception.getMessage())); }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEditorPoint(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (modelEditor.place(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (excludedSource(event.getEntity()) || modelEditor.isPreview(event.getMount())) { event.setCancelled(true); return; }
        UUID id = vehicleId(event.getMount());
        if (id == null) return;
        if (!(event.getEntity() instanceof Player player)) { event.setCancelled(true); return; }
        Mount mount = mounts.get(player.getUniqueId());
        VehicleInstance instance = instances.get(id);
        if (!player.hasPermission("mik.vehicle.use") || mount == null || !mount.vehicle().equals(id) || instance == null || instance.entities == null
                || instance.entities.seatOf(event.getMount()) != mount.seat() || !canAccess(player, instance)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player) || vehicleId(event.getDismounted()) == null) return;
        Mount mount = mounts.remove(player.getUniqueId());
        dashboard.forget(player.getUniqueId());
        hud.clear(player);
        if (mount == null) return;
        VehicleInstance instance = instances.get(mount.vehicle());
        if (instance == null || instance.entities == null || instance.entities.removing()) return;
        if (instance.body.definition.seats().get(mount.seat()).driver()) instance.body.engineRunning = false;
        Location exit = location(instance.body.point(instance.body.definition.seats().get(mount.seat()).exit()), player.getWorld());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (enabled && player.isOnline() && !player.isInsideVehicle() && !player.isDead()
                    && player.getWorld().equals(exit.getWorld()) && player.getLocation().distanceSquared(exit) < 1024
                    && safeExit(exit)) {
                exit.setYaw(player.getYaw());
                exit.setPitch(player.getPitch());
                player.teleport(exit);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        Mount mount = mounts.get(event.getPlayer().getUniqueId());
        if (mount == null) return;
        VehicleInstance instance = instances.get(mount.vehicle());
        if (instance == null || !instance.body.definition.seats().get(mount.seat()).driver()) return;
        event.setCancelled(true);
        int difference = Math.floorMod(event.getNewSlot() - event.getPreviousSlot(), 9);
        int direction = difference <= 4 ? -1 : 1;
        if (instance.body.definition.kind() == VehicleDefinition.Kind.CAR)
            VehicleControls.stepGear(instance.body, direction);
        else instance.body.throttle = Math.clamp(instance.body.throttle + direction * 0.05,
                instance.body.definition.kind() == VehicleDefinition.Kind.BOAT ? -0.5 : 0, 1);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Mount mount = mounts.get(event.getPlayer().getUniqueId());
        if (mount == null) return;
        VehicleInstance instance = instances.get(mount.vehicle());
        if (instance == null || !instance.body.definition.seats().get(mount.seat()).driver()) return;
        event.setCancelled(true);
        toggleMode(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (vehicleId(event.getEntity()) != null) event.setCancelled(true);
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { modelEditor.forget(event.getPlayer()); detach(event.getPlayer()); selections.remove(event.getPlayer().getUniqueId()); origins.remove(event.getPlayer().getUniqueId()); hudHidden.remove(event.getPlayer().getUniqueId()); if (axiomGizmos != null) axiomGizmos.forgetViewer(event.getPlayer().getUniqueId()); }
    @EventHandler public void onDeath(PlayerDeathEvent event) { detach(event.getEntity()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) { modelEditor.clearPreview(event.getPlayer().getUniqueId()); if (mounts.containsKey(event.getPlayer().getUniqueId())) detach(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        modelEditor.unload(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
        Set<UUID> affected = new LinkedHashSet<>();
        for (Entity entity : event.getChunk().getEntities()) {
            UUID id = vehicleId(entity);
            if (id != null) affected.add(id);
        }
        for (VehicleInstance instance : instances.values()) if (instance.worldId.equals(event.getWorld().getUID()) && instance.entities != null
                && (affected.contains(instance.id) || ((int) Math.floor(instance.body.origin().coordinateX()) >> 4) == event.getChunk().getX()
                && ((int) Math.floor(instance.body.origin().coordinateZ()) >> 4) == event.getChunk().getZ())) deactivate(instance);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) { for (Entity entity : event.getChunk().getEntities()) cleanOrphan(entity); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        modelEditor.unload(event.getWorld(), null, null);
        for (VehicleInstance instance : instances.values()) if (instance.worldId.equals(event.getWorld().getUID())) deactivate(instance);
    }

    private void detach(Player player) {
        dashboard.forget(player.getUniqueId());
        Mount mount = mounts.remove(player.getUniqueId());
        if (mount != null) {
            VehicleInstance instance = instances.get(mount.vehicle());
            if (instance != null && instance.body.definition.seats().get(mount.seat()).driver()) instance.body.engineRunning = false;
            player.leaveVehicle();
        }
        hud.clear(player);
    }

    private boolean safeExit(Location location) {
        World world = location.getWorld();
        if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)
                || location.getY() < world.getMinHeight() || location.getY() + 2 >= world.getMaxHeight()) return false;
        return world.getBlockAt(location).isPassable() && world.getBlockAt(location.clone().add(0, 1, 0)).isPassable();
    }
    private UUID vehicleId(Entity entity) {
        String value = entity.getPersistentDataContainer().get(entityKey, PersistentDataType.STRING);
        if (value == null) return null;
        try { return UUID.fromString(value); } catch (IllegalArgumentException exception) { return null; }
    }
    private void cleanOrphan(Entity entity) {
        UUID id = vehicleId(entity);
        if (id == null) return;
        VehicleInstance instance = instances.get(id);
        if (instance == null || instance.entities == null || !instance.entities.contains(entity)) entity.remove();
    }
    private void persist() throws IOException { store.saveInstances(instances.values().stream().map(VehicleInstance::snapshot).toList()); }
    private long activeCount() { return instances.values().stream().filter(instance -> instance.entities != null).count(); }
    private boolean canAccess(Player player, VehicleInstance instance) { return instance.publicAccess || instance.owner.equals(player.getUniqueId()) || player.hasPermission("mik.vehicle.admin"); }
    private void requireAccess(Player player, VehicleInstance instance) { if (!canAccess(player, instance)) throw new IllegalArgumentException("Private vehicle"); }
    private void requireOwner(Player player, VehicleInstance instance) { if (!instance.owner.equals(player.getUniqueId())) requireAdmin(player); }
    private void requireAdmin(Player player) { if (!player.hasPermission("mik.vehicle.admin")) throw new IllegalArgumentException("mik.vehicle.admin required"); }
    private void requireAllowed(Player player, UUID id, Location location, VehicleUseEvent.Action action) {
        if (disabledWorlds.contains(location.getWorld().getName())) throw new IllegalArgumentException("Vehicles disabled in this world");
        VehicleUseEvent event = new VehicleUseEvent(player, id, location, action);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) throw new IllegalArgumentException("Vehicle action denied by protection policy");
    }
    void success(Player player, String detail) { player.sendMessage(language.text(player, Message.VEHICLE_RESULT, NamedTextColor.GREEN, detail)); }
    private static VehicleVector vector(Location location) { return new VehicleVector(location.getX(), location.getY(), location.getZ()); }
    private Location location(VehicleInstance instance) {
        World world = plugin.getServer().getWorld(instance.worldId);
        if (world == null) throw new IllegalArgumentException("World unavailable");
        return location(instance.body.origin(), world);
    }
    private static Location location(VehicleVector vector, World world) { return new Location(world, vector.coordinateX(), vector.coordinateY(), vector.coordinateZ()); }
    private static int boundedInteger(YamlConfiguration settings, String key, int minimum, int maximum) {
        if (!settings.isInt(key) || settings.getInt(key) < minimum || settings.getInt(key) > maximum)
            throw new IllegalArgumentException("Invalid setting: " + key);
        return settings.getInt(key);
    }
    private void validatePayloads(Iterable<VehicleDefinition> loaded) {
        for (VehicleDefinition definition : loaded) for (VehicleDefinition.Part part : definition.parts()) {
            switch (part.kind()) {
                case BLOCK -> plugin.getServer().createBlockData(part.payload());
                case ITEM -> ItemStack.deserializeBytes(Base64.getDecoder().decode(part.payload()));
                case TEXT -> GsonComponentSerializer.gson().deserialize(part.payload());
            }
        }
    }

    Map<String, VehicleDefinition> models() { return Map.copyOf(definitions); }

    Location editorOrigin(Player player) {
        VehicleSelection selection = selection(player);
        Location fallback = selection.root() == null ? player.getLocation() : selectedGroup(player, selection).origin();
        return captureOrigin(player, fallback);
    }

    private Location captureOrigin(Player player, Location fallback) {
        Location origin = origins.getOrDefault(player.getUniqueId(), fallback).clone();
        if (!origin.getWorld().equals(player.getWorld()) || origin.distanceSquared(player.getLocation()) > 1024)
            throw new IllegalArgumentException("Origin must be within 32 blocks in this world");
        return origin;
    }

    List<VehicleDefinition.Part> selectedParts(Player player) {
        CapturedSelection selected = captureSelection(player);
        return VehicleModel.captureParts(selected.origin(), selected.displays());
    }

    List<VehicleDefinition.Part> selectedParts(Player player, Location origin) {
        CapturedSelection selected = captureSelection(player, origin);
        return VehicleModel.captureParts(selected.origin(), selected.displays());
    }

    private CapturedSelection captureSelection(Player player) {
        return captureSelection(player, null);
    }

    private CapturedSelection captureSelection(Player player, Location editorOrigin) {
        requireAdmin(player);
        VehicleSelection selection = selection(player);
        if (selection.root() != null) {
            VehicleDisplayGroup group = selectedGroup(player, selection);
            Location origin = editorOrigin == null ? captureOrigin(player, group.origin()) : editorOrigin.clone();
            validateCapture(player, origin, group.displays());
            recheckGroup(player, group);
            return new CapturedSelection(origin, group.displays());
        }
        Location origin = editorOrigin == null ? captureOrigin(player, player.getLocation()) : editorOrigin.clone();
        List<Display> selected = new ArrayList<>();
        for (UUID id : selection.ids()) {
            Entity entity = plugin.getServer().getEntity(id);
            if (!(entity instanceof Display display) || vehicleId(display) != null || modelEditor.isPreview(display)
                    || !display.getWorld().equals(player.getWorld()) || display.getLocation().distanceSquared(player.getLocation()) > 1024)
                throw new IllegalArgumentException("Selected display unavailable; reselect it");
            selected.add(display);
        }
        validateCapture(player, origin, selected);
        return new CapturedSelection(origin, List.copyOf(selected));
    }

    private void validateCapture(Player player, Location origin, List<Display> displays) {
        if (!origin.getWorld().equals(player.getWorld()) || origin.distanceSquared(player.getLocation()) > 1024)
            throw new VehicleImportException(Message.VEHICLE_GROUP_INVALID);
        requireAllowed(player, null, origin, VehicleUseEvent.Action.CAPTURE);
        for (Display display : displays) requireAllowed(player, null, display.getLocation(), VehicleUseEvent.Action.CAPTURE);
    }

    VehicleDefinition saveModel(VehicleModelDraft draft) throws IOException {
        VehicleDefinition replacement = draft.validate();
        VehicleDefinition current = definitions.get(draft.id());
        if (current != draft.base()) throw new IllegalArgumentException("Model changed; reopen the editor");
        if (current == null && definitions.size() >= 256) throw new IllegalArgumentException("Model limit reached");
        validatePayloads(List.of(replacement));
        Map<UUID, VehicleInstance> proposed = VehicleModelChanges.replace(instances, replacement);
        for (VehicleInstance instance : proposed.values()) {
            if (instances.get(instance.id) == instance) continue;
            if (plugin.getServer().getWorld(instance.worldId) != null && world(instance).loaded(instance.body.origin())
                    && !world(instance, proposed.values()).clear(instance.body))
                throw new IllegalArgumentException("New collision shape intersects terrain or another vehicle: " + instance.id);
        }
        if (current == null) store.saveDefinition(replacement); else store.replaceDefinition(current, replacement);
        definitions.put(replacement.id(), replacement);
        for (VehicleInstance instance : List.copyOf(instances.values())) {
            if (proposed.get(instance.id) != instance) {
                try { deactivate(instance); }
                catch (RuntimeException exception) { plugin.getLogger().log(Level.SEVERE, "Vehicle editor cleanup failed", exception); }
                instances.put(instance.id, proposed.get(instance.id));
                failed.remove(instance.id);
            }
        }
        return replacement;
    }

    void deleteModel(VehicleDefinition expected) throws IOException {
        if (definitions.get(expected.id()) != expected) throw new IllegalArgumentException("Model changed; confirm again");
        VehicleModelChanges.requireUnused(instances.values(), expected.id());
        store.deleteDefinition(expected);
        definitions.remove(expected.id());
    }
}
