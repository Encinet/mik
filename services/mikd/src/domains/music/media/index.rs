use super::TrackRef;
use std::{io, path::Path, sync::Arc};
use tokio_rusqlite::{Connection, rusqlite::params};

pub(super) struct Cached {
    pub track: Arc<TrackRef>,
    pub version: String,
    pub bytes: u64,
    pub content_type: Option<String>,
    pub provider: String,
    pub used: i64,
}

pub(super) struct Index(Connection);

impl Index {
    pub async fn open(path: &Path) -> io::Result<Self> {
        let connection = Connection::open(path).await.map_err(io::Error::other)?;
        connection
            .call(|db| {
                db.execute_batch(
                    "PRAGMA journal_mode=WAL;
             CREATE TABLE IF NOT EXISTS media (
                platform TEXT NOT NULL,
                track_id TEXT NOT NULL,
                version TEXT NOT NULL UNIQUE,
                bytes INTEGER NOT NULL CHECK (bytes > 0),
                content_type TEXT,
                provider TEXT NOT NULL,
                used INTEGER NOT NULL,
                PRIMARY KEY (platform, track_id)
             ) WITHOUT ROWID;",
                )
            })
            .await
            .map_err(io::Error::other)?;
        Ok(Self(connection))
    }

    pub async fn close(&self) -> io::Result<()> {
        self.0.clone().close().await.map_err(io::Error::other)
    }

    pub async fn entries(&self, after: Option<Arc<TrackRef>>) -> io::Result<Vec<Cached>> {
        self.0
            .call(move |db| {
                let (platform, id) = after
                    .as_ref()
                    .map_or(("", ""), |key| (key.platform.as_str(), key.id.as_str()));
                let mut query = db.prepare_cached(
                    "SELECT platform, track_id, version, bytes, content_type, provider, used FROM media
                     WHERE (platform, track_id) > (?1, ?2) ORDER BY platform, track_id LIMIT 256",
                )?;
                let rows = query.query_map(params![platform, id], |row| {
                    Ok(Cached {
                        track: Arc::new(TrackRef {
                            platform: row.get(0)?,
                            id: row.get(1)?,
                        }),
                        version: row.get(2)?,
                        bytes: row.get(3)?,
                        content_type: row.get(4)?,
                        provider: row.get(5)?,
                        used: row.get(6)?,
                    })
                })?;
                rows.collect::<Result<_, _>>()
            })
            .await
            .map_err(io::Error::other)
    }

    pub async fn store(&self, record: Cached) -> io::Result<()> {
        self.0
            .call(move |db| {
                db.execute(
                    "INSERT OR REPLACE INTO media VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)",
                    params![
                        record.track.platform,
                        record.track.id,
                        record.version,
                        record.bytes,
                        record.content_type,
                        record.provider,
                        record.used
                    ],
                )
            })
            .await
            .map(|_| ())
            .map_err(io::Error::other)
    }

    pub async fn touch(&self, version: String, used: i64) -> io::Result<()> {
        self.0
            .call(move |db| {
                db.execute(
                    "UPDATE media SET used=MAX(used, ?2) WHERE version=?1",
                    params![version, used],
                )
            })
            .await
            .map(|_| ())
            .map_err(io::Error::other)
    }

    pub async fn remove(&self, versions: Vec<String>) -> io::Result<()> {
        if versions.is_empty() {
            return Ok(());
        }
        self.0
            .call(move |db| {
                let tx = db.transaction()?;
                {
                    let mut delete = tx.prepare("DELETE FROM media WHERE version=?1")?;
                    for version in versions {
                        delete.execute([version])?;
                    }
                }
                tx.commit()
            })
            .await
            .map_err(io::Error::other)
    }
}
