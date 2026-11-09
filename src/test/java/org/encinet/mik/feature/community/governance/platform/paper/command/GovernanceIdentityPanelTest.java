package org.encinet.mik.feature.community.governance.platform.paper.command;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.community.governance.membership.activity.ActiveMemberEligibility;
import org.encinet.mik.feature.community.governance.membership.participation.ParticipationSummary;
import org.encinet.mik.feature.community.governance.membership.promotion.PromotionEligibility;
import org.encinet.mik.feature.community.governance.platform.paper.GovernanceText;
import org.encinet.mik.feature.community.governance.platform.paper.command.GovernanceIdentityPanel.*;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.role.PlayerRole;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class GovernanceIdentityPanelTest {
    private static final ActiveMemberEligibility ACTIVE = new ActiveMemberEligibility(true, true, true, true, true, true);

    @Test void identityAndProgressUseTheSameLoadedSnapshotWhenPermissionsChange() throws Exception {
        var text = text(Language.ZH_CN);
        var participation = new ParticipationSummary(0, 0, 0, 0, 0);
        var promotion = new PromotionEligibility(false, false, false, true, true, null);
        var snapshot = new IdentityView(PlayerRole.DEFAULT, participation, promotion, null);
        var scene = GovernanceIdentityPanel.definition(text, Language.ZH_CN, snapshot, false, UiReadingPosition.START, _ -> {});
        var plain = PlainTextComponentSerializer.plainText();
        assertEquals("新玩家", plain.serialize(entry(scene, "current-role").label()));
        assertTrue(plain.serialize(entry(scene, "status:0").label()).contains(text.t(Language.ZH_CN,Message.GOVERNANCE_PROMOTION_ACCOUNT_AGE)));

        var refreshed = GovernanceIdentityPanel.definition(text, Language.ZH_CN, new IdentityView(PlayerRole.MODERATOR, participation, null, ACTIVE), false, UiReadingPosition.START, _ -> {});
        assertEquals("管理", plain.serialize(entry(refreshed, "current-role").label()));
        assertTrue(plain.serialize(entry(refreshed, "status:0").label()).contains(text.t(Language.ZH_CN,Message.GOVERNANCE_ACTIVE_MEMBER_AGE)));
    }

    @Test void identityRemainsReadableInAllLanguagesAndTextSizes() throws Exception {
        var participation = new ParticipationSummary(99_999, 30, 80_000, 18, 9);
        var promotion = new PromotionEligibility(true, true, true, true, true, null);
        for (var language : Language.values()) for (var scale : FloatingMenuTextScale.values()) {
            var text = text(language);
            for (boolean moderator : new boolean[] {false, true}) {
                var scene = GovernanceIdentityPanel.definition(text, language, new IdentityView(
                        moderator ? PlayerRole.MODERATOR : PlayerRole.DEFAULT, participation,
                        moderator ? null : promotion, moderator ? ACTIVE : null), false, UiReadingPosition.START, _ -> {});
                assertDisjoint(SceneGeometryTestSupport.measure(scene, scale.factor()));
                assertTrue(scene.titleVisible(), "the host owns the common title and close row");
                assertNull(entry(scene, "close"));
            }
        }
    }

    @Test void everyQualificationAndLongPauseFieldCanBeReadCompletelyAcrossActualPages() throws Exception {
        var start = java.time.Instant.parse("2026-10-28T00:00:00Z");
        var pause = new org.encinet.mik.feature.community.governance.membership.promotion.PromotionPause(
                1,"说明 Review note ".repeat(24),start,start.plusSeconds(259_200),"https://example.test/" + "appeal-path-".repeat(12),"moderator");
        var view = new IdentityView(PlayerRole.DEFAULT,new ParticipationSummary(99_999,30,80_000,18,9),
                new PromotionEligibility(true,true,true,true,false,pause),null);
        var plain = PlainTextComponentSerializer.plainText();
        for (var locale : Language.values()) {
            var text = text(locale);
            for (double typography : new double[]{.7,1.8}) for (double scale : new double[]{.7,1.3}) for (int fov : new int[]{30,110}) {
                var position = new java.util.concurrent.atomic.AtomicReference<>(UiReadingPosition.START);
                var expected = new java.util.LinkedHashMap<String,String>();
                var read = new java.util.HashMap<String,StringBuilder>();
                var initial = GovernanceIdentityPanel.definition(text,locale,view,false,position.get(),position::set);
                for (var node : elements(initial).values()) {
                    String key = node.key().value();
                    if (key.startsWith("status:") || key.startsWith("pause-")) expected.put(key,plain.serialize(node.content().label()));
                }
                int count = 1;
                for (int index = 0; index < count; index++) {
                    assertTrue(index < 64,"Reading must progress through a bounded source");
                    var scene = GovernanceIdentityPanel.definition(text,locale,view,false,position.get(),position::set);
                    var frame = SceneGeometryTestSupport.readableFrame(scene,typography,new FloatingMenuFieldOfView(fov),scale);
                    assertDisjoint(frame); assertNotNull(element(frame,"current-role"));
                    var paging = frame.pagination().getFirst(); count = paging.page().count();
                    assertEquals(index,paging.page().index());
                    for (var node : frame.elements()) if (expected.containsKey(node.node().key().value()))
                        read.computeIfAbsent(node.node().key().value(),_ -> new StringBuilder()).append(plain.serialize(node.node().content().label()));
                    if (paging.page().hasNext()) paging.selectPage().accept(paging.page().next().index());
                }
                assertEquals(expected.keySet(),read.keySet());
                expected.forEach((key,value) -> assertEquals(value.replaceAll("\\s+",""),read.get(key).toString().replaceAll("\\s+",""),locale + ": " + key));
            }
        }
    }

    private static GovernanceText text(Language language) throws Exception {
        var bundle = FluentTestSupport.bundle(language);
        var languages = new LanguageService(null) {
            @Override public Language language(Player player) { return language; }
            @Override public String t(Language selected, Message message, Object... args) {
                return FluentTestSupport.text(bundle, message, args);
            }
        };
        return new GovernanceText(languages);
    }

}
