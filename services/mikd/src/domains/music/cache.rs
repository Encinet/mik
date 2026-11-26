use super::{
    Service, failure,
    media::{CacheCleanup, CacheMedia, CacheState, CacheUsage, Media},
    track::label,
    version,
};
use mik_protocol::music::music_failure::Code;
use serde::{Deserialize, Serialize};
use std::{collections::HashMap, io, sync::Arc};

#[derive(Serialize, Deserialize)]
pub(crate) struct CachePage {
    pub entries: Vec<CacheEntry>,
    pub next: Option<String>,
    pub usage: CacheUsage,
}

#[derive(Serialize, Deserialize)]
pub(crate) struct CacheEntry {
    pub version: String,
    pub track: Option<CacheTrack>,
    pub channel: String,
    pub source: Option<CacheSource>,
    pub state: CacheState,
    pub bytes: u64,
    pub content_type: Option<String>,
    pub last_used_unix: i64,
}

#[derive(Clone, Serialize, Deserialize)]
pub(crate) struct CacheTrack {
    pub id: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub retired: bool,
}

#[derive(Serialize, Deserialize)]
pub(crate) struct CacheSource {
    pub id: String,
    pub name: Option<String>,
}

impl Service {
    fn cache(&self) -> io::Result<&Arc<Media>> {
        self.media
            .as_ref()
            .ok_or_else(|| io::Error::other("music media cache is not ready"))
    }

    pub(crate) async fn list_cache(
        &self,
        after: &str,
        source: Option<&str>,
    ) -> io::Result<CachePage> {
        let after = (!after.is_empty()).then(|| version(after)).transpose()?;
        let (page, next, usage) = self.cache()?.cache_page(after, source).await;
        Ok(CachePage {
            entries: self.describe_cache(page).await?,
            next: next.map(|version| version.simple().to_string()),
            usage,
        })
    }

    pub(crate) async fn inspect_cache(&self, value: &str) -> io::Result<CacheEntry> {
        let entry = self
            .cache()?
            .cache_entry(version(value)?)
            .await
            .ok_or_else(|| failure(Code::VersionGone))?;
        self.describe_cache(vec![entry])
            .await?
            .pop()
            .ok_or_else(|| failure(Code::VersionGone))
    }

    pub(crate) async fn evict_cache(&self, value: &str) -> io::Result<u64> {
        self.cache()?.evict(version(value)?).await
    }

    pub(crate) async fn clear_idle_cache(&self) -> io::Result<CacheCleanup> {
        self.cache()?.clear_idle().await
    }

    async fn describe_cache(&self, entries: Vec<CacheMedia>) -> io::Result<Vec<CacheEntry>> {
        if entries.is_empty() {
            return Ok(Vec::new());
        }
        let tracks = self
            .identities
            .by_keys(
                entries
                    .iter()
                    .map(|cached| (*cached.track).clone())
                    .collect(),
            )
            .await?;
        let tracks: HashMap<_, _> = tracks
            .into_iter()
            .map(|row| {
                let track = CacheTrack {
                    id: row.id.to_string(),
                    title: label(&row.record.title),
                    artist: label(&row.record.artist),
                    album: label(&row.record.album),
                    retired: row.retired,
                };
                (row.record.key, track)
            })
            .collect();
        let sources = match &self.source {
            Some(source) => source.snapshot().await,
            None => Vec::new(),
        };
        let names: HashMap<_, _> = sources
            .into_iter()
            .map(|source| (source.id, label(&source.name)))
            .collect();
        Ok(entries
            .into_iter()
            .map(|cached| CacheEntry {
                version: cached.version.simple().to_string(),
                track: tracks.get(cached.track.as_ref()).cloned(),
                channel: cached.track.platform.clone(),
                source: cached.provider.map(|id| CacheSource {
                    name: names.get(&id).filter(|name| !name.is_empty()).cloned(),
                    id,
                }),
                state: cached.state,
                bytes: cached.bytes,
                content_type: cached.content_type,
                last_used_unix: cached.last_used_unix,
            })
            .collect())
    }
}
