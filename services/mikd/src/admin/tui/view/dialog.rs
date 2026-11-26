use super::super::{
    app::{App, Layer},
    form::{Confirmation, Form},
};
use super::{ERROR, FOCUS, STRONG, box_area, inline, level, safe, u16_len, wrapped};
use ratatui::{
    Frame,
    layout::{Constraint, Layout, Rect},
    style::Style,
    text::{Line, Span},
    widgets::{Clear, Paragraph, Wrap},
};

pub(super) fn render(frame: &mut Frame<'_>, area: Rect, app: &App) {
    if let Some(layer) = app.layer() {
        let scroll = app.layer_scroll();
        match layer {
            Layer::Form(form) => {
                let progress = app.pending.as_ref().filter(|_| app.form_submitted());
                render_form(frame, area, form, progress.map(|pending| pending.label));
            }
            Layer::Confirm(confirm) => render_confirmation(frame, area, confirm, scroll),
            Layer::Online(online) => super::music::online(frame, area, online),
            Layer::Versions(versions) => super::music::versions(frame, area, app, versions),
            Layer::SourceTracks(tracks) => super::music::source_tracks(frame, area, app, tracks),
            Layer::SourceCache(cache) => super::cache::downloads(frame, area, app, cache),
            Layer::Lyrics(lyrics) => {
                let (lines, keys) = super::music::lyrics_detail(lyrics);
                detail(frame, area, "Lyrics", lines, &keys, scroll);
            }
            Layer::Track(track) => {
                let (lines, keys) = super::music::track_detail(track, app);
                detail(frame, area, "Track", lines, &keys, scroll);
            }
            Layer::Source(source) => {
                let (lines, keys) = super::music::source_detail(source, app);
                detail(frame, area, "Source", lines, &keys, scroll);
            }
            Layer::CacheEntry(entry) => {
                let (lines, keys) = super::cache::detail(entry, app);
                detail(frame, area, "Cached media", lines, &keys, scroll);
            }
            Layer::Help => detail(frame, area, "Keyboard shortcuts", help(), "", scroll),
            Layer::Notice => {
                if let Some(notice) = &app.notice {
                    let lines = safe(&notice.text)
                        .lines()
                        .map(|line| Line::styled(line.to_owned(), level(notice.level)))
                        .collect();
                    detail(frame, area, "Last operation", lines, "", scroll);
                }
            }
        }
    }
}

fn render_form(frame: &mut Frame<'_>, area: Rect, form: &Form, progress: Option<&str>) {
    let title = form.target().map_or_else(
        || Line::raw(form.title()),
        |target| Line::raw(format!("{} · {}", form.title(), inline(&target))),
    );
    let inner = popup(frame, area, title, 13);
    let [hint, fields, problem, controls] = Layout::vertical([
        Constraint::Length(2),
        Constraint::Length(u16_len(form.fields.len() * 2)),
        Constraint::Min(1),
        Constraint::Length(1),
    ])
    .areas(inner);
    frame.render_widget(Paragraph::new(form.hint()).wrap(Wrap { trim: true }), hint);
    for (idx, field) in form.fields.iter().enumerate() {
        let y = fields.y + u16_len(idx * 2);
        let selected = form.selected == idx;
        let label = if selected {
            format!("› {}", field.label)
        } else {
            format!("  {}", field.label)
        };
        frame.render_widget(
            Paragraph::new(label).style(if selected { STRONG } else { Style::default() }),
            Rect::new(fields.x, y, fields.width, 1),
        );
        let input = Rect::new(fields.x, y + 1, fields.width, 1);
        let value = Line::raw(inline(&field.value));
        let current = Line::raw(inline(&field.value[..field.cursor])).width();
        let left = current.saturating_sub(usize::from(input.width.saturating_sub(1)));
        frame.render_widget(
            Paragraph::new(value)
                .style(if selected { FOCUS } else { Style::default() })
                .scroll((0, u16_len(left))),
            input,
        );
        if selected && input.width > 0 && progress.is_none() {
            frame.set_cursor_position((input.x + u16_len(current - left), input.y));
        }
    }
    if let Some(label) = progress {
        frame.render_widget(Paragraph::new(format!("{label}…")).style(STRONG), problem);
    } else if let Some(error) = &form.error {
        frame.render_widget(
            Paragraph::new(inline(error))
                .style(ERROR)
                .wrap(Wrap { trim: true }),
            problem,
        );
    }
    let keys = if progress.is_some() {
        "Esc Back"
    } else if form.fields.len() > 1 {
        "Tab Field   Enter Submit   Esc Back"
    } else {
        "Enter Submit   Esc Back"
    };
    frame.render_widget(Paragraph::new(keys), controls);
}

fn render_confirmation(frame: &mut Frame<'_>, area: Rect, confirm: &Confirmation, scroll: u16) {
    let inner = popup(frame, area, confirm.work.label(), 12);
    let [impact, target, buttons, tip] = Layout::vertical([
        Constraint::Length(4),
        Constraint::Min(1),
        Constraint::Length(1),
        Constraint::Length(1),
    ])
    .areas(inner);
    frame.render_widget(
        Paragraph::new(confirm.impact).wrap(Wrap { trim: true }),
        impact,
    );
    let lines: Vec<_> = safe(&confirm.target)
        .lines()
        .map(|line| Line::styled(line.to_owned(), STRONG))
        .collect();
    wrapped(frame, target, lines, scroll);
    let choices = Line::from(vec![
        Span::styled(
            " Cancel ",
            if confirm.accepted {
                Style::default()
            } else {
                FOCUS
            },
        ),
        Span::raw("  "),
        Span::styled(
            format!(" {} ", confirm.work.label()),
            if confirm.accepted {
                FOCUS
            } else {
                Style::default()
            },
        ),
    ]);
    frame.render_widget(Paragraph::new(choices), buttons);
    frame.render_widget(Paragraph::new("↑↓ Target   ←→ Choose   Enter/Esc"), tip);
}

fn help() -> Vec<Line<'static>> {
    [
        "1 Overview   2 Logs   3 Music   6 Console",
        "Tab / Shift+Tab page · F5 reload · ! result · q quit",
        "",
        "Overview   a start · r restart · x stop",
        "Logs       Space pause/follow · / filter · End follow",
        "           ↑↓ scroll · PgUp/PgDn page · ←→ pan",
        "Music      3 Library   4 Sources   5 Cache",
        "           Enter details · p/n previous/next · Esc back",
        "Library    / registered search · d online · o collection",
        "           a import · f track ID · y lyrics by version",
        "           l/c set/clear override by version",
        "Track      v versions · b cache · y lyrics",
        "           s registered source · a audio source if different",
        "           r replace · x retire · u restore · d prepare cache",
        "Versions   Enter lyrics · b cache for downloaded media",
        "Lyrics     l set override · c clear override",
        "Sources    f source ID · a import · r refresh all",
        "Source     t registered tracks · b cached downloads",
        "           r refresh subscription · x remove subscription",
        "Cache      f media version · c clear idle",
        "Cache entry  t track · s audio source · y lyrics · x evict",
        "Console    [ / ] provider · : command · p/n older/newer logs",
        "           Space pause/follow · / filter · End follow",
        "Forms      Tab field · Enter submit · Esc back · Ctrl+U clear",
        "Confirm    ←→ choose · Enter select · Esc back",
    ]
    .into_iter()
    .map(Line::raw)
    .collect()
}

fn detail(
    frame: &mut Frame<'_>,
    area: Rect,
    title: &str,
    lines: Vec<Line<'static>>,
    keys: &str,
    scroll: u16,
) {
    let inner = popup(frame, area, title.to_owned(), 20);
    let body = body_with_keys(frame, inner, keys, "↑↓ Scroll   Esc Back");
    wrapped(frame, body, lines, scroll);
}

pub(super) fn list(
    frame: &mut Frame<'_>,
    area: Rect,
    title: String,
    notes: Vec<Line<'static>>,
    keys: &str,
    render: impl FnOnce(&mut Frame<'_>, Rect),
) {
    let inner = popup(frame, area, title, 24);
    let body = body_with_keys(frame, inner, keys, "↑↓ Select   Esc Back");
    let notes = Paragraph::new(notes).wrap(Wrap { trim: true });
    let height = u16_len(notes.line_count(body.width)).min(body.height / 2);
    let [meta, list] =
        Layout::vertical([Constraint::Length(height), Constraint::Min(1)]).areas(body);
    frame.render_widget(notes, meta);
    render(frame, list);
}

fn body_with_keys(frame: &mut Frame<'_>, area: Rect, keys: &str, navigation: &str) -> Rect {
    let labels = if keys.is_empty() {
        navigation.to_owned()
    } else {
        format!("{keys}\n{navigation}")
    };
    let footer = Paragraph::new(labels).wrap(Wrap { trim: true });
    let height = u16_len(footer.line_count(area.width)).min(area.height / 2);
    let [body, keys] =
        Layout::vertical([Constraint::Min(1), Constraint::Length(height)]).areas(area);
    frame.render_widget(footer, keys);
    body
}

fn popup(frame: &mut Frame<'_>, area: Rect, title: impl Into<Line<'static>>, height: u16) -> Rect {
    let width = area.width.saturating_sub(4).min(78);
    let height = area.height.saturating_sub(1).min(height);
    let area = Rect::new(
        area.x + (area.width - width) / 2,
        area.y + (area.height - height) / 2,
        width,
        height,
    );
    frame.render_widget(Clear, area);
    box_area(frame, area, title)
}
