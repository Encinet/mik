use super::platform::{matrix::Matrix, qq::Qq};
use serde::Deserialize;
use std::{
    env, fs, io,
    path::{Path, PathBuf},
};

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Config {
    #[serde(skip)]
    pub(super) state: PathBuf,
    pub(super) qq: Option<Qq>,
    pub(super) matrix: Option<Matrix>,
}

#[derive(Clone, Copy, Default, Deserialize, Eq, PartialEq)]
pub(super) enum CommandPrefix {
    #[default]
    #[serde(rename = "/")]
    Slash,
    #[serde(rename = "!")]
    Bang,
}

impl CommandPrefix {
    pub(super) const fn as_str(self) -> &'static str {
        match self {
            Self::Slash => "/",
            Self::Bang => "!",
        }
    }
}

#[derive(Deserialize)]
#[serde(default, deny_unknown_fields)]
pub(super) struct Conversation {
    pub(super) inbound: Option<Direction>,
    pub(super) outbound: Option<Direction>,
    pub(super) commands: bool,
    pub(super) membership: bool,
    pub(super) locale: String,
    pub(super) ai: bool,
}

impl Default for Conversation {
    fn default() -> Self {
        Self {
            inbound: None,
            outbound: None,
            commands: false,
            membership: false,
            locale: "en-US".into(),
            ai: false,
        }
    }
}

#[derive(Default, Deserialize)]
#[serde(default, deny_unknown_fields)]
pub(super) struct Direction {
    pub(super) prefix: String,
    pub(super) strip: bool,
}

impl Direction {
    pub(super) fn after_prefix<'a>(&self, text: &'a str) -> Option<&'a str> {
        text.strip_prefix(&self.prefix)
    }

    pub(super) fn accept<'a>(&self, text: &'a str, has_description: bool) -> Option<&'a str> {
        let rest = self.after_prefix(text)?;
        let value = if self.strip { rest.trim_start() } else { text };
        if value.trim().is_empty() {
            has_description.then_some(&text[text.len()..])
        } else {
            Some(value)
        }
    }
}

impl Config {
    /// # Errors
    /// Rejects unreadable configuration and non-HTTPS homeservers.
    pub(crate) fn load(path: &Path, state: PathBuf) -> io::Result<Self> {
        let mut config: Self =
            serde_yaml_ng::from_slice(&fs::read(path)?).map_err(io::Error::other)?;
        config.state = state;
        if config.qq.is_none() && config.matrix.is_none() {
            return Err(io::Error::other("social config must enable a platform"));
        }
        if let Some(matrix) = &config.matrix {
            matrix.validate()?;
        }
        if config
            .qq
            .iter()
            .flat_map(|qq| qq.groups.values())
            .chain(
                config
                    .matrix
                    .iter()
                    .flat_map(|matrix| matrix.rooms.values()),
            )
            .any(|route| !super::locale::Locale::supports(&route.locale))
        {
            return Err(io::Error::other("unsupported social locale"));
        }
        Ok(config)
    }
}

pub(super) fn secret(name: &str) -> io::Result<String> {
    env::var(name)
        .ok()
        .filter(|secret| !secret.trim().is_empty())
        .ok_or_else(|| io::Error::other(format!("{name} is required")))
}
