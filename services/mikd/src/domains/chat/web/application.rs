use super::{
    render,
    session::{Lease, WebChat},
};
use crate::domains::chat::{
    HistoryError, HistoryStore,
    history::{Audience, LiveAnchor, PAGE_BYTES, PostedReceipt, SyncPage, SyncRequest},
    model::{Channel, Destination, destination, player_snapshot},
    presence::GameView,
    service::{SendError, WebPost},
};
use mik_protocol::wire::{ChatPlayer, ChatWebPeer};
use serde::{Deserialize, Serialize};
use serde_json::{
    Value, json,
    value::{RawValue, to_raw_value},
};
use std::collections::HashSet;
use uuid::Uuid;

#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct Request<'a> {
    #[serde(rename = "type")]
    kind: &'a str,
    #[serde(rename = "requestId")]
    id: &'a str,
    channel: &'a str,
    target_uuid: Option<&'a str>,
    text: Option<String>,
    since: Option<&'a str>,
    page: Option<&'a str>,
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct Positions<'a> {
    public: Option<&'a str>,
    private: Option<&'a str>,
    staff: Option<&'a str>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct Resume<'a> {
    #[serde(rename = "type")]
    kind: &'a str,
    request_id: &'a str,
    #[serde(borrow)]
    positions: Positions<'a>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct ReceiptQuery<'a> {
    #[serde(rename = "type")]
    kind: &'a str,
    request_ids: Vec<String>,
}

#[derive(Deserialize)]
#[serde(untagged)]
enum Incoming<'a> {
    Single(#[serde(borrow)] Request<'a>),
    Resume(#[serde(borrow)] Resume<'a>),
    Receipts(#[serde(borrow)] ReceiptQuery<'a>),
}

#[derive(Serialize)]
pub(super) struct HistoryBody {
    #[serde(flatten)]
    pub(super) fields: Value,
    #[serde(skip_serializing_if = "Option::is_none")]
    messages: Option<Vec<Box<RawValue>>>,
}

pub(super) enum Replies {
    Single(Reply),
    Resume { id: String, pages: Vec<Reply> },
}

pub(super) enum Reply {
    Ordinary(Value),
    Receipts(Vec<PostedReceipt>),
    History {
        output: HistoryBody,
        compressible: bool,
        hidden: Vec<String>,
        anchor: Option<LiveAnchor>,
        channel: Option<&'static str>,
        continuation: Option<String>,
    },
}

pub(super) async fn request(
    web: &WebChat,
    lease: &Lease,
    body: &str,
    compressed: bool,
    lane: super::Lane,
    authorized: impl Fn() -> bool + Send + Sync + 'static,
) -> Replies {
    let request = match serde_json::from_str(body) {
        Ok(Incoming::Single(request)) => request,
        Ok(Incoming::Receipts(query))
            if lane == super::Lane::Live
                && !compressed
                && query.kind == "receipts"
                && (1..=20).contains(&query.request_ids.len())
                && query.request_ids.iter().all(|id| token(id, 16, 64)) =>
        {
            let viewer = &lease.grant.player_uuid;
            let reply = if web.allow_read(viewer) {
                web.chat
                    .history()
                    .posted_receipts(viewer.clone(), lease.staff, query.request_ids)
                    .await
                    .map_or_else(|_| Reply::Ordinary(error("", "chat_busy")), Reply::Receipts)
            } else {
                Reply::Ordinary(error("", "chat_busy"))
            };
            return Replies::Single(reply);
        }
        Ok(Incoming::Resume(batch))
            if lane == super::Lane::History
                && !compressed
                && batch.kind == "resume"
                && token(batch.request_id, 16, 56) =>
        {
            let mut pages = Vec::new();
            for (channel, since) in [
                ("public", batch.positions.public),
                ("private", batch.positions.private),
                ("staff", batch.positions.staff),
            ] {
                if let Some(since) = since {
                    let id = format!("{}-{channel}", batch.request_id);
                    let query = Request {
                        kind: "history",
                        id: &id,
                        channel,
                        since: Some(since),
                        page: None,
                        text: None,
                        target_uuid: None,
                    };
                    pages.push(history(web, lease, &query).await);
                }
            }
            if !pages.is_empty() {
                return Replies::Resume {
                    id: batch.request_id.to_owned(),
                    pages,
                };
            }
            return Replies::Single(Reply::Ordinary(error(batch.request_id, "invalid_request")));
        }
        _ => return Replies::Single(Reply::Ordinary(error("", "invalid_request"))),
    };
    let id = request.id;
    let reply = if (request.kind == "history") != (lane == super::Lane::History)
        || !token(id, 16, 64)
        || (compressed
            && (request.kind != "send" || request.page.is_some() || request.since.is_some()))
    {
        Reply::Ordinary(error("", "invalid_request"))
    } else if request.kind == "history" {
        history(web, lease, &request).await
    } else {
        Reply::Ordinary(submit(web, lease, &request, authorized).await)
    };
    Replies::Single(reply)
}

async fn submit(
    web: &WebChat,
    lease: &Lease,
    request: &Request<'_>,
    authorized: impl Fn() -> bool + Send + Sync + 'static,
) -> Value {
    let id = request.id;
    if request.kind != "send" || request.since.is_some() || request.page.is_some() {
        return error(id, "invalid_request");
    }
    let channel = match (request.channel, request.target_uuid) {
        ("public", None) => Channel::Public,
        ("staff", None) => Channel::Staff,
        ("private", Some(target)) if Uuid::parse_str(target).is_ok() => Channel::Private,
        _ => return error(id, "invalid_request"),
    };
    let post = WebPost {
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
    match web.chat.publish_web(post, authorized).await {
        Ok(message_id) => json!({"type":"accepted", "requestId":id, "messageId":message_id}),
        Err(SendError::Invalid) => error(id, "invalid_message"),
        Err(SendError::StaffRequired) => error(id, "staff_required"),
        Err(SendError::RecipientUnavailable) => error(id, "recipient_unavailable"),
        Err(SendError::RateLimited) => error(id, "rate_limited"),
        Err(SendError::Commit(HistoryError::AuthorizationExpired)) => {
            error(id, "authorization_expired")
        }
        Err(SendError::Commit(HistoryError::Conflict)) => error(id, "request_conflict"),
        Err(SendError::Commit(_)) => error(id, "chat_busy"),
    }
}

pub(super) fn error(id: &str, code: &str) -> Value {
    json!({"type":"error", "requestId":id, "error":code})
}

async fn history(web: &WebChat, lease: &Lease, request: &Request<'_>) -> Reply {
    let id = request.id;
    if request.text.is_some() {
        return Reply::Ordinary(error(id, "invalid_request"));
    }
    let Some(hidden) = web.chat.hidden_from(&lease.grant.player_uuid) else {
        return Reply::Ordinary(error(id, "history_unavailable"));
    };
    if !web.allow_read(&lease.grant.player_uuid) {
        return Reply::Ordinary(error(id, "chat_busy"));
    }
    match history_response(web.chat.history(), lease, hidden, request).await {
        Ok(response) => response,
        Err(HistoryError::Invalid(_)) => Reply::Ordinary(error(id, "invalid_request")),
        _ => Reply::Ordinary(error(id, "history_unavailable")),
    }
}

async fn history_response(
    store: &HistoryStore,
    lease: &Lease,
    hidden: Vec<String>,
    request: &Request<'_>,
) -> Result<Reply, HistoryError> {
    let viewer = &lease.grant.player_uuid;
    if [request.since, request.page]
        .into_iter()
        .flatten()
        .any(|value| !token(value, 91, 91))
    {
        return Err(HistoryError::Invalid("invalid web chat request"));
    }
    let channel = match request.channel {
        "public" => Channel::Public,
        "private" => Channel::Private,
        "staff" if lease.staff => Channel::Staff,
        "staff" => {
            return Ok(Reply::Ordinary(error(request.id, "staff_required")));
        }
        _ => return Err(HistoryError::Invalid("invalid web chat channel")),
    };
    let page = store
        .sync(SyncRequest {
            audience: Audience {
                viewer: viewer.clone(),
                staff: lease.staff,
                channel,
                target: request.target_uuid.map(str::to_owned),
            },
            since: request.since.map(str::to_owned),
            continuation: request.page.map(str::to_owned),
            hidden: hidden.clone(),
        })
        .await?;
    render_history(page, channel, viewer, hidden, request)
}

fn render_history(
    page: SyncPage,
    channel: Channel,
    viewer: &str,
    hidden: Vec<String>,
    request: &Request<'_>,
) -> Result<Reply, HistoryError> {
    let excluded = hidden.iter().map(String::as_str).collect::<HashSet<_>>();
    let mut response = json!({
        "type": "history", "requestId": request.id, "channel": request.channel,
        "reset": page.reset,
    });
    let mut messages = Vec::new();
    let mut compressible = true;
    let mut private_peer = None;
    let mut bytes = 2;
    let mut before = None;
    let mut more = page.has_more;
    for row in &page.rows {
        let view = render::MessageView {
            record: &row.record,
            message_id: &row.message_id,
            order_key: &row.order_key,
            expires_at: row.expires_at,
        };
        if render::visible_to(&row.record, viewer, |id| excluded.contains(id))
            && let Some(mut message) = render::project(&view, Some(channel))
        {
            if message.mentioned(&row.record, viewer, |person| {
                player_snapshot(person).and_then(|player| Uuid::parse_str(&player.id).ok())
            }) {
                message.fields["mentioned"] = json!(true);
            }
            let message = to_raw_value(&message.fields)
                .map_err(|_| HistoryError::Invalid("invalid rendered message"))?;
            let size = message.get().len() + usize::from(!messages.is_empty());
            if bytes + size > PAGE_BYTES {
                if messages.is_empty() {
                    return Err(HistoryError::Invalid("web chat message exceeds page size"));
                }
                more = true;
                break;
            }
            // Only records actually present in this frame share a compression context.
            compressible &= !row.restricted;
            if let Destination::Private { author, recipient } = destination(&row.record)? {
                let peer = if author.id == viewer {
                    &recipient.id
                } else {
                    &author.id
                };
                compressible &= private_peer.get_or_insert(peer) == &peer;
            }
            bytes += size;
            messages.push(message);
        }
        before = Some(row.cursor);
    }
    if more && let Some(before) = before {
        response["nextPage"] = Value::String(page.continuation(before)?);
    }
    let channel_scope = request.target_uuid.is_none().then_some(channel.name());
    let fresh = request.page.is_none() && channel_scope.is_some();
    let unchanged = request
        .since
        .filter(|_| page.from_checkpoint && !more && messages.is_empty());
    messages.reverse();
    let mut anchor = None;
    if !more || fresh {
        let checkpoint = if let Some(since) = unchanged {
            // Other channels and invisible rows must produce the same reply as an idle database.
            response = json!({"type":"historyCurrent","requestId":request.id});
            compressible = false;
            since.to_owned()
        } else {
            let checkpoint = page.checkpoint()?;
            if !more {
                response["checkpoint"] = json!(checkpoint);
            }
            checkpoint
        };
        if fresh {
            anchor = Some(page.into_live_anchor(channel, &hidden, checkpoint));
        }
    }
    Ok(Reply::History {
        output: HistoryBody {
            fields: response,
            messages: unchanged.is_none().then_some(messages),
        },
        compressible,
        hidden,
        anchor,
        channel: channel_scope,
        continuation: request.page.map(str::to_owned),
    })
}

fn token(value: &str, minimum: usize, maximum: usize) -> bool {
    (minimum..=maximum).contains(&value.len())
        && value
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'-' | b'_'))
}

#[derive(Serialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub(super) struct Presence {
    #[serde(rename = "type")]
    kind: &'static str,
    pub(super) players: Vec<PresencePlayer>,
    pub(super) staff_players: Vec<String>,
}

#[derive(Serialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub(super) struct PresencePlayer {
    pub(super) player_uuid: String,
    name: String,
    game: bool,
    web_connections: u32,
}

pub(super) fn presence(game: Option<&GameView>, web: &[ChatWebPeer], lease: &Lease) -> Presence {
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
            player_uuid: peer.player.id.clone(),
            name: peer.player.name.clone(),
            game: peer.game,
            web_connections: peer.connections,
        });
        if lease.staff && peer.staff {
            presence.staff_players.push(peer.player.id.clone());
        }
    }
    presence
}
