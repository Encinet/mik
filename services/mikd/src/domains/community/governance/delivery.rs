use super::{DbResult, Error, canonical};
use mik_protocol::wire::{
    GovernanceDeliveryReceipt, GovernanceGameGrant, governance_game_grant::Permission,
};
use tokio_rusqlite::rusqlite::{Connection, OptionalExtension, Row, params};

pub(super) fn request(
    db: &Connection,
    player: &str,
    permission: Permission,
    enabled: bool,
    now: i64,
    vote: Option<i64>,
) -> DbResult<()> {
    db.execute(
        "UPDATE gov_game_grants SET superseded_ms=?3 WHERE player_id=?1 AND permission=?2 AND superseded_ms IS NULL",
        params![player, i32::from(permission), now],
    )?;
    db.execute(
        "INSERT INTO gov_game_grants(player_id,permission,enabled,requested_ms,vote_id) VALUES (?1,?2,?3,?4,?5)",
        params![player, i32::from(permission), enabled, now, vote],
    )?;
    Ok(())
}

pub(super) fn pending(db: &Connection, deferred: &[String]) -> DbResult<Vec<GovernanceGameGrant>> {
    if deferred.len() > 32 {
        return Err(Error::Invalid("too many deferred governance players"));
    }
    let players = deferred
        .iter()
        .map(|player| canonical(player))
        .collect::<DbResult<Vec<_>>>()?;
    let encoded = serde_json::to_string(&players)
        .map_err(|_| Error::Invalid("could not encode deferred governance players"))?;
    let mut statement = db.prepare_cached(
        "WITH deferred AS (SELECT value FROM json_each(?1)),
              work AS (
                SELECT id,player_id,permission,enabled FROM gov_game_grants
                WHERE confirmed_ms IS NULL AND superseded_ms IS NULL
                  AND player_id NOT IN (SELECT value FROM deferred)
                ORDER BY enabled,id LIMIT 32
              )
         SELECT * FROM work
         UNION ALL
         SELECT id,player_id,permission,enabled FROM gov_game_grants
         WHERE confirmed_ms IS NULL AND superseded_ms IS NULL AND enabled=0
           AND player_id IN (SELECT value FROM deferred)
         ORDER BY enabled,id",
    )?;
    Ok(statement
        .query_map([encoded], grant)?
        .collect::<Result<_, _>>()?)
}

pub(super) fn targets(db: &Connection, player: &str) -> DbResult<Vec<GovernanceGameGrant>> {
    let mut statement = db.prepare_cached(
        "SELECT id,player_id,permission,enabled FROM gov_game_grants
         WHERE player_id=?1 AND confirmed_ms IS NULL AND superseded_ms IS NULL
         ORDER BY enabled,id",
    )?;
    Ok(statement
        .query_map([player], grant)?
        .collect::<Result<_, _>>()?)
}

pub(super) fn confirm(
    db: &Connection,
    receipt: GovernanceDeliveryReceipt,
    now: i64,
) -> DbResult<Vec<GovernanceGameGrant>> {
    if receipt.grants.len() > 2 || receipt.announcements.len() > 32 {
        return Err(Error::Invalid("too many governance receipts"));
    }
    let mut confirm = db.prepare_cached(
        "UPDATE gov_game_grants SET confirmed_ms=coalesce(confirmed_ms,?2)
         WHERE id=?1 AND superseded_ms IS NULL",
    )?;
    let mut current = db.prepare_cached(
        "SELECT target.id,target.player_id,target.permission,target.enabled
         FROM gov_game_grants attempted JOIN gov_game_grants target USING (player_id,permission)
         WHERE attempted.id=?1 AND target.superseded_ms IS NULL",
    )?;
    let mut replacements = Vec::new();
    for id in receipt.grants {
        if confirm.execute(params![id, now])? == 0 {
            replacements.push(
                current
                    .query_row([id], grant)
                    .optional()?
                    .ok_or(Error::Invalid("unknown governance grant generation"))?,
            );
        }
    }
    let mut announce = db.prepare_cached(
        "UPDATE gov_votes SET announcement_delivered=1 WHERE id=?1 AND finalized_ms IS NOT NULL",
    )?;
    for id in receipt.announcements {
        announce.execute([id])?;
    }
    Ok(replacements)
}

fn grant(row: &Row<'_>) -> tokio_rusqlite::rusqlite::Result<GovernanceGameGrant> {
    Ok(GovernanceGameGrant {
        id: row.get(0)?,
        player_id: row.get(1)?,
        permission: row.get(2)?,
        enabled: row.get(3)?,
    })
}
