package org.encinet.mik.feature.community.governance.platform.paper.command;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.feature.community.governance.voting.model.*;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.encinet.mik.shared.role.PlayerRole;
import org.junit.jupiter.api.Test;

import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernanceActivityTestSupport.*;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernanceWorkspaceData.*;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class GovernanceVoteDetailsTest {
    @Test void replacingTheVoteInTheSameHostSlotReplacesItsActionIdentity() throws Exception {
        var text = text(Language.EN_US);
        var votes = new java.util.ArrayList<Long>();
        String previous = null;
        for (long id : new long[]{1,2}) {
            var matter = vote(id,true,null);
            var content = new GovernanceVoteDetails(text,Language.EN_US).content(matter.ballot(),false,
                    GovernanceVoteDetails.VotingState.READY,new UiReadingPosition(java.util.Optional.empty(),0,Integer.MAX_VALUE),
                    _ -> {},_ -> votes.add(matter.ballot().vote().id()));
            var scene = scene(UiTemplates.split(Ui.text(net.kyori.adventure.text.Component.text("Directory")).build(),content));
            var frame = org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport.measure(scene,1);
            var choice = UiSceneTestSupport.entry(scene.measured(frame),frame,"choice:YES");
            assertNotNull(choice);
            if (previous != null) assertNotEquals(previous,choice.id(),"A new object must not reuse the previous object's live action path");
            choice.trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
            previous = choice.id();
        }
        assertEquals(List.of(1L,2L),votes);
    }

    @Test void activityPopupAndMattersWorkspaceShowTheSameCompleteVoteAndDispatchTheSameChoices() throws Exception {
        var source = vote(1, true, null).ballot().vote();
        var proposals = List.of(new VoteProposal.Appointment(source.subjectId(), source.subjectName()),
                new VoteProposal.Removal(source.subjectId(), source.subjectName()),
                new VoteProposal.Ban(source.subjectId(), source.subjectName(), VIEWER, "Viewer", Duration.ofDays(1), "Important conditions"));
        for (var language : Language.values()) {
            var text = text(language);
            for (var proposal : proposals) for (boolean busy : new boolean[]{false, true}) {
                var vote = new GovernanceVote(source.id(), proposal, source.opensAt(), source.closesAt(), source.status(),
                        source.eligibleVoters(), source.quorumRequired(), source.tally(), null, VoteFailure.NONE, null);
                for (var ballot : List.of(new Ballot(vote, true, null), new Ballot(vote, true, VoteChoice.NO), new Ballot(vote, false, null))) {
                    var matter = GovernanceMatter.vote(ballot);
                    var activityState = new GovernanceActivityState();
                    activityState.update(new GovernanceActivityData(List.of(matter), true));
                    var compactChoice = new AtomicReference<VoteChoice>();
                    var compactActions = new GovernanceActivityScenes.Actions() {
                        public void open(GovernanceMatter selected, UiKey key, boolean confirm) { }
                        public void cast(GovernanceMatter selected, VoteChoice choice) {
                            assertEquals(matter.id(), selected.id()); compactChoice.set(choice);
                        }
                        public void join(GovernanceMatter selected) { }
                        public void all() { }
                        public void reload() { }
                    };
                    var popup = scene(new GovernanceActivityScenes(text, language, VIEWER, activityState, NOW, busy, null, compactActions)
                            .details(matter,false,UiReadingPosition.START,_ -> {}, () -> {}));
                    var state = new GovernanceWorkspaceState();
                    state.section(GovernanceWorkspaceState.Section.MATTERS); state.busy = busy;
                    var fullAction = new AtomicReference<GovernanceWorkspaceAction>();
                    var base = GovernanceWorkspaceScenesTest.data();
                    var data = new GovernanceWorkspaceData(List.of(), List.of(ballot), List.of(), List.of(), 0, 0,
                            List.of(), base.active(), base.participation(), java.util.Set.of(), false);
                    var workspace = new GovernanceWorkspaceScenes(text, language,
                            new GovernanceWorkspaceScenes.Viewer(VIEWER, PlayerRole.MEMBER, true), state, data, fullAction::set, _ -> false)
                            .render(null, false);

                    var expected = detailElements(workspace);
                    var actual = detailElements(popup);
                    assertEquals(expected.keySet(), actual.keySet());
                    assertTrue(actual.keySet().containsAll(List.of("vote-title", "electorate", "period", "progress", "own-ballot")));
                    assertEquals(proposal instanceof VoteProposal.Ban, actual.containsKey("proposal"));
                    for (var key : expected.keySet()) {
                        var fromWorkspace = expected.get(key); var fromPopup = actual.get(key);
                        assertEquals(fromWorkspace.content(), fromPopup.content(), language + " " + key);
                        assertEquals(fromWorkspace.role(), fromPopup.role(), key);
                        assertEquals(fromWorkspace.style(), fromPopup.style(), key);
                        assertEquals(fromWorkspace.alignment(), fromPopup.alignment(), key);
                        assertEquals(fromWorkspace.textWidthPixels(), fromPopup.textWidthPixels(), key);
                        assertEquals(fromWorkspace.input().selected(), fromPopup.input().selected(), key);
                        assertEquals(fromWorkspace.input().disabledReason(), fromPopup.input().disabledReason(), key);
                    }
                    String progress = PlainTextComponentSerializer.plainText().serialize(entry(popup, "progress").label());
                    assertEquals(text.t(language, Message.GOVERNANCE_VOTE_IN_PROGRESS, vote.tally().participating()), progress,
                            "live details show participation, without choice totals");
                    var fullPage = lastVotePage(workspace);
                    var popupPage = lastVotePage(popup);
                    for (var choice : VoteChoice.values()) {
                        var fullButton = fullPage.entry("choice:" + choice);
                        var popupButton = popupPage.entry("choice:" + choice);
                        assertEquals(ballot.voter(),fullButton != null);
                        assertEquals(ballot.voter(),popupButton != null);
                        if (ballot.voter()) {
                            assertEquals(choice == ballot.choice(),fullButton.selected());
                            assertEquals(fullButton.selected(),popupButton.selected());
                            assertEquals(!busy,fullButton.enabled()); assertEquals(fullButton.enabled(),popupButton.enabled());
                        }
                    }
                    if (ballot.voter() && !busy) {
                        fullPage.entry("choice:YES").trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
                        popupPage.entry("choice:YES").trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
                        assertEquals(new GovernanceWorkspaceAction.Cast(vote.id(), VoteChoice.YES), fullAction.get());
                        assertEquals(VoteChoice.YES, compactChoice.get());
                    }
                }
            }
        }
    }

    @Test void aHeldVoteThatClosesKeepsItsCompleteContextWithoutStaleVotingActions() throws Exception {
        var state = new GovernanceActivityState();
        var matter = vote(1, true, VoteChoice.NO);
        state.update(new GovernanceActivityData(List.of(matter), true));
        state.hold(); state.update(new GovernanceActivityData(List.of(), true));
        var popup = scene(scenes(Language.EN_US, state, false, NO_ACTIONS).details(matter,false,UiReadingPosition.START,_ -> {}, () -> {}));
        for (String key : List.of("vote-title", "electorate", "period", "own-ballot", "closed"))
            assertNotNull(entry(popup, key), key);
        assertNull(entry(popup, "progress"));
        var text = text(Language.EN_US);
        assertEquals(text.t(Language.EN_US, Message.GOVERNANCE_OWN_CHOICE_CLOSED, text.choice(Language.EN_US, VoteChoice.NO)),
                PlainTextComponentSerializer.plainText().serialize(entry(popup, "own-ballot").label()));
        for (var choice : VoteChoice.values()) assertNull(entry(popup, "choice:" + choice));
        state.release();
    }

    private record VotePage(MenuScene scene, UiFrame frame) {
        MenuScene.Entry entry(String key) { return UiSceneTestSupport.entry(scene,frame,key); }
    }

    private static VotePage lastVotePage(MenuScene source) {
        var found = new AtomicReference<UiDocument>();
        UiTree.visit(source.content(),(_,node) -> { if (node instanceof UiDocument document && document.key().value().startsWith("vote:")) found.set(document); });
        var original = java.util.Objects.requireNonNull(found.get());
        var last = original.blocks().getLast();
        var document = new UiDocument(original.key(),original.header(),original.blocks(),original.gap(),
                new UiReadingPosition(last.scope(),Integer.MAX_VALUE,original.blocks().size() - 1),_ -> {},original.style(),original.sizing(),original.pager(),original.bodyReading());
        var scene = MenuScene.screen("vote-last-page").content(document).build();
        var frame = org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport.measure(scene,1);
        return new VotePage(scene.measured(frame),frame);
    }

    private static Map<String, UiElement> detailElements(MenuScene scene) {
        var detail = new AtomicReference<UiNode>();
        UiTree.visit(scene.content(), (_, node) -> {
            if (node.key().value().equals("matter-detail")) detail.set(node);
        });
        assertNotNull(detail.get());
        return UiSceneContent.elements(detail.get()).values().stream().collect(Collectors.toMap(node -> node.key().value(), node -> node));
    }
}
