use crate::domains::{ai::AiConfig, music, social};
use std::{
    env,
    ffi::{OsStr, OsString},
    fs, io,
    path::{Path, PathBuf},
    process::Command as Process,
};

const WEB_API_PORT: u16 = 35353;
const WEB_CHAT_PORT: u16 = 35354;
const WEB_CHAT_ORIGIN: &str = "https://mcmik.top";

pub(super) enum Command {
    Serve,
    Validate,
}

impl Command {
    fn optional<T>(&self, name: &str, result: io::Result<Option<T>>) -> io::Result<Option<T>> {
        match (self, result) {
            (_, Ok(config)) => Ok(config),
            (Self::Serve, Err(error)) => {
                eprintln!("mikd {name} disabled until restart: invalid configuration: {error}");
                Ok(None)
            }
            (Self::Validate, Err(error)) => Err(io::Error::new(
                error.kind(),
                format!("{name} configuration: {error}"),
            )),
        }
    }
}

pub(super) struct Config {
    pub(super) control_socket: PathBuf,
    pub(super) game_uid: u32,
    pub(super) history: PathBuf,
    pub(super) community: PathBuf,
    pub(super) announcements: PathBuf,
    pub(super) web_api: Option<u16>,
    pub(super) web_chat: Option<WebChatConfig>,
    pub(super) social: Option<social::Config>,
    pub(super) ai: Option<AiConfig>,
    pub(super) music: MusicStartup,
}

pub(super) struct WebChatConfig {
    pub(super) port: u16,
    pub(super) key: PathBuf,
    pub(super) origin: String,
}

pub(super) struct MusicStartup {
    pub(super) socket: PathBuf,
    pub(super) service: music::Config,
}

struct Launch {
    control_socket: PathBuf,
    game_uid: u32,
    settings: PathBuf,
    state: PathBuf,
    cache: PathBuf,
    web_api: Option<u16>,
    web_chat: Option<u16>,
}

impl Launch {
    fn parse(args: &[OsString]) -> io::Result<Self> {
        let account = match args {
            [flag, args @ ..] if flag == "--instance" => return Self::isolated(args),
            [] => OsStr::new("paper"),
            [account] => account.as_os_str(),
            _ => return Err(usage()),
        };
        Ok(Self {
            control_socket: default_control_socket(),
            game_uid: game_uid(account)?,
            settings: "/etc/mikd".into(),
            state: "/var/lib/mikd".into(),
            cache: "/var/cache/mikd".into(),
            web_api: Some(WEB_API_PORT),
            web_chat: Some(WEB_CHAT_PORT),
        })
    }

    fn isolated(args: &[OsString]) -> io::Result<Self> {
        let [socket, uid, state, ports @ ..] = args else {
            return Err(instance_usage());
        };
        if ports.len() > 2 {
            return Err(instance_usage());
        }
        let state = absolute_path(state, "instance directory")?;
        Ok(Self {
            control_socket: socket_path(socket.into())?,
            game_uid: uid
                .to_str()
                .and_then(|uid| uid.parse().ok())
                .ok_or_else(instance_usage)?,
            settings: state.join("config"),
            cache: state.join("cache"),
            state,
            web_api: ports.first().map(|port| instance_port(port)).transpose()?,
            web_chat: ports.get(1).map(|port| instance_port(port)).transpose()?,
        })
    }
}

impl Config {
    pub(super) fn load(args: &[OsString]) -> io::Result<(Command, Self)> {
        let [action, args @ ..] = args else {
            return Err(usage());
        };
        let command = match action.to_str() {
            Some("serve") => Command::Serve,
            Some("validate") => Command::Validate,
            _ => return Err(usage()),
        };
        let launch = Launch::parse(args)?;
        let music = music_config(&launch, matches!(command, Command::Validate))?;
        let social = config_file(&launch.settings, "social.yml")?
            .map(|path| {
                social::Config::load(&path, launch.state.join("social"))
                    .map_err(|error| config_error(&path, &error))
            })
            .transpose()?;
        let ai = command.optional("AI assistant", ai_config(&launch))?;
        let web_chat = launch.web_chat.map(|port| WebChatConfig {
            port,
            key: launch.state.join("web-chat.key"),
            origin: WEB_CHAT_ORIGIN.into(),
        });
        Ok((
            command,
            Self {
                control_socket: launch.control_socket,
                game_uid: launch.game_uid,
                history: launch.state.join("chat-messages-v6.sqlite"),
                community: launch.state.join("community.db"),
                announcements: launch.state.join("announcements"),
                web_api: launch.web_api,
                web_chat,
                social,
                ai,
                music,
            },
        ))
    }
}

pub(super) fn default_control_socket() -> PathBuf {
    "/run/mik/mikd.sock".into()
}

fn usage() -> io::Error {
    invalid("usage: mikd <serve|validate> [paper-account-or-uid] | mikd admin --help")
}

fn instance_usage() -> io::Error {
    invalid(
        "usage: mikd <serve|validate> --instance <socket> <uid> <directory> [api-port [chat-port]]",
    )
}

fn instance_port(port: &OsStr) -> io::Result<u16> {
    port.to_str()
        .and_then(|port| port.parse().ok())
        .ok_or_else(instance_usage)
}

fn game_uid(account: &OsStr) -> io::Result<u32> {
    if let Some(uid) = account.to_str().and_then(|account| account.parse().ok()) {
        return Ok(uid);
    }
    let output = Process::new("/usr/bin/id")
        .env_clear()
        .args(["-u", "--"])
        .arg(account)
        .output()?;
    std::str::from_utf8(&output.stdout)
        .ok()
        .filter(|_| output.status.success())
        .and_then(|uid| uid.trim().parse().ok())
        .ok_or_else(|| {
            invalid(format!(
                "cannot resolve Paper account {}",
                account.to_string_lossy()
            ))
        })
}

fn music_config(launch: &Launch, validate: bool) -> io::Result<MusicStartup> {
    let mut path = launch.control_socket.as_os_str().to_owned();
    path.push(".music.sock");
    Ok(MusicStartup {
        socket: socket_path(path.into())?,
        service: music::Config::load(&launch.state, &launch.cache, validate)?,
    })
}

fn ai_config(launch: &Launch) -> io::Result<Option<AiConfig>> {
    let Some(path) = config_file(&launch.settings, "ai.yml")? else {
        return Ok(None);
    };
    let api_key = optional_secret("MIKD_MODEL_API_KEY")?;
    let tavily_api_key = optional_secret("MIKD_TAVILY_API_KEY")?;
    AiConfig::load(
        &path,
        &launch.state,
        Some(launch.cache.join("minecraft-wiki")),
        api_key.as_deref(),
        tavily_api_key.as_deref(),
    )
    .map(Some)
    .map_err(|error| config_error(&path, &error))
}

fn optional_secret(name: &str) -> io::Result<Option<String>> {
    match env::var(name) {
        Ok(key) => Ok(Some(key)),
        Err(env::VarError::NotPresent) => Ok(None),
        Err(env::VarError::NotUnicode(_)) => Err(invalid(format!("{name} must be UTF-8"))),
    }
}

fn config_file(directory: &Path, name: &str) -> io::Result<Option<PathBuf>> {
    let path = directory.join(name);
    match fs::metadata(&path) {
        Ok(metadata) if metadata.is_file() => Ok(Some(path)),
        Ok(_) => Err(invalid(format!(
            "{} must be a regular file",
            path.display()
        ))),
        Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(None),
        Err(error) => Err(config_error(&path, &error)),
    }
}

fn config_error(path: &Path, error: &io::Error) -> io::Error {
    io::Error::new(error.kind(), format!("{}: {error}", path.display()))
}

fn socket_path(path: PathBuf) -> io::Result<PathBuf> {
    if !path.is_absolute() || path.file_name().is_none() || path.as_os_str().len() > 100 {
        return Err(invalid("socket must be an absolute file path <= 100 bytes"));
    }
    Ok(path)
}

fn absolute_path(path: &OsStr, name: &str) -> io::Result<PathBuf> {
    let path = PathBuf::from(path);
    if !path.is_absolute() {
        return Err(invalid(format!("{name} must be an absolute path")));
    }
    Ok(path)
}

fn invalid(error: impl Into<Box<dyn std::error::Error + Send + Sync>>) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidInput, error)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn standard_launch_uses_fixed_paths_and_loopback_ports() {
        let launch = Launch::parse(&["1234".into()]).unwrap();
        assert_eq!(launch.game_uid, 1234);
        assert_eq!(launch.control_socket, Path::new("/run/mik/mikd.sock"));
        assert_eq!(launch.settings, Path::new("/etc/mikd"));
        assert_eq!(launch.state, Path::new("/var/lib/mikd"));
        assert_eq!(launch.cache, Path::new("/var/cache/mikd"));
        assert_eq!(launch.web_api, Some(35353));
        assert_eq!(launch.web_chat, Some(35354));
    }
}
