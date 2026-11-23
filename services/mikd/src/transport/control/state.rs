use super::reply;
use crate::{
    domains::chat::{ChatService, Committed, web::WebChat},
    game::{GameBridge, Publication},
};
use mik_protocol::{
    MAX_FRAME_BYTES, MAX_PLAYERS,
    wire::{
        ChatDeliveries, ChatResult, ChatWebRoster, HistoryEntry, PaperStateAccepted,
        PaperStateUpdate, PlayerSnapshot, Response, chat_result,
        response::{Code, Result as ResponseResult},
    },
};
use prost::Message;
use std::{collections::HashSet, sync::Arc, time::Duration};
use tokio::{sync::broadcast, time::timeout};
use uuid::Uuid;

pub(super) struct StateSession<'a> {
    chat: Option<&'a ChatService>,
    web_chat: Option<&'a WebChat>,
    game: Publication,
}

impl<'a> StateSession<'a> {
    pub(super) fn new(
        game: &GameBridge,
        chat: Option<&'a ChatService>,
        web_chat: Option<&'a WebChat>,
        instance: &str,
        session: &str,
    ) -> Self {
        Self {
            chat,
            web_chat,
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
            || next
                .chat_roster
                .as_ref()
                .is_some_and(|roster| roster.players.len() > MAX_PLAYERS)
        {
            return reply(Code::InvalidRequest);
        }
        if !self.game.publish(next.version, server, next.public_roster) {
            return reply(Code::Busy);
        }
        if let Some(chat) = &self.chat {
            chat.project_game(next.chat_roster.map(|roster| roster.players));
        }
        let web_roster = match self.web_chat {
            Some(web) => {
                let roster = web.web_roster();
                (roster.revision != next.known_web_revision).then(|| (*roster).clone())
            }
            None => (next.known_web_revision != 1).then(|| ChatWebRoster {
                revision: 1,
                players: Vec::new(),
            }),
        };
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

pub(super) struct ChatFeed {
    live: broadcast::Receiver<Arc<Committed>>,
    pending: Option<HistoryEntry>,
}

impl ChatFeed {
    pub(super) fn new(chat: &ChatService) -> Self {
        Self {
            live: chat.subscribe(),
            pending: None,
        }
    }

    pub(super) async fn poll(&mut self, deadline: u32) -> Response {
        const BATCH_ITEMS: usize = 16;
        const BATCH_BYTES: usize = MAX_FRAME_BYTES - 128;
        let first = match self.pending.take() {
            Some(entry) => Some(entry),
            None => timeout(Duration::from_millis(u64::from(deadline).min(750)), async {
                loop {
                    match self.live.recv().await {
                        Ok(message) => {
                            if let Some(entry) = message.paper_delivery() {
                                return Some(entry);
                            }
                        }
                        Err(broadcast::error::RecvError::Lagged(count)) => {
                            eprintln!("mikd Paper chat missed {count} live records");
                        }
                        Err(broadcast::error::RecvError::Closed) => return None,
                    }
                }
            })
            .await
            .unwrap_or_default(),
        };
        let mut entries = Vec::new();
        let mut bytes = 0;
        if let Some(first) = first {
            let mut next = Some(first);
            for _ in 0..BATCH_ITEMS {
                let entry = match next.take() {
                    Some(entry) => entry,
                    None => match self.live.try_recv() {
                        Ok(message) => match message.paper_delivery() {
                            Some(entry) => entry,
                            None => continue,
                        },
                        Err(broadcast::error::TryRecvError::Lagged(count)) => {
                            eprintln!("mikd Paper chat missed {count} live records");
                            continue;
                        }
                        Err(_) => break,
                    },
                };
                let size = entry.encoded_len() + 5;
                if size > BATCH_BYTES {
                    eprintln!("mikd Paper chat dropped an oversized live record");
                    continue;
                }
                if bytes + size > BATCH_BYTES {
                    self.pending = Some(entry);
                    break;
                }
                bytes += size;
                entries.push(entry);
            }
        }
        Response {
            code: Code::Ok.into(),
            result: Some(ResponseResult::Chat(ChatResult {
                value: Some(chat_result::Value::Deliveries(ChatDeliveries { entries })),
            })),
        }
    }
}
