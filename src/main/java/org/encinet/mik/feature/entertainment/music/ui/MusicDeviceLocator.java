package org.encinet.mik.feature.entertainment.music.ui;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.entertainment.music.application.MusicDeviceRegistry;
import org.encinet.mik.feature.entertainment.music.application.MusicProblem;
import org.encinet.mik.feature.entertainment.music.jukebox.MusicDevice;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.UiText;

/** Personal wayfinding for the current control target. The workspace owns its pulse and lifetime. */
final class MusicDeviceLocator {
    static final int MAX_MARKERS = 4;
    static final double RANGE = 32;
    private static final int[] COLORS = {0x70D7FF,0xD4A4FF,0xFFB4DE,0x8FE8D0,0xADBFFF,0xFFE4A3,0xB9EAFF,0xD9EFA0};
    record Marker(MusicDevice device, String code, int color, boolean focused) { }
    record Point(double x, double y, double z) { }
    private final MusicDeviceRegistry registry;
    private final LanguageService language;

    MusicDeviceLocator(MusicDeviceRegistry registry, LanguageService language) { this.registry = registry; this.language = language; }

    static Marker marker(MusicDevice device, boolean focused) {
        String identity = device.id().toString();
        String code = identity.substring(identity.length()-4).toUpperCase(Locale.ROOT);
        return new Marker(device,code,COLORS[Math.floorMod(device.id().hashCode(),COLORS.length)],focused);
    }

    int color(UUID id) {
        var target = registry.target(id);
        return target.members().size() == 1 ? marker(target.members().getFirst(),false).color()
                : COLORS[Math.floorMod(id.hashCode(),COLORS.length)];
    }

    Component name(Player player, MusicDevice device) {
        var marker = marker(device,false);
        String name = registry.alias(player,device);
        return Component.text((device.group() == null ? "" : "#"+marker.code()+" ")+name,TextColor.color(marker.color()));
    }

    private MusicDevice nearest(Player player, UUID target) {
        var viewer = player.getLocation();
        return registry.target(target).members().stream().min(Comparator
                .comparing((MusicDevice device) -> !device.world().equals(player.getWorld().getUID()))
                .thenComparingDouble(device -> distanceSquared(viewer,device))).orElseThrow(() -> new MusicProblem(MusicProblem.Code.NOT_FOUND));
    }

    Component position(Player player, UUID target) {
        var device = nearest(player,target);
        var hint = MusicDeviceLocation.from(player.getLocation(),device);
        if (!hint.sameWorld()) return Component.text(hint.world(),NamedTextColor.AQUA);
        var value = language.text(player,Message.MUSIC_SPACE_DEVICE_DISTANCE,TextColor.color(marker(device,false).color()),
                language.t(player,Message.MUSIC_SPACE_LABEL,hint.direction().key()),hint.distance());
        if (Math.abs(hint.height()) >= 3 && hint.direction() != MusicDeviceLocation.Direction.ABOVE && hint.direction() != MusicDeviceLocation.Direction.BELOW)
            value = value.append(Component.text(" · ")).append(language.text(player,Message.MUSIC_SPACE_DEVICE_HEIGHT,NamedTextColor.AQUA,
                    hint.height() > 0 ? "above" : "below",Math.abs(hint.height())));
        return value;
    }

    Component coordinates(Player player, UUID target) {
        var device = nearest(player,target);
        return language.text(player,Message.MUSIC_SPACE_DEVICE_COORDINATES,NamedTextColor.AQUA,
                device.worldName(),device.x(),device.y(),device.z());
    }

    List<Marker> visible(Player player, MusicWorkspaceState state) {
        var candidates = new LinkedHashMap<UUID,MusicDevice>();
        if (state.panel() instanceof MusicWorkspaceState.GroupEditor editor)
            for (UUID id : editor.draft().members) {
                var device = registry.snapshot().devices().get(id);
                if (device != null) candidates.put(id,device);
            }
        else add(candidates,state.target());
        var viewer = player.getLocation();
        var focus = state.panel() instanceof MusicWorkspaceState.MemberOutput member ? member.member() : state.target();
        return candidates.values().stream().filter(device -> device.world().equals(player.getWorld().getUID())
                        && distanceSquared(viewer,device) <= RANGE*RANGE)
                .map(device -> marker(device,device.id().equals(focus)))
                .sorted(Comparator.comparing(Marker::focused).reversed().thenComparingDouble(marker -> distanceSquared(viewer,marker.device())))
                .filter(marker -> registry.online(marker.device()) && registry.mayControl(player,marker.device().id()))
                .limit(MAX_MARKERS).toList();
    }

    private void add(Map<UUID,MusicDevice> candidates, UUID target) {
        if (target == null) return;
        try { registry.target(target).members().forEach(device -> candidates.putIfAbsent(device.id(),device)); }
        catch (MusicProblem removed) { }
    }

    void decorate(Player player, MusicWorkspaceState state, MenuScene.Builder scene) {
        var eye = player.getEyeLocation();
        for (var marker : visible(player,state)) {
            var device = marker.device();
            var at = registry.location(device).add(.5,1.7,.5);
            float yaw = (float)Math.toDegrees(Math.atan2(at.getX()-eye.getX(),eye.getZ()-at.getZ()));
            var label = UiText.abbreviate(name(player,device),40,1).append(Component.newline()).append(Component.text("↓",NamedTextColor.WHITE));
            scene.worldTextDecoration("mark:"+device.id(),at,yaw,0,label,0xD0181B22,3,1.4f,.6f,FloatingMenuDecoration.Alignment.CENTER);
        }
    }

    void pulse(Player player, MusicWorkspaceState state) {
        boolean reduced = FloatingMenus.preferences(player).reducedMotion();
        var worldVisible = FloatingMenus.worldOverlayVisibility(player);
        for (var marker : visible(player,state)) {
            var device = marker.device();
            var dust = new Particle.DustOptions(Color.fromRGB(marker.color()),marker.focused() ? .9f : .65f);
            for (var point : outline(marker.focused() && !reduced)) {
                var at = new Location(player.getWorld(),device.x()+point.x(),device.y()+point.y(),device.z()+point.z());
                if (worldVisible.test(at)) player.spawnParticle(Particle.DUST,at,1,0,0,0,0,dust);
            }
        }
    }

    /** Sparse corners identify every member; a focused device gains edge midpoints and a short beacon. */
    static List<Point> outline(boolean focused) {
        var points = new ArrayList<Point>();
        for (double x : new double[]{-.04,1.04}) for (double y : new double[]{-.04,1.04})
            for (double z : new double[]{-.04,1.04}) points.add(new Point(x,y,z));
        if (focused) {
            for (double a : new double[]{-.04,1.04}) for (double b : new double[]{-.04,1.04}) {
                points.add(new Point(.5,a,b)); points.add(new Point(a,.5,b)); points.add(new Point(a,b,.5));
            }
            for (double y : new double[]{1.35,1.65,1.95}) points.add(new Point(.5,y,.5));
        }
        return List.copyOf(points);
    }

    private static double distanceSquared(Location viewer, MusicDevice device) {
        double x = viewer.getX()-device.x()-.5, y = viewer.getY()-device.y()-.5, z = viewer.getZ()-device.z()-.5;
        return x*x+y*y+z*z;
    }
}
