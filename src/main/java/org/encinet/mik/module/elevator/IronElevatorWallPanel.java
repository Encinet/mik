package org.encinet.mik.module.elevator;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.encinet.mik.module.menu.runtime.WorldTextDisplayService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

final class IronElevatorWallPanel {

    private static final String PREFIX = "iron-elevator:";
    private static final int BACKGROUND = 0xD0202833;

    private final WorldTextDisplayService labels;
    private final IronElevatorFloors floors;
    private final BiConsumer<Player, Integer> selectFloor;
    private final Map<UUID, View> viewers = new HashMap<>();
    private final IronElevatorPanelGeometry geometry = new IronElevatorPanelGeometry();

    record Button(double sideways, double above, double halfWidth, double halfHeight,
                  Integer targetY, int pageDelta, boolean enabled) {
        boolean contains(IronElevatorWall.Hit hit) {
            return Math.abs(hit.sideways() - sideways) <= halfWidth
                    && Math.abs(hit.above() - above) <= halfHeight;
        }
    }

    private record View(IronElevatorPlatform source, IronElevatorWall wall, List<IronElevatorFloors.Floor> floors,
                        IronElevatorPanelLayout layout, int page, List<Button> buttons, List<String> keys) {
    }

    IronElevatorWallPanel(WorldTextDisplayService labels,
                          IronElevatorFloors floors, BiConsumer<Player, Integer> selectFloor) {
        this.labels = labels;
        this.floors = floors;
        this.selectFloor = selectFloor;
    }

    void update(Player player, IronElevatorPlatform source) {
        View previous = viewers.get(player.getUniqueId());
        List<IronElevatorFloors.Floor> levels = floors.scan(source.anchor(), player.getLocation());
        if (levels.size() < 3) {
            clear(player);
            return;
        }
        IronElevatorPanelGeometry.Plan plan = geometry.plan(source, levels.size());
        if (plan == null) {
            clear(player);
            return;
        }
        IronElevatorWall wall = plan.wall();
        IronElevatorPanelLayout layout = plan.layout();
        int currentIndex = 0;
        for (int index = 0; index < levels.size(); index++) {
            if (levels.get(index).block().getY() == source.height()) {
                currentIndex = index;
                break;
            }
        }
        int page = previous != null && previous.source().equals(source)
                ? previous.page() * previous.layout().pageSize() / layout.pageSize()
                : currentIndex / layout.pageSize();
        render(player, source, wall, levels, layout, page);
    }

    private void render(Player player, IronElevatorPlatform source, IronElevatorWall wall,
                        List<IronElevatorFloors.Floor> levels, IronElevatorPanelLayout layout, int requestedPage) {
        int pageSize = layout.pageSize();
        int pageCount = (levels.size() + pageSize - 1) / pageSize;
        int page = Math.clamp(requestedPage, 0, pageCount - 1);
        List<Button> buttons = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        int start = page * pageSize;
        int pageItems = Math.min(levels.size() - start, pageSize);
        for (int index = start; index < start + pageItems; index++) {
            IronElevatorFloors.Floor floor = levels.get(index);
            int slot = index - start;
            boolean selected = floor.block().getY() == source.height();
            boolean available = !selected && IronElevatorLanding.on(floor.platform(), player.getLocation(),
                    IronElevatorModule.size(player), player::wouldCollideUsing) != null;
            Button button = floorButton(layout, slot, pageItems, floor.block().getY(), available);
            show(player, wall, keys, "floor:" + slot, button.sideways(), button.above(),
                    floorLabel(floor.number(), selected, available));
            buttons.add(button);
        }
        if (pageCount > 1) {
            if (layout.pageCounter()) {
                show(player, wall, keys, "page", layout.centerSideways(), layout.navigation(0).above(),
                        Component.text((page + 1) + " / " + pageCount, NamedTextColor.GRAY));
            }
            navigation(player, wall, layout, keys, buttons, "previous", "◀", -1, page > 0);
            navigation(player, wall, layout, keys, buttons, "next", "▶", 1, page + 1 < pageCount);
        }
        View previous = viewers.put(player.getUniqueId(),
                new View(source, wall, levels, layout, page, List.copyOf(buttons), List.copyOf(keys)));
        if (previous != null) {
            previous.keys().stream().filter(key -> !keys.contains(key)).forEach(key -> labels.clear(player, key));
        }
    }

    static Button floorButton(IronElevatorPanelLayout layout, int slot, int pageItems, int height, boolean available) {
        IronElevatorPanelLayout.Position position = layout.floor(slot, pageItems);
        return new Button(position.sideways(), position.above(), layout.halfWidth(), layout.halfHeight(),
                height, 0, available);
    }

    static Component floorLabel(int number, boolean selected, boolean available) {
        NamedTextColor color = selected ? NamedTextColor.GREEN
                : available ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY;
        Component label = Component.text(Integer.toString(number), color);
        return selected ? label.decorate(TextDecoration.UNDERLINED) : label;
    }

    private void navigation(Player player, IronElevatorWall wall, IronElevatorPanelLayout layout, List<String> keys,
                            List<Button> buttons, String key,
                            String text, int delta, boolean enabled) {
        IronElevatorPanelLayout.Position position = layout.navigation(delta);
        show(player, wall, keys, key, position.sideways(), position.above(),
                Component.text(text, enabled ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY));
        buttons.add(new Button(position.sideways(), position.above(), layout.halfWidth(), layout.halfHeight(),
                null, delta, enabled));
    }

    private void show(Player player, IronElevatorWall wall, List<String> keys, String key,
                      double sideways, double above, Component text) {
        String id = PREFIX + key;
        keys.add(id);
        labels.show(player, id, wall.position(sideways, above - 0.045), wall.yaw(), text, BACKGROUND);
    }

    boolean click(Player player, boolean activate) {
        View view = viewers.get(player.getUniqueId());
        if (view == null) return false;
        IronElevatorWall.Hit hit = hitAt(player, view.source(), view.wall(), view.layout());
        if (hit == null) return false;
        Button button = buttonAt(hit, view.buttons());
        if (button != null && activate && button.enabled()) {
            if (button.targetY() != null) {
                selectFloor.accept(player, button.targetY());
            } else {
                render(player, view.source(), view.wall(), view.floors(), view.layout(), view.page() + button.pageDelta());
            }
        }
        return true;
    }

    static IronElevatorWall.Hit hitAt(Player player, IronElevatorPlatform source, IronElevatorWall wall,
                                    IronElevatorPanelLayout layout) {
        Block standing = IronElevatorLanding.platformAt(player.getLocation());
        if (standing == null || !source.contains(standing)) {
            return null;
        }
        Location eye = player.getEyeLocation();
        IronElevatorWall.Hit hit = wall.hit(eye);
        if (hit == null) {
            return null;
        }
        if (!layout.contains(hit.sideways(), hit.above())) return null;
        IronElevatorWall.Surface surface = wall.surface();
        if (surface == null || !surface.contains(layout)) return null;
        RayTraceResult obstruction = player.getWorld().rayTraceBlocks(eye, eye.getDirection(),
                Math.max(0, hit.distance() - 0.01), FluidCollisionMode.NEVER, true);
        if (obstruction != null) {
            return null;
        }
        return hit;
    }

    static Button buttonAt(IronElevatorWall.Hit hit, List<Button> buttons) {
        for (Button button : buttons) {
            if (!button.contains(hit)) {
                continue;
            }
            return button;
        }
        return null;
    }

    void clear(Player player) {
        View view = viewers.remove(player.getUniqueId());
        if (view != null) {
            view.keys().forEach(key -> labels.clear(player, key));
        }
    }

    void forget(UUID playerId) {
        viewers.remove(playerId);
    }

    void invalidate(Block changed) {
        geometry.invalidate(changed);
    }

    void invalidateChunk(UUID worldId, int chunkX, int chunkZ) {
        geometry.invalidateChunk(worldId, chunkX, chunkZ);
    }

    void clearGeometry() {
        geometry.clear();
    }
}
