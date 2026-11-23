//! Private, versioned, one-command-per-connection IPC for the local mikd CLI.
use super::{PaperServices, SocketListener};
use crate::domains::music::{LocalTrack, Service, SourceImport, SourceRefresh, SourceState};
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
    sync::RwLock,
    task::JoinSet,
    time::timeout,
};
use tokio_util::sync::CancellationToken;

const MAX_COMMAND: usize = 16 * 1024;
const MAX_RESULT: usize = 512 * 1024;
const FRAME_TIMEOUT: Duration = Duration::from_secs(5);
const COMMAND_TIMEOUT: Duration = Duration::from_secs(300);
const MAX_CONNECTIONS: usize = 8;

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
    ListSources,
    InspectSource(String),
    ImportSource(String),
    RefreshSources,
    RemoveSource(String),
    ListLibrary(String),
    InspectTrack(String),
    ImportLocal(PathBuf),
    ReplaceLocal { id: String, path: PathBuf },
    RetireTrack(String),
    SetLyrics { version: String, path: PathBuf },
    ClearLyrics(String),
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "snake_case", tag = "result", content = "data")]
pub(crate) enum Reply {
    Status {
        ready: Vec<String>,
        problems: BTreeMap<String, String>,
        web_api_port: u32,
        web_chat_port: u32,
        music_catalog: bool,
        music_source_registry: bool,
        music_media_cache: bool,
        paper_music: bool,
    },
    Sources(Vec<SourceState>),
    Source(SourceState),
    Imported(SourceImport),
    Refreshed(SourceRefresh),
    Removed {
        id: String,
    },
    Library(Vec<LocalTrack>),
    LocalTrack(LocalTrack),
    Track(serde_json::Value),
    LyricsEdited,
    Error(String),
}

impl Command {
    async fn execute(self, paper: &PaperServices, music: &MusicSlot) -> io::Result<Reply> {
        let paper_music = music.paper_ready.load(Ordering::Relaxed);
        let music = music.service.read().await;
        let music_service = || {
            music
                .as_ref()
                .ok_or_else(|| io::Error::other("music service is not ready"))
        };
        match self {
            Self::Status => {
                let status = paper.readiness().await;
                Ok(Reply::Status {
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
                })
            }
            Self::ListLibrary(after) => Ok(Reply::Library(music_service()?.list_local(&after)?)),
            Self::InspectTrack(id) => Ok(Reply::Track(music_service()?.inspect_track(&id).await?)),
            Self::ImportLocal(path) => Ok(Reply::LocalTrack(
                music_service()?.import_local(path, None).await?,
            )),
            Self::ReplaceLocal { id, path } => Ok(Reply::LocalTrack(
                music_service()?.import_local(path, Some(id)).await?,
            )),
            Self::RetireTrack(id) => {
                music_service()?.retire_track(id.clone()).await?;
                Ok(Reply::Removed { id })
            }
            Self::SetLyrics { version, path } => {
                music_service()?.edit_lyrics(&version, Some(path)).await?;
                Ok(Reply::LyricsEdited)
            }
            Self::ClearLyrics(version) => {
                music_service()?.edit_lyrics(&version, None).await?;
                Ok(Reply::LyricsEdited)
            }
            Self::ListSources => Ok(Reply::Sources(music_service()?.sources().await?)),
            Self::InspectSource(id) => music_service()?
                .sources()
                .await?
                .into_iter()
                .find(|source| source.id == id)
                .map(Reply::Source)
                .ok_or_else(|| io::Error::new(io::ErrorKind::NotFound, "unknown LX source ID")),
            Self::ImportSource(url) => {
                Ok(Reply::Imported(music_service()?.import_source(&url).await?))
            }
            Self::RefreshSources => Ok(Reply::Refreshed(music_service()?.refresh_sources().await?)),
            Self::RemoveSource(id) => {
                if !music_service()?.remove_source(&id).await? {
                    return Err(io::Error::new(
                        io::ErrorKind::NotFound,
                        "unknown subscription ID (local scripts are managed on disk)",
                    ));
                }
                Ok(Reply::Removed { id })
            }
        }
    }
}

pub(crate) async fn serve(
    listener: SocketListener,
    paper: Arc<PaperServices>,
    music: MusicSlot,
    stop: CancellationToken,
) -> io::Result<()> {
    let mut tasks = JoinSet::new();
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
                let music = music.clone();
                tasks.spawn(async move { drop(handle(socket, &paper, &music).await); });
            }
        }
    };
    while tasks.join_next().await.is_some() {}
    result
}

async fn handle(
    mut socket: UnixStream,
    paper: &PaperServices,
    music: &MusicSlot,
) -> io::Result<()> {
    let Request::V1(command) = timeout(FRAME_TIMEOUT, read(&mut socket, MAX_COMMAND)).await??;
    let reply = match timeout(COMMAND_TIMEOUT, command.execute(paper, music)).await {
        Ok(Ok(reply)) => reply,
        Ok(Err(error)) => Reply::Error(error.to_string()),
        Err(_) => Reply::Error("command timed out; check status before retrying".into()),
    };
    let packet = encode(&reply, MAX_RESULT).or_else(|_| {
        encode(
            &Reply::Error("admin response exceeds size limit".into()),
            MAX_RESULT,
        )
    })?;
    timeout(FRAME_TIMEOUT, write(&mut socket, &packet)).await?
}

pub(crate) async fn call(path: &Path, command: Command) -> io::Result<Reply> {
    let packet = encode(&Request::V1(command), MAX_COMMAND)?;
    let mut socket = timeout(FRAME_TIMEOUT, UnixStream::connect(path))
        .await?
        .map_err(|error| {
            io::Error::new(
                error.kind(),
                format!("admin socket {}: {error}", path.display()),
            )
        })?;
    timeout(FRAME_TIMEOUT, write(&mut socket, &packet)).await??;
    timeout(
        COMMAND_TIMEOUT + FRAME_TIMEOUT,
        read(&mut socket, MAX_RESULT),
    )
    .await?
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
