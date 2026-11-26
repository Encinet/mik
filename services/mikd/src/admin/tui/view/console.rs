use super::{OK, WARN, box_area, inline, log_entries, panel};
use crate::{
    admin::tui::console::Console,
    console::{Capability, Lane},
};
use ratatui::{
    Frame,
    layout::Rect,
    text::{Line, Span},
};

pub(super) fn render(frame: &mut Frame<'_>, area: Rect, console: &Console) -> usize {
    let Some(entry) = console.selected() else {
        let message = console.list_error.as_ref().map_or_else(
            || Line::raw("Waiting for a plugin to register a console."),
            |error| Line::styled(format!("Could not list consoles · {}", inline(error)), WARN),
        );
        panel(frame, area, "Console", vec![message], 0);
        return 0;
    };
    let inner = box_area(
        frame,
        area,
        format!("{} · {}", inline(&entry.label), entry.id),
    );
    let mut state = vec![
        capability("Commands", &entry.commands),
        Span::raw("  ·  "),
        capability("Logs", &entry.logs),
    ];
    if matches!(entry.logs, Capability::Connected(_)) && console.list_error.is_none() {
        state.extend([Span::raw("  ·  "), Span::raw(console.view.mode())]);
    }
    let mut notes = vec![Line::from(state)];
    if let Some(error) = &console.list_error {
        notes.push(Line::styled(
            format!("Could not refresh consoles · {}", inline(error)),
            WARN,
        ));
    }
    if let Some(message) = console.log_error.as_ref().or_else(|| {
        console
            .output
            .as_ref()
            .and_then(|page| page.notice.as_ref())
    }) {
        notes.push(Line::styled(inline(message), WARN));
    }
    let empty = match (&entry.logs, &console.output, &console.log_error) {
        (Capability::Unsupported, _, _) => "This console only accepts commands.",
        (Capability::Offline, None, _) => "Waiting for the log provider to reconnect.",
        (_, None, Some(_)) => "No log page available.",
        (_, None, None) => "Loading log output…",
        (_, Some(_), _) if console.view.filter.is_empty() => "No log lines on this page.",
        _ => "No matching entries.",
    };
    log_entries(frame, inner, &console.view, console.lines(), notes, empty)
}

fn capability(label: &str, status: &Capability) -> Span<'static> {
    match status {
        Capability::Unsupported => Span::raw(format!("{label} unsupported")),
        Capability::Offline => Span::styled(format!("{label} offline"), WARN),
        Capability::Connected(_) => Span::styled(format!("{label} ready"), OK),
    }
}

pub(super) fn keys(console: &Console) -> String {
    let mut hints = Vec::new();
    if console.providers.len() > 1 {
        hints.push("[ / ] Console");
    }
    if console.target(Lane::Commands).is_some() {
        hints.push(": Command");
    }
    if console.target(Lane::Logs).is_some() {
        if let Some(page) = &console.output {
            if page.before.is_some() {
                hints.push("p Older");
            }
            if !console.view.following && page.after.is_some() {
                hints.push("n Newer");
            }
        }
        hints.push("Space Pause/follow");
    }
    if console.output.is_some() {
        hints.push("/ Filter");
        hints.push("↑↓ Scroll");
    }
    hints.join("   ")
}
