mod console;
mod music;
mod systemd;
mod tui;

use crate::{
    console::{CommandOutcome, Lane, LogPosition, ProviderStatus},
    transport::admin::{self, Command, Reply},
};
use std::{
    ffi::OsString,
    io::{self, Write},
    path::{Path, PathBuf},
};

const USAGE: &str = "usage: mikd admin [--socket <admin-socket>] [--no-mouse | <command>]
\
  Omit <command> to open the dashboard. Use --no-mouse for terminal text selection.
\
  status
\
  console list
\
  console logs <instance>/<provider> [--before <cursor> | --after <cursor>]
\
  console run <instance>/<provider> <command-line>
\
  music catalog list [--query <text>] [--after <track-id>]
\
  music catalog search <text> [--after <cursor>]
\
  music catalog collection <link> [--after <cursor>]
\
  music sources <list | inspect <id> | import <url> | refresh [<id>] | remove <id>>
\
  music sources edit <id> <url>
\
  music sources <enable | disable> <id>
  music sources test <source-id> <track-id>
\
  music sources tracks <id> [--after <track-id>]
\
  music sources cache <id> [--after <media-version>]
\
  music library <list [--after <id>] | import <file> | replace <id> <file>>
\
  music track <inspect | prepare | retire | restore> <track-id>
\
  music track versions <track-id> [--after <media-version>]
\
  music cache <list [--after <media-version>] | inspect <media-version> | evict <media-version> | clear-idle>
\
  music lyrics <inspect <media-version> | set <media-version> <file.json|file.lrc|file.txt> | clear <media-version>>";

pub(super) fn run(args: &[OsString], control_socket: &Path) -> io::Result<()> {
    let (socket, systemd, args) = if let [flag, socket, args @ ..] = args
        && flag == "--socket"
    {
        (PathBuf::from(socket), false, args)
    } else {
        (admin::socket_path(control_socket), true, args)
    };
    let args = args
        .iter()
        .map(|arg| arg.to_str().ok_or_else(usage))
        .collect::<io::Result<Vec<_>>>()?;
    if let ["console", args @ ..] = args.as_slice() {
        return runtime()?.block_on(run_console(&socket, args));
    }
    let command = match args.as_slice() {
        [] => return tui::run(&socket, systemd, true),
        ["--no-mouse"] => return tui::run(&socket, systemd, false),
        ["help" | "--help" | "-h"] => return print(format_args!("{USAGE}\n")),
        ["status"] => Command::Status,
        ["music", args @ ..] => music::command(args)?,
        _ => return Err(usage()),
    };
    let reply = runtime()?.block_on(admin::call(&socket, command))?;
    match reply {
        Reply::Error(detail) => Err(io::Error::other(detail)),
        Reply::Sources(sources) => {
            let mut out = io::stdout().lock();
            for source in sources {
                let state = match (source.enabled, source.available) {
                    (false, _) => "disabled",
                    (true, true) => "available",
                    (true, false) => "unavailable",
                };
                writeln!(
                    out,
                    "{}\t{state}\t{:?}",
                    source.id.escape_debug(),
                    source.name
                )?;
            }
            Ok(())
        }
        reply => {
            print_reply(&reply)?;
            match reply {
                Reply::Refreshed(result) if result.failed > 0 => {
                    Err(io::Error::other("some LX subscriptions failed to refresh"))
                }
                Reply::CacheCleared(result) if result.failed > 0 => {
                    Err(io::Error::other("some media cache files failed to clear"))
                }
                _ => Ok(()),
            }
        }
    }
}

async fn run_console(socket: &Path, args: &[&str]) -> io::Result<()> {
    let (id, position) = match args {
        ["list"] => return print_reply(&Reply::Consoles(console::providers(socket).await?)),
        ["logs", id] => (id, LogPosition::Latest),
        ["logs", id, "--before", cursor] => (id, LogPosition::Before((*cursor).into())),
        ["logs", id, "--after", cursor] => (id, LogPosition::After((*cursor).into())),
        ["run", id, line] => {
            let target = console_provider(socket, id)
                .await?
                .target(Lane::Commands)
                .ok_or_else(|| io::Error::other(CommandOutcome::Offline.description()))?;
            let outcome = console::run(socket, target, (*line).into())
                .await
                .map_err(|error| match error {
                    console::RunError::Failed(error) => error,
                    console::RunError::Unconfirmed(error) => io::Error::other(format!(
                        "{} {error}",
                        CommandOutcome::Unconfirmed.description()
                    )),
                })?;
            print_reply(&Reply::ConsoleCommand(outcome))?;
            return if outcome == CommandOutcome::Submitted {
                Ok(())
            } else {
                Err(io::Error::other(outcome.description()))
            };
        }
        _ => return Err(usage()),
    };
    let target = console_provider(socket, id).await?.log_target()?;
    print_reply(&Reply::ConsoleLogs(
        console::logs(socket, target, position).await?,
    ))
}

async fn console_provider(socket: &Path, id: &str) -> io::Result<ProviderStatus> {
    let (instance, provider) = id.split_once('/').ok_or_else(usage)?;
    console::providers(socket)
        .await?
        .into_iter()
        .find(|entry| entry.id.instance == instance && entry.id.provider == provider)
        .ok_or_else(|| {
            io::Error::new(
                io::ErrorKind::NotFound,
                "Unknown console. Use console list to see providers.",
            )
        })
}

fn runtime() -> io::Result<tokio::runtime::Runtime> {
    tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
}

fn print_reply(reply: &Reply) -> io::Result<()> {
    let json = serde_json::to_string_pretty(reply).map_err(io::Error::other)?;
    print(format_args!("{json}\n"))
}

fn print(args: std::fmt::Arguments<'_>) -> io::Result<()> {
    io::stdout().lock().write_fmt(args)
}

fn usage() -> io::Error {
    io::Error::new(io::ErrorKind::InvalidInput, USAGE)
}
