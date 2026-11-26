//! Music administration composes the catalog, originals, identities and media cache.
use super::{
    CacheState, LocalTrack, Service, WaitFor, failure, invalid, media::CacheMedia, track::TrackId,
    version,
};
use mik_protocol::music::{
    MusicCatalogQuery, MusicCollectionQuery, MusicTrackData, music_catalog_query::Scope,
    music_failure::Code, music_track_data::State,
};
use serde::{Deserialize, Serialize};
use std::{io, path::PathBuf, sync::PoisonError};
use uuid::Uuid;

const PAGE_SIZE: usize = 24;
const MANAGED_LIBRARY: &str = "managed_library";

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub(crate) enum CatalogQuery {
    Stored(String),
    RegisteredBySource(String),
    Search(String),
    Collection(String),
}

#[derive(Serialize, Deserialize)]
pub(crate) struct CatalogPage {
    pub entries: Vec<CatalogTrack>,
    pub next: Option<String>,
    pub partial: bool,
    pub title: Option<String>,
}

#[derive(Serialize, Deserialize)]
pub(crate) struct CatalogTrack {
    pub id: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub local: bool,
    pub retired: bool,
}

impl CatalogTrack {
    fn new(track: MusicTrackData, local: bool) -> Self {
        Self {
            retired: track.state() == State::Retired,
            id: track.track_id,
            title: track.title,
            artist: track.artist,
            album: track.album,
            local,
        }
    }
}

#[derive(Serialize, Deserialize)]
pub(crate) struct TrackInspection {
    pub id: String,
    pub state: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub availability: Option<String>,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub duration_ms: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub source: Option<String>,
    pub channel: Option<String>,
    pub registered_source: Option<String>,
    pub last_failure: Option<String>,
    pub media: Option<InspectedMedia>,
}

#[derive(Serialize, Deserialize)]
#[serde(untagged)]
pub(crate) enum InspectedMedia {
    Local(LocalTrack),
    Cached {
        version: String,
        source: Option<String>,
        state: CacheState,
    },
}

impl InspectedMedia {
    pub fn version(&self) -> String {
        match self {
            Self::Local(track) => track.version.simple().to_string(),
            Self::Cached { version, .. } => version.clone(),
        }
    }

    fn cached(entry: CacheMedia) -> Self {
        Self::Cached {
            version: entry.version.simple().to_string(),
            source: entry.provider,
            state: entry.state,
        }
    }
}

impl TrackInspection {
    pub fn active(&self) -> bool {
        self.state == State::Active.as_str_name()
    }

    pub fn local(&self) -> bool {
        self.source.as_deref() == Some(MANAGED_LIBRARY)
    }

    pub fn audio_source(&self) -> Option<&str> {
        match &self.media {
            Some(InspectedMedia::Cached { source, .. }) => source.as_deref(),
            _ => None,
        }
    }

    pub fn lyrics_version(&self) -> Option<String> {
        let media = self.media.as_ref()?;
        match media {
            InspectedMedia::Cached { source: None, .. } => None,
            _ => Some(media.version()),
        }
    }
}

#[derive(Serialize, Deserialize)]
pub(crate) struct VersionPage {
    pub entries: Vec<VersionEntry>,
    pub latest: Option<String>,
    pub next: Option<String>,
}

#[derive(Serialize, Deserialize)]
pub(crate) struct VersionEntry {
    pub version: String,
    #[serde(default)]
    pub cached: bool,
    pub format: Option<String>,
    pub source: Option<String>,
    pub bytes: u64,
}

impl VersionEntry {
    pub fn lyrics_available(&self) -> bool {
        !self.cached || self.source.is_some()
    }
}

#[derive(Serialize, Deserialize)]
pub(crate) struct PreparedTrack {
    pub id: String,
    pub version: String,
}

impl Service {
    pub(crate) fn list_local(&self, after: &str) -> io::Result<Vec<LocalTrack>> {
        self.library.list(after)
    }

    pub(crate) async fn query_catalog(
        &self,
        query: CatalogQuery,
        after: String,
    ) -> io::Result<CatalogPage> {
        let catalog = self.catalog(&self.admin_cursors);
        let cursor = || {
            self.source()?;
            hex::decode(&after).map_err(|_| failure(Code::CursorExpired))
        };
        let (title, page) = match query {
            CatalogQuery::Stored(keyword) => {
                return self.browse_catalog(keyword, after, None).await;
            }
            CatalogQuery::RegisteredBySource(source) => {
                return self
                    .browse_catalog(String::new(), after, Some(source))
                    .await;
            }
            CatalogQuery::Search(query) => {
                if query.trim().is_empty() {
                    return Err(invalid("enter a search term"));
                }
                let page = catalog
                    .list(MusicCatalogQuery {
                        query,
                        cursor: cursor()?,
                        scope: Scope::All.into(),
                    })
                    .await?;
                (None, page)
            }
            CatalogQuery::Collection(reference) => {
                let result = catalog
                    .collection(MusicCollectionQuery {
                        reference,
                        cursor: cursor()?,
                    })
                    .await?;
                let page = result
                    .page
                    .ok_or_else(|| io::Error::other("missing collection page"))?;
                (Some(result.title), page)
            }
        };
        let entries = page
            .tracks
            .into_iter()
            .map(|track| {
                let local = self
                    .library
                    .track(TrackId::parse(&track.track_id)?)?
                    .is_some();
                Ok(CatalogTrack::new(track, local))
            })
            .collect::<io::Result<Vec<_>>>()?;
        Ok(CatalogPage {
            entries,
            next: (!page.next_cursor.is_empty()).then(|| hex::encode(page.next_cursor)),
            partial: page.partial,
            title,
        })
    }

    async fn browse_catalog(
        &self,
        keyword: String,
        after: String,
        source: Option<String>,
    ) -> io::Result<CatalogPage> {
        if keyword.len() > 768 {
            return Err(invalid("music query too long"));
        }
        if !after.is_empty() {
            TrackId::parse(&after)?;
        }
        let keyword = keyword.trim().to_owned();
        let (local, remote) = if source.is_some() {
            let remote = self
                .identities
                .registered_page(keyword, after, source, PAGE_SIZE + 1)
                .await?;
            (Vec::new(), remote)
        } else {
            tokio::try_join!(
                self.library
                    .registered_page(keyword.clone(), after.clone(), PAGE_SIZE + 1),
                self.identities
                    .registered_page(keyword, after, None, PAGE_SIZE + 1),
            )?
        };
        let mut entries: Vec<_> = local
            .into_iter()
            .map(|track| CatalogTrack::new(track, true))
            .chain(
                remote
                    .into_iter()
                    .map(|track| CatalogTrack::new(track, false)),
            )
            .collect();
        entries.sort_unstable_by(|a, b| a.id.cmp(&b.id));
        let next = (entries.len() > PAGE_SIZE).then(|| entries[PAGE_SIZE - 1].id.clone());
        entries.truncate(PAGE_SIZE);
        Ok(CatalogPage {
            entries,
            next,
            partial: false,
            title: None,
        })
    }

    pub(crate) async fn inspect_track(&self, value: &str) -> io::Result<TrackInspection> {
        let id = TrackId::parse(value)?;
        let last_failure = self
            .failures
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
            .iter()
            .rev()
            .find(|(track, _)| *track == id)
            .map(|(_, failure)| failure.clone());
        if let Some((track, media)) = self.library.inspect(id)? {
            return Ok(TrackInspection {
                id: track.track_id.clone(),
                state: track.state().as_str_name().into(),
                availability: Some(track.availability().as_str_name().into()),
                title: track.title,
                artist: track.artist,
                album: track.album,
                duration_ms: track.duration_ms,
                source: Some(MANAGED_LIBRARY.into()),
                channel: None,
                registered_source: None,
                last_failure,
                media: media.map(InspectedMedia::Local),
            });
        }
        let row = self.registered_remote(id).await?;
        let media = if let Some(media) = &self.media {
            media
                .cache_for_track(&row.record.key)
                .await
                .map(InspectedMedia::cached)
        } else {
            None
        };
        let track = row.display(&std::collections::BTreeSet::new());
        Ok(TrackInspection {
            id: track.track_id.clone(),
            state: track.state().as_str_name().into(),
            availability: None,
            title: track.title,
            artist: track.artist,
            album: track.album,
            duration_ms: track.duration_ms,
            source: None,
            channel: Some(row.record.key.platform),
            registered_source: Some(row.record.provider),
            last_failure,
            media,
        })
    }

    pub(crate) async fn track_versions(&self, value: &str, after: &str) -> io::Result<VersionPage> {
        let id = TrackId::parse(value)?;
        let after = if after.is_empty() {
            Uuid::nil()
        } else {
            version(after)?
        };
        if let Some(mut versions) = self.library.versions(id, after, PAGE_SIZE + 1).await? {
            let next = (versions.entries.len() > PAGE_SIZE)
                .then(|| versions.entries[PAGE_SIZE - 1].version.simple().to_string());
            versions.entries.truncate(PAGE_SIZE);
            return Ok(VersionPage {
                entries: versions
                    .entries
                    .into_iter()
                    .map(|track| VersionEntry {
                        version: track.version.simple().to_string(),
                        cached: false,
                        format: Some(track.format),
                        source: None,
                        bytes: track.bytes,
                    })
                    .collect(),
                latest: versions.latest.map(|id| id.simple().to_string()),
                next,
            });
        }
        let row = self.registered_remote(id).await?;
        let current = if let Some(media) = &self.media {
            media.cache_for_track(&row.record.key).await
        } else {
            None
        };
        Ok(VersionPage {
            latest: current
                .as_ref()
                .map(|entry| entry.version.simple().to_string()),
            entries: current
                .into_iter()
                .filter(|entry| entry.version > after)
                .map(|entry| VersionEntry {
                    version: entry.version.simple().to_string(),
                    cached: true,
                    format: entry.content_type,
                    source: entry.provider,
                    bytes: entry.bytes,
                })
                .collect(),
            next: None,
        })
    }

    pub(crate) async fn prepare_track(&self, value: &str) -> io::Result<PreparedTrack> {
        let id = TrackId::parse(value)?;
        if self.library.track(id)?.is_some() {
            return Err(invalid(
                "managed originals are already stored in the library",
            ));
        }
        let row = self.active_remote(id).await?;
        let lease = self.open_remote(row, WaitFor::Complete, None, None).await?;
        Ok(PreparedTrack {
            id: id.to_string(),
            version: lease.version().simple().to_string(),
        })
    }

    pub(crate) async fn import_local(
        &self,
        path: PathBuf,
        replace: Option<String>,
    ) -> io::Result<LocalTrack> {
        self.library.import(path, replace).await
    }

    pub(crate) async fn retire_track(&self, id: String) -> io::Result<()> {
        self.set_track_retired(id, true).await
    }

    pub(crate) async fn restore_track(&self, id: String) -> io::Result<()> {
        self.set_track_retired(id, false).await
    }

    async fn set_track_retired(&self, id: String, retired: bool) -> io::Result<()> {
        let track = TrackId::parse(&id)?;
        if self.library.track(track)?.is_some() {
            self.library.set_retired(id, retired).await
        } else {
            self.identities.set_retired(track, retired).await
        }
    }
}
