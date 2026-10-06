package org.encinet.mik.module.plot.protection;

import org.encinet.mik.module.role.RolePermissions;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.plot.PlotRegistry;

import java.util.Locale;

/** Preflights vanilla commands that write blocks directly without Bukkit placement events. */
public final class PlotCommandGuard implements Listener {
    private final PlotRegistry registry;
    private final LanguageService language;

    public PlotCommandGuard(PlotRegistry registry, LanguageService language) {
        this.registry = registry;
        this.language = language;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void command(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (RolePermissions.canModerate(player)) return;
        String[] parts = event.getMessage().strip().substring(1).split("\\s+");
        if (parts.length == 0) return;
        String name = parts[0].toLowerCase(Locale.ROOT).replace("minecraft:", "");
        boolean denied = switch (name) {
            case "setblock" -> !canWrite(player, parts, 1, 1);
            case "fill", "fillbiome" -> !canWrite(player, parts, 1, 4);
            case "clone" -> !canClone(player, parts);
            case "execute", "function", "place" -> mutates(parts) && !registry.all().isEmpty();
            case "data" -> parts.length > 5 &&
                    (parts[1].equalsIgnoreCase("modify") || parts[1].equalsIgnoreCase("merge")
                            || parts[1].equalsIgnoreCase("remove"))
                    && parts[2].equalsIgnoreCase("block") && !canWrite(player, parts, 3, 3);
            case "item" -> parts.length > 5 && parts[1].equalsIgnoreCase("replace")
                    && parts[2].equalsIgnoreCase("block") && !canWrite(player, parts, 3, 3);
            default -> false;
        };
        if (denied) {
            event.setCancelled(true);
            player.sendMessage("§c" + language.t(player, Message.PLOT_COMMAND_BLOCKED));
        }
    }

    static boolean mutates(String[] parts) {
        String whole = String.join(" ", parts).toLowerCase(Locale.ROOT);
        return parts[0].equalsIgnoreCase("function") || parts[0].equalsIgnoreCase("place")
                || whole.matches(".*\\brun\\s+(minecraft:)?(fill|setblock|clone|fillbiome|data|item|function|place)\\b.*");
    }

    private boolean canClone(Player player, String[] parts) {
        if (parts.length < 10) return false;
        int[] first = point(player, parts, 1);
        int[] last = point(player, parts, 4);
        int[] destination = point(player, parts, 7);
        if (first == null || last == null || destination == null) return false;
        int[] end = {destination[0] + Math.abs(last[0] - first[0]),
                destination[1] + Math.abs(last[1] - first[1]),
                destination[2] + Math.abs(last[2] - first[2])};
        if (!area(player, destination, end)) return false;
        for (int i = 10; i < parts.length; i++) {
            if (parts[i].equalsIgnoreCase("move") && !area(player, first, last)) return false;
        }
        return true;
    }

    private boolean canWrite(Player player, String[] parts, int from, int to) {
        int[] first = point(player, parts, from);
        int[] last = point(player, parts, to);
        return first != null && last != null && area(player, first, last);
    }

    private boolean area(Player player, int[] first, int[] last) {
        int minX = Math.min(first[0], last[0]), maxX = Math.max(first[0], last[0]);
        int minY = Math.min(first[1], last[1]), maxY = Math.max(first[1], last[1]);
        int minZ = Math.min(first[2], last[2]), maxZ = Math.max(first[2], last[2]);
        long width = (long) maxX - minX + 1;
        long height = (long) maxY - minY + 1;
        long depth = (long) maxZ - minZ + 1;
        if (width > 262_144 || height > 262_144 || depth > 262_144
                || width * height * depth > 262_144) return false;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (!registry.allowed(player.getWorld().getUID(), x, y, z,
                            player.getUniqueId(), RolePermissions.isMember(player),
                            false, "build")) return false;
                }
            }
        }
        return true;
    }

    private int[] point(Player player, String[] parts, int index) {
        if (index + 2 >= parts.length) return null;
        try {
            return new int[] {coordinate(parts[index], player.getLocation().getX()),
                    coordinate(parts[index + 1], player.getLocation().getY()),
                    coordinate(parts[index + 2], player.getLocation().getZ())};
        } catch (NumberFormatException error) { return null; }
    }

    private int coordinate(String part, double origin) {
        if (part.startsWith("^")) throw new NumberFormatException("local coordinates");
        double value = part.startsWith("~")
                ? origin + (part.length() == 1 ? 0 : Double.parseDouble(part.substring(1)))
                : Double.parseDouble(part);
        if (!Double.isFinite(value) || Math.abs(value) > 30_000_000)
            throw new NumberFormatException("outside world coordinates");
        return (int) Math.floor(value);
    }
}
