use super::{App, Layer, Level, move_selection};
use crate::{
    admin::tui::{
        catalog::{
            BySource, Collection, Online, PageQuery, SourceDetail, Step, Versions,
            separate_audio_source,
        },
        form::{Confirmation, Form, Prompt},
        jobs::{Change, Effect, Work},
    },
    domains::music::{
        CacheEntry, CachePage, CacheState, CatalogPage, CatalogQuery, InspectedMedia,
        TrackInspection, VersionPage,
    },
    transport::admin::Command,
};
use crossterm::event::KeyCode;

impl App {
    pub(super) fn next_music_load(&mut self) -> Option<Work> {
        if !self.ready(self.music.page) {
            return None;
        }
        match self.music.page {
            Collection::Library if std::mem::take(&mut self.music.library.list.refresh) => {
                Some(Work::Library {
                    keyword: self.music.library.keyword.clone(),
                    page: self.music.library.list.query(Step::Current)?,
                })
            }
            Collection::Sources if std::mem::take(&mut self.music.sources.refresh) => {
                Some(Work::Sources)
            }
            Collection::Cache if std::mem::take(&mut self.music.cache.list.refresh) => {
                Some(Work::Cache {
                    source: None,
                    page: self.music.cache.list.query(Step::Current)?,
                })
            }
            _ => None,
        }
    }

    pub(super) fn music_key(&mut self, key: KeyCode) -> Option<Work> {
        match self.music.page {
            Collection::Library => self.library_key(key),
            Collection::Sources => self.sources_key(key),
            Collection::Cache => self.cache_key(key),
        }
    }

    fn library_key(&mut self, key: KeyCode) -> Option<Work> {
        let discover = self.ready(Collection::Sources);
        let ready = self.ready(Collection::Library);
        let list = &mut self.music.library.list;
        if move_selection(key, &mut list.selected, list.entries.len()) || !ready {
            return None;
        }
        let work = match key {
            KeyCode::Enter => Work::Track(list.entries.get(list.selected)?.id.clone()),
            KeyCode::Char('p' | 'n') => Work::Library {
                keyword: self.music.library.keyword.clone(),
                page: list.query(page_step(key))?,
            },
            KeyCode::Char('/') => {
                let keyword = self.music.library.keyword.clone();
                return self.prompt(Prompt::FindLibrary, &keyword);
            }
            KeyCode::Char('d') if discover => return self.prompt(Prompt::Discover, ""),
            KeyCode::Char('o') if discover => return self.prompt(Prompt::PreviewCollection, ""),
            KeyCode::Char('f') => return self.prompt(Prompt::Track, ""),
            KeyCode::Char('a') => return self.prompt(Prompt::Import, ""),
            KeyCode::Char('y') => return self.prompt(Prompt::Lyrics, ""),
            KeyCode::Char('l') => return self.prompt(Prompt::SetLyrics, ""),
            KeyCode::Char('c') => return self.prompt(Prompt::ClearLyrics, ""),
            _ => return None,
        };
        self.begin(work)
    }

    fn sources_key(&mut self, key: KeyCode) -> Option<Work> {
        let sources = &mut self.music.sources;
        if move_selection(key, &mut sources.selected, sources.entries.len())
            || !self.ready(Collection::Sources)
        {
            return None;
        }
        let work = match key {
            KeyCode::Enter => {
                let sources = &self.music.sources;
                Work::Source(sources.entries.get(sources.selected)?.id.clone())
            }
            KeyCode::Char('f') => return self.prompt(Prompt::Source, ""),
            KeyCode::Char('a') => return self.prompt(Prompt::Subscribe, ""),
            KeyCode::Char('r') => change(
                Command::RefreshSources,
                Collection::Sources,
                "Refresh subscriptions",
            ),
            _ => return None,
        };
        self.begin(work)
    }

    fn cache_key(&mut self, key: KeyCode) -> Option<Work> {
        let list = &mut self.music.cache.list;
        if move_selection(key, &mut list.selected, list.entries.len())
            || !self.ready(Collection::Cache)
        {
            return None;
        }
        let list = &self.music.cache.list;
        let work = match key {
            KeyCode::Enter => Work::CacheEntry(list.entries.get(list.selected)?.version.clone()),
            KeyCode::Char('p' | 'n') => Work::Cache {
                source: None,
                page: list.query(page_step(key))?,
            },
            KeyCode::Char('f') => return self.prompt(Prompt::CacheVersion, ""),
            KeyCode::Char('c') => {
                let work = change(
                    Command::ClearIdleCache,
                    Collection::Cache,
                    "Clear idle cache",
                );
                self.show(Layer::Confirm(Confirmation::new(
                    work,
                    "Remote music cache".into(),
                    "Removes idle copies across all pages. Active playback and downloads keep their entries. New playback may download the removed media again.",
                )));
                return None;
            }
            _ => return None,
        };
        self.begin(work)
    }

    pub(super) fn next_layer_load(&mut self) -> Option<Work> {
        let open = self.layers.last_mut()?;
        let list_refresh = match &mut open.layer {
            Layer::Versions(versions) => std::mem::take(&mut versions.list.refresh),
            Layer::SourceTracks(tracks) => std::mem::take(&mut tracks.list.refresh),
            Layer::SourceCache(cache) => std::mem::take(&mut cache.list.refresh),
            _ => false,
        };
        if !std::mem::take(&mut open.refresh) && !list_refresh {
            return None;
        }
        match &open.layer {
            Layer::Track(track) => Some(Work::Track(track.id.clone())),
            Layer::Online(online) => Some(Work::Online {
                query: online.query.clone(),
                after: String::new(),
            }),
            Layer::Versions(versions) => Some(Work::Versions {
                id: versions.id.clone(),
                title: versions.title.clone(),
                page: versions.list.query(Step::Current)?,
            }),
            Layer::Source(source) => Some(Work::FollowSource(source.id().to_owned())),
            Layer::SourceTracks(tracks) => Some(Work::SourceTracks {
                id: tracks.source.clone(),
                page: tracks.list.query(Step::Current)?,
            }),
            Layer::SourceCache(cache) => Some(Work::Cache {
                source: Some(cache.source.clone()),
                page: cache.list.query(Step::Current)?,
            }),
            Layer::CacheEntry(entry) => Some(Work::CacheEntry(entry.version.clone())),
            Layer::Lyrics(lyrics) => Some(Work::Lyrics(lyrics.version.clone())),
            _ => None,
        }
    }

    pub(super) fn music_layer_key(&mut self, key: KeyCode) -> Option<Work> {
        let loading = self.pending.is_some();
        let library = self.ready(Collection::Library);
        let sources = self.ready(Collection::Sources);
        let cache = self.ready(Collection::Cache);
        let work = match self.layer_mut()? {
            Layer::Track(track) => {
                let Some(work) = track_work(track, key, sources, cache) else {
                    if let Some(layer) = track_edit(track, key) {
                        self.show(layer);
                    } else {
                        self.scroll_panel(key);
                    }
                    return None;
                };
                work
            }
            Layer::Online(online) if !loading => online_key(online, key)?,
            Layer::Versions(versions) if !loading => versions_key(versions, key, cache)?,
            Layer::Lyrics(lyrics) => {
                let prompt = match key {
                    KeyCode::Char('l') if lyrics.has_media => Prompt::SetLyrics,
                    KeyCode::Char('c') if !lyrics.overrides.is_empty() => Prompt::ClearLyrics,
                    _ => {
                        self.scroll_panel(key);
                        return None;
                    }
                };
                let version = lyrics.version.clone();
                return self.prompt(prompt, &version);
            }
            Layer::Source(_) => return self.source_detail_key(key),
            Layer::SourceTracks(tracks) if !loading && library => {
                let list = &mut tracks.list;
                if move_selection(key, &mut list.selected, list.entries.len()) {
                    return None;
                }
                match key {
                    KeyCode::Enter => Work::Track(list.entries.get(list.selected)?.id.clone()),
                    KeyCode::Char('p' | 'n') => Work::SourceTracks {
                        id: tracks.source.clone(),
                        page: list.query(page_step(key))?,
                    },
                    _ => return None,
                }
            }
            Layer::SourceCache(downloads) if !loading && cache => {
                let list = &mut downloads.list;
                if move_selection(key, &mut list.selected, list.entries.len()) {
                    return None;
                }
                match key {
                    KeyCode::Enter => {
                        Work::CacheEntry(list.entries.get(list.selected)?.version.clone())
                    }
                    KeyCode::Char('p' | 'n') => Work::Cache {
                        source: Some(downloads.source.clone()),
                        page: list.query(page_step(key))?,
                    },
                    _ => return None,
                }
            }
            Layer::CacheEntry(entry) => match key {
                KeyCode::Char('t') if library => Work::Track(entry.track.as_ref()?.id.clone()),
                KeyCode::Char('s') if sources => {
                    Work::FollowSource(entry.source.as_ref()?.id.clone())
                }
                KeyCode::Char('y') if entry.source.is_some() => Work::Lyrics(entry.version.clone()),
                KeyCode::Char('x') if cache && entry.state == CacheState::Idle => {
                    let layer = evict(entry);
                    self.show(layer);
                    return None;
                }
                _ => {
                    self.scroll_panel(key);
                    return None;
                }
            },
            _ => return None,
        };
        self.begin(work)
    }

    fn source_detail_key(&mut self, key: KeyCode) -> Option<Work> {
        let Some(Layer::Source(source)) = self.layer() else {
            return None;
        };
        match key {
            KeyCode::Char('t') if self.ready(Collection::Library) && self.pending.is_none() => {
                let tracks = BySource::new(source.id().to_owned());
                self.show(Layer::SourceTracks(Box::new(tracks)));
            }
            KeyCode::Char('b') if self.ready(Collection::Cache) && self.pending.is_none() => {
                let downloads = BySource::new(source.id().to_owned());
                self.show(Layer::SourceCache(Box::new(downloads)));
            }
            KeyCode::Char('r' | 'x') if self.ready(Collection::Sources) => {
                let SourceDetail::Registered(source) = source else {
                    return None;
                };
                if !source.subscription {
                    return None;
                }
                if key == KeyCode::Char('r') {
                    return self.begin(change(
                        Command::RefreshSource(source.id.clone()),
                        Collection::Sources,
                        "Refresh subscription",
                    ));
                }
                let work = change(
                    Command::RemoveSource(source.id.clone()),
                    Collection::Sources,
                    "Remove subscription",
                );
                let target = format!("{}\n{}", source.id, source.name);
                self.show(Layer::Confirm(Confirmation::new(
                    work,
                    target,
                    "Removes the subscription and its registered source.",
                )));
            }
            _ => self.scroll_panel(key),
        }
        None
    }

    pub(super) fn library_loaded(
        &mut self,
        keyword: String,
        page: PageQuery,
        result: Result<CatalogPage, String>,
        same_context: bool,
    ) {
        if page.starts_over() {
            if !same_context {
                return;
            }
            if let Err(error) = result {
                self.work_error("Search registered tracks", &error, true, Level::Error);
                return;
            }
            self.music.library.keyword = keyword;
            drop(self.pop_layer());
        }
        self.music.library.list.load(
            page,
            result.map(|page| (page.entries, page.next)),
            |track| &track.id,
        );
    }

    pub(super) fn online_loaded(
        &mut self,
        query: CatalogQuery,
        after: &str,
        result: Result<CatalogPage, String>,
        label: &str,
    ) {
        match result {
            Ok(page) => {
                if let Some(Layer::Online(online)) = self.layer_mut()
                    && !after.is_empty()
                {
                    online.push(page);
                } else {
                    self.open_detail(Layer::Online(Box::new(Online::new(query, page))));
                }
            }
            Err(error) => {
                if let Some(Layer::Online(online)) = self.layer_mut() {
                    online.problem = Some(error.clone());
                }
                self.work_error(label, &error, true, Level::Error);
            }
        }
    }

    pub(super) fn versions_loaded(
        &mut self,
        id: String,
        title: String,
        page: PageQuery,
        result: Result<VersionPage, String>,
        label: &str,
    ) {
        if let Some(Layer::Versions(versions)) = self.layer_mut() {
            versions.load(page, result);
        } else {
            match result {
                Ok(result) => {
                    let mut versions = Versions::new(id, title);
                    versions.load(page, Ok(result));
                    self.show(Layer::Versions(Box::new(versions)));
                }
                Err(error) => self.work_error(label, &error, true, Level::Error),
            }
        }
    }

    pub(super) fn source_tracks_loaded(
        &mut self,
        id: &str,
        query: PageQuery,
        result: Result<CatalogPage, String>,
    ) {
        if let Some(Layer::SourceTracks(tracks)) = self.layer_mut()
            && tracks.source == id
        {
            tracks.list.load(
                query,
                result.map(|page| (page.entries, page.next)),
                |track| &track.id,
            );
        }
    }

    pub(super) fn source_cache_loaded(
        &mut self,
        id: &str,
        query: PageQuery,
        result: Result<CachePage, String>,
    ) {
        if let Some(Layer::SourceCache(cache)) = self.layer_mut()
            && cache.source == id
        {
            cache.list.load(
                query,
                result.map(|page| (page.entries, page.next)),
                |entry| &entry.version,
            );
        }
    }

    pub(super) fn apply_music_change(
        &mut self,
        collection: Collection,
        effect: &Effect,
        same_context: bool,
    ) {
        if same_context && matches!(self.layer(), Some(Layer::Form(_))) {
            drop(self.pop_layer());
        }
        if !matches!(collection, Collection::Cache) && !matches!(effect, Effect::Lyrics { .. }) {
            self.refresh(Collection::Cache, None);
        }
        let depth = self.layers.len();
        self.layers.retain_mut(|open| {
            let reload = match (&mut open.layer, effect) {
                (Layer::Source(source), Effect::SourceRemoved(id)) if source.id() == id => {
                    return false;
                }
                (Layer::CacheEntry(entry), Effect::CacheEvicted(version))
                    if entry.version == *version =>
                {
                    return false;
                }
                (Layer::Lyrics(lyrics), Effect::Lyrics { version, cleared })
                    if lyrics.version == *version =>
                {
                    if *cleared && !lyrics.has_media {
                        return false;
                    }
                    true
                }
                (Layer::Online(online), Effect::TrackState { id, retired }) => {
                    online.set_retired(id, *retired);
                    false
                }
                (_, Effect::Lyrics { .. }) => false,
                (Layer::Track(_) | Layer::Versions(_), _) => {
                    matches!(collection, Collection::Library | Collection::Cache)
                }
                (Layer::Source(_), _) => matches!(collection, Collection::Sources),
                (Layer::SourceTracks(_), _) => matches!(collection, Collection::Library),
                (Layer::SourceCache(_) | Layer::CacheEntry(_), _) => true,
                _ => false,
            };
            open.refresh |= reload;
            true
        });
        if self.layers.len() != depth {
            self.changed_context();
        }
    }
}

fn online_key(online: &mut Online, key: KeyCode) -> Option<Work> {
    let len = online.current().entries.len();
    if move_selection(key, &mut online.selected, len) {
        return None;
    }
    match key {
        KeyCode::Enter => Some(Work::Track(
            online.current().entries.get(online.selected)?.id.clone(),
        )),
        KeyCode::Char('p') => {
            online.previous();
            None
        }
        KeyCode::Char('n') => Some(Work::Online {
            query: online.query.clone(),
            after: online.next()?,
        }),
        _ => None,
    }
}

fn versions_key(versions: &mut Versions, key: KeyCode, cache: bool) -> Option<Work> {
    let list = &mut versions.list;
    if move_selection(key, &mut list.selected, list.entries.len()) {
        return None;
    }
    match key {
        KeyCode::Enter => Some(Work::Lyrics(
            list.entries
                .get(list.selected)
                .filter(|entry| entry.lyrics_available())?
                .version
                .clone(),
        )),
        KeyCode::Char('b') if cache => Some(Work::CacheEntry(
            list.entries
                .get(list.selected)
                .filter(|entry| entry.cached)?
                .version
                .clone(),
        )),
        KeyCode::Char('p' | 'n') => Some(Work::Versions {
            id: versions.id.clone(),
            title: versions.title.clone(),
            page: list.query(page_step(key))?,
        }),
        _ => None,
    }
}

fn track_work(track: &TrackInspection, key: KeyCode, sources: bool, cache: bool) -> Option<Work> {
    match key {
        KeyCode::Char('d') if track.active() && !track.local() && sources && cache => Some(change(
            Command::PrepareTrack(track.id.clone()),
            Collection::Cache,
            "Prepare cache",
        )),
        KeyCode::Char('u') if !track.active() => Some(change(
            Command::RestoreTrack(track.id.clone()),
            Collection::Library,
            "Restore track",
        )),
        KeyCode::Char('v') => Some(Work::Versions {
            id: track.id.clone(),
            title: track.title.clone(),
            page: PageQuery::first(),
        }),
        KeyCode::Char('s') if sources => Some(Work::FollowSource(track.registered_source.clone()?)),
        KeyCode::Char('a') if sources => {
            Some(Work::FollowSource(separate_audio_source(track)?.to_owned()))
        }
        KeyCode::Char('b') if cache => {
            if let Some(InspectedMedia::Cached { version, .. }) = &track.media {
                Some(Work::CacheEntry(version.clone()))
            } else {
                None
            }
        }
        KeyCode::Char('y') => Some(Work::Lyrics(track.lyrics_version()?)),
        _ => None,
    }
}

fn track_edit(track: &TrackInspection, key: KeyCode) -> Option<Layer> {
    let layer = match key {
        KeyCode::Char('r') if track.active() && track.local() => Layer::Form(Form::new(
            Prompt::Replace {
                id: track.id.clone(),
                title: track.title.clone(),
            },
            "",
        )),
        KeyCode::Char('x') if track.active() => Layer::Confirm(Confirmation::new(
            change(
                Command::RetireTrack(track.id.clone()),
                Collection::Library,
                "Retire track",
            ),
            format!("{}\n{}", track.id, track.title),
            "The track stops accepting new playback. Open playback continues.",
        )),
        KeyCode::Char('l') => Layer::Form(Form::new(Prompt::SetLyrics, &track.lyrics_version()?)),
        KeyCode::Char('c') => Layer::Form(Form::new(Prompt::ClearLyrics, &track.lyrics_version()?)),
        _ => return None,
    };
    Some(layer)
}

fn evict(entry: &CacheEntry) -> Layer {
    let title = entry
        .track
        .as_ref()
        .map_or("Unknown track", |track| &track.title);
    Layer::Confirm(Confirmation::new(
        change(
            Command::EvictCache(entry.version.clone()),
            Collection::Cache,
            "Evict cached version",
        ),
        format!("{}\n{title}", entry.version),
        "Removes this cached copy. Resuming from a position saved for this version may require starting the track again.",
    ))
}

fn change(command: Command, collection: Collection, label: &'static str) -> Work {
    Work::Change(Change {
        command,
        collection,
        label,
    })
}

fn page_step(key: KeyCode) -> Step {
    if key == KeyCode::Char('p') {
        Step::Previous
    } else {
        Step::Next
    }
}
