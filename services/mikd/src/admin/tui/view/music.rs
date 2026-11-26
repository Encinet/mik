use super::{
    CollectionView, FOCUS, STRONG, WARN, cache, collection, dialog, inline, page_keys, pair,
    source_health,
};
use crate::{
    admin::tui::{
        app::App,
        catalog::{BySource, Collection, Online, SourceDetail, Versions, separate_audio_source},
    },
    domains::music::{
        CatalogQuery, CatalogTrack, InspectedMedia, LocalTrack, LyricsInspection, SourceState,
        TrackInspection,
    },
};
use ratatui::{
    Frame,
    layout::{Constraint, Layout, Rect},
    text::{Line, Span},
    widgets::Tabs,
};

pub(super) fn render(frame: &mut Frame<'_>, area: Rect, app: &App) {
    let [sections, content] =
        Layout::vertical([Constraint::Length(1), Constraint::Min(1)]).areas(area);
    let labels = Collection::ALL.map(|page| format!("{} {}", page.index() + 3, page.name()));
    frame.render_widget(
        Tabs::new(labels)
            .select(app.music.page.index())
            .highlight_style(FOCUS)
            .padding("", "")
            .divider("  "),
        sections,
    );
    match app.music.page {
        Collection::Library => library(frame, content, app),
        Collection::Sources => sources(frame, content, app),
        Collection::Cache => cache::render(frame, content, app),
    }
}

pub(super) fn keys(app: &App) -> String {
    if !app.ready(app.music.page) {
        return String::new();
    }
    match app.music.page {
        Collection::Library => {
            let list = &app.music.library.list;
            let page = page_keys(list.page() > 1, list.can_next());
            let online = if app.ready(Collection::Sources) {
                "d Online   o Collection   "
            } else {
                ""
            };
            format!("Enter Track   / Search   f ID   a Import\n{online}y Lyrics{page}")
        }
        Collection::Sources => "Enter Source   f ID   a Import   r Refresh".into(),
        Collection::Cache => {
            let list = &app.music.cache.list;
            let page = page_keys(list.page() > 1, list.can_next());
            format!("Enter Cache entry   f Version{page}\nc Clear idle")
        }
    }
}

fn library(frame: &mut Frame<'_>, area: Rect, app: &App) {
    let library = &app.music.library;
    let list = &library.list;
    let mut title = format!("Music catalog · Page {}", list.page());
    if !library.keyword.is_empty() {
        title.push_str(" · ");
        title.push_str(&inline(&library.keyword));
    }
    let empty = if list.known {
        "No tracks on this page."
    } else {
        "Loading library…"
    };
    collection(
        frame,
        area,
        title,
        &CollectionView {
            unavailable: !app.ready(Collection::Library),
            problem: list.problem.as_deref(),
            empty,
            selected: list.selected,
        },
        list.entries.iter().map(track_row),
    );
}

fn sources(frame: &mut Frame<'_>, area: Rect, app: &App) {
    let sources = &app.music.sources;
    let rows = sources.entries.iter().map(|source| {
        let (state, style) = source_health(source);
        Line::from(vec![
            Span::raw(inline(&source.name)),
            Span::raw("  ·  "),
            Span::styled(state, style),
            Span::raw(if source.subscription {
                "  [subscription]"
            } else {
                "  [local script]"
            }),
        ])
    });
    let empty = if sources.known {
        "No registered sources."
    } else {
        "Loading sources…"
    };
    collection(
        frame,
        area,
        "LX sources",
        &CollectionView {
            unavailable: !app.ready(Collection::Sources),
            problem: sources.problem.as_deref(),
            empty,
            selected: sources.selected,
        },
        rows,
    );
}

pub(super) fn track_row(track: &CatalogTrack) -> Line<'static> {
    let kind = if track.local { "Local" } else { "Remote" };
    let mut row = vec![Span::raw(inline(&track.title))];
    if !track.artist.is_empty() {
        row.push(Span::raw(format!("  ·  {}", inline(&track.artist))));
    }
    row.push(Span::raw(format!("  [{kind}]")));
    if track.retired {
        row.push(Span::styled("  Retired", WARN));
    }
    Line::from(row)
}

pub(super) fn online(frame: &mut Frame<'_>, area: Rect, online: &Online) {
    let page = online.current();
    let title = match &online.query {
        CatalogQuery::Search(text) => format!("Online search · {}", inline(text)),
        CatalogQuery::Collection(_) => {
            format!(
                "Collection · {}",
                inline(page.title.as_deref().unwrap_or("Preview"))
            )
        }
        CatalogQuery::Stored(_) | CatalogQuery::RegisteredBySource(_) => "Registered tracks".into(),
    };
    let mut notes = Vec::new();
    if page.partial {
        notes.push(Line::styled(
            "Partial results · Some sources or results were unavailable",
            WARN,
        ));
    }
    if let Some(error) = &online.problem {
        notes.push(Line::styled(
            format!("{} · F5 Restart search", inline(error)),
            WARN,
        ));
    }
    dialog::list(
        frame,
        area,
        format!("{title} · Page {}", online.page + 1),
        notes,
        &format!(
            "Enter Track{}   F5 Restart search",
            page_keys(online.page > 0, online.can_next())
        ),
        |frame, area| {
            collection(
                frame,
                area,
                "Tracks",
                &CollectionView {
                    unavailable: false,
                    problem: None,
                    empty: "No tracks on this page.",
                    selected: online.selected,
                },
                page.entries.iter().map(track_row),
            );
        },
    );
}

pub(super) fn source_tracks(
    frame: &mut Frame<'_>,
    area: Rect,
    app: &App,
    tracks: &BySource<CatalogTrack>,
) {
    let list = &tracks.list;
    let enter = if list.entries.is_empty() {
        ""
    } else {
        "Enter Track"
    };
    let keys = format!("{enter}{}", page_keys(list.page() > 1, list.can_next()));
    dialog::list(
        frame,
        area,
        format!("Registered tracks · Page {}", list.page()),
        vec![Line::raw(inline(&tracks.source))],
        keys.trim_start(),
        |frame, area| {
            collection(
                frame,
                area,
                "Tracks",
                &CollectionView {
                    unavailable: !app.ready(Collection::Library),
                    problem: list.problem.as_deref(),
                    empty: if list.known {
                        "No tracks registered by this source."
                    } else {
                        "Loading registered tracks…"
                    },
                    selected: list.selected,
                },
                list.entries.iter().map(track_row),
            );
        },
    );
}

pub(super) fn versions(frame: &mut Frame<'_>, area: Rect, app: &App, versions: &Versions) {
    let list = &versions.list;
    let rows = list.entries.iter().map(|entry| {
        let mut row = vec![Span::raw(inline(&entry.version))];
        if versions.latest.as_deref() == Some(&entry.version) {
            row.push(Span::styled("  Latest", STRONG));
        }
        if let Some(format) = &entry.format {
            row.push(Span::raw(format!("  {}", inline(format))));
        }
        if let Some(source) = &entry.source {
            row.push(Span::raw(format!("  {}", inline(source))));
        }
        row.push(Span::raw(format!("  {} B", entry.bytes)));
        Line::from(row)
    });
    let mut actions = Vec::new();
    if let Some(selected) = list.entries.get(list.selected) {
        if selected.lyrics_available() {
            actions.push("Enter Lyrics");
        }
        if selected.cached && app.ready(Collection::Cache) {
            actions.push("b Cache");
        }
    }
    let keys = format!(
        "{}{}",
        actions.join("   "),
        page_keys(list.page() > 1, list.can_next())
    );
    dialog::list(
        frame,
        area,
        format!("Media versions · Page {}", list.page()),
        vec![Line::raw(inline(&versions.title))],
        keys.trim_start(),
        |frame, area| {
            collection(
                frame,
                area,
                "Versions",
                &CollectionView {
                    unavailable: false,
                    problem: list.problem.as_deref(),
                    empty: "No recorded media versions.",
                    selected: list.selected,
                },
                rows,
            );
        },
    );
}

pub(super) fn track_detail(track: &TrackInspection, app: &App) -> (Vec<Line<'static>>, String) {
    let mut lines = vec![
        Line::styled(inline(&track.title), STRONG),
        pair("Track ID", &track.id),
        pair("State", &track.state),
    ];
    if let Some(source) = &track.registered_source {
        lines.push(pair("Registered source", source));
    }
    if let Some(source) = track.audio_source() {
        lines.push(pair("Audio source", source));
    }
    for (label, value) in [("Artist", &track.artist), ("Album", &track.album)] {
        if !value.is_empty() {
            lines.push(pair(label, value));
        }
    }
    if let Some(ms) = track.duration_ms {
        lines.push(pair(
            "Duration",
            &format!("{}:{:02}", ms / 60_000, ms / 1000 % 60),
        ));
    }
    for (label, value) in [
        ("Availability", &track.availability),
        ("Source", &track.source),
        ("Channel", &track.channel),
    ] {
        if let Some(value) = value {
            lines.push(pair(label, value));
        }
    }
    if let Some(media) = &track.media {
        lines.push(pair("Version", &media.version()));
        match media {
            InspectedMedia::Local(media) => local_media(&mut lines, media),
            InspectedMedia::Cached { state, .. } => {
                let (state, style) = cache::status(*state);
                lines.push(Line::styled(state, style));
            }
        }
    } else {
        lines.push(Line::raw("No media version available."));
    }
    if let Some(failure) = &track.last_failure {
        lines.push(Line::styled(
            format!("Last playback failure  {}", inline(failure)),
            WARN,
        ));
    }
    let mut related = vec!["v Versions"];
    if matches!(track.media, Some(InspectedMedia::Cached { .. })) && app.ready(Collection::Cache) {
        related.push("b Cache");
    }
    if track.lyrics_version().is_some() {
        related.push("y Lyrics");
    }
    let mut keys = vec![related.join("   ")];
    if app.ready(Collection::Sources) {
        let mut sources = Vec::new();
        if let Some(registered) = track.registered_source.as_deref() {
            sources.push(if track.audio_source() == Some(registered) {
                "s Registered/audio source"
            } else {
                "s Registered source"
            });
        }
        if separate_audio_source(track).is_some() {
            sources.push("a Audio source");
        }
        if !sources.is_empty() {
            keys.push(sources.join("   "));
        }
    }
    let mut management = Vec::new();
    if track.active() {
        if track.local() {
            management.push("r Replace");
        } else if app.ready(Collection::Sources) && app.ready(Collection::Cache) {
            management.push("d Prepare cache");
        }
        management.push("x Retire");
    } else {
        management.push("u Restore");
    }
    keys.push(management.join("   "));
    (lines, keys.join("\n"))
}

fn local_media(lines: &mut Vec<Line<'static>>, media: &LocalTrack) {
    if !media.original_author.is_empty() {
        lines.push(pair("Original author", &media.original_author));
    }
    lines.push(pair("Format", &media.format));
    if let Some(rate) = media.sample_rate_hz {
        lines.push(pair("Sample rate", &format!("{rate} Hz")));
    }
    lines.push(pair("Media size", &format!("{} bytes", media.bytes)));
    if let Some(notes) = &media.notes {
        let seek = if notes.seekable {
            "seekable"
        } else {
            "seeking disabled"
        };
        lines.push(pair(
            "Note score",
            &format!("{} bytes · {seek}", notes.bytes),
        ));
    }
}

fn registered_source(source: &SourceState) -> Vec<Line<'static>> {
    let (health, style) = source_health(source);
    let kind = if source.subscription {
        "Subscription"
    } else {
        "Local script"
    };
    let mut lines = vec![
        Line::styled(inline(&source.name), STRONG),
        pair("Source ID", &source.id),
        pair("Type", kind),
        Line::styled(health, style),
    ];
    if !source.last_error.is_empty() {
        lines.push(Line::styled(
            format!("Last error  {}", inline(&source.last_error)),
            WARN,
        ));
    }
    if !source.actions.is_empty() {
        lines.push(Line::styled("Channels", STRONG));
    }
    for (channel, actions) in &source.actions {
        lines.push(Line::raw(format!(
            "{}  ·  {}",
            inline(channel),
            inline(&actions.join(", "))
        )));
    }
    if !source.subscription {
        lines.push(Line::raw(
            "Manage this script on disk and restart mikd to apply changes.",
        ));
    }
    lines
}

pub(super) fn source_detail(source: &SourceDetail, app: &App) -> (Vec<Line<'static>>, String) {
    let (lines, subscription) = match source {
        SourceDetail::Registered(source) => (registered_source(source), source.subscription),
        SourceDetail::Missing(id) => (
            vec![
                pair("Source ID", id),
                Line::styled("Source no longer registered.", WARN),
            ],
            false,
        ),
    };
    let mut related = Vec::new();
    if app.ready(Collection::Library) {
        related.push("t Registered tracks");
    }
    if app.ready(Collection::Cache) {
        related.push("b Cached downloads");
    }
    let mut keys = related.join("   ");
    if subscription && app.ready(Collection::Sources) {
        if !keys.is_empty() {
            keys.push('\n');
        }
        keys.push_str("r Refresh   x Remove");
    }
    (lines, keys)
}

pub(super) fn lyrics_detail(lyrics: &LyricsInspection) -> (Vec<Line<'static>>, String) {
    let mut lines = vec![pair("Media version", &lyrics.version)];
    if !lyrics.has_media {
        lines.push(Line::styled("Media version no longer available.", WARN));
    }
    if let Some(provider) = &lyrics.provider {
        lines.push(pair("Provider lookup", provider));
    }
    if lyrics.overrides.is_empty() {
        lines.push(Line::raw("No administrator override."));
    }
    for (origin, track) in lyrics
        .overrides
        .iter()
        .map(|track| ("Override", track))
        .chain(lyrics.embedded.iter().map(|track| ("Embedded", track)))
    {
        let role = track.role.to_ascii_lowercase();
        let language = track.language_tag.as_deref().unwrap_or("default");
        let entries = match (track.timed, track.entries) {
            (true, 1) => "timed cue",
            (true, _) => "timed cues",
            (false, 1) => "line",
            (false, _) => "lines",
        };
        lines.push(pair(
            origin,
            &format!("{role} · {language} · {} {entries}", track.entries),
        ));
    }
    let mut keys = Vec::new();
    if lyrics.has_media {
        keys.push("l Set override");
    }
    if !lyrics.overrides.is_empty() {
        keys.push("c Clear override");
    }
    (lines, keys.join("   "))
}
