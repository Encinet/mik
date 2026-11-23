use mik_protocol::wire::governance_vote_view::Failure;
use time::{Date, Month, UtcOffset};

pub(super) const DAY: i64 = 86_400_000;
pub(super) const VOTE_WINDOW: i64 = 3 * DAY;
const SINGAPORE_OFFSET_MS: i64 = 8 * 3_600_000;

pub(super) fn day(ms: i64) -> i64 {
    (ms + SINGAPORE_OFFSET_MS).div_euclid(DAY)
}

pub(super) fn end_of_day(ms: i64) -> i64 {
    (day(ms) + 1) * DAY - SINGAPORE_OFFSET_MS
}

pub(super) fn required_activity(date: Date) -> i64 {
    let month_day = (date.month(), date.day());
    if month_day <= (Month::March, 15)
        || ((Month::June, 25)..=(Month::September, 15)).contains(&month_day)
    {
        12 * 3600
    } else {
        6 * 3600
    }
}

pub(super) fn required_activity_at(ms: i64) -> i64 {
    time::OffsetDateTime::from_unix_timestamp(ms.div_euclid(1000)).map_or(6 * 3600, |date| {
        required_activity(
            date.to_offset(UtcOffset::from_hms(8, 0, 0).unwrap_or(UtcOffset::UTC))
                .date(),
        )
    })
}

pub(super) fn quorum(eligible: usize) -> usize {
    if eligible <= 3 {
        2
    } else {
        eligible.div_ceil(4).max(3)
    }
}

pub(super) fn outcome(eligible: u32, quorum: u32, yes: u32, no: u32, abstain: u32) -> Failure {
    let participation = yes + no + abstain;
    if participation < quorum {
        Failure::Quorum
    } else if yes < if eligible >= 4 { 3 } else { 2 } {
        Failure::MinimumYes
    } else if u64::from(yes) * 3 < (u64::from(yes) + u64::from(no)) * 2 {
        Failure::TwoThirdsNonAbstaining
    } else if u64::from(yes) * 2 <= u64::from(participation) {
        Failure::MajorityOfAllBallots
    } else {
        Failure::None
    }
}
