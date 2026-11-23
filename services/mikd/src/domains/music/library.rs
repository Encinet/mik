//! Persistent originals. Published objects are immutable and never enter the LX cache.
use super::{
    Lease, notes,
    track::{TrackId, label, search_rank, status},
};
use mik_protocol::music::{
    MusicMediaDescriptor, MusicRepresentation, MusicTrackData,
    music_failure::Code,
    music_track_data::{Availability, State},
};
use prost::Message;
use serde::{Deserialize, Serialize};
use std::{
    collections::BTreeMap,
    fs::{self, DirBuilder, File, OpenOptions},
    io::{self, Read, Seek, Write},
    os::unix::fs::{DirBuilderExt, OpenOptionsExt, PermissionsExt},
    path::{Path, PathBuf},
    process::{Command, Stdio},
    sync::{Arc, Mutex},
    time::{Duration, Instant},
};
use tempfile::NamedTempFile;
use tokio_util::task::TaskTracker;
use uuid::Uuid;

const PAGE: usize = 24;
const AUDIO_BYTES: u64 = 512 * 1024 * 1024;
const NBS_BYTES: u64 = 16 * 1024 * 1024;
const PROBE_BYTES: u64 = 2 * 1024 * 1024;
const LYRICS_BYTES: usize = 1024 * 1024;

#[derive(Clone, Serialize, Deserialize)]
pub(crate) struct LocalTrack {
    pub id: String,
    #[serde(with = "uuid::serde::simple")]
    version: Uuid,
    title: String,
    artist: String,
    album: String,
    original_author: String,
    format: String,
    duration_ms: Option<i64>,
    sample_rate_hz: Option<u32>,
    bytes: u64,
    notes: Option<Notes>,
    #[serde(skip_serializing_if = "str::is_empty", default)]
    lyrics: Arc<str>,
}

#[derive(Clone, Serialize, Deserialize)]
struct Notes {
    bytes: u64,
    seekable: bool,
}

#[derive(Clone, Serialize, Deserialize)]
struct MissingTrack {
    title: String,
    retired: bool,
}

#[derive(Clone, Serialize, Deserialize)]
struct Index {
    #[serde(skip, default = "Uuid::new_v4")]
    revision: Uuid,
    published: BTreeMap<String, Uuid>,
    versions: BTreeMap<Uuid, Arc<LocalTrack>>,
    retired: BTreeMap<String, Uuid>,
    missing: BTreeMap<String, MissingTrack>,
}

impl Default for Index {
    fn default() -> Self {
        Self {
            revision: Uuid::new_v4(),
            published: BTreeMap::new(),
            versions: BTreeMap::new(),
            retired: BTreeMap::new(),
            missing: BTreeMap::new(),
        }
    }
}

pub(super) struct Library {
    root: PathBuf,
    owner: File,
    index: Mutex<Index>,
    mutation: Mutex<()>,
    jobs: TaskTracker,
}

impl Library {
    pub fn open(root: PathBuf) -> io::Result<Arc<Self>> {
        DirBuilder::new()
            .recursive(true)
            .mode(0o700)
            .create(&root)?;
        let metadata = fs::symlink_metadata(&root)?;
        if !metadata.is_dir() || metadata.permissions().mode() & 0o077 != 0 {
            return Err(io::Error::other(
                "local music library must be a private directory",
            ));
        }
        let owner = OpenOptions::new()
            .read(true)
            .write(true)
            .create(true)
            .truncate(false)
            .mode(0o600)
            .open(root.join("owner.lock"))?;
        owner.try_lock().map_err(io::Error::other)?;
        let index: Index = match File::open(root.join("catalog.json")) {
            Ok(file) => {
                serde_json::from_reader(io::BufReader::new(file)).map_err(io::Error::other)?
            }
            Err(error) if error.kind() == io::ErrorKind::NotFound => Index::default(),
            Err(error) => return Err(error),
        };
        if index
            .published
            .iter()
            .chain(&index.retired)
            .any(|(id, version)| {
                index
                    .versions
                    .get(version)
                    .is_none_or(|entry| entry.id != *id)
            })
            || index.versions.iter().any(|(version, entry)| {
                *version != entry.version
                    || TrackId::parse(&entry.id).is_err()
                    || content_type(&entry.format).is_none()
            })
            || index.missing.keys().any(|id| {
                TrackId::parse(id).is_err()
                    || index.published.contains_key(id)
                    || index.retired.contains_key(id)
            })
            || index
                .published
                .keys()
                .any(|id| index.retired.contains_key(id))
        {
            return Err(io::Error::other("invalid local music index"));
        }
        Ok(Arc::new(Self {
            root,
            owner,
            index: Mutex::new(index),
            mutation: Mutex::new(()),
            jobs: TaskTracker::new(),
        }))
    }

    fn index(&self) -> io::Result<std::sync::MutexGuard<'_, Index>> {
        self.index
            .lock()
            .map_err(|_| io::Error::other("local music index poisoned"))
    }

    pub fn browse(&self, after: &str, count: usize) -> io::Result<(Uuid, Vec<MusicTrackData>)> {
        let index = self.index()?;
        let rows = index
            .published
            .range::<str, _>((std::ops::Bound::Excluded(after), std::ops::Bound::Unbounded))
            .take(count)
            .map(|(_, version)| index.versions[version].wire(State::Active))
            .collect();
        Ok((index.revision, rows))
    }

    pub fn revision(&self) -> io::Result<Uuid> {
        Ok(self.index()?.revision)
    }

    pub fn search(&self, query: &str, limit: usize) -> io::Result<(Vec<MusicTrackData>, bool)> {
        let index = self.index()?;
        let mut result = BTreeMap::new();
        let mut partial = false;
        for (id, version) in &index.published {
            let track = &index.versions[version];
            let rank = search_rank(
                query,
                &track.title,
                &track.artist,
                &track.album,
                &track.original_author,
            );
            if let Some(rank) = rank {
                result.insert((rank, id), track);
                if result.len() > limit {
                    result.pop_last();
                    partial = true;
                }
            }
        }
        Ok((
            result
                .into_values()
                .map(|track| track.wire(State::Active))
                .collect(),
            partial,
        ))
    }

    pub fn track(&self, id: TrackId) -> io::Result<Option<MusicTrackData>> {
        let index = self.index()?;
        let key = id.to_string();
        Ok(if let Some(version) = index.published.get(&key) {
            Some(index.versions[version].wire(State::Active))
        } else if let Some(version) = index.retired.get(&key) {
            Some(index.versions[version].wire(State::Retired))
        } else if let Some(missing) = index.missing.get(&key) {
            let mut row = status(
                id,
                if missing.retired {
                    State::Retired
                } else {
                    State::Active
                },
                Availability::Unavailable,
            );
            row.title.clone_from(&missing.title);
            Some(row)
        } else {
            None
        })
    }

    pub fn inspect(&self, id: &str) -> io::Result<LocalTrack> {
        let index = self.index()?;
        index
            .published
            .get(id)
            .and_then(|version| index.versions.get(version))
            .map(|entry| entry.summary())
            .ok_or_else(not_found)
    }

    pub fn list(&self, after: &str) -> io::Result<Vec<LocalTrack>> {
        let index = self.index()?;
        Ok(index
            .published
            .range::<str, _>((std::ops::Bound::Excluded(after), std::ops::Bound::Unbounded))
            .take(PAGE)
            .map(|(_, version)| index.versions[version].summary())
            .collect())
    }

    pub fn open_track(&self, id: TrackId, exact: Option<Uuid>) -> io::Result<Lease> {
        let index = self.index()?;
        let key = id.to_string();
        if index.retired.contains_key(&key) {
            return Err(super::failure(Code::Retired));
        }
        if let Some(missing) = index.missing.get(&key) {
            return Err(super::failure(if missing.retired {
                Code::Retired
            } else {
                Code::Unavailable
            }));
        }
        let current = *index.published.get(&key).ok_or_else(not_found)?;
        let entry = index
            .versions
            .get(&exact.unwrap_or(current))
            .filter(|entry| entry.id == key)
            .ok_or_else(|| super::failure(Code::VersionGone))?;
        let duration_ms = entry
            .duration_ms
            .and_then(|value| u64::try_from(value).ok());
        let (path, representation, content_type, bytes, seekable) = match &entry.notes {
            Some(notes) => (
                self.score(entry.version),
                MusicRepresentation::NoteEvents,
                super::notes::CONTENT_TYPE,
                notes.bytes,
                notes.seekable,
            ),
            None => (
                self.object(entry.version),
                MusicRepresentation::EncodedAudio,
                content_type(&entry.format).unwrap_or("application/octet-stream"),
                entry.bytes,
                true,
            ),
        };
        if !fs::symlink_metadata(&path).is_ok_and(|file| file.is_file() && file.len() == bytes) {
            return Err(super::failure(if exact.is_some() {
                Code::VersionGone
            } else {
                Code::Unavailable
            }));
        }
        Ok(Lease::local(
            entry.version,
            path,
            MusicMediaDescriptor {
                representation: representation.into(),
                representation_version: 1,
                byte_length: Some(bytes),
                content_type: Some(content_type.into()),
                duration_ms,
                seekable,
            },
        ))
    }

    pub fn owns(&self, track: TrackId, version: Uuid) -> io::Result<bool> {
        Ok(self
            .index()?
            .versions
            .get(&version)
            .is_some_and(|entry| entry.id == track.to_string()))
    }

    pub fn lyrics(&self, version: Uuid) -> io::Result<Option<(Arc<str>, Option<u64>)>> {
        Ok(self.index()?.versions.get(&version).map(|entry| {
            (
                entry.lyrics.clone(),
                entry
                    .duration_ms
                    .and_then(|value| u64::try_from(value).ok()),
            )
        }))
    }

    fn score(&self, version: Uuid) -> PathBuf {
        self.root.join(format!("{}.notes", version.simple()))
    }

    fn object(&self, version: Uuid) -> PathBuf {
        self.root.join(format!("{}.media", version.simple()))
    }

    pub async fn import(
        self: &Arc<Self>,
        path: PathBuf,
        replace: Option<String>,
    ) -> io::Result<LocalTrack> {
        let library = self.clone();
        self.jobs
            .spawn_blocking(move || library.import_sync(&path, replace.as_deref()))
            .await
            .map_err(io::Error::other)?
    }

    fn import_sync(&self, path: &Path, replace: Option<&str>) -> io::Result<LocalTrack> {
        let _mutation = self
            .mutation
            .lock()
            .map_err(|_| io::Error::other("local music mutation poisoned"))?;
        let version = Uuid::new_v4();
        let id = replace.map_or_else(|| Uuid::new_v4().to_string(), str::to_owned);
        let format = extension(path)?;
        let mut next = self.index()?.clone();
        if replace.is_some() && !next.published.contains_key(&id) {
            if next.missing.get(&id).is_none_or(|missing| missing.retired) {
                return Err(not_found());
            }
            next.missing.remove(&id);
        }
        let entry = self.stage_track(path, id, format, version)?;
        next.published.insert(entry.id.clone(), version);
        let summary = entry.summary();
        next.versions.insert(version, Arc::new(entry));
        self.commit(next)?;
        eprintln!(
            "mikd local music published {:?} ({version}, {})",
            summary.id, summary.format
        );
        Ok(summary)
    }

    fn stage_track(
        &self,
        path: &Path,
        id: String,
        format: String,
        version: Uuid,
    ) -> io::Result<LocalTrack> {
        TrackId::parse(&id)?;
        if content_type(&format).is_none() {
            return Err(super::invalid("unsupported local format"));
        }
        let flags = rustix::fs::OFlags::NONBLOCK | rustix::fs::OFlags::NOFOLLOW;
        let file = OpenOptions::new()
            .read(true)
            .custom_flags(flags.bits().cast_signed())
            .open(path)?;
        if !file.metadata()?.is_file() {
            return Err(super::invalid("import requires a regular file"));
        }
        let mut staged = NamedTempFile::new_in(&self.root)?;
        let limit = if format == "nbs" {
            NBS_BYTES
        } else {
            AUDIO_BYTES
        };
        let bytes = io::copy(&mut file.take(limit + 1), staged.as_file_mut())?;
        if bytes == 0 || bytes > limit {
            return Err(super::invalid("local music file size out of range"));
        }
        let stem = path
            .file_stem()
            .and_then(|stem| stem.to_str())
            .unwrap_or_default();
        let title = label(stem);
        let title = if title.is_empty() {
            "Local track".into()
        } else {
            title
        };
        let mut entry = LocalTrack {
            id,
            version,
            title,
            artist: String::new(),
            album: String::new(),
            original_author: String::new(),
            format,
            duration_ms: None,
            sample_rate_hz: None,
            bytes,
            notes: None,
            lyrics: Arc::from(""),
        };
        if entry.format == "nbs" {
            staged.as_file_mut().rewind()?;
            let imported = notes::compile(staged.as_file_mut())?;
            let name = label(&imported.title);
            if !name.is_empty() {
                entry.title = name;
            }
            entry.artist = label(&imported.artist);
            entry.original_author = label(&imported.original_author);
            entry.duration_ms = i64::try_from(imported.score.duration_us.div_ceil(1000)).ok();
            let mut score = NamedTempFile::new_in(&self.root)?;
            let bytes = imported.score.encode_to_vec();
            score.write_all(&bytes)?;
            score
                .as_file()
                .set_permissions(fs::Permissions::from_mode(0o400))?;
            score.as_file().sync_all()?;
            score
                .persist_noclobber(self.score(version))
                .map_err(io::Error::other)?;
            entry.notes = Some(Notes {
                bytes: bytes.len() as u64,
                seekable: imported.score.r#loop.is_none(),
            });
        } else {
            probe_audio(staged.path(), &self.root, &mut entry)?;
        }
        staged
            .as_file()
            .set_permissions(fs::Permissions::from_mode(0o400))?;
        staged.as_file().sync_all()?;
        staged
            .persist_noclobber(self.object(version))
            .map_err(io::Error::other)?;
        Ok(entry)
    }

    pub async fn remove(self: &Arc<Self>, id: String) -> io::Result<()> {
        let library = self.clone();
        self.jobs
            .spawn_blocking(move || {
                let _mutation = library
                    .mutation
                    .lock()
                    .map_err(|_| io::Error::other("local music mutation poisoned"))?;
                let mut next = library.index()?.clone();
                if let Some(missing) = next.missing.get_mut(&id) {
                    missing.retired = true;
                } else if let Some(version) = next.published.remove(&id) {
                    next.retired.insert(id, version);
                } else if next.retired.contains_key(&id) {
                    return Ok(());
                } else {
                    return Err(not_found());
                }
                library.commit(next)
            })
            .await
            .map_err(io::Error::other)?
    }

    fn commit(&self, mut next: Index) -> io::Result<()> {
        // Persist all staged objects before publishing an index that references them.
        File::open(&self.root)?.sync_all()?;
        next.revision = Uuid::new_v4();
        let mut staged = NamedTempFile::new_in(&self.root)?;
        {
            let mut writer = io::BufWriter::new(staged.as_file_mut());
            serde_json::to_writer(&mut writer, &next).map_err(io::Error::other)?;
            writer.flush()?;
        }
        staged.as_file().sync_all()?;
        staged
            .persist(self.root.join("catalog.json"))
            .map_err(io::Error::other)?;
        *self.index()? = next;
        File::open(&self.root)?.sync_all()
    }

    pub async fn close(&self) {
        self.jobs.close();
        self.jobs.wait().await;
    }
}

impl Drop for Library {
    fn drop(&mut self) {
        // Explicitly unlock the file description even if a concurrent fork has not exec'ed yet.
        drop(self.owner.unlock());
    }
}

impl LocalTrack {
    fn summary(&self) -> Self {
        Self {
            lyrics: Arc::from(""),
            ..self.clone()
        }
    }

    fn wire(&self, state: State) -> MusicTrackData {
        let availability = if state == State::Active {
            Availability::Ready
        } else {
            Availability::Unavailable
        };
        MusicTrackData {
            track_id: self.id.clone(),
            title: self.title.clone(),
            artist: self.artist.clone(),
            album: self.album.clone(),
            duration_ms: self.duration_ms.and_then(|value| u64::try_from(value).ok()),
            original_author: (!self.original_author.is_empty())
                .then(|| self.original_author.clone()),
            state: state.into(),
            availability: availability.into(),
        }
    }
}

fn not_found() -> io::Error {
    super::failure(Code::NotFound)
}

fn extension(path: &Path) -> io::Result<String> {
    let format = path
        .extension()
        .and_then(|ext| ext.to_str())
        .unwrap_or_default()
        .to_ascii_lowercase();
    content_type(&format).ok_or_else(|| super::invalid("unsupported local music extension"))?;
    Ok(format)
}

fn content_type(format: &str) -> Option<&'static str> {
    Some(match format {
        "nbs" => "application/x-nbs",
        "mp3" => "audio/mpeg",
        "m4a" | "mp4" => "audio/mp4",
        "wav" => "audio/wav",
        "flac" => "audio/flac",
        "ogg" | "oga" | "opus" => "audio/ogg",
        "aac" => "audio/aac",
        "mka" => "audio/x-matroska",
        "webm" => "audio/webm",
        _ => return None,
    })
}

fn run_probe(path: &Path, root: &Path) -> io::Result<serde_json::Value> {
    let output = NamedTempFile::new_in(root)?;
    let mut process = Command::new("ffprobe")
        .args([
            "-v",
            "error",
            "-protocol_whitelist",
            "file",
            "-format_whitelist",
            "mp3,mov,mp4,m4a,3gp,3g2,mj2,wav,flac,ogg,aac,matroska,webm",
            "-probesize",
            "8388608",
            "-analyzeduration",
            "5000000",
            "-select_streams",
            "a:0",
            "-show_entries",
            "stream=sample_rate,duration:stream_tags:format=duration:format_tags",
            "-of",
            "json",
            "-i",
        ])
        .arg(path)
        .stdin(Stdio::null())
        .stderr(Stdio::null())
        .stdout(output.reopen()?)
        .spawn()
        .map_err(|error| io::Error::new(error.kind(), format!("cannot start ffprobe: {error}")))?;
    let start = Instant::now();
    loop {
        if let Some(status) = process.try_wait()? {
            if !status.success() {
                return Err(super::invalid("ffprobe could not read local audio"));
            }
            break;
        }
        if start.elapsed() > Duration::from_secs(20)
            || output
                .as_file()
                .metadata()
                .is_ok_and(|meta| meta.len() > PROBE_BYTES)
        {
            drop(process.kill());
            drop(process.wait());
            return Err(super::invalid("ffprobe exceeded its resource limits"));
        }
        std::thread::sleep(Duration::from_millis(25));
    }
    serde_json::from_reader(output.reopen()?.take(PROBE_BYTES)).map_err(io::Error::other)
}

fn probe_audio(path: &Path, root: &Path, entry: &mut LocalTrack) -> io::Result<()> {
    let data = run_probe(path, root)?;
    let stream = data["streams"]
        .get(0)
        .ok_or_else(|| super::invalid("no audio stream"))?;
    let format = &data["format"];
    let tag = |names: &[&str]| {
        names.iter().find_map(|name| {
            [stream, format]
                .into_iter()
                .filter_map(|v| v["tags"].as_object())
                .find_map(|tags| {
                    tags.iter()
                        .find(|(key, _)| key.eq_ignore_ascii_case(name))
                        .and_then(|(_, value)| value.as_str())
                        .filter(|value| !value.trim().is_empty())
                })
        })
    };
    if let Some(title) = tag(&["title"]).map(label).filter(|name| !name.is_empty()) {
        entry.title = title;
    }
    entry.artist = label(tag(&["artist"]).unwrap_or_default());
    entry.album = label(tag(&["album"]).unwrap_or_default());
    entry.sample_rate_hz = stream["sample_rate"]
        .as_str()
        .and_then(|value| value.parse().ok());
    entry.duration_ms = [stream, format].into_iter().find_map(|value| {
        value["duration"]
            .as_str()
            .and_then(|value| value.parse::<f64>().ok())
            .filter(|value| value.is_finite() && *value > 0.0)
            .and_then(|seconds| Duration::try_from_secs_f64(seconds).ok())
            .and_then(|duration| i64::try_from(duration.as_millis()).ok())
    });
    let lyrics = tag(&[
        "lyrics",
        "unsyncedlyrics",
        "unsynced_lyrics",
        "syncedlyrics",
        "lyric",
    ])
    .or_else(|| {
        [stream, format]
            .into_iter()
            .filter_map(|v| v["tags"].as_object())
            .find_map(|tags| {
                tags.iter()
                    .find(|(key, _)| key.starts_with("lyrics-"))
                    .and_then(|(_, value)| value.as_str())
            })
    });
    entry.lyrics = Arc::from(
        lyrics
            .filter(|text| text.len() <= LYRICS_BYTES)
            .unwrap_or_default(),
    );
    Ok(())
}
