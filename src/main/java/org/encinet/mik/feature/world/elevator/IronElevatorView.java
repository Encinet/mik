package org.encinet.mik.feature.world.elevator;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.FloatingMenuPage;
import org.encinet.mik.shared.menu.FloatingMenuAnimation;
import org.encinet.mik.shared.menu.FloatingMenuAppearance;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.WorldMenuScope;
import org.encinet.mik.shared.menu.ui.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Reads ready domain snapshots; layout and pagination never query blocks or entities. */
final class IronElevatorView implements IronElevatorController.Presentation {
    interface Actions {
        void select(Player player, Object visit, int height);
        void visible(Player player, Object visit, IronElevatorCatalog.Result catalog, FloatingMenuPage page);
        void pause(Player player, Object visit);
    }
    private static final UiKey PANEL = UiKey.named("floors");
    private record Rendered(IronElevatorController.PanelSnapshot data, int anchorY) { }
    private final WorldMenuScope panels;
    private final LanguageService language;
    private final Actions actions;
    private final Map<UUID,Rendered> viewers = new HashMap<>();
    private final Set<UUID> hints = new HashSet<>();

    IronElevatorView(WorldMenuScope panels, LanguageService language, Actions actions) {
        this.panels = panels; this.language = language; this.actions = actions;
    }

    @Override public void panel(Player player, IronElevatorController.PanelSnapshot snapshot) {
        var previous = viewers.get(player.getUniqueId());
        int anchor = previous != null && previous.data().source().equals(snapshot.source())
                ? previous.anchorY() : snapshot.source().height();
        render(player,new Rendered(snapshot,anchor));
    }

    private void render(Player player, Rendered view) {
        var data = view.data();
        var floors = data.catalog().floors();
        int anchor = 0;
        for (int index = 0; index < floors.size(); index++) if (floors.get(index).level().height() == view.anchorY()) { anchor = index; break; }
        var items = Ui.windowedList(floors.size(),Ui.button(Component.text("-9999…")).singleLine().build(),index -> {
            var floor = floors.get(index);
            int height = floor.level().height();
            boolean selected = height == data.source().height();
            Boolean available = data.available().get(height);
            var button = Ui.button(floorLabel(floor.number(),selected,available)).key("floor:" + height).singleLine().selected(selected);
            if (!selected && Boolean.TRUE.equals(available)) {
                button.primary((viewer,_) -> actions.select(viewer,data.visit(),height));
                button.secondary((viewer,_) -> actions.select(viewer,data.visit(),height));
            }
            return button.build();
        }).key("levels").gap(UiLength.ui(4)).containing(anchor)
                .observeWindow(data.catalog(),page -> actions.visible(player,data.visit(),data.catalog(),page))
                .pager(page -> UiTemplates.pager(page,next -> {
                    if (viewers.get(player.getUniqueId()) != view) return;
                    int first = Math.clamp(next * page.pageSize(),0,floors.size() - 1);
                    render(player,new Rendered(data,floors.get(first).level().height()));
                })).build();
        var scene = MenuScene.screen("iron-elevator").content(Ui.windowedGrid(items,16,UiLength.ui(58),UiLength.ui(8)))
                .appearance(FloatingMenuAppearance.NO_BACKGROUNDS).animation(FloatingMenuAnimation.PLANAR)
                .lifecycle((viewer,_,_,state,_) -> {
                    if (state == org.encinet.mik.shared.menu.FloatingMenuState.SUSPENDED
                            || state == org.encinet.mik.shared.menu.FloatingMenuState.CLOSED) actions.pause(viewer,data.visit());
                }).build();
        panels.show(player,PANEL,data.placement().surface(),scene,viewer -> viewers.get(viewer.getUniqueId()) == view && data.valid().test(viewer));
        viewers.put(player.getUniqueId(),view);
    }

    static Component floorLabel(int number, boolean selected, Boolean available) {
        var label = Component.text(Integer.toString(number),selected ? NamedTextColor.GREEN
                : Boolean.TRUE.equals(available) ? NamedTextColor.WHITE : NamedTextColor.YELLOW);
        if (selected) return label.decorate(TextDecoration.UNDERLINED);
        return available == null ? label.append(Component.text("…")) : available ? label : label.decorate(TextDecoration.STRIKETHROUGH);
    }

    @Override public void hint(Player player, boolean up, boolean down) {
        player.sendActionBar(language.text(player,Message.IRON_ELEVATOR_UP,up ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                .append(Component.text("   ")).append(language.text(player,Message.IRON_ELEVATOR_DOWN,down ? NamedTextColor.GREEN : NamedTextColor.GRAY)));
        hints.add(player.getUniqueId());
    }
    @Override public void clearHint(Player player) { if (hints.remove(player.getUniqueId())) player.sendActionBar(Component.empty()); }
    @Override public void clearPanel(Player player) { viewers.remove(player.getUniqueId()); panels.clear(player,PANEL); }
    @Override public void clear(Player player) { clearPanel(player); clearHint(player); }
    @Override public void forget(UUID player) { viewers.remove(player); panels.clear(player); hints.remove(player); }
    @Override public void travelled(Player player, Location departure) { particles(departure); particles(player.getLocation()); }
    private static void particles(Location location) { location.getWorld().spawnParticle(Particle.PORTAL,location.clone().add(0,.9,0),40,.35,.65,.35,.1); }
}
