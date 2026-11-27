mod music;

pub(super) use music::{cache_entry_actions, track_actions};

use super::{
    catalog::{BySource, Collection, Music, Online, SourceDetail, Versions},
    console::Console,
    form::{Confirmation, Form, Prompt, Submit},
    jobs::{ChangeError, Effect, Finished, Work},
    journal::Journal,
    menu::{Choice, Item, Menu},
    mouse::{Control, Input},
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
use std::{
    io,
    time::{Duration, Instant},
};

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
        match self {
            Self::Overview => 0,
            Self::Journal => 1,
            Self::Music => 2,
            Self::Console => 3,
        }
    }

    pub fn name(self) -> &'static str {
        match self {
            Self::Overview => "Overview",
            Self::Journal => "Logs",
            Self::Music => "Music",
            Self::Console => "Console",
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
    Menu(Menu),
    Form(Form),
    Confirm(Confirmation),
    Notice,
}

impl Layer {
    pub(super) fn is_modal(&self) -> bool {
        matches!(self, Self::Form(_) | Self::Confirm(_) | Self::Menu(_))
    }
}

struct OpenLayer {
    layer: Layer,
    scroll: u16,
    refresh: bool,
}

const SERVICE_ACTIONS: [(char, &str, Action); 3] = [
    ('a', "Start service", Action::Start),
    ('r', "Restart service", Action::Restart),
    ('x', "Stop service", Action::Stop),
];

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
    pub last_status: Option<(Status, Instant)>,
    pub status_problem: Option<String>,
    pub unit: Option<Result<Unit, String>>,
    pub music: Music,
    pub journal: Journal,
    pub console: Console,
    layers: Vec<OpenLayer>,
    pub page_scroll: u16,
    pub notice: Option<Notice>,
    pub pending: Option<Pending>,
    context: u64,
    last_row_click: Option<(usize, Instant)>,
}

impl App {
    pub fn new(systemd: bool) -> Self {
        Self {
            systemd,
            page: Page::Overview,
            last_status: None,
            status_problem: None,
            unit: None,
            music: Music::default(),
            journal: Journal::new(),
            console: Console::default(),
            layers: Vec::new(),
            page_scroll: 0,
            notice: None,
            pending: None,
            context: 0,
            last_row_click: None,
        }
    }

    pub fn ready(&self, collection: Collection) -> bool {
        self.status_problem.is_none()
            && self
                .last_status
                .as_ref()
                .is_some_and(|(status, _)| collection.ready(status))
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
        match status {
            Ok(status) => {
                self.last_status = Some((status, Instant::now()));
                self.status_problem = None;
            }
            Err(problem) => self.status_problem = Some(problem),
        }
    }

    pub fn refresh_visible(&mut self) {
        match self.page {
            Page::Overview => {}
            Page::Journal => self.journal.refresh = true,
            Page::Music => self.refresh(self.music.current, None),
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
            self.next_music_load(self.music.current)
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
        self.last_row_click = None;
        if key.code == KeyCode::F(5) {
            if let Some(open) = self.layers.last_mut() {
                if !open.layer.is_modal() {
                    open.refresh = true;
                }
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
        if !modified && self.can_navigate() {
            match key.code {
                KeyCode::Char('1'..='4') | KeyCode::Tab | KeyCode::BackTab => {
                    self.select_page(key.code);
                    return None;
                }
                KeyCode::Char('[' | ']') if self.page == Page::Music => {
                    let current = self.music.current.index();
                    let count = Collection::ALL.len();
                    let step = if key.code == KeyCode::Char(']') {
                        1
                    } else {
                        count - 1
                    };
                    self.open_collection(Collection::ALL[(current + step) % count]);
                    return None;
                }
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
        match self.page {
            Page::Overview => self.overview_key(key.code),
            Page::Journal => self.journal_key(key.code, page_rows),
            Page::Music => self.music_key(self.music.current, key.code),
            Page::Console => self.console_key(key.code, page_rows),
        }
    }

    pub(super) fn can_navigate(&self) -> bool {
        !self.layer().is_some_and(Layer::is_modal)
    }

    fn select_page(&mut self, key: KeyCode) {
        let page = match key {
            KeyCode::Char(number @ '1'..='4') => Page::ALL[number as usize - '1' as usize],
            KeyCode::Tab => Page::ALL[(self.page.index() + 1) % Page::ALL.len()],
            KeyCode::BackTab => {
                Page::ALL[(self.page.index() + Page::ALL.len() - 1) % Page::ALL.len()]
            }
            _ => return,
        };
        self.open_page(page);
    }

    fn open_page(&mut self, page: Page) {
        if self.page == page {
            return;
        }
        self.page = page;
        self.page_scroll = 0;
        self.layers.clear();
        self.changed_context();
        if page == Page::Music {
            self.refresh(self.music.current, None);
        }
    }

    fn open_collection(&mut self, collection: Collection) {
        if self.page == Page::Music && self.music.current != collection {
            self.music.current = collection;
            self.layers.clear();
            self.changed_context();
            self.refresh(collection, None);
        }
    }

    pub fn mouse(&mut self, input: Input, page_rows: usize) -> Option<Work> {
        let last_row_click = self.last_row_click.take();
        let key = match input {
            Input::Scroll { key, logs: true } => {
                // Inline editors keep focus while the pointer scrolls the log behind them.
                match self.page {
                    Page::Journal => self.journal.key(key, page_rows),
                    Page::Console => self.console.key(key, page_rows),
                    _ => {}
                }
                return None;
            }
            Input::Scroll { key, logs: false } | Input::Click(Control::Key(key)) => key,
            Input::Click(Control::Page(page)) => {
                if self.can_navigate() {
                    self.open_page(page);
                }
                return None;
            }
            Input::Click(Control::Collection(collection)) => {
                if self.can_navigate() {
                    self.open_collection(collection);
                }
                return None;
            }
            Input::Click(Control::Row(index)) => {
                if !self.select_music_row(index) {
                    return None;
                }
                let now = Instant::now();
                if !last_row_click.is_some_and(|(row, clicked)| {
                    row == index && now.duration_since(clicked) <= Duration::from_millis(400)
                }) {
                    self.last_row_click = Some((index, now));
                    return None;
                }
                KeyCode::Enter
            }
            Input::Click(Control::Menu(index)) => return self.choose_menu_item(index),
            Input::Click(Control::Field(field)) => {
                self.focus_field(field, None);
                return None;
            }
            Input::Click(Control::Input { field, offset }) => {
                self.focus_field(field, Some(offset));
                return None;
            }
            Input::Click(Control::Confirm(accepted)) => {
                let Some(Layer::Confirm(confirm)) = self.layer_mut() else {
                    return None;
                };
                confirm.accepted = accepted;
                KeyCode::Enter
            }
        };
        self.key(KeyEvent::new(key, KeyModifiers::NONE), page_rows)
    }

    fn focus_field(&mut self, index: usize, column: Option<usize>) {
        if !self.form_submitted()
            && let Some(Layer::Form(form)) = self.layer_mut()
            && let Some(field) = form.fields.get_mut(index)
        {
            form.selected = index;
            if let Some(column) = column {
                field.click(column);
            }
        }
    }

    fn overview_key(&mut self, key: KeyCode) -> Option<Work> {
        if !matches!(key, KeyCode::Char('m' | 'a' | 'r' | 'x')) {
            self.scroll_panel(key);
            return None;
        }
        let items = SERVICE_ACTIONS
            .into_iter()
            .filter(|(_, _, action)| self.service_allows(*action))
            .map(|(shortcut, label, action)| {
                let work = Work::Service(action);
                match action {
                    Action::Start => Item::run(label, work),
                    Action::Stop | Action::Restart => Item::confirm(
                        label,
                        Confirmation::new(
                            work,
                            systemd::UNIT.into(),
                            if matches!(action, Action::Stop) {
                                "Paper and web clients lose their backend connection until the service starts again."
                            } else {
                                "Paper and web clients temporarily lose their backend connection."
                            },
                        ),
                    ),
                }
                .key(shortcut)
            })
            .collect();
        self.menu_key(key, "Service actions", Some(systemd::UNIT.into()), items)
    }

    pub(super) fn overview_has_actions(&self) -> bool {
        self.pending.is_none()
            && SERVICE_ACTIONS
                .iter()
                .any(|(_, _, action)| self.service_allows(*action))
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
            Layer::Menu(_)
                if !key
                    .modifiers
                    .intersects(KeyModifiers::CONTROL | KeyModifiers::ALT) =>
            {
                self.open_menu_key(key.code)
            }
            Layer::Form(_) | Layer::Menu(_) => None,
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
        let scroll = self.scroll_mut();
        *scroll = match key {
            KeyCode::Up => scroll.saturating_sub(1),
            KeyCode::Down => scroll.saturating_add(1),
            KeyCode::PageUp => scroll.saturating_sub(8),
            KeyCode::PageDown => scroll.saturating_add(8),
            KeyCode::Home => 0,
            _ => *scroll,
        };
    }

    pub fn sync_scroll(&mut self, limit: u16, list_offset: Option<u16>) {
        let scroll = self.scroll_mut();
        *scroll = list_offset.unwrap_or_else(|| (*scroll).min(limit));
    }

    fn scroll_mut(&mut self) -> &mut u16 {
        if let Some(open) = self.layers.last_mut() {
            &mut open.scroll
        } else if self.page == Page::Music {
            self.music.scroll_mut()
        } else {
            &mut self.page_scroll
        }
    }

    fn prompt(&mut self, prompt: Prompt, initial: &str) -> Option<Work> {
        self.show(Layer::Form(Form::new(prompt, initial)));
        None
    }

    fn menu_key(
        &mut self,
        key: KeyCode,
        title: &'static str,
        target: Option<String>,
        items: Vec<Item>,
    ) -> Option<Work> {
        if self.pending.is_some() || items.is_empty() {
            return None;
        }
        if key == KeyCode::Char('m') {
            self.show(Layer::Menu(Menu {
                title,
                target,
                items,
                selected: 0,
            }));
            return None;
        }
        let KeyCode::Char(shortcut) = key else {
            return None;
        };
        let item = items
            .into_iter()
            .find(|item| item.shortcut == Some(shortcut))?;
        self.choose(item.action)
    }

    fn open_menu_key(&mut self, key: KeyCode) -> Option<Work> {
        let Some(Layer::Menu(menu)) = self.layer_mut() else {
            return None;
        };
        if move_selection(key, &mut menu.selected, menu.items.len()) {
            return None;
        }
        let index = match key {
            KeyCode::Enter => menu.selected,
            KeyCode::Char(shortcut) => menu
                .items
                .iter()
                .position(|item| item.shortcut == Some(shortcut))?,
            _ => return None,
        };
        self.choose_menu_item(index)
    }

    fn choose_menu_item(&mut self, index: usize) -> Option<Work> {
        let Some(Layer::Menu(menu)) = self.layer() else {
            return None;
        };
        menu.items.get(index)?;
        let Layer::Menu(menu) = self.pop_layer()? else {
            return None;
        };
        self.choose(menu.items.into_iter().nth(index)?.action)
    }

    fn choose(&mut self, action: Choice) -> Option<Work> {
        match action {
            Choice::Run(work) => self.begin(work),
            Choice::Form(prompt, initial) => self.prompt(prompt, &initial),
            Choice::Confirm(confirm) => {
                self.show(Layer::Confirm(confirm));
                None
            }
        }
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

    pub(super) fn background_layer(&self) -> Option<(&Layer, u16)> {
        self.layers
            .iter()
            .rev()
            .skip(1)
            .find(|open| !open.layer.is_modal())
            .map(|open| (&open.layer, open.scroll))
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
        self.last_row_click = None;
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
            Work::ConsoleCommand { target, .. } => {
                self.console.target(Lane::Commands).is_some_and(|current| {
                    current.id == target.id && current.session == target.session
                })
            }
            _ => true,
        };
        if !available {
            self.work_error(
                work.label(),
                if matches!(work, Work::ConsoleCommand { .. }) {
                    "The console session changed or is offline. Press Esc, then : to select the current session."
                } else {
                    "The target is currently unavailable. Refresh its status and try again."
                },
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
        self.last_row_click = None;
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
            Finished::SourcePlaybackTested(result) => {
                self.source_test_finished(result, pending.label, same_context);
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
                self.last_status = None;
                self.status_problem = None;
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
                self.notice(Level::Good, format!("Command submitted to {id}."));
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
        App, ChangeError, Collection, Control, Effect, Finished, Input, Layer, Page, SourceDetail,
        Status, Work,
    };
    use crate::{
        admin::tui::jobs::Report,
        domains::music::{CacheEntry, CachePage, CacheState, CacheUsage, SourceState},
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
            minecraft: None,
        }));
        assert!(key(&mut app, KeyCode::Char('3')).is_none());
        app
    }

    fn key(app: &mut App, code: KeyCode) -> Option<Work> {
        app.key(KeyEvent::new(code, KeyModifiers::NONE), 16)
    }

    #[test]
    fn switching_pages_keeps_the_paused_console_in_place() {
        let mut app = library();
        assert!(key(&mut app, KeyCode::Tab).is_none());
        assert!(app.page == Page::Console);
        assert!(key(&mut app, KeyCode::Char(' ')).is_none());
        assert!(!app.console.view.following);
        assert!(key(&mut app, KeyCode::Tab).is_none());
        assert!(key(&mut app, KeyCode::BackTab).is_none());
        assert_eq!(app.page.index(), Page::Console.index());
        assert!(!app.console.view.following);
        assert!(key(&mut app, KeyCode::F(5)).is_none());
        assert!(app.console.view.following);
    }

    #[test]
    fn a_failed_management_poll_keeps_the_snapshot_but_disables_music_actions() {
        let mut app = library();
        assert!(app.ready(Collection::Library));
        app.connect(Err("Connection refused".into()));
        assert!(!app.ready(Collection::Library));
        assert!(
            app.last_status
                .as_ref()
                .is_some_and(|(status, _)| status.music_catalog)
        );
        assert!(key(&mut app, KeyCode::Char('a')).is_none());
        assert!(app.layer().is_none());
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
        let Some((status, _)) = &mut app.last_status else {
            panic!("management must be connected");
        };
        status.music_source_registry = true;
        assert!(key(&mut app, KeyCode::Char(']')).is_none());
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
    fn source_playback_test_keeps_the_track_id_for_retry() {
        let mut app = library();
        let Some((status, _)) = &mut app.last_status else {
            panic!("management must be connected");
        };
        status.music_source_registry = true;
        status.music_media_cache = true;
        assert!(key(&mut app, KeyCode::Char(']')).is_none());
        app.music.sources.load(Ok(vec![SourceState {
            id: "local:demo.js".into(),
            name: "Demo".into(),
            available: true,
            last_error: String::new(),
            channels: vec!["demo".into()],
            actions: BTreeMap::from([("demo".into(), vec!["musicUrl".into()])]),
            subscription: false,
            enabled: true,
            origin: None,
        }]));
        assert!(key(&mut app, KeyCode::Char('m')).is_none());
        assert!(app.mouse(Input::Click(Control::Menu(0)), 16).is_none());
        app.paste("my-track");
        assert!(matches!(
            key(&mut app, KeyCode::Enter),
            Some(Work::TestSourcePlayback { source_id, track_id })
                if source_id == "local:demo.js" && track_id == "my-track"
        ));
        app.finished(Finished::SourcePlaybackTested(Err(
            "audio request failed".into()
        )));
        let Some(Layer::Form(form)) = app.layer() else {
            panic!("the form must stay open");
        };
        assert_eq!(form.fields[0].value, "my-track");
        assert_eq!(form.error.as_deref(), Some("audio request failed"));
        assert!(matches!(
            key(&mut app, KeyCode::Enter),
            Some(Work::TestSourcePlayback { .. })
        ));
        app.finished(Finished::SourcePlaybackTested(Ok(())));
        assert!(app.layer().is_none());
    }

    #[test]
    fn evicting_from_source_downloads_returns_to_the_same_filtered_list() {
        let mut app = library();
        app.last_status.as_mut().unwrap().0.music_media_cache = true;
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
