use super::{DbResult, delivery, players, rules};
use mik_protocol::wire::{governance_game_grant::Permission, governance_vote_view::Termination};
use tokio_rusqlite::rusqlite::{Connection, OptionalExtension, params};

pub(super) fn current(db: &Connection, player: &str) -> DbResult<Option<i64>> {
    Ok(db
        .query_row(
            "SELECT id FROM gov_tenures WHERE player_id=?1 AND ended_ms IS NULL",
            [player],
            |row| row.get(0),
        )
        .optional()?)
}

pub(super) fn end(
    db: &Connection,
    tenure: i64,
    player: &str,
    reason: &str,
    termination: Option<Termination>,
    now: i64,
    vote: Option<i64>,
) -> DbResult<()> {
    if db.execute(
        "UPDATE gov_tenures SET ended_ms=?2,end_reason=?3 WHERE id=?1 AND ended_ms IS NULL",
        params![tenure, now, reason],
    )? == 0
    {
        return Ok(());
    }
    if let Some(termination) = termination {
        db.execute("UPDATE gov_votes SET status=4,finalized_ms=?2,termination=?3 WHERE tenure_id=?1 AND status=1",
            params![tenure, now, i32::from(termination)])?;
    }
    delivery::request(db, player, Permission::Moderator, false, now, vote)?;
    Ok(())
}

pub(super) fn review(db: &Connection, now: i64) -> DbResult<()> {
    if !players::facts_current(db, now)? {
        return Ok(());
    }
    let today = rules::day(now);
    if db.execute(
        "INSERT INTO gov_schedule VALUES (1,?1) ON CONFLICT(singleton) DO UPDATE SET tenure_check_day=excluded.tenure_check_day
         WHERE tenure_check_day<excluded.tenure_check_day", [today],
    )? == 0 { return Ok(()); }
    let mut statement = db.prepare_cached(
        "SELECT t.id,t.player_id FROM gov_tenures t JOIN gov_players p ON p.player_id=t.player_id
         WHERE t.ended_ms IS NULL AND p.last_login_ms<?1 ORDER BY p.last_login_ms,t.id
         LIMIT (SELECT max(0,count(*)-4) FROM gov_tenures WHERE ended_ms IS NULL)",
    )?;
    let inactive = statement
        .query_map([now - 180 * rules::DAY], |row| {
            Ok((row.get::<_, i64>(0)?, row.get::<_, String>(1)?))
        })?
        .collect::<Result<Vec<_>, _>>()?;
    for (id, player) in inactive {
        end(
            db,
            id,
            &player,
            "inactivity",
            Some(Termination::ModeratorAutoRemoved),
            now,
            None,
        )?;
    }
    Ok(())
}
