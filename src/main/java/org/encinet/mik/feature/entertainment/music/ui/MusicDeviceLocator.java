package org.encinet.mik.feature.entertainment.music.ui;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.entertainment.music.application.MusicDeviceRegistry;
import org.encinet.mik.feature.entertainment.music.application.MusicProblem;
import org.encinet.mik.feature.entertainment.music.jukebox.MusicDevice;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.UiTextMetrics;
import net.kyori.adventure.text.format.TextDecoration;

/** Private, stable wayfinding rendered and cleaned up with the owning menu scene. */
final class MusicDeviceLocator {
    static final int MAX_MARKERS = 4;
    static final double RANGE = 32;
    private static final int[] COLORS = {0x70D7FF,0xD4A4FF,0xFFB4DE,0x8FE8D0,0xADBFFF,0xFFE4A3,0xB9EAFF,0xD9EFA0};
    record Marker(MusicDevice device, String code, int color, boolean focused) { }
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
        else if (state.panel() instanceof MusicWorkspaceState.DevicePanel panel) add(candidates,panel.target());
        else if (state.panel() instanceof MusicWorkspaceState.MemberOutput member) add(candidates,member.group());
        else add(candidates,state.target());
        var viewer = player.getLocation();
        var focus = state.panel() instanceof MusicWorkspaceState.MemberOutput member ? member.member()
                : state.panel() instanceof MusicWorkspaceState.DevicePanel panel ? panel.target() : state.target();
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
        for (var marker : visible(player,state)) {
            var device = marker.device();
            var at = registry.location(device).add(.5,1.25,.5);
            var label = UiTextMetrics.fitSingleLine(name(player,device).decorate(TextDecoration.BOLD),192);
            scene.decoration(FloatingMenuDecoration.worldText("mark:"+device.id(),at,0,0,label,0xE8181B22,3,.5f,
                    marker.focused() ? .8f : .7f,FloatingMenuDecoration.Alignment.CENTER).billboard());
            // Keep the copied block just inside the real one: its ordinary texture is hidden,
            // while the client's glowing pass draws the silhouette through walls.
            scene.decoration(FloatingMenuDecoration.worldOutline("outline:"+device.id(),registry.location(device).add(.5,.5,.5),
                    Material.JUKEBOX,.998f,marker.color()));
        }
    }

    private static double distanceSquared(Location viewer, MusicDevice device) {
        double x = viewer.getX()-device.x()-.5, y = viewer.getY()-device.y()-.5, z = viewer.getZ()-device.z()-.5;
        return x*x+y*y+z*z;
    }
}
