use super::reply;
use crate::{
    domains::chat::{ChatService, empty_web_roster, valid_game_roster},
    game::{GameBridge, Publication},
};
use mik_protocol::{
    MAX_PLAYERS,
    wire::{
        PaperStateAccepted, PaperStateUpdate, PlayerSnapshot, Response,
        response::{Code, Result as ResponseResult},
    },
};
use std::collections::HashSet;
use uuid::Uuid;

pub(super) struct StateSession<'a> {
    chat: Option<&'a ChatService>,
    game: Publication,
}

impl<'a> StateSession<'a> {
    pub(super) fn new(
        game: &GameBridge,
        chat: Option<&'a ChatService>,
        instance: &str,
        session: &str,
    ) -> Self {
        Self {
            chat,
            game: game.state.publisher(instance, session),
        }
    }

    pub(super) fn update(&mut self, next: PaperStateUpdate) -> Response {
        let Some(server) = next.server else {
            return reply(Code::InvalidRequest);
        };
        if next.version <= self.game.version {
            return reply(Code::StaleVersion);
        }
        if (self.game.version == 0
            && (next.public_roster.is_none()
                || (self.chat.is_some() && next.chat_roster.is_none())))
            || next
                .public_roster
                .as_ref()
                .is_some_and(|roster| !valid_players(&roster.players))
            || next.chat_roster.as_ref().is_some_and(|roster| {
                roster.players.len() > MAX_PLAYERS || !valid_game_roster(&roster.players)
            })
        {
            return reply(Code::InvalidRequest);
        }
        if !self.game.publish(next.version, server, next.public_roster) {
            return reply(Code::Busy);
        }
        if let Some(chat) = &self.chat {
            chat.project_game(next.chat_roster.map(|roster| roster.players));
        }
        let roster = self
            .chat
            .map_or_else(empty_web_roster, ChatService::web_roster);
        let web_roster = (roster.revision != next.known_web_revision).then(|| (*roster).clone());
        Response {
            code: Code::Ok.into(),
            result: Some(ResponseResult::PaperState(PaperStateAccepted {
                version: next.version,
                web_roster,
            })),
        }
    }
}

impl Drop for StateSession<'_> {
    fn drop(&mut self) {
        if let Some(chat) = &self.chat {
            chat.clear_game();
        }
    }
}

fn valid_players(players: &[PlayerSnapshot]) -> bool {
    let mut ids = HashSet::new();
    players.len() <= MAX_PLAYERS
        && players.iter().all(|player| {
            Uuid::parse_str(&player.player_id).is_ok()
                && ids.insert(&player.player_id)
                && !player.name.is_empty()
                && player.name.encode_utf16().count() <= 64
                && !player.name.chars().any(char::is_control)
        })
}
