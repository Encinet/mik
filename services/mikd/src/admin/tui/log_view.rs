use crossterm::event::KeyCode;
use std::ops::Range;

pub(super) struct LogView {
    pub filter: String,
    pub following: bool,
    pub horizontal: u16,
    // Home stays anchored to the first row when the requested page arrives.
    rows_from_end: Option<usize>,
}

impl Default for LogView {
    fn default() -> Self {
        Self {
            filter: String::new(),
            following: true,
            horizontal: 0,
            rows_from_end: Some(0),
        }
    }
}

impl LogView {
    pub fn filtered<'a>(
        &'a self,
        lines: impl DoubleEndedIterator<Item = &'a str> + 'a,
    ) -> impl DoubleEndedIterator<Item = &'a str> + 'a {
        lines.filter(|line| line.contains(&self.filter))
    }

    pub fn window(&self, lines: usize, page_rows: usize) -> Range<usize> {
        let end = lines - self.tail_offset(lines, page_rows);
        end.saturating_sub(page_rows)..end
    }

    pub fn show_page_end(&mut self) {
        self.rows_from_end = Some(0);
    }

    /// Returns true when the operator returns to the latest output.
    pub fn key(&mut self, key: KeyCode, page_rows: usize, lines: usize) -> bool {
        match key {
            KeyCode::Char(' ') if self.following => self.following = false,
            KeyCode::Char(' ') | KeyCode::End => {
                self.following = true;
                self.show_page_end();
                return true;
            }
            KeyCode::Up | KeyCode::PageUp => {
                self.following = false;
                let count = if key == KeyCode::Up { 1 } else { page_rows };
                let offset = self.tail_offset(lines, page_rows).saturating_add(count);
                self.rows_from_end = (offset < lines.saturating_sub(page_rows)).then_some(offset);
            }
            KeyCode::Down | KeyCode::PageDown => {
                let count = if key == KeyCode::Down { 1 } else { page_rows };
                self.rows_from_end = Some(self.tail_offset(lines, page_rows).saturating_sub(count));
            }
            KeyCode::Home => {
                self.following = false;
                self.rows_from_end = None;
            }
            KeyCode::Left => self.horizontal = self.horizontal.saturating_sub(12),
            KeyCode::Right => self.horizontal = self.horizontal.saturating_add(12),
            _ => {}
        }
        false
    }

    pub fn filter(&mut self, text: String) {
        self.filter = text;
        self.show_page_end();
        self.horizontal = 0;
    }

    pub fn mode(&self) -> &'static str {
        if self.following {
            "Following"
        } else {
            "Paused"
        }
    }

    fn tail_offset(&self, lines: usize, page_rows: usize) -> usize {
        let overflow = lines.saturating_sub(page_rows);
        self.rows_from_end.unwrap_or(overflow).min(overflow)
    }
}
