use super::{ACCENT, ERROR, OK, WARN, inline, log_entries, panel, section};
use crate::{
    admin::tui::{console::Console, console_log},
    console::{Capability, Lane},
};
use ratatui::{
    Frame,
    layout::Rect,
    style::Style,
    text::{Line, Span},
};

pub(super) fn render(frame: &mut Frame<'_>, area: Rect, console: &Console) -> usize {
    let Some(entry) = console.selected() else {
        let message = console.list_error.as_ref().map_or_else(
            || {
                if console.providers_loaded {
                    Line::raw("No consoles registered. Check the plugin connection and press F5.")
                } else {
                    Line::styled("Looking for plugin consoles…", ACCENT)
                }
            },
            |error| Line::styled(format!("Could not list consoles · {}", inline(error)), WARN),
        );
        panel(frame, area, "Console", vec![message], 0);
        return 0;
    };
    let inner = section(
        frame,
        area,
        format!("{} · {}", inline(&entry.label), entry.id),
    );
    let mut notes = Vec::new();
    if let Some(error) = &console.list_error {
        notes.push(Line::styled(
            format!("Console status is stale · {}", inline(error)),
            WARN,
        ));
    } else {
        notes.push(Line::from(vec![
            capability("Commands", &entry.commands),
            Span::raw("  |  "),
            capability("Logs", &entry.logs),
        ]));
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
        _ => "No matching entries. / Edit filter.",
    };
    log_entries(
        frame,
        inner,
        &console.view,
        console.lines(),
        notes,
        empty,
        colored_line,
    )
}

fn colored_line(line: &str) -> Line<'static> {
    if line.contains(console_log::CSI) {
        return Line::from(
            console_log::segments(line)
                .filter(|(text, _)| !text.is_empty())
                .map(|(text, color)| {
                    let style = color.map_or_else(Style::new, |color| Style::new().fg(color));
                    Span::styled(inline(text), style)
                })
                .collect::<Vec<_>>(),
        );
    }
    if let Some((start, end, style)) = paper_level(line) {
        return Line::from(vec![
            Span::raw(inline(&line[..start])),
            Span::styled(inline(&line[start..end]), style),
            Span::raw(inline(&line[end..])),
        ]);
    }
    Line::raw(inline(line))
}

fn paper_level(line: &str) -> Option<(usize, usize, Style)> {
    let timestamp = line.strip_prefix('[')?.get(..8)?;
    if !timestamp.bytes().enumerate().all(|(i, byte)| {
        if matches!(i, 2 | 5) {
            byte == b':'
        } else {
            byte.is_ascii_digit()
        }
    }) {
        return None;
    }
    let (header, _) = line.split_once("]:")?;
    let marker = header.rsplit([' ', '/']).next()?;
    let style = match marker {
        "INFO" => ACCENT,
        "WARN" => WARN,
        "ERROR" | "FATAL" => ERROR,
        _ => return None,
    };
    Some((header.len() - marker.len(), header.len(), style))
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
    if console.target(Lane::Commands).is_some() {
        hints.push(": Command");
    }
    if console.providers.len() > 1 {
        hints.push("[ Prev provider");
        hints.push("] Next provider");
    }
    if console.target(Lane::Logs).is_some() {
        if let Some(page) = &console.output {
            if page.before.is_some() {
                hints.push("p Older");
            }
            if page.after.is_some() {
                hints.push("n Newer");
            }
        }
        hints.push(if console.view.following {
            "Space Pause"
        } else {
            "Space Follow"
        });
    }
    if console.output.is_some() {
        hints.push("/ Filter");
        hints.push("↑↓ Scroll");
    }
    hints.join("   ")
}
