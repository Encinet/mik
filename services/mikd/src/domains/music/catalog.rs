//! A catalog cursor is a short-lived capability, never a provider cursor.
use super::{
    Media, Source, failure,
    identity::IdentityStore,
    invalid,
    library::Library,
    track::{TrackId, TrackRecord, TrackRef, search_rank, status},
};
use mik_protocol::music::{
    MusicCatalogPage, MusicCatalogQuery, MusicCollectionPage, MusicCollectionQuery, MusicTrackData,
    MusicTrackQuery, MusicTracks,
    music_catalog_query::Scope,
    music_failure::Code,
    music_track_data::{Availability, State},
};
use std::{
    collections::{BTreeMap, BTreeSet, HashMap},
    io,
    sync::Mutex,
    time::{Duration, Instant},
};
use uuid::Uuid;

const PAGE: usize = 24; // normalized fields guarantee this page is <128 KiB even in UTF-8
const RESULTS: usize = 384;
const SNAPSHOT_BYTES: usize = 2 * 1024 * 1024;
const CURSORS: usize = 32;
const TTL: Duration = Duration::from_secs(120);

#[derive(PartialEq, Eq)]
enum Query {
    List(String, Scope),
    Collection(String),
}

enum Row {
    Known(MusicTrackData),
    Discovered(TrackRecord),
}

struct Snapshot {
    rows: Vec<Row>,
    title: String,
    partial: bool,
}

enum Position {
    Browse {
        revision: (Uuid, u64),
        ready: Vec<(TrackRef, Uuid)>,
        after: String,
    },
    Snapshot(Snapshot),
}

struct Cursor {
    query: Query,
    position: Position,
    expires: Instant,
}

#[derive(Default)]
pub(super) struct Cursors(Mutex<HashMap<Uuid, Cursor>>);

impl Cursors {
    fn take(&self, token: &[u8], query: &Query) -> io::Result<Option<Cursor>> {
        if token.is_empty() {
            return Ok(None);
        }
        let id = Uuid::from_slice(token).map_err(|_| failure(Code::CursorExpired))?;
        let mut cursors = self
            .0
            .lock()
            .map_err(|_| io::Error::other("music state lock unavailable"))?;
        cursors.retain(|_, entry| entry.expires > Instant::now());
        if !cursors.get(&id).is_some_and(|entry| entry.query == *query) {
            return Err(failure(Code::CursorExpired));
        }
        Ok(cursors.remove(&id))
    }

    fn save(&self, query: Query, position: Position, expires: Instant) -> io::Result<Vec<u8>> {
        let mut cursors = self
            .0
            .lock()
            .map_err(|_| io::Error::other("music state lock unavailable"))?;
        cursors.retain(|_, entry| entry.expires > Instant::now());
        if cursors.len() == CURSORS
            && let Some(oldest) = cursors
                .iter()
                .min_by_key(|(_, entry)| entry.expires)
                .map(|(id, _)| *id)
        {
            cursors.remove(&oldest);
        }
        let id = Uuid::new_v4();
        cursors.insert(
            id,
            Cursor {
                query,
                position,
                expires,
            },
        );
        Ok(id.as_bytes().to_vec())
    }
}

pub(super) struct Catalog<'a> {
    pub library: &'a Library,
    pub identities: &'a IdentityStore,
    pub source: Option<&'a Source>,
    pub media: Option<&'a Media>,
    pub cursors: &'a Cursors,
}

impl Catalog<'_> {
    async fn ready(&self) -> Vec<(TrackRef, Uuid)> {
        let Some(media) = self.media else {
            return Vec::new();
        };
        let mut ready = media.ready().await;
        ready.sort_unstable();
        ready
    }

    pub async fn list(&self, request: MusicCatalogQuery) -> io::Result<MusicCatalogPage> {
        if request.query.len() > 768 {
            return Err(invalid("music query too long"));
        }
        let scope = Scope::try_from(request.scope).map_err(|_| invalid("invalid catalog scope"))?;
        let keyword = request.query.trim();
        let query = Query::List(keyword.into(), scope);
        let cursor = self.cursors.take(&request.cursor, &query)?;
        let expires = cursor
            .as_ref()
            .map_or_else(|| Instant::now() + TTL, |cursor| cursor.expires);
        let position = cursor.map(|cursor| cursor.position);
        if keyword.is_empty() {
            let ready = self.ready().await;
            let after = match &position {
                Some(Position::Browse { after, .. }) => after.as_str(),
                _ => "",
            };
            let (revision, mut tracks) = self.library.browse(after, PAGE + 1)?;
            let revision = (revision, self.identities.revision());
            if let Some(Position::Browse {
                revision: old,
                ready: old_ready,
                ..
            }) = &position
                && (*old != revision || *old_ready != ready)
            {
                return Err(failure(Code::CursorExpired));
            }
            let mut remote = self
                .identities
                .ready_page(
                    ready.iter().map(|(key, _)| key.clone()).collect(),
                    after.to_owned(),
                    PAGE + 1,
                )
                .await?;
            if revision != (self.library.revision()?, self.identities.revision()) {
                return Err(failure(Code::CursorExpired));
            }
            tracks.append(&mut remote);
            tracks.sort_unstable_by(|a, b| a.track_id.cmp(&b.track_id));
            let next_cursor = if tracks.len() > PAGE {
                tracks.truncate(PAGE);
                let after = tracks[PAGE - 1].track_id.clone();
                self.cursors.save(
                    query,
                    Position::Browse {
                        revision,
                        ready,
                        after,
                    },
                    expires,
                )?
            } else {
                Vec::new()
            };
            return Ok(MusicCatalogPage {
                tracks,
                next_cursor,
                partial: false,
            });
        }
        let snapshot = match position {
            Some(Position::Snapshot(snapshot)) => snapshot,
            _ => self.search(keyword, scope).await?,
        };
        self.page(query, snapshot, expires)
            .await
            .map(|(_, page)| page)
    }

    async fn search(&self, keyword: &str, scope: Scope) -> io::Result<Snapshot> {
        let discovery = async {
            match (scope, self.source) {
                (Scope::All, Some(source)) => source.discover(keyword).await,
                _ => super::source::Discovery::default(),
            }
        };
        let cache = async {
            let ready = self.ready().await.into_iter().map(|(key, _)| key).collect();
            self.identities
                .search(keyword.to_owned(), ready, scope == Scope::Ready, RESULTS)
                .await
        };
        let (discovery, known) = tokio::join!(discovery, cache);
        let (mut known, known_partial) = known?;
        let (local, local_partial) = self.library.search(keyword, RESULTS)?;
        let mut rows = BTreeMap::new();
        let mut partial = discovery.partial || known_partial || local_partial;
        for track in local {
            rows.insert(
                (
                    search_rank(
                        keyword,
                        &track.title,
                        &track.artist,
                        &track.album,
                        track.original_author.as_deref().unwrap_or(""),
                    ),
                    track.track_id.clone(),
                ),
                Row::Known(track),
            );
        }
        let records = self
            .identities
            .by_keys(
                discovery
                    .tracks
                    .iter()
                    .map(|track| track.key.clone())
                    .collect(),
            )
            .await?;
        let mut discovered = BTreeSet::new();
        let by_key = records
            .into_iter()
            .map(|row| (row.record.key.clone(), row))
            .collect::<BTreeMap<_, _>>();
        let mut size = 0;
        for track in discovery.tracks {
            if !discovered.insert(track.key.clone()) {
                continue;
            }
            let rank_key = search_rank(keyword, &track.title, &track.artist, &track.album, "")
                .or(Some(u32::MAX));
            if let Some(row) = by_key.get(&track.key) {
                if row.retired {
                    continue;
                }
                let id = row.id.to_string();
                let existing = known.remove(&id);
                // Discovering an existing key can supply new resolver metadata or change providers.
                // Refresh it only when this row is published, just like a newly discovered identity.
                if size + track.info_json.len() <= SNAPSHOT_BYTES {
                    size += track.info_json.len();
                    rows.insert((rank_key, id), Row::Discovered(track));
                } else {
                    rows.insert(
                        (rank_key, id),
                        Row::Known(existing.unwrap_or_else(|| {
                            row.record.display(row.id, Availability::NeedsPreparation)
                        })),
                    );
                }
            } else if size + track.info_json.len() <= SNAPSHOT_BYTES {
                size += track.info_json.len();
                let key = format!("~{}:{}", track.key.platform, track.key.id);
                rows.insert((rank_key, key), Row::Discovered(track));
            } else {
                partial = true;
            }
        }
        for (id, track) in known {
            rows.insert(
                (
                    search_rank(keyword, &track.title, &track.artist, &track.album, ""),
                    id,
                ),
                Row::Known(track),
            );
        }
        partial |= rows.len() > RESULTS;
        Ok(Snapshot {
            rows: rows.into_values().take(RESULTS).collect(),
            title: String::new(),
            partial,
        })
    }

    pub async fn collection(
        &self,
        request: MusicCollectionQuery,
    ) -> io::Result<MusicCollectionPage> {
        let query = Query::Collection(request.reference);
        let cursor = self.cursors.take(&request.cursor, &query)?;
        let expires = cursor
            .as_ref()
            .map_or_else(|| Instant::now() + TTL, |cursor| cursor.expires);
        let snapshot = if let Some(Cursor {
            position: Position::Snapshot(snapshot),
            ..
        }) = cursor
        {
            snapshot
        } else {
            let Query::Collection(reference) = &query else {
                return Err(invalid("invalid collection query"));
            };
            let collection = self
                .source
                .ok_or_else(|| failure(Code::Unavailable))?
                .collection(reference, RESULTS)
                .await?;
            let mut size = 0;
            let mut shipped = BTreeSet::new();
            let mut partial = collection.discovery.partial;
            let mut rows = Vec::new();
            for track in collection.discovery.tracks {
                if !shipped.insert(track.key.clone()) {
                    continue;
                }
                size += track.info_json.len();
                if size > SNAPSHOT_BYTES || rows.len() >= RESULTS {
                    partial = true;
                    break;
                }
                rows.push(Row::Discovered(track));
            }
            Snapshot {
                rows,
                title: super::track::label(&collection.title),
                partial,
            }
        };
        let (title, page) = self.page(query, snapshot, expires).await?;
        Ok(MusicCollectionPage {
            title,
            page: Some(page),
        })
    }

    async fn page(
        &self,
        query: Query,
        mut snapshot: Snapshot,
        expires: Instant,
    ) -> io::Result<(String, MusicCatalogPage)> {
        let mut ordered = Vec::new();
        let mut candidates = Vec::new();
        for row in snapshot.rows.drain(..snapshot.rows.len().min(PAGE)) {
            match row {
                Row::Known(track) => ordered.push(Some(track)),
                Row::Discovered(track) => {
                    ordered.push(None);
                    candidates.push(track);
                }
            }
        }
        let ready = self
            .ready()
            .await
            .into_iter()
            .map(|(key, _)| key)
            .collect::<BTreeSet<_>>();
        let mut published = self.identities.publish(candidates).await?.into_iter();
        let mut tracks = Vec::new();
        for slot in ordered {
            if let Some(track) = slot {
                tracks.push(track);
            } else if let Some(Some(row)) = published.next() {
                if !row.retired {
                    tracks.push(row.display(&ready));
                }
            } else {
                snapshot.partial = true;
            }
        }
        let partial = snapshot.partial;
        let title = snapshot.title.clone();
        let next_cursor = if snapshot.rows.is_empty() {
            Vec::new()
        } else {
            self.cursors
                .save(query, Position::Snapshot(snapshot), expires)?
        };
        Ok((
            title,
            MusicCatalogPage {
                tracks,
                next_cursor,
                partial,
            },
        ))
    }

    pub async fn tracks(&self, request: MusicTrackQuery) -> io::Result<MusicTracks> {
        if request.track_ids.is_empty() || request.track_ids.len() > PAGE {
            return Err(invalid("invalid music track batch"));
        }
        let ids = request
            .track_ids
            .iter()
            .map(|id| TrackId::parse(id))
            .collect::<io::Result<Vec<_>>>()?;
        let ready = self
            .ready()
            .await
            .into_iter()
            .map(|(key, _)| key)
            .collect::<BTreeSet<_>>();
        let rows = self.identities.tracks(ids.clone()).await?;
        let tracks = ids
            .into_iter()
            .zip(rows)
            .map(|(id, row)| {
                Ok(self
                    .library
                    .track(id)?
                    .or_else(|| row.map(|row| row.display(&ready)))
                    .unwrap_or_else(|| status(id, State::Unknown, Availability::Unavailable)))
            })
            .collect::<io::Result<_>>()?;
        Ok(MusicTracks { tracks })
    }
}
