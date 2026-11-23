use super::{DbResult, canonical, delivery, rules};
use mik_protocol::wire::{
    ChatPlayer, GovernanceBanSnapshot, GovernanceCredential, GovernanceObservation,
    GovernancePasswordRemoval, GovernanceProfile, GovernancePromotionPause, GovernanceStaffEntry,
    governance_game_grant::Permission, governance_result::Outcome,
};
use tokio_rusqlite::rusqlite::{Connection, OptionalExtension, params};

const BAN_MAX_AGE_MS: i64 = 120_000;

pub(super) fn facts_current(db: &Connection, now: i64) -> DbResult<bool> {
    Ok(db.query_row(
        "SELECT EXISTS(SELECT 1 FROM gov_ban_snapshot WHERE observed_ms>=?1)",
        [now - BAN_MAX_AGE_MS],
        |row| row.get(0),
    )?)
}

pub(super) fn observe_bans(
    db: &Connection,
    snapshot: GovernanceBanSnapshot,
    now: i64,
) -> DbResult<()> {
    if !(0..=now + 60_000).contains(&snapshot.observed_ms) {
        return Err(super::Error::Invalid("invalid ban snapshot time"));
    }
    if db.execute("INSERT INTO gov_ban_snapshot VALUES (1,?1) ON CONFLICT(singleton)
                DO UPDATE SET observed_ms=excluded.observed_ms WHERE observed_ms<=excluded.observed_ms",
        [snapshot.observed_ms.min(now)])? == 0 { return Ok(()) }
    db.execute("DELETE FROM gov_game_bans", [])?;
    let mut insert = db.prepare_cached(
        "INSERT INTO gov_game_bans VALUES (?1,?2)
        ON CONFLICT(subject) DO UPDATE SET expires_ms=CASE
            WHEN expires_ms IS NULL OR excluded.expires_ms IS NULL THEN NULL
            ELSE max(expires_ms,excluded.expires_ms) END",
    )?;
    for ban in snapshot.bans {
        insert.execute(params![ban.subject, ban.expires_ms])?;
    }
    Ok(())
}

pub(super) fn password_required(db: &Connection, player: &str) -> DbResult<bool> {
    Ok(db.query_row(
        "SELECT EXISTS(SELECT 1 FROM gov_tenures WHERE player_id=?1 AND ended_ms IS NULL)
         OR EXISTS(SELECT 1 FROM gov_votes WHERE player_id=?1 AND tenure_id IS NULL AND status=1)
         OR EXISTS(SELECT 1 FROM gov_game_grants WHERE player_id=?1 AND permission=2
                    AND confirmed_ms IS NULL AND superseded_ms IS NULL)",
        [player],
        |row| row.get(0),
    )?)
}

pub(super) fn observe(
    db: &Connection,
    mut observation: GovernanceObservation,
    now: i64,
) -> DbResult<()> {
    let player = observation
        .player
        .take()
        .ok_or(super::Error::Invalid("player required"))?;
    let player_id = canonical(&player.id)?;
    let name = player.name.trim();
    if name.is_empty() || name.len() > 64 || !(0..=now + 60_000).contains(&observation.observed_ms)
    {
        return Err(super::Error::Invalid("invalid player observation"));
    }
    let observed = observation.observed_ms.min(now);
    db.execute(
        "INSERT INTO gov_players(player_id,display_name) VALUES (?1,?2)
         ON CONFLICT(player_id) DO UPDATE SET display_name=excluded.display_name
         WHERE excluded.display_name<>excluded.player_id",
        params![player_id, name],
    )?;
    if let Some(login) = observation
        .login_ms
        .filter(|login| (0..=observed).contains(login))
    {
        db.execute(
            "UPDATE gov_players SET first_login_ms=min(coalesce(first_login_ms,?2),?2),
              last_login_ms=max(coalesce(last_login_ms,?2),?2) WHERE player_id=?1",
            params![player_id, login],
        )?;
    }
    if let Some(credential) = observation.credential {
        update_credential(db, &player_id, &credential)?;
    }
    let promotes = observation.login_ms.is_some() || !observation.activity.is_empty();
    let mut through = db.query_row(
        "SELECT coalesce(activity_through_ms,0) FROM gov_players WHERE player_id=?1",
        [&player_id],
        |row| row.get::<_, i64>(0),
    )?;
    observation
        .activity
        .sort_unstable_by_key(|interval| interval.start_ms);
    for interval in observation.activity {
        if interval.start_ms < 0 || interval.end_ms.saturating_sub(interval.start_ms) > rules::DAY {
            return Err(super::Error::Invalid("invalid activity interval"));
        }
        let mut cursor = interval.start_ms.max(through);
        let end_ms = interval.end_ms.min(observed);
        while cursor < end_ms {
            let end = end_ms.min(rules::end_of_day(cursor));
            db.execute(
                "INSERT INTO gov_activity VALUES (?1,?2,?3) ON CONFLICT(player_id,day)
                 DO UPDATE SET duration_ms=min(86400000,duration_ms+excluded.duration_ms)",
                params![player_id, rules::day(cursor), end - cursor],
            )?;
            through = end;
            cursor = end;
        }
    }
    if through != 0 {
        db.execute(
            "UPDATE gov_players SET activity_through_ms=?2 WHERE player_id=?1",
            params![player_id, through],
        )?;
    }
    if promotes && profile(db, &player_id, now)?.is_some_and(|profile| profile.promotion_eligible) {
        db.execute(
            "UPDATE gov_players SET member_since_ms=?2 WHERE player_id=?1",
            params![player_id, now],
        )?;
        delivery::request(db, &player_id, Permission::Member, true, now, None)?;
    }
    Ok(())
}

pub(super) fn update_credential(
    db: &Connection,
    player: &str,
    credential: &GovernanceCredential,
) -> DbResult<()> {
    let revision = i64::try_from(credential.revision)
        .map_err(|_| super::Error::Invalid("credential revision overflow"))?;
    db.execute(
        "UPDATE gov_players SET credential_revision=?2,has_password=?3,credential_changed_ms=?4
         WHERE player_id=?1 AND coalesce(credential_revision,-1)<?2",
        params![player, revision, credential.present, credential.changed_ms],
    )?;
    Ok(())
}

pub(super) fn password_removal(
    db: &Connection,
    removal: &GovernancePasswordRemoval,
    finalize: bool,
) -> DbResult<Outcome> {
    let player = canonical(&removal.player_id)?;
    let removal_id = canonical(&removal.removal_id)?;
    let credential = removal
        .credential
        .as_ref()
        .ok_or(super::Error::Invalid("credential state required"))?;
    if finalize {
        db.execute(
            "INSERT OR IGNORE INTO gov_password_removal_receipts VALUES (?1)",
            [&removal_id],
        )?;
        if db.execute("UPDATE gov_players SET password_removal=NULL WHERE player_id=?1 AND password_removal=?2",
            params![player, removal_id])? != 0 {
            update_credential(db, &player, credential)?;
        }
        return Ok(Outcome::Done);
    }
    if db.query_row(
        "SELECT EXISTS(SELECT 1 FROM gov_password_removal_receipts WHERE removal_id=?1)",
        [&removal_id],
        |row| row.get::<_, bool>(0),
    )? {
        return Ok(Outcome::CredentialStale);
    }
    let Some((pending, revision, present)) = db.query_row(
        "SELECT password_removal,credential_revision,has_password FROM gov_players WHERE player_id=?1",
        [&player],
        |row| Ok((row.get::<_, Option<String>>(0)?, row.get::<_, Option<u64>>(1)?, row.get::<_, bool>(2)?)),
    ).optional()? else {
        return Ok(Outcome::UnknownPlayer);
    };
    if let Some(pending) = pending {
        return Ok(if pending == removal_id {
            Outcome::Done
        } else {
            Outcome::PasswordRemovalPending
        });
    }
    if !credential.present
        || (revision == Some(credential.revision) && !present)
        || revision.is_some_and(|revision| revision > credential.revision)
    {
        return Ok(Outcome::CredentialStale);
    }
    if password_required(db, &player)? {
        return Ok(Outcome::PasswordProtected);
    }
    update_credential(db, &player, credential)?;
    db.execute(
        "UPDATE gov_players SET password_removal=?2 WHERE player_id=?1",
        params![player, removal_id],
    )?;
    Ok(Outcome::Done)
}

pub(super) fn profile(
    db: &Connection,
    player: &str,
    now: i64,
) -> DbResult<Option<GovernanceProfile>> {
    let required_recent_seconds = rules::required_activity_at(now);
    let today = rules::day(now);
    let result = db.query_row(
        "SELECT display_name,first_login_ms,last_login_ms,member_since_ms,
                CASE WHEN EXISTS(SELECT 1 FROM gov_ban_snapshot WHERE observed_ms>=?3) THEN
                    EXISTS(SELECT 1 FROM gov_game_bans WHERE (subject=p.player_id OR subject=lower(p.display_name))
                    AND (expires_ms IS NULL OR expires_ms>?2)) END,has_password,
                EXISTS(SELECT 1 FROM gov_tenures WHERE player_id=p.player_id AND ended_ms IS NULL),
                EXISTS(SELECT 1 FROM gov_maintainers WHERE player_id=p.player_id AND ended_ms IS NULL)
         FROM gov_players p WHERE player_id=?1",
        params![player, now, now - BAN_MAX_AGE_MS],
        |row| {
            let moderator = row.get(6)?;
            Ok(GovernanceProfile {
                player: Some(ChatPlayer { id: player.to_owned(), name: row.get(0)? }),
                first_login_ms: row.get(1)?,
                last_login_ms: row.get(2)?,
                member_since_ms: row.get(3)?,
                banned: row.get(4)?,
                has_password: row.get(5)?,
                moderator,
                maintainer: row.get(7)?,
                required_recent_seconds,
                ..GovernanceProfile::default()
            })
        },
    ).optional()?;
    let Some(mut profile) = result else {
        return Ok(None);
    };
    (
        profile.lifetime_seconds,
        profile.lifetime_days,
        profile.recent_seconds,
        profile.active_days,
        profile.recent_days,
    ) = db.query_row(
        "SELECT coalesce(sum(duration_ms),0)/1000, count(*) FILTER (WHERE duration_ms>0),
            coalesce(sum(duration_ms) FILTER (WHERE day BETWEEN ?2-59 AND ?2),0)/1000,
            count(*) FILTER (WHERE duration_ms>=1800000 AND day BETWEEN ?2-59 AND ?2),
            count(*) FILTER (WHERE duration_ms>=1800000 AND day BETWEEN ?2-29 AND ?2)
         FROM gov_activity WHERE player_id=?1",
        params![player, today],
        |row| {
            Ok((
                row.get(0)?,
                row.get(1)?,
                row.get(2)?,
                row.get(3)?,
                row.get(4)?,
            ))
        },
    )?;
    let not_banned = profile.banned == Some(false);
    profile.login_age_met = profile
        .first_login_ms
        .is_some_and(|time| time <= now - 3 * rules::DAY);
    profile.member_age_met = profile
        .member_since_ms
        .is_some_and(|time| time <= now - 30 * rules::DAY);
    profile.recent_playtime_met = profile.recent_seconds >= required_recent_seconds;
    profile.active_days_met = profile.active_days >= 6;
    profile.recent_days_met = profile.recent_days >= 2;
    profile.promotion_playtime_met = profile.lifetime_seconds >= 8 * 3600;
    profile.promotion_days_met = profile.lifetime_days >= 2;
    profile.active_member = not_banned
        && profile.member_age_met
        && profile.recent_playtime_met
        && profile.active_days_met
        && profile.recent_days_met;
    profile.pause = active_pause(db, player, now)?;
    profile.promotion_eligible = not_banned
        && profile.member_since_ms.is_none()
        && profile.login_age_met
        && profile.promotion_playtime_met
        && profile.promotion_days_met
        && profile.pause.is_none();
    profile.governance_authorized = profile.moderator && not_banned;
    profile.requires_password = password_required(db, player)?;
    profile.appointment_pending = profile.moderator && db.query_row(
        "SELECT EXISTS(SELECT 1 FROM gov_game_grants WHERE player_id=?1 AND permission=2 AND enabled=1
                        AND confirmed_ms IS NULL AND superseded_ms IS NULL)", [player], |row| row.get::<_, bool>(0),
    )?;
    Ok(Some(profile))
}

pub(super) fn search(db: &Connection, query: &str, limit: u32) -> DbResult<Vec<ChatPlayer>> {
    let mut statement = db.prepare_cached(
        "SELECT player_id,display_name FROM gov_players WHERE instr(lower(display_name),lower(?1))>0
         OR instr(player_id,lower(?1))>0 ORDER BY display_name COLLATE NOCASE,player_id LIMIT ?2",
    )?;
    Ok(statement
        .query_map(params![query, limit], |row| {
            Ok(ChatPlayer {
                id: row.get(0)?,
                name: row.get(1)?,
            })
        })?
        .collect::<Result<_, _>>()?)
}

pub(super) fn active_pause(
    db: &Connection,
    player: &str,
    now: i64,
) -> DbResult<Option<GovernancePromotionPause>> {
    Ok(db
        .query_row(
            "SELECT id,created_ms,ends_ms,actor,reason,appeal FROM gov_promotion_pauses
         WHERE player_id=?1 AND lifted_ms IS NULL AND ends_ms>?2 ORDER BY id DESC LIMIT 1",
            params![player, now],
            |row| {
                Ok(GovernancePromotionPause {
                    id: row.get(0)?,
                    created_ms: row.get(1)?,
                    ends_ms: row.get(2)?,
                    actor: row.get(3)?,
                    reason: row.get(4)?,
                    appeal: row.get(5)?,
                })
            },
        )
        .optional()?)
}

pub(super) fn electorate(db: &Connection, now: i64) -> DbResult<Vec<String>> {
    let mut statement = db.prepare_cached(
        "SELECT p.player_id FROM gov_players p JOIN gov_activity a ON a.player_id=p.player_id
         WHERE p.member_since_ms<=?1
           AND EXISTS(SELECT 1 FROM gov_ban_snapshot WHERE observed_ms>=?5)
           AND NOT EXISTS(SELECT 1 FROM gov_game_bans WHERE (subject=p.player_id OR subject=lower(p.display_name))
                    AND (expires_ms IS NULL OR expires_ms>?4))
           AND a.day BETWEEN ?2-59 AND ?2
         GROUP BY p.player_id HAVING sum(a.duration_ms)>=?3
          AND count(*) FILTER (WHERE a.duration_ms>=1800000)>=6
          AND count(*) FILTER (WHERE a.duration_ms>=1800000 AND a.day>=?2-29)>=2",
    )?;
    let players = statement
        .query_map(
            params![
                now - 30 * rules::DAY,
                rules::day(now),
                rules::required_activity_at(now) * 1000,
                now,
                now - BAN_MAX_AGE_MS
            ],
            |row| row.get(0),
        )?
        .collect::<Result<_, _>>()?;
    Ok(players)
}

pub(super) fn roster(db: &Connection) -> DbResult<Vec<GovernanceStaffEntry>> {
    let mut statement = db.prepare_cached(
        "SELECT p.player_id,p.display_name,t.started_ms,'',0,
            EXISTS(SELECT 1 FROM gov_game_grants WHERE player_id=p.player_id AND permission=2 AND confirmed_ms IS NULL AND superseded_ms IS NULL)
          FROM gov_tenures t JOIN gov_players p ON p.player_id=t.player_id WHERE t.ended_ms IS NULL
         UNION ALL
         SELECT p.player_id,p.display_name,m.started_ms,m.responsibility,1,0
          FROM gov_maintainers m JOIN gov_players p ON p.player_id=m.player_id WHERE m.ended_ms IS NULL
         ORDER BY 5 DESC, 2, 1",
    )?;
    Ok(statement
        .query_map([], |row| {
            Ok(GovernanceStaffEntry {
                player: Some(ChatPlayer {
                    id: row.get(0)?,
                    name: row.get(1)?,
                }),
                started_ms: row.get(2)?,
                responsibility: row.get(3)?,
                maintainer: row.get(4)?,
                grant_pending: row.get(5)?,
            })
        })?
        .collect::<Result<_, _>>()?)
}
