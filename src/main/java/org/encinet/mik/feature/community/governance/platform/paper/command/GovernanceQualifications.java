package org.encinet.mik.feature.community.governance.platform.paper.command;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.encinet.mik.feature.community.governance.membership.activity.ActiveMemberEligibility;
import org.encinet.mik.feature.community.governance.membership.participation.ParticipationSummary;
import org.encinet.mik.feature.community.governance.platform.paper.GovernanceText;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.Message;

/** The identity view and application use the same evaluated requirements and translated progress. */
final class GovernanceQualifications {
    record Requirement(boolean met, Component text) { }

    private GovernanceQualifications() { }

    static List<Requirement> active(GovernanceText text, Language language,
                                    ActiveMemberEligibility check, ParticipationSummary participation) {
        if (check == null || !check.currentMember()) return List.of(
                new Requirement(false, text.line(language, false, Message.GOVERNANCE_ACTIVE_MEMBER)));
        return List.of(
                new Requirement(check.memberAgeMet(), text.line(language, check.memberAgeMet(), Message.GOVERNANCE_ACTIVE_MEMBER_AGE)),
                new Requirement(check.playtimeMet(), text.line(language, check.playtimeMet(), Message.GOVERNANCE_ACTIVE_PLAYTIME,
                        text.hours(language, participation.secondsLast60Days()), text.hours(language, check.requiredPlaytime().toSeconds()))),
                new Requirement(check.participationDaysMet(), text.line(language, check.participationDaysMet(), Message.GOVERNANCE_ACTIVE_DAYS_60,
                        participation.participationDaysLast60())),
                new Requirement(check.recentDaysMet(), text.line(language, check.recentDaysMet(), Message.GOVERNANCE_ACTIVE_DAYS_30,
                        participation.participationDaysLast30())),
                new Requirement(check.noActiveBan(), text.line(language, check.noActiveBan(), Message.GOVERNANCE_NO_ACTIVE_BAN)));
    }
}
