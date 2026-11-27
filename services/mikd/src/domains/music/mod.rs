mod cache;
mod catalog;
mod http;
mod identity;
mod library;
mod lyrics;
mod management;
mod media;
mod notes;
mod source;
mod track;

#[cfg(test)]
mod tests;

pub(crate) use cache::{CacheEntry, CachePage};
use catalog::{Catalog, Cursors};
use identity::IdentityStore;
pub(crate) use library::LocalTrack;
pub(crate) use lyrics::LyricsInspection;
use lyrics::LyricsService;
pub(crate) use management::{
    CatalogPage, CatalogQuery, CatalogTrack, InspectedMedia, PreparedTrack, TrackInspection,
    VersionEntry, VersionPage,
};
pub(crate) use media::{CacheCleanup, CacheState, CacheUsage, Lease, ReadFailure};
use media::{Media, WaitFor};
use mik_protocol::music::{
    MusicFailure, MusicRepresentation, MusicRequest,
    music_failure::Code,
    music_open_media::{Constraint, Readiness},
    music_reply::Result as Reply,
    music_request::Operation,
};
use mik_protocol::{MUSIC_ENCODED_AUDIO_V1, MUSIC_NOTE_EVENTS_V1};
use source::Source;
pub(crate) use source::{SourceImport, SourceRefresh, SourceState};
use std::{
    fs, io,
    os::unix::fs::{DirBuilderExt, PermissionsExt},
    path::{Path, PathBuf},
    sync::{Arc, Mutex, PoisonError},
    time::Duration,
};
use tokio_util::task::AbortOnDropHandle;
use track::{TrackId, TrackRef};
use uuid::Uuid;

const MIB: u64 = 1024 * 1024;

pub struct Config {
    root: PathBuf,
    sources: PathBuf,
    cache: PathBuf,
    cache_bytes: u64,
}

impl Config {
    pub(crate) fn load(state: &Path, cache: &Path, validate: bool) -> io::Result<Self> {
        let sources = state.join("music/sources");
        if validate {
            match fs::metadata(&sources) {
                Ok(metadata) if metadata.is_dir() => {}
                Err(error) if error.kind() == io::ErrorKind::NotFound => {}
                _ => {
                    return Err(invalid(
                        "music configuration: sources must be a readable directory",
                    ));
                }
            }
        }
        Ok(Self {
            root: state.join("music/catalog-v2"),
            sources,
            cache: cache.join("music/media-v2"),
            cache_bytes: 8192 * MIB,
        })
    }
}

pub(crate) struct Service {
    library: Arc<library::Library>,
    identities: Arc<IdentityStore>,
    cursors: Cursors,
    admin_cursors: Cursors,
    source: Option<Source>,
    media: Option<Arc<Media>>,
    refresh: Mutex<Option<AbortOnDropHandle<()>>>,
    lyrics: LyricsService,
    failures: Mutex<std::collections::VecDeque<(TrackId, String)>>,
}

pub(crate) enum Outcome {
    Reply(Reply),
    Media(Lease),
}

impl Service {
    pub(crate) async fn open(config: Config) -> io::Result<Self> {
        let (root, library) = tokio::task::spawn_blocking(move || {
            let library = open_library(&config.root)?;
            Ok::<_, io::Error>((config.root, library))
        })
        .await
        .map_err(io::Error::other)??;
        let identities = Arc::new(IdentityStore::open(&root.join(identity::FILE)).await?);
        let source = match Source::open(config.sources, identities.clone()).await {
            Ok(source) => Some(source),
            Err(error) => {
                eprintln!("mikd music source registry unavailable: {error}");
                None
            }
        };
        let media = match Media::new(config.cache, 512 * MIB, config.cache_bytes).await {
            Ok(media) => Some(media),
            Err(error) => {
                eprintln!("mikd music media cache unavailable: {error}");
                None
            }
        };
        let refresh = source.as_ref().map(|source| {
            let source = source.clone();
            AbortOnDropHandle::new(tokio::spawn(async move {
                loop {
                    drop(source.refresh(None).await);
                    tokio::time::sleep(Duration::from_hours(6)).await;
                }
            }))
        });
        let lyrics = LyricsService::new(root.join("lyrics"));
        Ok(Self {
            library,
            identities,
            cursors: Cursors::default(),
            admin_cursors: Cursors::default(),
            source,
            media,
            refresh: Mutex::new(refresh),
            lyrics,
            failures: Mutex::default(),
        })
    }

    pub(crate) fn source_registry_ready(&self) -> bool {
        self.source.is_some()
    }
    pub(crate) fn media_cache_ready(&self) -> bool {
        self.media.is_some()
    }

    fn source(&self) -> io::Result<&Source> {
        self.source
            .as_ref()
            .ok_or_else(|| failure(Code::Unavailable))
    }

    fn catalog<'a>(&'a self, cursors: &'a Cursors) -> Catalog<'a> {
        Catalog {
            library: &self.library,
            identities: &self.identities,
            source: self.source.as_ref(),
            media: self.media.as_deref(),
            cursors,
        }
    }

    async fn open_media(
        &self,
        open: mik_protocol::music::MusicOpenMedia,
        profile: u8,
    ) -> io::Result<Lease> {
        let id = TrackId::parse(&open.track_id)?;
        let wait = match open.readiness() {
            Readiness::Readable => WaitFor::Readable,
            Readiness::Complete => WaitFor::Complete,
            Readiness::Unspecified => return Err(invalid("missing media readiness")),
        };
        let (exact, avoid) = match open.constraint {
            None => (None, None),
            Some(Constraint::ExactVersion(value)) => (Some(version(&value)?), None),
            Some(Constraint::AvoidVersion(value)) => (None, Some(version(&value)?)),
        };
        if self.library.track(id)?.is_some() {
            let lease = self.library.open_track(id, exact).inspect_err(|error| {
                self.log_open_failure(id, exact, "managed_library", error);
            })?;
            if avoid.is_some()
                || !supports(
                    profile,
                    lease.descriptor.representation(),
                    lease.descriptor.representation_version,
                )
            {
                return Err(failure(Code::UnsupportedMedia));
            }
            eprintln!(
                "mikd music open track={id} version={} source=managed_library",
                lease.version().simple()
            );
            return Ok(lease);
        }
        let row = self.active_remote(id).await?;
        if !supports(profile, MusicRepresentation::EncodedAudio, 1) {
            return Err(failure(Code::UnsupportedMedia));
        }
        self.open_remote(row, wait, exact, avoid).await
    }

    async fn active_remote(&self, id: TrackId) -> io::Result<identity::Registered> {
        let row = self.registered_remote(id).await?;
        if row.retired {
            Err(failure(Code::Retired))
        } else {
            Ok(row)
        }
    }

    async fn registered_remote(&self, id: TrackId) -> io::Result<identity::Registered> {
        self.identities
            .tracks(vec![id])
            .await?
            .pop()
            .flatten()
            .ok_or_else(|| failure(Code::NotFound))
    }

    async fn open_remote(
        &self,
        row: identity::Registered,
        wait: WaitFor,
        exact: Option<Uuid>,
        avoid: Option<Uuid>,
    ) -> io::Result<Lease> {
        let id = row.id;
        let media = self.media.as_ref().ok_or_else(|| {
            failure(if exact.is_some() {
                Code::VersionGone
            } else {
                Code::Unavailable
            })
        })?;
        let result = match exact {
            Some(version) => media.open_exact(&row.record.key, version, wait).await,
            None => {
                media
                    .open(self.source.as_ref(), row.record.key, wait, avoid)
                    .await
            }
        };
        match &result {
            Ok(lease) => eprintln!(
                "mikd music open track={id} version={} {}",
                lease.version().simple(),
                lease.origin()
            ),
            Err(error) => self.log_open_failure(id, exact, "resolver", error),
        }
        result
    }

    fn log_open_failure(&self, id: TrackId, exact: Option<Uuid>, route: &str, error: &io::Error) {
        let detail = error.to_string();
        eprintln!(
            "mikd music open track={id} version={:?} route={route} failed: {detail}",
            exact.map(|version| version.simple().to_string())
        );
        let mut failures = self.failures.lock().unwrap_or_else(PoisonError::into_inner);
        if failures.len() >= 128 {
            failures.pop_front();
        }
        failures.push_back((id, detail));
    }

    pub(crate) async fn execute(&self, request: MusicRequest, profile: u8) -> io::Result<Outcome> {
        let operation = request
            .operation
            .ok_or_else(|| invalid("missing music operation"))?;
        let catalog = self.catalog(&self.cursors);
        let result = match operation {
            Operation::List(query) => Reply::List(catalog.list(query).await?),
            Operation::Tracks(query) => Reply::Tracks(catalog.tracks(query).await?),
            Operation::Collection(query) => Reply::Collection(catalog.collection(query).await?),
            Operation::OpenMedia(open) => {
                return self.open_media(open, profile).await.map(Outcome::Media);
            }
            Operation::DecodeFailed(report) => {
                let id = TrackId::parse(&report.track_id)?;
                let version = version(&report.media_version)?;
                let retry = if self.library.owns(id, version)? {
                    eprintln!(
                        "mikd music decode failed track={id} version={version} source=managed_library; original retained"
                    );
                    false
                } else if let (Some(media), Some(row)) = (
                    &self.media,
                    self.identities.tracks(vec![id]).await?.pop().flatten(),
                ) {
                    media.report_corrupt(row.record.key, version).await?
                } else {
                    false
                };
                Reply::Done(retry)
            }
            Operation::Lyrics(command) => Reply::Lyrics(self.lyrics.execute(self, command).await?),
            Operation::ReadMedia(_) => return Err(invalid("not a control operation")),
        };
        Ok(Outcome::Reply(result))
    }

    pub(crate) async fn close(&self) -> io::Result<()> {
        let refresh = self
            .refresh
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
            .take();
        if let Some(refresh) = refresh {
            refresh.abort();
            drop(refresh.await);
        }
        self.library.close().await;
        self.lyrics.close().await;
        if let Some(source) = &self.source {
            source.close().await;
        }
        let closed = if let Some(media) = &self.media {
            media.close().await
        } else {
            Ok(())
        };
        closed.and(self.identities.close().await)
    }
}

fn supports(profile: u8, kind: MusicRepresentation, version: u32) -> bool {
    match (kind, version) {
        (MusicRepresentation::EncodedAudio, 1) => profile & MUSIC_ENCODED_AUDIO_V1 != 0,
        (MusicRepresentation::NoteEvents, 1) => profile & MUSIC_NOTE_EVENTS_V1 != 0,
        _ => false,
    }
}

fn version(value: &str) -> io::Result<Uuid> {
    let id = Uuid::parse_str(value).map_err(|_| invalid("invalid media version"))?;
    if value != id.simple().to_string() || id.is_nil() {
        return Err(invalid("noncanonical media version"));
    }
    Ok(id)
}

fn invalid(detail: &'static str) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidInput, detail)
}

#[derive(Debug)]
struct Failure(Code);
impl std::fmt::Display for Failure {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        self.0.as_str_name().fmt(formatter)
    }
}
impl std::error::Error for Failure {}
fn failure(code: Code) -> io::Error {
    io::Error::other(Failure(code))
}

pub(crate) fn wire_failure(error: &io::Error, fallback: Code) -> MusicFailure {
    let code = error
        .get_ref()
        .and_then(|error| error.downcast_ref::<Failure>())
        .map_or_else(
            || match error.kind() {
                io::ErrorKind::InvalidInput => Code::InvalidRequest,
                io::ErrorKind::WouldBlock => Code::Busy,
                io::ErrorKind::NotFound => Code::NotFound,
                io::ErrorKind::TimedOut => Code::DeadlineExceeded,
                io::ErrorKind::Unsupported => Code::Unavailable,
                _ => fallback,
            },
            |failure| failure.0,
        );
    MusicFailure { code: code.into() }
}

fn open_library(root: &Path) -> io::Result<Arc<library::Library>> {
    fs::DirBuilder::new()
        .recursive(true)
        .mode(0o700)
        .create(root)?;
    let meta = fs::symlink_metadata(root)?;
    if !meta.is_dir() || meta.permissions().mode() & 0o077 != 0 {
        return Err(invalid("music catalog must be a private directory"));
    }
    // The library holds the writer lock for the lifetime of the music service.
    library::Library::open(root.join("library"))
}
