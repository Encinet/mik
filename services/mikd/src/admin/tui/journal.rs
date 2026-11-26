use super::log_view::LogView;
use crate::admin::systemd::JournalOutput;
use crossterm::event::KeyCode;
use std::io;

pub(super) struct Journal {
    pub output: Option<JournalOutput>,
    pub problem: Option<String>,
    pub view: LogView,
    pub refresh: bool,
}

impl Journal {
    pub fn new() -> Self {
        Self {
            output: None,
            problem: None,
            view: LogView::default(),
            refresh: true,
        }
    }

    pub fn lines(&self) -> impl DoubleEndedIterator<Item = &str> + '_ {
        self.view.filtered(
            self.output
                .as_ref()
                .map_or("", |output| output.entries.as_str())
                .lines(),
        )
    }

    pub fn key(&mut self, key: KeyCode, page_rows: usize) {
        let lines = self.lines().count();
        self.refresh |= self.view.key(key, page_rows, lines);
    }

    pub fn update(&mut self, result: io::Result<JournalOutput>) {
        match result {
            Ok(output) => {
                self.output = Some(output);
                self.problem = None;
            }
            Err(error) => self.problem = Some(error.to_string()),
        }
    }
}
