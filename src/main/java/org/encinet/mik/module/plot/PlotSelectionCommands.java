package org.encinet.mik.module.plot;

import org.encinet.mik.module.i18n.Message;

final class PlotSelectionCommands {
    private PlotSelectionCommands() { }

    record Mark(boolean first, PlotPosition point) { }

    static boolean matches(String input) {
        String command = input.strip().split("\\s+", 2)[0];
        return command.equalsIgnoreCase("//pos1") || command.equalsIgnoreCase("//pos2");
    }

    static Mark parse(String input, PlotPosition current) {
        String[] arguments = input.strip().split("\\s+");
        if (!matches(input) || arguments.length > 2)
            throw new PlotProblem(Message.PLOT_ERROR_ARGS);
        boolean first = arguments[0].equalsIgnoreCase("//pos1");
        if (arguments.length == 1) return new Mark(first, current);
        String[] coordinates = arguments[1].split(",", -1);
        if (coordinates.length != 3) throw new PlotProblem(Message.PLOT_ERROR_ARGS);
        try {
            return new Mark(first, new PlotPosition(current.world(),
                    Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1]),
                    Integer.parseInt(coordinates[2])));
        } catch (NumberFormatException error) {
            throw new PlotProblem(Message.PLOT_ERROR_ARGS);
        }
    }

    static String status(PlotSelectionState.Points points) {
        String status = "//pos1" + (points.first() == null ? "" : " ✓")
                + " · //pos2" + (points.second() == null ? "" : " ✓");
        if (points.first() != null && points.second() != null
                && points.first().world().equals(points.second().world())) {
            PlotSelection.Bounds bounds = PlotSelectionShape.of(points.first(), points.second()).alignedBounds();
            status += " · " + bounds.width() + "×" + bounds.height() + "×" + bounds.depth();
        }
        return status;
    }
}
