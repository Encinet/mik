package org.encinet.mik.feature.community.governance.platform.paper.command;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.feature.community.governance.membership.activity.ActiveMemberEligibility;
import org.encinet.mik.feature.community.governance.membership.participation.ParticipationSummary;
import org.encinet.mik.feature.community.governance.voting.model.*;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.encinet.mik.shared.role.PlayerRole;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernanceWorkspaceScenesTest.*;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernanceWorkspaceState.*;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class GovernanceApplicationScenesTest {
    private static final ActiveMemberEligibility INACTIVE = new ActiveMemberEligibility(true, false, false, false, false, false, java.time.Duration.ofHours(6));

    @Test void anIneligibleApplicantCanOpenTheEntryAndSeeOnlyTheMissingRequirements() throws Exception {
        var state = new GovernanceWorkspaceState();
        var action = new AtomicReference<GovernanceWorkspaceAction>();
        var data = applicant(INACTIVE, false, false);
        var initial = scene(Language.ZH_CN, state, data, PlayerRole.MEMBER, true, action);
        assertTrue(entry(initial,"application").enabled());
        click(initial,"application");
        var opened = assertInstanceOf(GovernanceWorkspaceAction.OpenPopup.class,action.get());
        assertEquals(Intent.APPLICATION,opened.intent());
        state.popup = new Popup(opened.source(),opened.subject(),opened.intent(),false);

        var application = scene(Language.ZH_CN,state,data,PlayerRole.MEMBER,true,action);

        assertEquals("申请管理",plain(application.popover().title()));
        assertTrue(application.popover().modal());
        assertNotNull(entry(application,"requirement:0"));
        assertTrue(plain(entry(application,"requirement:1").label()).contains("近 60 天"));
        assertTrue(plain(entry(application,"requirement:2").label()).contains("当前 1"));
        assertNull(entry(application,"apply"));
        assertNull(entry(application,"set-password"));
        var newcomer = scene(Language.ZH_CN,state,applicant(null,false,false),PlayerRole.DEFAULT,true,action);
        assertTrue(plain(entry(newcomer,"requirement:0").label()).contains("成员身份"));
        assertNull(entry(newcomer,"requirement:1"),"new members first need membership, not an unrelated wall of activity checks");
    }

    @Test void settingAPasswordReturnsToTheSameApplicationAndStillRequiresExplicitSubmission() throws Exception {
        var state = application();
        var action = new AtomicReference<GovernanceWorkspaceAction>();
        var data = applicant(ACTIVE,false,false);
        var missing = scene(Language.ZH_CN,state,data,PlayerRole.MEMBER,false,action);
        assertNull(entry(missing,"apply"));
        click(missing,"set-password");
        assertEquals(GovernanceWorkspaceAction.Command.PASSWORD,action.get());
        var restored = state.navigationCopy();

        var ready = scene(Language.ZH_CN,restored,data,PlayerRole.MEMBER,true,action);

        assertNotNull(ready.popover());
        assertEquals(Intent.APPLICATION,restored.popup.intent());
        assertTrue(entry(ready,"apply").enabled());
        assertEquals("申请管理",plain(entry(ready,"apply").label()));
        assertNull(entry(ready,"set-password"));
        assertEquals(GovernanceWorkspaceAction.Command.PASSWORD,action.get(),"returning must not submit automatically");
        click(ready,"apply");
        assertEquals(GovernanceWorkspaceAction.Command.PERFORM,action.get());
        restored.busy = true;
        var submitting = scene(Language.ZH_CN,restored,data,PlayerRole.MEMBER,true,action);
        assertFalse(entry(submitting,"apply").enabled());
        assertFalse(entry(submitting,"application").enabled());
        assertFalse(entry(submitting,"refresh").enabled());
    }

    @Test void runningApplicationsOfferWithdrawalAndQueuedAppointmentsCannotBeSubmittedAgain() throws Exception {
        var state = new GovernanceWorkspaceState();
        var action = new AtomicReference<GovernanceWorkspaceAction>();
        var active = applicant(ACTIVE,false,true);
        var initial = scene(Language.ZH_CN,state,active,PlayerRole.MEMBER,true,action);
        assertEquals("撤回申请",plain(entry(initial,"application").label()));
        click(initial,"application");
        var opened = assertInstanceOf(GovernanceWorkspaceAction.OpenPopup.class,action.get());
        assertEquals(Intent.WITHDRAW,opened.intent());
        state.popup = new Popup(opened.source(),opened.subject(),opened.intent(),false);
        var withdrawal = scene(Language.ZH_CN,state,active,PlayerRole.MEMBER,true,action);
        assertEquals("撤回申请",plain(withdrawal.popover().title()));
        assertEquals("撤回申请",plain(entry(withdrawal,"confirm").label()));
        var ended = scene(Language.ZH_CN,state,applicant(ACTIVE,false,false),PlayerRole.MEMBER,true,action);
        assertNull(entry(ended,"confirm"));
        assertNotNull(entry(ended,"application-closed"));

        state = application();
        var pending = scene(Language.ZH_CN,state,applicant(ACTIVE,true,false),PlayerRole.MEMBER,true,action);
        assertEquals("申请状态",plain(entry(pending,"application").label()));
        assertTrue(plain(entry(pending,"application-status").label()).contains("等待任命"));
        assertNull(entry(pending,"apply")); assertNull(entry(pending,"confirm"));
    }

    @Test void personalContextAndApplicationPopoversFitEveryLanguageAndTextSize() throws Exception {
        for (var language : Language.values()) for (var scale : FloatingMenuTextScale.values()) {
            for (boolean ready : new boolean[] {false,true}) {
                var state = application();
                var action = new AtomicReference<GovernanceWorkspaceAction>();
                var data = applicant(ready ? ACTIVE : INACTIVE,false,false);
                var scene = scene(language,state,data,PlayerRole.MEMBER,ready,action);
                var parent = SceneGeometryTestSupport.measure(scene,scale.factor());
                assertDisjoint(parent);
                state.popup = null;
                assertEquals(parent.size(),SceneGeometryTestSupport.measure(scene(language,state,data,PlayerRole.MEMBER,ready,action),scale.factor()).size());
                for (int fov : new int[] {30,70,110}) {
                    var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.popoverFrame(scene,scale.factor(),new FloatingMenuFieldOfView(fov)),
                            language+" @ "+scale+" FOV "+fov+" ready "+ready);
                    assertNotNull(frame); assertDisjoint(frame);
                }
            }
        }
    }

    private static GovernanceWorkspaceState application() {
        var state = new GovernanceWorkspaceState();
        state.popup = new Popup(UiKey.named("application"),VIEWER,Intent.APPLICATION,false);
        return state;
    }

    private static GovernanceWorkspaceData applicant(ActiveMemberEligibility active,boolean pending,boolean open) {
        var original = data();
        var source = original.openVotes().getFirst();
        var vote = source.vote();
        var own = new GovernanceWorkspaceData.Ballot(new GovernanceVote(vote.id(),new VoteProposal.Appointment(VIEWER,"Viewer"),
                vote.opensAt(),vote.closesAt(),vote.status(),vote.eligibleVoters(),vote.quorumRequired(),vote.tally(),null,VoteFailure.NONE,null),true,null);
        var participation = active != null && active.eligible() ? new ParticipationSummary(21600,6,21600,6,2) : new ParticipationSummary(3600,1,3600,1,1);
        return new GovernanceWorkspaceData(original.staff(),open ? List.of(own) : List.of(),original.petitions(),original.history(),
                original.historyTotal(),original.historyOffset(),original.related(),active,participation,original.pendingVotes(),pending);
    }

    private static MenuScene scene(Language language,GovernanceWorkspaceState state,GovernanceWorkspaceData data,
                                   PlayerRole role,boolean password,AtomicReference<GovernanceWorkspaceAction> action) throws Exception {
        var base = view(language,state,data,action);
        return new GovernanceWorkspaceScenes(base.text,language,new GovernanceWorkspaceScenes.Viewer(VIEWER,role,password),
                state,data,action::set,_ -> false).render(null,false);
    }
    private static void click(MenuScene scene,String key) { entry(scene,key).trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY); }
    private static String plain(Component value) { return PlainTextComponentSerializer.plainText().serialize(value); }
}
