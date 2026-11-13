package org.encinet.mik.shared.menu.ui;

/** One content tree keeps its identities while its host changes placement and surface orientation together. */
final class UiSplitLayout implements UiLayout {
    private static final UiKey SIDEBAR = UiKey.named("sidebar");
    private final UiLayout horizontal = UiLayouts.row(UiLength.ui(16));
    private final UiLayout vertical = UiLayouts.contentColumn(UiKey.named("main"),UiLength.ui(12));
    private final UiLayouts.Axis preferred;

    UiSplitLayout(UiLayouts.Axis preferred) { this.preferred = java.util.Objects.requireNonNull(preferred); }

    @Override public Result layout(MeasureScope scope, UiConstraints constraints) {
        try { return arrange(preferred,scope,constraints); }
        catch (UiOverflowException unavailable) {
            try { return arrange(preferred == UiLayouts.Axis.HORIZONTAL ? UiLayouts.Axis.VERTICAL : UiLayouts.Axis.HORIZONTAL,scope,constraints); }
            catch (UiOverflowException fallback) {
                fallback.addSuppressed(unavailable);
                throw fallback;
            }
        }
    }

    private Result arrange(UiLayouts.Axis axis, MeasureScope scope, UiConstraints constraints) {
        return axis == UiLayouts.Axis.HORIZONTAL
                ? horizontal.layout(scope,constraints).facing(SIDEBAR,UiSurfaceFacing.HORIZONTAL)
                : vertical.layout(scope,constraints).facing(SIDEBAR,UiSurfaceFacing.VERTICAL);
    }
}
