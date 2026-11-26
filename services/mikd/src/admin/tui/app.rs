mod music;

use super::{
    catalog::{BySource, Collection, Music, Online, SourceDetail, Versions},
    console::Console,
    form::{Confirmation, Form, Prompt, Submit},
    jobs::{ChangeError, Effect, Finished, Work},
    journal::Journal,
};
use crate::{
    admin::{
        console::RunError,
        systemd::{self, Action, Unit},
    },
    console::{CommandOutcome, Lane},
    domains::music::{CacheEntry, CatalogTrack, LyricsInspection, TrackInspection},
    transport::admin::Status,
};
use crossterm::event::{KeyCode, KeyEvent, KeyModifiers};
use std::io;

#[derive(Clone, Copy, Default, Eq, PartialEq)]
pub(super) enum Page {
    #[default]
    Overview,
    Journal,
    Music,
    Console,
}

impl Page {
    pub const ALL: [Self; 4] = [Self::Overview, Self::Journal, Self::Music, Self::Console];

    pub fn index(self) -> usize {
        self as usize
    }

    pub fn name(self) -> &'static str {
        match self {
            Self::Overview => "Overview",
            Self::Journal => "Logs",
            Self::Music => "Music",
            Self::Console => "Console",
        }
    }

    pub fn shortcut(self) -> char {
        match self {
            Self::Overview => '1',
            Self::Journal => '2',
            Self::Music => '3',
            Self::Console => '6',
        }
    }
}

pub(super) enum Layer {
    Help,
    Track(Box<TrackInspection>),
    Online(Box<Online>),
    Versions(Box<Versions>),
    Lyrics(LyricsInspection),
    Source(SourceDetail),
    SourceTracks(Box<BySource<CatalogTrack>>),
    SourceCache(Box<BySource<CacheEntry>>),
    CacheEntry(Box<CacheEntry>),
    Form(Form),
    Confirm(Confirmation),
    Notice,
}

struct OpenLayer {
    layer: Layer,
    scroll: u16,
    refresh: bool,
}

#[derive(Clone, Copy)]
pub(super) enum Level {
    Good,
    Warning,
    Error,
}

pub(super) struct Notice {
    pub level: Level,
    pub text: String,
}

pub(super) struct Pending {
    pub label: &'static str,
    pub modifies: bool,
    context: u64,
    layer_refresh: bool,
}

pub(super) struct App {
    pub systemd: bool,
    pub page: Page,
    pub status: Option<Result<Status, String>>,
    pub unit: Option<Result<Unit, String>>,
    pub music: Music,
    pub journal: Journal,
    pub console: Console,
    layers: Vec<OpenLayer>,
    pub page_scroll: u16,
    pub notice: Option<Notice>,
    pub pending: Option<Pending>,
    context: u64,
}

impl App {
    pub fn new(systemd: bool) -> Self {
        Self {
            systemd,
            page: Page::Overview,
            status: None,
            unit: None,
            music: Music::default(),
            journal: Journal::new(),
            console: Console::default(),
            layers: Vec::new(),
            page_scroll: 0,
            notice: None,
            pending: None,
            context: 0,
        }
    }

    pub fn ready(&self, collection: Collection) -> bool {
        self.status
            .as_ref()
            .is_some_and(|status| status.as_ref().is_ok_and(|status| collection.ready(status)))
    }

    pub fn service_allows(&self, action: Action) -> bool {
        self.unit
            .as_ref()
            .is_some_and(|unit| unit.as_ref().is_ok_and(|unit| unit.allows(action)))
    }

    pub fn connect(&mut self, status: Result<Status, String>) {
        for collection in Collection::ALL {
            let before = self.ready(collection);
            let after = status.as_ref().is_ok_and(|status| collection.ready(status));
            if after && !before {
                self.refresh(collection, None);
            }
        }
        self.status = Some(status);
    }

    pub fn refresh_visible(&mut self) {
        match self.page {
            Page::Overview => {}
            Page::Journal => self.journal.refresh = true,
            Page::Music => self.refresh(self.music.page, None),
            Page::Console => self.console.latest(),
        }
    }

    pub fn next_load(&mut self) -> Option<Work> {
        if self.pending.is_some() {
            return None;
        }
        let work = if self.layer().is_some() {
            self.next_layer_load()
        } else if self.page == Page::Music {
            self.next_music_load()
        } else {
            None
        }?;
        let work = self.begin(work)?;
        if let (Some(open), Some(pending)) = (self.layers.last_mut(), self.pending.as_mut()) {
            // Retry the reload when a temporary overlay hides its result.
            open.refresh = true;
            pending.layer_refresh = true;
        }
        Some(work)
    }

    pub fn paste(&mut self, text: &str) {
        if !self.form_submitted()
            && let Some(Layer::Form(form)) = self.layer_mut()
        {
            form.paste(text);
        }
    }

    pub fn key(&mut self, key: KeyEvent, page_rows: usize) -> Option<Work> {
        if key.code == KeyCode::F(5) {
            if let Some(open) = self.layers.last_mut() {
                open.refresh = true;
            } else {
                self.refresh_visible();
            }
            return None;
        }
        if key.code == KeyCode::Esc && self.pop_layer().is_some() {
            return None;
        }
        let modified = key
            .modifiers
            .intersects(KeyModifiers::CONTROL | KeyModifiers::ALT);
        if !modified && !matches!(self.layer(), Some(Layer::Form(_) | Layer::Confirm(_))) {
            match key.code {
                KeyCode::Char('?') | KeyCode::F(1)
                    if !matches!(self.layer(), Some(Layer::Help)) =>
                {
                    self.show(Layer::Help);
                    return None;
                }
                KeyCode::Char('!')
                    if self.notice.is_some() && !matches!(self.layer(), Some(Layer::Notice)) =>
                {
                    self.show(Layer::Notice);
                    return None;
                }
                _ => {}
            }
        }
        if self.layer().is_some() {
            return self.layer_key(key);
        }
        if modified {
            return None;
        }
        match key.code {
            KeyCode::Char('1'..='6') | KeyCode::Tab | KeyCode::BackTab => {
                self.select_page(key.code);
                None
            }
            _ => match self.page {
                Page::Overview => self.overview_key(key.code),
                Page::Journal => self.journal_key(key.code, page_rows),
                Page::Music => self.music_key(key.code),
                Page::Console => self.console_key(key.code, page_rows),
            },
        }
    }

    fn select_page(&mut self, key: KeyCode) {
        self.page = match key {
            KeyCode::Char('1') => Page::Overview,
            KeyCode::Char('2') => Page::Journal,
            KeyCode::Char(number @ '3'..='5') => {
                self.music.page = Collection::ALL[number as usize - '3' as usize];
                Page::Music
            }
            KeyCode::Char('6') => Page::Console,
            KeyCode::Tab => Page::ALL[(self.page.index() + 1) % Page::ALL.len()],
            KeyCode::BackTab => {
                Page::ALL[(self.page.index() + Page::ALL.len() - 1) % Page::ALL.len()]
            }
            _ => return,
        };
        self.page_scroll = 0;
        self.changed_context();
        self.refresh_visible();
    }

    fn overview_key(&mut self, key: KeyCode) -> Option<Work> {
        let action = match key {
            KeyCode::Char('a') => Action::Start,
            KeyCode::Char('x') => Action::Stop,
            KeyCode::Char('r') => Action::Restart,
            _ => {
                self.scroll_panel(key);
                return None;
            }
        };
        if !self.service_allows(action) || self.pending.is_some() {
            return None;
        }
        match action {
            Action::Start => self.begin(Work::Service(action)),
            Action::Stop | Action::Restart => {
                let impact = match action {
                    Action::Stop => {
                        "Paper and web clients lose their backend connection until the service starts again."
                    }
                    _ => "Paper and web clients temporarily lose their backend connection.",
                };
                self.show(Layer::Confirm(Confirmation::new(
                    Work::Service(action),
                    systemd::UNIT.into(),
                    impact,
                )));
                None
            }
        }
    }

    fn journal_key(&mut self, key: KeyCode, page_rows: usize) -> Option<Work> {
        if !self.systemd {
            return None;
        }
        if key == KeyCode::Char('/') {
            self.show(Layer::Form(Form::new(
                Prompt::Filter("Filter journal"),
                &self.journal.view.filter,
            )));
        } else {
            self.journal.key(key, page_rows);
        }
        None
    }

    fn console_key(&mut self, key: KeyCode, page_rows: usize) -> Option<Work> {
        match key {
            KeyCode::Char(':') => {
                if let Some(target) = self.console.target(Lane::Commands) {
                    return self.prompt(Prompt::ConsoleCommand(target), "");
                }
            }
            KeyCode::Char('/') => {
                self.show(Layer::Form(Form::new(
                    Prompt::Filter("Filter console log"),
                    &self.console.view.filter,
                )));
            }
            _ => self.console.key(key, page_rows),
        }
        None
    }

    fn layer_key(&mut self, key: KeyEvent) -> Option<Work> {
        let submitted = self.form_submitted();
        match self.layer_mut()? {
            Layer::Form(form) if key.code == KeyCode::Enter && !submitted => {
                let submit = form.submit()?;
                self.submit(submit)
            }
            Layer::Form(form) if !submitted => {
                form.edit(key);
                None
            }
            Layer::Form(_) => None,
            Layer::Confirm(confirm) => match key.code {
                KeyCode::Left => {
                    confirm.accepted = false;
                    None
                }
                KeyCode::Right => {
                    confirm.accepted = true;
                    None
                }
                KeyCode::Tab | KeyCode::BackTab => {
                    confirm.accepted = !confirm.accepted;
                    None
                }
                KeyCode::Enter => {
                    let Some(Layer::Confirm(confirm)) = self.pop_layer() else {
                        return None;
                    };
                    confirm.accepted.then(|| self.begin(confirm.work)).flatten()
                }
                _ => {
                    self.scroll_panel(key.code);
                    None
                }
            },
            Layer::Help | Layer::Notice => {
                self.scroll_panel(key.code);
                None
            }
            _ => self.music_layer_key(key.code),
        }
    }

    fn submit(&mut self, submit: Submit) -> Option<Work> {
        match submit {
            Submit::Work(work) => self.begin(work),
            Submit::Confirm(confirm) => {
                self.show(Layer::Confirm(confirm));
                None
            }
            Submit::Filter(value) => {
                if self.page == Page::Console {
                    self.console.view.filter(value);
                } else {
                    self.journal.view.filter(value);
                }
                drop(self.pop_layer());
                None
            }
        }
    }

    fn scroll_panel(&mut self, key: KeyCode) {
        let scroll = self
            .layers
            .last_mut()
            .map_or(&mut self.page_scroll, |open| &mut open.scroll);
        *scroll = match key {
            KeyCode::Up => scroll.saturating_sub(1),
            KeyCode::Down => scroll.saturating_add(1),
            KeyCode::PageUp => scroll.saturating_sub(8),
            KeyCode::PageDown => scroll.saturating_add(8),
            KeyCode::Home => 0,
            _ => *scroll,
        };
    }

    fn prompt(&mut self, prompt: Prompt, initial: &str) -> Option<Work> {
        self.show(Layer::Form(Form::new(prompt, initial)));
        None
    }

    fn show(&mut self, layer: Layer) {
        self.layers.push(OpenLayer {
            layer,
            scroll: 0,
            refresh: false,
        });
        self.changed_context();
    }

    pub fn layer(&self) -> Option<&Layer> {
        self.layers.last().map(|open| &open.layer)
    }

    fn layer_mut(&mut self) -> Option<&mut Layer> {
        self.layers.last_mut().map(|open| &mut open.layer)
    }

    fn pop_layer(&mut self) -> Option<Layer> {
        let open = self.layers.pop()?;
        self.changed_context();
        Some(open.layer)
    }

    pub fn layer_scroll(&self) -> u16 {
        self.layers.last().map_or(0, |open| open.scroll)
    }

    fn changed_context(&mut self) {
        self.context = self.context.wrapping_add(1);
    }

    pub fn form_submitted(&self) -> bool {
        self.pending
            .as_ref()
            .is_some_and(|pending| pending.context == self.context)
    }

    fn begin(&mut self, work: Work) -> Option<Work> {
        if let Some(label) = self.pending.as_ref().map(|pending| pending.label) {
            if let Some(Layer::Form(form)) = self.layer_mut() {
                form.error = Some(format!(
                    "{label} is still in progress. Try again once it finishes."
                ));
            }
            return None;
        }
        let available = match &work {
            Work::Change(change) => self.ready(change.collection),
            Work::Service(action) => self.service_allows(*action),
            _ => true,
        };
        if !available {
            self.work_error(
                work.label(),
                "The target is currently unavailable. Refresh its status and try again.",
                true,
                Level::Warning,
            );
            return None;
        }
        if work.modifies() {
            self.notice = None;
        }
        self.pending = Some(Pending {
            label: work.label(),
            modifies: work.modifies(),
            context: self.context,
            layer_refresh: false,
        });
        Some(work)
    }

    pub fn finished(&mut self, result: Finished) {
        let Some(pending) = self.pending.take() else {
            return;
        };
        let same_context = pending.context == self.context;
        if same_context
            && pending.layer_refresh
            && let Some(open) = self.layers.last_mut()
        {
            open.refresh = false;
        }
        match result {
            Finished::Library(keyword, page, tracks) => {
                self.library_loaded(keyword, page, tracks, same_context);
            }
            Finished::Online(query, after, result) => {
                if result.is_ok() {
                    self.music.library.list.refresh = true;
                }
                if same_context {
                    self.online_loaded(query, &after, result, pending.label);
                }
            }
            Finished::Versions(id, title, page, result) if same_context => {
                self.versions_loaded(id, title, page, result, pending.label);
            }
            Finished::Sources(sources) => self.music.sources.load(sources),
            Finished::SourceTracks(id, query, result) if same_context => {
                self.source_tracks_loaded(&id, query, result);
            }
            Finished::Cache(None, query, result) => self.music.cache.load(query, result),
            Finished::Cache(Some(id), query, result) if same_context => {
                self.source_cache_loaded(&id, query, result);
            }
            Finished::Track(result) if same_context => {
                self.detail_finished(result.map(Layer::Track), pending.label);
            }
            Finished::Source(result) if same_context => {
                self.detail_finished(result.map(Layer::Source), pending.label);
            }
            Finished::CacheEntry(result) if same_context => {
                self.detail_finished(result.map(Layer::CacheEntry), pending.label);
            }
            Finished::Lyrics(result) if same_context => {
                self.detail_finished(result.map(Layer::Lyrics), pending.label);
            }
            Finished::Track(_)
            | Finished::Source(_)
            | Finished::CacheEntry(_)
            | Finished::Lyrics(_)
            | Finished::SourceTracks(..)
            | Finished::Cache(Some(_), ..)
            | Finished::Versions(..) => {}
            Finished::ConsoleCommand(id, result) => {
                self.console_finished(&id, result, same_context);
            }
            Finished::Change(collection, result) => {
                self.change_finished(collection, result, pending.label, same_context);
            }
            Finished::Service(action, result) => {
                self.unit = None;
                self.status = None;
                match result {
                    Ok(()) => {
                        let message = match action {
                            Action::Start => "systemd started the service.",
                            Action::Stop => "Service stopped.",
                            Action::Restart => "systemd restarted the service.",
                        };
                        self.notice(Level::Good, message);
                    }
                    Err(error) => {
                        let (level, message) = if error.kind() == io::ErrorKind::TimedOut {
                            (Level::Warning, "result unconfirmed")
                        } else {
                            (Level::Error, "failed")
                        };
                        self.notice(
                            level,
                            format!(
                                "systemd {} {message}: {error}. Check the unit state and logs.",
                                action.verb()
                            ),
                        );
                    }
                }
            }
        }
    }

    fn change_finished(
        &mut self,
        collection: Collection,
        result: Result<super::jobs::Report, ChangeError>,
        label: &str,
        same_context: bool,
    ) {
        match result {
            Ok(report) => {
                self.refresh(collection, report.selection);
                self.apply_music_change(collection, &report.effect, same_context);
                self.notice(
                    if report.partial {
                        Level::Warning
                    } else {
                        Level::Good
                    },
                    report.message,
                );
            }
            Err(ChangeError::Failed(error)) => {
                if matches!(collection, Collection::Cache) {
                    self.refresh(collection, None);
                    self.apply_music_change(collection, &Effect::Changed, false);
                }
                self.work_error(label, &error, same_context, Level::Error);
            }
            Err(ChangeError::Unconfirmed(error)) => {
                self.refresh(collection, None);
                self.apply_music_change(collection, &Effect::Changed, false);
                self.work_error(
                    label,
                    &format!("Result unconfirmed. Check before retrying. {error}"),
                    same_context,
                    Level::Warning,
                );
            }
        }
    }

    fn console_finished(
        &mut self,
        id: &str,
        result: Result<CommandOutcome, RunError>,
        same_context: bool,
    ) {
        let label = format!("Console {id}");
        match result {
            Ok(CommandOutcome::Submitted) => {
                if same_context {
                    self.layers.clear();
                    self.changed_context();
                }
                self.notice(
                    Level::Good,
                    format!("{label} · {}", CommandOutcome::Submitted.description()),
                );
            }
            Ok(outcome) => self.work_error(
                &label,
                outcome.description(),
                same_context,
                if outcome == CommandOutcome::Unconfirmed {
                    Level::Warning
                } else {
                    Level::Error
                },
            ),
            Err(RunError::Failed(error)) => {
                self.work_error(&label, &error.to_string(), same_context, Level::Error);
            }
            Err(RunError::Unconfirmed(error)) => self.work_error(
                &label,
                &format!("{} {error}", CommandOutcome::Unconfirmed.description()),
                same_context,
                Level::Warning,
            ),
        }
    }

    fn open_detail(&mut self, layer: Layer) {
        let reloading = self.layer().is_some_and(|current| {
            std::mem::discriminant(current) == std::mem::discriminant(&layer)
        });
        if !reloading
            && self
                .notice
                .as_ref()
                .is_some_and(|notice| matches!(notice.level, Level::Error))
        {
            self.notice = None;
        }
        if let Some(open) = self.layers.last_mut()
            && (matches!(open.layer, Layer::Form(_)) || reloading)
        {
            open.layer = layer;
            self.changed_context();
        } else {
            self.show(layer);
        }
    }

    fn detail_finished(&mut self, result: Result<Layer, String>, label: &str) {
        match result {
            Ok(layer) => self.open_detail(layer),
            Err(error) => self.work_error(label, &error, true, Level::Error),
        }
    }

    fn refresh(&mut self, collection: Collection, selection: Option<String>) {
        match collection {
            Collection::Library => {
                self.music.library.list.refresh = true;
                if selection.is_some() {
                    self.music.library.list.select_on_load = selection;
                }
            }
            Collection::Sources => {
                self.music.sources.refresh = true;
                if selection.is_some() {
                    self.music.sources.select_on_load = selection;
                }
            }
            Collection::Cache => {
                self.music.cache.list.refresh = true;
                if selection.is_some() {
                    self.music.cache.list.select_on_load = selection;
                }
            }
        }
    }

    fn notice(&mut self, level: Level, text: impl Into<String>) {
        self.notice = Some(Notice {
            level,
            text: text.into(),
        });
    }

    fn work_error(&mut self, label: &str, error: &str, same_context: bool, level: Level) {
        if same_context && let Some(Layer::Form(form)) = self.layer_mut() {
            form.error = Some(error.to_owned());
        }
        self.notice(level, format!("{label}: {error}"));
    }
}

fn move_selection(key: KeyCode, selected: &mut usize, len: usize) -> bool {
    let last = len.saturating_sub(1);
    *selected = match key {
        KeyCode::Up => selected.saturating_sub(1),
        KeyCode::Down => (*selected + 1).min(last),
        KeyCode::PageUp => selected.saturating_sub(8),
        KeyCode::PageDown => (*selected + 8).min(last),
        KeyCode::Home => 0,
        KeyCode::End => last,
        _ => return false,
    };
    true
}

#[cfg(test)]
mod tests {
    use super::{
        App, ChangeError, Collection, Effect, Finished, Layer, SourceDetail, Status, Work,
    };
    use crate::{
        admin::tui::jobs::Report,
        domains::music::{CacheEntry, CachePage, CacheState, CacheUsage},
        transport::admin::Command,
    };
    use crossterm::event::{KeyCode, KeyEvent, KeyModifiers};
    use std::collections::BTreeMap;
    use std::path::Path;

    fn library() -> App {
        let mut app = App::new(false);
        app.connect(Ok(Status {
            ready: Vec::new(),
            problems: BTreeMap::new(),
            web_api_port: 0,
            web_chat_port: 0,
            music_catalog: true,
            music_source_registry: false,
            music_media_cache: false,
            paper_music: false,
        }));
        assert!(key(&mut app, KeyCode::Char('3')).is_none());
        app
    }

    fn key(app: &mut App, code: KeyCode) -> Option<Work> {
        app.key(KeyEvent::new(code, KeyModifiers::NONE), 16)
    }

    #[test]
    fn unconfirmed_import_keeps_the_draft_and_reloads_without_resubmitting() {
        let mut app = library();
        assert!(key(&mut app, KeyCode::Char('a')).is_none());
        let path = "/music/曲目.flac";
        app.paste(path);
        match key(&mut app, KeyCode::Enter) {
            Some(Work::Change(change)) => {
                assert!(
                    matches!(change.command, Command::ImportLocal(file) if file == Path::new(path))
                );
            }
            _ => panic!("the form must issue a single import"),
        }
        assert!(key(&mut app, KeyCode::Enter).is_none());
        app.finished(Finished::Change(
            Collection::Library,
            Err(ChangeError::Unconfirmed("connection closed".into())),
        ));
        let Some(Layer::Form(form)) = app.layer() else {
            panic!("the form must stay open");
        };
        assert_eq!(form.fields[0].value, path);
        assert!(
            form.error
                .as_ref()
                .is_some_and(|error| error.contains("unconfirmed"))
        );
        assert!(app.next_load().is_none());
        assert!(key(&mut app, KeyCode::Esc).is_none());
        assert!(matches!(app.next_load(), Some(Work::Library { .. })));
    }

    #[test]
    fn clearing_lyrics_defaults_to_cancel_then_uses_the_entered_version() {
        let mut app = library();
        let version = "e587d10b0c734da0bb85f3b12efdc584";
        assert!(key(&mut app, KeyCode::Char('c')).is_none());
        app.paste(version);
        assert!(key(&mut app, KeyCode::Enter).is_none());
        assert!(matches!(app.layer(), Some(Layer::Confirm(_))));
        assert!(key(&mut app, KeyCode::Enter).is_none());
        assert!(matches!(app.layer(), Some(Layer::Form(_))));
        assert!(key(&mut app, KeyCode::Enter).is_none());
        assert!(key(&mut app, KeyCode::Right).is_none());
        let Some(Work::Change(change)) = key(&mut app, KeyCode::Enter) else {
            panic!("confirmation must issue one clear request");
        };
        assert!(matches!(change.command, Command::ClearLyrics(target) if target == version));
    }

    #[test]
    fn source_lookup_failure_stays_with_the_entered_id() {
        let mut app = library();
        let Some(Ok(status)) = &mut app.status else {
            panic!("management must be connected");
        };
        status.music_source_registry = true;
        assert!(key(&mut app, KeyCode::Char('4')).is_none());
        assert!(key(&mut app, KeyCode::Char('f')).is_none());
        app.paste("remote-missing");
        assert!(
            matches!(key(&mut app, KeyCode::Enter), Some(Work::Source(id)) if id == "remote-missing")
        );
        app.finished(Finished::Source(Err("unknown LX source ID".into())));
        let Some(Layer::Form(form)) = app.layer() else {
            panic!("the form must stay open");
        };
        assert_eq!(form.fields[0].value, "remote-missing");
        assert_eq!(form.error.as_deref(), Some("unknown LX source ID"));
    }

    #[test]
    fn evicting_from_source_downloads_returns_to_the_same_filtered_list() {
        let mut app = library();
        app.status
            .as_mut()
            .unwrap()
            .as_mut()
            .unwrap()
            .music_media_cache = true;
        let id = "local:fallback.js";
        let version = "e587d10b0c734da0bb85f3b12efdc584";
        app.show(Layer::Source(SourceDetail::Missing(id.into())));
        assert!(key(&mut app, KeyCode::Char('b')).is_none());
        let Some(Work::Cache { source, page }) = app.next_load() else {
            panic!("downloads should open a filtered cache page");
        };
        assert_eq!(source.as_deref(), Some(id));
        assert!(key(&mut app, KeyCode::Char('?')).is_none());
        app.finished(Finished::Cache(source, page, Err("hidden".into())));
        assert!(key(&mut app, KeyCode::Esc).is_none());

        let Some(Work::Cache { source, page }) = app.next_load() else {
            panic!("returning from help should finish loading the downloads");
        };
        let entry = || CacheEntry {
            version: version.into(),
            track: None,
            channel: "demo".into(),
            source: None,
            state: CacheState::Idle,
            bytes: 8,
            content_type: None,
            last_used_unix: 0,
        };
        app.finished(Finished::Cache(
            source,
            page,
            Ok(CachePage {
                entries: vec![entry()],
                next: None,
                usage: CacheUsage {
                    entries: 1,
                    charged_bytes: 8,
                    limit_bytes: 64,
                    retired_bytes: 0,
                },
            }),
        ));
        assert!(
            matches!(key(&mut app, KeyCode::Enter), Some(Work::CacheEntry(id)) if id == version)
        );
        app.finished(Finished::CacheEntry(Ok(Box::new(entry()))));
        assert!(key(&mut app, KeyCode::Char('x')).is_none());
        assert!(key(&mut app, KeyCode::Right).is_none());
        assert!(
            matches!(key(&mut app, KeyCode::Enter), Some(Work::Change(change))
            if matches!(change.command, Command::EvictCache(ref target) if target == version))
        );
        app.finished(Finished::Change(
            Collection::Cache,
            Ok(Report {
                message: "Evicted".into(),
                partial: false,
                selection: None,
                effect: Effect::CacheEvicted(version.into()),
            }),
        ));
        assert!(matches!(app.layer(), Some(Layer::SourceCache(cache)) if cache.source == id));
        assert!(
            matches!(app.next_load(), Some(Work::Cache { source: Some(source), .. }) if source == id)
        );
        assert!(key(&mut app, KeyCode::Esc).is_none());
        assert!(matches!(app.layer(), Some(Layer::Source(source)) if source.id() == id));
    }
}
