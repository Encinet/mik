mod app;
mod catalog;
mod console;
mod form;
mod jobs;
mod journal;
mod log_view;
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
        DisableBracketedPaste, EnableBracketedPaste, Event, EventStream, KeyCode, KeyEvent,
        KeyEventKind, KeyModifiers,
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

pub(super) fn run(socket: &Path, systemd: bool) -> io::Result<()> {
    if !io::stdin().is_terminal() || !io::stdout().is_terminal() {
        return Err(io::Error::other(
            "mikd admin needs an interactive terminal. Use 'mikd admin --help' for script commands.",
        ));
    }
    tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()?
        .block_on(console(socket, systemd))
}

async fn console(socket: &Path, systemd: bool) -> io::Result<()> {
    let mut interrupt = signal(SignalKind::interrupt())?;
    let mut terminate = signal(SignalKind::terminate())?;
    let mut hangup = signal(SignalKind::hangup())?;
    let mut terminal_mode = TerminalMode(true);
    let mut terminal = ratatui::try_init()?;
    execute!(io::stdout(), EnableBracketedPaste)?;

    let mut app = App::new(systemd);
    let result = tokio::select! {
        biased;
        _ = interrupt.recv() => Ok(()),
        _ = terminate.recv() => Ok(()),
        _ = hangup.recv() => Ok(()),
        result = run_console(&mut terminal, &mut app, socket) => result,
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

struct TerminalMode(bool);

impl TerminalMode {
    fn close(&mut self) -> io::Result<()> {
        if !std::mem::take(&mut self.0) {
            return Ok(());
        }
        let output = execute!(io::stdout(), DisableBracketedPaste, Show);
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
) -> io::Result<()> {
    let mut input = EventStream::new();
    let mut tasks = JoinSet::new();
    let mut polls = Polls::default();
    let mut tick = interval(POLL);
    tick.set_missed_tick_behavior(MissedTickBehavior::Skip);
    loop {
        if let Some(work) = app.next_load() {
            spawn(&mut tasks, socket, work);
        }
        let mut page_rows = 0;
        let frame = terminal.draw(|frame| page_rows = view::render(frame, app, socket))?;
        let fits = view::fits(frame.area);
        tokio::select! {
            event = input.next() => match event.transpose()? {
                Some(Event::Key(key)) if key.kind != KeyEventKind::Release => {
                    if quit(key, app) {
                        return Ok(());
                    }
                    if !fits {
                        continue;
                    }
                    let previous = app.page;
                    if let Some(work) = app.key(key, page_rows) {
                        spawn(&mut tasks, socket, work);
                    }
                    if key.code == KeyCode::F(5)
                        || (app.page == Page::Journal && app.journal.refresh)
                        || (app.page == Page::Console && (previous != app.page || app.console.requested()))
                    {
                        tick.reset_immediately();
                    }
                }
                Some(Event::Paste(text)) if fits => app.paste(&text),
                Some(_) => {}
                None => return Ok(()),
            },
            _ = tick.tick() => polls.schedule(&mut tasks, app, socket),
            Some(result) = tasks.join_next(), if !tasks.is_empty() => {
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
