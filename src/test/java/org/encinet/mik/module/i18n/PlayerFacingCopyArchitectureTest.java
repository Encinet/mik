package org.encinet.mik.module.i18n;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerFacingCopyArchitectureTest {
    private static final Path MAIN = Path.of("src/main/java/org/encinet/mik");

    @Test
    void announcementLinkKeepsItsLabelAndActionWithoutRepeatingClickInstructions() throws IOException {
        String module = source("module/communication/AnnouncementModule.java");
        String footer = module.substring(module.indexOf("private Component chatFooterClickable("),
                module.indexOf("public void openAnnouncementsMenu("));
        assertTrue(footer.contains("Message.ANNOUNCEMENT_VIEW_ALL"));
        assertTrue(footer.contains("TextDecoration.UNDERLINED"));
        assertTrue(footer.contains("ClickEvent.runCommand(\"/announcements\")"));
        assertFalse(footer.contains("ANNOUNCEMENT_CLICK_HINT"));
        assertFalse(footer.contains("ANNOUNCEMENT_OPEN_MENU"));
        assertFalse(footer.contains("hoverEvent("));
    }

    @Test
    void emptyGovernanceScreensKeepEmptyStateAndRealActionsInsteadOfImpossibleSelectionInstructions() throws IOException {
        String controller = source("module/governance/platform/paper/command/GovernanceMenuController.java");
        assertFalse(controller.contains("GOVERNANCE_MENU_SELECT_VOTE"));
        assertFalse(controller.contains("GOVERNANCE_MENU_PETITIONS_HINT"));
        assertTrue(controller.contains("if (selected != null)"));
        assertTrue(controller.contains("Message.GOVERNANCE_NONE"));
        assertTrue(controller.contains("Message.GOVERNANCE_MENU_NEW_PETITION"));
        assertTrue(controller.contains("Message.GOVERNANCE_MENU_CONFIRM_SPONSOR"));
    }

    @Test
    void emptyMusicQueueOffersADirectActionAndKeepsItsPageAndTrackCount() throws IOException {
        String control = source("module/music/ui/JukeboxControlGui.java");
        assertFalse(control.contains("MUSIC_QUEUE_EMPTY_DESCRIPTION"));
        assertTrue(control.contains("menu.item(\"queue:select-music\""));
        assertTrue(control.contains("actions.selectMusic(viewer, control.location())"));
        for (String status : List.of("MUSIC_QUEUE_EMPTY", "MUSIC_QUEUE_SUMMARY",
                "MUSIC_QUEUE_SUMMARY_DESCRIPTION, page, totalPages", "MUSIC_NEXT_PAGE", "MUSIC_PREV_PAGE"))
            assertTrue(control.contains(status), status);
        String browser = source("module/music/ui/MusicBrowserGui.java");
        assertFalse(browser.contains("MUSIC_LOADING_DESCRIPTION"));
        assertTrue(browser.contains("Message.MUSIC_LOADING_TITLE"));
        assertTrue(browser.contains("Message.MUSIC_REQUEST_ERROR"));
    }

    @Test
    void musicDiscsKeepIdentityMetadataAndIntegrityWithoutADecorativeFooter() throws IOException {
        String factory = source("module/music/disc/MusicDiscFactory.java");
        assertFalse(factory.contains("MUSIC_DISC_FOOTER"));
        for (String identity : List.of("meta.displayName(", "Message.MUSIC_ARTIST", "Message.MUSIC_DURATION",
                "Message.MUSIC_DISC_LEFT", "Message.MUSIC_DISC_RIGHT", "MusicDiscKeys.TRACK",
                "MusicDiscKeys.TRACK_SIGNATURE", "signer.sign(snapshot)"))
            assertTrue(factory.contains(identity), identity);
    }

    @Test
    void urlDialogKeepsDestinationAndConfirmationWithNoDuplicateBackTooltip() throws IOException {
        String dialogs = source("module/menu/MenuDialogs.java");
        assertFalse(dialogs.contains("BACK_TO_MAIN_DESCRIPTION"));
        for (String important : List.of("Message.URL_DIALOG_HINT", "Message.URL_DIALOG_QUESTION_RICH",
                "Message.URL_DIALOG_CONFIRM", "Message.BACK_TO_MAIN", "ClickEvent.openUrl(url)"))
            assertTrue(dialogs.contains(important), important);
    }

    @Test
    void destructiveAndNonObviousOperationGuidanceRemainsAvailable() throws IOException {
        assertTrue(source("module/ban/BanDialogController.java").contains("Message.BAN_DIALOG_NEVER_JOINED_WARNING"));
        assertTrue(source("module/world/regen/AsyncRegenModule.java").contains("Message.ASYNC_REGEN_SAFETY_NOTE"));
        assertTrue(source("module/event/FifthAnniversaryEventModule.java").contains("Message.ANNIVERSARY_DELIVERY_NOTE"));
        assertTrue(source("module/commands/SimpleFeaturesModule.java").contains("Message.TRASH_TITLE_HINT"));
        assertTrue(source("module/vehicle/VehicleModule.java").contains("Message.VEHICLE_CONTROLS"));
        assertTrue(source("module/identity/IdentityBindingCommandPresenter.java").contains("Message.IDENTITY_BINDINGS_UNLINK_HINT"));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }
}
