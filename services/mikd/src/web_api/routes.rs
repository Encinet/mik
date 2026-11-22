use super::{WebApi, chat};
use crate::{
    domains::access::{Account, AccountLookupError, Confirmation, valid_code},
    game::{GameResult, Operation},
};
use axum::{
    Json, Router,
    body::{Body, Bytes},
    extract::{Path, RawQuery, State},
    http::{HeaderValue, Method, StatusCode, header},
    response::{IntoResponse, Response},
    routing::{get, post},
};
use mik_protocol::wire::GameQueryEmpty;
use serde::Serialize;
use serde_json::json;
use std::sync::Arc;
use time::format_description::well_known::Rfc3339;
use uuid::Uuid;

pub(super) fn router(api: Arc<WebApi>) -> Router {
    Router::new()
        .route("/api/players", get(players).head(method_not_allowed))
        .route(
            "/api/announcements",
            get(announcements).head(method_not_allowed),
        )
        .route("/api/bans", get(bans).head(method_not_allowed))
        .route(
            "/api/players/resolve",
            get(resolve).head(method_not_allowed),
        )
        .route(
            "/api/auth/accounts/{uuid}",
            get(account).head(method_not_allowed),
        )
        .route("/api/auth/challenges/{code}", axum::routing::any(challenge))
        .route("/api/chat/authorize", post(chat::authorize))
        .route("/api/chat/revoke", post(chat::revoke))
        .fallback(|| async { error(StatusCode::NOT_FOUND, "not_found") })
        .method_not_allowed_fallback(method_not_allowed)
        .with_state(api)
}

async fn method_not_allowed() -> Response {
    error(StatusCode::METHOD_NOT_ALLOWED, "method_not_allowed")
}

pub(super) fn no_store(mut response: Response) -> Response {
    response
        .headers_mut()
        .insert(header::CACHE_CONTROL, HeaderValue::from_static("no-store"));
    response
}

pub(super) fn error(status: StatusCode, code: &str) -> Response {
    no_store((status, Json(json!({"error": code}))).into_response())
}

pub(super) fn game_error(value: Option<&GameResult>, unavailable: &str) -> Response {
    if value.is_none() {
        error(StatusCode::SERVICE_UNAVAILABLE, unavailable)
    } else {
        error(StatusCode::INTERNAL_SERVER_ERROR, "internal_error")
    }
}

fn name(query: Option<&str>) -> String {
    url::form_urlencoded::parse(query.unwrap_or_default().as_bytes())
        .find(|(key, _)| key == "name")
        .map_or_else(String::new, |(_, value)| value.into_owned())
}

#[derive(Serialize)]
struct OnlinePlayers<'snapshot> {
    online: usize,
    players: Vec<OnlinePlayer<'snapshot>>,
}

#[derive(Serialize)]
struct OnlinePlayer<'snapshot> {
    uuid: &'snapshot str,
    name: &'snapshot str,
    joined_at: &'snapshot str,
}

async fn players(State(api): State<Arc<WebApi>>) -> Response {
    let Some(snapshot) = api.game.state.current() else {
        return error(StatusCode::SERVICE_UNAVAILABLE, "api_unavailable");
    };
    let players = snapshot
        .players
        .iter()
        .map(|player| OnlinePlayer {
            uuid: &player.player_id,
            name: &player.name,
            joined_at: &player.joined_at,
        })
        .collect();
    no_store(
        Json(OnlinePlayers {
            online: snapshot.players.len(),
            players,
        })
        .into_response(),
    )
}

async fn announcements(State(api): State<Arc<WebApi>>) -> Response {
    let (state, snapshot) = match &api.announcements {
        Some(catalog) => {
            let (state, snapshot) = catalog.json().await;
            (state.label(), snapshot)
        }
        None => ("disabled", None),
    };
    let mut response = match snapshot {
        Some(json) => no_store(
            (
                [(header::CONTENT_TYPE, "application/json; charset=utf-8")],
                Body::from(Bytes::from_owner(json)),
            )
                .into_response(),
        ),
        None => error(StatusCode::SERVICE_UNAVAILABLE, "api_unavailable"),
    };
    response
        .headers_mut()
        .insert("x-mik-announcements-state", HeaderValue::from_static(state));
    response
}

async fn bans(State(api): State<Arc<WebApi>>) -> Response {
    match api.game.query(Operation::Bans(GameQueryEmpty {})).await {
        Some(GameResult::Bans(snapshot)) => {
            let rows: Vec<_> = snapshot
                .bans
                .into_iter()
                .map(|item| {
                    json!({
                        "playerName": item.name, "playerUuid": item.uuid, "reason": item.reason,
                        "bannedBy": item.banned_by, "bannedAt": item.banned_at,
                        "expiresAt": item.expires_at, "isPermanent": item.expires_at.is_none(),
                    })
                })
                .collect();
            Json(rows).into_response()
        }
        value => game_error(value.as_ref(), "api_unavailable"),
    }
}

async fn resolve(State(api): State<Arc<WebApi>>, RawQuery(query): RawQuery) -> Response {
    let name = name(query.as_deref());
    let name = name.trim();
    if !(3..=16).contains(&name.len())
        || !name.bytes().all(|c| c.is_ascii_alphanumeric() || c == b'_')
    {
        return error(StatusCode::BAD_REQUEST, "invalid_name");
    }
    match api
        .game
        .query(Operation::ResolvePlayer(name.to_owned()))
        .await
    {
        Some(GameResult::Player(player)) => no_store(
            Json(json!({"player": {"uuid": player.id, "name": player.name}})).into_response(),
        ),
        Some(GameResult::NotFound(_)) => error(StatusCode::NOT_FOUND, "not_found"),
        value => game_error(value.as_ref(), "api_unavailable"),
    }
}

async fn account(
    State(api): State<Arc<WebApi>>,
    Path(uuid): Path<String>,
    RawQuery(query): RawQuery,
) -> Response {
    if !Uuid::parse_str(&uuid).is_ok_and(|id| id.to_string() == uuid) {
        return error(StatusCode::NOT_FOUND, "not_found");
    }
    match api.access.account(&uuid, name(query.as_deref())).await {
        Ok(Account { policy, role }) => no_store(Json(json!({"player": {
            "uuid": uuid, "role": role, "credentialsChangedAt": policy.credentials_changed_at, "banned": policy.banned,
        }})).into_response()),
        Err(AccountLookupError::Unavailable) => error(StatusCode::SERVICE_UNAVAILABLE, "api_unavailable"),
        Err(AccountLookupError::Failed) => error(StatusCode::INTERNAL_SERVER_ERROR, "internal_error"),
    }
}

async fn challenge(
    State(api): State<Arc<WebApi>>,
    Path(code): Path<String>,
    method: Method,
) -> Response {
    if !valid_code(&code) {
        return error(StatusCode::NOT_FOUND, "not_found");
    }
    if method != Method::GET {
        return error(StatusCode::METHOD_NOT_ALLOWED, "method_not_allowed");
    }
    let value = match api.access.lookup(&code).await {
        Confirmation::Banned => json!({"status": "banned"}),
        Confirmation::Confirmed { approval, role } => {
            let Ok(confirmed_at) = approval.confirmed_at.format(&Rfc3339) else {
                return error(StatusCode::INTERNAL_SERVER_ERROR, "internal_error");
            };
            json!({"status": "confirmed", "player": {"uuid": approval.player_uuid, "name": approval.player_name, "role": role}, "confirmedAt": confirmed_at})
        }
        Confirmation::Missing => json!({"status": "not_found"}),
        Confirmation::Unavailable => {
            return error(StatusCode::SERVICE_UNAVAILABLE, "api_unavailable");
        }
    };
    no_store(Json(value).into_response())
}
