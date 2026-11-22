use super::{crypto::Identity, socket};
use crate::domains::chat::{ChatService, now};
use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD as BASE64};
use mik_protocol::wire::{
    AccountPolicy, ChatNoticeRecord, ChatPlayer, ChatPresenceNotice, ChatRecord, ChatWebPeer,
    ChatWebRoster, HistoryChannel, HistoryEntry, chat_notice_record, chat_presence_notice,
    chat_record,
};
use serde::Deserialize;
use std::{
    collections::{BTreeMap, HashMap},
    io,
    net::IpAddr,
    path::Path,
    sync::{Arc, Mutex, MutexGuard},
};
use tokio::{net::TcpListener, sync::watch};

const MAX_PENDING: usize = 16;
const MAX_CONNECTIONS: usize = 64;
const MAX_PER_PLAYER: usize = 4;

#[derive(Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Grant {
    pub(crate) player_uuid: String,
    pub(crate) player_name: String,
    pub(crate) session_ref: String,
    pub(crate) connection_id: String,
    pub(crate) client_public_key: String,
    pub(crate) authenticated_at: i64,
    pub(crate) checked_at: i64,
    pub(crate) expires_at: i64,
}

#[derive(Clone, Copy)]
pub(crate) enum Outcome {
    Accepted,
    ConnectionUnavailable,
    Forbidden,
    SessionChanged,
    ChatCapacity,
    ConnectionLimit,
    Busy,
    Unavailable,
}

pub(crate) struct WebChat {
    pub(super) identity: Identity,
    pub(super) chat: Arc<ChatService>,
    sessions: Mutex<Sessions>,
}

#[derive(Default)]
struct Sessions {
    connections: HashMap<String, Connection>,
    revoked: HashMap<String, i64>,
    policy_changed: HashMap<String, i64>,
    roster: Arc<ChatWebRoster>,
    roster_valid_until: i64,
}

struct Connection {
    address: IpAddr,
    state: watch::Sender<State>,
    confirmed: Option<ChatPlayer>,
    last_activity: i64,
}

#[derive(Clone, Default)]
pub(super) enum State {
    #[default]
    Awaiting,
    Authorized(Arc<Lease>),
    Closed(&'static str),
}

impl State {
    pub(super) fn permits_reply(&self, request: &Arc<Lease>, now: i64) -> bool {
        matches!(self, Self::Authorized(current) if current.grant.expires_at > now
            && (Arc::ptr_eq(current, request)
                || (current.staff == request.staff && same_client(&current.grant, &request.grant))))
    }
}

pub(super) struct Lease {
    pub(super) grant: Grant,
    pub(super) staff: bool,
}

pub(super) struct Registration<'a> {
    pub(super) id: String,
    pub(super) state: watch::Receiver<State>,
    owner: &'a WebChat,
}

impl Drop for Registration<'_> {
    fn drop(&mut self) {
        let Some(mut sessions) = self.owner.lock_sessions() else {
            return;
        };
        if let Some(player) = sessions
            .connections
            .remove(&self.id)
            .and_then(|connection| connection.confirmed)
        {
            sessions.roster_valid_until = 0;
            if !sessions.has_confirmed(&player.id) {
                self.owner.present(player, false);
            }
        }
    }
}

impl Sessions {
    fn has_confirmed(&self, id: &str) -> bool {
        self.connections.values().any(|connection| {
            connection
                .confirmed
                .as_ref()
                .is_some_and(|player| player.id == id)
        })
    }
}

impl WebChat {
    pub(crate) fn open(chat: Arc<ChatService>, path: &Path) -> io::Result<Self> {
        let identity = Identity::open(path)?;
        eprintln!("Web chat public key: {}", identity.public_key());
        Ok(Self {
            chat,
            identity,
            sessions: Mutex::default(),
        })
    }

    fn lock_sessions(&self) -> Option<MutexGuard<'_, Sessions>> {
        match self.sessions.lock() {
            Ok(sessions) => Some(sessions),
            Err(poisoned) => {
                // Revocations may be incomplete. Close existing clients and refuse new grants.
                for (_, connection) in poisoned.into_inner().connections.drain() {
                    connection
                        .state
                        .send_replace(State::Closed("chat_unavailable"));
                }
                None
            }
        }
    }

    pub(crate) async fn serve(
        self: Arc<Self>,
        listener: TcpListener,
        origin: String,
        stop: tokio_util::sync::CancellationToken,
    ) -> io::Result<()> {
        socket::serve(self, listener, origin, stop).await
    }

    pub(super) fn register(&self, address: IpAddr) -> Option<Registration<'_>> {
        let mut sessions = self.lock_sessions()?;
        let pending = sessions
            .connections
            .values()
            .filter(|connection| matches!(*connection.state.borrow(), State::Awaiting));
        if pending.clone().count() >= MAX_PENDING
            || pending
                .filter(|connection| connection.address == address)
                .count()
                >= 4
        {
            return None;
        }
        let mut bytes = [0; 24];
        getrandom::fill(&mut bytes).ok()?;
        let id = BASE64.encode(bytes);
        let (sender, state) = watch::channel(State::Awaiting);
        sessions.connections.insert(
            id.clone(),
            Connection {
                address,
                state: sender,
                confirmed: None,
                last_activity: 0,
            },
        );
        Some(Registration {
            id,
            state,
            owner: self,
        })
    }

    pub(crate) fn authorize(
        &self,
        grant: Grant,
        policy: &AccountPolicy,
        staff: bool,
        queried_at: i64,
    ) -> (Outcome, i64) {
        let now = now();
        let Some(mut sessions) = self.lock_sessions() else {
            return (Outcome::Unavailable, 0);
        };
        sessions.revoked.retain(|_, expiry| *expiry > now);
        let outcome = |outcome, last_activity_at| (outcome, last_activity_at);
        if sessions
            .policy_changed
            .get(&grant.player_uuid)
            .is_some_and(|changed| *changed >= queried_at)
        {
            return outcome(Outcome::Busy, 0);
        }
        sessions.roster_valid_until = 0;
        let Some(connection) = sessions.connections.get(&grant.connection_id) else {
            return outcome(Outcome::ConnectionUnavailable, 0);
        };
        let previous = connection.state.borrow().clone();
        if matches!(previous, State::Closed(_)) {
            return outcome(Outcome::ConnectionUnavailable, 0);
        }
        if grant.expires_at <= now
            || sessions.revoked.contains_key(&grant.session_ref)
            || policy.banned
            || grant.authenticated_at <= policy.credentials_changed_at
        {
            connection
                .state
                .send_replace(State::Closed("chat_forbidden"));
            return outcome(Outcome::Forbidden, 0);
        }
        if let State::Authorized(previous) = &previous
            && !same_client(&previous.grant, &grant)
        {
            connection
                .state
                .send_replace(State::Closed("session_changed"));
            return outcome(Outcome::SessionChanged, 0);
        }
        let mut total = 0;
        let mut player = 0;
        let mut last_activity = 0;
        for other in sessions.connections.values() {
            if let State::Authorized(lease) = &*other.state.borrow() {
                if lease.grant.session_ref == grant.session_ref {
                    if !same_login(&lease.grant, &grant) {
                        return outcome(Outcome::Forbidden, 0);
                    }
                    last_activity = last_activity.max(other.last_activity);
                }
                total += 1;
                player += usize::from(lease.grant.player_uuid == grant.player_uuid);
            }
        }
        if matches!(previous, State::Awaiting) {
            if total >= MAX_CONNECTIONS {
                return outcome(Outcome::ChatCapacity, 0);
            }
            if player >= MAX_PER_PLAYER {
                return outcome(Outcome::ConnectionLimit, 0);
            }
        }
        for other in sessions.connections.values() {
            let state = other.state.borrow().clone();
            if let State::Authorized(lease) = state
                && same_login(&lease.grant, &grant)
            {
                let mut refreshed = lease.grant.clone();
                refreshed.expires_at = grant.expires_at.max(refreshed.expires_at);
                refreshed.checked_at = grant.checked_at.max(refreshed.checked_at);
                refreshed.player_name.clone_from(&grant.player_name);
                other.state.send_replace(State::Authorized(Arc::new(Lease {
                    grant: refreshed,
                    staff,
                })));
            }
        }
        if matches!(previous, State::Awaiting) {
            connection
                .state
                .send_replace(State::Authorized(Arc::new(Lease { grant, staff })));
        }
        outcome(Outcome::Accepted, last_activity)
    }

    pub(crate) fn revoke(&self, refs: Vec<String>) {
        let Some(mut sessions) = self.lock_sessions() else {
            return;
        };
        sessions.roster_valid_until = 0;
        let now = now();
        sessions.revoked.retain(|_, expiry| *expiry > now);
        for session_ref in refs {
            for connection in sessions.connections.values() {
                let revoke = matches!(&*connection.state.borrow(), State::Authorized(lease) if lease.grant.session_ref == session_ref);
                if revoke {
                    connection
                        .state
                        .send_replace(State::Closed("session_revoked"));
                }
            }
            sessions.revoked.insert(session_ref, now + 60_000);
        }
    }

    pub(crate) fn invalidate(&self, ids: &[String]) {
        let Some(mut sessions) = self.lock_sessions() else {
            return;
        };
        sessions.roster_valid_until = 0;
        let now = now();
        sessions
            .policy_changed
            .retain(|_, changed| *changed > now - 5_000);
        for id in ids {
            for connection in sessions.connections.values() {
                let revoke = matches!(&*connection.state.borrow(), State::Authorized(lease) if lease.grant.player_uuid == *id);
                if revoke {
                    connection
                        .state
                        .send_replace(State::Closed("chat_forbidden"));
                }
            }
            sessions.policy_changed.insert(id.clone(), now);
        }
    }

    pub(super) fn confirm(&self, id: &str) {
        let Some(mut sessions) = self.lock_sessions() else {
            return;
        };
        let Some(connection) = sessions.connections.get(id) else {
            return;
        };
        let State::Authorized(lease) = connection.state.borrow().clone() else {
            return;
        };
        if lease.grant.expires_at <= now() {
            return;
        }
        let player = ChatPlayer {
            id: lease.grant.player_uuid.clone(),
            name: lease.grant.player_name.clone(),
        };
        if !sessions.has_confirmed(&player.id) {
            self.present(player.clone(), true);
        }
        if let Some(connection) = sessions.connections.get_mut(id) {
            connection.confirmed = Some(player);
            sessions.roster_valid_until = 0;
        }
    }

    fn present(&self, player: ChatPlayer, joined: bool) {
        let Some(game) = self.chat.game() else {
            return;
        };
        let restriction = game.iter().find(|peer| {
            peer.concealed
                && peer
                    .player
                    .as_ref()
                    .is_some_and(|subject| subject.id == player.id)
        });
        let presence = ChatPresenceNotice {
            kind: if joined {
                chat_presence_notice::Kind::WebJoined
            } else {
                chat_presence_notice::Kind::WebLeft
            }
            .into(),
            name: player.name.clone(),
        };
        let entry = HistoryEntry {
            message_id: uuid::Uuid::new_v4().to_string(),
            channel: HistoryChannel::HistoryPublic.into(),
            restricted: restriction.is_some(),
            viewers: restriction
                .map_or_else(Vec::new, |peer| game.viewers(peer).cloned().collect()),
            record: Some(ChatRecord {
                created_at: now(),
                kind: Some(chat_record::Kind::Notice(ChatNoticeRecord {
                    subject: Some(player),
                    content: Some(chat_notice_record::Content::Presence(presence)),
                    ..ChatNoticeRecord::default()
                })),
            }),
            ..HistoryEntry::default()
        };
        match self.chat.enqueue_notice(entry) {
            // The worker owns the queued notice; presence does not wait for its receipt.
            Ok(receipt) => drop(receipt),
            Err(error) => eprintln!("mikd web chat presence notice dropped: {error}"),
        }
    }

    pub(super) fn renewal_connection(&self, id: &str, lease: &Lease) -> bool {
        let Some(sessions) = self.lock_sessions() else {
            return false;
        };
        sessions.connections.iter().filter_map(|(id, connection)| {
            (connection.confirmed.is_some() && matches!(&*connection.state.borrow(), State::Authorized(other) if same_login(&other.grant, &lease.grant))).then_some(id.as_str())
        }).min() == Some(id)
    }

    pub(super) fn activity(&self, id: &str) {
        if let Some(mut sessions) = self.lock_sessions()
            && let Some(connection) = sessions.connections.get_mut(id)
        {
            connection.last_activity = now();
        }
    }

    pub(crate) fn web_roster(&self) -> Arc<ChatWebRoster> {
        let Some(mut sessions) = self.lock_sessions() else {
            return Arc::default();
        };
        let now = now();
        if now < sessions.roster_valid_until {
            return sessions.roster.clone();
        }
        let mut peers = BTreeMap::new();
        let mut valid_until = i64::MAX;
        for connection in sessions.connections.values() {
            if let State::Authorized(lease) = &*connection.state.borrow()
                && connection.confirmed.is_some()
                && lease.grant.expires_at > now
            {
                valid_until = valid_until.min(lease.grant.expires_at);
                let (checked_at, peer) = peers
                    .entry(lease.grant.player_uuid.clone())
                    .or_insert_with(|| {
                        (
                            lease.grant.checked_at,
                            ChatWebPeer {
                                player: Some(ChatPlayer {
                                    id: lease.grant.player_uuid.clone(),
                                    name: lease.grant.player_name.clone(),
                                }),
                                staff: lease.staff,
                                connections: 0,
                            },
                        )
                    });
                if lease.grant.checked_at > *checked_at {
                    if let Some(player) = &mut peer.player {
                        player.name.clone_from(&lease.grant.player_name);
                    }
                    *checked_at = lease.grant.checked_at;
                }
                peer.staff |= lease.staff;
                peer.connections += 1;
            }
        }
        let players: Vec<_> = peers.into_values().map(|(_, peer)| peer).collect();
        if sessions.roster.revision == 0 || sessions.roster.players != players {
            sessions.roster = Arc::new(ChatWebRoster {
                revision: sessions.roster.revision + 1,
                players,
            });
        }
        sessions.roster_valid_until = valid_until;
        sessions.roster.clone()
    }
}

fn same_login(left: &Grant, right: &Grant) -> bool {
    left.session_ref == right.session_ref
        && left.player_uuid == right.player_uuid
        && left.authenticated_at == right.authenticated_at
}

fn same_client(left: &Grant, right: &Grant) -> bool {
    same_login(left, right) && left.client_public_key == right.client_public_key
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::domains::chat::HistoryStore;
    use std::{fs::Permissions, net::Ipv4Addr, os::unix::fs::PermissionsExt};

    async fn open_chat() -> (tempfile::TempDir, WebChat) {
        let directory = tempfile::Builder::new()
            .permissions(Permissions::from_mode(0o700))
            .tempdir()
            .unwrap();
        let history = HistoryStore::open(&directory.path().join("chat.db"))
            .await
            .unwrap();
        let chat = Arc::new(ChatService::new(history, None));
        let web = WebChat::open(chat, &directory.path().join("chat.key")).unwrap();
        (directory, web)
    }

    fn grant(connection: &Registration<'_>, started: i64) -> Grant {
        Grant {
            player_uuid: uuid::Uuid::new_v4().to_string(),
            player_name: "Player".into(),
            session_ref: "login".into(),
            connection_id: connection.id.clone(),
            client_public_key: "client".into(),
            authenticated_at: started - 1_000,
            checked_at: started,
            expires_at: started + 60_000,
        }
    }

    #[tokio::test]
    async fn in_flight_reply_survives_renewal_but_not_permission_changes() {
        let (_directory, web) = open_chat().await;
        let connection = web.register(Ipv4Addr::LOCALHOST.into()).unwrap();
        let started = now();
        let mut grant = grant(&connection, started);
        let policy = AccountPolicy::default();
        let authorize = |grant: &Grant, staff| {
            assert!(matches!(
                web.authorize(grant.clone(), &policy, staff, started).0,
                Outcome::Accepted
            ));
        };
        let permits_reply =
            |request: &Arc<Lease>, at| connection.state.borrow().permits_reply(request, at);
        authorize(&grant, true);
        let State::Authorized(request) = connection.state.borrow().clone() else {
            panic!("authorization did not open the connection");
        };

        grant.checked_at += 1_000;
        grant.expires_at += 1_000;
        authorize(&grant, true);
        assert!(permits_reply(&request, request.grant.expires_at));
        assert!(!permits_reply(&request, grant.expires_at));

        authorize(&grant, false);
        assert!(!permits_reply(&request, started));
        let State::Authorized(request) = connection.state.borrow().clone() else {
            panic!("renewal unexpectedly closed the connection");
        };
        web.revoke(vec![grant.session_ref]);
        assert!(!permits_reply(&request, started));
        drop(connection);
        web.chat.close().await.unwrap();
    }

    #[tokio::test]
    async fn session_state_failure_closes_active_grants_and_rejects_new_ones() {
        let (_directory, web) = open_chat().await;
        let address = Ipv4Addr::LOCALHOST.into();
        let connection = web.register(address).unwrap();
        let started = now();
        let grant = grant(&connection, started);
        let policy = AccountPolicy::default();
        assert!(matches!(
            web.authorize(grant.clone(), &policy, true, started).0,
            Outcome::Accepted
        ));

        let sessions = &web.sessions;
        assert!(
            std::panic::catch_unwind(|| {
                let _guard = sessions.lock().unwrap();
                panic!("revocation interrupted");
            })
            .is_err()
        );

        assert!(matches!(
            web.authorize(grant, &policy, true, started).0,
            Outcome::Unavailable
        ));
        assert!(matches!(
            *connection.state.borrow(),
            State::Closed("chat_unavailable")
        ));
        assert!(web.register(address).is_none());
        drop(connection);
        web.chat.close().await.unwrap();
    }
}
