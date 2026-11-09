package org.encinet.mik.shared.menu.ui;

public sealed interface UiNode permits UiElement, UiBranch, UiPort, UiPagedPort, UiPagedLayoutPort, UiDocumentPort, UiPagedFlow, UiPagedLayout, UiPagedList, UiWindowedList, UiWindowedGrid, UiExcerpt, UiAlternative, UiDocument, UiPagedTable, UiWindowedTable {
    UiKey key();
    UiStyle style();
    UiSizing sizing();
}
