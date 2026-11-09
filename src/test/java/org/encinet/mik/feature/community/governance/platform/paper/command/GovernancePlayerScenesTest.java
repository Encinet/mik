package org.encinet.mik.feature.community.governance.platform.paper.command;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernancePlayerState.*;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernancePlayerTestSupport.*;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class GovernancePlayerScenesTest {
    @Test void sharedReportsKeepWrittenFieldsAndRevealTheAccountIdOnlyInManagementViews() throws Exception {
        var source = data(FIRST,1);
        var pause = ((GovernancePlayerReport.Eligibility.Promotion) source.eligibility()).value().activePause();
        for (var language : Language.values()) {
            var reports = new GovernancePlayerReport(text(language));
            var management = plain(reports.render(language,source,true));
            var personal = plain(reports.render(language,source,false));
            assertTrue(management.contains(FIRST.toString())); assertFalse(personal.contains(FIRST.toString()));
            for (var value : List.of(management,personal)) {
                assertTrue(value.contains(source.profile().playerName()));
                assertTrue(value.contains(pause.reason())); assertTrue(value.contains(pause.appealPath()));
            }
        }
    }

    @Test void changingEarlierFieldsOrLanguageKeepsTheSelectedWrittenDetailsBlock() throws Exception {
        var original = data(FIRST,1);
        var changed = GovernancePlayerReport.Data.promoting(new org.encinet.mik.feature.community.governance.membership.profile.PlayerProfile(
                        FIRST,"Changed_Player_Name",original.profile().firstJoinedAt(),null,original.profile().lastSuccessfulLoginAt()),
                original.participation(),((GovernancePlayerReport.Eligibility.Promotion) original.eligibility()).value());
        var position = new UiReadingPosition(UiKey.named("pause-details"),17,11);
        for (var language : new Language[]{Language.EN_US,Language.DE_DE,Language.ZH_CN}) for (var source : List.of(original,changed)) {
            var scenes = new GovernancePlayerScenes(text(language));
            var scene = scenes.render(language,new Loaded(source,position),_ -> {},(_,_) -> {});
            var frame = SceneGeometryTestSupport.readableFrame(scene,1.8,new FloatingMenuFieldOfView(30),1.3);
            assertNotNull(element(frame,"pause-details"));
            assertEquals(UiKey.named("pause-details"),frame.pagination().getFirst().readingPosition().orElseThrow().block().orElseThrow());
        }
    }

    @Test void completeReportsAndWrittenPauseFieldsStayReadableWithPersistentActionsAtAllPreferences() throws Exception {
        for (var language : Language.values()) {
            var text = text(language); var scenes = new GovernancePlayerScenes(text); var reports = new GovernancePlayerReport(text);
            for (double font : new double[]{.7,1.8}) for (double scale : new double[]{.7,1.3}) for (int fov : new int[]{30,110})
                for (int mode = 0; mode < 3; mode++) {
                    var data = data(FIRST,mode); var current = new AtomicReference<>(new Loaded(data,UiReadingPosition.START));
                    String expected = withoutSpacing(plain(reports.render(language,data,true)));
                    var actual = new StringBuilder(); var invoked = new ArrayList<GovernancePlayerScenes.Command>(); boolean complete = false;
                    for (int pass = 0; pass < 128; pass++) {
                        var source = current.get();
                        var scene = scenes.render(language,source,position -> current.set((Loaded) read(current.get(),source,position)),
                                (_,command) -> invoked.add(command));
                        var frame = SceneGeometryTestSupport.readableFrame(scene,font,new FloatingMenuFieldOfView(fov),scale);
                        assertDisjoint(frame);
                        assertNotNull(element(frame,"password-security")); assertNotNull(element(frame,"refresh")); assertNotNull(element(frame,"back"));
                        for (var item : frame.elements()) if (item.path().startsWith(frame.readingRegion().path() + "/")) actual.append(plain(item.node().content().label()));
                        if (mode < 2) assertNotNull(element(frame,mode == 0 ? "pause" : "resume"));
                        else assertTrue(frame.elements().stream().noneMatch(item -> Set.of("pause","resume").contains(item.node().key().value())));
                        if (pass == 0) {
                            assertTrue(invoked.isEmpty(),"Building and measuring a report cannot execute a management action");
                            entry(scene,frame,"password-security").trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
                            assertEquals(List.of(GovernancePlayerScenes.Command.PASSWORD),invoked);
                        }
                        var page = frame.pagination().getFirst();
                        if (!page.page().hasNext()) { complete = true; break; }
                        page.action(FloatingMenuInteraction.SCROLL_DOWN).execute(null,null,FloatingMenuInteraction.SCROLL_DOWN);
                    }
                    assertTrue(complete,language + " mode=" + mode);
                    assertEquals(expected,withoutSpacing(actual.toString()),language + " @ " + font + " FOV=" + fov + " mode=" + mode);
                    if (mode == 1) {
                        var pause = ((GovernancePlayerReport.Eligibility.Promotion) data.eligibility()).value().activePause();
                        assertTrue(withoutSpacing(actual.toString()).contains(withoutSpacing(pause.reason())));
                        assertTrue(withoutSpacing(actual.toString()).contains(withoutSpacing(pause.appealPath())));
                    }
                }
        }
    }

    @Test void loadingAndFailuresRemainReadableAndOnlyFailuresOfferRetry() throws Exception {
        for (var language : Language.values()) {
            var scenes = new GovernancePlayerScenes(text(language));
            for (double font : new double[]{.7,1.8}) for (double scale : new double[]{.7,1.3}) for (int fov : new int[]{30,110}) {
                for (GovernancePlayerState state : List.of(new Loading(FIRST,UiReadingPosition.START),
                        new Failed(FIRST,UiReadingPosition.START,Message.GOVERNANCE_ROLE_LOOKUP_ERROR),
                        new Failed(FIRST,UiReadingPosition.START,Message.GOVERNANCE_OPERATION_ERROR))) {
                    var invoked = new ArrayList<GovernancePlayerScenes.Command>();
                    var scene = scenes.render(language,state,_ -> fail("Status has no reading cursor"),(_,command) -> invoked.add(command));
                    var frame = SceneGeometryTestSupport.readableFrame(scene,font,new FloatingMenuFieldOfView(fov),scale);
                    assertDisjoint(frame); assertNotNull(element(frame,"back"));
                    assertNotNull(element(frame,state instanceof Loading ? "status" : "error"));
                    if (state instanceof Failed) {
                        entry(scene,frame,"refresh").trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
                        assertEquals(List.of(GovernancePlayerScenes.Command.REFRESH),invoked);
                    } else assertNull(entry(scene,frame,"refresh"));
                }
            }
        }
    }

    @Test void eachTargetKeepsItsActionIdentityAndBindingWhenAnotherTargetIsBuilt() throws Exception {
        var scenes = new GovernancePlayerScenes(text(Language.EN_US)); var invoked = new ArrayList<UUID>();
        var first = scenes.render(Language.EN_US,new Loaded(data(FIRST,0),UiReadingPosition.START),_ -> {},(_,_) -> invoked.add(FIRST));
        var second = scenes.render(Language.EN_US,new Loaded(data(SECOND,0),UiReadingPosition.START),_ -> {},(_,_) -> invoked.add(SECOND));
        assertNotEquals(entry(first,"password-security").id(),entry(second,"password-security").id());
        entry(first,"pause").trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
        entry(second,"pause").trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
        assertEquals(List.of(FIRST,SECOND),invoked);
    }
}
