use super::{ChatService, HistoryError, history::SendReceipt};
use mik_protocol::wire::{
    ChatAuthor, ChatBody, ChatPart, ChatPlayer, ChatPlayerMention, ChatRecord, ChatUserRecord,
    ChatWebPeer, HistoryChannel, HistoryEntry, chat_part::Content, chat_record::Kind,
    chat_user_record::Source,
};
use sha2::{Digest, Sha256};
use std::collections::HashMap;
use tokio::time::Instant;
use uuid::Uuid;

#[derive(Default)]
pub(super) struct SendAllowance {
    traffic: HashMap<String, (f64, Instant)>,
}

impl SendAllowance {
    fn allow_send(&mut self, player: &str) -> bool {
        self.traffic
            .retain(|_, (_, at)| at.elapsed().as_secs() < 120);
        if self.traffic.len() >= 512 && !self.traffic.contains_key(player) {
            return false;
        }
        let (tokens, at) = self
            .traffic
            .entry(player.into())
            .or_insert_with(|| (3.0, Instant::now()));
        *tokens = (*tokens + at.elapsed().as_secs_f64() / 2.0).min(3.0);
        *at = Instant::now();
        if *tokens < 1.0 {
            return false;
        }
        *tokens -= 1.0;
        true
    }
}

pub(super) struct PlayerPost<'a> {
    pub(super) sender: ChatPlayer,
    pub(super) staff: bool,
    pub(super) channel: HistoryChannel,
    pub(super) target: Option<&'a str>,
    pub(super) text: &'a str,
    pub(super) request_id: &'a str,
}

pub(super) enum SendError {
    Invalid,
    StaffRequired,
    RecipientUnavailable,
    RateLimited,
    Commit(HistoryError),
}

impl ChatService {
    pub(super) async fn send_player(
        &self,
        post: PlayerPost<'_>,
        web: &[ChatWebPeer],
    ) -> Result<String, SendError> {
        let PlayerPost {
            sender,
            staff,
            channel,
            target: requested_target,
            text,
            request_id,
        } = post;
        let text = text.trim();
        if text.is_empty()
            || text.chars().count() > 256
            || text
                .chars()
                .any(|ch| ch.is_control() || matches!(ch, '\u{2028}' | '\u{2029}'))
        {
            return Err(SendError::Invalid);
        }
        if channel == HistoryChannel::HistoryStaff && !staff {
            return Err(SendError::StaffRequired);
        }
        let game = self.game().ok_or(SendError::Commit(HistoryError::Busy))?;
        let target = requested_target
            .map(|id| {
                game.player(&sender.id, id, web)
                    .filter(|player| player.id != sender.id)
                    .cloned()
                    .ok_or(SendError::RecipientUnavailable)
            })
            .transpose()?;
        let allowed = self
            .allowance
            .lock()
            .map_err(|_| SendError::Commit(HistoryError::Busy))?
            .allow_send(&sender.id);
        if !allowed {
            return Err(SendError::RateLimited);
        }
        let sender_id = sender.id.clone();
        let target_id = target.as_ref().map_or("", |player| player.id.as_str());
        let fingerprint = hex::encode(Sha256::digest(format!(
            "{}\0{target_id}\0{text}",
            channel.as_str_name()
        )));
        let receipt = SendReceipt {
            sender: sender.id.clone(),
            request_id: request_id.into(),
            fingerprint,
        };
        let participants = target.as_ref().map_or_else(Vec::new, |target| {
            vec![sender.id.clone(), target.id.clone()]
        });
        let entry = HistoryEntry {
            message_id: Uuid::new_v4().to_string(),
            channel: channel.into(),
            participants,
            record: Some(ChatRecord {
                created_at: super::now(),
                kind: Some(Kind::User(ChatUserRecord {
                    source: Source::Web.into(),
                    author: Some(ChatAuthor {
                        display_name: sender.name.clone(),
                        player: Some(sender),
                        external: None,
                    }),
                    target,
                    body: Some(ChatBody {
                        parts: vec![part(Content::Text(text.into()))],
                    }),
                    social: None,
                })),
            }),
            ..HistoryEntry::default()
        };
        let mut peers = None;
        self.publish_user(entry, Some(receipt), |name| {
            let peers = peers.get_or_insert_with(|| game.peers(&sender_id, web));
            let mut candidates = peers.iter().filter(|peer| {
                peer.player.name.eq_ignore_ascii_case(name)
                    && peer.player.id != sender_id
                    && match channel {
                        HistoryChannel::HistoryStaff => peer.staff,
                        HistoryChannel::HistoryPrivate => {
                            requested_target == Some(peer.player.id.as_str())
                        }
                        _ => true,
                    }
            });
            let candidate = candidates.next()?;
            candidates
                .next()
                .is_none()
                .then(|| candidate.player.clone())
        })
        .await
        .map(|saved| saved.message_id)
        .map_err(SendError::Commit)
    }
}

pub(super) fn resolve_mentions(
    body: &mut ChatBody,
    mut resolve: impl FnMut(&str) -> Option<ChatPlayer>,
) {
    let mut remaining = 64;
    body.parts = std::mem::take(&mut body.parts)
        .into_iter()
        .flat_map(|part| {
            let Some(Content::Text(text)) = &part.content else {
                return vec![part];
            };
            if remaining == 0 || !text.contains('@') {
                return vec![part];
            }
            let before = remaining;
            let mut parts = mentions(text, |name| {
                if remaining == 0 {
                    return None;
                }
                let player = resolve(name)?;
                remaining -= 1;
                Some(player)
            });
            if before == remaining {
                return vec![part];
            }
            for node in &mut parts {
                node.style = part.style;
            }
            parts
        })
        .collect();
}

fn mentions(text: &str, mut resolve: impl FnMut(&str) -> Option<ChatPlayer>) -> Vec<ChatPart> {
    let mut parts = Vec::new();
    let mut start = 0;
    for (index, _) in text.match_indices('@') {
        if text[..index]
            .chars()
            .next_back()
            .is_some_and(|ch| ch.is_ascii_alphanumeric() || ch == '_')
        {
            continue;
        }
        let tail = text[index + 1..].trim_start_matches([' ', '\t']);
        let name_start = text.len() - tail.len();
        let length = tail
            .bytes()
            .take_while(|byte| byte.is_ascii_alphanumeric() || *byte == b'_')
            .count();
        if length == 0 {
            continue;
        }
        let Some(player) = resolve(&tail[..length]) else {
            continue;
        };
        if start < index {
            parts.push(part(Content::Text(text[start..index].into())));
        }
        parts.push(part(Content::PlayerMention(ChatPlayerMention {
            player: Some(player),
            source_identity: None,
        })));
        start = name_start + length;
    }
    if start < text.len() {
        parts.push(part(Content::Text(text[start..].into())));
    }
    parts
}

fn part(content: Content) -> ChatPart {
    ChatPart {
        style: None,
        content: Some(content),
    }
}
