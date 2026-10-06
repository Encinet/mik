package org.encinet.mik.module.elevator;

record IronElevatorPanelLayout(double centerSideways, double centerAbove,
                               int columns, int rows, double horizontalGap, double verticalGap,
                               double halfWidth, double halfHeight, boolean portrait,
                               boolean paginated, boolean pageCounter, double footerWidth) {

    private static final double MARGIN = 0.12;
    private static final double FOOTER_GAP = 0.08;
    private static final double FRAME_PADDING = 0.04;
    private static final double MINIMUM_ABOVE = 0;
    private static final double MAXIMUM_ABOVE = 1.6;
    private static final double PREFERRED_ABOVE = 0.6;
    private static final double HORIZONTAL_GAP = 0.3;
    private static final double VERTICAL_GAP = 0.18;
    private static final double HALF_WIDTH = 0.135;
    private static final double HALF_HEIGHT = 0.08;
    private static final double ARROW_FOOTER_WIDTH = 0.64;
    private static final double COUNTER_FOOTER_WIDTH = 1.35;

    record Position(double sideways, double above) {
    }

    static IronElevatorPanelLayout fit(double minimumSideways, double maximumSideways,
                                       double minimumAbove, double maximumAbove, int levels) {
        return fit(minimumSideways, maximumSideways, minimumAbove, maximumAbove, levels,
                (minimumSideways + maximumSideways) / 2);
    }

    static IronElevatorPanelLayout fit(double minimumSideways, double maximumSideways,
                                       double minimumAbove, double maximumAbove, int levels,
                                       double preferredSideways) {
        if (levels < 1) throw new IllegalArgumentException("At least one floor is required");
        minimumAbove = Math.max(minimumAbove, MINIMUM_ABOVE);
        maximumAbove = Math.min(maximumAbove, MAXIMUM_ABOVE);
        double availableWidth = maximumSideways - minimumSideways - MARGIN * 2;
        double availableHeight = maximumAbove - minimumAbove - MARGIN * 2;
        int fittingColumns = fittingSlots(availableWidth, HALF_WIDTH, HORIZONTAL_GAP);
        int fittingRows = fittingSlots(availableHeight, HALF_HEIGHT, VERTICAL_GAP);
        if (fittingColumns < 1 || fittingRows < 1) return null;
        IronElevatorPanelLayout best = null;
        for (int columns = 1; columns <= Math.min(fittingColumns, levels); columns++) {
            int rows = (levels + columns - 1) / columns;
            if (rows > fittingRows) continue;
            var candidate = candidate(minimumSideways, maximumSideways, minimumAbove, maximumAbove,
                    preferredSideways, columns, rows, false, false, 0);
            if (better(candidate, best, levels)) best = candidate;
        }
        if (best != null) return best;
        if (availableWidth < ARROW_FOOTER_WIDTH) return null;
        fittingRows = fittingSlots(availableHeight - FOOTER_GAP - HALF_HEIGHT * 2, HALF_HEIGHT, VERTICAL_GAP);
        boolean pageCounter = availableWidth >= COUNTER_FOOTER_WIDTH;
        for (int columns = 1; columns <= fittingColumns; columns++) {
            for (int rows = 1; rows <= fittingRows; rows++) {
                double footerWidth = Math.max((columns - 1) * HORIZONTAL_GAP + HALF_WIDTH * 2,
                        pageCounter ? COUNTER_FOOTER_WIDTH : ARROW_FOOTER_WIDTH);
                var candidate = candidate(minimumSideways, maximumSideways, minimumAbove, maximumAbove,
                        preferredSideways, columns, rows, true, pageCounter, footerWidth);
                if (better(candidate, best, levels)) best = candidate;
            }
        }
        return best;
    }

    private static IronElevatorPanelLayout candidate(double minimumSideways, double maximumSideways,
                                                      double minimumAbove, double maximumAbove,
                                                      double preferredSideways, int columns, int rows,
                                                      boolean paginated, boolean pageCounter, double footerWidth) {
        double width = Math.max((columns - 1) * HORIZONTAL_GAP + HALF_WIDTH * 2, footerWidth);
        double height = (rows - 1) * VERTICAL_GAP + HALF_HEIGHT * 2
                + (paginated ? FOOTER_GAP + HALF_HEIGHT * 2 : 0);
        double centerSideways = Math.clamp(preferredSideways,
                minimumSideways + MARGIN + width / 2, maximumSideways - MARGIN - width / 2);
        double centerAbove = Math.clamp(PREFERRED_ABOVE,
                minimumAbove + MARGIN + height / 2, maximumAbove - MARGIN - height / 2);
        return new IronElevatorPanelLayout(centerSideways, centerAbove, columns, rows,
                HORIZONTAL_GAP, VERTICAL_GAP, HALF_WIDTH, HALF_HEIGHT,
                height > width, paginated, pageCounter, footerWidth);
    }

    private static boolean better(IronElevatorPanelLayout candidate, IronElevatorPanelLayout previous, int levels) {
        if (previous == null) return true;
        int candidatePages = (levels + candidate.pageSize() - 1) / candidate.pageSize();
        int previousPages = (levels + previous.pageSize() - 1) / previous.pageSize();
        if (candidatePages != previousPages) return candidatePages < previousPages;
        return score(candidate, levels) < score(previous, levels);
    }

    private static double score(IronElevatorPanelLayout layout, int levels) {
        double shape = Math.abs(Math.log(layout.width() / layout.height()));
        if (levels <= 3 && !layout.paginated() && layout.rows() == 1) shape -= 10;
        return shape + layout.width() * layout.height() * 0.1
                + Math.abs(layout.centerAbove() - PREFERRED_ABOVE) * 0.1;
    }

    private static int fittingSlots(double available, double halfSize, double gap) {
        return Math.max(0, (int) Math.floor((available - halfSize * 2) / gap + 1.0E-9) + 1);
    }

    int pageSize() {
        return columns * rows;
    }

    double width() {
        return Math.max((columns - 1) * horizontalGap + halfWidth * 2, footerWidth);
    }

    double height() {
        return (rows - 1) * verticalGap + halfHeight * 2
                + (paginated ? FOOTER_GAP + halfHeight * 2 : 0);
    }

    Position floor(int slot, int pageItems) {
        if (slot < 0 || slot >= pageItems || pageItems > pageSize())
            throw new IllegalArgumentException("Floor slot must belong to this page");
        int row = slot / columns;
        int rowColumns = Math.min(columns, pageItems - row * columns);
        return new Position(centerSideways + (slot % columns - (rowColumns - 1) / 2.0) * horizontalGap,
                centerAbove + height() / 2 - halfHeight - row * verticalGap);
    }

    Position navigation(int direction) {
        return new Position(centerSideways + direction * (footerWidth / 2 - halfWidth),
                centerAbove - height() / 2 + halfHeight);
    }

    boolean contains(double sideways, double above) {
        return Math.abs(sideways - centerSideways) <= width() / 2 + FRAME_PADDING
                && Math.abs(above - centerAbove) <= height() / 2 + FRAME_PADDING;
    }

    boolean fits(double minimumSideways, double maximumSideways, double minimumAbove, double maximumAbove) {
        return centerSideways - width() / 2 - FRAME_PADDING >= minimumSideways
                && centerSideways + width() / 2 + FRAME_PADDING <= maximumSideways
                && centerAbove - height() / 2 - FRAME_PADDING >= minimumAbove
                && centerAbove + height() / 2 + FRAME_PADDING <= maximumAbove;
    }
}
