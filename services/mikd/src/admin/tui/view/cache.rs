use super::{CollectionView, OK, STRONG, WARN, collection, dialog, inline, page_keys, pair};
use crate::{
    admin::tui::{
        app::App,
        catalog::{BySource, Collection},
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

pub(super) fn render(frame: &mut Frame<'_>, area: Rect, app: &App) {
    let cache = &app.music.cache;
    let ready = app.ready(Collection::Cache);
    let area = if let Some(usage) = cache.usage.as_ref().filter(|_| ready) {
        let mut lines = vec![Line::raw(format!(
            "Entries {} · Quota {} / {}",
            usage.entries,
            size(usage.charged_bytes),
            size(usage.limit_bytes),
        ))];
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
    let list = &cache.list;
    let empty = if list.known {
        "No remote music cached."
    } else {
        "Loading media cache…"
    };
    collection(
        frame,
        area,
        format!("Remote media cache · Page {}", list.page()),
        &CollectionView {
            unavailable: !ready,
            problem: list.problem.as_deref(),
            empty,
            selected: list.selected,
        },
        list.entries.iter().map(row),
    );
}

pub(super) fn downloads(
    frame: &mut Frame<'_>,
    area: Rect,
    app: &App,
    cache: &BySource<CacheEntry>,
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
        |frame, area| {
            collection(
                frame,
                area,
                "Downloads",
                &CollectionView {
                    unavailable: !app.ready(Collection::Cache),
                    problem: list.problem.as_deref(),
                    empty: if list.known {
                        "No cached downloads from this source."
                    } else {
                        "Loading cached downloads…"
                    },
                    selected: list.selected,
                },
                list.entries.iter().map(row),
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
    let source = entry.source.as_ref().map_or("Resolving source", |source| {
        source.name.as_deref().unwrap_or(&source.id)
    });
    Line::from(vec![
        Span::styled(state, style),
        Span::raw(format!(
            "  ·  {}  ·  {}  ·  {}",
            inline(title),
            inline(source),
            size(entry.bytes)
        )),
    ])
}

pub(super) fn detail(entry: &CacheEntry, app: &App) -> (Vec<Line<'static>>, String) {
    let (state, style) = status(entry.state);
    let mut lines = vec![
        Line::styled(
            entry
                .track
                .as_ref()
                .map_or_else(|| "Unknown track".into(), |track| inline(&track.title)),
            STRONG,
        ),
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
    let mut keys = Vec::new();
    if entry.track.is_some() && app.ready(Collection::Library) {
        keys.push("t Track");
    }
    if entry.source.is_some() {
        if app.ready(Collection::Sources) {
            keys.push("s Audio source");
        }
        keys.push("y Lyrics");
    }
    let mut actions = keys.join("   ");
    if entry.state == CacheState::Idle && app.ready(Collection::Cache) {
        if !actions.is_empty() {
            actions.push('\n');
        }
        actions.push_str("x Evict version");
    }
    (lines, actions)
}

pub(super) fn status(state: CacheState) -> (&'static str, Style) {
    match state {
        CacheState::Preparing => ("Preparing", STRONG),
        CacheState::Downloading => ("Downloading", STRONG),
        CacheState::InUse => ("In use", WARN),
        CacheState::Idle => ("Idle", OK),
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
