package org.encinet.mik.module.plot;

import java.nio.file.Path;
import java.util.Objects;

/** All plot-owned persistence lives beneath one plugin data directory. */
public record PlotDataPaths(Path directory) {
    public PlotDataPaths {
        Objects.requireNonNull(directory, "directory");
    }

    public static PlotDataPaths in(Path pluginDirectory) {
        return new PlotDataPaths(Objects.requireNonNull(pluginDirectory, "pluginDirectory").resolve("plots"));
    }

    public Path plotsDatabase() { return directory.resolve("plots.db"); }

    public Path boardDatabase() { return directory.resolve("board.db"); }

    public Path boardAlerts() { return directory.resolve("board-alerts.yml"); }
}
