package org.encinet.mik.module.plot;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotMenuArchitectureTest {
    private static final Path MAIN = Path.of("src/main/java/org/encinet/mik");

    @Test
    void childCreationHasDirectEntriesAndCannotSaveTheParentArea() throws IOException {
        String module = Files.readString(MAIN.resolve("module/plot/PlotModule.java"));
        assertTrue(module.contains("addSubPlotCreateAction(player, menu, plot, \"subplots\")"));
        assertTrue(module.contains("addSubPlotCreateAction(player, menu, parent, \"actions\")"));
        assertTrue(module.contains("PlotEditorContext.subPlot(parentId)"));
        assertTrue(module.contains("!scene.regions().editing() && !context.subPlot()"));
        assertTrue(module.contains("plot != null && !selections.context(player.getUniqueId()).subPlot()"));
        String create = module.substring(module.indexOf("private void createSubPlotInput("),
                module.indexOf("private void editArrivalText("));
        assertTrue(create.contains("PlotPermission.CREATE_SUBPLOT"));
        assertTrue(create.contains("preflightCache.request("));
        assertFalse(create.contains("registry.creatableSubPlotCells("));
        assertTrue(create.contains("selections.requireSubmission("));
        assertTrue(create.contains("parent.equals(expectedParent)"));
        assertTrue(create.contains("selections.context(actor).equals(area.context())"));
    }

    @Test
    void memberScreensUseTheRosterWithoutTreatingOwnersAsInvitedPlayers() throws IOException {
        String module = Files.readString(MAIN.resolve("module/plot/PlotModule.java"));
        assertFalse(module.contains("plot.members().size()"));
        assertTrue(module.contains("PlotRoster.entries(plot).stream()"));
        assertTrue(module.contains("Comparator.comparing(MemberCard::group)"));
        String detail = module.substring(module.indexOf("private FloatingMenuDefinition memberDefinition("),
                module.indexOf("private boolean canManage("));
        assertTrue(detail.contains("!member.owner() && permitted(player, plot, PlotPermission.MANAGE_MEMBERS)"));
        assertTrue(detail.contains("current.permissionSubject()"));
        assertTrue(detail.contains("current.owner()) transferInput"));
        assertTrue(detail.contains("parent != registry.parentOf(fresh)"));
        assertFalse(detail.contains("menu.information(\"hint\""));
        String roster = Files.readString(MAIN.resolve("module/plot/PlotRoster.java"));
        for (String dependency : List.of("org.bukkit", "module.i18n", "java.sql"))
            assertFalse(roster.contains(dependency), dependency);
    }

    @Test
    void longNoticesUseBoundedPreviewsAndAReadOnlyReaderWithoutPageHistoryGrowth() throws IOException {
        String module = Files.readString(MAIN.resolve("module/plot/PlotModule.java"));
        String reader = module.substring(module.indexOf("public void openNoticeBoard("),
                module.indexOf("public void registerCommands("));
        assertTrue(reader.contains("PlotNoticeText.pages(body)"));
        assertTrue(reader.contains("Component.text(pages.get(page.index())"));
        assertTrue(reader.contains("handle.update(noticeBoardDefinition("));
        assertTrue(reader.contains("!body.equals(registry.noticeBoard(plotId))"));
        assertTrue(reader.contains("registry.noticeBoard(plotId).isEmpty()"));
        assertTrue(reader.contains("fresh == null"));
        assertFalse(reader.contains("canManage("));
        assertFalse(reader.contains("saveNoticeBoard("));
        assertFalse(reader.contains("setNoticeBoard("));
        assertTrue(module.contains("PlotNoticeText.preview(notice)"));
        String shell = Files.readString(MAIN.resolve("shell/MainMenuModule.java"));
        assertTrue(shell.contains("plotNotice.preview()"));
        assertTrue(shell.contains("plotModule.openNoticeBoard(p, plotNotice.plotId())"));
        assertTrue(shell.contains("Message.PLOT_NOTICE_READ"));
    }

    @Test
    void noticeEditorAllowsLongMultilineInputAndRetainsInvalidDrafts() throws IOException {
        String module = Files.readString(MAIN.resolve("module/plot/PlotModule.java"));
        String editor = module.substring(module.indexOf("private void editNoticeBoard("),
                module.indexOf("private Component atmosphereLabel("));
        assertTrue(editor.contains("Message.PLOT_NOTICE_INPUT, PlotNoticeText.MAX_LENGTH"));
        assertTrue(editor.contains("initial, PlotNoticeText.MAX_INPUT_LENGTH, TextDialogInput.MultilineOptions.create(null, 120)"));
        assertTrue(editor.contains("PlotNoticeText.normalize(value)"));
        assertTrue(editor.contains("editNoticeBoard(viewer, plotId, value)"));
        assertTrue(editor.contains("managed(player, plotId.toString())"));
        assertTrue(editor.contains("persist(player, staged -> staged.setNoticeBoard("));
    }

    @Test
    void permanentEditorTextContainsStatusNotTutorials() throws IOException {
        String module = Files.readString(MAIN.resolve("module/plot/PlotModule.java"));
        String status = module.substring(module.indexOf("private Component miniatureHint("),
                module.indexOf("private Component miniatureInstructions("));
        for (String guidance : List.of("PLOT_MODEL_LEGEND", "PLOT_AREA_HINT", "PLOT_MODEL_RESOLUTION",
                "PLOT_SELECTION_SESSION_HINT")) assertFalse(status.contains(guidance), guidance);
        for (String warning : List.of("PLOT_MODEL_LEVEL", "PLOT_MODEL_PROGRESS", "PLOT_MODEL_PREPARING",
                "PLOT_MODEL_UNKNOWN", "PLOT_MODEL_LIMITED")) assertTrue(status.contains(warning), warning);
        assertTrue(module.contains("if (!status.equals(Component.empty()))"));
    }

    @Test
    void explanationsRemainReachableWithoutChangingRiskConfirmations() throws IOException {
        String module = Files.readString(MAIN.resolve("module/plot/PlotModule.java"));
        String help = module.substring(module.indexOf("private void contextHelp("),
                module.indexOf("private void confirmSaveArea("));
        assertTrue(help.contains("help.back("));
        assertTrue(help.contains("instructions.apply(viewer)"));
        assertTrue(module.contains("viewer -> miniatureInstructions(viewer, scene)"));
        for (String warning : List.of("PLOT_MENU_CONFIRM_DELETE", "PLOT_MENU_CONFIRM_TRANSFER",
                "PLOT_PERMISSION_CHANGE_CONFIRM", "PLOT_AREA_CONFIRM", "PLOT_REGION_DELETE_CONFIRM"))
            assertTrue(module.contains(warning), warning);
    }

    @Test
    void permissionAndMemberScreensHaveNoRepeatedHelpOrSwitchingInstructions() throws IOException {
        String module = Files.readString(MAIN.resolve("module/plot/PlotModule.java"));
        String permissions = module.substring(module.indexOf("private FloatingMenuDefinition permissionListDefinition("),
                module.indexOf("private FloatingMenuDefinition permissionSubjectDefinition("));
        assertFalse(permissions.contains("menu.information(\"hint\""));
        assertTrue(permissions.contains("permissionStatus(player, plot, access)"));
        assertTrue(permissions.contains("Message.PLOT_PERMISSION_RESET"));
        assertEquals(1, module.lines().filter(line -> line.strip().startsWith("contextHelp(menu,")).count());
        for (String redundant : List.of("PLOT_PERMISSION_LIST_HINT", "PLOT_PERMISSION_SUBJECT_HINT",
                "PLOT_MENU_ROLE_HINT", "PLOT_MENU_SUBPLOT_ROLE_HINT", "PLOT_MENU_ATMOSPHERE_HINT",
                "PLOT_MODEL_RESOLUTION", "PLOT_GUIDE_ACCESS", "PLOT_MENU_CORE_HINT"))
            assertFalse(module.contains(redundant), redundant);
        assertTrue(module.contains("Message.PLOT_MEMBER_DRAFT_HINT"));
    }

    @Test
    void guideKeepsPracticalSelectionHelpWithoutRepeatingMenuNavigation() throws IOException {
        String module = Files.readString(MAIN.resolve("module/plot/PlotModule.java"));
        String guide = module.substring(module.indexOf("private void openGuide("),
                module.indexOf("private void mark("));
        assertTrue(guide.contains("PlotMenuLayouts.help()"));
        for (String useful : List.of("PLOT_AREA_HINT", "PLOT_MODEL_LEGEND", "PLOT_SELECTION_SESSION_HINT"))
            assertTrue(guide.contains(useful), useful);
        assertFalse(guide.contains("guideSection("));
        assertFalse(guide.contains("PLOT_GUIDE_"));
    }

    @Test
    void detailSummaryDoesNotRepeatBoundaryInstructions() throws IOException {
        String module = Files.readString(MAIN.resolve("module/plot/PlotModule.java"));
        String summary = module.substring(module.indexOf("menu.information(\"summary\", summary.append"),
                module.indexOf("if (!notice.isEmpty())", module.indexOf("menu.information(\"summary\", summary.append")));
        assertFalse(summary.contains("PLOT_MENU_CORE_HINT"));
        assertFalse(summary.contains("PLOT_MENU_SUBPLOT_CORE_HINT"));
        assertTrue(summary.contains("currentAccess"));
        String detail = module.substring(module.indexOf("private FloatingMenuDefinition detailDefinition("),
                module.indexOf("private boolean editorAllowed("));
        assertFalse(detail.contains("contextHelp("));
        assertTrue(detail.contains("openGuide(p)"));
    }

    @Test
    void runtimePlotWritesUseTheAsyncCoordinatorAndNeverWaitForTheDatabase() throws IOException {
        String module = Files.readString(MAIN.resolve("module/plot/PlotModule.java"));
        assertTrue(module.contains("registry.enableAsyncWrites("));
        assertTrue(module.contains("registry.stopWrites()"));
        assertFalse(module.contains("edits.resizedCells("));
        for (String file : List.of("PlotModule.java", "PlotCreationController.java", "PlotArrivalController.java")) {
            String source = Files.readString(MAIN.resolve("module/plot/" + file));
            assertTrue(source.contains("writeAsync(") || source.contains("writeBehind("), file);
            assertFalse(source.contains(".join()"), file);
            assertFalse(source.contains("executeBatch("), file);
            for (String mutation : List.of("create", "resize", "createSubPlot", "delete", "invite",
                    "removeMember", "transfer", "rename", "flag", "setPublic", "setNoticeBoard", "setTime",
                    "setWeather", "cycleGroupAccess", "resetAccessToParent", "cycleAccess", "resetAccess", "applyAccess"))
                assertFalse(source.contains("edits." + mutation + "("), file + " still writes synchronously: " + mutation);
            for (String mutation : List.of("put", "remove", "rename", "setArrival", "clearArrival", "setNoticeBoard", "setAtmosphere"))
                assertFalse(source.contains("registry." + mutation + "("), file + " still writes synchronously: " + mutation);
        }
    }

    @Test
    void lightweightEditsPublishImmediatelyAndPersistenceHasNoPlayerFacingProgress() throws IOException {
        String module = Files.readString(MAIN.resolve("module/plot/PlotModule.java"));
        String feedback = Files.readString(MAIN.resolve("module/plot/PlotWriteFeedback.java"));
        String registry = Files.readString(MAIN.resolve("module/plot/PlotRegistry.java"));
        assertTrue(module.contains("edits.writeBehind(mutation)"));
        assertTrue(module.contains("persistGeometry("));
        assertFalse(feedback.contains("Message.PLOT_SAVING"));
        assertFalse(feedback.contains("future.isDone()"));
        assertTrue(registry.contains("revision != expectedRevision"));
        assertTrue(registry.contains("store.prepareAsync("));
        assertTrue(registry.contains("store.persistAsync("));
        assertFalse(registry.contains("pending != null"));
    }

    @Test
    void invitationDraftAndExplicitManagementStatesUseSharedPreviewAndCommit() throws IOException {
        String module = Files.readString(MAIN.resolve("module/plot/PlotModule.java"));
        String invite = module.substring(module.indexOf("private void invite("), module.indexOf("private void removeMember("));
        assertTrue(invite.contains("PlotPermission.MANAGE_MEMBERS"));
        assertTrue(invite.contains("PlotMemberDraft.of("));
        assertTrue(invite.contains("memberDraftDefinition("));
        assertFalse(invite.contains("persist("));
        String commit = module.substring(module.indexOf("private void commitAccessChange("),
                module.indexOf("private Component accessChangeBody("));
        assertTrue(commit.contains("persist(player"));
        assertTrue(commit.contains("staged.applyAccess(plan, actor, staff)"));
        String states = module.substring(module.indexOf("private FloatingMenuDefinition permissionSettingDefinition("),
                module.indexOf("private FloatingMenuDefinition memberDraftDefinition("));
        assertTrue(states.contains("PlotAccessPolicy.Setting.values()"));
        assertTrue(states.contains("submitAccessChange("));
        assertTrue(states.contains("accessProblem("));
        assertTrue(states.contains(".refreshEvery("));
        assertFalse(module.contains("confirmAdmin("));
    }

    @Test
    void permissionListsKeepOwnershipSeparateAndEditorsUseGranularEntityChecks() throws IOException {
        String module = Files.readString(MAIN.resolve("module/plot/PlotModule.java"));
        String index = module.substring(module.indexOf("private FloatingMenuDefinition accessDefinition("),
                module.indexOf("private void openPermissionList("));
        assertTrue(index.contains("PlotAccessPolicy.Group.values()"));
        assertTrue(index.contains("openPlotManagement("));
        assertFalse(index.contains("addReleaseAction("));
        assertFalse(index.contains("accessFlag("));
        assertTrue(module.contains("new PlotAccessRequest.Permission("));
        assertTrue(module.contains("new PlotAccessRequest.Reset("));
        assertTrue(module.contains("staged.applyAccess(plan, actor, staff)"));
        String axiom = Files.readString(MAIN.resolve("module/plot/integration/PlotAxiomHook.java"));
        assertFalse(axiom.contains("\"entity\""));
        for (String operation : List.of("PLACE", "MODIFY", "REMOVE"))
            assertTrue(axiom.contains("PlotEntityEditActions.Operation." + operation));
        String worldEdit = Files.readString(MAIN.resolve("module/plot/integration/PlotWorldEditHook.java"));
        assertTrue(worldEdit.contains("PlotEntityEditActions.placement("));
    }

    @Test
    void permissionTableKeepsCategoriesInPlaceAndResetsHaveExplicitScopes() throws IOException {
        String source = Files.readString(MAIN.resolve("module/plot/PlotModule.java"));
        String table = source.substring(source.indexOf("private FloatingMenuDefinition permissionListDefinition("),
                source.indexOf("private FloatingMenuDefinition permissionSubjectDefinition("));
        assertTrue(table.contains("PlotMenuLayouts.permissionTable()"));
        assertTrue(table.contains("PlotPermission.Category.values()"));
        assertTrue(table.contains(".selected(next == category)"));
        assertTrue(table.contains("refreshPermissionList(viewer, plotId, subject, next, handle)"));
        assertTrue(table.contains(".region(region + \"-group-heading\")"));
        assertTrue(table.contains(".region(region)"));
        assertTrue(table.contains(".spatialOnly()"));
        assertFalse(table.contains("menu.item("));
        assertFalse(table.contains("menu.choice("));
        assertFalse(table.contains("openPermissionList("));
        assertFalse(table.contains("FloatingMenus.present("));
        String picker = source.substring(source.indexOf("private FloatingMenuDefinition permissionSubjectDefinition("),
                source.indexOf("private void refreshPermissionList("));
        assertTrue(picker.contains("Message.PLOT_PERMISSION_ALL_ACTIONS"));
        assertTrue(picker.contains("subject,\n                            null, category, handle"));
        assertTrue(picker.contains("new PlotAccessRequest.Reset(subject, scope)"));
        assertTrue(picker.contains("submitAccessChange(player, plotId"));
        assertFalse(picker.contains(".join()"));
    }

    @Test
    void editorPreflightValidatesWithoutMaterializingTheSelectedCuboid() throws IOException {
        String source = Files.readString(MAIN.resolve("module/plot/PlotModule.java"));
        int start = source.indexOf("private FloatingMenuDefinition editorDefinition(");
        int end = source.indexOf("boolean spatial =", start);
        assertTrue(start >= 0 && end > start);
        String preflight = source.substring(start, end);
        assertTrue(preflight.contains("preflightCache.request("));
        assertFalse(preflight.contains("edits.validateCreatable("));
        assertFalse(preflight.contains("edits.validateResize("));
        assertFalse(preflight.contains("creatableCells("));
        assertFalse(preflight.contains("resizedCells("));
        assertFalse(preflight.contains("alignedCells("));
    }

    @Test
    void plotControllersDeclareTasksInsteadOfDuplicatingSpatialGeometry() throws IOException {
        for (String controller : List.of("module/plot/PlotModule.java",
                "module/plot/board/PlotCommunityBoard.java")) {
            String source = Files.readString(MAIN.resolve(controller));
            assertTrue(source.contains("PlotMenuLayouts.screen("), controller);
            assertFalse(source.contains("FloatingMenuLayouts"), controller);
            assertFalse(source.contains("new FloatingMenuFraming"), controller);
            assertFalse(source.contains("GeyserApi"), controller);
            assertFalse(source.contains("BedrockSimpleForm"), controller);
        }
    }

    @Test
    void sharedArcAndTaskLayoutsDoNotDependOnWorldStateOrStorage() throws IOException {
        for (String layout : List.of("module/menu/FloatingMenuArcLayout.java",
                "module/plot/PlotMenuLayouts.java",
                "module/plot/PlotPermissionMenuLayout.java",
                "module/plot/PlotSelection.java", "module/plot/PlotPreviewGeometry.java",
                "module/plot/PlotSelectionShape.java",
                "module/plot/PlotSelectionRegions.java",
                "module/governance/platform/paper/command/GovernanceArcLayouts.java")) {
            String source = Files.readString(MAIN.resolve(layout));
            for (String forbidden : List.of("import org.bukkit", "import java.sql",
                    "import org.encinet.mik.shell", "PlotRegistry", "PlotRepository")) {
                assertFalse(source.contains(forbidden), layout + " must not depend on " + forbidden);
            }
        }
        String mainMenu = Files.readString(MAIN.resolve("shell/MainMenuArcLayout.java"));
        assertTrue(mainMenu.contains("FloatingMenuLayouts.panoramicPanels("));
        assertFalse(mainMenu.contains("Math.sin("));
    }

    @Test
    void ordinaryPlotsHaveNoReservationOrConstructionEvidenceWorkflow() throws IOException {
        String module = Files.readString(MAIN.resolve("module/plot/PlotModule.java"));
        String creation = Files.readString(MAIN.resolve("module/plot/PlotCreationController.java"));
        String edits = Files.readString(MAIN.resolve("module/plot/PlotEdits.java"));
        for (String source : List.of(module, creation, edits)) {
            assertFalse(source.contains("confirmReservation"));
            assertFalse(source.contains("expireReservations"));
            assertFalse(source.contains("previewReservation"));
            assertFalse(source.contains("PlotPrismHistory"));
        }
        assertTrue(module.contains("case \"create\""));
        assertFalse(module.contains("case \"reserve\""));
        assertTrue(module.contains("PlotMenuLayouts.editor()"));
    }

    @Test
    void worldEditPositionsUseEventInterceptionInsteadOfCommandRegistration() throws IOException {
        String controller = Files.readString(MAIN.resolve("module/plot/PlotSelectionController.java"));
        assertTrue(controller.contains("void onCommand(PlayerCommandPreprocessEvent event)"));
        assertTrue(controller.contains("ignoreCancelled = true"));
        assertTrue(controller.contains("event.setCancelled(true)"));
        for (String registration : List.of("Commands.literal", "getCommand(", "CommandMap", "registerCommands"))
            assertFalse(controller.contains(registration));
        String module = Files.readString(MAIN.resolve("module/plot/PlotModule.java"));
        assertFalse(module.contains("Commands.literal(\"pos1\")"));
        assertFalse(module.contains("Commands.literal(\"pos2\")"));
        assertFalse(module.contains("Commands.literal(\"/pos1\")"));
        assertFalse(module.contains("Commands.literal(\"/pos2\")"));
    }

    @Test
    void oneRangeEditorDoesNotOfferDuplicatePointOrAdvancedSelectionScreens() throws IOException {
        String module = Files.readString(MAIN.resolve("module/plot/PlotModule.java"));
        for (String removed : List.of("openAdvancedEditor", "advanced-shape", "discard-brush",
                "preview-world", "menu.item(first ? \"pos1\"", "simpleSubmission", "PLOT_SELECTION_PENDING"))
            assertFalse(module.contains(removed), removed);
        assertTrue(module.contains("PlotSelectionCommands.status(points)"));
        assertTrue(module.contains("PlotSelectionShape.Operation.ADD, PlotSelectionShape.Operation.SUBTRACT"));
    }

    @Test
    void editorShortcutIsConsumedBeforeMainMenuAndSharedMenusRespectClaimedInput() throws Exception {
        var editor = PlotSelectionController.class.getMethod("onSwapHands",
                org.bukkit.event.player.PlayerSwapHandItemsEvent.class)
                .getAnnotation(org.bukkit.event.EventHandler.class);
        var menu = org.encinet.mik.module.menu.runtime.FloatingMenuService.class.getMethod("onSwapHandItems",
                org.bukkit.event.player.PlayerSwapHandItemsEvent.class)
                .getAnnotation(org.bukkit.event.EventHandler.class);
        assertTrue(editor.ignoreCancelled());
        assertTrue(menu.ignoreCancelled());
        assertTrue(editor.priority().getSlot() < menu.priority().getSlot());
    }
}
