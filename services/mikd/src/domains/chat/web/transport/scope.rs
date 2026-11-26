use super::super::session::{Lease, Registration, State, WebChat};
use super::{Result, invalid};
use crate::domains::chat::{now, presence::GameView};
use futures_util::FutureExt;
use std::{future::Future, sync::Arc, time::Duration};

pub(super) fn permits_delivery(
    web: &WebChat,
    session: &Registration<'_>,
    lease: &Arc<Lease>,
    game: &Arc<GameView>,
    hidden: &[String],
) -> bool {
    session.state.borrow().permits_reply(lease, now())
        && web.chat.game().is_some_and(|current| {
            Arc::ptr_eq(&current, game) || current.hidden_from(&lease.grant.player_uuid) == hidden
        })
}

/// Watch the same authorization and visibility that admitted an in-flight response.
pub(super) async fn scope_ended(
    web: &WebChat,
    session: &Registration<'_>,
    lease: &Arc<Lease>,
    game: &Arc<GameView>,
    hidden: &[String],
) {
    let mut authority = session.state.clone();
    let mut views = web.presence_changed.subscribe();
    loop {
        let current = authority.borrow_and_update().clone();
        if !permits_delivery(web, session, lease, game, hidden) {
            return;
        }
        let State::Authorized(current) = current else {
            return;
        };
        let remaining = (current.grant.expires_at - now()).max(0).unsigned_abs();
        tokio::select! {
            result = authority.changed() => if result.is_err() { return; },
            result = views.changed() => if result.is_err() { return; },
            () = tokio::time::sleep(Duration::from_millis(remaining)) => {},
        }
    }
}

/// Recheck scope when an encrypted write waits for the socket.
pub(super) async fn guard_write(
    write: impl Future<Output = Result<()>>,
    ended: impl Future<Output = ()>,
) -> Result<()> {
    let mut write = std::pin::pin!(write);
    if let Some(result) = write.as_mut().now_or_never() {
        return result;
    }
    tokio::select! {
        biased;
        () = ended => Err(invalid()),
        result = write => result,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::domains::chat::web::test_support::{grant, open_chat};
    use mik_protocol::wire::{AccountPolicy, ChatGamePeer, ChatPlayer};
    use std::{future::pending, net::Ipv4Addr};

    #[tokio::test]
    async fn a_flow_controlled_write_survives_unrelated_roster_changes_and_stops_on_lost_visibility()
     {
        let (_directory, web) = open_chat().await;
        let session = web.register(Ipv4Addr::LOCALHOST.into()).unwrap();
        let at = now();
        web.authorize(grant(&session, at), &AccountPolicy::default(), false, at);
        let State::Authorized(lease) = session.state.borrow().clone() else {
            panic!("missing initial lease")
        };
        web.chat.project_game(Some(Vec::new()));
        let game = web.chat.game().unwrap();
        let write = guard_write(pending(), scope_ended(&web, &session, &lease, &game, &[]));
        let mut write = std::pin::pin!(write);
        assert!(write.as_mut().now_or_never().is_none());
        let mut peer = ChatGamePeer {
            player: Some(ChatPlayer {
                id: uuid::Uuid::new_v4().to_string(),
                name: "Peer".into(),
            }),
            ..Default::default()
        };
        web.chat.project_game(Some(vec![peer.clone()]));
        web.presence_changed.send_replace(());
        assert!(write.as_mut().now_or_never().is_none());
        peer.visible_to = Some(Vec::new());
        web.chat.project_game(Some(vec![peer]));
        web.presence_changed.send_replace(());
        assert!(write.await.is_err());
        web.chat.close().await.unwrap();
    }
}
