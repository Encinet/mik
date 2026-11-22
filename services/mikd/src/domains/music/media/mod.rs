mod index;
mod transfer;

use super::{Source, TrackRef};
use index::{Cached, Index};
use mik_protocol::music::{MusicMediaDescriptor, MusicRepresentation, music_failure::Code};
use std::{
    collections::{HashMap, VecDeque},
    fs::{DirBuilder, File as LockFile, OpenOptions},
    io,
    os::unix::fs::{DirBuilderExt, OpenOptionsExt, PermissionsExt},
    path::{Path, PathBuf},
    sync::{
        Arc, Weak,
        atomic::{AtomicBool, AtomicI64, Ordering},
    },
    time::{Duration, SystemTime, UNIX_EPOCH},
};
use tokio::{
    fs::{self, File},
    io::{AsyncReadExt, AsyncSeekExt},
    sync::{Mutex, Semaphore, watch},
};
use tokio_util::{
    sync::{CancellationToken, DropGuard},
    task::TaskTracker,
};
use uuid::Uuid;

const DOWNLOADS: usize = 4;
const MAX_OBJECTS: usize = 2048;
const RECENT_REPORTS: usize = 128;
pub(super) const INDEX_FILE: &str = "media.sqlite3";

#[derive(Clone)]
struct MediaInfo {
    byte_length: Option<u64>,
    content_type: Option<String>,
    provider: String,
}

#[derive(Clone)]
enum Progress {
    Pending,
    Growing {
        info: MediaInfo,
        bytes: u64,
    },
    Complete {
        info: MediaInfo,
        bytes: u64,
    },
    Failed {
        kind: io::ErrorKind,
        detail: Arc<str>,
    },
}

struct Entry {
    track: Arc<TrackRef>,
    version: Uuid,
    path: PathBuf,
    progress: watch::Sender<Progress>,
    used: AtomicI64,
    persisted_used: AtomicI64,
    committed: AtomicBool,
    retired: AtomicBool,
    media: Weak<Media>,
    demand: Weak<DropGuard>,
    cancel: CancellationToken,
    promoted: CancellationToken,
}

impl Entry {
    fn new(
        media: &Arc<Media>,
        track: Arc<TrackRef>,
        version: Uuid,
        progress: Progress,
        used: i64,
        demand: Weak<DropGuard>,
        cancel: CancellationToken,
    ) -> Self {
        let committed = matches!(progress, Progress::Complete { .. });
        Self {
            track,
            version,
            path: media_path(&media.root, version),
            progress: watch::channel(progress).0,
            used: AtomicI64::new(used),
            persisted_used: AtomicI64::new(used),
            committed: AtomicBool::new(committed),
            retired: AtomicBool::new(false),
            media: Arc::downgrade(media),
            demand,
            cancel,
            promoted: CancellationToken::new(),
        }
    }

    fn complete(&self) -> bool {
        matches!(*self.progress.borrow(), Progress::Complete { .. })
    }
}

impl Drop for Entry {
    fn drop(&mut self) {
        if *self.retired.get_mut()
            && let Some(media) = self.media.upgrade()
            && !media.shutdown.is_cancelled()
        {
            media.tasks.clone().spawn(async move {
                let _disk = media.disk.lock().await;
                media.reclaim_idle().await;
            });
        }
    }
}

struct Slot {
    entry: Arc<Entry>,
    charged: u64,
}

struct Retired {
    entry: Weak<Entry>,
    charged: u64,
    indexed: bool,
}

#[derive(Default)]
struct Entries {
    tracks: HashMap<Arc<TrackRef>, Uuid>,
    live: HashMap<Uuid, Slot>,
    retired: HashMap<Uuid, Retired>,
    charged: u64,
    rejected: VecDeque<(Uuid, Arc<TrackRef>, String)>,
}

impl Entries {
    fn insert(&mut self, entry: Arc<Entry>, charged: u64) {
        self.tracks.insert(entry.track.clone(), entry.version);
        self.live.insert(entry.version, Slot { entry, charged });
        self.charged += charged;
    }

    fn retire(&mut self, version: Uuid) -> Option<Arc<Entry>> {
        let Slot { entry, charged } = self.live.remove(&version)?;
        self.tracks.remove(&entry.track);
        if charged != 0 {
            if Arc::strong_count(&entry) > 1 {
                entry.retired.store(true, Ordering::Release);
            }
            self.retired.insert(
                version,
                Retired {
                    entry: Arc::downgrade(&entry),
                    charged,
                    indexed: entry.committed.load(Ordering::Acquire),
                },
            );
        }
        Some(entry)
    }
}

pub(super) struct Media {
    root: PathBuf,
    _owner: LockFile,
    index: Index,
    entries: Mutex<Entries>,
    disk: Mutex<()>,
    http: reqwest::Client,
    downloads: Semaphore,
    complete_only: Semaphore,
    touches: Arc<Semaphore>,
    file_bytes: u64,
    cache_bytes: u64,
    shutdown: CancellationToken,
    tasks: TaskTracker,
}

#[derive(Clone, Copy, PartialEq, Eq)]
pub(super) enum WaitFor {
    Readable,
    Complete,
}

enum LeaseSource {
    Cached(Arc<Entry>),
    Local {
        version: Uuid,
        path: PathBuf,
        bytes: u64,
    },
}

pub(crate) struct Lease {
    source: LeaseSource,
    _demand: Option<Arc<DropGuard>>,
    pub descriptor: MusicMediaDescriptor,
    origin: String,
}

pub(crate) struct MediaReader {
    file: File,
    progress: ReaderProgress,
    position: u64,
}

enum ReaderProgress {
    Cached(watch::Receiver<Progress>),
    Local(u64),
}

pub(crate) enum ReadFailure {
    Transfer(String),
    Io(io::Error),
}

impl Media {
    pub async fn new(root: PathBuf, file_bytes: u64, cache_bytes: u64) -> io::Result<Arc<Self>> {
        DirBuilder::new()
            .recursive(true)
            .mode(0o700)
            .create(&root)?;
        let metadata = std::fs::symlink_metadata(&root)?;
        if !metadata.is_dir() || metadata.permissions().mode() & 0o077 != 0 {
            return Err(io::Error::other(
                "music media cache must be a private directory",
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
        let media = Arc::new(Self {
            index: Index::open(&root.join(INDEX_FILE)).await?,
            root,
            _owner: owner,
            entries: Mutex::default(),
            disk: Mutex::new(()),
            http: crate::http_egress::client_builder()
                .connect_timeout(Duration::from_secs(20))
                .read_timeout(Duration::from_secs(15))
                .timeout(Duration::from_mins(10))
                .no_proxy()
                .build()
                .map_err(io::Error::other)?,
            downloads: Semaphore::new(DOWNLOADS),
            complete_only: Semaphore::new(DOWNLOADS - 1),
            touches: Arc::new(Semaphore::new(2)),
            file_bytes,
            cache_bytes,
            shutdown: CancellationToken::new(),
            tasks: TaskTracker::new(),
        });
        let mut entries = Entries::default();
        let mut after = None;
        loop {
            let batch = media.index.entries(after).await?;
            let Some(last) = batch.last() else { break };
            after = Some(last.track.clone());
            let mut invalid = Vec::new();
            for cached in batch {
                let Ok(version) = Uuid::parse_str(&cached.version) else {
                    invalid.push(cached.version);
                    continue;
                };
                let entry = Entry::new(
                    &media,
                    cached.track,
                    version,
                    Progress::Complete {
                        info: MediaInfo {
                            byte_length: Some(cached.bytes),
                            content_type: cached.content_type,
                            provider: cached.provider,
                        },
                        bytes: cached.bytes,
                    },
                    cached.used,
                    Weak::new(),
                    CancellationToken::new(),
                );
                if cached.bytes > file_bytes
                    || !fs::symlink_metadata(&entry.path)
                        .await
                        .is_ok_and(|file| file.is_file() && file.len() == cached.bytes)
                {
                    invalid.push(cached.version);
                    continue;
                }
                entries.insert(Arc::new(entry), cached.bytes);
            }
            media.index.remove(invalid).await?;
        }
        let mut files = fs::read_dir(&media.root).await?;
        while let Some(file) = files.next_entry().await? {
            let path = file.path();
            if path.extension().is_some_and(|ext| ext == "audio")
                && let Some(stem) = path.file_stem().and_then(|stem| stem.to_str())
                && stem.len() == 32
                && let Ok(version) = Uuid::parse_str(stem)
                && (!entries.live.contains_key(&version)
                    || stem.bytes().any(|byte| byte.is_ascii_uppercase()))
            {
                fs::remove_file(path).await?;
            }
        }
        *media.entries.lock().await = entries;
        media.reclaim(0).await?;
        Ok(media)
    }

    pub fn shutdown(&self) {
        self.shutdown.cancel();
    }

    pub async fn close(&self) -> io::Result<()> {
        self.shutdown();
        self.tasks.close();
        self.tasks.wait().await;
        self.reclaim(0).await.and(self.index.close().await)
    }

    pub async fn open(
        self: &Arc<Self>,
        source: Option<&Source>,
        track: TrackRef,
        wait: WaitFor,
        avoid: Option<Uuid>,
    ) -> io::Result<Lease> {
        let (entry, demand) = {
            let mut entries = self.entries.lock().await;
            let excluded = avoid
                .map(|id| {
                    entries
                        .rejected
                        .iter()
                        .find(|(version, key, _)| *version == id && **key == track)
                        .map(|(_, _, provider)| vec![provider.clone()])
                        .ok_or_else(|| super::failure(Code::VersionGone))
                })
                .transpose()?
                .unwrap_or_default();
            match entries.tracks.get(&track).copied() {
                Some(version) if entries.live[&version].entry.complete() => {
                    (entries.live[&version].entry.clone(), None)
                }
                Some(version) => {
                    let entry = &entries.live[&version].entry;
                    if let Some(demand) = entry.demand.upgrade() {
                        (entry.clone(), Some(demand))
                    } else {
                        entries.retire(version);
                        self.start(&mut entries, source, track, excluded)?
                    }
                }
                None => self.start(&mut entries, source, track, excluded)?,
            }
        };
        if wait == WaitFor::Readable && demand.is_some() {
            entry.promoted.cancel();
        }
        self.lease(entry, demand, wait, false).await
    }

    fn start(
        self: &Arc<Self>,
        entries: &mut Entries,
        source: Option<&Source>,
        track: TrackRef,
        excluded: Vec<String>,
    ) -> io::Result<(Arc<Entry>, Option<Arc<DropGuard>>)> {
        let source = source.ok_or_else(|| super::failure(Code::Unavailable))?;
        if entries.live.len() >= MAX_OBJECTS
            && entries
                .live
                .values()
                .all(|slot| Arc::strong_count(&slot.entry) > 1 || !slot.entry.complete())
        {
            return Err(super::failure(Code::Busy));
        }
        let cancel = self.shutdown.child_token();
        let demand = Arc::new(cancel.clone().drop_guard());
        let entry = Arc::new(Entry::new(
            self,
            Arc::new(track),
            Uuid::new_v4(),
            Progress::Pending,
            now(),
            Arc::downgrade(&demand),
            cancel,
        ));
        entries.insert(entry.clone(), 0);
        self.tasks.spawn(
            self.clone()
                .download(source.clone(), entry.clone(), excluded),
        );
        Ok((entry, Some(demand)))
    }

    pub async fn open_exact(
        self: &Arc<Self>,
        track: &TrackRef,
        version: Uuid,
        wait: WaitFor,
    ) -> io::Result<Lease> {
        let (entry, demand) = {
            let entries = self.entries.lock().await;
            let entry = &entries
                .live
                .get(&version)
                .filter(|slot| &*slot.entry.track == track)
                .ok_or_else(|| super::failure(Code::VersionGone))?
                .entry;
            let demand = if entry.complete() {
                None
            } else {
                Some(
                    entry
                        .demand
                        .upgrade()
                        .ok_or_else(|| super::failure(Code::VersionGone))?,
                )
            };
            (entry.clone(), demand)
        };
        self.lease(entry, demand, wait, true).await
    }

    pub async fn ready(&self) -> Vec<(TrackRef, Uuid)> {
        self.entries
            .lock()
            .await
            .live
            .values()
            .filter(|slot| slot.entry.complete())
            .map(|slot| ((*slot.entry.track).clone(), slot.entry.version))
            .collect()
    }

    pub async fn inspect(&self, key: &TrackRef) -> Option<(String, String)> {
        let entries = self.entries.lock().await;
        let version = entries.tracks.get(key)?;
        let entry = &entries.live.get(version)?.entry;
        let progress = entry.progress.borrow();
        match &*progress {
            Progress::Growing { info, .. } | Progress::Complete { info, .. } => {
                Some((version.simple().to_string(), info.provider.clone()))
            }
            _ => None,
        }
    }

    pub async fn version_source(&self, version: Uuid) -> Option<(Arc<TrackRef>, String)> {
        let entries = self.entries.lock().await;
        let entry = entries
            .live
            .get(&version)
            .map(|slot| slot.entry.clone())
            .or_else(|| {
                entries
                    .retired
                    .get(&version)
                    .and_then(|slot| slot.entry.upgrade())
            })?;
        let progress = entry.progress.borrow();
        match &*progress {
            Progress::Growing { info, .. } | Progress::Complete { info, .. } => {
                Some((entry.track.clone(), info.provider.clone()))
            }
            Progress::Pending | Progress::Failed { .. } => None,
        }
    }

    async fn lease(
        self: &Arc<Self>,
        entry: Arc<Entry>,
        demand: Option<Arc<DropGuard>>,
        wait: WaitFor,
        exact: bool,
    ) -> io::Result<Lease> {
        let (info, complete_bytes) = {
            let mut progress = entry.progress.subscribe();
            let state = progress
                .wait_for(|state| match state {
                    Progress::Pending => false,
                    Progress::Growing { bytes, .. } => wait == WaitFor::Readable && *bytes > 0,
                    Progress::Complete { .. } | Progress::Failed { .. } => true,
                })
                .await
                .map_err(io::Error::other)?;
            match &*state {
                Progress::Growing { info, .. } => (info.clone(), None),
                Progress::Complete { info, bytes } => (info.clone(), Some(*bytes)),
                Progress::Failed { kind, detail } => {
                    return Err(io::Error::new(*kind, detail.to_string()));
                }
                Progress::Pending => unreachable!(),
            }
        };
        if let Some(bytes) = complete_bytes
            && !fs::symlink_metadata(&entry.path)
                .await
                .is_ok_and(|file| file.is_file() && file.len() == bytes)
        {
            return Err(super::failure(if exact {
                Code::VersionGone
            } else {
                Code::Unavailable
            }));
        }
        let used = now();
        entry.used.fetch_max(used, Ordering::Relaxed);
        let persisted = entry.persisted_used.load(Ordering::Relaxed);
        if entry.committed.load(Ordering::Acquire)
            && used.saturating_sub(persisted) >= 60
            && let Ok(permit) = self.touches.clone().try_acquire_owned()
            && entry
                .persisted_used
                .compare_exchange(persisted, used, Ordering::Relaxed, Ordering::Relaxed)
                .is_ok()
        {
            let media = self.clone();
            let version = entry.version.simple().to_string();
            self.tasks.spawn(async move {
                let _permit = permit;
                if let Err(error) = media.index.touch(version, used).await {
                    eprintln!("music cache access update failed: {error}");
                }
            });
        }
        if entry.complete() && demand.is_none() {
            super::source::log_playback_source(
                &entry.track.platform,
                entry.version,
                &info.provider,
                "cache_hit",
            );
        }
        Ok(Lease {
            source: LeaseSource::Cached(entry),
            origin: format!(
                "source={:?} acquisition={}",
                info.provider,
                if demand.is_none() {
                    "cache_hit"
                } else {
                    "download"
                }
            ),
            _demand: demand,
            descriptor: MusicMediaDescriptor {
                representation: MusicRepresentation::EncodedAudio.into(),
                representation_version: 1,
                byte_length: info.byte_length,
                content_type: info.content_type,
                duration_ms: None,
                seekable: true,
            },
        })
    }

    pub async fn report_corrupt(
        self: &Arc<Self>,
        track: TrackRef,
        version: Uuid,
    ) -> io::Result<bool> {
        let media = self.clone();
        self.tasks
            .spawn(async move {
                let _disk = media.disk.lock().await;
                let entry = {
                    let mut entries = media.entries.lock().await;
                    let Some(slot) = entries
                        .live
                        .get(&version)
                        .filter(|slot| *slot.entry.track == track)
                    else {
                        return Ok(false);
                    };
                    let provider = match &*slot.entry.progress.borrow() {
                        Progress::Growing { info, .. } | Progress::Complete { info, .. } => {
                            info.provider.clone()
                        }
                        _ => return Ok(false),
                    };
                    // Active readers may finish the immutable bytes, but this version is not offered again.
                    let Some(entry) = entries.retire(version) else {
                        return Ok(false);
                    };
                    if entries.rejected.len() == RECENT_REPORTS {
                        entries.rejected.pop_front();
                    }
                    entries
                        .rejected
                        .push_back((version, entry.track.clone(), provider));
                    entry
                };
                let result = if entry.committed.load(Ordering::Acquire) {
                    let result = media.index.remove(vec![version.simple().to_string()]).await;
                    if result.is_ok()
                        && let Some(slot) = media.entries.lock().await.retired.get_mut(&version)
                    {
                        slot.indexed = false;
                    }
                    result
                } else {
                    Ok(())
                };
                drop(entry);
                media.reclaim(0).await?;
                result.map(|()| true)
            })
            .await
            .map_err(io::Error::other)?
    }

    async fn reserve(&self, entry: &Entry, incoming: u64) -> io::Result<()> {
        let _disk = self.disk.lock().await;
        if entry.cancel.is_cancelled() {
            return Err(cancelled());
        }
        self.reclaim(incoming).await?;
        let mut entries = self.entries.lock().await;
        let slot = entries.live.get_mut(&entry.version).ok_or_else(cancelled)?;
        slot.charged = incoming;
        entries.charged += incoming;
        Ok(())
    }

    async fn reclaim(&self, incoming: u64) -> io::Result<()> {
        let candidates = {
            let mut entries = self.entries.lock().await;
            let mut shortage = entries
                .charged
                .saturating_add(incoming)
                .saturating_sub(self.cache_bytes);
            let mut victims = Vec::new();
            for (&version, slot) in entries
                .retired
                .iter()
                .filter(|(_, slot)| slot.entry.strong_count() == 0)
            {
                shortage = shortage.saturating_sub(slot.charged);
                victims.push((version, slot.indexed));
            }
            let mut over_count = entries.live.len().saturating_sub(MAX_OBJECTS);
            if shortage != 0 || over_count != 0 {
                let mut unleased: Vec<_> = entries
                    .live
                    .values()
                    .filter(|slot| Arc::strong_count(&slot.entry) == 1 && slot.entry.complete())
                    .map(|slot| {
                        (
                            slot.entry.version,
                            slot.charged,
                            slot.entry.used.load(Ordering::Relaxed),
                        )
                    })
                    .collect();
                unleased.sort_unstable_by_key(|(_, _, used)| *used);
                let mut evict = Vec::new();
                for (version, bytes, _) in unleased {
                    evict.push(version);
                    shortage = shortage.saturating_sub(bytes);
                    over_count = over_count.saturating_sub(1);
                    if shortage == 0 && over_count == 0 {
                        break;
                    }
                }
                if shortage == 0 && over_count == 0 {
                    for version in evict {
                        if let Some(entry) = entries.retire(version) {
                            victims.push((version, entry.committed.load(Ordering::Acquire)));
                        }
                    }
                }
            }
            victims
        };
        self.index
            .remove(
                candidates
                    .iter()
                    .filter(|(_, indexed)| *indexed)
                    .map(|(version, _)| version.simple().to_string())
                    .collect(),
            )
            .await?;
        for (version, _) in candidates {
            match fs::remove_file(media_path(&self.root, version)).await {
                Err(error) if error.kind() != io::ErrorKind::NotFound => return Err(error),
                _ => {}
            }
            let mut entries = self.entries.lock().await;
            if let Some(slot) = entries.retired.remove(&version) {
                entries.charged -= slot.charged;
            }
        }
        if self.entries.lock().await.charged.saturating_add(incoming) > self.cache_bytes {
            return Err(io::Error::new(
                io::ErrorKind::WouldBlock,
                "music cache disk quota is in use",
            ));
        }
        Ok(())
    }

    async fn download(self: Arc<Self>, source: Source, entry: Arc<Entry>, excluded: Vec<String>) {
        match self.transfer(&source, &entry, excluded).await {
            Ok((info, bytes)) => {
                let current = {
                    let mut entries = self.entries.lock().await;
                    if let Some(slot) = entries.live.get_mut(&entry.version) {
                        let released = slot.charged - bytes;
                        slot.charged = bytes;
                        entries.charged -= released;
                        entry.progress.send_replace(Progress::Complete {
                            info: info.clone(),
                            bytes,
                        });
                        true
                    } else {
                        false
                    }
                };
                if current {
                    let _disk = self.disk.lock().await;
                    if self.entries.lock().await.live.contains_key(&entry.version) {
                        let record = Cached {
                            track: entry.track.clone(),
                            version: entry.version.simple().to_string(),
                            bytes,
                            content_type: info.content_type,
                            provider: info.provider,
                            used: entry.used.load(Ordering::Relaxed),
                        };
                        match self.index.store(record).await {
                            Ok(()) => entry.committed.store(true, Ordering::Release),
                            Err(error) => eprintln!("music cache index write failed: {error}"),
                        }
                        return;
                    }
                } else {
                    entry
                        .progress
                        .send_replace(Progress::Complete { info, bytes });
                }
            }
            Err(error) => {
                let mut entries = self.entries.lock().await;
                entry.progress.send_replace(Progress::Failed {
                    kind: error.kind(),
                    detail: error.to_string().into(),
                });
                entries.retire(entry.version);
            }
        }
        drop(entry);
        let _disk = self.disk.lock().await;
        self.reclaim_idle().await;
    }

    async fn reclaim_idle(&self) {
        if let Err(error) = self.reclaim(0).await {
            eprintln!("music cache file cleanup failed: {error}");
        }
    }
}

impl Lease {
    pub(super) fn local(version: Uuid, path: PathBuf, descriptor: MusicMediaDescriptor) -> Self {
        Self {
            source: LeaseSource::Local {
                version,
                path,
                bytes: descriptor.byte_length.unwrap_or_default(),
            },
            _demand: None,
            descriptor,
            origin: "source=managed_library".into(),
        }
    }

    pub fn origin(&self) -> &str {
        &self.origin
    }

    pub fn version(&self) -> Uuid {
        match &self.source {
            LeaseSource::Cached(entry) => entry.version,
            LeaseSource::Local { version, .. } => *version,
        }
    }

    pub async fn reader(&self, position: u64) -> Result<MediaReader, ReadFailure> {
        let (path, progress) = match &self.source {
            LeaseSource::Cached(entry) => {
                let progress = entry.progress.subscribe();
                if let Progress::Failed { detail, .. } = &*progress.borrow() {
                    return Err(ReadFailure::Transfer(detail.to_string()));
                }
                (&entry.path, ReaderProgress::Cached(progress))
            }
            LeaseSource::Local { path, bytes, .. } => (path, ReaderProgress::Local(*bytes)),
        };
        let mut file = File::open(path).await.map_err(ReadFailure::Io)?;
        if position != 0 {
            file.seek(io::SeekFrom::Start(position))
                .await
                .map_err(ReadFailure::Io)?;
        }
        Ok(MediaReader {
            file,
            progress,
            position,
        })
    }
}

impl MediaReader {
    pub async fn read(&mut self, buffer: &mut [u8]) -> Result<usize, ReadFailure> {
        let position = self.position;
        let available = match &mut self.progress {
            ReaderProgress::Local(bytes) => bytes.saturating_sub(position),
            ReaderProgress::Cached(progress) => {
                let state = progress
                    .wait_for(|state| match state {
                        Progress::Pending => false,
                        Progress::Growing { bytes, .. } => *bytes > position,
                        Progress::Complete { .. } | Progress::Failed { .. } => true,
                    })
                    .await
                    .map_err(|error| ReadFailure::Io(io::Error::other(error)))?;
                match &*state {
                    Progress::Growing { bytes, .. } | Progress::Complete { bytes, .. } => {
                        bytes.saturating_sub(position)
                    }
                    Progress::Failed { detail, .. } => {
                        return Err(ReadFailure::Transfer(detail.to_string()));
                    }
                    Progress::Pending => unreachable!(),
                }
            }
        };
        let length = usize::try_from(available)
            .unwrap_or(usize::MAX)
            .min(buffer.len());
        if length > 0 {
            self.file
                .read_exact(&mut buffer[..length])
                .await
                .map_err(ReadFailure::Io)?;
            self.position += length as u64;
        }
        Ok(length)
    }
}

fn media_path(root: &Path, version: Uuid) -> PathBuf {
    root.join(format!("{}.audio", version.simple()))
}

fn cancelled() -> io::Error {
    io::Error::new(io::ErrorKind::Interrupted, "music download cancelled")
}

fn now() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .ok()
        .and_then(|time| i64::try_from(time.as_secs()).ok())
        .unwrap_or_default()
}
