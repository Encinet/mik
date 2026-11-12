package org.encinet.mik.feature.community.governance.platform.paper.command;

import org.encinet.mik.shared.menu.ui.UiReadingPosition;

import java.util.function.BiFunction;
import java.time.Instant;
import org.encinet.mik.feature.community.governance.membership.activity.ActiveMemberEligibility;
import org.encinet.mik.feature.community.governance.membership.participation.ParticipationSummary;
import org.encinet.mik.feature.community.governance.membership.promotion.PromotionEligibility;
import org.encinet.mik.feature.community.governance.membership.promotion.PromotionPause;
import org.encinet.mik.feature.community.governance.platform.paper.GovernanceText;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.ui.UiNode;
import org.encinet.mik.shared.role.PlayerRole;

/** The actual feature-owned identity content used by host integration geometry tests. */
public final class GovernanceIdentityTestSupport {
    private GovernanceIdentityTestSupport() { }
    public static UiNode content(Language language, BiFunction<Message,Object[],String> translate, boolean member) {
        return content(language,translate,new GovernanceIdentityPanel.IdentityView(member ? PlayerRole.MEMBER : PlayerRole.DEFAULT,
                new ParticipationSummary(99_999,30,80_000,18,9),member ? null : new PromotionEligibility(true,true,true,true,true,null),
                member ? new ActiveMemberEligibility(true,true,true,true,true,true,java.time.Duration.ofHours(6)) : null));
    }
    public static UiNode pausedContent(Language language, BiFunction<Message,Object[],String> translate) {
        var start = Instant.parse("2026-10-28T00:00:00Z");
        var pause = new PromotionPause(1,"理".repeat(160),start,start.plusSeconds(259_200),"https://example.test/"+"a".repeat(60),"moderator");
        return content(language,translate,new GovernanceIdentityPanel.IdentityView(PlayerRole.DEFAULT,
                new ParticipationSummary(99_999,30,80_000,18,9),new PromotionEligibility(true,true,true,true,false,pause),null));
    }
    private static UiNode content(Language language, BiFunction<Message,Object[],String> translate, GovernanceIdentityPanel.IdentityView view) {
        var text = new GovernanceText(new LanguageService(null) {
            @Override public String t(Language selected,Message message,Object... args) { return translate.apply(message,args); }
        });
        return GovernanceIdentityPanel.definition(text, language, view, false, UiReadingPosition.START, _->{}).content();
    }
}
