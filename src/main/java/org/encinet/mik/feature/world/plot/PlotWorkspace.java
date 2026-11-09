package org.encinet.mik.feature.world.plot;

import java.util.List;
import java.util.function.Predicate;
import org.encinet.mik.shared.i18n.Message;

/** The plot hub offers tasks according to their actual capability, without intermediate section tabs. */
final class PlotWorkspace {
    private PlotWorkspace() { }

    enum Task {
        DESTINATIONS(Message.PLOT_DESTINATIONS, Message.PLOT_WORKSPACE_DESTINATIONS_HINT),
        AREA(Message.PLOT_WORKSPACE_AREA, Message.PLOT_WORKSPACE_AREA_HINT),
        SUBPLOTS(Message.PLOT_WORKSPACE_SUBPLOTS, Message.PLOT_WORKSPACE_SUBPLOTS_HINT),
        ACCESS(Message.PLOT_MENU_ACCESS, Message.PLOT_WORKSPACE_ACCESS_HINT),
        SETTINGS(Message.PLOT_MENU_SETTINGS, Message.PLOT_WORKSPACE_SETTINGS_HINT);
        final Message label;
        final Message hint;
        Task(Message label, Message hint) { this.label = label; this.hint = hint; }
    }

    static List<Task> tasks(Plot plot, Predicate<PlotPermission> allowed, boolean visibleDestinations, boolean children) {
        return java.util.Arrays.stream(Task.values()).filter(task -> switch (task) {
            case DESTINATIONS -> visibleDestinations || allowed.test(PlotPermission.MANAGE_SETTINGS);
            case AREA -> allowed.test(PlotPermission.MANAGE_AREA);
            case SUBPLOTS -> !plot.subPlot() && (children || allowed.test(PlotPermission.CREATE_SUBPLOT));
            case ACCESS -> allowed.test(PlotPermission.MANAGE_MEMBERS) || allowed.test(PlotPermission.MANAGE_PERMISSIONS);
            case SETTINGS -> allowed.test(PlotPermission.MANAGE_SETTINGS) || allowed.test(PlotPermission.TRANSFER)
                    || allowed.test(PlotPermission.DELETE);
        }).toList();
    }
}
