package org.encinet.mik.feature.community.governance.platform.paper.command;

import java.util.UUID;
import org.encinet.mik.feature.community.governance.voting.model.VoteChoice;
import org.encinet.mik.shared.menu.ui.UiKey;
import org.encinet.mik.shared.menu.ui.UiReadingPosition;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernanceWorkspaceState.*;

/** Semantic intents shared by the declarations and their main-thread owner. */
sealed interface GovernanceWorkspaceAction {
    enum Command implements GovernanceWorkspaceAction {
        REFRESH, CLOSE, IDENTITY, PLAYERS, PASSWORD, DISMISS_POPUP, CANCEL_CONFIRM, PERFORM, CLEAR_FILTER
    }
    record Navigate(Section section) implements GovernanceWorkspaceAction { }
    record FilterMatters(Filter filter) implements GovernanceWorkspaceAction { }
    record StaffPage(int index) implements GovernanceWorkspaceAction { }
    record ReadMatterWindow(long revision,GovernanceWorkspaceData source,int sourceIndex) implements GovernanceWorkspaceAction { }
    record ReadPopup(long revision, Popup source, UiReadingPosition position) implements GovernanceWorkspaceAction { }
    record SelectStaff(UUID id, UiKey source) implements GovernanceWorkspaceAction { }
    record SelectMatter(String id) implements GovernanceWorkspaceAction { }
    record ReadMatter(long revision, String id, UiReadingPosition position) implements GovernanceWorkspaceAction { }
    record OpenPopup(Intent intent, UUID subject, UiKey source) implements GovernanceWorkspaceAction { }
    record Related(UUID id, String name) implements GovernanceWorkspaceAction { }
    record Cast(long id, VoteChoice choice) implements GovernanceWorkspaceAction { }
}
