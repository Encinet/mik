use crate::transport::admin::{self, Command, Reply};
use std::{
    ffi::OsString,
    io::{self, Write},
    path::PathBuf,
};

const USAGE: &str = "usage: mikd admin [--socket <admin-socket>] <command>
\
  status
\
  music sources <list | inspect <id> | import <url> | refresh | remove <id>>
\
  music library <list [--after <id>] | import <file> | replace <id> <file>>
\
  music track <inspect <track-id> | retire <track-id>>
\
  music lyrics <set <media-version> <file.json|file.lrc|file.txt> | clear <media-version>>";

pub(super) fn run(args: &[OsString]) -> io::Result<()> {
    let (socket, args) = if let [flag, socket, args @ ..] = args
        && flag == "--socket"
    {
        (PathBuf::from(socket), args)
    } else {
        (
            admin::socket_path(&super::config::default_control_socket()),
            args,
        )
    };
    let args = args
        .iter()
        .map(|arg| arg.to_str().ok_or_else(usage))
        .collect::<io::Result<Vec<_>>>()?;
    let command = match args.as_slice() {
        ["help" | "--help" | "-h"] => return print(format_args!("{USAGE}\n")),
        ["status"] => Command::Status,
        ["music", "library", "list"] => Command::ListLibrary(String::new()),
        ["music", "library", "list", "--after", id] => Command::ListLibrary((*id).into()),
        ["music", "track", "inspect", id] => Command::InspectTrack((*id).into()),
        ["music", "library", "import", path] => Command::ImportLocal(std::path::absolute(path)?),
        ["music", "library", "replace", id, path] => Command::ReplaceLocal {
            id: (*id).into(),
            path: std::path::absolute(path)?,
        },
        ["music", "track", "retire", id] => Command::RetireTrack((*id).into()),
        ["music", "lyrics", "set", version, path] => Command::SetLyrics {
            version: (*version).into(),
            path: std::path::absolute(path)?,
        },
        ["music", "lyrics", "clear", version] => Command::ClearLyrics((*version).into()),
        ["music", "sources", "list"] => Command::ListSources,
        ["music", "sources", "inspect", id] => Command::InspectSource((*id).into()),
        ["music", "sources", "import", url] => Command::ImportSource((*url).into()),
        ["music", "sources", "refresh"] => Command::RefreshSources,
        ["music", "sources", "remove", id] => Command::RemoveSource((*id).into()),
        _ => return Err(usage()),
    };
    let reply = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()?
        .block_on(admin::call(&socket, command))?;
    match reply {
        Reply::Error(detail) => Err(io::Error::other(detail)),
        Reply::Sources(sources) => {
            let mut out = io::stdout().lock();
            for source in sources {
                let state = if source.available {
                    "available"
                } else {
                    "unavailable"
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
            let json = serde_json::to_string_pretty(&reply).map_err(io::Error::other)?;
            print(format_args!("{json}\n"))?;
            if matches!(reply, Reply::Refreshed(result) if result.failed > 0) {
                return Err(io::Error::other("some LX subscriptions failed to refresh"));
            }
            Ok(())
        }
    }
}

fn print(args: std::fmt::Arguments<'_>) -> io::Result<()> {
    io::stdout().lock().write_fmt(args)
}

fn usage() -> io::Error {
    io::Error::new(io::ErrorKind::InvalidInput, USAGE)
}
