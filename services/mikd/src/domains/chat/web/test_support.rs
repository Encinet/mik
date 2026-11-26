use super::session::{Grant, Registration, WebChat};
use crate::domains::chat::{ChatService, HistoryStore};
use std::{fs::Permissions, os::unix::fs::PermissionsExt, sync::Arc};

pub(super) async fn open_chat() -> (tempfile::TempDir, Arc<WebChat>) {
    let directory = tempfile::Builder::new()
        .permissions(Permissions::from_mode(0o700))
        .tempdir()
        .unwrap();
    let history = HistoryStore::open(&directory.path().join("chat.db"))
        .await
        .unwrap();
    let chat = Arc::new(ChatService::new(history, None));
    let web = WebChat::open(chat).unwrap();
    (directory, web)
}

pub(super) fn grant(connection: &Registration<'_>, started: i64) -> Grant {
    Grant {
        player_uuid: uuid::Uuid::new_v4().to_string(),
        player_name: "Player".into(),
        session_ref: "login".into(),
        renewal_token: "proof".into(),
        connection_id: connection.id.clone(),
        client_public_key: "client".into(),
        authenticated_at: started - 1_000,
        checked_at: started,
        expires_at: started + 60_000,
    }
}
