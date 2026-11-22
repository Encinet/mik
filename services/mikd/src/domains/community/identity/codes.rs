use super::{ExternalKey, LinkCode};
use crate::domains::community::Error;
use sha2::{Digest, Sha256};
use tokio_rusqlite::rusqlite::{Connection, OptionalExtension, params};
use uuid::Uuid;

const CODE_TTL_MS: i64 = 5 * 60 * 1000;
const FAILURE_WINDOW_MS: i64 = 10 * 60 * 1000;
const LOCKOUT_MS: i64 = 15 * 60 * 1000;
const MAX_PRINCIPALS: i64 = 4096;

pub struct IssuedCode {
    pub code: LinkCode,
    pub expires_at: i64,
}

pub(super) struct Pending {
    pub account: Uuid,
    pub platform: String,
}

pub(super) fn issue(
    db: &Connection,
    account: Uuid,
    platform: &str,
    now: i64,
) -> Result<IssuedCode, Error> {
    db.execute("DELETE FROM link_challenges WHERE expires_at<=?1", [now])?;
    let expires_at = now + CODE_TTL_MS;
    let account = account.to_string();
    for _ in 0..32 {
        let code = LinkCode::generate();
        let changed = db.execute(
            "INSERT INTO link_challenges(digest,account_id,platform,expires_at) VALUES (?1,?2,?3,?4)
             ON CONFLICT(digest) DO NOTHING
             ON CONFLICT(account_id,platform) DO UPDATE SET digest=excluded.digest,expires_at=excluded.expires_at",
            params![code.digest().as_slice(), account, platform, expires_at],
        )?;
        if changed == 1 {
            return Ok(IssuedCode { code, expires_at });
        }
    }
    Err(Error::Busy)
}

pub(super) fn pending(
    db: &Connection,
    digest: &[u8; 32],
    now: i64,
) -> Result<Option<Pending>, Error> {
    Ok(db
        .query_row(
            "SELECT account_id,platform FROM link_challenges
         WHERE digest=?1 AND expires_at>?2",
            params![digest.as_slice(), now],
            |row| {
                Ok(Pending {
                    account: super::super::schema::row_uuid(row, 0)?,
                    platform: row.get(1)?,
                })
            },
        )
        .optional()?)
}

pub(super) fn consume(db: &Connection, digest: &[u8; 32]) -> Result<(), Error> {
    if db.execute(
        "DELETE FROM link_challenges WHERE digest=?1",
        [digest.as_slice()],
    )? != 1
    {
        return Err(Error::Stale);
    }
    Ok(())
}

pub(super) struct Attempts {
    principal: [u8; 32],
    state: Option<(i64, i64, Option<i64>)>,
    now: i64,
}

impl Attempts {
    pub(super) fn read(db: &Connection, key: &ExternalKey, now: i64) -> Result<Self, Error> {
        let mut digest = Sha256::new();
        for part in key.parts() {
            digest.update(part.as_bytes());
            digest.update([0]); // identity parts exclude NUL
        }
        let principal: [u8; 32] = digest.finalize().into();
        db.execute("DELETE FROM binding_attempts WHERE expires_at<=?1", [now])?;
        let state = db.query_row(
            "SELECT failure_count,window_started,locked_until FROM binding_attempts WHERE principal_digest=?1",
            [principal.as_slice()],
            |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?)),
        ).optional()?;
        Ok(Self {
            principal,
            state,
            now,
        })
    }

    pub(super) fn locked_until(&self) -> Option<i64> {
        self.state.and_then(|(_, _, lock)| lock)
    }

    pub(super) fn admit_new_principal(&self, db: &Connection) -> Result<(), Error> {
        if self.state.is_none() {
            // Never evict an active rate limit just to admit a new failure.
            let entries: i64 =
                db.query_row("SELECT count(*) FROM binding_attempts", [], |row| {
                    row.get(0)
                })?;
            if entries >= MAX_PRINCIPALS {
                return Err(Error::Busy);
            }
        }
        Ok(())
    }

    pub(super) fn fail(self, db: &Connection) -> Result<Option<i64>, Error> {
        let (count, started) = self
            .state
            .map_or((1, self.now), |(count, started, _)| (count + 1, started));
        let locked_until = (count == 5).then_some(self.now + LOCKOUT_MS);
        let expires_at = locked_until.unwrap_or(started + FAILURE_WINDOW_MS + 1);
        db.execute(
            "INSERT INTO binding_attempts(principal_digest,failure_count,window_started,locked_until,expires_at)
             VALUES (?1,?2,?3,?4,?5) ON CONFLICT(principal_digest) DO UPDATE SET
             failure_count=excluded.failure_count,window_started=excluded.window_started,
             locked_until=excluded.locked_until,expires_at=excluded.expires_at",
            params![self.principal.as_slice(), count, started, locked_until, expires_at],
        )?;
        Ok(locked_until)
    }

    pub(super) fn clear(self, db: &Connection) -> Result<(), Error> {
        if self.state.is_some() {
            db.execute(
                "DELETE FROM binding_attempts WHERE principal_digest=?1",
                [self.principal.as_slice()],
            )?;
        }
        Ok(())
    }
}
