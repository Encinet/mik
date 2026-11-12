package org.encinet.mik.feature.community.governance.platform.paper.command;

import java.time.Instant;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.feature.community.governance.membership.activity.ActiveMemberEligibility;
import org.encinet.mik.feature.community.governance.membership.participation.ParticipationSummary;
import org.encinet.mik.feature.community.governance.membership.profile.PlayerProfile;
import org.encinet.mik.feature.community.governance.membership.promotion.*;
import org.encinet.mik.feature.community.governance.platform.paper.GovernanceText;
import org.encinet.mik.shared.i18n.*;

final class GovernancePlayerTestSupport {
    static final UUID FIRST = new UUID(0,1), SECOND = new UUID(0,2);
    static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");
    private GovernancePlayerTestSupport() { }

    static GovernancePlayerReport.Data data(UUID id,int mode) {
        var profile = new PlayerProfile(id,"Long_Player_Name",NOW.minusSeconds(86400L * 500),mode == 2 ? NOW.minusSeconds(86400L * 365) : null,NOW);
        var participation = new ParticipationSummary(360000,100,72000,60,30);
        if (mode == 2) return GovernancePlayerReport.Data.active(profile,participation,new ActiveMemberEligibility(true,true,true,true,true,true,java.time.Duration.ofHours(6)));
        var pause = mode == 1 ? new PromotionPause(1,"reason-".repeat(20) + " 👩‍👩‍👧‍👦 e\u0301",NOW.minusSeconds(60),NOW.plusSeconds(86400),
                "https://example.invalid/appeal/" + "申诉路径".repeat(20),"Staff") : null;
        return GovernancePlayerReport.Data.promoting(profile,participation,new PromotionEligibility(true,true,true,true,pause == null,pause));
    }

    static GovernanceText text(Language language) throws Exception {
        var bundle = FluentTestSupport.bundle(language);
        return new GovernanceText(new LanguageService(null) {
            @Override public String t(Language selected,Message message,Object... args) { return FluentTestSupport.text(bundle,message,args); }
        });
    }

    static String plain(Component value) { return PlainTextComponentSerializer.plainText().serialize(value); }
    static String withoutSpacing(String value) {
        var result = new StringBuilder();
        value.codePoints().filter(point -> !Character.isWhitespace(point) && !Character.isSpaceChar(point)).forEach(result::appendCodePoint);
        return result.toString();
    }
}
