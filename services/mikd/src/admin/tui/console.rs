use super::log_view::LogView;
use crate::console::{Capability, ConsoleId, Lane, LogPage, LogPosition, ProviderStatus, Target};
use crossterm::event::KeyCode;
use std::io;

#[derive(Default)]
pub(super) struct Console {
    pub providers: Vec<ProviderStatus>,
    pub list_error: Option<String>,
    pub log_error: Option<String>,
    pub output: Option<LogPage>,
    pub view: LogView,
    selected: Option<ConsoleId>,
    next: Option<LogPosition>,
    revision: u64,
}

pub(super) struct Read {
    pub target: Target,
    pub position: LogPosition,
    revision: u64,
}

impl Console {
    pub fn selected(&self) -> Option<&ProviderStatus> {
        self.providers
            .iter()
            .find(|entry| Some(&entry.id) == self.selected.as_ref())
    }

    pub fn target(&self, lane: Lane) -> Option<Target> {
        if self.list_error.is_some() {
            return None;
        }
        self.selected()?.target(lane)
    }

    pub fn update_providers(&mut self, result: io::Result<Vec<ProviderStatus>>) {
        match result {
            Ok(providers) => {
                self.list_error = None;
                let previous = self.selected().map(|entry| entry.logs.clone());
                self.providers = providers;
                if let Some(entry) = self.selected() {
                    if previous.as_ref() != Some(&entry.logs) {
                        if matches!(
                            entry.logs,
                            Capability::Connected(_) | Capability::Unsupported
                        ) {
                            self.reset_log();
                        } else {
                            self.refresh(None);
                            self.log_error = None;
                        }
                    }
                } else {
                    let entry = self
                        .providers
                        .iter()
                        .find(|entry| {
                            matches!(entry.logs, Capability::Connected(_))
                                || matches!(entry.commands, Capability::Connected(_))
                        })
                        .or_else(|| self.providers.first());
                    self.select(entry.map(|entry| entry.id.clone()));
                }
            }
            Err(error) => self.list_error = Some(error.to_string()),
        }
    }

    pub fn key(&mut self, key: KeyCode, page_rows: usize) {
        match key {
            KeyCode::Char('[' | ']') if !self.providers.is_empty() => {
                let current = self
                    .providers
                    .iter()
                    .position(|entry| Some(&entry.id) == self.selected.as_ref())
                    .unwrap_or_default();
                let len = self.providers.len();
                let next = (current
                    + if key == KeyCode::Char(']') {
                        1
                    } else {
                        len - 1
                    })
                    % len;
                self.select(Some(self.providers[next].id.clone()));
            }
            KeyCode::Char('p' | 'n') if self.target(Lane::Logs).is_some() => {
                let position = self.output.as_ref().and_then(|page| {
                    if key == KeyCode::Char('p') {
                        page.before.clone().map(LogPosition::Before)
                    } else {
                        page.after.clone().map(LogPosition::After)
                    }
                });
                if position.is_some() {
                    self.view.following = false;
                    self.view.show_page_end();
                    self.refresh(position);
                }
            }
            _ => {
                let was_following = self.view.following;
                let lines = self.lines().count();
                if self.view.key(key, page_rows, lines) {
                    self.latest();
                } else if was_following && !self.view.following && self.output.is_some() {
                    self.refresh(None);
                }
            }
        }
    }

    pub fn lines(&self) -> impl DoubleEndedIterator<Item = &str> + '_ {
        self.view.filtered(
            self.output
                .iter()
                .flat_map(|page| page.lines.iter().map(String::as_str)),
        )
    }

    fn select(&mut self, id: Option<ConsoleId>) {
        if self.selected != id {
            self.selected = id;
            self.reset_log();
        }
    }

    fn reset_log(&mut self) {
        self.output = None;
        self.log_error = None;
        self.view.horizontal = 0;
        self.latest();
    }

    pub fn latest(&mut self) {
        self.view.following = true;
        self.view.show_page_end();
        self.refresh(Some(LogPosition::Latest));
    }

    fn refresh(&mut self, position: Option<LogPosition>) {
        self.revision = self.revision.wrapping_add(1);
        self.next = position;
    }

    pub fn requested(&self) -> bool {
        self.next.is_some() && self.target(Lane::Logs).is_some()
    }

    pub fn read(&mut self) -> Option<Read> {
        let target = self.target(Lane::Logs)?;
        let position = self
            .next
            .take()
            .or_else(|| self.view.following.then_some(LogPosition::Latest))?;
        Some(Read {
            target,
            position,
            revision: self.revision,
        })
    }

    pub fn update_log(&mut self, read: &Read, result: io::Result<LogPage>) {
        if read.revision != self.revision {
            return;
        }
        match result {
            Ok(page) => {
                if matches!(read.position, LogPosition::Latest) || !page.lines.is_empty() {
                    self.output = Some(page);
                    self.log_error = None;
                } else {
                    self.log_error = page.notice.or_else(|| {
                        Some(
                            match read.position {
                                LogPosition::Before(_) => "No older log lines available.",
                                _ => "No newer log lines available.",
                            }
                            .into(),
                        )
                    });
                }
            }
            Err(error) => self.log_error = Some(format!("Log read failed · {error}")),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::{Capability, Console, ConsoleId, KeyCode, LogPage, LogPosition, ProviderStatus};

    #[test]
    fn home_during_history_read_shows_the_first_rows_of_the_arriving_page() {
        let mut console = Console::default();
        console.update_providers(Ok(vec![ProviderStatus {
            id: ConsoleId {
                instance: "server".into(),
                provider: "logs".into(),
            },
            label: "Server logs".into(),
            commands: Capability::Unsupported,
            logs: Capability::Connected("session".into()),
        }]));
        let latest = console.read().expect("latest page requested");
        console.update_log(
            &latest,
            Ok(LogPage {
                lines: vec!["Recent line".into()],
                before: Some("older".into()),
                after: None,
                notice: None,
            }),
        );

        console.key(KeyCode::Char('p'), 3);
        let older = console.read().expect("history page requested");
        assert!(matches!(&older.position, LogPosition::Before(cursor) if cursor == "older"));
        console.key(KeyCode::Home, 3);
        console.update_log(
            &older,
            Ok(LogPage {
                lines: ["Startup", "First log", "Second log", "Third log"]
                    .map(str::to_owned)
                    .into(),
                before: None,
                after: Some("newer".into()),
                notice: None,
            }),
        );
        let lines: Vec<_> = console.lines().collect();
        assert_eq!(
            &lines[console.view.window(lines.len(), 3)],
            ["Startup", "First log", "Second log"]
        );

        console.key(KeyCode::Down, 3);
        let lines: Vec<_> = console.lines().collect();
        assert_eq!(
            &lines[console.view.window(lines.len(), 3)],
            ["First log", "Second log", "Third log"]
        );
    }
}
