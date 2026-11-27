mod app;
mod catalog;
mod console;
mod console_log;
mod form;
mod jobs;
mod journal;
mod log_view;
mod menu;
mod mouse;
mod view;

use self::{
    app::{App, Page},
    jobs::{Finished, Work},
};
use super::{console as admin_console, systemd};
use crate::{
    console::{LogPage, ProviderStatus},
    transport::admin::Status,
};
use crossterm::{
    cursor::Show,
    event::{
        DisableBracketedPaste, DisableMouseCapture, EnableBracketedPaste, Event, EventStream,
        KeyCode, KeyEvent, KeyEventKind, KeyModifiers,
    },
    execute,
};
use futures_util::StreamExt;
use std::{
    io::{self, IsTerminal, Write},
    path::Path,
    time::Duration,
};
use tokio::{
    signal::unix::{SignalKind, signal},
    task::JoinSet,
    time::{MissedTickBehavior, interval},
};

const POLL: Duration = Duration::from_secs(2);

pub(super) fn run(socket: &Path, systemd: bool, mouse: bool) -> io::Result<()> {
    if !io::stdin().is_terminal() || !io::stdout().is_terminal() {
        return Err(io::Error::other(
            "mikd admin needs an interactive terminal. Use 'mikd admin --help' for script commands.",
        ));
    }
    tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()?
        .block_on(console(socket, systemd, mouse))
}

async fn console(socket: &Path, systemd: bool, mouse: bool) -> io::Result<()> {
    let mut interrupt = signal(SignalKind::interrupt())?;
    let mut terminate = signal(SignalKind::terminate())?;
    let mut hangup = signal(SignalKind::hangup())?;
    let mut terminal_mode = TerminalMode {
        active: true,
        mouse,
    };
    let mut terminal = ratatui::try_init()?;
    let restore_terminal = std::panic::take_hook();
    std::panic::set_hook(Box::new(move |panic| {
        drop(restore_input(mouse));
        restore_terminal(panic);
    }));
    execute!(io::stdout(), EnableBracketedPaste)?;
    if mouse {
        // Button and wheel events in SGR format. Passive pointer motion is unnecessary.
        let mut out = io::stdout().lock();
        out.write_all(b"\x1b[?1000h\x1b[?1006h")?;
        out.flush()?;
    }

    let mut app = App::new(systemd);
    let result = tokio::select! {
        biased;
        _ = interrupt.recv() => Ok(()),
        _ = terminate.recv() => Ok(()),
        _ = hangup.recv() => Ok(()),
        result = run_console(&mut terminal, &mut app, socket, mouse) => result,
    };
    let restored = terminal_mode.close();
    if let Some(pending) = app.pending.filter(|pending| pending.modifies) {
        writeln!(
            io::stderr().lock(),
            "{} was in progress. Check the result before retrying.",
            pending.label,
        )?;
    }
    result.and(restored)
}

fn restore_input(mouse: bool) -> io::Result<()> {
    let mut out = io::stdout().lock();
    let capture = if mouse {
        execute!(out, DisableMouseCapture)
    } else {
        Ok(())
    };
    capture.and(execute!(out, DisableBracketedPaste, Show))
}

struct TerminalMode {
    active: bool,
    mouse: bool,
}

impl TerminalMode {
    fn close(&mut self) -> io::Result<()> {
        if !std::mem::take(&mut self.active) {
            return Ok(());
        }
        let output = restore_input(self.mouse);
        let restored = ratatui::try_restore();
        output.and(restored)
    }
}

impl Drop for TerminalMode {
    fn drop(&mut self) {
        drop(self.close());
    }
}

enum Completion {
    Status(u64, Result<Status, String>),
    Unit(u64, io::Result<systemd::Unit>),
    Journal {
        forced: bool,
        result: io::Result<systemd::JournalOutput>,
    },
    ConsoleProviders(io::Result<Vec<ProviderStatus>>),
    ConsoleLog(console::Read, io::Result<LogPage>),
    Work(Finished),
}

#[derive(Default)]
struct Polls {
    generation: u64,
    status: bool,
    unit: bool,
    journal: bool,
    console: ConsolePolls,
}

#[derive(Default)]
struct ConsolePolls {
    providers: bool,
    log: bool,
}

impl Polls {
    fn schedule(&mut self, tasks: &mut JoinSet<Completion>, app: &mut App, socket: &Path) {
        let generation = self.generation;
        if !self.status {
            self.status = true;
            let socket = socket.to_owned();
            tasks.spawn(async move { Completion::Status(generation, jobs::status(&socket).await) });
        }
        if app.systemd && !self.unit {
            self.unit = true;
            tasks.spawn(async move { Completion::Unit(generation, systemd::status().await) });
        }
        if app.systemd
            && app.page == Page::Journal
            && !self.journal
            && (app.journal.view.following || app.journal.refresh)
        {
            self.journal = true;
            let forced = std::mem::take(&mut app.journal.refresh);
            tasks.spawn(async move {
                Completion::Journal {
                    forced,
                    result: systemd::recent_logs().await,
                }
            });
        }
        if app.page == Page::Console {
            self.schedule_console(tasks, app, socket);
        }
    }

    fn schedule_console(&mut self, tasks: &mut JoinSet<Completion>, app: &mut App, socket: &Path) {
        if !self.console.providers {
            self.console.providers = true;
            let socket = socket.to_owned();
            tasks.spawn(async move {
                Completion::ConsoleProviders(admin_console::providers(&socket).await)
            });
        }
        if !self.console.log
            && let Some(read) = app.console.read()
        {
            self.console.log = true;
            let socket = socket.to_owned();
            tasks.spawn(async move {
                let result =
                    admin_console::logs(&socket, read.target.clone(), read.position.clone()).await;
                Completion::ConsoleLog(read, result)
            });
        }
    }

    fn completed(&mut self, app: &mut App, completion: Completion) -> bool {
        match completion {
            Completion::Status(generation, result) => {
                self.status = false;
                if generation == self.generation {
                    app.connect(result);
                }
            }
            Completion::Unit(generation, result) => {
                self.unit = false;
                if generation == self.generation {
                    app.unit = Some(result.map_err(|error| error.to_string()));
                }
            }
            Completion::Journal { forced, result } => {
                self.journal = false;
                if forced || app.journal.view.following || app.journal.output.is_none() {
                    app.journal.update(result);
                }
            }
            Completion::ConsoleProviders(result) => {
                self.console.providers = false;
                app.console.update_providers(result);
                return !self.console.log && app.page == Page::Console && app.console.requested();
            }
            Completion::ConsoleLog(read, result) => {
                self.console.log = false;
                app.console.update_log(&read, result);
                return app.page == Page::Console && app.console.requested();
            }
            Completion::Work(result) => {
                let service_changed = matches!(result, Finished::Service(..));
                if service_changed {
                    self.generation = self.generation.wrapping_add(1);
                }
                app.finished(result);
                return service_changed;
            }
        }
        false
    }
}

async fn run_console(
    terminal: &mut ratatui::DefaultTerminal,
    app: &mut App,
    socket: &Path,
    mouse: bool,
) -> io::Result<()> {
    let mut input = EventStream::new();
    let mut tasks = JoinSet::new();
    let mut polls = Polls::default();
    let theme = view::Theme::detect();
    let mut tick = interval(POLL);
    tick.set_missed_tick_behavior(MissedTickBehavior::Skip);
    let mut viewport = view::Viewport::default();
    let mut fits = false;
    let mut redraw = true;
    loop {
        if let Some(work) = app.next_load() {
            spawn(&mut tasks, socket, work);
            redraw = true;
        }
        if std::mem::take(&mut redraw) {
            let frame =
                terminal.draw(|frame| viewport = view::render(frame, app, socket, theme))?;
            fits = view::fits(frame.area);
            if fits {
                app.sync_scroll(viewport.scroll_limit, viewport.targets.list_offset);
            }
        }
        tokio::select! {
            event = input.next() => {
                let previous = app.page;
                let mut refresh = false;
                let work = match event.transpose()? {
                    Some(Event::Key(key)) if key.kind != KeyEventKind::Release => {
                        if quit(key, app) {
                            return Ok(());
                        }
                        if !fits {
                            continue;
                        }
                        refresh = key.code == KeyCode::F(5);
                        app.key(key, viewport.log_rows)
                    }
                    Some(Event::Mouse(event)) if mouse && fits => {
                        let Some(input) = viewport.targets.input(event) else {
                            continue;
                        };
                        if matches!(input, mouse::Input::Click(mouse::Control::Key(KeyCode::Char('q'))))
                            && app.layer().is_none()
                        {
                            return Ok(());
                        }
                        refresh = matches!(input, mouse::Input::Click(mouse::Control::Key(KeyCode::F(5))));
                        app.mouse(input, viewport.log_rows)
                    }
                    Some(Event::Paste(text)) if fits => {
                        app.paste(&text);
                        None
                    }
                    Some(Event::Resize(..)) => None,
                    Some(_) => continue,
                    None => return Ok(()),
                };
                redraw = true;
                if let Some(work) = work {
                        spawn(&mut tasks, socket, work);
                }
                if refresh
                    || (app.page == Page::Journal && (previous != app.page || app.journal.refresh))
                    || (app.page == Page::Console && (previous != app.page || app.console.requested()))
                {
                    tick.reset_immediately();
                }
            },
            _ = tick.tick() => polls.schedule(&mut tasks, app, socket),
            Some(result) = tasks.join_next(), if !tasks.is_empty() => {
                redraw = true;
                if polls.completed(app, result.map_err(io::Error::other)?) {
                    tick.reset_immediately();
                }
            }
        }
    }
}

fn quit(key: KeyEvent, app: &App) -> bool {
    (key.code == KeyCode::Char('c') && key.modifiers.contains(KeyModifiers::CONTROL))
        || (key.code == KeyCode::Char('q') && key.modifiers.is_empty() && app.layer().is_none())
}

fn spawn(tasks: &mut JoinSet<Completion>, socket: &Path, work: Work) {
    let socket = socket.to_owned();
    tasks.spawn(async move { Completion::Work(work.run(&socket).await) });
}
