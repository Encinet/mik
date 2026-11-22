mod ai;
mod bindings;
mod game;

use super::{
    chat::Inbound,
    config::{CommandPrefix, Conversation},
    locale::{Locale, arguments},
    platform::ReplyDelivery,
    runtime::{ReplyBody, Runtime},
};
use crate::domains::ai::DeliveryReceipt;
pub(super) use bindings::Confirmations;
use mik_protocol::wire::ChatExternalIdentity;
use std::{collections::HashMap, sync::LazyLock};

#[derive(Clone, Copy)]
enum Kind {
    Online,
    Status,
    Profile,
    Help,
    Bind,
    Unbind,
    Ai,
    Unknown,
}

impl Kind {
    fn is_private(self) -> bool {
        matches!(self, Self::Bind | Self::Unbind | Self::Ai)
    }
}

const COMMANDS: &[(Kind, &str, &str, Option<&str>)] = &[
    (
        Kind::Online,
        "online",
        "social-online-command-description",
        None,
    ),
    (
        Kind::Status,
        "status",
        "social-status-command-description",
        None,
    ),
    (
        Kind::Profile,
        "profile",
        "social-profile-command-description",
        Some("social-profile-command-parameters"),
    ),
    (
        Kind::Bind,
        "bind",
        "social-link-command-description",
        Some("social-link-command-parameters"),
    ),
    (
        Kind::Unbind,
        "unbind",
        "social-unlink-command-description",
        Some("social-unlink-command-parameters"),
    ),
    (
        Kind::Ai,
        "ai",
        "ai-social-command-description",
        Some("ai-social-command-parameters"),
    ),
    (Kind::Help, "help", "social-help-command-description", None),
];

static ALIASES: LazyLock<HashMap<String, Kind>> = LazyLock::new(|| {
    let mut aliases = HashMap::new();
    for &(kind, name, _, _) in COMMANDS {
        let id = format!("social-aliases-{name}");
        for locale in Locale::all() {
            for alias in locale.text(&id).split_whitespace() {
                aliases.insert(alias.to_lowercase(), kind);
            }
        }
        aliases.insert(name.into(), kind);
    }
    aliases
});

pub(super) enum Input {
    Chat,
    Command(Command),
}

pub(super) struct Command {
    kind: Kind,
    argument: String,
    prefix: CommandPrefix,
}

impl Command {
    pub(super) fn ai(argument: String, prefix: CommandPrefix) -> Self {
        Self {
            kind: Kind::Ai,
            argument,
            prefix,
        }
    }

    pub(super) fn is_ai(&self) -> bool {
        matches!(self.kind, Kind::Ai)
    }

    fn recognized(&self) -> bool {
        !matches!(self.kind, Kind::Unknown)
    }
}

pub(super) struct Reply {
    pub(super) text: String,
    pub(super) dynamic: bool,
    pub(super) skin_texture: Option<String>,
}

pub(super) enum Response {
    Plain(Reply),
    Approved(ReplyBody, DeliveryReceipt),
}

impl Reply {
    fn fixed(text: impl Into<String>) -> Self {
        Self {
            text: text.into(),
            dynamic: false,
            skin_texture: None,
        }
    }
    fn dynamic(text: String) -> Self {
        Self {
            text,
            dynamic: true,
            skin_texture: None,
        }
    }

    fn profile(text: String, skin_texture: String) -> Self {
        Self {
            skin_texture: (!skin_texture.is_empty()).then_some(skin_texture),
            ..Self::dynamic(text)
        }
    }
}

pub(super) fn classify(
    input: &str,
    commands: bool,
    route: &Conversation,
    prefix: CommandPrefix,
) -> Option<Input> {
    match parse_incoming(input, route, prefix) {
        Some(command) if commands && route.commands && command.prefix == prefix => {
            Some(Input::Command(command))
        }
        Some(command) if command.kind.is_private() => None,
        _ => Some(Input::Chat),
    }
}

fn parse_incoming(input: &str, route: &Conversation, prefix: CommandPrefix) -> Option<Command> {
    let raw = parse(input, prefix);
    if raw.as_ref().is_some_and(Command::recognized) {
        raw
    } else {
        route
            .inbound
            .as_ref()
            .and_then(|direction| direction.after_prefix(input))
            .and_then(|text| parse(text, prefix))
            .or(raw)
    }
}

fn parse(input: &str, configured: CommandPrefix) -> Option<Command> {
    let (marker, input) = input.trim_start().split_at_checked(1)?;
    let prefix = match marker {
        "/" => CommandPrefix::Slash,
        "!" => CommandPrefix::Bang,
        _ => return None,
    };
    let (word, argument) = input.split_once(char::is_whitespace).unwrap_or((input, ""));
    let kind = ALIASES
        .get(&word.trim_start_matches(['/', '!']).to_lowercase())
        .copied()
        .unwrap_or(Kind::Unknown);
    if prefix != configured && !kind.is_private() {
        return None;
    }
    Some(Command {
        kind,
        argument: argument.trim().into(),
        prefix,
    })
}

pub(super) async fn answer(
    runtime: &Runtime,
    event: &Inbound,
    route: &Conversation,
    locale: &Locale,
    command: Command,
    replied_author: Option<impl Future<Output = Option<ChatExternalIdentity>>>,
    delivery: ReplyDelivery<'_>,
) -> Response {
    let argument = command.argument.as_str();
    let prefix = command.prefix.as_str();
    let fixed = |id| Reply::fixed(locale.text(id));
    let reply = match command.kind {
        Kind::Online if argument.is_empty() => game::online(runtime, locale),
        Kind::Status if argument.is_empty() => game::status(runtime, locale),
        Kind::Profile => game::profile(runtime, event, locale, argument, replied_author).await,
        Kind::Bind => bindings::link(runtime, event, locale, argument, prefix).await,
        Kind::Unbind => bindings::unlink(runtime, event, locale, argument, prefix).await,
        Kind::Ai if route.ai => {
            return ai::answer(runtime, event, locale, argument, delivery, prefix).await;
        }
        Kind::Ai => fixed("ai-disabled"),
        Kind::Help if argument.is_empty() => {
            let mut lines = vec![locale.text("social-title-command-help")];
            for &(kind, name, description, parameters) in COMMANDS {
                if matches!(kind, Kind::Ai) && !route.ai {
                    continue;
                }
                let parameters = parameters
                    .map(|id| format!(" {}", locale.text(id)))
                    .unwrap_or_default();
                lines.push(format!(
                    "{prefix}{name}{parameters} — {}",
                    locale.text(description)
                ));
            }
            Reply::fixed(lines.join("\n"))
        }
        Kind::Unknown => Reply::fixed(locale.format(
            "social-query-unknown",
            arguments([format!("{prefix}help").into()]),
        )),
        _ => Reply::fixed(format!(
            "{} {prefix}help",
            locale.text("social-command-usage")
        )),
    };
    Response::Plain(reply)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn social_commands_require_the_configured_prefix() {
        let route: Conversation =
            serde_yaml_ng::from_str("commands: true\ninbound: { prefix: '> ', strip: true }")
                .unwrap();

        let prefix = CommandPrefix::Bang;
        for message in [
            "?",
            " ? ",
            "> ?",
            "help",
            "帮助",
            "> 帮助",
            "online",
            "status",
            "/help",
            "> /帮助",
        ] {
            assert!(
                matches!(classify(message, true, &route, prefix), Some(Input::Chat)),
                "{message:?} must be treated as chat"
            );
        }
        for message in ["!help", "!帮助", " !help ", "> !help"] {
            assert!(
                matches!(
                    classify(message, true, &route, prefix),
                    Some(Input::Command(Command {
                        kind: Kind::Help,
                        ..
                    }))
                ),
                "{message:?} must be treated as help"
            );
        }
        assert!(matches!(
            classify("/帮助", true, &route, CommandPrefix::Slash),
            Some(Input::Command(Command {
                kind: Kind::Help,
                ..
            }))
        ));
        assert!(matches!(
            classify("!help", true, &route, CommandPrefix::Slash),
            Some(Input::Chat)
        ));
        assert!(classify("> /bind sample-code", true, &route, prefix).is_none());
        assert!(classify("!绑定 sample-code", true, &route, CommandPrefix::Slash).is_none());
    }

    #[test]
    fn retaining_an_inbound_prefix_never_turns_a_binding_command_into_chat() {
        let route: Conversation =
            serde_yaml_ng::from_str("inbound: { prefix: '> ', strip: false }").unwrap();
        let prefix = CommandPrefix::Slash;
        let command = parse_incoming("> /bind sample-code", &route, prefix).unwrap();

        assert!(!route.commands);
        assert!(command.kind.is_private());
        assert!(matches!(command.kind, Kind::Bind));
        assert_eq!(command.argument, "sample-code");
        assert!(classify("> /bind sample-code", true, &route, prefix).is_none());
        assert!(parse_incoming("> hello /bind sample-code", &route, prefix).is_none());
    }
}
