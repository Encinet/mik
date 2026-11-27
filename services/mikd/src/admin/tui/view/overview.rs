use super::{ACCENT, ERROR, OK, STRONG, Theme, WARN, inline, key_lines, pair, u16_len, wrapped};
use crate::{
    admin::{
        systemd,
        tui::{app::App, mouse::Targets},
    },
    transport::admin::{MinecraftStatus, Status},
};
use ratatui::{
    Frame,
    layout::{Constraint, Layout, Margin, Rect},
    style::Style,
    text::{Line, Span},
    widgets::{Paragraph, Wrap},
};
use std::path::Path;

pub(super) fn unit_status(app: &App) -> (&str, Style) {
    if !app.systemd {
        return ("Socket target", Style::new());
    }
    match &app.unit {
        Some(Ok(unit)) if unit.load != "loaded" => (&unit.load, WARN),
        Some(Ok(unit)) => match unit.active.as_str() {
            "active" if unit.sub == "running" => ("Running", OK),
            "active" => ("Active", OK),
            "inactive" => ("Stopped", WARN),
            "failed" => ("Failed", ERROR),
            "activating" => ("Starting", ACCENT),
            "deactivating" => ("Stopping", ACCENT),
            "reloading" | "refreshing" => ("Reloading", ACCENT),
            state => (state, WARN),
        },
        Some(Err(_)) => ("Unknown", WARN),
        None => ("Checking", ACCENT),
    }
}

pub(super) fn render(
    frame: &mut Frame<'_>,
    area: Rect,
    app: &App,
    socket: &Path,
    theme: Theme,
    targets: &mut Targets,
) -> u16 {
    let wide = area.width >= 76;
    let service_width = 32;
    let gap = u16::from(wide || area.height >= 17);
    let minecraft_width = area.width - if wide { service_width + gap } else { 0 };
    let minecraft = Paragraph::new(minecraft_status(app)).wrap(Wrap { trim: true });
    let min_height = if wide { 3 } else { 1 };
    let minecraft_height = u16_len(minecraft.line_count(minecraft_width - 2)).clamp(min_height, 4);
    let cards_height = if wide {
        minecraft_height
    } else {
        minecraft_height + gap + 3
    };
    let keys = key_lines(
        &[
            app.overview_has_actions().then_some("m Service actions"),
            app.systemd.then_some("2 Logs"),
            Some("F5 Refresh"),
        ]
        .into_iter()
        .flatten()
        .collect::<Vec<_>>()
        .join("   "),
        area.width - 2,
    );
    let [cards, toolbar, _, details] = Layout::vertical([
        Constraint::Length(cards_height),
        Constraint::Length(u16_len(keys.len())),
        Constraint::Length(u16::from(area.height >= 16)),
        Constraint::Min(1),
    ])
    .areas(area);
    let [game_card, service_card] = if wide {
        Layout::horizontal([Constraint::Min(1), Constraint::Length(service_width)])
            .spacing(gap)
            .areas(cards)
    } else {
        Layout::vertical([Constraint::Length(minecraft_height), Constraint::Length(3)])
            .spacing(gap)
            .areas(cards)
    };
    for card in [game_card, service_card] {
        frame.buffer_mut().set_style(card, theme.chrome);
    }
    frame.render_widget(minecraft, game_card.inner(Margin::new(1, 0)));

    let modules = modules(app);
    let unit = if app.systemd {
        unit_status(app)
    } else {
        ("Socket only", Style::new())
    };
    let admin = if app.status_problem.is_some() {
        ("Offline", ERROR)
    } else if app.last_status.is_some() {
        ("Connected", OK)
    } else {
        ("Checking", ACCENT)
    };
    let service = [
        ("mikd service", unit),
        ("Admin socket", admin),
        ("Modules", (modules.summary.as_str(), modules.style)),
    ]
    .into_iter()
    .map(|(label, (value, style))| state_row(label, value, style.patch(STRONG)))
    .collect::<Vec<_>>();
    frame.render_widget(
        Paragraph::new(service),
        service_card.inner(Margin::new(1, 0)),
    );
    keys.render(frame, toolbar.inner(Margin::new(1, 0)), targets);

    let mut health = service_issues(app);
    if !health.is_empty() {
        health.push(Line::default());
    }
    health.extend(modules.lines);
    let connection = connection_details(app, socket);
    targets.scroll(details, false);
    if wide {
        let [health_area, runtime] = Layout::horizontal([Constraint::Fill(6), Constraint::Fill(5)])
            .spacing(3)
            .areas(details);
        wrapped(frame, health_area, health, app.page_scroll).max(wrapped(
            frame,
            runtime,
            connection,
            app.page_scroll,
        ))
    } else {
        health.push(Line::default());
        health.extend(connection);
        wrapped(frame, details, health, app.page_scroll)
    }
}

fn minecraft_status(app: &App) -> Vec<Line<'static>> {
    let minecraft = app
        .last_status
        .as_ref()
        .and_then(|(status, _)| status.minecraft.as_ref());
    let heading = |state, style| {
        Line::from(vec![
            Span::styled("Minecraft Status", STRONG),
            Span::raw("   "),
            Span::styled(state, style),
        ])
    };
    if app.status_problem.is_some() {
        return vec![heading("Admin unavailable", WARN)];
    }
    match minecraft {
        Some(MinecraftStatus::Live {
            version,
            visible_players,
            max_players,
            tps,
            mspt,
            uptime_seconds,
        }) => vec![
            heading("Live", OK),
            Line::from(vec![
                Span::raw("Visible "),
                Span::styled(format!("{visible_players}/{max_players}"), STRONG),
                Span::raw("   TPS "),
                Span::styled(tick_value(*tps), STRONG),
                Span::raw("   MSPT "),
                Span::styled(tick_value(*mspt), STRONG),
            ]),
            Line::raw(format!(
                "Version {}   MIK uptime {}",
                if version.is_empty() {
                    "—".into()
                } else {
                    inline(version)
                },
                uptime_seconds.map_or_else(|| "—".into(), uptime),
            )),
        ],
        Some(MinecraftStatus::NoRecentSample) => vec![heading("No recent data", WARN)],
        None if app.last_status.is_none() => vec![heading("Checking", ACCENT)],
        None => vec![heading("Status not reported", WARN)],
    }
}

fn tick_value(value: Option<f64>) -> String {
    value.map_or_else(|| "—".into(), |value| format!("{value:.2}"))
}

fn uptime(seconds: u64) -> String {
    let (days, hours, minutes) = (seconds / 86_400, seconds / 3_600 % 24, seconds / 60 % 60);
    if days > 0 {
        format!("{days}d {hours:02}h {minutes:02}m")
    } else if hours > 0 {
        format!("{hours}h {minutes:02}m")
    } else if minutes > 0 {
        format!("{minutes}m {:02}s", seconds % 60)
    } else {
        format!("{seconds}s")
    }
}

fn service_issues(app: &App) -> Vec<Line<'static>> {
    let mut lines = Vec::new();
    match &app.unit {
        Some(Ok(unit)) if !unit.result.is_empty() && unit.result != "success" => {
            lines.push(state_row("Unit result", &unit.result, ERROR));
        }
        Some(Err(problem)) => {
            lines.push(Line::styled(
                format!("Systemd query failed · {}", inline(problem)),
                WARN,
            ));
        }
        _ => {}
    }
    if let Some(problem) = &app.status_problem {
        lines.push(Line::styled(
            format!("Management socket unreachable · {}", inline(problem)),
            ERROR,
        ));
    }
    if !lines.is_empty() {
        lines.insert(0, Line::styled("Service diagnostics", STRONG));
    }
    lines
}

fn connection_details(app: &App, socket: &Path) -> Vec<Line<'static>> {
    let mut lines = vec![Line::styled("Target", STRONG)];
    if let Some(Ok(unit)) = &app.unit {
        lines.push(pair(
            systemd::UNIT,
            &format!("{} / {} ({})", unit.load, unit.active, unit.sub),
        ));
    }
    lines.push(pair("Socket", &socket.display().to_string()));
    if let Some((status, checked)) = &app.last_status {
        lines.extend([Line::default(), Line::styled("Web endpoints", STRONG)]);
        let style = if app.status_problem.is_some() {
            lines.push(Line::raw(format!(
                "Last seen {}s ago",
                checked.elapsed().as_secs()
            )));
            Style::new()
        } else {
            OK
        };
        append_endpoints(&mut lines, status, style);
    }
    lines
}

struct Modules {
    summary: String,
    style: Style,
    lines: Vec<Line<'static>>,
}

fn modules(app: &App) -> Modules {
    let Some((status, checked)) = &app.last_status else {
        let message = if app.status_problem.is_some() {
            "Connect to the admin socket to read module health."
        } else {
            "Waiting for the first admin response…"
        };
        return Modules {
            summary: if app.status_problem.is_some() {
                "No data"
            } else {
                "Checking"
            }
            .into(),
            style: if app.status_problem.is_some() {
                WARN
            } else {
                ACCENT
            },
            lines: vec![Line::styled("Module health", STRONG), Line::raw(message)],
        };
    };
    let disconnected = app.status_problem.is_some();
    let mut lines = vec![Line::styled("Core modules", STRONG)];
    if disconnected {
        lines.push(Line::styled(
            format!("Last seen {}s ago", checked.elapsed().as_secs()),
            WARN,
        ));
    }
    let (ok, warn, error) = if disconnected {
        (Style::new(), Style::new(), Style::new())
    } else {
        (OK, WARN, ERROR)
    };
    let mut ready = 0;
    let mut total = 0;
    for (name, problem) in status
        .problems
        .iter()
        .filter(|(name, _)| !web_endpoint(name))
    {
        total += 1;
        let (state, style) = match problem.as_str() {
            "STALE" => ("Stale", warn),
            "UNAVAILABLE" => ("Unavailable", error),
            _ => (problem.as_str(), warn),
        };
        lines.push(state_row(domain_name(name), state, style));
    }
    for name in status
        .ready
        .iter()
        .filter(|name| !web_endpoint(name))
        .filter(|name| !status.problems.contains_key(*name))
    {
        ready += 1;
        total += 1;
        lines.push(state_row(domain_name(name), "Ready", ok));
    }
    if total == 0 {
        lines.push(Line::raw("No core modules reported."));
    }
    lines.push(Line::default());
    lines.push(Line::styled("Music", STRONG));
    for (name, available) in [
        ("Tracks", status.music_catalog),
        ("LX sources", status.music_source_registry),
        ("Media cache", status.music_media_cache),
        ("Paper music", status.paper_music),
    ] {
        total += 1;
        let (value, style) = if available {
            ready += 1;
            ("Ready", ok)
        } else {
            ("Unavailable", warn)
        };
        lines.push(state_row(name, value, style));
    }
    Modules {
        summary: if disconnected {
            "Stale".into()
        } else {
            format!("{ready}/{total} ready")
        },
        style: if disconnected || ready < total {
            WARN
        } else {
            OK
        },
        lines,
    }
}

fn web_endpoint(name: &str) -> bool {
    matches!(name, "web-api" | "web-chat")
}

fn append_endpoints(lines: &mut Vec<Line<'static>>, status: &Status, ok: Style) {
    for (key, name, port) in [
        ("web-api", "Web API", status.web_api_port),
        ("web-chat", "Web chat", status.web_chat_port),
    ] {
        let listening = format!("Listening on {port}");
        let (value, style) = if let Some(problem) = status.problems.get(key) {
            (problem.as_str(), if ok == OK { WARN } else { ok })
        } else if port == 0 {
            ("Not listening", Style::new())
        } else {
            (listening.as_str(), ok)
        };
        lines.push(state_row(name, value, style));
    }
}

fn domain_name(name: &str) -> &str {
    match name {
        "announcements" => "Announcements",
        "assistant" => "AI assistant",
        "chat" => "Chat",
        "governance" => "Governance",
        "identity" => "Identity",
        "qq-moderation" => "QQ moderation",
        name => name,
    }
}

fn state_row(label: &str, value: &str, style: Style) -> Line<'static> {
    let label = Span::raw(inline(label));
    let gap = 17_usize.saturating_sub(label.width()).max(2);
    Line::from(vec![
        label,
        Span::raw(" ".repeat(gap)),
        Span::styled(inline(value), style),
    ])
}
