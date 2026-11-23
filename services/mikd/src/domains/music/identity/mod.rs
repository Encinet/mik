//! Persistent mapping from upstream locators to stable track IDs.
mod database;

use super::track::{ResolverInput, TrackId, TrackRecord, TrackRef, search_rank};
use database::{Database, check_cancelled};
use mik_protocol::music::{
    MusicTrackData,
    music_track_data::{Availability, State},
};
use std::{
    collections::{BTreeMap, BTreeSet},
    io,
    path::Path,
    sync::atomic::{AtomicU64, Ordering},
};
use tokio_rusqlite::{
    Connection,
    rusqlite::{self, OptionalExtension, params},
};
use uuid::Uuid;

pub(super) const FILE: &str = "identities.sqlite3";
const MAX_IDENTITIES: i64 = 50_000;
const COLUMNS: &str =
    "id, namespace, source_key, provider, music_info, title, artist, album, duration_ms, retired";

pub(super) struct IdentityStore(Database, AtomicU64);

pub(super) struct Registered {
    pub id: TrackId,
    pub record: TrackRecord,
    pub retired: bool,
}

impl Registered {
    pub fn display(&self, ready: &BTreeSet<TrackRef>) -> MusicTrackData {
        let availability = if self.retired {
            Availability::Unavailable
        } else if ready.contains(&self.record.key) {
            Availability::Ready
        } else {
            Availability::NeedsPreparation
        };
        let mut row = self.record.display(self.id, availability);
        if self.retired {
            row.state = State::Retired.into();
        }
        row
    }
}

impl IdentityStore {
    pub async fn open(path: &Path) -> io::Result<Self> {
        let db = Connection::open(path).await.map_err(io::Error::other)?;
        db.call(|db| {
            db.execute_batch(
                "PRAGMA journal_mode=WAL;
            CREATE TABLE IF NOT EXISTS identities (
                id TEXT PRIMARY KEY NOT NULL,
                namespace TEXT NOT NULL,
                source_key TEXT NOT NULL,
                provider TEXT NOT NULL,
                music_info TEXT NOT NULL,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                album TEXT NOT NULL,
                duration_ms INTEGER,
                retired INTEGER NOT NULL DEFAULT 0 CHECK(retired IN (0,1)),
                UNIQUE(namespace, source_key)
            ) STRICT;",
            )
        })
        .await
        .map_err(io::Error::other)?;
        Ok(Self(Database::new(db, 16), AtomicU64::new(0)))
    }

    pub async fn close(&self) -> io::Result<()> {
        self.0.close().await
    }

    /// Commit only rows in the page about to be delivered, before exposing IDs.
    pub async fn publish(&self, records: Vec<TrackRecord>) -> io::Result<Vec<Option<Registered>>> {
        if records.is_empty() {
            return Ok(Vec::new());
        }
        self.0.call(move |db, cancel| {
            let tx = db.transaction()?;
            let mut count: i64 = tx.query_row("SELECT COUNT(*) FROM identities", [], |row| row.get(0))?;
            let mut output = Vec::with_capacity(records.len());
            {
                let mut by_key = tx.prepare_cached("SELECT id, retired FROM identities WHERE namespace=?1 AND source_key=?2")?;
                let mut upsert = tx.prepare_cached(
                    "INSERT INTO identities (id, namespace, source_key, provider, music_info, title, artist, album, duration_ms) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)
                    ON CONFLICT(namespace,source_key) DO UPDATE SET
                        provider=excluded.provider, music_info=excluded.music_info, title=excluded.title,
                        artist=excluded.artist, album=excluded.album, duration_ms=excluded.duration_ms",
                )?;
                for record in records {
                    check_cancelled(cancel)?;
                    let known = by_key.query_row(params![record.key.platform, record.key.id], |row| Ok((row.get::<_, String>(0)?, row.get::<_, bool>(1)?))).optional()?;
                    let id = match known {
                        Some((value, retired)) => {
                            let id = parse_id(&value)?;
                            if retired { output.push(Some(Registered { id, record, retired })); continue; }
                            id
                        }
                        None if count >= MAX_IDENTITIES => {
                            output.push(None);
                            continue;
                        }
                        None => { count += 1; TrackId(Uuid::new_v4()) }
                    };
                    upsert.execute(params![id.to_string(), record.key.platform, record.key.id,
                        record.provider, record.info_json, record.title, record.artist, record.album, record.duration_ms])?;
                    output.push(Some(Registered { id, record, retired: false }));
                }
            }
            tx.commit()?;
            Ok(output)
        }).await
    }

    pub async fn tracks(&self, ids: Vec<TrackId>) -> io::Result<Vec<Option<Registered>>> {
        self.0
            .call(move |db, cancel| {
                let mut query =
                    db.prepare_cached(&format!("SELECT {COLUMNS} FROM identities WHERE id=?1"))?;
                ids.into_iter()
                    .map(|id| {
                        check_cancelled(cancel)?;
                        query.query_row([id.to_string()], read).optional()
                    })
                    .collect()
            })
            .await
    }

    pub async fn by_keys(&self, keys: Vec<TrackRef>) -> io::Result<Vec<Registered>> {
        self.0
            .call(move |db, cancel| {
                let mut query = db.prepare_cached(&format!(
                    "SELECT {COLUMNS} FROM identities WHERE namespace=?1 AND source_key=?2"
                ))?;
                let mut found = Vec::new();
                for key in keys {
                    check_cancelled(cancel)?;
                    if let Some(row) = query
                        .query_row(params![key.platform, key.id], read)
                        .optional()?
                    {
                        found.push(row);
                    }
                }
                Ok(found)
            })
            .await
    }

    pub fn revision(&self) -> u64 {
        self.1.load(Ordering::Acquire)
    }

    pub async fn retire(&self, id: TrackId) -> io::Result<()> {
        let changed = self
            .0
            .call(move |db, _| {
                db.execute(
                    "UPDATE identities SET retired=1 WHERE id=?1",
                    [id.to_string()],
                )
            })
            .await?;
        if changed == 0 {
            return Err(super::failure(
                mik_protocol::music::music_failure::Code::NotFound,
            ));
        }
        self.1.fetch_add(1, Ordering::Release);
        Ok(())
    }

    pub async fn ready_page(
        &self,
        keys: Vec<TrackRef>,
        after: String,
        limit: usize,
    ) -> io::Result<Vec<MusicTrackData>> {
        let ready = keys.iter().cloned().collect::<BTreeSet<_>>();
        self.0.call(move |db, cancel| {
            let mut query = db.prepare_cached(
                "SELECT id, namespace, source_key, '', '', title, artist, album, duration_ms, retired
                 FROM identities WHERE namespace=?1 AND source_key=?2 AND retired=0 AND id>?3")?;
            let mut found = BTreeMap::new();
            for key in keys {
                check_cancelled(cancel)?;
                if let Some(row) = query.query_row(params![key.platform, key.id, after], read).optional()? {
                    found.insert(row.id, row.display(&ready));
                    if found.len() > limit { found.pop_last(); }
                }
            }
            Ok(found.into_values().collect())
        }).await
    }

    pub async fn search(
        &self,
        keyword: String,
        ready: BTreeSet<TrackRef>,
        ready_only: bool,
        limit: usize,
    ) -> io::Result<(BTreeMap<String, MusicTrackData>, bool)> {
        self.0.call(move |db, cancel| {
            let mut query = db.prepare_cached(
                "SELECT id, namespace, source_key, '', '', title, artist, album, duration_ms, retired FROM identities WHERE retired=0")?;
            let mut rows = query.query([])?;
            let mut found = BTreeMap::new();
            let mut partial = false;
            while let Some(row) = rows.next()? {
                check_cancelled(cancel)?;
                let record = read(row)?;
                if ready_only && !ready.contains(&record.record.key) { continue; }
                let track = record.display(&ready);
                if let Some(score) = search_rank(&keyword, &track.title, &track.artist, &track.album, "") {
                    found.insert((score, record.id), track);
                    if found.len() > limit { found.pop_last(); partial = true; }
                }
            }
            Ok((found.into_values().map(|row| (row.track_id.clone(), row)).collect(), partial))
        }).await
    }

    pub async fn resolver_input(&self, key: &TrackRef) -> io::Result<Option<ResolverInput>> {
        self.by_keys(vec![key.clone()])
            .await?
            .pop()
            .map(|row| ResolverInput::from_record(&row.record))
            .transpose()
    }
}

fn parse_id(value: &str) -> rusqlite::Result<TrackId> {
    TrackId::parse(value).map_err(|error| {
        rusqlite::Error::FromSqlConversionFailure(0, rusqlite::types::Type::Text, Box::new(error))
    })
}

fn read(row: &rusqlite::Row<'_>) -> rusqlite::Result<Registered> {
    let key = TrackRef::new(&row.get::<_, String>(1)?, row.get(2)?)
        .ok_or(rusqlite::Error::InvalidQuery)?;
    Ok(Registered {
        id: parse_id(&row.get::<_, String>(0)?)?,
        retired: row.get(9)?,
        record: TrackRecord {
            key,
            provider: row.get(3)?,
            info_json: row.get(4)?,
            title: row.get(5)?,
            artist: row.get(6)?,
            album: row.get(7)?,
            duration_ms: row.get(8)?,
        },
    })
}
