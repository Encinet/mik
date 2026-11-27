use super::super::{
    app::{App, Layer},
    form::{Confirmation, Field, Form},
    menu::{Choice, Menu},
    mouse::{Control, Targets},
};
use super::{
    ACCENT, ERROR, STRONG, Theme, inline, key_lines, level, safe, section, u16_len, wrapped,
};
use ratatui::{
    Frame,
    buffer::CellWidth,
    layout::{Constraint, Layout, Rect},
    style::Style,
    text::{Line, Span},
    widgets::{Block, BorderType, Clear, List, ListState, Padding, Paragraph, Wrap},
};

#[derive(Default)]
pub(super) struct Detail {
    pub lines: Vec<Line<'static>>,
    pub related: String,
    pub actions: String,
}

pub(super) fn render(
    frame: &mut Frame<'_>,
    area: Rect,
    app: &App,
    layer: &Layer,
    theme: Theme,
    scroll: u16,
    targets: &mut Targets,
) -> u16 {
    match layer {
        Layer::Form(form) => {
            let progress = app.pending.as_ref().filter(|_| app.form_submitted());
            render_form(
                frame,
                area,
                form,
                progress.map(|pending| pending.label),
                theme,
                targets,
            );
        }
        Layer::Confirm(confirm) => {
            return render_confirmation(frame, area, confirm, scroll, theme, targets);
        }
        Layer::Menu(menu) => render_menu(frame, area, menu, theme, targets),
        Layer::Online(online) => super::music::online(frame, area, online, scroll, targets),
        Layer::Versions(versions) => {
            super::music::versions(frame, area, app, versions, scroll, targets);
        }
        Layer::SourceTracks(tracks) => {
            super::music::source_tracks(frame, area, app, tracks, scroll, targets);
        }
        Layer::SourceCache(cache) => {
            super::cache::downloads(frame, area, app, cache, scroll, targets);
        }
        Layer::Lyrics(lyrics) => {
            let (lines, keys) = super::music::lyrics_detail(lyrics);
            let detail_view = Detail {
                lines,
                actions: keys,
                ..Detail::default()
            };
            return detail(frame, area, "Lyrics", detail_view, scroll, targets);
        }
        Layer::Track(track) => {
            return detail(
                frame,
                area,
                format!("Track · {}", inline(&track.title)),
                super::music::track_detail(track, app),
                scroll,
                targets,
            );
        }
        Layer::Source(source) => {
            let title = match source {
                super::super::catalog::SourceDetail::Registered(source) => {
                    format!("LX source · {}", inline(&source.name))
                }
                super::super::catalog::SourceDetail::Missing(_) => "LX source".into(),
            };
            return detail(
                frame,
                area,
                title,
                super::music::source_detail(source, app),
                scroll,
                targets,
            );
        }
        Layer::CacheEntry(entry) => {
            let title = entry.track.as_ref().map_or_else(
                || "Cached media".into(),
                |track| format!("Cached media · {}", inline(&track.title)),
            );
            return detail(
                frame,
                area,
                title,
                super::cache::detail(entry, app),
                scroll,
                targets,
            );
        }
        Layer::Help => {
            let view = Detail {
                lines: help(),
                ..Detail::default()
            };
            return detail(frame, area, "Controls", view, scroll, targets);
        }
        Layer::Notice => {
            if let Some(notice) = &app.notice {
                let lines = safe(&notice.text)
                    .lines()
                    .map(|line| Line::styled(line.to_owned(), level(notice.level)))
                    .collect();
                let view = Detail {
                    lines,
                    ..Detail::default()
                };
                return detail(frame, area, "Last operation", view, scroll, targets);
            }
        }
    }
    0
}

fn render_menu(
    frame: &mut Frame<'_>,
    area: Rect,
    menu: &Menu,
    theme: Theme,
    targets: &mut Targets,
) {
    let rows = u16_len(menu.items.len());
    let has_target = u16::from(menu.target.is_some());
    let inner = popup(frame, area, menu.title, rows + 4 + has_target, theme);
    let gap = u16::from(inner.height > rows + has_target + 1);
    let [target, actions, _, keys] = Layout::vertical([
        Constraint::Length(has_target),
        Constraint::Min(1),
        Constraint::Length(gap),
        Constraint::Length(1),
    ])
    .areas(inner);
    let page = usize::from(actions.height.max(1));
    if let Some(name) = &menu.target {
        let count = if menu.items.len() > page {
            format!("{}/{}", menu.selected + 1, menu.items.len())
        } else {
            String::new()
        };
        let [name_area, count_area] = Layout::horizontal([
            Constraint::Min(1),
            Constraint::Length(u16_len(count.len() + usize::from(!count.is_empty()))),
        ])
        .areas(target);
        frame.render_widget(Paragraph::new(inline(name)).style(STRONG), name_area);
        frame.render_widget(Paragraph::new(count).right_aligned(), count_area);
    }
    let actions_list = List::new(menu.items.iter().map(|item| {
        let style = if matches!(item.action, Choice::Confirm(_)) {
            ERROR
        } else {
            Style::new()
        };
        let mut line = Line::styled(item.label, style);
        if let Some(shortcut) = item.shortcut {
            let width = usize::from(actions.width.saturating_sub(4));
            let gap = width.saturating_sub(line.width()).max(2);
            line.spans.push(Span::raw(" ".repeat(gap)));
            line.spans.push(Span::styled(shortcut.to_string(), ACCENT));
        }
        line
    }))
    .highlight_symbol("› ")
    .highlight_spacing(ratatui::widgets::HighlightSpacing::Always)
    .highlight_style(theme.selection);
    let offset = (menu.selected / page * page).min(menu.items.len().saturating_sub(page));
    let mut state = ListState::default()
        .with_offset(offset)
        .with_selected(Some(menu.selected));
    frame.render_stateful_widget(actions_list, actions, &mut state);
    for (row, item) in (state.offset()..menu.items.len())
        .take(usize::from(actions.height))
        .enumerate()
    {
        targets.add(
            Rect::new(actions.x, actions.y + u16_len(row), actions.width, 1),
            Control::Menu(item),
        );
    }
    targets.scroll(actions, false);
    key_lines("↑↓ Choose   Enter Select   Esc Back", keys.width).render(frame, keys, targets);
}

fn render_form(
    frame: &mut Frame<'_>,
    area: Rect,
    form: &Form,
    progress: Option<&str>,
    theme: Theme,
    targets: &mut Targets,
) {
    let inner = popup(frame, area, form.title(), 13, theme);
    let keys = if progress.is_some() {
        key_lines("Esc Back", inner.width)
    } else {
        let fields = if form.fields.len() > 1 {
            "Tab Field   "
        } else {
            ""
        };
        key_lines(
            &format!("{fields}Enter {}   Esc Cancel", form.submit_label()),
            inner.width,
        )
    };
    let keys_height = u16_len(keys.len());
    let instructions = Paragraph::new(form.hint()).wrap(Wrap { trim: true });
    let target_height = u16::from(form.target().is_some());
    let field_height = u16_len(form.fields.len() * 2);
    let hint_height = u16_len(instructions.line_count(inner.width)).min(
        inner
            .height
            .saturating_sub(target_height + field_height + keys_height + 1),
    );
    let [target, hint, fields, problem, controls] = Layout::vertical([
        Constraint::Length(target_height),
        Constraint::Length(hint_height),
        Constraint::Length(field_height),
        Constraint::Min(1),
        Constraint::Length(keys_height),
    ])
    .areas(inner);
    if let Some(name) = form.target() {
        frame.render_widget(Paragraph::new(inline(name)).style(STRONG), target);
    }
    frame.render_widget(instructions, hint);
    for (idx, field) in form.fields.iter().enumerate() {
        let y = fields.y + u16_len(idx * 2);
        let selected = form.selected == idx;
        let label = if selected {
            format!("> {}", field.label)
        } else {
            format!("  {}", field.label)
        };
        let label_area = Rect::new(fields.x, y, fields.width, 1).intersection(fields);
        frame.render_widget(
            Paragraph::new(label).style(if selected { STRONG } else { Style::default() }),
            label_area,
        );
        let input = Rect::new(fields.x, y + 1, fields.width, 1).intersection(fields);
        let offset = input_field(
            frame,
            input,
            field,
            if selected {
                theme.selection
            } else {
                Style::new()
            },
            selected && progress.is_none(),
        );
        if progress.is_none() {
            targets.add(label_area, Control::Field(idx));
            targets.add(input, Control::Input { field: idx, offset });
        }
    }
    if let Some(label) = progress {
        frame.render_widget(Paragraph::new(format!("{label}…")).style(ACCENT), problem);
    } else if let Some(error) = &form.error {
        frame.render_widget(
            Paragraph::new(inline(error))
                .style(ERROR)
                .wrap(Wrap { trim: true }),
            problem,
        );
    }
    keys.render(frame, controls, targets);
}

pub(super) fn input_field(
    frame: &mut Frame<'_>,
    area: Rect,
    field: &Field,
    style: Style,
    show_cursor: bool,
) -> usize {
    let value = Line::raw(inline(&field.value));
    let current = usize::from(inline(&field.value[..field.cursor]).cell_width());
    let left = current.saturating_sub(usize::from(area.width.saturating_sub(1)));
    frame.render_widget(
        Paragraph::new(value)
            .style(style)
            .scroll((0, u16_len(left))),
        area,
    );
    if show_cursor && !area.is_empty() {
        frame.set_cursor_position((area.x + u16_len(current - left), area.y));
    }
    left
}

fn render_confirmation(
    frame: &mut Frame<'_>,
    area: Rect,
    confirm: &Confirmation,
    scroll: u16,
    theme: Theme,
    targets: &mut Targets,
) -> u16 {
    let inner = popup(frame, area, confirm.work.label(), 12, theme);
    let [content, buttons, tip] = Layout::vertical([
        Constraint::Min(1),
        Constraint::Length(1),
        Constraint::Length(1),
    ])
    .areas(inner);
    let target = safe(&confirm.target);
    let mut lines = vec![Line::styled("Target", STRONG)];
    lines.extend(target.lines().map(|line| Line::raw(line.to_owned())));
    lines.extend([Line::default(), Line::raw(confirm.impact)]);
    let scroll_limit = wrapped(frame, content, lines, scroll);
    targets.scroll(content, false);
    let choices = Line::from(vec![
        Span::styled(
            " Cancel ",
            if confirm.accepted {
                Style::default()
            } else {
                theme.selection
            },
        ),
        Span::raw("  "),
        Span::styled(
            format!(" {} ", confirm.work.label()),
            if confirm.accepted {
                theme.selection
            } else {
                Style::default()
            },
        ),
    ]);
    let mut x = buttons.x;
    for (span, accepted) in choices.spans.iter().zip([Some(false), None, Some(true)]) {
        let width = u16_len(span.width());
        if let Some(accepted) = accepted {
            targets.add(
                Rect::new(x, buttons.y, width, 1).intersection(buttons),
                Control::Confirm(accepted),
            );
        }
        x += width;
    }
    frame.render_widget(Paragraph::new(choices), buttons);
    key_lines("↑↓ Read   ←→ Choose   Enter   Esc", tip.width).render(frame, tip, targets);
    scroll_limit
}

fn help() -> Vec<Line<'static>> {
    let groups: &[(&str, &[&str])] = &[
        (
            "Navigation",
            &[
                "1 Overview   2 Logs   3 Music   4 Console",
                "Tab / Shift+Tab switch pages   F5 refresh   ! last result",
                "Esc back   ? / F1 help   q quit from a page   Ctrl+C quit",
            ],
        ),
        (
            "Mouse",
            &[
                "Click tabs, labeled actions and fields. Click a music row to select it, or double-click to open it.",
                "Wheel scrolls the pane under the pointer. Hold Shift to select terminal text.",
                "Start with --no-mouse to use the terminal's own mouse controls.",
            ],
        ),
        (
            "Overview",
            &["m service actions   2 service logs   ↑↓ scroll"],
        ),
        (
            "Logs and Console",
            &[
                "Space pause/follow   / filter   Home first line   End follow",
                "↑↓ scroll   PgUp/PgDn page   ←→ pan",
                "[/] console provider   : command   p/n older/newer console logs",
            ],
        ),
        (
            "Music lists",
            &[
                "↑↓ select   Home/End first/last   Enter details   p/n page where available",
                "[ / ] switch between Tracks, Sources and Cache",
                "Tracks    / search   a import   m more actions",
                "Sources   a add   m source actions   f source ID",
                "Cache     m find version or clear idle cache",
            ],
        ),
        (
            "Music details",
            &[
                "Track        v media versions   s registered source   a download source",
                "Versions     Enter lyrics   b cache for downloaded media",
                "Lyrics       l set override   c clear override",
                "Source       t registered tracks   b cached downloads",
                "Cache entry  t track   s download source   y lyrics",
                "Related links are clickable. m opens the actions menu.",
            ],
        ),
        (
            "Dialogs",
            &[
                "Tab field   Enter submit   Esc back   Ctrl+U clear field",
                "↑↓ choose action   Enter run   Esc back",
                "Letters shown in action menus also work from their page or detail",
                "←→ or Tab choose   Enter select   Esc cancel",
            ],
        ),
    ];
    let mut lines = Vec::new();
    for &(title, help) in groups {
        if !lines.is_empty() {
            lines.push(Line::default());
        }
        lines.push(Line::styled(title, ACCENT));
        lines.extend(help.iter().copied().map(Line::raw));
    }
    lines
}

fn detail(
    frame: &mut Frame<'_>,
    area: Rect,
    title: impl Into<Line<'static>>,
    view: Detail,
    scroll: u16,
    targets: &mut Targets,
) -> u16 {
    let inner = section(frame, area, title);
    let body = body_with_keys(frame, inner, &view.actions, "↑↓ Scroll   Esc Back", targets);
    let body = if view.related.is_empty() {
        body
    } else {
        let links = key_lines(&format!("Related   {}", view.related), body.width);
        let [toolbar, details] =
            Layout::vertical([Constraint::Length(u16_len(links.len())), Constraint::Min(1)])
                .areas(body);
        links.render(frame, toolbar, targets);
        details
    };
    targets.scroll(body, false);
    wrapped(frame, body, view.lines, scroll)
}

pub(super) fn list(
    frame: &mut Frame<'_>,
    area: Rect,
    title: String,
    notes: Vec<Line<'static>>,
    keys: &str,
    targets: &mut Targets,
    render: impl FnOnce(&mut Frame<'_>, Rect, &mut Targets),
) {
    let inner = section(frame, area, title);
    let body = body_with_keys(frame, inner, keys, "↑↓ Select   Esc Back", targets);
    let notes = Paragraph::new(notes).wrap(Wrap { trim: true });
    let height = u16_len(notes.line_count(body.width)).min(body.height / 2);
    let [meta, list] =
        Layout::vertical([Constraint::Length(height), Constraint::Min(1)]).areas(body);
    frame.render_widget(notes, meta);
    render(frame, list, targets);
}

fn body_with_keys(
    frame: &mut Frame<'_>,
    area: Rect,
    keys: &str,
    navigation: &str,
    targets: &mut Targets,
) -> Rect {
    let footer = key_lines(&format!("{keys}   {navigation}"), area.width);
    let height = u16_len(footer.len());
    let [body, keys] =
        Layout::vertical([Constraint::Min(1), Constraint::Length(height)]).areas(area);
    footer.render(frame, keys, targets);
    body
}

fn popup(
    frame: &mut Frame<'_>,
    area: Rect,
    title: &'static str,
    height: u16,
    theme: Theme,
) -> Rect {
    let width = (area.width * 4 / 5).max(40).min(area.width).min(76);
    let height = area.height.min(height);
    let area = Rect::new(
        area.x + (area.width - width) / 2,
        area.y + (area.height - height) / 2,
        width,
        height,
    );
    frame.render_widget(Clear, area);
    let block = Block::bordered()
        .title(Line::styled(format!(" {title} "), STRONG))
        .border_type(BorderType::Rounded)
        .style(theme.chrome)
        .border_style(theme.outline)
        .padding(Padding::horizontal(1));
    let inner = block.inner(area);
    frame.render_widget(block, area);
    inner
}
