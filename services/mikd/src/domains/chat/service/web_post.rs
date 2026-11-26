use super::{ChatService, require_readable_recipient};
use crate::domains::chat::{
    BodyInput, HistoryError,
    history::{Admission, Append, SendReceipt},
    model::{Channel, player_principal, room},
    now,
};
use mik_protocol::wire::{
    ChatDestination, ChatEvent, ChatPlayer, ChatRecord, ChatTextRun, ChatUserRecord,
    chat_destination::{Room, Target},
    chat_record::Kind,
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

pub(in crate::domains::chat) struct WebPost<'a> {
    pub(in crate::domains::chat) sender: ChatPlayer,
    pub(in crate::domains::chat) staff: bool,
    pub(in crate::domains::chat) channel: Channel,
    pub(in crate::domains::chat) target: Option<&'a str>,
    pub(in crate::domains::chat) text: &'a str,
    pub(in crate::domains::chat) request_id: &'a str,
}

pub(in crate::domains::chat) enum SendError {
    Invalid,
    StaffRequired,
    RecipientUnavailable,
    RateLimited,
    Commit(HistoryError),
}

impl ChatService {
    pub(in crate::domains::chat) async fn publish_web(
        &self,
        post: WebPost<'_>,
        authorized: impl Fn() -> bool + Send + Sync + 'static,
    ) -> Result<String, SendError> {
        let WebPost {
            sender,
            staff,
            channel,
            target: requested_target,
            text,
            request_id,
        } = post;
        let text = checked_text(text)?;
        if channel == Channel::Staff && !staff {
            return Err(SendError::StaffRequired);
        }
        let receipt = web_receipt(&sender, request_id, channel, requested_target, text);
        if let Some(id) = self
            .history()
            .web_receipt(receipt.clone())
            .await
            .map_err(SendError::Commit)?
        {
            return Ok(id);
        }
        let game = self.game().ok_or(SendError::Commit(HistoryError::Busy))?;
        let roster = self.web_roster();
        let web = &roster.players;
        let target = requested_target
            .map(|id| {
                game.player(&sender.id, id, web)
                    .filter(|player| player.id != sender.id)
                    .cloned()
                    .ok_or(SendError::RecipientUnavailable)
            })
            .transpose()?;
        let allowed = self
            .web_allowance
            .lock()
            .map_err(|_| SendError::Commit(HistoryError::Busy))?
            .allow_send(&sender.id);
        if !allowed {
            return Err(SendError::RateLimited);
        }
        let destination = player_destination(channel, target)?;
        let mut entry = ChatEvent {
            message_id: Uuid::new_v4().to_string(),
            record: Some(ChatRecord {
                created_at_ms: now(),
                platform: "web".into(),
                kind: Some(Kind::User(ChatUserRecord {
                    destination: Some(destination),
                    author: Some(player_principal(sender)),
                    body: None,
                })),
            }),
            ..ChatEvent::default()
        };
        self.prepare_post(
            &mut entry,
            [BodyInput::Text(ChatTextRun {
                text: text.into(),
                style: None,
            })],
            Some(&game),
            web,
        )
        .map_err(SendError::Commit)?;
        require_readable_recipient(&entry).map_err(send_error)?;
        self.commit(Append {
            entry,
            admission: Admission::Web(receipt, Box::new(authorized)),
        })
        .await
        .map_err(send_error)
    }
}

fn send_error(error: HistoryError) -> SendError {
    match error {
        HistoryError::RecipientUnavailable => SendError::RecipientUnavailable,
        other => SendError::Commit(other),
    }
}

fn checked_text(text: &str) -> Result<&str, SendError> {
    let text = text.trim();
    if text.is_empty()
        || text.chars().count() > 256
        || text
            .chars()
            .any(|ch| ch.is_control() || matches!(ch, '\u{2028}' | '\u{2029}'))
    {
        return Err(SendError::Invalid);
    }
    Ok(text)
}

fn player_destination(
    channel: Channel,
    target: Option<ChatPlayer>,
) -> Result<ChatDestination, SendError> {
    match (channel, target) {
        (Channel::Public, None) => Ok(room(Room::Public)),
        (Channel::Staff, None) => Ok(room(Room::Staff)),
        (Channel::Private, Some(recipient)) => Ok(ChatDestination {
            target: Some(Target::Recipient(recipient)),
        }),
        _ => Err(SendError::Invalid),
    }
}

fn web_receipt(
    sender: &ChatPlayer,
    request_id: &str,
    channel: Channel,
    target: Option<&str>,
    text: &str,
) -> SendReceipt {
    let fingerprint = Sha256::digest(format!(
        "{}\0{}\0{text}",
        channel.name(),
        target.unwrap_or_default()
    ));
    SendReceipt {
        sender: sender.id.clone(),
        request_id: request_id.into(),
        fingerprint: hex::encode(fingerprint),
    }
}
