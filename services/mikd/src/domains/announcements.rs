use mik_protocol::wire::{AnnouncementEntry, AnnouncementResult};
use rustix::fs::{Mode, OFlags, open};
use serde::{Deserialize, Serialize};
use std::{
    fs::File,
    io::{self, Read},
    path::{Path, PathBuf},
    sync::Arc,
    time::Duration,
};
use time::{OffsetDateTime, format_description::well_known::Rfc3339};
use tokio::{
    sync::RwLock,
    time::{MissedTickBehavior, interval},
};
use tokio_util::sync::CancellationToken;

const REFRESH_INTERVAL: Duration = Duration::from_secs(5);
const MAX_SOURCE_BYTES: u64 = 128 * 1024;
const MAX_ENTRIES: usize = 256;
const MAX_CONTENT_BYTES: usize = 8 * 1024;
const CATALOG_FILE: &str = "catalog.json";

pub(crate) struct Announcements {
    source: PathBuf,
    state: RwLock<CatalogState>,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum CatalogStatus {
    Unavailable,
    Ready,
    Stale,
}

impl CatalogStatus {
    pub(crate) fn label(self) -> &'static str {
        match self {
            Self::Unavailable => "unavailable",
            Self::Ready => "ready",
            Self::Stale => "stale",
        }
    }
}

#[derive(Deserialize, Serialize)]
#[serde(deny_unknown_fields)]
struct Document {
    timestamp: String,
    content: String,
}

struct Catalog {
    entries: Vec<AnnouncementEntry>,
    json: Arc<[u8]>,
}

#[derive(Default)]
enum CatalogState {
    #[default]
    Unavailable,
    Loaded {
        catalog: Catalog,
        stale: bool,
    },
}

impl CatalogState {
    fn status(&self) -> CatalogStatus {
        match self {
            Self::Unavailable => CatalogStatus::Unavailable,
            Self::Loaded { stale: false, .. } => CatalogStatus::Ready,
            Self::Loaded { stale: true, .. } => CatalogStatus::Stale,
        }
    }

    fn catalog(&self) -> Option<&Catalog> {
        match self {
            Self::Unavailable => None,
            Self::Loaded { catalog, .. } => Some(catalog),
        }
    }
}

impl Announcements {
    pub(crate) fn new(root: &Path) -> Self {
        Self {
            source: root.join(CATALOG_FILE),
            state: RwLock::default(),
        }
    }

    /// Checks the deployment file without creating it or starting a refresh task.
    pub(crate) fn validate(root: &Path) -> io::Result<()> {
        let source = root.join(CATALOG_FILE);
        match load(&source) {
            Ok(_) => Ok(()),
            Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(()),
            Err(error) => Err(io::Error::new(
                error.kind(),
                format!("announcement catalog {}: {error}", source.display()),
            )),
        }
    }

    pub(crate) async fn status(&self) -> CatalogStatus {
        self.state.read().await.status()
    }

    pub(crate) async fn json(&self) -> (CatalogStatus, Option<Arc<[u8]>>) {
        let state = self.state.read().await;
        (
            state.status(),
            state.catalog().map(|catalog| catalog.json.clone()),
        )
    }

    pub(crate) async fn snapshot(&self) -> Option<AnnouncementResult> {
        let state = self.state.read().await;
        let CatalogState::Loaded { catalog, stale } = &*state else {
            return None;
        };
        Some(AnnouncementResult {
            entries: catalog.entries.clone(),
            stale: *stale,
        })
    }

    pub(crate) async fn maintain(&self, stop: &CancellationToken) {
        let mut ticks = interval(REFRESH_INTERVAL);
        ticks.set_missed_tick_behavior(MissedTickBehavior::Skip);
        let mut failing = false;
        while stop.run_until_cancelled(ticks.tick()).await.is_some() {
            match self.refresh().await {
                Ok(()) if failing => {
                    eprintln!(
                        "mikd announcement catalog {} recovered",
                        self.source.display()
                    );
                    failing = false;
                }
                Err(error)
                    if !failing
                        && (error.kind() != io::ErrorKind::NotFound
                            || self.status().await == CatalogStatus::Stale) =>
                {
                    eprintln!(
                        "mikd announcement catalog {}: {error}",
                        self.source.display()
                    );
                    failing = true;
                }
                _ => {}
            }
        }
    }

    async fn refresh(&self) -> io::Result<()> {
        let source = self.source.clone();
        let loaded = tokio::task::spawn_blocking(move || load(&source))
            .await
            .map_err(io::Error::other)
            .flatten();
        let mut state = self.state.write().await;
        match loaded {
            Ok(next) => {
                match &mut *state {
                    CatalogState::Loaded { catalog, stale } if catalog.json == next.json => {
                        *stale = false;
                    }
                    _ => {
                        *state = CatalogState::Loaded {
                            catalog: next,
                            stale: false,
                        }
                    }
                }
                Ok(())
            }
            Err(error) => {
                if let CatalogState::Loaded { stale, .. } = &mut *state {
                    *stale = true;
                }
                Err(error)
            }
        }
    }
}

fn load(source: &Path) -> io::Result<Catalog> {
    let invalid = |message| io::Error::new(io::ErrorKind::InvalidData, message);
    // A misplaced FIFO must not keep the refresher waiting for another process to open it.
    let file = File::from(open(
        source,
        OFlags::RDONLY | OFlags::NONBLOCK | OFlags::CLOEXEC,
        Mode::empty(),
    )?);
    if !file.metadata()?.is_file() {
        return Err(invalid("announcement catalog must be a regular file"));
    }
    let mut input = Vec::new();
    file.take(MAX_SOURCE_BYTES + 1).read_to_end(&mut input)?;
    if input.len() as u64 > MAX_SOURCE_BYTES {
        return Err(invalid("announcement catalog exceeds 128 KiB"));
    }
    let documents: Vec<Document> = serde_json::from_slice(&input)
        .map_err(|error| io::Error::new(io::ErrorKind::InvalidData, error))?;
    if documents.len() > MAX_ENTRIES {
        return Err(invalid("announcement catalog exceeds 256 entries"));
    }
    let mut parsed = documents
        .into_iter()
        .map(|mut document| {
            let date = OffsetDateTime::parse(&document.timestamp, &Rfc3339)
                .map_err(|_| invalid("announcement timestamp must be RFC 3339"))?;
            if document.content.len() > MAX_CONTENT_BYTES || document.content.trim().is_empty() {
                return Err(invalid(
                    "announcement content must be nonempty and at most 8 KiB",
                ));
            }
            document.timestamp = date
                .checked_to_offset(time::UtcOffset::UTC)
                .ok_or_else(|| invalid("announcement UTC timestamp is out of range"))?
                .format(&Rfc3339)
                .map_err(|_| invalid("announcement UTC timestamp is out of range"))?;
            Ok((date, document))
        })
        .collect::<io::Result<Vec<_>>>()?;
    parsed.sort_by_key(|(date, _)| std::cmp::Reverse(*date));
    let json = serde_json::to_vec(
        &parsed
            .iter()
            .map(|(_, document)| document)
            .collect::<Vec<_>>(),
    )
    .map_err(io::Error::other)?
    .into();
    let entries = parsed
        .into_iter()
        .map(|(date, document)| AnnouncementEntry {
            timestamp: date.unix_timestamp(),
            content: document.content,
        })
        .collect();
    Ok(Catalog { entries, json })
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    #[tokio::test]
    async fn refresh_only_publishes_complete_catalogs() {
        let root = tempfile::tempdir().unwrap();
        let source = root.path().join("catalog.json");
        let service = Announcements::new(root.path());
        assert_eq!(
            service.refresh().await.unwrap_err().kind(),
            io::ErrorKind::NotFound
        );
        assert_eq!(service.json().await, (CatalogStatus::Unavailable, None));
        assert_eq!(service.snapshot().await, None);
        fs::write(
            &source,
            br#"[{"timestamp":"2026-11-01T12:00:00+08:00","content":"Update"}]"#,
        )
        .unwrap();
        service.refresh().await.unwrap();
        let updated = service.snapshot().await.unwrap();
        assert_eq!(updated.entries[0].content, "Update");
        assert!(!updated.stale);
        let (status, stable) = service.json().await;
        assert_eq!(status, CatalogStatus::Ready);

        fs::write(
            &source,
            br#"[{"timestamp":"2026-11-01T12:00:00Z","content":"Edit"},
                 {"timestamp":"invalid","content":"Broken"}]"#,
        )
        .unwrap();
        assert_eq!(
            service.refresh().await.unwrap_err().kind(),
            io::ErrorKind::InvalidData
        );
        assert_eq!(service.json().await, (CatalogStatus::Stale, stable.clone()));
        let retained = service.snapshot().await.unwrap();
        assert_eq!(retained.entries, updated.entries);
        assert!(retained.stale);

        fs::remove_file(&source).unwrap();
        assert_eq!(
            service.refresh().await.unwrap_err().kind(),
            io::ErrorKind::NotFound
        );
        assert_eq!(service.json().await, (CatalogStatus::Stale, stable));

        fs::write(&source, b"[]").unwrap();
        service.refresh().await.unwrap();
        assert_eq!(service.status().await, CatalogStatus::Ready);
        assert_eq!(service.json().await.1.as_deref(), Some(b"[]".as_slice()));
        assert!(service.snapshot().await.unwrap().entries.is_empty());
    }
}
