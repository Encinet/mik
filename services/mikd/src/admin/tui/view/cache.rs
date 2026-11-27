use super::{
    ACCENT, CollectionView, STRONG, WARN, collection_rows, dialog, inline, music, page_keys, pair,
    section,
};
use crate::{
    admin::tui::{
        app::{App, cache_entry_actions},
        catalog::{BySource, Collection},
        mouse::Targets,
    },
    domains::music::{CacheEntry, CacheState},
};
use ratatui::{
    Frame,
    layout::{Constraint, Layout, Rect},
    style::Style,
    text::{Line, Span},
    widgets::Paragraph,
};
use time::{OffsetDateTime, format_description::well_known::Rfc3339};

pub(super) fn render(frame: &mut Frame<'_>, area: Rect, app: &App, targets: &mut Targets) {
    let cache = &app.music.cache;
    let ready = app.ready(Collection::Cache);
    let list = &cache.list;
    let area = section(
        frame,
        area,
        format!("Remote media cache · Page {}", list.page()),
    );
    let area = if let Some(usage) = cache.usage.as_ref().filter(|_| ready) {
        let mut lines = vec![Line::from(vec![
            Span::styled("Quota", STRONG),
            Span::raw(format!(
                "  {} / {}   |   {} entries",
                size(usage.charged_bytes),
                size(usage.limit_bytes),
                usage.entries
            )),
        ])];
        if usage.retired_bytes > 0 {
            lines.push(Line::raw(format!(
                "Quota held by retired versions · {}",
                size(usage.retired_bytes),
            )));
        }
        let [summary, list] = Layout::vertical([
            Constraint::Length(super::u16_len(lines.len())),
            Constraint::Min(1),
        ])
        .areas(area);
        frame.render_widget(Paragraph::new(lines), summary);
        list
    } else {
        area
    };
    let empty = if list.known {
        "No remote music cached."
    } else {
        "Loading media cache…"
    };
    collection_rows(
        frame,
        area,
        &CollectionView {
            unavailable: !ready,
            problem: list.problem.as_deref(),
            empty,
            selected: list.selected,
            scroll: app.music.scroll(),
        },
        list.entries.iter().map(row),
        targets,
    );
}

pub(super) fn downloads(
    frame: &mut Frame<'_>,
    area: Rect,
    app: &App,
    cache: &BySource<CacheEntry>,
    scroll: u16,
    targets: &mut Targets,
) {
    let list = &cache.list;
    let enter = if list.entries.is_empty() {
        ""
    } else {
        "Enter Cache entry"
    };
    let keys = format!("{enter}{}", page_keys(list.page() > 1, list.can_next()));
    dialog::list(
        frame,
        area,
        format!("Cached downloads · Page {}", list.page()),
        vec![Line::raw(inline(&cache.source))],
        keys.trim_start(),
        targets,
        |frame, area, targets| {
            collection_rows(
                frame,
                area,
                &CollectionView {
                    unavailable: !app.ready(Collection::Cache),
                    problem: list.problem.as_deref(),
                    empty: if list.known {
                        "No cached downloads from this source."
                    } else {
                        "Loading cached downloads…"
                    },
                    selected: list.selected,
                    scroll,
                },
                list.entries.iter().map(row),
                targets,
            );
        },
    );
}

fn row(entry: &CacheEntry) -> Line<'static> {
    let (state, style) = status(entry.state);
    let title = entry
        .track
        .as_ref()
        .map_or(entry.version.as_str(), |track| &track.title);
    Line::from(vec![
        Span::styled(state, style),
        Span::raw(format!("  {}  {}", size(entry.bytes), inline(title))),
    ])
}

pub(super) fn detail(entry: &CacheEntry, app: &App) -> dialog::Detail {
    let (state, style) = status(entry.state);
    let mut lines = vec![
        pair("Media version", &entry.version),
        Line::styled(state, style),
    ];
    if let Some(source) = &entry.source {
        if let Some(name) = &source.name {
            lines.push(pair("Audio source", name));
            lines.push(pair("Source ID", &source.id));
        } else {
            lines.push(pair("Audio source", &source.id));
        }
    }
    lines.push(pair("Channel", &entry.channel));
    if let Some(track) = &entry.track {
        lines.push(pair("Track ID", &track.id));
        if track.retired {
            lines.push(Line::styled("Track retired", WARN));
        }
        for (label, value) in [("Artist", &track.artist), ("Album", &track.album)] {
            if !value.is_empty() {
                lines.push(pair(label, value));
            }
        }
    }
    lines.push(pair("Media size", &format!("{} bytes", entry.bytes)));
    if let Some(content_type) = &entry.content_type {
        lines.push(pair("Content type", content_type));
    }
    if let Ok(instant) = OffsetDateTime::from_unix_timestamp(entry.last_used_unix)
        && let Ok(timestamp) = instant.format(&Rfc3339)
    {
        lines.push(pair("Last access", &timestamp));
    }
    music::item_detail(
        lines,
        &cache_entry_actions(
            entry,
            app.ready(Collection::Library),
            app.ready(Collection::Sources),
            app.ready(Collection::Cache),
        ),
    )
}

pub(super) fn status(state: CacheState) -> (&'static str, Style) {
    match state {
        CacheState::Preparing => ("Preparing", ACCENT),
        CacheState::Downloading => ("Downloading", ACCENT),
        CacheState::InUse => ("In use", ACCENT),
        CacheState::Idle => ("Idle", Style::new()),
    }
}

fn size(bytes: u64) -> String {
    for (unit, suffix) in [(1 << 30, "GiB"), (1 << 20, "MiB"), (1 << 10, "KiB")] {
        if bytes >= unit {
            let whole = bytes / unit;
            let tenths = (bytes % unit) * 10 / unit;
            return if tenths == 0 {
                format!("{whole} {suffix}")
            } else {
                format!("{whole}.{tenths} {suffix}")
            };
        }
    }
    format!("{bytes} B")
}
