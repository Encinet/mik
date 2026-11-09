package org.encinet.mik.shared.menu.ui;

/** Semantic purpose, independent of text, item, block and image presentation. */
public enum UiRole {
    TEXT, HEADING, ACTION, NAVIGATION, CHOICE, STATUS;
    public boolean supportsActions() { return this == ACTION || this == NAVIGATION || this == CHOICE; }
}
