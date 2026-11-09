package org.encinet.mik.feature.community.governance.platform.paper.command;

import java.util.*;
import org.encinet.mik.feature.community.governance.roster.StaffMember;
import org.encinet.mik.shared.menu.ui.UiKey;
import org.encinet.mik.shared.menu.ui.UiReadingPosition;
import org.encinet.mik.shared.role.PlayerRole;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernanceWorkspaceState.*;
import static org.junit.jupiter.api.Assertions.*;

class GovernanceWorkspaceStateTest {
    @Test void directoryRequestsBelongToTheirSnapshotSectionAndRevision() {
        var state = new GovernanceWorkspaceState(); state.section(Section.MATTERS);
        var data = GovernanceWorkspaceScenesTest.data(); long revision = state.detailRevision;
        assertFalse(state.readWindow(revision,GovernanceWorkspaceScenesTest.data(),1,data));
        assertFalse(state.readWindow(revision,data,-1,data));
        assertFalse(state.readWindow(revision,data,300,data));
        assertTrue(state.readWindow(revision,data,1,data)); assertEquals(1,state.matterAnchor);
        assertFalse(state.readWindow(revision,data,0,data));
        state.section(Section.TEAM);
        assertFalse(state.readWindow(state.detailRevision,data,0,data));
    }

    @Test void matterReadingBelongsToTheCurrentObjectAndSurvivesRefreshWithoutAcceptingRetiredRequests() {
        var data = GovernanceWorkspaceScenesTest.data();
        var state = new GovernanceWorkspaceState(); state.section(Section.MATTERS);
        var visible = GovernanceMatterWindow.of(state,data).items(); assertTrue(visible.size() > 1);
        String first = state.selectedMatter(data).id();
        var position = new UiReadingPosition(UiKey.named("proposal"),12,2);
        long revision = state.detailRevision;
        assertTrue(state.readMatter(revision,first,position,data));
        assertEquals(position,state.matterPosition(first));
        assertEquals(position,state.navigationCopy().matterPosition(first));
        String other = visible.stream().filter(matter -> !matter.id().equals(first)).findFirst().orElseThrow().id();
        state.selectedMatter = other;
        assertFalse(state.readMatter(revision,first,UiReadingPosition.START,data));
        assertEquals(UiReadingPosition.START,state.matterPosition(other));
        state.resetMatterReading();
        assertFalse(state.readMatter(revision,other,position,data));
        assertTrue(state.readMatter(state.detailRevision,other,position,data));
        long beforeRefresh = state.detailRevision++;
        assertEquals(position,state.matterPosition(other));
        assertFalse(state.readMatter(beforeRefresh,other,UiReadingPosition.START,data));
        assertTrue(state.readMatter(state.detailRevision,other,position,data));
        state.section(Section.TEAM);
        assertFalse(state.readMatter(state.detailRevision,other,position,data));
        assertEquals(UiReadingPosition.START,state.matterPosition(other));
    }

    @Test void returningFromPasswordSettingsKeepsTheApplicationAndBrowsingPositionWithoutAnOldBusyState() {
        var state = new GovernanceWorkspaceState();
        state.section = Section.MATTERS;
        state.filter = Filter.COMPLETED;
        state.query = "Alex";
        state.staffPage = 2; state.matterAnchor = 173;
        state.subjectFilter = UUID.randomUUID(); state.subjectName = "Alex";
        state.selectedMatter = "vote:42";
        state.popup = new Popup(UiKey.named("application"), UUID.randomUUID(), Intent.APPLICATION, false);
        var popupPosition = new UiReadingPosition(UiKey.named("requirement:1"),5,1);
        assertTrue(state.readPopup(state.detailRevision,state.popup,popupPosition));
        state.busy = true;

        var restored = state.navigationCopy();

        assertNotSame(state, restored);
        assertEquals(state.section, restored.section); assertEquals(state.filter, restored.filter);
        assertEquals(state.query, restored.query); assertEquals(state.subjectFilter, restored.subjectFilter);
        assertEquals(state.subjectName, restored.subjectName); assertEquals(state.selectedMatter, restored.selectedMatter);
        assertEquals(2, restored.staffPage); assertEquals(173, restored.matterAnchor);
        assertEquals(state.popup,restored.popup); assertEquals(popupPosition,restored.popupPosition());
        assertFalse(restored.busy);
        restored.cancelConfirmation();
        assertNotNull(state.popup); assertNull(restored.popup);
    }

    @Test void popupReadingRejectsReplacedCompanionsAndOldSnapshots() {
        var state = new GovernanceWorkspaceState();
        var first = new Popup(UiKey.named("person"),new UUID(0,1),Intent.DETAILS,false);
        state.popup = first;
        var position = new UiReadingPosition(UiKey.named("responsibility"),12,1);
        long revision = state.detailRevision;
        assertTrue(state.readPopup(revision,first,position));
        assertEquals(position,state.popupPosition());
        state.detailRevision++;
        assertFalse(state.readPopup(revision,first,UiReadingPosition.START));
        assertEquals(position,state.popupPosition(),"Refreshing preserves the source position");
        state.popup = new Popup(first.source(),first.subject(),first.intent(),false);
        assertEquals(UiReadingPosition.START,state.popupPosition());
        assertFalse(state.readPopup(state.detailRevision,first,position),"Reopening the same source starts a new companion");
        assertTrue(state.readPopup(state.detailRevision,state.popup,position));
        state.cancelConfirmation();
        assertFalse(state.readPopup(state.detailRevision,first,position));
    }

    @Test void confirmationCancelsBackToDetailsAndNavigationLeavesNoDetachedCompanion() {
        var state = new GovernanceWorkspaceState();
        UUID id = UUID.randomUUID();
        state.query = "Alex";
        state.popup = new Popup(UiKey.named("person"), id, Intent.DETAILS, false).confirm(Intent.SPONSOR);
        assertTrue(state.popup.returnToDetails());
        state.cancelConfirmation();
        assertEquals(Intent.DETAILS, state.popup.intent());
        assertEquals("Alex", state.query);
        state.section(Section.MATTERS);
        assertNull(state.popup);
        state.popup = new Popup(UiKey.named("application"), id, Intent.APPLICATION, false);
        state.cancelConfirmation();
        assertNull(state.popup);
    }

    @Test void removedOrFilteredPeopleCannotLeaveAnOrphanedInteractiveLayer() {
        var people = java.util.stream.IntStream.range(0, 12).mapToObj(index -> new StaffMember(new UUID(0,index+1), "Staff"+index, PlayerRole.MODERATOR, true)).toList();
        var state = new GovernanceWorkspaceState();
        state.staffPage = 1;
        state.popup = new Popup(UiKey.named("person"), people.get(7).id(), Intent.DETAILS, false);
        state.reconcileStaff(people);
        assertNotNull(state.popup);
        state.reconcileStaff(people.subList(0,4));
        assertNull(state.popup);
        state.popup = new Popup(UiKey.named("person"),people.getFirst().id(),Intent.DETAILS,false);
        state.search("sTaFf1");
        assertNull(state.popup);
        assertTrue(state.matches("STAFF10"));
        assertFalse(state.matches("Staff2"));
    }

    @Test void relatedHistoryKeepsItsSubjectUntilExplicitlyCleared() {
        var state = new GovernanceWorkspaceState();
        UUID subject = UUID.randomUUID();
        state.related(subject,"Alex");
        assertEquals(Section.MATTERS,state.section);
        assertEquals(Filter.COMPLETED,state.filter);
        assertEquals(subject,state.subjectFilter);
        state.filter(Filter.ONGOING);
        assertEquals(subject,state.subjectFilter);
        state.clearSubject();
        assertNull(state.subjectFilter);
        assertEquals(0,state.matterAnchor);
    }
}
