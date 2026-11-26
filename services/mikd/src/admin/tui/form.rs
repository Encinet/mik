use super::{
    catalog::{Collection, PageQuery},
    jobs::{Change, Work},
};
use crate::{console::Target, domains::music::CatalogQuery, transport::admin::Command};
use crossterm::event::{KeyCode, KeyEvent, KeyModifiers};
use std::path::{self, PathBuf};

const MAX_INPUT_BYTES: usize = 8192;

pub(super) enum Prompt {
    FindLibrary,
    Discover,
    PreviewCollection,
    Track,
    Source,
    CacheVersion,
    Import,
    Replace { id: String, title: String },
    Lyrics,
    SetLyrics,
    ClearLyrics,
    Subscribe,
    Filter(&'static str),
    ConsoleCommand(Target),
}

pub(super) struct Form {
    kind: Prompt,
    pub fields: Vec<Field>,
    pub selected: usize,
    pub error: Option<String>,
}

pub(super) struct Field {
    pub label: &'static str,
    pub value: String,
    pub cursor: usize,
}

pub(super) struct Confirmation {
    pub work: Work,
    pub target: String,
    pub impact: &'static str,
    pub accepted: bool,
}

pub(super) enum Submit {
    Work(Work),
    Confirm(Confirmation),
    Filter(String),
}

impl Confirmation {
    pub fn new(work: Work, target: String, impact: &'static str) -> Self {
        Self {
            work,
            target,
            impact,
            accepted: false,
        }
    }
}

impl Form {
    pub fn new(kind: Prompt, initial: &str) -> Self {
        let label = match kind {
            Prompt::FindLibrary | Prompt::Discover => "Search text",
            Prompt::PreviewCollection => "Collection link",
            Prompt::Track => "Track ID",
            Prompt::Source => "Source ID",
            Prompt::Import | Prompt::Replace { .. } => "Server file path",
            Prompt::CacheVersion | Prompt::Lyrics | Prompt::SetLyrics | Prompt::ClearLyrics => {
                "Media version"
            }
            Prompt::Subscribe => "Subscription URL",
            Prompt::Filter(_) => "Text to match",
            Prompt::ConsoleCommand(_) => "Console command",
        };
        let mut fields = vec![Field::new(label, initial)];
        if matches!(kind, Prompt::SetLyrics) {
            fields.push(Field::new("Server file path", ""));
        }
        Self {
            kind,
            fields,
            selected: 0,
            error: None,
        }
    }

    pub fn title(&self) -> &'static str {
        match self.kind {
            Prompt::FindLibrary => "Search registered tracks",
            Prompt::Discover => "Search online catalog",
            Prompt::PreviewCollection => "Preview external collection",
            Prompt::Track => "Inspect track",
            Prompt::Source => "Inspect source",
            Prompt::CacheVersion => "Inspect cached media",
            Prompt::Import => "Import track",
            Prompt::Replace { .. } => "Replace track media",
            Prompt::Lyrics => "Inspect lyrics",
            Prompt::SetLyrics => "Set lyrics",
            Prompt::ClearLyrics => "Clear lyrics",
            Prompt::Subscribe => "Import subscription",
            Prompt::Filter(title) => title,
            Prompt::ConsoleCommand(_) => "Send command",
        }
    }

    pub fn hint(&self) -> &'static str {
        match self.kind {
            Prompt::FindLibrary => {
                "Search titles, artists or albums. Leave blank to show all registered tracks."
            }
            Prompt::Discover => {
                "Search the configured music sources. Displayed results are registered in the catalog."
            }
            Prompt::PreviewCollection => {
                "Paste a playlist link supported by a configured music source."
            }
            Prompt::Track => "Use the track ID reported by Paper or the journal.",
            Prompt::Source => "Use the source ID reported by the journal.",
            Prompt::CacheVersion => "Use the media version shown in the journal or track details.",
            Prompt::Import | Prompt::Replace { .. } => {
                "Use a file readable by mikd, such as /var/lib/mikd/music/inbox/song.flac."
            }
            Prompt::Lyrics => "Use the media version shown in track or cache details.",
            Prompt::SetLyrics => "Enter a media version and a readable .json, .lrc or .txt file.",
            Prompt::ClearLyrics => "Clears the override for this media version.",
            Prompt::Subscribe => "Use an HTTPS URL or an allowed local file URL.",
            Prompt::Filter(_) => "An empty value shows all entries on the page.",
            Prompt::ConsoleCommand(_) => "Enter a command as you would at the server console.",
        }
    }

    pub fn target(&self) -> Option<String> {
        match &self.kind {
            Prompt::ConsoleCommand(target) => Some(target.id.to_string()),
            _ => None,
        }
    }

    pub fn edit(&mut self, key: KeyEvent) {
        let last = self.fields.len() - 1;
        match key.code {
            KeyCode::Tab | KeyCode::Down => {
                self.selected = if self.selected == last {
                    0
                } else {
                    self.selected + 1
                };
            }
            KeyCode::BackTab | KeyCode::Up => {
                self.selected = if self.selected == 0 {
                    last
                } else {
                    self.selected - 1
                };
            }
            _ => {
                self.error = self.fields[self.selected]
                    .edit(key)
                    .err()
                    .map(str::to_owned);
            }
        }
    }

    pub fn paste(&mut self, value: &str) {
        self.error = self.fields[self.selected]
            .insert(value)
            .err()
            .map(str::to_owned);
    }

    pub fn submit(&mut self) -> Option<Submit> {
        match self.prepare() {
            Ok(submit) => Some(submit),
            Err(error) => {
                self.error = Some(error);
                None
            }
        }
    }

    fn prepare(&self) -> Result<Submit, String> {
        let value = &self.fields[0].value;
        let change = |command, collection| {
            Work::Change(Change {
                command,
                collection,
                label: self.title(),
            })
        };
        let library = |command| change(command, Collection::Library);
        let submit = match &self.kind {
            Prompt::FindLibrary => Submit::Work(Work::Library {
                keyword: value.trim().to_owned(),
                page: PageQuery::first(),
            }),
            Prompt::Discover => Submit::Work(Work::Online {
                query: CatalogQuery::Search(required(value, "search term")?),
                after: String::new(),
            }),
            Prompt::PreviewCollection => Submit::Work(Work::Online {
                query: CatalogQuery::Collection(required(value, "collection link")?),
                after: String::new(),
            }),
            Prompt::Track => Submit::Work(Work::Track(required(value, "track ID")?)),
            Prompt::Source => Submit::Work(Work::Source(required(value, "source ID")?)),
            Prompt::CacheVersion => {
                Submit::Work(Work::CacheEntry(required(value, "media version")?))
            }
            Prompt::Import => Submit::Work(library(Command::ImportLocal(file(value)?))),
            Prompt::Replace { id, title } => {
                let path = file(value)?;
                let target = format!("{id}\n{title}\n{}", path.display());
                let work = library(Command::ReplaceLocal {
                    id: id.clone(),
                    path,
                });
                Submit::Confirm(Confirmation::new(
                    work,
                    target,
                    "Publishes a new media version under this track ID. Open playback continues on its current version.",
                ))
            }
            Prompt::Lyrics => Submit::Work(Work::Lyrics(required(value, "media version")?)),
            Prompt::SetLyrics => {
                let version = required(value, "media version")?;
                let path = file(&self.fields[1].value)?;
                Submit::Work(library(Command::SetLyrics { version, path }))
            }
            Prompt::ClearLyrics => {
                let version = required(value, "media version")?;
                let work = library(Command::ClearLyrics(version.clone()));
                Submit::Confirm(Confirmation::new(
                    work,
                    version,
                    "Removes the administrator override. Playback can use embedded or provider lyrics.",
                ))
            }
            Prompt::Subscribe => Submit::Work(change(
                Command::ImportSource(required(value, "subscription URL")?),
                Collection::Sources,
            )),
            Prompt::Filter(_) => Submit::Filter(value.trim().to_owned()),
            Prompt::ConsoleCommand(target) => Submit::Work(Work::ConsoleCommand {
                target: target.clone(),
                line: required(value, "console command")?,
            }),
        };
        Ok(submit)
    }
}

impl Field {
    fn new(label: &'static str, value: &str) -> Self {
        Self {
            label,
            value: value.to_owned(),
            cursor: value.len(),
        }
    }

    fn edit(&mut self, key: KeyEvent) -> Result<(), &'static str> {
        let previous = || {
            self.value[..self.cursor]
                .char_indices()
                .next_back()
                .map(|(at, _)| at)
        };
        let next = || {
            self.value[self.cursor..]
                .chars()
                .next()
                .map(|ch| self.cursor + ch.len_utf8())
        };
        match (key.code, key.modifiers) {
            (KeyCode::Char('u'), KeyModifiers::CONTROL) => {
                drop(self.value.drain(..self.cursor));
                self.cursor = 0;
            }
            (KeyCode::Char(ch), KeyModifiers::NONE | KeyModifiers::SHIFT) => {
                self.insert(ch.encode_utf8(&mut [0; 4]))?;
            }
            (KeyCode::Left, _) => self.cursor = previous().unwrap_or(self.cursor),
            (KeyCode::Right, _) => self.cursor = next().unwrap_or(self.cursor),
            (KeyCode::Home, _) => self.cursor = 0,
            (KeyCode::End, _) => self.cursor = self.value.len(),
            (KeyCode::Backspace, _) => {
                if let Some(at) = previous() {
                    self.value.replace_range(at..self.cursor, "");
                    self.cursor = at;
                }
            }
            (KeyCode::Delete, _) => {
                if let Some(at) = next() {
                    self.value.replace_range(self.cursor..at, "");
                }
            }
            _ => {}
        }
        Ok(())
    }

    fn insert(&mut self, value: &str) -> Result<(), &'static str> {
        if value.len() > MAX_INPUT_BYTES.saturating_sub(self.value.len()) {
            return Err("Input is too long.");
        }
        if value.chars().any(char::is_control) {
            return Err("Enter one line of text.");
        }
        self.value.insert_str(self.cursor, value);
        self.cursor += value.len();
        Ok(())
    }
}

fn required(value: &str, label: &str) -> Result<String, String> {
    let value = value.trim();
    if value.is_empty() {
        Err(format!("Enter a {label}."))
    } else {
        Ok(value.to_owned())
    }
}

fn file(value: &str) -> Result<PathBuf, String> {
    if value.is_empty() {
        return Err("Enter the server file path.".into());
    }
    path::absolute(value).map_err(|error| error.to_string())
}
