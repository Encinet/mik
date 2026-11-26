mod cache;
mod console;
mod dialog;
mod music;

use super::{
    app::{App, Layer, Level, Page},
    log_view::LogView,
};
use crate::{
    admin::systemd::{self, Action},
    domains::music::SourceState,
    transport::admin::Status,
};
use ratatui::{
    Frame,
    layout::{Constraint, Layout, Rect},
    style::{Color, Modifier, Style},
    text::{Line, Span, Text},
    widgets::{Block, List, ListState, Padding, Paragraph, Tabs, Wrap},
};
use std::path::Path;

const FOCUS: Style = Style::new().add_modifier(Modifier::REVERSED);
const STRONG: Style = Style::new().add_modifier(Modifier::BOLD);
const OK: Style = Style::new().fg(Color::Green);
const WARN: Style = Style::new().fg(Color::Yellow);
const ERROR: Style = Style::new().fg(Color::Red);
const CHROME_ROWS: u16 = 7;

pub(super) fn fits(area: Rect) -> bool {
    area.width >= 48 && area.height >= 16
}

pub(super) fn render(frame: &mut Frame<'_>, app: &App, socket: &Path) -> usize {
    let area = frame.area();
    if !fits(area) {
        frame.render_widget(
            Paragraph::new("Resize to at least 48 × 16 cells.\nCtrl+C Quit")
                .wrap(Wrap { trim: true }),
            area,
        );
        return 0;
    }
    let [header, tabs, body, footer] = Layout::vertical([
        Constraint::Length(1),
        Constraint::Length(2),
        Constraint::Min(5),
        Constraint::Length(CHROME_ROWS - 3),
    ])
    .areas(area);

    let connection = match &app.status {
        Some(Ok(_)) => Span::styled("Management connected ", OK),
        Some(Err(_)) => Span::styled("Management offline ", WARN),
        None => Span::raw("Checking management socket "),
    };
    frame.render_widget(Paragraph::new(" mikd / admin").style(STRONG), header);
    frame.render_widget(
        Paragraph::new(Line::from(connection)).right_aligned(),
        header,
    );
    actions(frame, footer, app);
    if app.layer().is_some() {
        let dialog = Rect::new(area.x, area.y + 1, area.width, area.height - 3);
        dialog::render(frame, dialog, app);
        return 0;
    }
    let labels = Page::ALL.map(|page| format!("{} {}", page.shortcut(), page.name()));
    let tabs_widget = Tabs::new(labels)
        .select(app.page.index())
        .highlight_style(FOCUS)
        .padding("", "")
        .divider(" ");
    frame.render_widget(tabs_widget, tabs);

    let mut log_rows = 0;
    match app.page {
        Page::Overview => overview(frame, body, app, socket),
        Page::Journal => log_rows = journal(frame, body, app),
        Page::Music => music::render(frame, body, app),
        Page::Console => log_rows = console::render(frame, body, &app.console),
    }
    log_rows
}

fn overview(frame: &mut Frame<'_>, area: Rect, app: &App, socket: &Path) {
    let mut readiness = vec![
        pair("Socket", &socket.display().to_string()),
        Line::default(),
    ];
    match &app.status {
        Some(Ok(status)) => readiness.extend(module_lines(status)),
        Some(Err(error)) => readiness.push(Line::styled(inline(error), WARN)),
        None => readiness.push(Line::raw("Waiting for a management response…")),
    }
    let host = host_lines(app);
    if area.width >= 76 {
        let [modules, service] =
            Layout::horizontal([Constraint::Percentage(57), Constraint::Percentage(43)])
                .areas(area);
        panel(
            frame,
            modules,
            "Management and modules",
            readiness,
            app.page_scroll,
        );
        panel(frame, service, systemd::UNIT, host, 0);
    } else {
        let mut lines = host;
        lines.push(Line::default());
        lines.extend(readiness);
        panel(frame, area, "Service and modules", lines, app.page_scroll);
    }
}

fn module_lines(status: &Status) -> Vec<Line<'static>> {
    let mut lines = Vec::new();
    let ready: Vec<_> = status
        .ready
        .iter()
        .filter(|name| !matches!(name.as_str(), "web-api" | "web-chat"))
        .map(String::as_str)
        .collect();
    if !ready.is_empty() {
        lines.push(pair("Ready", &ready.join(", ")));
    }
    for (name, problem) in &status.problems {
        lines.push(Line::styled(
            format!("{}  {}", inline(name), inline(problem)),
            WARN,
        ));
    }
    for (name, port) in [
        ("Web API", status.web_api_port),
        ("Web chat", status.web_chat_port),
    ] {
        let value = if port == 0 {
            "Not listening".into()
        } else {
            format!("Listening on {port}")
        };
        lines.push(pair(name, &value));
    }
    for (name, ready) in [
        ("Music catalog", status.music_catalog),
        ("LX registry", status.music_source_registry),
        ("Media cache", status.music_media_cache),
        ("Paper music listener", status.paper_music),
    ] {
        lines.push(pair(name, if ready { "Ready" } else { "Unavailable" }));
    }
    lines
}

fn host_lines(app: &App) -> Vec<Line<'static>> {
    if !app.systemd {
        return vec![Line::raw(
            "Socket-only target. Systemd actions and logs are unavailable.",
        )];
    }
    match &app.unit {
        Some(Ok(unit)) => {
            let mut lines = vec![
                pair("Load", &unit.load),
                pair("State", &format!("{} / {}", unit.active, unit.sub)),
            ];
            if !unit.result.is_empty() && unit.result != "success" {
                lines.push(pair("Result", &unit.result));
            }
            lines
        }
        Some(Err(error)) => vec![Line::styled(inline(error), WARN)],
        None => vec![Line::raw("Checking systemd unit…")],
    }
}

struct CollectionView<'a> {
    unavailable: bool,
    problem: Option<&'a str>,
    empty: &'a str,
    selected: usize,
}

fn collection(
    frame: &mut Frame<'_>,
    area: Rect,
    title: impl Into<Line<'static>>,
    data: &CollectionView<'_>,
    rows: impl Iterator<Item = Line<'static>>,
) {
    let inner = box_area(frame, area, title);
    let mut rows = rows.peekable();
    if let Some(problem) = data.problem.filter(|_| !data.unavailable) {
        let [hint, list] =
            Layout::vertical([Constraint::Length(2), Constraint::Min(1)]).areas(inner);
        frame.render_widget(
            Paragraph::new(format!("Load failed · {} · F5 Retry", inline(problem)))
                .style(WARN)
                .wrap(Wrap { trim: true }),
            hint,
        );
        if rows.peek().is_some() {
            render_list(frame, list, rows, data.selected);
        }
    } else if data.unavailable {
        frame.render_widget(
            Paragraph::new("Unavailable. Check management and module readiness in Overview.")
                .wrap(Wrap { trim: true }),
            inner,
        );
    } else if rows.peek().is_none() {
        frame.render_widget(Paragraph::new(data.empty), inner);
    } else {
        render_list(frame, inner, rows, data.selected);
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
    let inner = box_area(
        frame,
        area,
        format!("{} · {}", systemd::UNIT, journal.view.mode()),
    );
    let mut notes = Vec::new();
    if let Some(problem) = &journal.problem {
        notes.push(Line::styled(
            format!("Journal read failed · {}", inline(problem)),
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
        (Some(_), _) => "No matching entries.",
        (None, Some(_)) => "No journal output available.",
        (None, None) => "Waiting for journal output…",
    };
    log_entries(frame, inner, &journal.view, journal.lines(), notes, empty)
}

fn log_entries<'a>(
    frame: &mut Frame<'_>,
    inner: Rect,
    view: &LogView,
    entries: impl Iterator<Item = &'a str>,
    mut notes: Vec<Line<'static>>,
    empty: &'static str,
) -> usize {
    if !view.filter.is_empty() {
        notes.push(pair("Filter", &view.filter));
    }
    let notes = Paragraph::new(notes).wrap(Wrap { trim: true });
    let top = u16_len(notes.line_count(inner.width)).min(inner.height / 2);
    let [meta, display] =
        Layout::vertical([Constraint::Length(top), Constraint::Min(1)]).areas(inner);
    frame.render_widget(notes, meta);
    let rows = usize::from(display.height);
    let entries: Vec<_> = entries.collect();
    let visible = &entries[view.window(entries.len(), rows)];
    let text = if visible.is_empty() {
        vec![Line::raw(empty)]
    } else {
        visible.iter().map(|line| Line::raw(inline(line))).collect()
    };
    frame.render_widget(Paragraph::new(text).scroll((0, view.horizontal)), display);
    rows
}

fn actions(frame: &mut Frame<'_>, area: Rect, app: &App) {
    let [commands, result, global] = Layout::vertical([
        Constraint::Length(2),
        Constraint::Length(1),
        Constraint::Length(1),
    ])
    .areas(area);
    let status = if let Some(pending) = &app.pending {
        Line::styled(format!(" {}…", pending.label), STRONG)
    } else if let Some(notice) = &app.notice {
        let prefix = match notice.level {
            Level::Good => "Done",
            Level::Warning => "Check",
            Level::Error => "Error",
        };
        Line::styled(
            format!(" {prefix} · {}", inline(&notice.text)),
            level(notice.level),
        )
    } else {
        Line::default()
    };
    frame.render_widget(Paragraph::new(status), result);
    let result_key = if app.notice.is_some() {
        "  ! Result"
    } else {
        ""
    };
    let global_keys = match app.layer() {
        None => {
            let hints = match app.page {
                Page::Overview if app.service_allows(Action::Start) => "a Start   2 Logs".into(),
                Page::Overview if app.service_allows(Action::Restart) => {
                    "r Restart   x Stop   2 Logs".into()
                }
                Page::Overview if app.service_allows(Action::Stop) => "x Stop   2 Logs".into(),
                Page::Journal if app.systemd => {
                    "Space Pause/follow   / Filter   ↑↓ Scroll   ←→ Pan".into()
                }
                Page::Music => music::keys(app),
                Page::Console => console::keys(&app.console),
                _ => String::new(),
            };
            frame.render_widget(Paragraph::new(hints).wrap(Wrap { trim: true }), commands);
            format!("Tab Page  F5 Reload  ? Help  q Quit{result_key}")
        }
        Some(Layer::Form(_) | Layer::Confirm(_) | Layer::Help | Layer::Notice) => String::new(),
        Some(_) => format!("F5 Reload  ? Help{result_key}"),
    };
    frame.render_widget(Paragraph::new(global_keys), global);
}

fn page_keys(previous: bool, next: bool) -> &'static str {
    match (previous, next) {
        (true, true) => "   p/n Page",
        (true, false) => "   p Prev",
        (false, true) => "   n Next",
        _ => "",
    }
}

fn source_health(source: &SourceState) -> (&'static str, Style) {
    match (source.available, source.last_error.is_empty()) {
        (true, true) => ("Available", OK),
        (true, false) => ("Available · see last error", WARN),
        (false, _) => ("Unavailable", ERROR),
    }
}

fn panel(
    frame: &mut Frame<'_>,
    area: Rect,
    title: impl Into<Line<'static>>,
    lines: Vec<Line<'static>>,
    scroll: u16,
) {
    let inner = box_area(frame, area, title);
    wrapped(frame, inner, lines, scroll);
}

fn wrapped(frame: &mut Frame<'_>, area: Rect, lines: Vec<Line<'static>>, scroll: u16) {
    let paragraph = Paragraph::new(Text::from(lines)).wrap(Wrap { trim: false });
    let height = paragraph.line_count(area.width);
    let top = scroll.min(u16_len(height.saturating_sub(usize::from(area.height))));
    frame.render_widget(paragraph.scroll((top, 0)), area);
}

fn render_list(
    frame: &mut Frame<'_>,
    area: Rect,
    rows: impl IntoIterator<Item = Line<'static>>,
    selected: usize,
) {
    let mut state = ListState::default().with_selected(Some(selected));
    frame.render_stateful_widget(
        List::new(rows)
            .highlight_style(FOCUS)
            .highlight_symbol("› "),
        area,
        &mut state,
    );
}

fn box_area(frame: &mut Frame<'_>, area: Rect, title: impl Into<Line<'static>>) -> Rect {
    let block = Block::bordered()
        .title(title.into().style(STRONG))
        .padding(Padding::horizontal(1));
    let inner = block.inner(area);
    frame.render_widget(block, area);
    inner
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
