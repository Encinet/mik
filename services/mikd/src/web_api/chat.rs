use super::{
    WebApi,
    routes::{error, no_store},
};
use crate::domains::chat::{
    now,
    web::{Grant, Outcome},
};
use axum::{
    Json,
    body::to_bytes,
    extract::{Request, State},
    http::{StatusCode, header},
    response::{IntoResponse, Response},
};
use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
use serde_json::{Value, json};
use std::{sync::Arc, time::Duration};
use tokio::time::timeout;
use uuid::Uuid;

fn valid_grant(grant: &Grant, now: i64) -> bool {
    Uuid::parse_str(&grant.player_uuid).is_ok_and(|id| id.to_string() == grant.player_uuid)
        && base64_token(&grant.session_ref, 43)
        && base64_token(&grant.connection_id, 32)
        && !grant.player_name.trim().is_empty()
        && grant.player_name.encode_utf16().count() <= 64
        && !grant.player_name.chars().any(char::is_control)
        && (now - 60_000..=now + 5_000).contains(&grant.checked_at)
        && (now + 1..=grant.checked_at + 60_000).contains(&grant.expires_at)
        && (1..=grant.checked_at + 5_000).contains(&grant.authenticated_at)
        && base64_token(&grant.client_public_key, 87)
        && URL_SAFE_NO_PAD
            .decode(&grant.client_public_key)
            .is_ok_and(|bytes| matches!(bytes.as_slice(), [4, rest @ ..] if rest.len() == 64))
}

fn base64_token(value: &str, length: usize) -> bool {
    value.len() == length
        && value
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'_' | b'-'))
}

pub(super) async fn authorize(State(api): State<Arc<WebApi>>, request: Request) -> Response {
    let body = match read_chat_body(request).await {
        Ok(body) => body,
        Err((status, code)) => return error(status, code),
    };
    let queried_at = now();
    let Some(grant) = serde_json::from_value::<Grant>(body)
        .ok()
        .filter(|grant| valid_grant(grant, queried_at))
    else {
        return control(Outcome::Unavailable, 0);
    };
    let Some(chat) = &api.chat else {
        return control(Outcome::Unavailable, 0);
    };
    let Ok(account) = api
        .access
        .account(&grant.player_uuid, grant.player_name.clone())
        .await
    else {
        return control(Outcome::Busy, 0);
    };
    let (outcome, last_activity_at) =
        chat.authorize(grant, &account.policy, account.staff(), queried_at);
    control(outcome, last_activity_at)
}

pub(super) async fn revoke(State(api): State<Arc<WebApi>>, request: Request) -> Response {
    let body = match read_chat_body(request).await {
        Ok(body) => body,
        Err((status, code)) => return error(status, code),
    };
    let refs = body
        .get("sessionRefs")
        .and_then(Value::as_array)
        .filter(|refs| refs.len() <= 128);
    let refs = refs.and_then(|refs| {
        refs.iter()
            .map(|item| {
                item.as_str()
                    .filter(|id| base64_token(id, 43))
                    .map(str::to_owned)
            })
            .collect()
    });
    match refs {
        Some(session_refs) => {
            let Some(chat) = &api.chat else {
                return control(Outcome::Unavailable, 0);
            };
            chat.revoke(session_refs);
            control(Outcome::Accepted, 0)
        }
        None => error(StatusCode::UNPROCESSABLE_ENTITY, "invalid_body"),
    }
}

async fn read_chat_body(request: Request) -> Result<Value, (StatusCode, &'static str)> {
    if !request
        .headers()
        .get(header::CONTENT_TYPE)
        .is_some_and(|value| value.as_bytes().eq_ignore_ascii_case(b"application/json"))
    {
        return Err((StatusCode::UNSUPPORTED_MEDIA_TYPE, "unsupported_media_type"));
    }
    let body = match timeout(
        Duration::from_secs(2),
        to_bytes(request.into_body(), 16_384),
    )
    .await
    {
        Ok(Ok(body)) => body,
        Ok(Err(_)) => return Err((StatusCode::PAYLOAD_TOO_LARGE, "body_too_large")),
        Err(_) => return Err((StatusCode::REQUEST_TIMEOUT, "request_timeout")),
    };
    serde_json::from_slice::<Value>(&body)
        .ok()
        .filter(Value::is_object)
        .ok_or((StatusCode::UNPROCESSABLE_ENTITY, "invalid_body"))
}

fn control(outcome: Outcome, last_activity_at: i64) -> Response {
    let (status, code) = match outcome {
        Outcome::Accepted => (StatusCode::OK, ""),
        Outcome::ConnectionUnavailable => (StatusCode::CONFLICT, "connection_unavailable"),
        Outcome::Forbidden => (StatusCode::FORBIDDEN, "chat_forbidden"),
        Outcome::SessionChanged => (StatusCode::CONFLICT, "session_changed"),
        Outcome::ChatCapacity => (StatusCode::TOO_MANY_REQUESTS, "chat_capacity"),
        Outcome::ConnectionLimit => (StatusCode::TOO_MANY_REQUESTS, "connection_limit"),
        Outcome::Busy => (StatusCode::SERVICE_UNAVAILABLE, "chat_busy"),
        Outcome::Unavailable => (StatusCode::SERVICE_UNAVAILABLE, "chat_unavailable"),
    };
    let mut body = json!({"ok": status == StatusCode::OK, "lastActivityAt": last_activity_at});
    if !code.is_empty() {
        body["error"] = code.into();
    }
    no_store((status, Json(body)).into_response())
}
