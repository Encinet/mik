package org.encinet.mik.feature.community.governance.platform.paper.command;

import java.util.*;
import java.util.concurrent.atomic.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.feature.community.governance.removal.RemovalPetition;
import org.encinet.mik.feature.community.governance.voting.model.*;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernanceActivityTestSupport.*;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class GovernanceActivityScenesTest {
    @Test void allDomainFailuresUseSymbolicDiagnosticsAndLocalizedPresentation() throws Exception {
        for (var language : Language.values()) {
            var text = text(language);
            for (var code : org.encinet.mik.feature.community.governance.GovernanceException.Code.values()) {
                var error = new org.encinet.mik.feature.community.governance.GovernanceException(code);
                assertEquals(code.name(), error.getMessage());
                String translated = text.t(language, text.error(error.code()));
                assertFalse(translated.isBlank());
                assertNotEquals(code.name(), translated, language + " " + code);
            }
        }
    }

    @Test void noMattersLeaveNoHeadingOrEmptySpaceAndOneVoteCanBeCastWithoutNavigation() throws Exception {
        assertEquals(UiSize.ZERO, SceneGeometryTestSupport.measure(scene(content(Language.EN_US,0)),1).size());
        var picked = new AtomicReference<VoteChoice>();
        var target = new AtomicReference<String>();
        var actions = new Actions() {
            @Override public void cast(GovernanceMatter matter,VoteChoice choice) { target.set(matter.id()); picked.set(choice); }
        };
        var state = state(1);
        var scene = scene(scenes(Language.EN_US,state,false,actions).content());
        assertNull(entry(scene,"activity-heading"));
        assertNull(entry(scene,"activity-all"));
        for (var choice : VoteChoice.values()) {
            var button = entry(scene,"choice:"+choice);
            assertFalse(button.selected());
            click(scene,"choice:"+choice);
            assertEquals("vote:1",target.get());
            assertEquals(choice,picked.get());
        }
        state.update(new GovernanceActivityData(List.of(vote(1,true,VoteChoice.NO)),true));
        var voted = scene(scenes(Language.EN_US,state,false,actions).content());
        assertTrue(entry(voted,"choice:NO").selected());
        assertFalse(entry(voted,"choice:YES").selected());
        assertTrue(voted.entries().values().stream().allMatch(row -> !plain(row.label()).contains("1,337") && !plain(row.label()).contains("1337")), "other voters' category totals stay out of the summary");
        var busy = scene(scenes(Language.EN_US,state,true,actions).content());
        for (var choice : VoteChoice.values()) assertNotNull(entry(busy,"choice:"+choice).disabledReason());
        var viewOnly = new GovernanceActivityState();
        viewOnly.update(new GovernanceActivityData(List.of(vote(1,false,null)),false));
        var read = scene(scenes(Language.EN_US,viewOnly,false,actions).content());
        assertNull(entry(read,"choice:YES")); assertNotNull(entry(read,"view-only"));
    }

    @Test void multipleMattersUseTwoRowsAndOnlyOverflowHasAnAllLink() throws Exception {
        var opened = new AtomicReference<String>();
        var all = new AtomicInteger();
        var actions = new Actions() {
            @Override public void open(GovernanceMatter matter,UiKey source,boolean confirm) {
                assertEquals(GovernanceActivityScenes.source(matter),source);
                assertFalse(confirm); opened.set(matter.id());
            }
            @Override public void all() { all.incrementAndGet(); }
        };
        var state = state(3);
        var scene = scene(scenes(Language.EN_US,state,false,actions).content());
        assertNull(entry(scene,"choice:YES"));
        assertNotNull(entry(scene,"activity:vote:1")); assertNotNull(entry(scene,"activity:vote:2"));
        assertNull(entry(scene,"activity:vote:3")); assertNotNull(entry(scene,"activity-all"));
        click(scene,"activity:vote:2"); assertEquals("vote:2",opened.get());
        click(scene,"activity-all"); assertEquals(1,all.get());
        assertNull(entry(scene(scenes(Language.EN_US,state(2),false,actions).content()),"activity-all"));
        state.update(new GovernanceActivityData(List.of(vote(2,true,VoteChoice.YES)),true));
        var shorter = scene(scenes(Language.EN_US,state,false,actions).content());
        assertNotNull(entry(shorter,"activity:vote:2"));
        assertNull(entry(shorter,"choice:YES"),"background removal must not introduce a vote action under the old list");
    }

    @Test void submissionAndCompanionHoldsKeepTheirSourceWhenTheMatterEnds() throws Exception {
        var state = state(1);
        var original = state.displayed().getFirst();
        state.hold(); state.hold();
        state.update(new GovernanceActivityData(List.of(vote(2,true,null)),true));
        assertEquals("vote:1",state.displayed().getFirst().id());
        assertFalse(state.current(original,NOW));
        var stale = scene(scenes(Language.EN_US,state,false,NO_ACTIONS).content());
        assertNotNull(entry(stale,"activity:vote:1")); assertNull(entry(stale,"choice:YES"));
        assertNotNull(entry(stale,"closed"));
        state.release(); assertEquals("vote:1",state.displayed().getFirst().id());
        state.release(); assertEquals("vote:2",state.displayed().getFirst().id());
        state.update(new GovernanceActivityData(List.of(),true));
        assertTrue(state.displayed().isEmpty());
    }

    @Test void petitionsRequireAnExplicitConfirmationAndJoinedOrIneligibleUsersCannotJoinAgain() throws Exception {
        var petition = new RemovalPetition(9,new UUID(0,7),"Steve",NOW.minusSeconds(20),NOW.plusSeconds(9000),3,1,null);
        var matter = GovernanceMatter.petition(new GovernanceWorkspaceData.Petition(petition,false));
        var state = new GovernanceActivityState(); state.update(new GovernanceActivityData(List.of(matter),true));
        var open = new AtomicBoolean(); var joined = new AtomicInteger(); var cancel = new AtomicInteger();
        var actions = new Actions() {
            @Override public void open(GovernanceMatter selected,UiKey source,boolean confirm) { assertEquals(matter,selected); open.set(confirm); }
            @Override public void join(GovernanceMatter selected) { assertEquals(matter.id(),selected.id()); joined.incrementAndGet(); }
        };
        var view = scenes(Language.EN_US,state,false,actions);
        var initial = scene(view.content());
        click(initial,"activity-join:petition:9"); assertTrue(open.get()); assertEquals(0,joined.get());
        var confirmation = scene(view.details(matter,true,UiReadingPosition.START,_ -> {},cancel::incrementAndGet));
        click(confirmation,"cancel-petition"); assertEquals(1,cancel.get()); assertEquals(0,joined.get());
        click(confirmation,"confirm-petition"); assertEquals(1,joined.get());
        var submitting = scene(scenes(Language.EN_US,state,true,actions).details(matter,true,UiReadingPosition.START,_ -> {},cancel::incrementAndGet));
        assertNotNull(entry(submitting,"cancel-petition").disabledReason());
        assertNotNull(entry(submitting,"confirm-petition").disabledReason());
        var already = GovernanceMatter.petition(new GovernanceWorkspaceData.Petition(petition,true));
        state.update(new GovernanceActivityData(List.of(already),true));
        var done = scene(scenes(Language.EN_US,state,false,actions).content());
        assertNotNull(entry(done,"joined")); assertNull(entry(done,"activity-join:petition:9"));
        state.update(new GovernanceActivityData(List.of(matter),false));
        assertNotNull(entry(scene(scenes(Language.EN_US,state,false,actions).content()),"view-only"));
    }

    @Test void anEndedPetitionDoesNotShowOutdatedRequirementsOrDuplicateItsStatus() throws Exception {
        var petition = new RemovalPetition(9,new UUID(0,7),"Steve",NOW.minusSeconds(20),NOW.plusSeconds(9000),3,1,null);
        var matter = GovernanceMatter.petition(new GovernanceWorkspaceData.Petition(petition,false));
        var state = new GovernanceActivityState(); state.update(new GovernanceActivityData(List.of(matter),true));
        state.hold(); state.update(new GovernanceActivityData(List.of(),true));
        var detail = scene(scenes(Language.EN_US,state,false,NO_ACTIONS).details(matter,false,UiReadingPosition.START,_ -> {},() -> {}));
        assertNotNull(entry(detail,"closed"));
        assertNull(entry(detail,"needed"));
        assertNull(entry(detail,"activity-join:petition:9"));
        assertEquals(1,detail.entries().values().stream().filter(row -> plain(row.label()).equals("Closed")).count());
        state.release();
    }

    @Test void aHeldListShowsEachClosedStatusOnlyOnce() throws Exception {
        var state = state(2);
        state.hold(); state.update(new GovernanceActivityData(List.of(),true));
        var list = scene(scenes(Language.EN_US,state,false,NO_ACTIONS).content());
        assertEquals(2,list.entries().values().stream().filter(row -> plain(row.label()).equals("Closed")).count());
        state.release();
    }

    @Test void longProposalsHaveNoQuickChoicesUntilTheirAdditionalTermsAreVisible() throws Exception {
        var source = vote(1,true,null).ballot().vote();
        var ban = new VoteProposal.Ban(source.subjectId(),source.subjectName(),VIEWER,"Viewer",java.time.Duration.ofDays(1),"Important conditions ".repeat(100));
        var matter = GovernanceMatter.vote(new GovernanceWorkspaceData.Ballot(new GovernanceVote(1,ban,source.opensAt(),source.closesAt(),source.status(),source.eligibleVoters(),source.quorumRequired(),source.tally(),null,VoteFailure.NONE,null),true,null));
        var state = new GovernanceActivityState(); state.update(new GovernanceActivityData(List.of(matter),true));
        var view = scenes(Language.EN_US,state,false,NO_ACTIONS);
        var compact = scene(view.content());
        assertNull(entry(compact,"choice:YES")); assertNotNull(entry(compact,"read-vote"));
        var position = new AtomicReference<>(UiReadingPosition.START);
        var complete = new StringBuilder();
        int count = 1;
        String expected = null;
        for (int index = 0; index < count; index++) {
            var sourceDetail = scene(view.details(matter,false,position.get(),position::set,() -> {}));
            if (expected == null) expected = plain(entry(sourceDetail,"proposal").label());
            var frame = SceneGeometryTestSupport.measure(sourceDetail,1.8);
            var expanded = sourceDetail.measured(frame);
            var paging = frame.pagination().getFirst(); count = paging.page().count();
            assertEquals(index,paging.page().index());
            boolean last = !paging.page().hasNext();
            assertEquals(last,entry(expanded,frame,"choice:YES") != null,"Voting belongs to the page that finishes the complete terms");
            for (var node : frame.elements()) if (node.node().key().value().equals("proposal")) complete.append(plain(node.node().content().label()));
            if (!last) paging.selectPage().accept(paging.page().next().index());
        }
        assertTrue(count > 1);
        assertEquals(expected.replaceAll("\\s+",""),complete.toString().replaceAll("\\s+",""));
        var submitting = scene(scenes(Language.EN_US,state,true,NO_ACTIONS).content());
        assertNotNull(entry(submitting,"read-vote").disabledReason());
    }

    @Test void translatedActivityAndCompanionsFitAllTextSizesAndNarrowHosts() throws Exception {
        for (var language : Language.values()) for (var scale : FloatingMenuTextScale.values()) {
            for (int count : new int[] {1,2,3}) {
                var state = state(count);
                var view = scenes(language,state,false,NO_ACTIONS);
                var content = view.content();
                for (int width : new int[] {320,640}) {
                    // The activity card is auxiliary in production; the detail itself owns a reading budget below.
                    var host = MenuScene.screen("test").viewport(new UiSize(width + 220,2048)).content(
                            UiTemplates.panoramicPanels("reader",UiLength.ui(16),
                                    UiTemplates.window("reader",Ui.stack().size(new UiSize(160,80)).build()),
                                    UiTemplates.sidebar("activity",content).withSizing(UiSizing.width(UiLength.ZERO,UiLength.ui(width))))).build();
                    assertDisjoint(assertDoesNotThrow(() -> SceneGeometryTestSupport.measure(host,scale.factor()),language+" @ "+scale+" width "+width));
                }
                var selected = state.displayed().getFirst();
                assertDisjoint(SceneGeometryTestSupport.measure(scene(view.details(selected,false,UiReadingPosition.START,_ -> {},() -> {})),scale.factor()));
            }
        }
    }

    private static void click(MenuScene scene,String key) { entry(scene,key).trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY); }
    private static String plain(Component label) { return PlainTextComponentSerializer.plainText().serialize(label); }
    private static class Actions implements GovernanceActivityScenes.Actions {
        public void open(GovernanceMatter matter,UiKey source,boolean confirm) { }
        public void cast(GovernanceMatter matter,VoteChoice choice) { }
        public void join(GovernanceMatter matter) { }
        public void all() { }
        public void reload() { }
    }
}
