package org.encinet.mik.feature.world.plot;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.encinet.mik.shared.i18n.Message;

/** Navigation state survives detail visits, live refreshes and paging without becoming plot data. */
record PlotDirectory(Filter filter, String query, int page) {
    enum Filter {
        OWNED(Message.PLOT_DIRECTORY_OWNED), PARTICIPATING(Message.PLOT_DIRECTORY_PARTICIPATING);
        final Message label;
        Filter(Message label) { this.label = label; }
        boolean includes(Plot plot, UUID player) {
            return this == OWNED ? plot.owner().equals(player)
                    : !plot.owner().equals(player) && plot.members().containsKey(player);
        }
    }
    PlotDirectory { query = query.strip(); }
    static PlotDirectory initial() { return new PlotDirectory(Filter.OWNED, "", 0); }
    PlotDirectory filter(Filter value) { return new PlotDirectory(value, query, 0); }
    PlotDirectory query(String value) { return new PlotDirectory(filter, value, 0); }
    PlotDirectory page(int value) { return new PlotDirectory(filter, query, value); }
    List<Plot> entries(List<Plot> plots, UUID player) {
        String search = query.toLowerCase(Locale.ROOT);
        return plots.stream().filter(plot -> filter.includes(plot, player))
                .filter(plot -> plot.name().toLowerCase(Locale.ROOT).contains(search)
                        || plot.id().toString().startsWith(search.replaceFirst("^#", "")))
                .sorted(Comparator.comparing(Plot::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Plot::id)).toList();
    }
}
