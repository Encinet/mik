use super::{DbResult, players, rules, tenures, votes};
use mik_protocol::wire::{ChatPlayer, GovernancePetitionView, governance_result::Outcome};
use std::collections::HashSet;
use tokio_rusqlite::rusqlite::{Connection, OptionalExtension, params};

pub(super) fn sponsor(
    db: &Connection,
    player: &str,
    actor: &str,
    expected: i64,
    now: i64,
) -> DbResult<Result<GovernancePetitionView, Outcome>> {
    if player == actor {
        return Ok(Err(Outcome::RemovalSelf));
    }
    let Some(tenure) = tenures::current(db, player)? else {
        return Ok(Err(Outcome::RemovalTargetNotModerator));
    };
    let electorate = players::electorate(db, now)?;
    if !electorate.iter().any(|player| player == actor) {
        return Ok(Err(Outcome::RemovalSponsorIneligible));
    }
    let others = electorate
        .iter()
        .filter(|elector| *elector != player)
        .map(String::as_str)
        .collect::<HashSet<_>>();
    let required = match others.len() {
        0 | 1 => return Ok(Err(Outcome::RemovalSponsorsTooFew)),
        2 => 2_u32,
        _ => 3,
    };
    if votes::open_vote(db, player, true)?.is_some() {
        return Ok(Err(Outcome::RemovalVoteOpen));
    }
    if votes::cooling_down(db, player, Some(tenure), now)? {
        return Ok(Err(Outcome::VoteCooldown));
    }
    let active: Option<i64> = db
        .query_row(
            "SELECT id FROM gov_petitions WHERE tenure_id=?1 AND expires_ms>?2 AND vote_id IS NULL",
            params![tenure, now],
            |row| row.get(0),
        )
        .optional()?;
    if expected != 0 && active != Some(expected) {
        return Ok(Err(Outcome::RemovalPetitionClosed));
    }
    let petition = if let Some(id) = active {
        db.execute(
            "UPDATE gov_petitions SET required=?2 WHERE id=?1",
            params![id, required],
        )?;
        id
    } else {
        db.execute("INSERT INTO gov_petitions(tenure_id,created_ms,expires_ms,required) VALUES (?1,?2,?3,?4)",
            params![tenure, now, now + rules::VOTE_WINDOW, required])?;
        db.last_insert_rowid()
    };
    let newly_sponsored = db.execute(
        "INSERT OR IGNORE INTO gov_sponsors VALUES (?1,?2)",
        params![petition, actor],
    )? != 0;
    let mut list = db.prepare_cached("SELECT player_id FROM gov_sponsors WHERE petition_id=?1")?;
    let sponsors = list
        .query_map([petition], |row| row.get::<_, String>(0))?
        .collect::<Result<Vec<_>, _>>()?;
    let mut count = 0;
    let mut remove =
        db.prepare_cached("DELETE FROM gov_sponsors WHERE petition_id=?1 AND player_id=?2")?;
    for sponsor in sponsors {
        if others.contains(sponsor.as_str()) {
            count += 1;
        } else {
            remove.execute(params![petition, sponsor])?;
        }
    }
    if count >= required {
        let vote = votes::open(db, player, Some(tenure), &electorate, now)?;
        db.execute(
            "UPDATE gov_petitions SET vote_id=?2 WHERE id=?1",
            params![petition, vote],
        )?;
    }
    let mut result = list_views(db, player, actor, petition, now, 1)?
        .pop()
        .ok_or(super::Error::Invalid("petition missing"))?;
    result.newly_sponsored = newly_sponsored;
    Ok(Ok(result))
}

pub(super) fn list_views(
    db: &Connection,
    subject: &str,
    viewer: &str,
    id: i64,
    now: i64,
    limit: u32,
) -> DbResult<Vec<GovernancePetitionView>> {
    let mut statement = db.prepare_cached(
        "SELECT p.id,t.player_id,a.display_name,p.created_ms,p.expires_ms,p.required,count(s.player_id),p.vote_id,
                count(*) FILTER (WHERE s.player_id=?2)>0
         FROM gov_petitions p JOIN gov_tenures t ON t.id=p.tenure_id JOIN gov_players a ON a.player_id=t.player_id
         LEFT JOIN gov_sponsors s ON s.petition_id=p.id
         WHERE (?1='' OR t.player_id=?1) AND ((?3<>0 AND p.id=?3) OR
               (?3=0 AND p.expires_ms>?4 AND p.vote_id IS NULL AND t.ended_ms IS NULL))
         GROUP BY p.id ORDER BY p.id DESC LIMIT ?5",
    )?;
    Ok(statement
        .query_map(params![subject, viewer, id, now, limit], |row| {
            Ok(GovernancePetitionView {
                id: row.get(0)?,
                subject: Some(ChatPlayer {
                    id: row.get(1)?,
                    name: row.get(2)?,
                }),
                created_ms: row.get(3)?,
                expires_ms: row.get(4)?,
                sponsors_required: row.get(5)?,
                sponsor_count: row.get(6)?,
                vote_id: row.get(7)?,
                own_sponsorship: row.get(8)?,
                newly_sponsored: false,
            })
        })?
        .collect::<Result<_, _>>()?)
}
