use crate::{
    domains::music::{
        CacheEntry, CachePage, CacheUsage, CatalogPage, CatalogQuery, CatalogTrack, SourceState,
        TrackInspection, VersionEntry, VersionPage,
    },
    transport::admin::Status,
};

#[derive(Default)]
pub(super) struct Music {
    pub page: Collection,
    pub library: Library,
    pub sources: Sources,
    pub cache: Cache,
}

#[derive(Clone, Copy, Default)]
pub(super) enum Collection {
    #[default]
    Library,
    Sources,
    Cache,
}

impl Collection {
    pub const ALL: [Self; 3] = [Self::Library, Self::Sources, Self::Cache];

    pub fn index(self) -> usize {
        self as usize
    }

    pub fn name(self) -> &'static str {
        match self {
            Self::Library => "Library",
            Self::Sources => "Sources",
            Self::Cache => "Cache",
        }
    }

    pub fn ready(self, status: &Status) -> bool {
        match self {
            Self::Library => status.music_catalog,
            Self::Sources => status.music_source_registry,
            Self::Cache => status.music_media_cache,
        }
    }
}

pub(super) fn separate_audio_source(track: &TrackInspection) -> Option<&str> {
    track
        .audio_source()
        .filter(|&audio| track.registered_source.as_deref() != Some(audio))
}

#[derive(Clone, Copy)]
pub(super) enum Step {
    First,
    Current,
    Previous,
    Next,
}

pub(super) struct PageQuery {
    pub after: String,
    step: Step,
}

impl PageQuery {
    pub fn first() -> Self {
        Self {
            after: String::new(),
            step: Step::First,
        }
    }

    pub fn starts_over(&self) -> bool {
        matches!(self.step, Step::First)
    }
}

pub(super) struct Paged<T> {
    pub entries: Vec<T>,
    pub selected: usize,
    pub problem: Option<String>,
    pub refresh: bool,
    pub known: bool,
    cursor_history: Vec<String>,
    next: Option<String>,
    pub select_on_load: Option<String>,
}

impl<T> Default for Paged<T> {
    fn default() -> Self {
        Self {
            entries: Vec::new(),
            selected: 0,
            problem: None,
            refresh: false,
            known: false,
            cursor_history: Vec::new(),
            next: None,
            select_on_load: None,
        }
    }
}

impl<T> Paged<T> {
    pub fn page(&self) -> usize {
        self.cursor_history.len() + 1
    }

    pub fn can_next(&self) -> bool {
        self.next.is_some()
    }

    pub fn query(&self, step: Step) -> Option<PageQuery> {
        let after = match step {
            Step::First => String::new(),
            Step::Current => self.cursor_history.last().cloned().unwrap_or_default(),
            Step::Previous => {
                self.cursor_history.last()?;
                self.cursor_history
                    .iter()
                    .rev()
                    .nth(1)
                    .cloned()
                    .unwrap_or_default()
            }
            Step::Next => self.next.clone()?,
        };
        Some(PageQuery { after, step })
    }

    pub fn load(
        &mut self,
        query: PageQuery,
        result: Result<(Vec<T>, Option<String>), String>,
        id: impl Fn(&T) -> &str,
    ) {
        let (entries, next) = match result {
            Ok(page) => page,
            Err(error) => {
                self.problem = Some(error);
                return;
            }
        };
        self.known = true;
        self.problem = None;
        if matches!(query.step, Step::Next) && entries.is_empty() {
            self.next = None;
            return;
        }
        let selected = self.select_on_load.take().or_else(|| {
            matches!(query.step, Step::Current)
                .then(|| {
                    self.entries
                        .get(self.selected)
                        .map(|row| id(row).to_owned())
                })
                .flatten()
        });
        match query.step {
            Step::First => self.cursor_history.clear(),
            Step::Next => self.cursor_history.push(query.after),
            Step::Previous => drop(self.cursor_history.pop()),
            Step::Current => {}
        }
        self.next = next;
        self.selected = selected
            .and_then(|selected| entries.iter().position(|row| id(row) == selected))
            .unwrap_or_else(|| {
                if matches!(query.step, Step::Current) {
                    self.selected.min(entries.len().saturating_sub(1))
                } else {
                    0
                }
            });
        self.entries = entries;
        if self.entries.is_empty() && !self.cursor_history.is_empty() {
            drop(self.cursor_history.pop());
            self.refresh = true;
        }
    }
}

#[derive(Default)]
pub(super) struct Library {
    pub list: Paged<CatalogTrack>,
    pub keyword: String,
}

pub(super) struct BySource<T> {
    pub source: String,
    pub list: Paged<T>,
}

impl<T> BySource<T> {
    pub fn new(source: String) -> Self {
        Self {
            source,
            list: Paged {
                refresh: true,
                ..Paged::default()
            },
        }
    }
}

pub(super) struct Online {
    pub query: CatalogQuery,
    pub selected: usize,
    pub page: usize,
    pub problem: Option<String>,
    pages: Vec<CatalogPage>,
}

impl Online {
    pub fn new(query: CatalogQuery, page: CatalogPage) -> Self {
        Self {
            query,
            selected: 0,
            page: 0,
            problem: None,
            pages: vec![page],
        }
    }

    pub fn current(&self) -> &CatalogPage {
        &self.pages[self.page]
    }

    pub fn previous(&mut self) {
        if self.page > 0 {
            self.page -= 1;
            self.selected = 0;
        }
    }

    pub fn next(&mut self) -> Option<String> {
        if self.page + 1 < self.pages.len() {
            self.page += 1;
            self.selected = 0;
            None
        } else {
            self.current().next.clone()
        }
    }

    pub fn can_next(&self) -> bool {
        self.page + 1 < self.pages.len() || self.current().next.is_some()
    }

    pub fn push(&mut self, page: CatalogPage) {
        self.pages.push(page);
        self.page = self.pages.len() - 1;
        self.selected = 0;
        self.problem = None;
    }

    pub fn set_retired(&mut self, id: &str, retired: bool) {
        for track in self.pages.iter_mut().flat_map(|page| &mut page.entries) {
            if track.id == id {
                track.retired = retired;
            }
        }
    }
}

pub(super) struct Versions {
    pub id: String,
    pub title: String,
    pub latest: Option<String>,
    pub list: Paged<VersionEntry>,
}

impl Versions {
    pub fn new(id: String, title: String) -> Self {
        Self {
            id,
            title,
            latest: None,
            list: Paged::default(),
        }
    }

    pub fn load(&mut self, query: PageQuery, result: Result<VersionPage, String>) {
        self.list.load(
            query,
            result.map(|page| {
                self.latest = page.latest;
                (page.entries, page.next)
            }),
            |entry| &entry.version,
        );
    }
}

#[derive(Default)]
pub(super) struct Cache {
    pub list: Paged<CacheEntry>,
    pub usage: Option<CacheUsage>,
}

impl Cache {
    pub fn load(&mut self, mut query: PageQuery, result: Result<CachePage, String>) {
        let rows = result.map(|page| {
            if page.usage.entries == 0 {
                self.list.cursor_history.clear();
                query = PageQuery {
                    after: String::new(),
                    step: Step::Current,
                };
            }
            self.usage = Some(page.usage);
            (page.entries, page.next)
        });
        self.list.load(query, rows, |entry| &entry.version);
    }
}

#[derive(Default)]
pub(super) struct Sources {
    pub entries: Vec<SourceState>,
    pub selected: usize,
    pub problem: Option<String>,
    pub refresh: bool,
    pub known: bool,
    pub select_on_load: Option<String>,
}

pub(super) enum SourceDetail {
    Registered(SourceState),
    Missing(String),
}

impl SourceDetail {
    pub fn id(&self) -> &str {
        match self {
            Self::Registered(state) => &state.id,
            Self::Missing(id) => id,
        }
    }
}

impl Sources {
    pub fn load(&mut self, result: Result<Vec<SourceState>, String>) {
        match result {
            Ok(entries) => {
                let selected = self.select_on_load.take().or_else(|| {
                    self.entries
                        .get(self.selected)
                        .map(|source| source.id.clone())
                });
                self.selected = selected
                    .and_then(|id| entries.iter().position(|source| source.id == id))
                    .unwrap_or_else(|| self.selected.min(entries.len().saturating_sub(1)));
                self.entries = entries;
                self.known = true;
                self.problem = None;
            }
            Err(error) => self.problem = Some(error),
        }
    }
}
