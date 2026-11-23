use super::{DbResult, delivery, players, rules, tenures};
use mik_protocol::wire::{
    ChatPlayer, GovernanceBallot, GovernanceVoteView,
    governance_game_grant::Permission,
    governance_result::Outcome,
    governance_vote_view::{Failure, Status, Termination},
};
use tokio_rusqlite::rusqlite::{Connection, OptionalExtension, params};

pub(super) fn open(
    db: &Connection,
    player: &str,
    tenure: Option<i64>,
    electorate: &[String],
    now: i64,
) -> DbResult<i64> {
    db.execute(
        "INSERT INTO gov_votes(player_id,tenure_id,subject_name,opens_ms,closes_ms,quorum)
         SELECT player_id,?2,display_name,?3,?4,?5 FROM gov_players WHERE player_id=?1",
        params![
            player,
            tenure,
            now,
            now + rules::VOTE_WINDOW,
            rules::quorum(electorate.len())
        ],
    )?;
    let id = db.last_insert_rowid();
    let mut insert =
        db.prepare_cached("INSERT INTO gov_electors(vote_id,player_id) VALUES (?1,?2)")?;
    for elector in electorate {
        insert.execute(params![id, elector])?;
    }
    Ok(id)
}

pub(super) fn open_vote(db: &Connection, player: &str, removal: bool) -> DbResult<Option<i64>> {
    Ok(db.query_row(
        "SELECT id FROM gov_votes WHERE player_id=?1 AND (tenure_id IS NOT NULL)=?2 AND status=1",
        params![player, removal], |row| row.get(0),
    ).optional()?)
}

pub(super) fn cooling_down(
    db: &Connection,
    player: &str,
    tenure: Option<i64>,
    now: i64,
) -> DbResult<bool> {
    Ok(db.query_row(
        "SELECT EXISTS(SELECT 1 FROM gov_votes WHERE player_id=?1 AND tenure_id IS ?2
          AND failure=1 AND finalized_ms>?3)",
        params![player, tenure, now - rules::VOTE_WINDOW],
        |row| row.get(0),
    )?)
}

pub(super) fn apply(db: &Connection, actor: &str, now: i64) -> DbResult<Result<i64, Outcome>> {
    let Some(profile) = players::profile(db, actor, now)? else {
        return Ok(Err(Outcome::UnknownPlayer));
    };
    if !profile.has_password {
        return Ok(Err(Outcome::PasswordRequired));
    }
    if db.query_row(
        "SELECT password_removal IS NOT NULL FROM gov_players WHERE player_id=?1",
        [actor],
        |row| row.get::<_, bool>(0),
    )? {
        return Ok(Err(Outcome::PasswordRemovalPending));
    }
    if profile.moderator || profile.maintainer || !profile.active_member {
        return Ok(Err(Outcome::CandidateIneligible));
    }
    if open_vote(db, actor, false)?.is_some() {
        return Ok(Err(Outcome::VoteAlreadyOpen));
    }
    if cooling_down(db, actor, None, now)? {
        return Ok(Err(Outcome::VoteCooldown));
    }
    let electorate = players::electorate(db, now)?;
    if electorate.len() < 2 {
        return Ok(Err(Outcome::VotersTooFew));
    }
    Ok(Ok(open(db, actor, None, &electorate, now)?))
}

pub(super) fn cast(
    db: &Connection,
    vote: i64,
    actor: &str,
    ballot: i32,
    now: i64,
) -> DbResult<Outcome> {
    if GovernanceBallot::try_from(ballot)
        .map_or(true, |choice| choice == GovernanceBallot::Unspecified)
    {
        return Err(super::Error::Invalid("valid ballot required"));
    }
    let Some(open) = db
        .query_row(
            "SELECT status=1 AND opens_ms<=?2 AND closes_ms>?2 FROM gov_votes WHERE id=?1",
            params![vote, now],
            |row| row.get::<_, bool>(0),
        )
        .optional()?
    else {
        return Ok(Outcome::VoteUnknown);
    };
    if !open {
        return Ok(Outcome::VoteClosed);
    }
    let count = db.execute(
        "UPDATE gov_electors SET choice=?3 WHERE vote_id=?1 AND player_id=?2",
        params![vote, actor, ballot],
    )?;
    Ok(if count == 0 {
        Outcome::NotVoter
    } else {
        Outcome::Done
    })
}

pub(super) fn withdraw(db: &Connection, actor: &str, expected: i64, now: i64) -> DbResult<Outcome> {
    let Some(vote) = open_vote(db, actor, false)?.filter(|id| expected == 0 || expected == *id)
    else {
        return Ok(Outcome::NominationNotOpen);
    };
    let changed = db.execute(
        "UPDATE gov_votes SET status=4,finalized_ms=?2,termination=?3 WHERE id=?1 AND closes_ms>?2",
        params![vote, now, i32::from(Termination::CandidateWithdrew)],
    )?;
    Ok(if changed == 0 {
        Outcome::NominationNotOpen
    } else {
        Outcome::Done
    })
}

pub(super) fn settle(db: &Connection, now: i64) -> DbResult<()> {
    let mut statement = db.prepare_cached("SELECT id,player_id,tenure_id,closes_ms,quorum FROM gov_votes WHERE status=1 ORDER BY closes_ms,id")?;
    let open = statement
        .query_map([], |row| {
            Ok((
                row.get::<_, i64>(0)?,
                row.get::<_, String>(1)?,
                row.get::<_, Option<i64>>(2)?,
                row.get::<_, i64>(3)?,
                row.get::<_, u32>(4)?,
            ))
        })?
        .collect::<Result<Vec<_>, _>>()?;
    for (id, player, tenure, closes, quorum) in open {
        if tenure.is_none() {
            let candidate = players::profile(db, &player, now)?
                .ok_or(super::Error::Invalid("vote player missing"))?;
            if candidate.banned.is_none() {
                continue;
            }
            if !candidate.active_member
                || !candidate.has_password
                || candidate.moderator
                || candidate.maintainer
            {
                db.execute(
                    "UPDATE gov_votes SET status=4,finalized_ms=?2,termination=?3 WHERE id=?1",
                    params![id, now, i32::from(Termination::CandidateIneligible)],
                )?;
                continue;
            }
        }
        if closes > now {
            continue;
        }
        let (eligible, yes, no, abstain) = db.query_row(
            "SELECT count(*),count(*) FILTER (WHERE choice=1),count(*) FILTER (WHERE choice=2),
                    count(*) FILTER (WHERE choice=3) FROM gov_electors WHERE vote_id=?1",
            [id],
            |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?, row.get(3)?)),
        )?;
        let failure = rules::outcome(eligible, quorum, yes, no, abstain);
        let status = if failure == Failure::None {
            Status::Passed
        } else {
            Status::Failed
        };
        db.execute(
            "UPDATE gov_votes SET status=?2,finalized_ms=?3,failure=?4 WHERE id=?1",
            params![id, i32::from(status), now, i32::from(failure)],
        )?;
        if status != Status::Passed {
            continue;
        }
        if let Some(tenure) = tenure {
            tenures::end(db, tenure, &player, "vote", None, now, Some(id))?;
        } else {
            db.execute(
                "INSERT INTO gov_tenures(player_id,started_ms,source) VALUES (?1,?2,?3)",
                params![player, now, format!("vote:{id}")],
            )?;
            delivery::request(db, &player, Permission::Moderator, true, now, Some(id))?;
        }
    }
    Ok(())
}

#[derive(Clone, Copy)]
pub(super) enum Selection {
    Vote,
    Open,
    Finished,
    Announcements,
}

pub(super) fn list(
    db: &Connection,
    selection: Selection,
    subject: &str,
    id: i64,
    viewer: &str,
    limit: u32,
    offset: u32,
) -> DbResult<Vec<GovernanceVoteView>> {
    let condition = match selection {
        Selection::Vote => "v.id=?2",
        Selection::Open => "v.status=1 AND (?1='' OR v.player_id=?1)",
        Selection::Finished => "v.status<>1 AND (?1='' OR v.player_id=?1)",
        Selection::Announcements => "v.finalized_ms IS NOT NULL AND v.announcement_delivered=0",
    };
    let mut statement = db.prepare_cached(&format!(
        "WITH selected AS (SELECT v.* FROM gov_votes v WHERE {condition} ORDER BY v.id DESC LIMIT ?4 OFFSET ?5)
         SELECT v.id,v.player_id,v.subject_name,v.tenure_id,v.opens_ms,v.closes_ms,v.status,count(e.player_id),v.quorum,
                count(e.choice),count(*) FILTER (WHERE v.status<>1 AND e.choice=1),
                count(*) FILTER (WHERE v.status<>1 AND e.choice=2),count(*) FILTER (WHERE v.status<>1 AND e.choice=3),
                v.finalized_ms,v.failure,v.termination,
                count(*) FILTER (WHERE e.player_id=?3)>0,coalesce(max(e.choice) FILTER (WHERE e.player_id=?3),0),
                v.status=2 AND EXISTS(SELECT 1 FROM gov_game_grants g WHERE g.vote_id=v.id
                 AND g.confirmed_ms IS NULL AND g.superseded_ms IS NULL)
         FROM selected v LEFT JOIN gov_electors e ON e.vote_id=v.id GROUP BY v.id ORDER BY v.id DESC"
    ))?;
    Ok(statement
        .query_map(params![subject, id, viewer, limit, offset], |row| {
            Ok(GovernanceVoteView {
                id: row.get(0)?,
                subject: Some(ChatPlayer {
                    id: row.get(1)?,
                    name: row.get(2)?,
                }),
                tenure_id: row.get(3)?,
                opens_ms: row.get(4)?,
                closes_ms: row.get(5)?,
                status: row.get(6)?,
                eligible_voters: row.get(7)?,
                quorum: row.get(8)?,
                participating: row.get(9)?,
                yes: row.get(10)?,
                no: row.get(11)?,
                abstain: row.get(12)?,
                finalized_ms: row.get(13)?,
                failure: row.get(14)?,
                termination: row.get(15)?,
                eligible_to_vote: row.get(16)?,
                own_ballot: row.get(17)?,
                grant_pending: row.get(18)?,
            })
        })?
        .collect::<Result<_, _>>()?)
}
