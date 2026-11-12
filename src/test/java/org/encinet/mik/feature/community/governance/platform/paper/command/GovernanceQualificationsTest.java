package org.encinet.mik.feature.community.governance.platform.paper.command;

import org.encinet.mik.feature.community.governance.membership.activity.ActiveMembershipPolicy;
import org.encinet.mik.feature.community.governance.membership.participation.ParticipationSummary;
import org.encinet.mik.shared.i18n.Language;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernancePlayerTestSupport.FIRST;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernancePlayerTestSupport.data;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernancePlayerTestSupport.plain;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernancePlayerTestSupport.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GovernanceQualificationsTest {
    @Test
    void eligibilityViewsShowTheThresholdFromTheirEvaluationInEveryLanguage() throws Exception {
        var participation = new ParticipationSummary(100_000, 20, Duration.ofHours(9).toSeconds(), 6, 2);
        for (var date : List.of(LocalDate.parse("2026-06-24"), LocalDate.parse("2026-06-25"))) {
            int requiredHours = date.getDayOfMonth() == 24 ? 6 : 12;
            var now = date.atTime(12, 0).toInstant(ZoneOffset.UTC);
            var check = ActiveMembershipPolicy.evaluate(true, now.minus(Duration.ofDays(100)),
                    participation, false, now, date);
            var snapshot = GovernancePlayerReport.Data.active(data(FIRST, 2).profile(), participation, check);

            for (var language : Language.values()) {
                var text = text(language);
                String qualification = plain(GovernanceQualifications.active(text, language, check, participation).get(1).text());
                String report = plain(new GovernancePlayerReport(text).blocks(language, snapshot, false).stream()
                        .filter(block -> block.key().equals("recent-playtime")).findFirst().orElseThrow().text());

                assertEquals(qualification, report, language.id());
                assertTrue(qualification.contains(text.hours(language, Duration.ofHours(requiredHours).toSeconds())), language.id());
                assertTrue(qualification.contains(text.hours(language, participation.secondsLast60Days())), language.id());
                assertTrue(qualification.startsWith(requiredHours == 6 ? "✔" : "✘"), language.id());
            }
        }
    }
}
