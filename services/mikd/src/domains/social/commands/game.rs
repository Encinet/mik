use super::super::chat::binding_key;
use super::{Inbound, Locale, Reply, Runtime, arguments};
use crate::game::{GameResult, Operation};
use mik_protocol::wire::{
    ChatExternalIdentity, GameProfileQuery, GameProfileView, game_profile_query::Target,
    game_profile_view::Presence,
};

pub(super) fn online(runtime: &Runtime, locale: &Locale) -> Reply {
    let Some(snapshot) = runtime.game.state.current() else {
        return unavailable(locale);
    };
    let mut players: Vec<_> = snapshot.players.iter().collect();
    if players.is_empty() {
        return Reply::fixed(locale.text("social-query-no-players"));
    }
    players.sort_by_cached_key(|player| player.name.to_lowercase());
    let afk = players.iter().filter(|player| player.afk).count();
    let names: Vec<_> = players
        .into_iter()
        .map(|player| {
            locale.format(
                "social-query-online-player",
                arguments([
                    player.name.clone().into(),
                    if player.afk { "afk" } else { "active" }.into(),
                ]),
            )
        })
        .collect();
    Reply::dynamic(format!(
        "{}\n{}",
        locale.format(
            "social-query-online-summary",
            arguments([names.len().into(), afk.into()])
        ),
        names.join(" · ")
    ))
}

pub(super) fn status(runtime: &Runtime, locale: &Locale) -> Reply {
    let Some(snapshot) = runtime.game.state.current() else {
        return unavailable(locale);
    };
    let facts = &snapshot.server;
    let online = snapshot.players.len();
    let away = snapshot.players.iter().filter(|player| player.afk).count();
    let players = locale.format(
        "social-query-population",
        arguments([
            online.into(),
            facts.max_players.into(),
            (online - away).into(),
            away.into(),
        ]),
    );
    Reply::dynamic(format!(
        "{}\n{}: {players}\nTPS: {:.2} · MSPT: {:.2}\n{}: {}\n{}: {}",
        locale.text("social-title-server-status"),
        locale.text("social-query-players-label"),
        facts.tps,
        facts.mspt,
        locale.text("social-query-uptime-label"),
        duration(locale, facts.uptime_seconds),
        locale.text("social-query-version-label"),
        facts.version,
    ))
}

pub(super) async fn profile(
    runtime: &Runtime,
    event: &Inbound,
    locale: &Locale,
    argument: &str,
    replied_author: Option<impl Future<Output = Option<ChatExternalIdentity>>>,
) -> Reply {
    let fixed = |id| Reply::fixed(locale.text(id));
    let Some(source) = binding_key(&event.external()) else {
        return fixed("social-binding-not-bound");
    };
    let bindings = runtime.identity.bindings();
    let Some(binding) = bindings.find(&source) else {
        return fixed("social-binding-not-bound");
    };
    let mut mentions = event.mentions();
    let mention = mentions.next();
    if mentions.next().is_some() || (mention.is_some() && replied_author.is_some()) {
        return fixed("social-profile-target-ambiguous");
    }
    let reply_author = if argument.is_empty()
        && let Some(resolve) = replied_author
    {
        let Some(author) = resolve.await else {
            return fixed("social-profile-target-unbound");
        };
        Some(author)
    } else {
        None
    };
    let target = match mention
        .map(|(_, identity)| identity)
        .or(reply_author.as_ref())
    {
        Some(external) => {
            if mention.is_some_and(|(range, _)| event.content[range.clone()].trim() != argument) {
                return fixed("social-profile-target-ambiguous");
            }
            let Some(target) = binding_key(external).and_then(|key| bindings.find(&key)) else {
                return fixed("social-profile-target-unbound");
            };
            Target::PlayerUuid(target.account.to_string())
        }
        None if argument.is_empty() => Target::PlayerUuid(binding.account.to_string()),
        None if argument.len() <= 64 && !argument.chars().any(char::is_control) => {
            Target::PlayerName(argument.into())
        }
        None => return fixed("social-profile-player-not-found"),
    };
    let viewer_uuid = binding.account.to_string();
    let community_authorized = if matches!(&target, Target::PlayerUuid(id) if id == &viewer_uuid) {
        true
    } else {
        match runtime.access.community_profile_access(&viewer_uuid).await {
            Some(allowed) => allowed,
            None => return unavailable(locale),
        }
    };
    let expected = binding.id;
    let identity = runtime.identity.clone();
    let answer = runtime
        .game
        .query_guarded(
            Operation::GameProfile(GameProfileQuery {
                viewer_uuid,
                target: Some(target),
                community_authorized,
            }),
            move || {
                identity
                    .bindings()
                    .find(&source)
                    .is_some_and(|binding| binding.id == expected)
            },
        )
        .await;
    let result = match answer {
        Some(GameResult::GameProfile(result)) => result,
        Some(GameResult::NotFound(_)) => return fixed("social-profile-player-not-found"),
        _ => return unavailable(locale),
    };
    render_profile(locale, result)
}

fn render_profile(locale: &Locale, result: GameProfileView) -> Reply {
    if result.forbidden {
        return Reply::fixed(locale.text("social-profile-member-required"));
    }
    let presence = locale.text(match result.presence() {
        Presence::Online => "social-profile-online",
        Presence::Afk => "social-profile-afk",
        Presence::Offline => "social-profile-offline",
    });
    let Some(player) = result.player else {
        return unavailable(locale);
    };
    let mut lines = vec![locale.text("social-title-player-profile")];
    for (label, value) in [
        ("player", player.name),
        ("uuid", player.id),
        ("presence", presence),
        ("play-time", duration(locale, result.play_seconds)),
        ("first-joined", result.first_joined),
        ("last-seen", result.last_seen),
    ] {
        if !value.is_empty() {
            lines.push(format!(
                "{}: {value}",
                locale.text(&format!("social-profile-{label}-label"))
            ));
        }
    }
    Reply::profile(lines.join("\n"), result.skin_texture_url)
}

fn duration(locale: &Locale, seconds: i64) -> String {
    let mut parts = Vec::new();
    for (unit, count) in [
        ("days", seconds / 86400),
        ("hours", seconds / 3600 % 24),
        ("minutes", seconds / 60 % 60),
    ] {
        if count > 0 || (unit == "minutes" && parts.is_empty()) {
            parts.push(locale.format(
                &format!("social-duration-{unit}"),
                arguments([count.into()]),
            ));
        }
    }
    parts.join(" ")
}

fn unavailable(locale: &Locale) -> Reply {
    Reply::fixed(locale.text("social-reply-unavailable"))
}
