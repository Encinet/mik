package org.encinet.mik.module.governance.platform.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.encinet.mik.module.governance.GovernanceException;
import org.encinet.mik.module.governance.voting.model.GovernanceVote;
import org.encinet.mik.module.governance.voting.model.VoteChoice;
import org.encinet.mik.module.governance.voting.model.VoteFailure;
import org.encinet.mik.module.governance.voting.model.VoteKind;
import org.encinet.mik.module.governance.voting.model.VoteProposal;
import org.encinet.mik.module.governance.voting.model.VoteStatus;
import org.encinet.mik.module.governance.voting.model.VoteTerminationReason;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.function.Function;

/** Recipient-specific text for governance commands and durable announcements. */
public final class GovernanceText {
    private static final ZoneId ZONE = ZoneId.of("Asia/Singapore");
    private final LanguageService languages;

    public GovernanceText(LanguageService languages) {
        this.languages = languages;
    }

    public Language language(CommandSender sender) {
        return sender instanceof Player player ? languages.language(player) : Language.DEFAULT;
    }

    public String t(Language language, Message message, Object... args) {
        return languages.t(language, message, args);
    }

    public void send(CommandSender sender, Message message, NamedTextColor color, Object... args) {
        sender.sendMessage(Component.text(t(language(sender), message, args), color));
    }

    public void broadcast(Message message, NamedTextColor color, Object... args) {
        broadcast(language -> t(language, message, args), color);
    }

    public void broadcast(Function<Language, String> content, NamedTextColor color) {
        for (Player recipient : Bukkit.getOnlinePlayers()) {
            recipient.sendMessage(Component.text(content.apply(languages.language(recipient)), color));
        }
        Bukkit.getConsoleSender().sendMessage(Component.text(content.apply(Language.DEFAULT), color));
    }

    public String time(Language language, Instant instant) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withLocale(language.locale()).withZone(ZONE).format(instant);
    }

    public String hours(Language language, long seconds) {
        NumberFormat number = NumberFormat.getNumberInstance(language.locale());
        number.setMaximumFractionDigits(1);
        number.setMinimumFractionDigits(1);
        return t(language, Message.GOVERNANCE_HOURS, number.format(seconds / 3600.0D));
    }

    public String duration(Language language, Duration duration) {
        long seconds = duration.toSeconds();
        if (seconds % 86_400 == 0) return t(language, Message.GOVERNANCE_DAYS, seconds / 86_400);
        if (seconds % 3_600 == 0) return t(language, Message.GOVERNANCE_HOURS, seconds / 3_600);
        if (seconds % 60 == 0) return t(language, Message.GOVERNANCE_MINUTES, seconds / 60);
        return t(language, Message.GOVERNANCE_SECONDS, seconds);
    }

    public Component line(Language language, boolean ok, Message message, Object... args) {
        return Component.text(ok ? "✔  " : "✘  ",
                        ok ? NamedTextColor.GREEN : NamedTextColor.RED)
                .append(Component.text(t(language, message, args), NamedTextColor.WHITE));
    }

    public String kind(Language language, VoteKind kind) {
        return t(language, switch (kind) {
            case APPOINTMENT -> Message.GOVERNANCE_KIND_APPOINTMENT;
            case REMOVAL -> Message.GOVERNANCE_KIND_REMOVAL;
            case BAN -> Message.GOVERNANCE_KIND_BAN;
        });
    }

    public String choice(Language language, VoteChoice choice) {
        return t(language, switch (choice) {
            case YES -> Message.GOVERNANCE_CHOICE_YES;
            case NO -> Message.GOVERNANCE_CHOICE_NO;
            case ABSTAIN -> Message.GOVERNANCE_CHOICE_ABSTAIN;
        });
    }

    public String result(Language language, GovernanceVote vote) {
        String outcome = switch (vote.status()) {
            case PASSED -> t(language, Message.GOVERNANCE_RESULT_PASSED);
            case FAILED -> t(language, Message.GOVERNANCE_RESULT_FAILED,
                    failure(language, vote.failure()));
            case TERMINATED -> t(language, Message.GOVERNANCE_RESULT_TERMINATED,
                    termination(language, vote.terminationReason()));
            case OPEN -> t(language, Message.GOVERNANCE_RESULT_OPEN);
        };
        if (vote.status() == VoteStatus.OPEN) return outcome;
        return t(language, Message.GOVERNANCE_RESULT_TALLY, outcome,
                vote.eligibleVoters(), vote.tally().participating(),
                vote.tally().yes(), vote.tally().no(), vote.tally().abstain());
    }

    public NamedTextColor resultColor(GovernanceVote vote) {
        return vote.status() == VoteStatus.PASSED ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
    }

    public String proposalDetails(Language language, VoteProposal proposal) {
        if (!(proposal instanceof VoteProposal.Ban ban)) return null;
        return t(language, Message.GOVERNANCE_BAN_DETAILS, ban.proposerName(),
                duration(language, ban.duration()), ban.reason());
    }

    public String resultProposalSuffix(Language language, VoteProposal proposal) {
        if (!(proposal instanceof VoteProposal.Ban ban)) return "";
        return t(language, Message.GOVERNANCE_BAN_SUFFIX,
                duration(language, ban.duration()), ban.reason());
    }

    public String voteResult(Language language, GovernanceVote vote) {
        return t(language, Message.GOVERNANCE_RESULT_ANNOUNCEMENT,
                vote.id(), kind(language, vote.kind()), vote.subjectName(),
                result(language, vote), resultProposalSuffix(language, vote.proposal()));
    }

    public Message error(GovernanceException.Code code) {
        return switch (code) {
            case PLAYER_UNKNOWN -> Message.GOVERNANCE_ERROR_PLAYER_UNKNOWN;
            case PAUSE_END_INVALID -> Message.GOVERNANCE_ERROR_PAUSE_END_INVALID;
            case PAUSE_ALREADY_ACTIVE -> Message.GOVERNANCE_ERROR_PAUSE_ALREADY_ACTIVE;
            case PAUSE_ALREADY_MEMBER -> Message.GOVERNANCE_ERROR_PAUSE_ALREADY_MEMBER;
            case VOTERS_TOO_FEW -> Message.GOVERNANCE_ERROR_VOTERS_TOO_FEW;
            case VOTE_SUBJECT_UNKNOWN -> Message.GOVERNANCE_ERROR_VOTE_SUBJECT_UNKNOWN;
            case VOTE_ALREADY_OPEN -> Message.GOVERNANCE_ERROR_VOTE_ALREADY_OPEN;
            case VOTE_COOLDOWN -> Message.GOVERNANCE_ERROR_VOTE_COOLDOWN;
            case VOTE_UNKNOWN -> Message.GOVERNANCE_ERROR_VOTE_UNKNOWN;
            case VOTE_CLOSED -> Message.GOVERNANCE_ERROR_VOTE_CLOSED;
            case NOT_VOTER -> Message.GOVERNANCE_ERROR_NOT_VOTER;
            case VOTE_TERMINATION_CLOSED -> Message.GOVERNANCE_ERROR_VOTE_TERMINATION_CLOSED;
            case CANDIDATE_INELIGIBLE -> Message.GOVERNANCE_ERROR_CANDIDATE_INELIGIBLE;
            case REMOVAL_REQUIRES_PETITION -> Message.GOVERNANCE_ERROR_REMOVAL_REQUIRES_PETITION;
            case BAN_SELF -> Message.GOVERNANCE_ERROR_BAN_SELF;
            case BAN_PROPOSER_INELIGIBLE -> Message.GOVERNANCE_ERROR_BAN_PROPOSER_INELIGIBLE;
            case BAN_ALREADY_ACTIVE -> Message.GOVERNANCE_ERROR_BAN_ALREADY_ACTIVE;
            case TERMINATION_REASON_INVALID -> Message.GOVERNANCE_ERROR_TERMINATION_REASON_INVALID;
            case REMOVAL_SELF -> Message.GOVERNANCE_ERROR_REMOVAL_SELF;
            case REMOVAL_SPONSOR_INELIGIBLE -> Message.GOVERNANCE_ERROR_REMOVAL_SPONSOR_INELIGIBLE;
            case REMOVAL_SPONSORS_TOO_FEW -> Message.GOVERNANCE_ERROR_REMOVAL_SPONSORS_TOO_FEW;
            case REMOVAL_VOTE_OPEN -> Message.GOVERNANCE_ERROR_REMOVAL_VOTE_OPEN;
            case NOMINATION_NOT_OPEN -> Message.GOVERNANCE_ERROR_NOMINATION_NOT_OPEN;
        };
    }

    private String failure(Language language, VoteFailure failure) {
        return t(language, switch (failure) {
            case QUORUM -> Message.GOVERNANCE_FAILURE_QUORUM;
            case MINIMUM_YES -> Message.GOVERNANCE_FAILURE_MINIMUM_YES;
            case TWO_THIRDS_NON_ABSTAINING -> Message.GOVERNANCE_FAILURE_TWO_THIRDS;
            case MAJORITY_OF_ALL_BALLOTS -> Message.GOVERNANCE_FAILURE_MAJORITY;
            case NONE -> Message.GOVERNANCE_FAILURE_NONE;
        });
    }

    private String termination(Language language, VoteTerminationReason reason) {
        if (reason == null) return t(language, Message.GOVERNANCE_TERMINATION_UNKNOWN);
        return t(language, switch (reason) {
            case CANDIDATE_WITHDREW -> Message.GOVERNANCE_TERMINATION_WITHDREW;
            case CANDIDATE_INELIGIBLE -> Message.GOVERNANCE_TERMINATION_INELIGIBLE;
            case MODERATOR_RESIGNED -> Message.GOVERNANCE_TERMINATION_RESIGNED;
            case MODERATOR_AUTO_REMOVED -> Message.GOVERNANCE_TERMINATION_INACTIVE;
        });
    }
}
