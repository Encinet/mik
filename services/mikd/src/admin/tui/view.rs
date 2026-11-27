mod cache;
mod console;
mod dialog;
mod music;
mod overview;
mod theme;

pub(super) use theme::Theme;

use super::{
    app::{App, Layer, Level, Page},
    form::Form,
    log_view::LogView,
    mouse::{Control, Targets},
};
use crate::{admin::systemd, domains::music::SourceState};
use crossterm::event::KeyCode;
use ratatui::{
    Frame,
    layout::{Constraint, Layout, Rect},
    style::{Color, Modifier, Style},
    text::{Line, Span, Text},
    widgets::{List, ListState, Paragraph, Wrap},
};
use std::path::Path;

const FOCUS: Style = Style::new().add_modifier(Modifier::REVERSED);
const STRONG: Style = Style::new().add_modifier(Modifier::BOLD);
const ACCENT: Style = STRONG.fg(Color::LightCyan);
const OK: Style = Style::new().fg(Color::LightGreen);
const WARN: Style = Style::new().fg(Color::LightYellow);
const ERROR: Style = Style::new().fg(Color::LightRed);

pub(super) fn fits(area: Rect) -> bool {
    area.width >= 48 && area.height >= 16
}

#[derive(Default)]
pub(super) struct Viewport {
    pub log_rows: usize,
    pub scroll_limit: u16,
    pub targets: Targets,
}

pub(super) fn render(frame: &mut Frame<'_>, app: &App, socket: &Path, theme: Theme) -> Viewport {
    let canvas = frame.area();
    if !fits(canvas) {
        let quit = if app.layer().is_some() {
            "Ctrl+C Quit"
        } else {
            "q / Ctrl+C Quit"
        };
        frame.render_widget(
            Paragraph::new(format!("Terminal too small. Need 48 × 16 cells.\n{quit}"))
                .wrap(Wrap { trim: true }),
            canvas,
        );
        return Viewport::default();
    }
    let area = Rect::new(canvas.x + 1, canvas.y, canvas.width - 2, canvas.height);
    let sidebar = area.width >= 108;
    let nav_height = u16::from(!sidebar);
    let mut viewport = Viewport::default();
    let footer = actions(app, area.width);
    let [header, nav, _, body, footer_area] = Layout::vertical([
        Constraint::Length(1),
        Constraint::Length(nav_height),
        Constraint::Length(u16::from(canvas.height >= 22)),
        Constraint::Min(1),
        Constraint::Length(u16_len(footer.len())),
    ])
    .areas(area);
    let (nav, body) = if sidebar {
        let [nav, _, body] = Layout::horizontal([
            Constraint::Length(18),
            Constraint::Length(2),
            Constraint::Min(1),
        ])
        .areas(body);
        (nav, body)
    } else {
        (nav, body)
    };
    let buffer = frame.buffer_mut();
    buffer.set_style(canvas, theme.background);
    for (row, style) in [(header, theme.chrome), (footer_area, theme.chrome)] {
        buffer.set_style(Rect::new(canvas.x, row.y, canvas.width, row.height), style);
    }
    if sidebar {
        buffer.set_style(nav, theme.navigation);
    } else {
        buffer.set_style(
            Rect::new(canvas.x, nav.y, canvas.width, nav.height),
            theme.navigation,
        );
    }

    let title = "mikd admin";
    if app.page != Page::Overview || app.layer().is_some() {
        let (unit, style) = overview::unit_status(app);
        let unit = Span::styled(inline(unit), style);
        let connection = connection_badge(app);
        let status = Line::from(vec![unit, Span::raw("  |  "), connection.clone()]);
        let status = if status.width() + title.len() + 2 <= usize::from(header.width) {
            status
        } else {
            Line::from(connection)
        };
        frame.render_widget(Paragraph::new(status).right_aligned(), header);
    }
    frame.render_widget(Paragraph::new(title).style(STRONG), header);

    let body = if app.page == Page::Music {
        let navigation_targets = app.can_navigate().then_some(&mut viewport.targets);
        music::workspace(frame, body, app.music.current, theme, navigation_targets)
    } else {
        body
    };
    render_body(frame, body, app, socket, theme, &mut viewport);
    let mut inactive = Targets::default();
    let nav_targets = if app.can_navigate() {
        &mut viewport.targets
    } else {
        &mut inactive
    };
    navigation(frame, nav, sidebar, app.page, theme, nav_targets);
    footer.render(frame, footer_area, &mut viewport.targets);
    if theme.monochrome {
        for cell in &mut frame.buffer_mut().content {
            cell.fg = Color::Reset;
            cell.bg = Color::Reset;
        }
    }
    viewport
}

fn render_body(
    frame: &mut Frame<'_>,
    body: Rect,
    app: &App,
    socket: &Path,
    theme: Theme,
    viewport: &mut Viewport,
) {
    if log_input_form(app).is_some() {
        render_page(frame, body, app, socket, theme, viewport);
    } else if let Some(layer) = app.layer() {
        if layer.is_modal() {
            if let Some((background, scroll)) = app.background_layer() {
                dialog::render(
                    frame,
                    body,
                    app,
                    background,
                    theme,
                    scroll,
                    &mut Targets::default(),
                );
            } else {
                render_page(frame, body, app, socket, theme, &mut Viewport::default());
            }
            frame
                .buffer_mut()
                .set_style(body, Style::new().add_modifier(Modifier::DIM));
        }
        viewport.scroll_limit = dialog::render(
            frame,
            body,
            app,
            layer,
            theme,
            app.layer_scroll(),
            &mut viewport.targets,
        );
    } else {
        render_page(frame, body, app, socket, theme, viewport);
    }
}

fn connection_badge(app: &App) -> Span<'static> {
    if app.status_problem.is_some() {
        Span::styled("Admin offline", ERROR)
    } else if app.last_status.is_some() {
        Span::styled("Admin connected", OK)
    } else {
        Span::styled("Checking admin", ACCENT)
    }
}

fn navigation(
    frame: &mut Frame<'_>,
    area: Rect,
    sidebar: bool,
    selected: Page,
    theme: Theme,
    targets: &mut Targets,
) {
    let mut x = area.x;
    let mut y = area.y;
    for page in Page::ALL {
        let label = format!(" {} {} ", page.index() + 1, page.name());
        let width = if sidebar {
            area.width
        } else {
            u16_len(label.len())
        };
        let cell = Rect::new(x, y, width, 1).intersection(area);
        frame.render_widget(
            Paragraph::new(label).style(if page == selected {
                theme.selection
            } else {
                Style::new()
            }),
            cell,
        );
        targets.add(cell, Control::Page(page));
        if sidebar {
            y += 1;
        } else {
            x += width + 1;
        }
    }
}

fn render_page(
    frame: &mut Frame<'_>,
    area: Rect,
    app: &App,
    socket: &Path,
    theme: Theme,
    viewport: &mut Viewport,
) {
    match app.page {
        Page::Overview => {
            viewport.scroll_limit =
                overview::render(frame, area, app, socket, theme, &mut viewport.targets);
        }
        Page::Journal | Page::Console => {
            let area = match log_input_form(app) {
                Some(form) => log_editor(
                    frame,
                    area,
                    form,
                    app.form_submitted(),
                    theme,
                    &mut viewport.targets,
                ),
                None => area,
            };
            viewport.log_rows = if app.page == Page::Journal {
                journal(frame, area, app)
            } else {
                console::render(frame, area, &app.console)
            };
            if viewport.log_rows != 0 {
                viewport.targets.scroll(area, true);
            }
        }
        Page::Music => {
            music::render(frame, area, app, &mut viewport.targets);
        }
    }
}

fn log_input_form(app: &App) -> Option<&Form> {
    match (app.page, app.layer()) {
        (Page::Console | Page::Journal, Some(Layer::Form(form))) => Some(form),
        _ => None,
    }
}

fn log_editor(
    frame: &mut Frame<'_>,
    area: Rect,
    form: &Form,
    submitted: bool,
    theme: Theme,
    targets: &mut Targets,
) -> Rect {
    let status = if submitted {
        Some(Line::styled("Submitting command…", ACCENT))
    } else {
        form.error
            .as_ref()
            .map(|error| Line::styled(inline(error), ERROR))
    };
    let status = Paragraph::new(status.into_iter().collect::<Vec<_>>()).wrap(Wrap { trim: true });
    let status_height = u16_len(status.line_count(area.width)).min(3);
    let keys = if submitted {
        "Esc Back"
    } else if form.console_target().is_some() {
        "Enter Send   Esc Cancel   Ctrl+U Clear"
    } else {
        "Enter Apply   Esc Cancel   Ctrl+U Clear"
    };
    let keys = key_lines(keys, area.width);
    let keys_height = u16_len(keys.len());
    let [logs, editor] = Layout::vertical([
        Constraint::Min(1),
        Constraint::Length(2 + status_height + keys_height),
    ])
    .areas(area);
    frame.buffer_mut().set_style(editor, theme.navigation);
    let [target, editor, message, controls] = Layout::vertical([
        Constraint::Length(1),
        Constraint::Length(1),
        Constraint::Length(status_height),
        Constraint::Length(keys_height),
    ])
    .areas(editor);
    let label = form.console_target().map_or_else(
        || form.title().to_owned(),
        |target| format!("Command to {}", inline(&target.id.to_string())),
    );
    frame.render_widget(Paragraph::new(label).style(ACCENT), target);
    let [prefix, field] =
        Layout::horizontal([Constraint::Length(2), Constraint::Min(1)]).areas(editor);
    let prompt = if form.console_target().is_some() {
        ": "
    } else {
        "/ "
    };
    frame.render_widget(Paragraph::new(prompt).style(ACCENT), prefix);
    let offset = dialog::input_field(frame, field, &form.fields[0], theme.editor, !submitted);
    if !submitted {
        targets.add(field, Control::Input { field: 0, offset });
    }
    frame.render_widget(status, message);
    keys.render(frame, controls, targets);
    logs
}

struct CollectionView<'a> {
    unavailable: bool,
    problem: Option<&'a str>,
    empty: &'a str,
    selected: usize,
    scroll: u16,
}

fn collection(
    frame: &mut Frame<'_>,
    area: Rect,
    title: impl Into<Line<'static>>,
    data: &CollectionView<'_>,
    rows: impl ExactSizeIterator<Item = Line<'static>>,
    targets: &mut Targets,
) {
    let inner = section(frame, area, title);
    collection_rows(frame, inner, data, rows, targets);
}

fn collection_rows(
    frame: &mut Frame<'_>,
    inner: Rect,
    data: &CollectionView<'_>,
    rows: impl ExactSizeIterator<Item = Line<'static>>,
    targets: &mut Targets,
) {
    let count = rows.len();
    let notice = if data.unavailable {
        Some(if count == 0 {
            "Module unavailable. 1 Overview shows service and connection status.".into()
        } else {
            "Module unavailable. Showing the last loaded entries.".into()
        })
    } else {
        data.problem.as_ref().map(|problem| {
            let prefix = if count == 0 {
                "Load failed"
            } else {
                "Refresh failed; showing previous entries"
            };
            format!("{prefix}. F5 Retry. {}", inline(problem))
        })
    };
    let inner = if let Some(notice) = notice {
        let message = Paragraph::new(notice).style(WARN).wrap(Wrap { trim: true });
        let height = u16_len(message.line_count(inner.width)).min(2);
        let [hint, list] =
            Layout::vertical([Constraint::Length(height), Constraint::Min(0)]).areas(inner);
        frame.render_widget(message, hint);
        if count == 0 {
            return;
        }
        list
    } else {
        inner
    };
    if count == 0 {
        frame.render_widget(Paragraph::new(data.empty), inner);
    } else {
        let [list, position] =
            Layout::vertical([Constraint::Min(1), Constraint::Length(1)]).areas(inner);
        render_list(frame, list, rows, data, targets);
        frame.render_widget(
            Paragraph::new(format!("{} / {count}", data.selected + 1)),
            position,
        );
    }
}

fn journal(frame: &mut Frame<'_>, area: Rect, app: &App) -> usize {
    if !app.systemd {
        panel(
            frame,
            area,
            "Journal",
            vec![Line::raw("A systemd target is required for the journal.")],
            0,
        );
        return 0;
    }
    let journal = &app.journal;
    let inner = section(frame, area, systemd::UNIT);
    let mut notes = Vec::new();
    if let Some(problem) = &journal.problem {
        notes.push(Line::styled(
            format!(
                "{} · {}",
                if journal.output.is_some() {
                    "Read failed; showing previous output"
                } else {
                    "Read failed"
                },
                inline(problem)
            ),
            WARN,
        ));
    } else if let Some(warning) = journal
        .output
        .as_ref()
        .and_then(|output| output.warning.as_ref())
    {
        notes.push(Line::styled(
            format!("Journal warning · {}", inline(warning)),
            WARN,
        ));
    }
    let empty = match (&journal.output, &journal.problem) {
        (Some(_), _) if !journal.view.filter.is_empty() => "No matching entries. / Edit filter.",
        (Some(_), _) => "No entries for this service.",
        (None, Some(_)) => "No journal output available. F5 retries.",
        (None, None) => "Waiting for journal output…",
    };
    log_entries(
        frame,
        inner,
        &journal.view,
        journal.lines(),
        notes,
        empty,
        |line| Line::raw(inline(line)),
    )
}

fn log_entries<'a>(
    frame: &mut Frame<'_>,
    inner: Rect,
    view: &LogView,
    entries: impl Iterator<Item = &'a str>,
    mut notes: Vec<Line<'static>>,
    empty: &'static str,
    style_line: fn(&str) -> Line<'static>,
) -> usize {
    if !view.filter.is_empty() {
        notes.push(pair("Filter", &view.filter));
    }
    let notes = Paragraph::new(notes).wrap(Wrap { trim: true });
    let top = u16_len(notes.line_count(inner.width)).min(inner.height.saturating_sub(1) / 2);
    let [meta, display, position] = Layout::vertical([
        Constraint::Length(top),
        Constraint::Min(1),
        Constraint::Length(1),
    ])
    .areas(inner);
    frame.render_widget(notes, meta);
    let rows = usize::from(display.height);
    let entries: Vec<_> = entries.collect();
    let window = view.window(entries.len(), rows);
    let text = if window.is_empty() {
        vec![Line::raw(empty)]
    } else {
        entries[window.clone()]
            .iter()
            .map(|line| style_line(line))
            .collect()
    };
    let pan_offset = if window.is_empty() {
        0
    } else {
        view.horizontal
    };
    frame.render_widget(Paragraph::new(text).scroll((0, pan_offset)), display);
    let count = if window.is_empty() {
        "0 lines".into()
    } else {
        format!(
            "Lines {}–{} of {}",
            window.start + 1,
            window.end,
            entries.len()
        )
    };
    let pan = if pan_offset == 0 {
        String::new()
    } else {
        format!("  |  Column {}", u32::from(pan_offset) + 1)
    };
    let status = Line::from(vec![
        Span::styled(view.mode(), if view.following { ACCENT } else { WARN }),
        Span::raw(format!("  |  {count}{pan}")),
    ]);
    frame.render_widget(Paragraph::new(status), position);
    rows
}

fn actions(app: &App, width: u16) -> KeyLines {
    if log_input_form(app).is_some() {
        return KeyLines::default();
    }
    let mut footer = KeyLines::default();
    if let Some(pending) = &app.pending {
        footer
            .lines
            .push(Line::styled(format!("Working · {}", pending.label), ACCENT));
    } else if let Some(notice) = &app.notice {
        let prefix = match notice.level {
            Level::Good => "Done",
            Level::Warning => "Check",
            Level::Error => "Error",
        };
        footer.lines.push(Line::styled(
            format!("{prefix} · {}", inline(&notice.text)),
            level(notice.level),
        ));
    }
    let result_key = if app.notice.is_some() {
        "   ! Result"
    } else {
        ""
    };
    let hints = match app.layer() {
        None => {
            let hints = match app.page {
                Page::Overview => {
                    return footer
                        .with_hints(&format!("↑↓ Scroll   ? Help   q Quit{result_key}"), width);
                }
                Page::Journal if app.systemd => format!(
                    "Space {}   / Filter   ↑↓ Scroll   ←→ Pan",
                    if app.journal.view.following {
                        "Pause"
                    } else {
                        "Follow"
                    },
                ),
                Page::Music => music::keys(app, app.music.current),
                Page::Console => console::keys(&app.console),
                Page::Journal => String::new(),
            };
            let separator = if hints.is_empty() { "" } else { "   " };
            format!("{hints}{separator}F5 Refresh   ? Help{result_key}")
        }
        Some(layer) if layer.is_modal() || matches!(layer, Layer::Help | Layer::Notice) => {
            String::new()
        }
        Some(_) => format!("F5 Refresh   ? Help{result_key}"),
    };
    footer.with_hints(&hints, width)
}

#[derive(Default)]
struct KeyLines {
    lines: Vec<Line<'static>>,
    targets: Vec<(Rect, KeyCode)>,
}

impl KeyLines {
    fn len(&self) -> usize {
        self.lines.len()
    }

    fn render(self, frame: &mut Frame<'_>, area: Rect, targets: &mut Targets) {
        for (mut rect, key) in self.targets {
            rect.x += area.x;
            rect.y += area.y;
            targets.add(rect.intersection(area), Control::Key(key));
        }
        frame.render_widget(Paragraph::new(self.lines), area);
    }

    fn with_hints(mut self, hints: &str, width: u16) -> Self {
        let mut row = Line::default();
        let mut used = 0;
        for hint in hints
            .split("   ")
            .map(str::trim)
            .filter(|hint| !hint.is_empty())
        {
            let (key, label) = hint.split_once(' ').unwrap_or((hint, ""));
            let mut hint = Line::from(Span::styled(key.to_owned(), ACCENT));
            if !label.is_empty() {
                hint.spans.push(Span::raw(format!(" {label}")));
            }
            let cells = hint.width();
            if used > 0 && used + 3 + cells > usize::from(width) {
                self.lines.push(std::mem::take(&mut row));
                used = 0;
            }
            if used > 0 {
                row.spans.push(Span::raw("   "));
                used += 3;
            }
            if let Some(key) = button_key(key) {
                let rect = Rect::new(u16_len(used), u16_len(self.lines.len()), u16_len(cells), 1);
                self.targets.push((rect, key));
            }
            row.spans.extend(hint.spans);
            used += cells;
        }
        if used > 0 {
            self.lines.push(row);
        }
        self
    }
}

fn key_lines(hints: &str, width: u16) -> KeyLines {
    KeyLines::default().with_hints(hints, width)
}

fn button_key(key: &str) -> Option<KeyCode> {
    match key {
        "Enter" => Some(KeyCode::Enter),
        "Esc" => Some(KeyCode::Esc),
        "F5" => Some(KeyCode::F(5)),
        "Space" => Some(KeyCode::Char(' ')),
        _ if key.len() == 1 => Some(KeyCode::Char(char::from(key.as_bytes()[0]))),
        _ => None,
    }
}

fn page_keys(previous: bool, next: bool) -> &'static str {
    match (previous, next) {
        (true, true) => "   p Prev   n Next",
        (true, false) => "   p Prev",
        (false, true) => "   n Next",
        _ => "",
    }
}

fn source_health(source: &SourceState) -> (&'static str, Style) {
    if !source.enabled {
        return ("Disabled", Style::new());
    }
    match (source.available, source.last_error.is_empty()) {
        (true, true) => ("Loaded", OK),
        (true, false) => ("Loaded · Error recorded", WARN),
        (false, _) => ("Unavailable", ERROR),
    }
}

fn panel(
    frame: &mut Frame<'_>,
    area: Rect,
    title: impl Into<Line<'static>>,
    lines: Vec<Line<'static>>,
    scroll: u16,
) -> u16 {
    let inner = section(frame, area, title);
    wrapped(frame, inner, lines, scroll)
}

fn wrapped(frame: &mut Frame<'_>, area: Rect, lines: Vec<Line<'static>>, scroll: u16) -> u16 {
    let paragraph = Paragraph::new(Text::from(lines)).wrap(Wrap { trim: false });
    let height = paragraph.line_count(area.width);
    let limit = u16_len(height.saturating_sub(usize::from(area.height)));
    frame.render_widget(paragraph.scroll((scroll.min(limit), 0)), area);
    limit
}

fn render_list(
    frame: &mut Frame<'_>,
    area: Rect,
    rows: impl ExactSizeIterator<Item = Line<'static>>,
    data: &CollectionView<'_>,
    targets: &mut Targets,
) {
    let count = rows.len();
    let mut state = ListState::default()
        .with_offset(usize::from(data.scroll))
        .with_selected(Some(data.selected));
    frame.render_stateful_widget(
        List::new(rows)
            .highlight_style(FOCUS)
            .highlight_symbol("> ")
            .highlight_spacing(ratatui::widgets::HighlightSpacing::Always),
        area,
        &mut state,
    );
    targets.scroll(area, false);
    let offset = state.offset();
    targets.list_offset = Some(u16_len(offset));
    for (row, index) in (offset..count).take(usize::from(area.height)).enumerate() {
        targets.add(
            Rect::new(area.x, area.y + u16_len(row), area.width, 1),
            Control::Row(index),
        );
    }
}

fn section(frame: &mut Frame<'_>, area: Rect, title: impl Into<Line<'static>>) -> Rect {
    let [heading, body] = Layout::vertical([Constraint::Length(1), Constraint::Min(0)]).areas(area);
    frame.render_widget(Paragraph::new(title.into().style(STRONG)), heading);
    body
}

fn pair(label: &'static str, value: &str) -> Line<'static> {
    Line::from(vec![
        Span::styled(label, STRONG),
        Span::raw("  "),
        Span::raw(inline(value)),
    ])
}

fn level(level: Level) -> Style {
    match level {
        Level::Good => OK,
        Level::Warning => WARN,
        Level::Error => ERROR,
    }
}

fn inline(text: &str) -> String {
    safe(text).replace('\n', " ")
}

fn safe(text: &str) -> String {
    text.chars()
        .map(|ch| match ch {
            '\n' => '\n',
            '\t' => ' ',
            '\u{202a}'..='\u{202e}' | '\u{2066}'..='\u{2069}' => '�',
            ch if ch.is_control() => '�',
            _ => ch,
        })
        .collect()
}

fn u16_len(value: usize) -> u16 {
    u16::try_from(value).unwrap_or(u16::MAX)
}

#[cfg(test)]
mod tests {
    use super::{App, FOCUS, Layer, Page, Path, Theme, Viewport, render};
    use crate::{
        admin::{
            systemd::{Action, JournalOutput, Unit},
            tui::{
                catalog::{Collection, SourceDetail},
                jobs::{Finished, Work},
            },
        },
        console::{Capability, ConsoleId, LogPage, ProviderStatus},
        domains::music::{CacheState, CatalogTrack, InspectedMedia, TrackInspection},
        transport::admin::{MinecraftStatus, Status},
    };
    use crossterm::event::{
        KeyCode, KeyEvent, KeyModifiers, MouseButton, MouseEvent, MouseEventKind,
    };
    use ratatui::{Terminal, backend::TestBackend, buffer::Cell};
    use std::{collections::BTreeMap, fmt::Write};

    fn draw(terminal: &mut Terminal<TestBackend>, app: &mut App) -> Viewport {
        let mut viewport = Viewport::default();
        terminal
            .draw(|frame| {
                viewport = render(frame, app, Path::new("/run/test.sock"), Theme::PLAIN);
            })
            .unwrap();
        app.sync_scroll(viewport.scroll_limit, viewport.targets.list_offset);
        viewport
    }

    fn on_text(terminal: &Terminal<TestBackend>, text: &str, kind: MouseEventKind) -> MouseEvent {
        let buffer = terminal.backend().buffer();
        let (row, column) = buffer
            .content()
            .chunks(usize::from(buffer.area.width))
            .enumerate()
            .find_map(|(row, cells)| {
                let line: String = cells.iter().map(Cell::symbol).collect();
                line.find(text)
                    .map(|column| (row, line[..column].chars().count()))
            })
            .unwrap_or_else(|| panic!("Missing rendered control {text}"));
        MouseEvent {
            row: u16::try_from(row).unwrap(),
            column: u16::try_from(column).unwrap(),
            kind,
            modifiers: KeyModifiers::NONE,
        }
    }

    #[test]
    fn minecraft_status_stays_visible_and_clears_live_values_on_disconnect() {
        for (width, height) in [(120, 30), (80, 24), (60, 24), (48, 16)] {
            let mut app = App::new(false);
            let mut terminal = Terminal::new(TestBackend::new(width, height)).unwrap();
            let click = MouseEventKind::Down(MouseButton::Left);
            let mut status: Status = serde_json::from_value(serde_json::json!({
                "ready": [],
                "problems": {},
                "web_api_port": 0,
                "web_chat_port": 0,
                "music_catalog": false,
                "music_source_registry": false,
                "music_media_cache": false,
                "paper_music": false,
            }))
            .unwrap();

            app.connect(Ok(status));
            draw(&mut terminal, &mut app);
            on_text(&terminal, "Minecraft Status   Status not reported", click);

            (status, _) = app.last_status.take().unwrap();
            status.minecraft = Some(MinecraftStatus::NoRecentSample);
            app.connect(Ok(status));
            draw(&mut terminal, &mut app);
            on_text(&terminal, "Minecraft Status   No recent data", click);

            (status, _) = app.last_status.take().unwrap();
            status.minecraft = Some(MinecraftStatus::Live {
                version: "1.21.11".into(),
                visible_players: 12,
                max_players: 60,
                tps: Some(19.98),
                mspt: Some(18.4),
                uptime_seconds: Some(90_600),
            });
            app.connect(Ok(status));
            let viewport = draw(&mut terminal, &mut app);
            on_text(&terminal, "Minecraft Status   Live", click);
            on_text(&terminal, "Socket only", click);
            on_text(&terminal, "Visible 12/60   TPS 19.98   MSPT 18.40", click);
            on_text(&terminal, "Version 1.21.11   MIK uptime 1d 01h 10m", click);
            assert!(
                viewport
                    .targets
                    .input(on_text(&terminal, "F5 Refresh", click))
                    .is_some()
            );

            app.connect(Err("connection closed".into()));
            draw(&mut terminal, &mut app);
            on_text(&terminal, "Minecraft Status   Admin unavailable", click);
            let screen: String = terminal
                .backend()
                .buffer()
                .content()
                .iter()
                .map(Cell::symbol)
                .collect();
            assert!(!screen.contains("TPS"));
            assert!(!screen.contains("1.21.11"));
        }
    }

    #[test]
    fn keeps_navigation_and_paused_log_position_visible_at_supported_widths() {
        let mut app = App::new(true);
        app.page = Page::Journal;
        app.journal.update(Ok(JournalOutput {
            entries: (1..=40).fold(String::new(), |mut entries, line| {
                writeln!(entries, "line {line}").unwrap();
                entries
            }),
            warning: None,
        }));
        app.journal.view.following = false;
        for (width, height, console_row) in [(120, 30, 5), (80, 24, 1), (60, 24, 1), (48, 16, 1)] {
            let mut terminal = Terminal::new(TestBackend::new(width, height)).unwrap();
            let mut rows = 0;
            terminal
                .draw(|frame| {
                    rows = render(frame, &app, Path::new("/run/test.sock"), Theme::PLAIN).log_rows;
                })
                .unwrap();
            let buffer = terminal.backend().buffer();
            let lines: Vec<String> = buffer
                .content()
                .chunks(usize::from(width))
                .map(|row| row.iter().map(Cell::symbol).collect())
                .collect();
            assert!(lines.iter().any(|line| line.contains("2 Logs")));
            assert!(lines[console_row].contains("4 Console"), "{lines:#?}");
            assert!(
                buffer
                    .content()
                    .iter()
                    .any(|cell| cell.symbol() == "L" && cell.modifier.contains(FOCUS.add_modifier))
            );
            assert!(lines.iter().any(|line| line.contains("line 40")));
            assert!(
                lines
                    .iter()
                    .any(|line| line.contains(&format!("Paused  |  Lines {}–40 of 40", 41 - rows)))
            );
            assert!(lines.iter().any(|line| line.contains("? Help")));
        }
    }

    #[test]
    fn mouse_selects_the_rendered_music_row_after_scrolling() {
        let mut app = App::new(false);
        app.connect(Ok(Status {
            ready: vec![],
            problems: BTreeMap::new(),
            web_api_port: 0,
            web_chat_port: 0,
            music_catalog: true,
            music_source_registry: false,
            music_media_cache: false,
            paper_music: false,
            minecraft: None,
        }));
        app.music.library.list.entries = (0..30)
            .map(|index| CatalogTrack {
                id: index.to_string(),
                title: format!("Track {index:02}"),
                artist: String::new(),
                album: String::new(),
                local: true,
                retired: false,
            })
            .collect();
        let click = MouseEventKind::Down(MouseButton::Left);
        let mut terminal = Terminal::new(TestBackend::new(60, 16)).unwrap();
        let viewport = draw(&mut terminal, &mut app);
        let input = viewport
            .targets
            .input(on_text(&terminal, "3 Music", click))
            .unwrap();
        assert!(app.mouse(input, viewport.log_rows).is_none());
        assert!(app.page == Page::Music);
        assert!(
            app.key(KeyEvent::new(KeyCode::End, KeyModifiers::NONE), 0)
                .is_none()
        );
        let viewport = draw(&mut terminal, &mut app);
        let input = viewport
            .targets
            .input(on_text(&terminal, "Track 29", MouseEventKind::ScrollUp))
            .unwrap();
        assert!(app.mouse(input, viewport.log_rows).is_none());
        assert_eq!(app.music.library.list.selected, 28);

        let viewport = draw(&mut terminal, &mut app);
        let row = on_text(&terminal, "Track 27", click);
        let input = viewport.targets.input(row).unwrap();
        assert!(app.mouse(input, viewport.log_rows).is_none());
        assert_eq!(app.music.library.list.selected, 27);
        let viewport = draw(&mut terminal, &mut app);
        assert_eq!(on_text(&terminal, "Track 27", click).row, row.row);
        let input = viewport
            .targets
            .input(on_text(&terminal, "Enter Details", click))
            .unwrap();
        assert!(matches!(app.mouse(input, viewport.log_rows), Some(Work::Track(id)) if id == "27"));
    }

    #[test]
    fn music_tabs_keep_list_state_and_related_links_keep_the_detail_stack() {
        for (width, height) in [(48, 16), (120, 30)] {
            let mut app = App::new(false);
            app.connect(Ok(Status {
                ready: vec![],
                problems: BTreeMap::new(),
                web_api_port: 0,
                web_chat_port: 0,
                music_catalog: true,
                music_source_registry: true,
                music_media_cache: true,
                paper_music: false,
                minecraft: None,
            }));
            let mut terminal = Terminal::new(TestBackend::new(width, height)).unwrap();
            let click = MouseEventKind::Down(MouseButton::Left);
            let mouse = |terminal: &mut Terminal<TestBackend>, app: &mut App, text: &str| {
                let viewport = draw(terminal, app);
                let input = viewport
                    .targets
                    .input(on_text(terminal, text, click))
                    .unwrap();
                app.mouse(input, viewport.log_rows)
            };
            let key = |app: &mut App, code| app.key(KeyEvent::new(code, KeyModifiers::NONE), 0);

            assert!(mouse(&mut terminal, &mut app, "3 Music").is_none());
            app.music.library.keyword = "Demo".into();
            app.music.library.list.entries = ["1", "2"]
                .into_iter()
                .map(|id| CatalogTrack {
                    id: id.into(),
                    title: format!("Demo {id}"),
                    artist: String::new(),
                    album: String::new(),
                    local: false,
                    retired: false,
                })
                .collect();
            app.music.library.list.selected = 1;
            assert!(mouse(&mut terminal, &mut app, " Sources ",).is_none());
            assert!(app.music.current == Collection::Sources);
            assert!(key(&mut app, KeyCode::Char(']')).is_none());
            assert!(app.music.current == Collection::Cache);
            assert!(mouse(&mut terminal, &mut app, " Tracks ",).is_none());
            assert!(app.music.current == Collection::Library);
            assert_eq!(app.music.library.keyword, "Demo");
            assert_eq!(app.music.library.list.selected, 1);

            let viewport = draw(&mut terminal, &mut app);
            let input = viewport
                .targets
                .input(on_text(&terminal, "Demo 2", click))
                .unwrap();
            assert!(app.mouse(input, 0).is_none());
            assert!(matches!(app.mouse(input, 0), Some(Work::Track(id)) if id == "2"));
            app.finished(Finished::Track(Ok(Box::new(TrackInspection {
                id: "2".into(),
                state: "ACTIVE".into(),
                availability: None,
                title: "Demo 2".into(),
                artist: String::new(),
                album: String::new(),
                duration_ms: None,
                source: Some("remote".into()),
                channel: None,
                registered_source: Some("local:registered.js".into()),
                last_failure: None,
                media: Some(InspectedMedia::Cached {
                    version: "media-v1".into(),
                    source: Some("local:fallback.js".into()),
                    state: CacheState::Idle,
                }),
            }))));
            assert!(matches!(
                mouse(&mut terminal, &mut app, "a Audio download source"),
                Some(Work::FollowSource(id)) if id == "local:fallback.js"
            ));
            app.finished(Finished::Source(Ok(SourceDetail::Missing(
                "local:fallback.js".into(),
            ))));
            assert!(mouse(&mut terminal, &mut app, "b Cached downloads").is_none());
            assert!(
                matches!(app.layer(), Some(Layer::SourceCache(cache)) if cache.source == "local:fallback.js")
            );
            assert!(key(&mut app, KeyCode::Esc).is_none());
            assert!(matches!(app.layer(), Some(Layer::Source(_))));
            assert!(mouse(&mut terminal, &mut app, "Esc Back").is_none());
            assert!(matches!(app.layer(), Some(Layer::Track(track)) if track.id == "2"));
            assert!(matches!(
                mouse(&mut terminal, &mut app, "s Registered source"),
                Some(Work::FollowSource(id)) if id == "local:registered.js"
            ));
        }
    }

    #[test]
    fn mouse_restart_requires_its_confirmation_and_blocks_underlying_navigation() {
        let mut app = App::new(true);
        app.unit = Some(Ok(Unit {
            load: "loaded".into(),
            active: "active".into(),
            sub: "running".into(),
            result: "success".into(),
        }));
        let click = MouseEventKind::Down(MouseButton::Left);
        let mut terminal = Terminal::new(TestBackend::new(120, 30)).unwrap();
        let viewport = draw(&mut terminal, &mut app);
        let console = on_text(&terminal, "4 Console", click);
        let input = viewport
            .targets
            .input(on_text(&terminal, "m Service actions", click))
            .unwrap();
        assert!(app.mouse(input, viewport.log_rows).is_none());
        let viewport = draw(&mut terminal, &mut app);
        let input = viewport
            .targets
            .input(on_text(&terminal, "Restart service", click))
            .unwrap();
        assert!(app.mouse(input, viewport.log_rows).is_none());
        assert!(matches!(app.layer(), Some(Layer::Confirm(confirm)) if !confirm.accepted));
        let viewport = draw(&mut terminal, &mut app);
        assert!(viewport.targets.input(console).is_none());
        let input = viewport
            .targets
            .input(on_text(&terminal, " Cancel ", click))
            .unwrap();
        assert!(app.mouse(input, viewport.log_rows).is_none());
        assert!(app.pending.is_none());
        assert!(app.layer().is_none());

        let viewport = draw(&mut terminal, &mut app);
        let input = viewport
            .targets
            .input(on_text(&terminal, "m Service actions", click))
            .unwrap();
        assert!(app.mouse(input, viewport.log_rows).is_none());
        let viewport = draw(&mut terminal, &mut app);
        let input = viewport
            .targets
            .input(on_text(&terminal, "Restart service", click))
            .unwrap();
        assert!(app.mouse(input, viewport.log_rows).is_none());
        let viewport = draw(&mut terminal, &mut app);
        let mut restart = on_text(&terminal, " Cancel ", click);
        restart.column += 11;
        let input = viewport.targets.input(restart).unwrap();
        assert!(matches!(
            app.mouse(input, viewport.log_rows),
            Some(Work::Service(Action::Restart))
        ));
    }

    #[test]
    fn console_keeps_colored_logs_visible_while_editing_inline_at_minimum_size() {
        let mut app = App::new(false);
        app.page = Page::Console;
        app.console.update_providers(Ok(vec![ProviderStatus {
            id: ConsoleId {
                instance: "paper".into(),
                provider: "native".into(),
            },
            label: "Server".into(),
            commands: Capability::Connected("cmd".into()),
            logs: Capability::Connected("log".into()),
        }]));
        app.console.output = Some(LogPage {
            lines: vec![
                "[12:34:56] [Server thread/WARN]: plain warning".into(),
                "\x1b[31mColored\x1b[0m output".into(),
            ],
            before: None,
            after: None,
            notice: None,
        });
        assert!(
            app.key(KeyEvent::new(KeyCode::Char(':'), KeyModifiers::NONE), 8)
                .is_none()
        );
        app.paste("say 在线");
        let mut terminal = Terminal::new(TestBackend::new(48, 16)).unwrap();
        let viewport = draw(&mut terminal, &mut app);
        let cells = terminal.backend().buffer().content();
        let text = cells.iter().map(Cell::symbol).collect::<String>();
        assert!(text.contains("Command to paper/native"));
        assert!(text.contains("say 在"));
        assert!(
            cells
                .windows(3)
                .any(|cells| cells[0].symbol() == "在" && cells[2].symbol() == "线")
        );
        assert!(text.contains("Enter Send"));
        assert!(text.contains("Colored output"));
        assert!(
            cells
                .iter()
                .any(|cell| cell.symbol() == "W" && cell.fg == ratatui::style::Color::LightYellow)
        );
        assert!(
            cells
                .iter()
                .any(|cell| cell.symbol() == "C" && cell.fg == ratatui::style::Color::Red)
        );

        let at = on_text(&terminal, "在", MouseEventKind::Down(MouseButton::Left));
        let input = viewport.targets.input(at).unwrap();
        assert!(app.mouse(input, viewport.log_rows).is_none());
        app.paste("哈");
        let roll = on_text(&terminal, "Colored", MouseEventKind::ScrollUp);
        let input = viewport.targets.input(roll).unwrap();
        assert!(app.mouse(input, viewport.log_rows).is_none());
        assert!(
            matches!(app.layer(), Some(Layer::Form(form)) if form.fields[0].value == "say 哈在线")
        );
        assert!(!app.console.view.following);
    }
}
