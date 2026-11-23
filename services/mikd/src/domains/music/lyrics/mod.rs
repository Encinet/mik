//! Immutable, media-version-bound documents. No provider text crosses the socket.
mod timeline;

use super::{Service, failure, invalid, version};
use mik_protocol::music::{
    LyricsCommand, LyricsCue, LyricsDocument, LyricsPage, LyricsRead, LyricsResult, LyricsTrack,
    lyrics_command::Operation,
    lyrics_result::{Content, State},
    lyrics_track::Role,
    music_failure::Code,
};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::{
    collections::{BTreeMap, BTreeSet, HashMap},
    fs,
    io::{self, Read, Write},
    os::unix::fs::OpenOptionsExt,
    path::{Path, PathBuf},
    sync::{Arc, Mutex},
    time::{Duration, Instant},
};
use tokio::sync::{Semaphore, watch};
use tokio_util::{sync::CancellationToken, task::TaskTracker};
use uuid::Uuid;

const MAX_BYTES: usize = 1024 * 1024;
const MAX_TEXT: usize = MAX_BYTES / 3;
const MAX_LINE: usize = 1024;
const MAX_ENTRIES: usize = 10_000;
const MAX_TRACKS: usize = 16;
const PAGE: usize = 64;
const TICKETS: usize = 64;
const TTL: Duration = Duration::from_secs(120);

#[derive(Default, PartialEq, Eq)]
pub(super) struct LyricsText {
    pub original: String,
    pub translation: String,
    pub romanization: String,
}

#[derive(Clone, Copy, Serialize, Deserialize, PartialEq, Eq, PartialOrd, Ord)]
#[serde(rename_all = "snake_case")]
enum Kind {
    Original,
    Translation,
    Pronunciation,
}

impl Kind {
    fn wire(self) -> Role {
        match self {
            Self::Original => Role::Original,
            Self::Translation => Role::Translation,
            Self::Pronunciation => Role::Pronunciation,
        }
    }
}

#[derive(Clone, Serialize, Deserialize)]
struct Cue {
    start_ms: u64,
    end_ms: Option<u64>,
    text: String,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(tag = "kind", rename_all = "snake_case")]
enum Body {
    Text { lines: Vec<String> },
    Timed { cues: Vec<Cue> },
}

impl Body {
    fn len(&self) -> usize {
        match self {
            Self::Text { lines } => lines.len(),
            Self::Timed { cues } => cues.len(),
        }
    }
}

#[derive(Clone, Serialize, Deserialize)]
struct Track {
    role: Kind,
    language_tag: Option<String>,
    #[serde(flatten)]
    body: Body,
}

struct Document {
    tracks: Vec<Track>,
}
struct Ticket {
    document: Arc<Document>,
    expires: Instant,
}
type Candidates = Result<Arc<Vec<Track>>, Code>;
type Fetch = (Instant, watch::Receiver<Option<Candidates>>);

pub(super) struct LyricsService {
    root: PathBuf,
    tickets: Mutex<HashMap<Uuid, Ticket>>,
    fetches: Mutex<HashMap<Uuid, Fetch>>,
    requests: Arc<Semaphore>,
    jobs: TaskTracker,
    stop: CancellationToken,
}

impl LyricsService {
    pub fn new(root: PathBuf) -> Self {
        Self {
            root,
            tickets: Mutex::default(),
            fetches: Mutex::default(),
            requests: Arc::new(Semaphore::new(4)),
            jobs: TaskTracker::new(),
            stop: CancellationToken::new(),
        }
    }

    pub async fn close(&self) {
        self.stop.cancel();
        self.jobs.close();
        self.jobs.wait().await;
    }

    pub async fn execute(
        &self,
        service: &Service,
        command: LyricsCommand,
    ) -> io::Result<LyricsResult> {
        match command
            .operation
            .ok_or_else(|| invalid("missing lyrics operation"))?
        {
            Operation::Lookup(open) => self.lookup(service, version(&open.media_version)?).await,
            Operation::Read(read) => self.read(read),
            Operation::Release(release) => {
                let id = ticket_id(&release.ticket)?;
                self.tickets
                    .lock()
                    .map_err(|_| io::Error::other("music state lock unavailable"))?
                    .remove(&id);
                Ok(result(State::Released, release.ticket, None))
            }
        }
    }

    async fn lookup(&self, service: &Service, version: Uuid) -> io::Result<LyricsResult> {
        let local = service.library.lyrics(version)?;
        let path = self.path(version);
        let (mut candidates, embedded) = tokio::task::spawn_blocking(move || {
            let overrides =
                match read_edits(&path, local.as_ref().and_then(|(_, duration)| *duration)) {
                    Ok(tracks) => tracks,
                    Err(error) if error.kind() == io::ErrorKind::NotFound => Vec::new(),
                    Err(error) => return Err(error),
                };
            let embedded = local.map(|(text, duration)| timeline::parse(&text, true, duration));
            Ok::<_, io::Error>((overrides, embedded))
        })
        .await
        .map_err(io::Error::other)??;
        if let Some(embedded) = embedded {
            if let Some(body) = embedded {
                candidates.push(Track {
                    role: Kind::Original,
                    language_tag: None,
                    body,
                });
            }
        } else {
            let origin = if let Some(media) = &service.media {
                media.version_source(version).await
            } else {
                None
            };
            let (key, provider) = origin.ok_or_else(|| failure(Code::VersionGone))?;
            if let Some(source) = &service.source {
                match self.remote(version, source.clone(), key, provider) {
                    Ok(Some(tracks)) => candidates.extend(tracks.iter().cloned()),
                    Ok(None) => {
                        if candidates.is_empty() {
                            return Ok(result(State::Pending, Vec::new(), None));
                        }
                    }
                    Err(_) if !candidates.is_empty() => {}
                    Err(error) => return Err(error),
                }
            } else if candidates.is_empty() {
                return Err(failure(Code::Unavailable));
            }
        }
        let mut seen = BTreeMap::new();
        for track in candidates {
            seen.entry((track.role, track.language_tag.clone()))
                .or_insert(track);
        }
        if seen.is_empty() {
            return Ok(result(State::Empty, Vec::new(), None));
        }
        let tracks = seen.into_values().take(MAX_TRACKS).collect::<Vec<_>>();
        let revision = hex::encode(Sha256::digest(
            serde_json::to_vec(&tracks).map_err(io::Error::other)?,
        ));
        let mut header = LyricsDocument {
            media_version: version.simple().to_string(),
            revision,
            ..Default::default()
        };
        for (key, track) in (1_u32..).zip(&tracks) {
            if header.default_original_track.is_none() && track.role == Kind::Original {
                header.default_original_track = Some(key);
            }
            header.tracks.push(LyricsTrack {
                track_key: key,
                role: track.role.wire().into(),
                language_tag: track.language_tag.clone(),
                timed: matches!(track.body, Body::Timed { .. }),
            });
        }
        let mut tickets = self
            .tickets
            .lock()
            .map_err(|_| io::Error::other("music state lock unavailable"))?;
        tickets.retain(|_, ticket| ticket.expires > Instant::now());
        if tickets.len() == TICKETS {
            return Err(failure(Code::Busy));
        }
        let id = Uuid::new_v4();
        tickets.insert(
            id,
            Ticket {
                document: Arc::new(Document { tracks }),
                expires: Instant::now() + TTL,
            },
        );
        Ok(result(
            State::Ready,
            id.as_bytes().to_vec(),
            Some(Content::Document(header)),
        ))
    }

    fn remote(
        &self,
        version: Uuid,
        source: super::Source,
        key: Arc<super::TrackRef>,
        provider: String,
    ) -> io::Result<Option<Arc<Vec<Track>>>> {
        let response = {
            let mut fetches = self
                .fetches
                .lock()
                .map_err(|_| io::Error::other("music state lock unavailable"))?;
            fetches.retain(|_, (expires, _)| *expires > Instant::now());
            if let Some((_, response)) = fetches.get(&version) {
                let current = response.borrow().clone();
                // Surface a completed error once, then allow a later lookup to try again.
                if matches!(current, Some(Err(_))) {
                    fetches.remove(&version);
                }
                current
            } else {
                if fetches.len() == 32
                    && let Some(oldest) = fetches
                        .iter()
                        .min_by_key(|(_, (expiry, _))| *expiry)
                        .map(|(id, _)| *id)
                {
                    fetches.remove(&oldest);
                }
                let permit = self
                    .requests
                    .clone()
                    .try_acquire_owned()
                    .map_err(|_| failure(Code::Busy))?;
                let (tx, rx) = watch::channel(None);
                let stop = self.stop.clone();
                self.jobs.spawn(async move {
                    let _permit = permit;
                    let candidates = tokio::select! {
                        () = stop.cancelled() => Err(Code::Unavailable),
                        result = async {
                            let mut input = source.resolver_input(&key).await.map_err(|_| Code::Unavailable)?.ok_or(Code::VersionGone)?;
                            input.provider = provider;
                            let text = source.lyrics(&key, &input).await.map_err(|_| Code::Unavailable)?;
                            let tracks = [(Kind::Original, text.original), (Kind::Translation, text.translation), (Kind::Pronunciation, text.romanization)]
                                .into_iter().filter_map(|(role, text)| Some(Track { role, language_tag: None, body: timeline::parse(&text, false, None)? })).collect();
                            Ok(Arc::new(tracks))
                        } => result,
                    };
                    tx.send_replace(Some(candidates));
                });
                fetches.insert(version, (Instant::now() + TTL, rx));
                None
            }
        };
        response.transpose().map_err(failure)
    }

    fn read(&self, read: LyricsRead) -> io::Result<LyricsResult> {
        let id = ticket_id(&read.ticket)?;
        let document = self
            .tickets
            .lock()
            .map_err(|_| io::Error::other("music state lock unavailable"))?
            .get(&id)
            .filter(|ticket| ticket.expires > Instant::now())
            .map(|ticket| ticket.document.clone())
            .ok_or_else(|| failure(Code::CursorExpired))?;
        let index = read
            .track_key
            .checked_sub(1)
            .ok_or_else(|| invalid("invalid lyrics track key"))? as usize;
        let track = document
            .tracks
            .get(index)
            .ok_or_else(|| invalid("invalid lyrics track key"))?;
        let offset = read.offset as usize;
        if offset >= track.body.len() {
            return Err(invalid("invalid lyrics offset"));
        }
        let end = (offset + PAGE).min(track.body.len());
        let mut page = LyricsPage {
            track_key: read.track_key,
            offset: read.offset,
            next_offset: (end < track.body.len())
                .then_some(u32::try_from(end).map_err(io::Error::other)?),
            ..Default::default()
        };
        match &track.body {
            Body::Text { lines } => page.lines = lines[offset..end].to_vec(),
            Body::Timed { cues } => {
                page.cues = cues[offset..end]
                    .iter()
                    .map(|cue| LyricsCue {
                        start_ms: cue.start_ms,
                        end_ms: cue.end_ms,
                        text: cue.text.clone(),
                    })
                    .collect();
            }
        }
        Ok(result(State::Ready, read.ticket, Some(Content::Page(page))))
    }

    pub async fn edit(
        &self,
        version: Uuid,
        duration: Option<u64>,
        input: Option<PathBuf>,
    ) -> io::Result<()> {
        let path = self.path(version);
        let root = self.root.clone();
        self.jobs
            .spawn_blocking(move || {
                let Some(input) = input else {
                    match fs::remove_file(path) {
                        Ok(()) => return fs::File::open(root)?.sync_all(),
                        Err(error) if error.kind() == io::ErrorKind::NotFound => return Ok(()),
                        Err(error) => return Err(error),
                    }
                };
                let tracks = import_edits(&input, duration)?;
                std::os::unix::fs::DirBuilderExt::mode(
                    fs::DirBuilder::new().recursive(true),
                    0o700,
                )
                .create(&root)?;
                let bytes = serde_json::to_vec(&tracks).map_err(io::Error::other)?;
                if bytes.len() > MAX_BYTES {
                    return Err(invalid("lyrics document over budget"));
                }
                let mut file = tempfile::NamedTempFile::new_in(&root)?;
                file.write_all(&bytes)?;
                file.as_file().sync_all()?;
                file.persist(path).map_err(io::Error::other)?;
                fs::File::open(root)?.sync_all()
            })
            .await
            .map_err(io::Error::other)?
    }

    fn path(&self, version: Uuid) -> PathBuf {
        self.root.join(format!("{}.json", version.simple()))
    }
}

impl Drop for LyricsService {
    fn drop(&mut self) {
        self.stop.cancel();
    }
}

fn ticket_id(bytes: &[u8]) -> io::Result<Uuid> {
    Uuid::from_slice(bytes).map_err(|_| invalid("invalid lyrics ticket"))
}
fn result(state: State, ticket: Vec<u8>, content: Option<Content>) -> LyricsResult {
    LyricsResult {
        state: state.into(),
        ticket,
        content,
    }
}

fn read_edits(path: &Path, duration: Option<u64>) -> io::Result<Vec<Track>> {
    let mut tracks: Vec<Track> =
        serde_json::from_slice(&read_bounded(path)?).map_err(io::Error::other)?;
    validate(&mut tracks, duration)?;
    Ok(tracks)
}

fn import_edits(path: &Path, duration: Option<u64>) -> io::Result<Vec<Track>> {
    let Some(extension) = path.extension().and_then(std::ffi::OsStr::to_str) else {
        return Err(invalid("expected a .json, .lrc or .txt lyrics file"));
    };
    let body = match extension.to_ascii_lowercase().as_str() {
        "json" => return read_edits(path, duration),
        "lrc" => {
            let source = String::from_utf8(read_bounded(path)?).map_err(io::Error::other)?;
            timeline::parse(&source, true, duration)
                .ok_or_else(|| invalid("empty or oversized LRC"))?
        }
        "txt" => {
            let source = String::from_utf8(read_bounded(path)?).map_err(io::Error::other)?;
            let lines = source
                .trim_start_matches('\u{feff}')
                .lines()
                .map(str::trim)
                .filter(|line| !line.is_empty())
                .map(str::to_owned)
                .collect();
            Body::Text { lines }
        }
        _ => return Err(invalid("expected a .json, .lrc or .txt lyrics file")),
    };
    let mut tracks = vec![Track {
        role: Kind::Original,
        language_tag: None,
        body,
    }];
    validate(&mut tracks, duration)?;
    Ok(tracks)
}

fn read_bounded(path: &Path) -> io::Result<Vec<u8>> {
    let flags = rustix::fs::OFlags::NONBLOCK | rustix::fs::OFlags::NOFOLLOW;
    let file = fs::OpenOptions::new()
        .read(true)
        .custom_flags(flags.bits().cast_signed())
        .open(path)?;
    let meta = file.metadata()?;
    if !meta.is_file() || meta.len() > MAX_BYTES as u64 {
        return Err(invalid("invalid lyrics document file"));
    }
    let mut bytes = Vec::new();
    file.take(MAX_BYTES as u64 + 1).read_to_end(&mut bytes)?;
    if bytes.len() > MAX_BYTES {
        return Err(invalid("lyrics document over budget"));
    }
    Ok(bytes)
}

fn validate(tracks: &mut [Track], duration: Option<u64>) -> io::Result<()> {
    if tracks.is_empty() || tracks.len() > MAX_TRACKS {
        return Err(invalid("invalid lyrics track count"));
    }
    let mut seen = BTreeSet::new();
    for track in tracks {
        if let Some(tag) = track.language_tag.as_mut() {
            if tag.len() > 63 {
                return Err(invalid("invalid lyrics language tag"));
            }
            let tag_value = language_tags::LanguageTag::parse(tag)
                .map_err(|_| invalid("invalid lyrics language tag"))?;
            tag_value
                .validate()
                .map_err(|_| invalid("invalid lyrics language tag"))?;
            *tag = tag_value
                .canonicalize()
                .map_err(|_| invalid("invalid lyrics language tag"))?
                .into_string();
        }
        if !seen.insert((track.role, track.language_tag.clone()))
            || !(1..=MAX_ENTRIES).contains(&track.body.len())
        {
            return Err(invalid("invalid or duplicate lyrics track"));
        }
        let valid_text =
            |text: &str| text.len() <= MAX_LINE && !text.trim().is_empty() && !text.contains('\0');
        match &mut track.body {
            Body::Text { lines } => {
                if !lines.iter().all(|line| valid_text(line)) {
                    return Err(invalid("invalid lyric line"));
                }
            }
            Body::Timed { cues } => {
                let mut previous_end = 0;
                for cue in cues.iter_mut() {
                    if !valid_text(&cue.text)
                        || cue.start_ms < previous_end
                        || cue.end_ms.is_some_and(|end| end <= cue.start_ms)
                    {
                        return Err(invalid("overlapping or invalid lyric intervals"));
                    }
                    previous_end = cue.end_ms.unwrap_or(u64::MAX);
                    if let Some(duration) = duration {
                        cue.end_ms = Some(previous_end.min(duration));
                    }
                }
                cues.retain(|cue| cue.end_ms.is_none_or(|end| end > cue.start_ms));
                if cues.is_empty() {
                    return Err(invalid("lyric track is outside media duration"));
                }
            }
        }
    }
    Ok(())
}
