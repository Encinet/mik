use super::{Entries, Entry, MAX_OBJECTS, Media, Progress, Retired, Slot, cancelled, media_path};
use crate::domains::music::{TrackRef, failure};
use mik_protocol::music::music_failure::Code;
use serde::{Deserialize, Serialize};
use std::{
    io,
    sync::{Arc, atomic::Ordering},
};
use tokio::fs;
use uuid::Uuid;

const PAGE_SIZE: usize = 24;

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub(crate) enum CacheState {
    Preparing,
    Downloading,
    InUse,
    Idle,
}

#[derive(Serialize, Deserialize)]
pub(crate) struct CacheUsage {
    pub entries: usize,
    pub charged_bytes: u64,
    pub limit_bytes: u64,
    pub retired_bytes: u64,
}

#[derive(Serialize, Deserialize)]
pub(crate) struct CacheCleanup {
    pub removed: usize,
    pub skipped: usize,
    pub failed: usize,
    pub released_bytes: u64,
}

pub(in crate::domains::music) struct CacheMedia {
    pub version: Uuid,
    pub track: Arc<TrackRef>,
    pub provider: Option<String>,
    pub state: CacheState,
    pub bytes: u64,
    pub content_type: Option<String>,
    pub last_used_unix: i64,
}

impl CacheMedia {
    fn snapshot(slot: &Slot, progress: &Progress) -> Self {
        let entry = &slot.entry;
        let (state, bytes, info) = match progress {
            Progress::Pending | Progress::Failed { .. } => (CacheState::Preparing, 0, None),
            Progress::Growing { info, bytes } => (CacheState::Downloading, *bytes, Some(info)),
            Progress::Complete { info, bytes } => {
                let state = if slot.in_use() {
                    CacheState::InUse
                } else {
                    CacheState::Idle
                };
                (state, *bytes, Some(info))
            }
        };
        Self {
            version: entry.version,
            track: entry.track.clone(),
            provider: info.map(|info| info.provider.clone()),
            state,
            bytes,
            content_type: info.and_then(|info| info.content_type.clone()),
            last_used_unix: entry.used.load(Ordering::Relaxed),
        }
    }
}

impl Slot {
    fn in_use(&self) -> bool {
        Arc::strong_count(&self.entry) > 1
    }

    pub(super) fn evictable(&self) -> bool {
        !self.in_use() && self.entry.complete()
    }
}

impl Retired {
    fn idle(&self) -> bool {
        self.entry.strong_count() == 0
    }
}

impl Entries {
    fn idle_retired(&self) -> impl Iterator<Item = (Uuid, &Retired)> {
        self.retired
            .iter()
            .filter(|(_, slot)| slot.idle())
            .map(|(&version, slot)| (version, slot))
    }

    fn retire_candidate(&mut self, version: Uuid) -> Option<(Uuid, bool)> {
        self.retire(version)
            .map(|entry| (version, entry.committed.load(Ordering::Acquire)))
    }
}

#[derive(Default)]
struct Purged {
    removed: usize,
    released_bytes: u64,
    failure: Option<io::Error>,
}

impl Media {
    pub(in crate::domains::music) async fn cache_page(
        &self,
        after: Option<Uuid>,
        provider: Option<&str>,
    ) -> (Vec<CacheMedia>, Option<Uuid>, CacheUsage) {
        let entries = self.entries.lock().await;
        let usage = CacheUsage {
            entries: entries.live.len(),
            charged_bytes: entries.charged,
            limit_bytes: self.cache_bytes,
            retired_bytes: entries.retired.values().map(|slot| slot.charged).sum(),
        };
        let mut slots: Vec<_> = entries
            .live
            .values()
            .filter(|slot| after.is_none_or(|after| slot.entry.version > after))
            .collect();
        slots.sort_unstable_by_key(|slot| slot.entry.version);
        let mut page: Vec<_> = slots
            .into_iter()
            .filter_map(|slot| {
                let progress = slot.entry.progress.borrow();
                if provider.is_some_and(|id| progress.info().is_none_or(|info| info.provider != id))
                {
                    return None;
                }
                Some(CacheMedia::snapshot(slot, &progress))
            })
            .take(PAGE_SIZE + 1)
            .collect();
        let next = (page.len() > PAGE_SIZE).then(|| page[PAGE_SIZE - 1].version);
        page.truncate(PAGE_SIZE);
        (page, next, usage)
    }

    pub(in crate::domains::music) async fn cache_entry(&self, version: Uuid) -> Option<CacheMedia> {
        self.entries
            .lock()
            .await
            .live
            .get(&version)
            .map(|slot| CacheMedia::snapshot(slot, &slot.entry.progress.borrow()))
    }

    pub(in crate::domains::music) async fn cache_for_track(
        &self,
        track: &TrackRef,
    ) -> Option<CacheMedia> {
        let entries = self.entries.lock().await;
        let version = entries.tracks.get(track)?;
        let slot = entries.live.get(version)?;
        Some(CacheMedia::snapshot(slot, &slot.entry.progress.borrow()))
    }

    pub(in crate::domains::music) async fn evict(
        self: &Arc<Self>,
        version: Uuid,
    ) -> io::Result<u64> {
        let media = self.clone();
        self.tasks
            .spawn(async move {
                let _disk = media.disk.lock().await;
                let candidate = {
                    let mut entries = media.entries.lock().await;
                    match (entries.live.get(&version), entries.retired.get(&version)) {
                        (Some(slot), _) if slot.evictable() => entries.retire_candidate(version),
                        (None, Some(slot)) if slot.idle() => Some((version, slot.indexed)),
                        (Some(_), _) | (None, Some(_)) => {
                            return Err(io::Error::new(
                                io::ErrorKind::WouldBlock,
                                "media version is in use or downloading",
                            ));
                        }
                        (None, None) => None,
                    }
                }
                .ok_or_else(|| failure(Code::VersionGone))?;
                let purged = media.purge_retired(vec![candidate]).await?;
                purged.failure.map_or(Ok(purged.released_bytes), Err)
            })
            .await
            .map_err(io::Error::other)?
    }

    pub(in crate::domains::music) async fn clear_idle(
        self: &Arc<Self>,
    ) -> io::Result<CacheCleanup> {
        let media = self.clone();
        self.tasks
            .spawn(async move {
                let _disk = media.disk.lock().await;
                let (candidates, skipped) = {
                    let mut entries = media.entries.lock().await;
                    let total = entries.live.len() + entries.retired.len();
                    let idle: Vec<_> = entries
                        .live
                        .iter()
                        .filter(|(_, slot)| slot.evictable())
                        .map(|(&version, _)| version)
                        .collect();
                    let mut candidates: Vec<_> = entries
                        .idle_retired()
                        .map(|(version, slot)| (version, slot.indexed))
                        .collect();
                    candidates.extend(
                        idle.into_iter()
                            .filter_map(|version| entries.retire_candidate(version)),
                    );
                    let skipped = total - candidates.len();
                    (candidates, skipped)
                };
                let attempted = candidates.len();
                let purged = media.purge_retired(candidates).await?;
                if let Some(error) = purged.failure {
                    eprintln!("music cache clear incomplete: {error}");
                }
                Ok(CacheCleanup {
                    removed: purged.removed,
                    skipped,
                    failed: attempted - purged.removed,
                    released_bytes: purged.released_bytes,
                })
            })
            .await
            .map_err(io::Error::other)?
    }

    pub(super) async fn reserve(&self, entry: &Entry, incoming: u64) -> io::Result<()> {
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

    pub(super) async fn reclaim(&self, incoming: u64) -> io::Result<()> {
        let candidates = {
            let mut entries = self.entries.lock().await;
            let mut shortage = entries
                .charged
                .saturating_add(incoming)
                .saturating_sub(self.cache_bytes);
            let mut victims = Vec::new();
            for (version, slot) in entries.idle_retired() {
                shortage = shortage.saturating_sub(slot.charged);
                victims.push((version, slot.indexed));
            }
            let mut over_count = entries.live.len().saturating_sub(MAX_OBJECTS);
            if shortage != 0 || over_count != 0 {
                let mut unleased: Vec<_> = entries
                    .live
                    .values()
                    .filter(|slot| slot.evictable())
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
                    victims.extend(
                        evict
                            .into_iter()
                            .filter_map(|version| entries.retire_candidate(version)),
                    );
                }
            }
            victims
        };
        if let Some(error) = self.purge_retired(candidates).await?.failure {
            return Err(error);
        }
        if self.entries.lock().await.charged.saturating_add(incoming) > self.cache_bytes {
            return Err(io::Error::new(
                io::ErrorKind::WouldBlock,
                "music cache disk quota is in use",
            ));
        }
        Ok(())
    }

    pub(super) async fn reclaim_idle(&self) {
        if let Err(error) = self.reclaim(0).await {
            eprintln!("music cache file cleanup failed: {error}");
        }
    }

    // The disk lock serializes this with index writes and other cleanup passes.
    async fn purge_retired(&self, candidates: Vec<(Uuid, bool)>) -> io::Result<Purged> {
        self.index
            .remove(
                candidates
                    .iter()
                    .filter(|(_, indexed)| *indexed)
                    .map(|(version, _)| version.simple().to_string())
                    .collect(),
            )
            .await?;
        let mut purged = Purged::default();
        for (version, _) in candidates {
            let result = fs::remove_file(media_path(&self.root, version)).await;
            let mut entries = self.entries.lock().await;
            if let Err(error) = result
                && error.kind() != io::ErrorKind::NotFound
            {
                if let Some(slot) = entries.retired.get_mut(&version) {
                    slot.indexed = false;
                }
                if purged.failure.is_none() {
                    purged.failure = Some(error);
                }
                continue;
            }
            if let Some(slot) = entries.retired.remove(&version) {
                entries.charged -= slot.charged;
                purged.removed += 1;
                purged.released_bytes += slot.charged;
            }
        }
        Ok(purged)
    }
}
