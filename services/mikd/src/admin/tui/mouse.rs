use super::{app::Page, catalog::Collection};
use crossterm::event::{KeyCode, MouseButton, MouseEvent, MouseEventKind};
use ratatui::layout::{Position, Rect};

#[derive(Clone, Copy)]
pub(super) enum Control {
    Key(KeyCode),
    Page(Page),
    Collection(Collection),
    Row(usize),
    Menu(usize),
    Field(usize),
    Input { field: usize, offset: usize },
    Confirm(bool),
}

#[derive(Clone, Copy)]
pub(super) enum Input {
    Click(Control),
    Scroll { key: KeyCode, logs: bool },
}

#[derive(Default)]
pub(super) struct Targets {
    controls: Vec<(Rect, Control)>,
    scroll: Rect,
    logs: bool,
    pub list_offset: Option<u16>,
}

impl Targets {
    pub fn add(&mut self, area: Rect, control: Control) {
        if !area.is_empty() {
            self.controls.push((area, control));
        }
    }

    pub fn scroll(&mut self, area: Rect, logs: bool) {
        self.scroll = area;
        self.logs = logs;
    }

    pub fn input(&self, mouse: MouseEvent) -> Option<Input> {
        if !mouse.modifiers.is_empty() {
            return None;
        }
        let point = Position::new(mouse.column, mouse.row);
        if mouse.kind == MouseEventKind::Down(MouseButton::Left) {
            let (area, control) = self
                .controls
                .iter()
                .rev()
                .find(|(area, _)| area.contains(point))?;
            let control = match *control {
                Control::Input { field, offset } => Control::Input {
                    field,
                    offset: offset + usize::from(point.x - area.x),
                },
                control => control,
            };
            return Some(Input::Click(control));
        }
        if !self.scroll.contains(point) {
            return None;
        }
        let key = match mouse.kind {
            MouseEventKind::ScrollUp => KeyCode::Up,
            MouseEventKind::ScrollDown => KeyCode::Down,
            MouseEventKind::ScrollLeft if self.logs => KeyCode::Left,
            MouseEventKind::ScrollRight if self.logs => KeyCode::Right,
            _ => return None,
        };
        Some(Input::Scroll {
            key,
            logs: self.logs,
        })
    }
}
