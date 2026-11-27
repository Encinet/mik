use super::{App, Layer, Level, Page, move_selection};
use crate::{
    admin::tui::{
        catalog::{
            BySource, Collection, Online, PageQuery, SourceDetail, Step, Versions,
            separate_audio_source,
        },
        form::{Confirmation, Prompt},
        jobs::{Change, Effect, Work},
        menu::Item,
    },
    domains::music::{
        CacheEntry, CachePage, CacheState, CatalogPage, CatalogQuery, InspectedMedia, SourceState,
        TrackInspection, VersionPage,
    },
    transport::admin::Command,
};
use crossterm::event::KeyCode;

impl App {
    pub(super) fn next_music_load(&mut self, collection: Collection) -> Option<Work> {
        if !self.ready(collection) {
            return None;
        }
        match collection {
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

    pub(super) fn music_key(&mut self, collection: Collection, key: KeyCode) -> Option<Work> {
        match collection {
            Collection::Library => self.library_key(key),
            Collection::Sources => self.sources_key(key),
            Collection::Cache => self.cache_key(key),
        }
    }

    pub(super) fn select_music_row(&mut self, index: usize) -> bool {
        let pending = self.pending.is_some();
        let on_music = self.page == Page::Music;
        let (selected, count) = match self.layer_mut() {
            Some(Layer::Online(online)) if !pending => {
                let count = online.current().entries.len();
                (&mut online.selected, count)
            }
            Some(Layer::Versions(versions)) if !pending => versions.list.selection(),
            Some(Layer::SourceTracks(tracks)) if !pending => tracks.list.selection(),
            Some(Layer::SourceCache(cache)) if !pending => cache.list.selection(),
            None if on_music => match self.music.current {
                Collection::Library => self.music.library.list.selection(),
                Collection::Cache => self.music.cache.list.selection(),
                Collection::Sources => {
                    let sources = &mut self.music.sources;
                    (&mut sources.selected, sources.entries.len())
                }
            },
            Some(_) | None => return false,
        };
        if index >= count {
            return false;
        }
        *selected = index;
        true
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
            KeyCode::Char('a') => return self.prompt(Prompt::Import, ""),
            KeyCode::Char('m' | 'd' | 'o' | 'f' | 'y' | 'l' | 'c') => {
                let mut items = Vec::new();
                if discover {
                    items.extend([
                        Item::form("Search online", Prompt::Discover, "").key('d'),
                        Item::form("Open a collection link", Prompt::PreviewCollection, "")
                            .key('o'),
                    ]);
                }
                items.extend([
                    Item::form("Find by track ID", Prompt::Track, "").key('f'),
                    Item::form("Inspect lyrics by version", Prompt::Lyrics, "").key('y'),
                    Item::form("Set lyrics by version", Prompt::SetLyrics, "").key('l'),
                    Item::form("Clear lyrics by version", Prompt::ClearLyrics, "").key('c'),
                ]);
                return self.menu_key(key, "Track library actions", None, items);
            }
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
        match key {
            KeyCode::Enter => self.begin(Work::Source(self.music.sources.selected()?.id.clone())),
            KeyCode::Char('f') => self.prompt(Prompt::Source, ""),
            KeyCode::Char('a') => self.prompt(Prompt::AddSource, ""),
            KeyCode::Char('m') => self.source_actions(key),
            _ => None,
        }
    }

    pub(in crate::admin::tui) fn manageable_source(&self) -> Option<&SourceState> {
        if self.pending.is_some() || !self.ready(Collection::Sources) {
            return None;
        }
        match self.layer() {
            Some(Layer::Source(SourceDetail::Registered(source))) => Some(source),
            Some(_) => None,
            None => self.music.sources.selected(),
        }
    }

    fn source_actions(&mut self, key: KeyCode) -> Option<Work> {
        let source = self.manageable_source()?;
        let (id, name) = (source.id.clone(), source.name.clone());
        let enabled = !source.enabled;
        let (label, work_label) = if enabled {
            ("Enable", "Enable source")
        } else {
            ("Disable", "Disable source")
        };
        let mut items = Vec::new();
        if source.enabled
            && self.ready(Collection::Cache)
            && source
                .actions
                .values()
                .flatten()
                .any(|action| action == "musicUrl")
        {
            items.push(Item::form(
                "Test playback",
                Prompt::TestSource {
                    id: id.clone(),
                    name: name.clone(),
                },
                "",
            ));
        }
        if source.subscription {
            items.push(Item::form(
                "Edit link",
                Prompt::EditSource {
                    id: id.clone(),
                    name: name.clone(),
                },
                "",
            ));
        }
        items.push(Item::run(
            label,
            change(
                Command::SetSourceEnabled {
                    id: id.clone(),
                    enabled,
                },
                Collection::Sources,
                work_label,
            ),
        ));
        if source.subscription {
            items.push(Item::run(
                "Refresh",
                change(
                    Command::RefreshSource(id.clone()),
                    Collection::Sources,
                    "Refresh subscription",
                ),
            ));
        }
        let impact = if source.subscription {
            "Removes the installed subscription. Registered tracks, media cache and original files are kept."
        } else {
            "Removes the script from the LX registry. The local file, registered tracks and media cache are kept."
        };
        items.push(Item::confirm(
            "Remove",
            Confirmation::new(
                change(
                    Command::RemoveSource(id.clone()),
                    Collection::Sources,
                    "Remove source",
                ),
                format!("{name}\n{id}"),
                impact,
            ),
        ));
        self.menu_key(key, "Source actions", Some(name), items)
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
            KeyCode::Char('m' | 'f' | 'c') => {
                let items = vec![
                    Item::form("Find by media version", Prompt::CacheVersion, "").key('f'),
                    Item::confirm("Clear idle cache", Confirmation::new(
                        change(Command::ClearIdleCache, Collection::Cache, "Clear idle cache"),
                        "Remote music cache".into(),
                        "Removes idle copies across all pages. Active playback and downloads keep their entries. New playback may download the removed media again.",
                    )).key('c'),
                ];
                return self.menu_key(key, "Cache actions", None, items);
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
                if !matches!(
                    key,
                    KeyCode::Char(
                        'm' | 'v' | 's' | 'a' | 'b' | 'y' | 'd' | 'u' | 'r' | 'x' | 'l' | 'c'
                    )
                ) {
                    self.scroll_panel(key);
                    return None;
                }
                let items = track_actions(track, library, sources, cache);
                let title = track.title.clone();
                return self.menu_key(key, "Track actions", Some(title), items);
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
            Layer::CacheEntry(entry) => {
                if !matches!(key, KeyCode::Char('m' | 't' | 's' | 'y' | 'x')) {
                    self.scroll_panel(key);
                    return None;
                }
                let items = cache_entry_actions(entry, library, sources, cache);
                let title = entry.track.as_ref().map(|track| track.title.clone());
                return self.menu_key(key, "Cached media actions", title, items);
            }
            _ => return None,
        };
        self.begin(work)
    }

    fn source_detail_key(&mut self, key: KeyCode) -> Option<Work> {
        if key == KeyCode::Char('m') {
            return self.source_actions(key);
        }
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

    pub(super) fn source_test_finished(
        &mut self,
        result: Result<(), String>,
        label: &str,
        same_context: bool,
    ) {
        self.music.sources.refresh = true;
        for layer in &mut self.layers {
            layer.refresh |= matches!(layer.layer, Layer::Source(_));
        }
        match result {
            Ok(()) => {
                if same_context && matches!(self.layer(), Some(Layer::Form(_))) {
                    drop(self.pop_layer());
                }
                self.notice(
                    Level::Good,
                    "Audio started downloading from the selected source.",
                );
            }
            Err(error) => self.work_error(label, &error, same_context, Level::Error),
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
                (Layer::SourceTracks(_), _) => matches!(collection, Collection::Library),
                (Layer::Source(_), _) => matches!(collection, Collection::Sources),
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

pub(in crate::admin::tui) fn track_actions(
    track: &TrackInspection,
    library: bool,
    sources: bool,
    cache: bool,
) -> Vec<Item> {
    let mut items = Vec::new();
    if library {
        items.push(
            Item::run(
                "Media versions",
                Work::Versions {
                    id: track.id.clone(),
                    title: track.title.clone(),
                    page: PageQuery::first(),
                },
            )
            .key('v'),
        );
    }
    if cache && let Some(InspectedMedia::Cached { version, .. }) = &track.media {
        items.push(Item::run("Cached media", Work::CacheEntry(version.clone())).key('b'));
    }
    if let Some(version) = track.lyrics_version() {
        items.push(Item::run("Inspect lyrics", Work::Lyrics(version.clone())).key('y'));
        items.push(Item::form("Set lyrics override", Prompt::SetLyrics, &version).key('l'));
        items.push(Item::form("Clear lyrics override", Prompt::ClearLyrics, version).key('c'));
    }
    if sources {
        if let Some(id) = &track.registered_source {
            items.push(Item::run("Registered source", Work::FollowSource(id.clone())).key('s'));
        }
        if let Some(id) = separate_audio_source(track) {
            items.push(
                Item::run("Audio download source", Work::FollowSource(id.to_owned())).key('a'),
            );
        }
    }
    if track.active() {
        if track.local() && library {
            items.push(
                Item::form(
                    "Replace local file",
                    Prompt::Replace {
                        id: track.id.clone(),
                        title: track.title.clone(),
                    },
                    "",
                )
                .key('r'),
            );
        } else if !track.local() && sources && cache {
            items.push(
                Item::run(
                    "Prepare cache",
                    change(
                        Command::PrepareTrack(track.id.clone()),
                        Collection::Cache,
                        "Prepare cache",
                    ),
                )
                .key('d'),
            );
        }
        if library {
            items.push(
                Item::confirm(
                    "Retire track",
                    Confirmation::new(
                        change(
                            Command::RetireTrack(track.id.clone()),
                            Collection::Library,
                            "Retire track",
                        ),
                        format!("{}\n{}", track.id, track.title),
                        "The track stops accepting new playback. Open playback continues.",
                    ),
                )
                .key('x'),
            );
        }
    } else if library {
        items.push(
            Item::run(
                "Restore track",
                change(
                    Command::RestoreTrack(track.id.clone()),
                    Collection::Library,
                    "Restore track",
                ),
            )
            .key('u'),
        );
    }
    items
}

pub(in crate::admin::tui) fn cache_entry_actions(
    entry: &CacheEntry,
    library: bool,
    sources: bool,
    cache: bool,
) -> Vec<Item> {
    let mut items = Vec::new();
    if library && let Some(track) = &entry.track {
        items.push(Item::run("Open track", Work::Track(track.id.clone())).key('t'));
    }
    if let Some(source) = &entry.source {
        if sources {
            items.push(
                Item::run(
                    "Audio download source",
                    Work::FollowSource(source.id.clone()),
                )
                .key('s'),
            );
        }
        items.push(Item::run("Inspect lyrics", Work::Lyrics(entry.version.clone())).key('y'));
    }
    if cache && entry.state == CacheState::Idle {
        let title = entry
            .track
            .as_ref()
            .map_or("Unknown track", |track| &track.title);
        items.push(Item::confirm("Evict cached version", Confirmation::new(
            change(Command::EvictCache(entry.version.clone()), Collection::Cache, "Evict cached version"),
            format!("{}\n{title}", entry.version),
            "Removes this cached copy. Resuming from a position saved for this version may require starting the track again.",
        )).key('x'));
    }
    items
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
