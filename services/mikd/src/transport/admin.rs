//! Private, versioned, one-command-per-connection IPC for local administration.
use super::{PaperServices, SocketListener};
use crate::console::{CommandOutcome, Console, LogPage, LogPosition, ProviderStatus, Target};
use crate::domains::music::{
    CacheCleanup, CacheEntry, CachePage, CatalogPage, CatalogQuery, LocalTrack, LyricsInspection,
    PreparedTrack, Service, SourceImport, SourceRefresh, SourceState, TrackInspection, VersionPage,
};
use serde::{Deserialize, Serialize, de::DeserializeOwned};
use std::{
    collections::BTreeMap,
    io,
    path::{Path, PathBuf},
    sync::{
        Arc,
        atomic::{AtomicBool, Ordering},
    },
    time::Duration,
};
use tokio::{
    io::{AsyncReadExt, AsyncWriteExt},
    net::UnixStream,
    sync::{RwLock, RwLockReadGuard, Semaphore},
    task::JoinSet,
    time::timeout,
};
use tokio_util::sync::CancellationToken;

const MAX_COMMAND: usize = 16 * 1024;
const MAX_RESULT: usize = 512 * 1024;
const FRAME_TIMEOUT: Duration = Duration::from_secs(5);
const COMMAND_TIMEOUT: Duration = Duration::from_secs(300);
const MAX_CONNECTIONS: usize = 24;
const MAX_MUSIC_COMMANDS: usize = 8;
const TIMED_OUT: &str = "command timed out; check status before retrying";
const RESULT_TOO_LARGE: &str = "admin response exceeds size limit";

pub(crate) type MusicSlot = Arc<MusicState>;

#[derive(Default)]
pub(crate) struct MusicState {
    pub service: RwLock<Option<Arc<Service>>>,
    pub paper_ready: AtomicBool,
}

pub(crate) fn socket_path(control: &Path) -> PathBuf {
    control.with_extension("admin.sock")
}

#[derive(Serialize, Deserialize)]
pub(crate) enum Request {
    V1(Command),
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "snake_case", tag = "command", content = "argument")]
pub(crate) enum Command {
    Status,
    ListConsoles,
    ReadConsoleLogs {
        target: Target,
        position: LogPosition,
    },
    SubmitConsoleInput {
        target: Target,
        line: String,
    },
    ListSources,
    InspectSource(String),
    FollowSource(String),
    ImportSource(String),
    UpdateSource {
        id: String,
        url: String,
    },
    SetSourceEnabled {
        id: String,
        enabled: bool,
    },
    RefreshSources,
    RefreshSource(String),
    RemoveSource(String),
    TestSourcePlayback {
        source_id: String,
        track_id: String,
    },
    ListLibrary(String),
    QueryCatalog {
        query: CatalogQuery,
        after: String,
    },
    InspectTrack(String),
    TrackVersions {
        id: String,
        after: String,
    },
    PrepareTrack(String),
    ImportLocal(PathBuf),
    ReplaceLocal {
        id: String,
        path: PathBuf,
    },
    RetireTrack(String),
    RestoreTrack(String),
    ListCache(String),
    ListSourceCache {
        id: String,
        after: String,
    },
    InspectCache(String),
    EvictCache(String),
    ClearIdleCache,
    InspectLyrics(String),
    SetLyrics {
        version: String,
        path: PathBuf,
    },
    ClearLyrics(String),
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "snake_case", tag = "result", content = "data")]
pub(crate) enum Reply {
    Status(Status),
    Consoles(Vec<ProviderStatus>),
    ConsoleLogs(LogPage),
    ConsoleCommand(CommandOutcome),
    Sources(Vec<SourceState>),
    Source(SourceState),
    SourceMissing(String),
    Imported(SourceImport),
    SourceUpdated {
        id: String,
    },
    SourceEnabled {
        id: String,
        enabled: bool,
    },
    SourcePlaybackTested {
        source_id: String,
        track_id: String,
    },
    Refreshed(SourceRefresh),
    Removed {
        id: String,
    },
    Library(Vec<LocalTrack>),
    Catalog(CatalogPage),
    LocalTrack(LocalTrack),
    Track(#[serde(serialize_with = "serialize_track")] Box<TrackInspection>),
    Versions(VersionPage),
    Prepared(PreparedTrack),
    Restored {
        id: String,
    },
    CachePage(CachePage),
    CacheEntry(Box<CacheEntry>),
    CacheEvicted {
        version: String,
        released_bytes: u64,
    },
    CacheCleared(CacheCleanup),
    Lyrics(LyricsInspection),
    LyricsEdited,
    Error(String),
}

#[derive(Serialize, Deserialize)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "v1 reports independent readiness flags"
)]
pub(crate) struct Status {
    pub ready: Vec<String>,
    pub problems: BTreeMap<String, String>,
    pub web_api_port: u32,
    pub web_chat_port: u32,
    pub music_catalog: bool,
    pub music_source_registry: bool,
    pub music_media_cache: bool,
    pub paper_music: bool,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub minecraft: Option<MinecraftStatus>,
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "snake_case", tag = "state")]
pub(crate) enum MinecraftStatus {
    NoRecentSample,
    Live {
        version: String,
        visible_players: usize,
        max_players: u32,
        tps: Option<f64>,
        mspt: Option<f64>,
        uptime_seconds: Option<u64>,
    },
}

impl Reply {
    pub(crate) fn is_unconfirmed(error: &str) -> bool {
        matches!(error, TIMED_OUT | RESULT_TOO_LARGE)
    }
}

fn serialize_track<S: serde::Serializer>(
    track: &TrackInspection,
    serializer: S,
) -> Result<S::Ok, S::Error> {
    // Keep the v1 key order of the JSON object printed by the explicit CLI command.
    serde_json::to_value(track)
        .map_err(serde::ser::Error::custom)?
        .serialize(serializer)
}

impl Command {
    async fn execute(
        self,
        paper: &PaperServices,
        consoles: &Console,
        music: &MusicSlot,
        music_commands: &Semaphore,
    ) -> io::Result<Reply> {
        match self {
            Self::Status => Ok(Reply::Status(status(paper, music).await)),
            Self::ListConsoles => Ok(Reply::Consoles(consoles.list())),
            Self::ReadConsoleLogs { target, position } => {
                Ok(Reply::ConsoleLogs(consoles.logs(target, position).await?))
            }
            Self::SubmitConsoleInput { target, line } => {
                Ok(Reply::ConsoleCommand(consoles.execute(target, line).await?))
            }
            command => {
                let _permit = music_commands
                    .try_acquire()
                    .map_err(|_| io::Error::other("Music administration is busy"))?;
                let service = music.service().await?;
                command.execute_music(&service).await
            }
        }
    }

    async fn execute_music(self, music: &Service) -> io::Result<Reply> {
        match self {
            Self::ListLibrary(after) => Ok(Reply::Library(music.list_local(&after)?)),
            Self::QueryCatalog { query, after } => {
                Ok(Reply::Catalog(music.query_catalog(query, after).await?))
            }
            Self::InspectTrack(id) => Ok(Reply::Track(Box::new(music.inspect_track(&id).await?))),
            Self::TrackVersions { id, after } => {
                Ok(Reply::Versions(music.track_versions(&id, &after).await?))
            }
            Self::PrepareTrack(id) => Ok(Reply::Prepared(music.prepare_track(&id).await?)),
            Self::ImportLocal(path) => Ok(Reply::LocalTrack(music.import_local(path, None).await?)),
            Self::ReplaceLocal { id, path } => {
                Ok(Reply::LocalTrack(music.import_local(path, Some(id)).await?))
            }
            Self::RetireTrack(id) => {
                music.retire_track(id.clone()).await?;
                Ok(Reply::Removed { id })
            }
            Self::RestoreTrack(id) => {
                music.restore_track(id.clone()).await?;
                Ok(Reply::Restored { id })
            }
            Self::ListCache(after) => Ok(Reply::CachePage(music.list_cache(&after, None).await?)),
            Self::ListSourceCache { id, after } => {
                Ok(Reply::CachePage(music.list_cache(&after, Some(&id)).await?))
            }
            Self::InspectCache(version) => Ok(Reply::CacheEntry(Box::new(
                music.inspect_cache(&version).await?,
            ))),
            Self::EvictCache(version) => {
                let released_bytes = music.evict_cache(&version).await?;
                Ok(Reply::CacheEvicted {
                    version,
                    released_bytes,
                })
            }
            Self::ClearIdleCache => Ok(Reply::CacheCleared(music.clear_idle_cache().await?)),
            Self::InspectLyrics(version) => {
                Ok(Reply::Lyrics(music.inspect_lyrics(&version).await?))
            }
            Self::SetLyrics { version, path } => {
                music.edit_lyrics(&version, Some(path)).await?;
                Ok(Reply::LyricsEdited)
            }
            Self::ClearLyrics(version) => {
                music.edit_lyrics(&version, None).await?;
                Ok(Reply::LyricsEdited)
            }
            Self::ListSources => Ok(Reply::Sources(music.sources().await?)),
            Self::InspectSource(id) => Ok(Reply::Source(music.inspect_source(&id).await?)),
            Self::FollowSource(id) => Ok(music
                .find_source(&id)
                .await?
                .map_or(Reply::SourceMissing(id), Reply::Source)),
            Self::ImportSource(url) => Ok(Reply::Imported(music.import_source(&url).await?)),
            Self::UpdateSource { id, url } => {
                music.update_source(&id, &url).await?;
                Ok(Reply::SourceUpdated { id })
            }
            Self::SetSourceEnabled { id, enabled } => {
                music.set_source_enabled(&id, enabled).await?;
                Ok(Reply::SourceEnabled { id, enabled })
            }
            Self::RefreshSources => Ok(Reply::Refreshed(music.refresh_sources(None).await?)),
            Self::RefreshSource(id) => {
                Ok(Reply::Refreshed(music.refresh_sources(Some(&id)).await?))
            }
            Self::RemoveSource(id) => {
                music.remove_source(&id).await?;
                Ok(Reply::Removed { id })
            }
            Self::TestSourcePlayback {
                source_id,
                track_id,
            } => {
                music.test_source_playback(&source_id, &track_id).await?;
                Ok(Reply::SourcePlaybackTested {
                    source_id,
                    track_id,
                })
            }
            _ => unreachable!(),
        }
    }
}

async fn status(paper: &PaperServices, music: &MusicState) -> Status {
    let status = paper.readiness().await;
    let paper_music = music.paper_ready.load(Ordering::Relaxed);
    let music = music.service.read().await;
    let minecraft = if let Some(snapshot) = paper.game.state.current() {
        let server = &snapshot.server;
        MinecraftStatus::Live {
            version: server.version.clone(),
            visible_players: snapshot.players.len(),
            max_players: server.max_players,
            tps: tick_metric(server.tps),
            mspt: tick_metric(server.mspt),
            uptime_seconds: u64::try_from(server.uptime_seconds).ok(),
        }
    } else {
        MinecraftStatus::NoRecentSample
    };
    Status {
        ready: status.ready_domains,
        problems: status
            .domain_problems
            .into_iter()
            .map(|(domain, code)| {
                let problem = mik_protocol::wire::status::DomainProblem::try_from(code)
                    .map_or("UNKNOWN", |problem| problem.as_str_name());
                (domain, problem.into())
            })
            .collect(),
        web_api_port: status.web_api_port,
        web_chat_port: status.web_chat_port,
        music_catalog: music.is_some(),
        music_source_registry: music
            .as_ref()
            .is_some_and(|music| music.source_registry_ready()),
        music_media_cache: music
            .as_ref()
            .is_some_and(|music| music.media_cache_ready()),
        paper_music,
        minecraft: Some(minecraft),
    }
}

fn tick_metric(value: f64) -> Option<f64> {
    (value.is_finite() && value >= 0.0).then_some(value)
}

impl MusicState {
    async fn service(&self) -> io::Result<RwLockReadGuard<'_, Arc<Service>>> {
        RwLockReadGuard::try_map(self.service.read().await, Option::as_ref)
            .map_err(|_| io::Error::other("music service is not ready"))
    }
}

pub(crate) async fn serve(
    listener: SocketListener,
    paper: Arc<PaperServices>,
    consoles: Arc<Console>,
    music: MusicSlot,
    stop: CancellationToken,
) -> io::Result<()> {
    let mut tasks = JoinSet::new();
    let music_commands = Arc::new(Semaphore::new(MAX_MUSIC_COMMANDS));
    let result = loop {
        tokio::select! {
            biased;
            () = stop.cancelled() => break Ok(()),
            Some(result) = tasks.join_next(), if !tasks.is_empty() => {
                if result.is_err() {
                    eprintln!("mikd admin connection stopped unexpectedly");
                }
            }
            accepted = listener.accept(), if tasks.len() < MAX_CONNECTIONS => {
                let socket = match accepted { Ok(socket) => socket, Err(error) => break Err(error) };
                let paper = paper.clone();
                let consoles = consoles.clone();
                let music = music.clone();
                let music_commands = music_commands.clone();
                tasks.spawn(async move { drop(handle(socket, &paper, &consoles, &music, &music_commands).await); });
            }
        }
    };
    while tasks.join_next().await.is_some() {}
    result
}

async fn handle(
    mut socket: UnixStream,
    paper: &PaperServices,
    consoles: &Console,
    music: &MusicSlot,
    music_commands: &Semaphore,
) -> io::Result<()> {
    let Request::V1(command) = timeout(FRAME_TIMEOUT, read(&mut socket, MAX_COMMAND)).await??;
    let reply = match timeout(
        COMMAND_TIMEOUT,
        command.execute(paper, consoles, music, music_commands),
    )
    .await
    {
        Ok(Ok(reply)) => reply,
        Ok(Err(error)) => Reply::Error(error.to_string()),
        Err(_) => Reply::Error(TIMED_OUT.into()),
    };
    let packet = encode(&reply, MAX_RESULT)
        .or_else(|_| encode(&Reply::Error(RESULT_TOO_LARGE.into()), MAX_RESULT))?;
    timeout(FRAME_TIMEOUT, write(&mut socket, &packet)).await?
}

pub(crate) async fn call(path: &Path, command: Command) -> io::Result<Reply> {
    call_with_outcome(path, command)
        .await
        .map_err(|error| match error {
            CallError::NotSent(error) | CallError::Unconfirmed(error) => error,
        })
}

pub(crate) enum CallError {
    NotSent(io::Error),
    Unconfirmed(io::Error),
}

pub(crate) async fn call_with_outcome(path: &Path, command: Command) -> Result<Reply, CallError> {
    let packet = encode(&Request::V1(command), MAX_COMMAND).map_err(CallError::NotSent)?;
    let connected = timeout(FRAME_TIMEOUT, UnixStream::connect(path))
        .await
        .map_err(|error| CallError::NotSent(error.into()))?;
    let mut socket = connected.map_err(|error| {
        CallError::NotSent(io::Error::new(
            error.kind(),
            format!("admin socket {}: {error}", path.display()),
        ))
    })?;
    timeout(FRAME_TIMEOUT, write(&mut socket, &packet))
        .await
        .map_err(|error| CallError::Unconfirmed(error.into()))?
        .map_err(CallError::Unconfirmed)?;
    timeout(
        COMMAND_TIMEOUT + FRAME_TIMEOUT,
        read(&mut socket, MAX_RESULT),
    )
    .await
    .map_err(|error| CallError::Unconfirmed(error.into()))?
    .map_err(CallError::Unconfirmed)
}

fn encode(value: &impl Serialize, limit: usize) -> io::Result<Vec<u8>> {
    let bytes = serde_json::to_vec(value).map_err(io::Error::other)?;
    if bytes.len() > limit {
        return Err(io::Error::new(
            io::ErrorKind::InvalidInput,
            "admin frame exceeds size limit",
        ));
    }
    Ok(bytes)
}

async fn read<T: DeserializeOwned>(socket: &mut UnixStream, limit: usize) -> io::Result<T> {
    let len = socket.read_u32().await? as usize;
    if !(1..=limit).contains(&len) {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            "invalid admin frame length",
        ));
    }
    let mut data = vec![0; len];
    socket.read_exact(&mut data).await?;
    serde_json::from_slice(&data).map_err(io::Error::other)
}

async fn write(socket: &mut UnixStream, packet: &[u8]) -> io::Result<()> {
    socket
        .write_u32(u32::try_from(packet.len()).map_err(io::Error::other)?)
        .await?;
    socket.write_all(packet).await
}
