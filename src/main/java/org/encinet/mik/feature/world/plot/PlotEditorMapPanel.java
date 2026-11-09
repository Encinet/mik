package org.encinet.mik.feature.world.plot;

import java.util.function.BiFunction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.FloatingMenuCommand;
import org.encinet.mik.shared.menu.ui.*;

/** Configures a genuine handheld Minecraft map instead of painting a substitute tile grid. */
final class PlotEditorMapPanel {
    private PlotEditorMapPanel() { }

    static UiNode create(PlotEditorMap.Scene scene, BiFunction<Message, Object[], Component> text,
                         FloatingMenuCommand open) {
        return UiTemplates.window("map", UiTemplates.focus(Ui.column().gap(UiLength.ui(8))
                .child(Ui.text(text.apply(scene.regions().open() && !scene.regions().editing()
                        ? Message.PLOT_MAP_READONLY_HINT : Message.PLOT_MAP_HANDHELD_HINT, new Object[0])).key("map-help").summaryLines(1).textWidth(200).build())
                .child(Ui.row().key("map-context").gap(UiLength.ui(12))
                        .child(Ui.text(Component.text("N −Z ↑     E +X →", NamedTextColor.AQUA)).key("compass").singleLine().build())
                        .child(Ui.text(text.apply(Message.PLOT_MAP_SCALE,new Object[]{scene.viewport().step()})).key("scale").singleLine().build()).build())
                .child(Ui.button(text.apply(Message.PLOT_MAP_OPEN, new Object[0])).key("open-native-map")
                        .tooltip(text.apply(scene.regions().editing() ? Message.PLOT_REGION_EDIT_HINT : Message.PLOT_AREA_HINT, new Object[0]))
                        .primary(open).build())
                .build(),UiKey.named("open-native-map")));
    }
}
