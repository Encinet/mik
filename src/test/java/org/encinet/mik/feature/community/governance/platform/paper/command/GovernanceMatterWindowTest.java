package org.encinet.mik.feature.community.governance.platform.paper.command;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.encinet.mik.feature.community.governance.voting.model.*;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernanceWorkspaceData.*;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernanceWorkspaceState.*;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class GovernanceMatterWindowTest {
    @Test void everyOngoingAndHistoricalMatterRemainsReachableAcrossMeasuredPagesAndStorageBatches() throws Exception {
        for (boolean completed : new boolean[]{false,true}) for (double font : new double[]{.7,1.8})
            for (double scale : new double[]{.7,1.3}) for (int fov : new int[]{30,110}) {
                var cards = cards(completed ? 237 : 300,completed);
                var state = new GovernanceWorkspaceState(); state.section(Section.MATTERS);
                if (completed) state.filter(Filter.COMPLETED);
                var action = new AtomicReference<GovernanceWorkspaceAction>(); var seen = new HashSet<String>();
                int pages = -1;
                for (int pass = 0; pass < 350; pass++) {
                    var snapshot = snapshot(cards,state.matterAnchor,completed);
                    state.reconcileMatters(snapshot);
                    var scene = GovernanceWorkspaceScenesTest.view(Language.EN_US,state,snapshot,action).render(null,false);
                    var frame = SceneGeometryTestSupport.readableFrame(scene,font,new FloatingMenuFieldOfView(fov),scale);
                    assertDisjoint(frame);
                    for (var row : frame.elements()) if (row.node().key().value().startsWith("vote:")) {
                        String id = row.node().key().value();
                        assertTrue(seen.add(id),"A source row was repeated across display or storage pages");
                        row.node().input().actions().get(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
                        assertEquals(new GovernanceWorkspaceAction.SelectMatter(id),action.get());
                    }
                    var page = frame.pagination().stream().filter(value -> value.path().endsWith("/matter-list")).findFirst().orElseThrow();
                    if (pages < 0) pages = page.page().count();
                    assertEquals(pages,page.page().count()); assertEquals(pass,page.page().index());
                    if (!page.page().hasNext()) break;
                    page.action(FloatingMenuInteraction.SCROLL_DOWN).execute(null,null,FloatingMenuInteraction.SCROLL_DOWN);
                    var read = assertInstanceOf(GovernanceWorkspaceAction.ReadMatterWindow.class,action.get());
                    assertSame(snapshot,read.source());
                    assertTrue(state.readWindow(read.revision(),read.source(),read.sourceIndex(),snapshot));
                }
                assertEquals(cards.size(),seen.size());
                assertEquals(cards.stream().map(card -> "vote:" + card.vote().id()).collect(java.util.stream.Collectors.toSet()),seen);
            }
    }

    @Test void sourceAnchorsAndBindingsSurviveTypographyChangesAndHistoryReordering() throws Exception {
        var cards = cards(237,true); var state = new GovernanceWorkspaceState(); state.section(Section.MATTERS); state.filter(Filter.COMPLETED);
        state.matterAnchor = 173;
        var action = new AtomicReference<GovernanceWorkspaceAction>(); String path = null;
        for (double font : new double[]{.7,1.8}) for (int fov : new int[]{30,110}) {
            var snapshot = snapshot(cards,state.matterAnchor,true);
            var scene = GovernanceWorkspaceScenesTest.view(Language.EN_US,state,snapshot,action).render(null,false);
            var frame = SceneGeometryTestSupport.readableFrame(scene,font,new FloatingMenuFieldOfView(fov),1.3);
            var row = element(frame,"vote:174");
            if (path == null) path = row.path(); else assertEquals(path,row.path());
        }
        var reverse = new ArrayList<>(cards); Collections.reverse(reverse);
        var snapshot = snapshot(reverse,63,true); state.matterAnchor = 63;
        var scene = GovernanceWorkspaceScenesTest.view(Language.EN_US,state,snapshot,action).render(null,false);
        var frame = SceneGeometryTestSupport.readableFrame(scene,1.8,new FloatingMenuFieldOfView(30),1.3);
        assertEquals(path,element(frame,"vote:174").path());
    }

    @Test void maximumHistoryCountsMapBackToTheLastSourceWithoutOverflow() {
        for (int capacity : new int[]{1,3,17,99,100,128,256}) {
            var first = new GovernanceMatterWindow(List.of(),0,Integer.MAX_VALUE,0,true);
            int batch = GovernanceMatterWindow.HISTORY_BATCH_SIZE;
            var navigation = first.navigation(new FloatingMenuPage(0,batch,capacity));
            int source = first.sourceIndex(navigation.count() - 1,capacity);
            assertTrue(source >= 0 && source < Integer.MAX_VALUE);
            int offset = source / batch * batch;
            var last = new GovernanceMatterWindow(List.of(),offset,Integer.MAX_VALUE,source,true);
            var local = new FloatingMenuPage((source - offset) / capacity,Integer.MAX_VALUE - offset,capacity);
            assertEquals(navigation.count() - 1,last.navigation(local).index());
        }
    }

    private static GovernanceWorkspaceData snapshot(List<Ballot> cards,int anchor,boolean completed) {
        var base = GovernanceWorkspaceScenesTest.data();
        int batch = GovernanceMatterWindow.HISTORY_BATCH_SIZE;
        int offset = completed ? anchor / batch * batch : 0;
        return new GovernanceWorkspaceData(List.of(),completed ? List.of() : cards,List.of(),
                completed ? cards.subList(offset,Math.min(cards.size(),offset + batch)) : List.of(),
                completed ? cards.size() : 0,offset,List.of(),base.active(),base.participation(),Set.of(),false);
    }

    private static List<Ballot> cards(int count,boolean completed) {
        var now = GovernanceWorkspaceScenesTest.NOW;
        return java.util.stream.IntStream.range(0,count).mapToObj(index -> new Ballot(new GovernanceVote(index + 1,
                new VoteProposal.Appointment(new UUID(0,index + 1),"Long_Player_Name_" + index),now.minusSeconds(500000),
                now.plusSeconds(completed ? index - count : index),completed ? VoteStatus.PASSED : VoteStatus.OPEN,16,4,new VoteTally(4,1,1),
                completed ? now : null,VoteFailure.NONE,null),false,null)).toList();
    }
}
