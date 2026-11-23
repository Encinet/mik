use super::{
    View, render, respond,
    session::{Lease, WebChat},
    token,
};
use crate::domains::chat::{
    HistoryError,
    history::RETENTION_MS,
    now,
    players::{PlayerPost, SendError},
    presence::GameView,
    service::Committed,
};
use mik_protocol::wire::{ChatPlayer, ChatWebPeer, HistoryChannel};
use serde::{Deserialize, Serialize};
use serde_json::{Value, json};
use std::collections::HashSet;
use uuid::Uuid;

#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub(super) struct Request<'a> {
    #[serde(rename = "type")]
    kind: &'a str,
    #[serde(rename = "requestId")]
    pub(super) id: &'a str,
    pub(super) channel: &'a str,
    pub(super) target_uuid: Option<&'a str>,
    text: Option<String>,
    pub(super) since: Option<&'a str>,
    pub(super) page: Option<&'a str>,
}

pub(super) async fn request(web: &WebChat, lease: &Lease, body: &str) -> Value {
    let Ok(request) = serde_json::from_str::<Request<'_>>(body) else {
        return error("", "invalid_request");
    };
    let id = request.id;
    if !token(id, 16, 64) {
        return error("", "invalid_request");
    }
    if request.kind == "history" {
        if request.text.is_some() {
            return error(id, "invalid_request");
        }
        let Some(hidden) = web.chat.hidden_from(&lease.grant.player_uuid) else {
            return error(id, "history_unavailable");
        };
        let view = context(lease, hidden.clone());
        return match respond(web.chat.history(), view, request).await {
            Ok(response) if web.chat.hidden_from(&lease.grant.player_uuid) == Some(hidden) => {
                response
            }
            Err(HistoryError::Invalid(_)) => error(id, "invalid_request"),
            _ => error(id, "history_unavailable"),
        };
    }
    if request.kind != "send" || request.since.is_some() || request.page.is_some() {
        return error(id, "invalid_request");
    }
    let channel = match (request.channel, request.target_uuid) {
        ("public", None) => HistoryChannel::HistoryPublic,
        ("staff", None) => HistoryChannel::HistoryStaff,
        ("private", Some(target)) if Uuid::parse_str(target).is_ok() => {
            HistoryChannel::HistoryPrivate
        }
        _ => return error(id, "invalid_request"),
    };
    let post = PlayerPost {
        sender: ChatPlayer {
            id: lease.grant.player_uuid.clone(),
            name: lease.grant.player_name.clone(),
        },
        staff: lease.staff,
        channel,
        target: request.target_uuid,
        text: request.text.as_deref().unwrap_or_default(),
        request_id: id,
    };
    match web.chat.send_player(post, &web.web_roster().players).await {
        Ok(message_id) => json!({"type":"accepted", "requestId":id, "messageId":message_id}),
        Err(SendError::Invalid) => error(id, "invalid_message"),
        Err(SendError::StaffRequired) => error(id, "staff_required"),
        Err(SendError::RecipientUnavailable) => error(id, "recipient_unavailable"),
        Err(SendError::RateLimited) => error(id, "rate_limited"),
        Err(SendError::Commit(HistoryError::Conflict)) => error(id, "request_conflict"),
        Err(SendError::Commit(_)) => error(id, "chat_busy"),
    }
}

fn error(id: &str, code: &str) -> Value {
    json!({"type":"error", "requestId":id, "error":code})
}

fn context(lease: &Lease, hidden: Vec<String>) -> View {
    View {
        viewer: lease.grant.player_uuid.clone(),
        staff: lease.staff,
        hidden,
    }
}

pub(super) fn live(web: &WebChat, lease: &Lease, event: &Committed) -> Option<Value> {
    let entry = &event.entry;
    let viewer = &lease.grant.player_uuid;
    if !event.visible_to(viewer, lease.staff) {
        return None;
    }
    let channel = match entry.channel() {
        HistoryChannel::HistoryPublic => "public",
        HistoryChannel::HistoryStaff => "staff",
        HistoryChannel::HistoryPrivate => "private",
        HistoryChannel::Unspecified => return None,
    };
    let hidden = web.chat.hidden_from(viewer)?;
    let excluded = hidden.iter().map(String::as_str).collect::<HashSet<_>>();
    let row = render::MessageView {
        expires_at: now() + RETENTION_MS,
        order_key: &event.order_key,
        message_id: &entry.message_id,
        record: entry.record.as_ref()?,
    };
    let mut message = render::message(&row, channel, viewer, &excluded)?;
    if let Some(receipt) = &event.receipt
        && receipt.sender == *viewer
    {
        message["requestId"] = json!(receipt.request_id);
    }
    Some(json!({"type":"message", "message":message}))
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct Presence<'snapshot> {
    #[serde(rename = "type")]
    kind: &'static str,
    players: Vec<PresencePlayer<'snapshot>>,
    staff_players: Vec<&'snapshot str>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct PresencePlayer<'snapshot> {
    player_uuid: &'snapshot str,
    name: &'snapshot str,
    game: bool,
    web_connections: u32,
}

pub(super) fn presence(
    game: Option<&GameView>,
    web: &[ChatWebPeer],
    lease: &Lease,
) -> serde_json::Result<String> {
    let mut peers = game.map_or_else(Vec::new, |game| game.peers(&lease.grant.player_uuid, web));
    peers.sort_unstable_by(|left, right| {
        let folded = |byte: u8| byte.to_ascii_lowercase();
        left.player
            .name
            .bytes()
            .map(folded)
            .cmp(right.player.name.bytes().map(folded))
            .then_with(|| left.player.id.cmp(&right.player.id))
    });
    let mut presence = Presence {
        kind: "presence",
        players: Vec::with_capacity(peers.len().min(512)),
        staff_players: Vec::new(),
    };
    for peer in peers.into_iter().take(512) {
        presence.players.push(PresencePlayer {
            player_uuid: &peer.player.id,
            name: &peer.player.name,
            game: peer.game,
            web_connections: peer.connections,
        });
        if lease.staff && peer.staff {
            presence.staff_players.push(&peer.player.id);
        }
    }
    serde_json::to_string(&presence)
}
