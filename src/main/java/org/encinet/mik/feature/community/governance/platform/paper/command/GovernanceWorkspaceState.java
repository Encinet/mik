package org.encinet.mik.feature.community.governance.platform.paper.command;

import java.util.*;
import org.encinet.mik.shared.menu.ui.UiKey;
import org.encinet.mik.shared.menu.ui.UiReadingPosition;

/** One main-thread workspace; browsing and explicit companion state have separate lifetimes. */
final class GovernanceWorkspaceState {
    enum Section { TEAM, MATTERS }
    enum Filter { ONGOING, COMPLETED }
    enum Intent { DETAILS, APPLICATION, WITHDRAW, SPONSOR, RESIGN }
    record Popup(UiKey source, UUID subject, Intent intent, boolean returnToDetails) {
        Popup confirm(Intent next) { return new Popup(source, subject, next, intent == Intent.DETAILS || returnToDetails); }
        Popup details() { return new Popup(source, subject, Intent.DETAILS, false); }
    }

    Section section = Section.TEAM;
    Filter filter = Filter.ONGOING;
    String query = "";
    int staffPage, matterAnchor;
    UUID subjectFilter;
    String subjectName, selectedMatter;
    Popup popup;
    long detailRevision;
    private String readingMatter;
    private UiReadingPosition detailPosition = UiReadingPosition.START;
    private Popup readingPopup;
    private UiReadingPosition popupPosition = UiReadingPosition.START;
    boolean busy;

    void section(Section value) { section = value; popup = null; resetMatterReading(); }
    void search(String value) { query = value.strip(); staffPage = 0; popup = null; resetMatterReading(); }
    void filter(Filter value) { filter = value; matterAnchor = 0; selectedMatter = null; popup = null; resetMatterReading(); }
    void related(UUID subject, String name) {
        section = Section.MATTERS; filter = Filter.COMPLETED;
        subjectFilter = subject; subjectName = name; matterAnchor = 0; selectedMatter = null; popup = null; resetMatterReading();
    }
    void clearSubject() { subjectFilter = null; subjectName = null; matterAnchor = 0; selectedMatter = null; popup = null; resetMatterReading(); }
    void cancelConfirmation() { popup = popup != null && popup.returnToDetails() ? popup.details() : null; }

    UiReadingPosition popupPosition() { return popup == readingPopup ? popupPosition : UiReadingPosition.START; }
    boolean readPopup(long revision, Popup source, UiReadingPosition position) {
        if (source == null || source != popup || revision != detailRevision) return false;
        readingPopup = source; popupPosition = Objects.requireNonNull(position); return true;
    }

    void resetMatterReading() { detailRevision++; readingMatter = null; detailPosition = UiReadingPosition.START; }
    UiReadingPosition matterPosition(String id) { return Objects.equals(readingMatter,id) ? detailPosition : UiReadingPosition.START; }
    boolean readMatter(long revision, String id, UiReadingPosition position, GovernanceWorkspaceData data) {
        var selected = selectedMatter(data);
        if (section != Section.MATTERS || revision != detailRevision || selected == null || !selected.id().equals(id)) return false;
        readingMatter = id; detailPosition = Objects.requireNonNull(position); return true;
    }
    GovernanceMatter selectedMatter(GovernanceWorkspaceData data) {
        return data == null ? null : GovernanceMatterWindow.of(this,data).selected(selectedMatter);
    }

    boolean readWindow(long revision,GovernanceWorkspaceData source,int index,GovernanceWorkspaceData current) {
        if (section != Section.MATTERS || revision != detailRevision || source != current || current == null) return false;
        int total = filter == Filter.COMPLETED ? current.historyTotal() : current.ongoing(subjectFilter).size();
        if (index < 0 || index >= Math.max(1,total)) return false;
        matterAnchor = index; selectedMatter = null; popup = null; resetMatterReading(); return true;
    }

    void reconcileMatters(GovernanceWorkspaceData data) {
        if (section != Section.MATTERS) return;
        var window = GovernanceMatterWindow.of(this,data);
        matterAnchor = Math.clamp(matterAnchor,0,Math.max(0,window.total() - 1));
        for (int index = 0; index < window.items().size(); index++) if (window.items().get(index).id().equals(selectedMatter)) {
            matterAnchor = window.offset() + index; break;
        }
    }

    /** Return from account settings keeps navigation and the open application, without reusing a live request. */
    GovernanceWorkspaceState navigationCopy() {
        var copy = new GovernanceWorkspaceState();
        copy.section = section; copy.filter = filter; copy.query = query;
        copy.staffPage = staffPage; copy.matterAnchor = matterAnchor;
        copy.subjectFilter = subjectFilter; copy.subjectName = subjectName; copy.selectedMatter = selectedMatter;
        copy.popup = popup; copy.readingMatter = readingMatter; copy.detailPosition = detailPosition;
        copy.readingPopup = readingPopup; copy.popupPosition = popupPosition;
        return copy;
    }

    /** Deferred UI factories retain this render's values rather than observing later owner mutations. */
    GovernanceWorkspaceState renderCopy() {
        var copy = navigationCopy(); copy.detailRevision = detailRevision; copy.busy = busy; return copy;
    }

    /** Layout clamps the requested page; refresh invalidates companions whose source no longer exists. */
    void reconcileStaff(List<org.encinet.mik.feature.community.governance.roster.StaffMember> staff) {
        var matches = staff.stream().filter(person -> matches(person.name())).toList();
        if (matches.isEmpty()) staffPage = 0;
        if (popup != null && (popup.returnToDetails() || popup.intent() == Intent.DETAILS)) {
            UUID selected = popup.subject();
            if (section != Section.TEAM || matches.stream().noneMatch(person -> person.id().equals(selected))) popup = null;
        }
    }
    boolean matches(String name) { return name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)); }
}
