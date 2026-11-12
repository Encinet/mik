package org.encinet.mik.feature.community.governance.platform.paper.command;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.feature.community.governance.membership.activity.ActiveMemberEligibility;
import org.encinet.mik.feature.community.governance.membership.participation.ParticipationSummary;
import org.encinet.mik.feature.community.governance.platform.paper.GovernanceText;
import org.encinet.mik.feature.community.governance.roster.StaffMember;
import org.encinet.mik.feature.community.governance.removal.RemovalPetition;
import org.encinet.mik.feature.community.governance.voting.model.*;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.encinet.mik.shared.role.PlayerRole;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernanceWorkspaceState.*;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernanceWorkspaceData.*;
import static org.junit.jupiter.api.Assertions.*;

class GovernanceWorkspaceScenesTest {
    static final UUID VIEWER = new UUID(0,99), STAFF = new UUID(0,1);
    static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");
    static final ActiveMemberEligibility ACTIVE = new ActiveMemberEligibility(true,true,true,true,true,true,java.time.Duration.ofHours(6));

    @Test void deferredRowsKeepTheirDeclaredInteractionSnapshotAfterTheOwnerChanges() throws Exception {
        var state = new GovernanceWorkspaceState();
        var view = view(Language.EN_US,state,data(),new AtomicReference<>());
        var declared = view.render(null,false);
        state.busy = true;
        var first = SceneGeometryTestSupport.measure(declared,1);
        assertTrue(element(first,"staff:" + STAFF).node().input().enabled());
        var updated = SceneGeometryTestSupport.measure(view.render(null,false),1);
        assertFalse(element(updated,"staff:" + STAFF).node().input().enabled());
    }

    @Test void pendingHistoryAndRetryStatesRetainReadableStatusAtAllPreferenceExtremes() throws Exception {
        for (var language : Language.values()) for (double font : new double[]{.7,1.8}) for (double scale : new double[]{.7,1.3})
            for (int fov : new int[]{30,110}) for (boolean failed : new boolean[]{false,true}) {
                var state = new GovernanceWorkspaceState(); state.section(Section.MATTERS); state.filter(Filter.COMPLETED);
                var scene = view(language,state,null,new AtomicReference<>()).render(null,failed);
                var frame = SceneGeometryTestSupport.readableFrame(scene,font,new FloatingMenuFieldOfView(fov),scale);
                assertDisjoint(frame); assertNotNull(element(frame,"status")); assertNotNull(element(frame,"refresh")); assertNotNull(element(frame,"close"));
                assertTrue(frame.readingRegion().path().endsWith("/status"));
            }
    }

    @Test void largeStaffDirectoriesPageByMeasuredCapacityAndEveryPublishedActionKeepsItsPerson() throws Exception {
        var people = java.util.stream.IntStream.range(0,300).mapToObj(index -> new StaffMember(new UUID(0,index + 1),
                "Long_Player_Name" + index,PlayerRole.MODERATOR,true)).toList();
        var original = data();
        var data = new GovernanceWorkspaceData(people,List.of(),List.of(),List.of(),0,0,List.of(),
                original.active(),original.participation(),Set.of(),false);
        var state = new GovernanceWorkspaceState();
        var action = new AtomicReference<GovernanceWorkspaceAction>();
        var view = view(Language.DE_DE,state,data,action);
        var seen = new HashSet<UUID>();
        int count = 1;
        for (int page = 0; page < count; page++) {
            var scene = view.render(null,false);
            var frame = SceneGeometryTestSupport.readableFrame(scene,1.8,new FloatingMenuFieldOfView(30),1.3);
            var paging = frame.pagination().getFirst(); count = paging.page().count();
            assertEquals(page,paging.page().index()); assertDisjoint(frame);
            assertNotNull(element(frame,"identity")); assertNotNull(element(frame,"role-header"));
            var published = scene.measured(frame);
            for (var row : frame.elements()) if (row.node().key().value().startsWith("staff:")) {
                UUID id = UUID.fromString(row.node().key().value().substring("staff:".length()));
                assertTrue(seen.add(id));
                published.entries().get(row.path()).trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
                var selected = assertInstanceOf(GovernanceWorkspaceAction.SelectStaff.class,action.get());
                assertEquals(id,selected.id()); assertEquals(row.node().key(),selected.source());
            }
            if (paging.page().hasNext()) {
                paging.selectPage().accept(paging.page().next().index());
                state.staffPage = assertInstanceOf(GovernanceWorkspaceAction.StaffPage.class,action.get()).index();
            }
        }
        assertEquals(people.size(),seen.size()); assertTrue(count > 1);
        var compact = new GovernanceWorkspaceData(people.subList(0,1),List.of(),List.of(),List.of(),0,0,List.of(),
                original.active(),original.participation(),Set.of(),false);
        var shrunk = SceneGeometryTestSupport.measure(view(Language.DE_DE,state,compact,action).render(null,false),1);
        assertEquals(0,shrunk.pagination().getFirst().page().index());
        assertNotNull(element(shrunk,"staff:" + people.getFirst().id()));
    }

    @Test void staffToolbarSharesSingleLineHeightAndCentersToolsWithinEachWrappedRow() throws Exception {
        for (var language : Language.values()) for (var scale : FloatingMenuTextScale.values()) {
            var state = new GovernanceWorkspaceState();
            var action = new AtomicReference<GovernanceWorkspaceAction>();
            var member = view(language, state, data(), action);
            var scene = new GovernanceWorkspaceScenes(member.text, language,
                    new GovernanceWorkspaceScenes.Viewer(VIEWER, PlayerRole.CUSTODIAN, true), state, data(), action::set, _ -> false)
                    .render(null, false);
            var frame = SceneGeometryTestSupport.measure(scene, scale.factor());
            assertDisjoint(frame);
            var refresh = element(frame, "refresh");
            for (String key : List.of("identity", "players", "close")) {
                var control = element(frame, key);
                assertEquals(owningSurface(frame, refresh), owningSurface(frame, control), "tools share the bottom toolbar");
                if (key.equals("close") || SceneGeometryTestSupport.textLines(scene, scale.factor(), key) == 1)
                    assertEquals(refresh.size().height(), control.size().height(), 1e-6, language+" @ "+scale+" "+key);
                else assertTrue(control.size().height() >= refresh.size().height(), "Multiline labels keep their natural height");
            }
            var controls = List.of("identity", "players", "refresh", "close").stream().map(key -> element(frame, key)).toList();
            assertTrue(element(frame, "identity").y() > element(frame, "staff:" + STAFF).y(), "identity stays below the directory");
            for (var first : controls) for (var second : controls) {
                if (first.y() < second.y()+second.size().height() && second.y() < first.y()+first.size().height())
                    assertEquals(first.y()+first.size().height()/2, second.y()+second.size().height()/2, 1e-6);
            }
            for (var expected : Map.of("identity", GovernanceWorkspaceAction.Command.IDENTITY,
                    "players", GovernanceWorkspaceAction.Command.PLAYERS, "refresh", GovernanceWorkspaceAction.Command.REFRESH,
                    "close", GovernanceWorkspaceAction.Command.CLOSE).entrySet()) {
                entry(scene, expected.getKey()).trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
                assertEquals(expected.getValue(), action.get());
            }
        }
    }

    @Test void directoryIsTheDefaultAndStaffRowsOpenOneExplicitCompanion() throws Exception {
        var state = new GovernanceWorkspaceState();
        var action = new AtomicReference<GovernanceWorkspaceAction>();
        var scene = view(Language.ZH_CN, state, data(), action).render(null,false);
        var frame = SceneGeometryTestSupport.measure(scene,1);
        var published = scene.measured(frame);
        assertNotNull(entry(published,frame,"staff:"+STAFF));
        assertNull(entry(scene,"choice:YES"));
        assertNull(entry(scene,"selected-vote"));
        var row = entry(published,frame,"staff:"+STAFF);
        assertEquals(FloatingMenuElementStyle.TEXT,row.style());
        row.triggers().get(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
        assertInstanceOf(GovernanceWorkspaceAction.SelectStaff.class,action.get());
        var selected = (GovernanceWorkspaceAction.SelectStaff) action.get();
        state.popup = new Popup(selected.source(),selected.id(),Intent.DETAILS,false);
        var detail = view(Language.ZH_CN,state,data(),action).render(null,false);
        assertNotNull(detail.popover());
        assertFalse(detail.popover().modal());
        assertNotNull(entry(detail,"related"));
        assertNull(entry(detail,"choice:YES"));
        entry(detail,"sponsor").triggers().get(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
        assertInstanceOf(GovernanceWorkspaceAction.OpenPopup.class,action.get());
        state.popup = state.popup.confirm(Intent.SPONSOR);
        assertTrue(view(Language.ZH_CN,state,data(),action).render(null,false).popover().modal());
    }

    @Test void liveVotesExposeOnlyParticipationAndTheRequestersChoiceAndPendingDeliveryIsExplicit() throws Exception {
        var state = new GovernanceWorkspaceState(); state.section(Section.MATTERS);
        state.selectedMatter = "vote:1";
        assertTrue(state.readMatter(state.detailRevision,"vote:1",new UiReadingPosition(Optional.empty(),0,Integer.MAX_VALUE),data()));
        var scene = view(Language.EN_US,state,data(),new AtomicReference<>()).render(null,false);
        String live = plain(entry(scene,"progress").label());
        assertTrue(live.contains("6"));
        assertFalse(live.contains("4"));
        assertTrue(plain(entry(scene,"own-ballot").label()).contains("Yes"));
        var frame = SceneGeometryTestSupport.measure(scene,1);
        var measured = scene.measured(frame);
        assertTrue(entry(measured,frame,"choice:YES").selected());
        assertFalse(entry(measured,frame,"choice:NO").selected());
        state.filter(Filter.COMPLETED);
        var completed = view(Language.EN_US,state,data(),new AtomicReference<>()).render(null,false);
        assertTrue(plain(entry(completed,"delivery").label()).contains("awaiting"));
        assertNull(entry(completed,"choice:YES"));
        assertTrue(plain(entry(completed,"result").label()).contains("4"));
    }

    @Test void translatedPagesAndCompanionsFitAllTypographySizesWithoutChangingTheParentFrame() throws Exception {
        for (var language : Language.values()) for (var scale : FloatingMenuTextScale.values()) {
            var state = new GovernanceWorkspaceState();
            var view = view(language,state,data(),new AtomicReference<>());
            var base = SceneGeometryTestSupport.measure(view.render(null,false),scale.factor());
            assertDisjoint(base);
            var compact = new GovernanceWorkspaceData(data().staff().subList(0,1),List.of(),List.of(),List.of(),0,0,List.of(),ACTIVE,new ParticipationSummary(21600,6,21600,6,2),Set.of(),false);
            for (var identity : List.of(
                    GovernanceIdentityTestSupport.content(language,(message,args) -> view.text.t(language,message,args),true),
                    GovernanceIdentityTestSupport.pausedContent(language,(message,args) -> view.text.t(language,message,args)))) {
                var compactScene = view(language,new GovernanceWorkspaceState(),compact,new AtomicReference<>()).render(null,false)
                        .toBuilder().popover(MenuPopover.attached(UiKey.named("identity"), Component.text("Details"), identity, Component.text("Close"))).build();
                for (int fov : new int[] {30,70,110}) assertNotNull(assertDoesNotThrow(() ->
                        SceneGeometryTestSupport.popoverFrame(compactScene,scale.factor(),new FloatingMenuFieldOfView(fov)),language+" compact @ "+scale+" FOV "+fov));
            }
            state.popup = new Popup(UiKey.named("staff:"+STAFF),STAFF,Intent.DETAILS,false);
            var detail = view.render(null,false);
            var parent = SceneGeometryTestSupport.measure(detail,scale.factor());
            assertEquals(base.size(),parent.size(),language+" @ "+scale);
            for (int fov : new int[] {30,70,110}) {
                var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.popoverFrame(detail,scale.factor(),new FloatingMenuFieldOfView(fov)),language+" @ "+scale+" FOV "+fov);
                assertNotNull(frame); assertDisjoint(frame);
            }
            state.popup = state.popup.confirm(Intent.SPONSOR);
            assertDisjoint(SceneGeometryTestSupport.popoverFrame(view.render(null,false),scale.factor(),FloatingMenuFieldOfView.DEFAULT));
            state.popup = null;
            state.section(Section.MATTERS);
            var scene = view.render(null,false);
            assertDisjoint(assertDoesNotThrow(() -> SceneGeometryTestSupport.measure(scene,scale.factor()),language+" matters @ "+scale));
        }
    }

    static GovernanceWorkspaceScenes view(Language language, GovernanceWorkspaceState state, GovernanceWorkspaceData data,
                                           AtomicReference<GovernanceWorkspaceAction> action) throws Exception {
        var bundle = FluentTestSupport.bundle(language);
        var languages = new LanguageService(null) {
            @Override public String t(Language selected,Message message,Object... args) { return FluentTestSupport.text(bundle,message,args); }
        };
        return new GovernanceWorkspaceScenes(new GovernanceText(languages),language,
                new GovernanceWorkspaceScenes.Viewer(VIEWER,PlayerRole.MEMBER,true),state,data,action::set,_ -> false);
    }
    static GovernanceWorkspaceData data() {
        var people = java.util.stream.IntStream.range(0,8).mapToObj(index -> new StaffMember(new UUID(0,index+1),
                "Long_Player_Name"+index,index==0?PlayerRole.MODERATOR:PlayerRole.CUSTODIAN,index==0)).toList();
        var proposal = new VoteProposal.Appointment(STAFF,"Long_Player_Name0");
        var open = new GovernanceVote(1,proposal,NOW,NOW.plusSeconds(259_200),VoteStatus.OPEN,16,4,new VoteTally(4,1,1),null,VoteFailure.NONE,null);
        var past = new GovernanceVote(2,proposal,NOW.minusSeconds(500_000),NOW.minusSeconds(240_800),VoteStatus.PASSED,16,4,
                new VoteTally(4,1,1),NOW.minusSeconds(240_800),VoteFailure.NONE,null);
        var petition = new RemovalPetition(1,new UUID(0,2),"Long_Player_Name1",NOW,NOW.plusSeconds(259_200),3,1,null);
        return new GovernanceWorkspaceData(people,List.of(new Ballot(open,true,VoteChoice.YES)),List.of(new Petition(petition,false)),
                List.of(new Ballot(past,true,VoteChoice.ABSTAIN)),1,0,List.of(past),ACTIVE,new ParticipationSummary(21600,6,21600,6,2),Set.of(2L),false);
    }
    private static String plain(Component component) { return PlainTextComponentSerializer.plainText().serialize(component); }
}
