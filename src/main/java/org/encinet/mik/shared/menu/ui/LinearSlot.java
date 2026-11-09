package org.encinet.mik.shared.menu.ui;

/** Main-axis allocation, cross-axis placement and sizing are independent layout decisions. */
public record LinearSlot(double weight, Alignment alignment, CrossSizing crossSizing) {
    public enum Alignment { START, CENTER, END }
    public enum CrossSizing { CONTENT, FIT, STRETCH }
    public static final LinearSlot CONTENT = new LinearSlot(0, Alignment.CENTER);

    public LinearSlot(double weight, Alignment alignment) { this(weight, alignment, CrossSizing.CONTENT); }

    public LinearSlot {
        if (!Double.isFinite(weight) || weight < 0 || alignment == null || crossSizing == null) {
            throw new IllegalArgumentException("Invalid linear slot");
        }
    }

    public static LinearSlot grow(double weight) { return new LinearSlot(weight, Alignment.CENTER, CrossSizing.STRETCH); }

    /** Contribute natural size, then fill the cross axis up to the child's maximum. */
    public static LinearSlot stretch() { return new LinearSlot(0, Alignment.CENTER, CrossSizing.STRETCH); }

    /** Fit inside the cross-axis space established by the parent and intrinsic siblings. */
    public static LinearSlot fit() { return fit(Alignment.CENTER); }
    public static LinearSlot fit(Alignment alignment) { return new LinearSlot(0, alignment, CrossSizing.FIT); }
}
