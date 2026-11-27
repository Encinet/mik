use super::{
    ACCENT, CollectionView, STRONG, Theme, WARN, cache, collection, collection_rows, dialog,
    inline, page_keys, pair, source_health, u16_len,
};
use crate::{
    admin::tui::{
        app::{App, track_actions},
        catalog::{BySource, Collection, Online, SourceDetail, Versions},
        jobs::Work,
        menu::{Choice, Item},
        mouse::{Control, Targets},
    },
    domains::music::{
        CatalogQuery, CatalogTrack, InspectedMedia, LocalTrack, LyricsInspection, SourceState,
        TrackInspection,
    },
};
use ratatui::{
    Frame,
    layout::{Constraint, Layout, Rect},
    style::Style,
    text::{Line, Span},
    widgets::Paragraph,
};

pub(super) fn workspace(
    frame: &mut Frame<'_>,
    area: Rect,
    current: Collection,
    theme: Theme,
    mut targets: Option<&mut Targets>,
) -> Rect {
    let [tabs, _, body] = Layout::vertical([
        Constraint::Length(1),
        Constraint::Length(u16::from(area.height >= 18)),
        Constraint::Min(1),
    ])
    .areas(area);
    frame.buffer_mut().set_style(tabs, theme.chrome);
    let mut x = tabs.x;
    for collection in Collection::ALL {
        let label = format!(" {} ", collection.name());
        let width = u16_len(label.len());
        let tab = Rect::new(x, tabs.y, width, 1);
        frame.render_widget(
            Paragraph::new(label).style(if collection == current {
                theme.selection
            } else {
                Style::new()
            }),
            tab,
        );
        if let Some(targets) = targets.as_deref_mut() {
            targets.add(tab, Control::Collection(collection));
        }
        x += width + 1;
    }
    frame.render_widget(
        Paragraph::new("[ / ] ").style(ACCENT).right_aligned(),
        Rect::new(x, tabs.y, tabs.right().saturating_sub(x), 1),
    );
    body
}

pub(super) fn render(frame: &mut Frame<'_>, area: Rect, app: &App, targets: &mut Targets) {
    match app.music.current {
        Collection::Library => library(frame, area, app, targets),
        Collection::Sources => sources(frame, area, app, targets),
        Collection::Cache => cache::render(frame, area, app, targets),
    }
}

pub(super) fn keys(app: &App, current: Collection) -> String {
    if !app.ready(current) {
        return String::new();
    }
    match current {
        Collection::Library => {
            let list = &app.music.library.list;
            let page = page_keys(list.page() > 1, list.can_next());
            let selection = if list.entries.is_empty() {
                ""
            } else {
                "   Enter Details"
            };
            format!("/ Search{selection}{page}   a Import   m Actions")
        }
        Collection::Sources => match (app.music.sources.selected(), app.manageable_source()) {
            (_, Some(_)) => "a Add   Enter Details   m Actions",
            (Some(_), None) => "a Add   Enter Details",
            (None, None) => "a Add",
        }
        .into(),
        Collection::Cache => {
            let list = &app.music.cache.list;
            let page = page_keys(list.page() > 1, list.can_next());
            let selection = if list.entries.is_empty() {
                ""
            } else {
                "Enter Details   "
            };
            format!("{selection}m Actions{page}")
        }
    }
}

fn library(frame: &mut Frame<'_>, area: Rect, app: &App, targets: &mut Targets) {
    let library = &app.music.library;
    let list = &library.list;
    let mut title = format!("Track library · Page {}", list.page());
    if !library.keyword.is_empty() {
        title.push_str(" · ");
        title.push_str(&inline(&library.keyword));
    }
    let empty = if list.known {
        "No tracks on this page."
    } else {
        "Loading tracks…"
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
            scroll: app.music.scroll(),
        },
        list.entries.iter().map(track_row),
        targets,
    );
}

fn sources(frame: &mut Frame<'_>, area: Rect, app: &App, targets: &mut Targets) {
    let sources = &app.music.sources;
    let rows = sources.entries.iter().map(|source| {
        let (state, style) = source_health(source);
        Line::from(vec![
            Span::styled(state, style),
            Span::raw(format!("  {}", inline(&source.name))),
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
            scroll: app.music.scroll(),
        },
        rows,
        targets,
    );
}

pub(super) fn track_row(track: &CatalogTrack) -> Line<'static> {
    let kind = if track.local { "Local" } else { "Remote" };
    let mut row = Vec::new();
    if track.retired {
        row.push(Span::styled("Retired  ·  ", WARN));
    }
    row.push(Span::raw(inline(&track.title)));
    if !track.artist.is_empty() {
        row.push(Span::raw(format!("  ·  {}", inline(&track.artist))));
    }
    row.push(Span::raw(format!("  [{kind}]")));
    Line::from(row)
}

pub(super) fn online(
    frame: &mut Frame<'_>,
    area: Rect,
    online: &Online,
    scroll: u16,
    targets: &mut Targets,
) {
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
            "{}{}",
            if page.entries.is_empty() {
                ""
            } else {
                "Enter Track"
            },
            page_keys(online.page > 0, online.can_next()),
        ),
        targets,
        |frame, area, targets| {
            collection_rows(
                frame,
                area,
                &CollectionView {
                    unavailable: false,
                    problem: None,
                    empty: "No tracks on this page.",
                    selected: online.selected,
                    scroll,
                },
                page.entries.iter().map(track_row),
                targets,
            );
        },
    );
}

pub(super) fn source_tracks(
    frame: &mut Frame<'_>,
    area: Rect,
    app: &App,
    tracks: &BySource<CatalogTrack>,
    scroll: u16,
    targets: &mut Targets,
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
        targets,
        |frame, area, targets| {
            collection_rows(
                frame,
                area,
                &CollectionView {
                    unavailable: !app.ready(Collection::Library),
                    problem: list.problem.as_deref(),
                    empty: if list.known {
                        "No tracks registered by this source."
                    } else {
                        "Loading registered tracks…"
                    },
                    selected: list.selected,
                    scroll,
                },
                list.entries.iter().map(track_row),
                targets,
            );
        },
    );
}

pub(super) fn versions(
    frame: &mut Frame<'_>,
    area: Rect,
    app: &App,
    versions: &Versions,
    scroll: u16,
    targets: &mut Targets,
) {
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
        targets,
        |frame, area, targets| {
            collection_rows(
                frame,
                area,
                &CollectionView {
                    unavailable: false,
                    problem: list.problem.as_deref(),
                    empty: "No recorded media versions.",
                    selected: list.selected,
                    scroll,
                },
                rows,
                targets,
            );
        },
    );
}

pub(super) fn track_detail(track: &TrackInspection, app: &App) -> dialog::Detail {
    let mut lines = vec![pair("Track ID", &track.id), pair("State", &track.state)];
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
    item_detail(
        lines,
        &track_actions(
            track,
            app.ready(Collection::Library),
            app.ready(Collection::Sources),
            app.ready(Collection::Cache),
        ),
    )
}

pub(super) fn item_detail(lines: Vec<Line<'static>>, items: &[Item]) -> dialog::Detail {
    let related = items
        .iter()
        .filter_map(|item| match item.action {
            Choice::Run(
                Work::Track(_)
                | Work::FollowSource(_)
                | Work::Versions { .. }
                | Work::CacheEntry(_)
                | Work::Lyrics(_),
            ) => Some(format!("{} {}", item.shortcut?, item.label)),
            _ => None,
        })
        .collect::<Vec<_>>()
        .join("   ");
    dialog::Detail {
        lines,
        related,
        actions: if items.is_empty() {
            String::new()
        } else {
            "m Actions".into()
        },
    }
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
        pair("Source ID", &source.id),
        pair("Type", kind),
        Line::styled(health, style),
    ];
    if let Some(origin) = &source.origin {
        lines.push(pair("Link", origin));
    }
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
    lines
}

pub(super) fn source_detail(source: &SourceDetail, app: &App) -> dialog::Detail {
    let lines = match source {
        SourceDetail::Registered(source) => registered_source(source),
        SourceDetail::Missing(id) => vec![
            pair("Source ID", id),
            Line::styled("Source no longer registered.", WARN),
        ],
    };
    let mut related = Vec::new();
    if app.ready(Collection::Library) {
        related.push("t Registered tracks");
    }
    if app.ready(Collection::Cache) {
        related.push("b Cached downloads");
    }
    dialog::Detail {
        lines,
        related: related.join("   "),
        actions: if app.manageable_source().is_some() {
            "m Actions".into()
        } else {
            String::new()
        },
    }
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
