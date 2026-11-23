mod application;
mod crypto;
mod render;
mod session;
mod socket;
pub(crate) use session::{Grant, Outcome, WebChat};

use super::{
    HistoryError, HistoryStore,
    history::{Audience, PAGE_BYTES, SyncRequest},
};
use mik_protocol::wire::HistoryChannel;
use serde_json::{Value, json};
use std::collections::HashSet;

struct View {
    viewer: String,
    staff: bool,
    hidden: Vec<String>,
}

async fn respond(
    store: &HistoryStore,
    context: View,
    request: application::Request<'_>,
) -> Result<Value, HistoryError> {
    if [request.since, request.page]
        .into_iter()
        .flatten()
        .any(|value| !token(value, 91, 91))
    {
        return Err(HistoryError::Invalid("invalid web chat request"));
    }
    let channel = match request.channel {
        "public" => HistoryChannel::HistoryPublic,
        "private" => HistoryChannel::HistoryPrivate,
        "staff" if context.staff => HistoryChannel::HistoryStaff,
        "staff" => {
            return Ok(json!({"type":"error", "requestId":request.id, "error":"staff_required"}));
        }
        _ => return Err(HistoryError::Invalid("invalid web chat channel")),
    };
    let page = store
        .sync(SyncRequest {
            audience: Audience {
                viewer: context.viewer.clone(),
                staff: context.staff,
                channel,
                target: request.target_uuid.map(str::to_owned),
            },
            since: request.since.map(str::to_owned),
            continuation: request.page.map(str::to_owned),
            hidden: context.hidden.clone(),
        })
        .await?;
    let hidden = context
        .hidden
        .iter()
        .map(String::as_str)
        .collect::<HashSet<_>>();
    let mut response = json!({
        "type": "history", "requestId": request.id, "channel": request.channel,
        "checkpoint": page.checkpoint()?, "reset": page.reset,
    });
    let mut messages = Vec::new();
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
        if let Some(message) = render::message(&view, request.channel, &context.viewer, &hidden) {
            let size = message.to_string().len() + 1;
            if bytes + size > PAGE_BYTES {
                if messages.is_empty() {
                    return Err(HistoryError::Invalid("web chat message exceeds page size"));
                }
                more = true;
                break;
            }
            bytes += size;
            messages.push(message);
        }
        before = Some(row.cursor);
    }
    if more && let Some(before) = before {
        response["nextPage"] = Value::String(page.continuation(before)?);
    }
    messages.reverse();
    response["messages"] = Value::Array(messages);
    Ok(response)
}

fn token(value: &str, minimum: usize, maximum: usize) -> bool {
    (minimum..=maximum).contains(&value.len())
        && value
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'-' | b'_'))
}
