package org.encinet.mik.feature.community.governance.platform.paper.command;

import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.encinet.mik.feature.community.governance.membership.activity.ActiveMemberEligibility;
import org.encinet.mik.feature.community.governance.membership.participation.ParticipationSummary;
import org.encinet.mik.feature.community.governance.membership.profile.PlayerProfile;
import org.encinet.mik.feature.community.governance.membership.promotion.PromotionEligibility;
import org.encinet.mik.feature.community.governance.membership.promotion.PromotionPause;
import org.encinet.mik.feature.community.governance.platform.paper.GovernanceText;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.Message;

/** Pure, current-language rendering shared by chat reports and the spatial player reader. */
final class GovernancePlayerReport {
    sealed interface Eligibility {
        record Promotion(PromotionEligibility value) implements Eligibility { public Promotion { Objects.requireNonNull(value); } }
        record Active(ActiveMemberEligibility value) implements Eligibility { public Active { Objects.requireNonNull(value); } }
    }
    record Data(PlayerProfile profile,ParticipationSummary participation,Eligibility eligibility) {
        Data { Objects.requireNonNull(profile); Objects.requireNonNull(participation); Objects.requireNonNull(eligibility); }
        static Data promoting(PlayerProfile profile,ParticipationSummary participation,PromotionEligibility value) {
            return new Data(profile,participation,new Eligibility.Promotion(value));
        }
        static Data active(PlayerProfile profile,ParticipationSummary participation,ActiveMemberEligibility value) {
            return new Data(profile,participation,new Eligibility.Active(value));
        }
    }

    private final GovernanceText text;
    GovernancePlayerReport(GovernanceText text) { this.text = Objects.requireNonNull(text); }

    record Block(String key,Component text) {
        Block { Objects.requireNonNull(key); Objects.requireNonNull(text); }
    }

    Component render(Language language,Data snapshot,boolean managementView) {
        return Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(),
                blocks(language,snapshot,managementView).stream().map(Block::text).toList());
    }

    java.util.List<Block> blocks(Language language,Data snapshot,boolean managementView) {
        var player = snapshot.profile();
        var blocks = new java.util.ArrayList<Block>();
        blocks.add(new Block("player-name",Component.text(text.t(language,Message.GOVERNANCE_PLAYER_HEADER,player.playerName()),NamedTextColor.GOLD)));
        if (managementView) blocks.add(new Block("player-id",Component.text(text.t(language,Message.GOVERNANCE_PLAYER_ID,player.playerId()),NamedTextColor.WHITE)));
        blocks.add(new Block("first-join",Component.text(text.t(language,Message.GOVERNANCE_PLAYER_FIRST_JOIN,text.time(language,player.firstJoinedAt())),NamedTextColor.WHITE)));
        blocks.add(new Block("last-login",Component.text(text.t(language,Message.GOVERNANCE_PLAYER_LAST_LOGIN,text.time(language,player.lastSuccessfulLoginAt())),NamedTextColor.WHITE)));
        blocks.add(new Block("member-since",Component.text(text.t(language,Message.GOVERNANCE_PLAYER_MEMBER_SINCE,
                player.memberSince() == null ? text.t(language,Message.GOVERNANCE_NOT_MEMBER) : text.time(language,player.memberSince())),NamedTextColor.WHITE)));
        switch (snapshot.eligibility()) {
            case Eligibility.Promotion(var promotion) -> promoting(blocks,language,snapshot.participation(),promotion);
            case Eligibility.Active(var active) -> active(blocks,language,snapshot.participation(),active);
        }
        return java.util.List.copyOf(blocks);
    }

    private void promoting(java.util.List<Block> blocks,Language language,ParticipationSummary participation,PromotionEligibility promotion) {
        blocks.add(new Block("promotion-heading",Component.text(text.t(language,Message.GOVERNANCE_PROMOTION_HEADER),NamedTextColor.GOLD)));
        blocks.add(new Block("account-age",text.line(language,promotion.accountAgeMet(),Message.GOVERNANCE_PROMOTION_ACCOUNT_AGE)));
        blocks.add(new Block("lifetime-playtime",text.line(language,promotion.playtimeMet(),Message.GOVERNANCE_PROMOTION_PLAYTIME,text.hours(language,participation.lifetimeSeconds()))));
        blocks.add(new Block("lifetime-days",text.line(language,promotion.playDaysMet(),Message.GOVERNANCE_PROMOTION_PLAY_DAYS,participation.lifetimePlayDays())));
        blocks.add(new Block("no-active-ban",text.line(language,promotion.noActiveBan(),Message.GOVERNANCE_NO_ACTIVE_BAN)));
        blocks.add(new Block("no-active-pause",text.line(language,promotion.noActivePause(),Message.GOVERNANCE_NO_ACTIVE_PAUSE)));
        if (promotion.activePause() != null) {
            PromotionPause pause = promotion.activePause();
            blocks.add(new Block("pause-details",Component.text(text.t(language,Message.GOVERNANCE_PAUSE_DETAILS,
                    pause.reason(),text.time(language,pause.endsAt()),pause.appealPath()),NamedTextColor.YELLOW)));
        }
    }

    private void active(java.util.List<Block> blocks,Language language,ParticipationSummary participation,ActiveMemberEligibility active) {
        blocks.add(new Block("active-heading",Component.text(text.t(language,Message.GOVERNANCE_ACTIVE_HEADER),NamedTextColor.GOLD)));
        blocks.add(new Block("current-member",text.line(language,active.currentMember(),Message.GOVERNANCE_ACTIVE_MEMBER)));
        blocks.add(new Block("member-age",text.line(language,active.memberAgeMet(),Message.GOVERNANCE_ACTIVE_MEMBER_AGE)));
        blocks.add(new Block("recent-playtime",text.line(language,active.playtimeMet(),Message.GOVERNANCE_ACTIVE_PLAYTIME,text.hours(language,participation.secondsLast60Days()))));
        blocks.add(new Block("participation-days-60",text.line(language,active.participationDaysMet(),Message.GOVERNANCE_ACTIVE_DAYS_60,participation.participationDaysLast60())));
        blocks.add(new Block("participation-days-30",text.line(language,active.recentDaysMet(),Message.GOVERNANCE_ACTIVE_DAYS_30,participation.participationDaysLast30())));
        blocks.add(new Block("no-active-ban",text.line(language,active.noActiveBan(),Message.GOVERNANCE_NO_ACTIVE_BAN)));
    }
}
