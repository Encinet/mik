mod notices;

use super::{
    live::{Inbox, LiveEvent},
    transport,
};
use crate::domains::chat::{
    ChatService, Committed, model::Destination, now, service::empty_web_roster,
};
use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD as BASE64};
use mik_protocol::wire::{AccountPolicy, ChatPlayer, ChatWebPeer, ChatWebRoster};
use notices::PresenceNotices;
use serde::Deserialize;
use std::{
    collections::{BTreeMap, HashMap, HashSet},
    io,
    net::IpAddr,
    sync::{Arc, Mutex, MutexGuard},
};
use tokio::sync::{Notify, watch};

const MAX_PENDING: usize = 16;
const MAX_CONNECTIONS: usize = 64;
const MAX_PER_PLAYER: usize = 4;

#[derive(Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Grant {
    pub(crate) player_uuid: String,
    pub(crate) player_name: String,
    pub(crate) session_ref: String,
    pub(crate) renewal_token: String,
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
    pub(super) chat: Arc<ChatService>,
    sessions: Mutex<Sessions>,
    pub(super) presence_changed: watch::Sender<()>,
    pub(super) renew_changed: Notify,
    departure_scheduled: Notify,
}

#[derive(Default)]
struct Sessions {
    connections: HashMap<String, Connection>,
    confirmed_by_player: HashMap<String, HashSet<String>>,
    notices: PresenceNotices,
    revoked: HashMap<String, i64>,
    policy_changed: HashMap<String, i64>,
    roster: Arc<ChatWebRoster>,
    roster_valid_until: i64,
    chat_reads: HashMap<String, (tokio::time::Instant, u32)>,
}

struct Connection {
    address: IpAddr,
    inbox: Arc<Inbox>,
    state: watch::Sender<State>,
    confirmed: Option<ChatPlayer>,
    last_activity: i64,
}

impl Connection {
    fn close(&self, reason: &'static str, roster: &watch::Sender<()>) {
        self.state.send_replace(State::Closed(reason));
        if self.confirmed.is_some() {
            roster.send_replace(());
        }
    }
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
                || (current.authority_epoch == request.authority_epoch && current.staff == request.staff && same_client(&current.grant, &request.grant))))
    }
}

pub(super) struct Lease {
    pub(super) grant: Grant,
    pub(super) staff: bool,
    policy_checked_at: i64,
    pub(super) authority_epoch: u64,
}

impl Lease {
    fn renew(&self, login: &Grant, staff: bool, queried_at: i64) -> Self {
        let mut grant = self.grant.clone();
        grant.expires_at = login.expires_at;
        grant.checked_at = login.checked_at;
        grant.player_name.clone_from(&login.player_name);
        grant.renewal_token.clone_from(&login.renewal_token);
        Self {
            authority_epoch: self.authority_epoch + u64::from(self.staff != staff),
            grant,
            policy_checked_at: queried_at,
            staff,
        }
    }
}

pub(super) struct Registration<'a> {
    pub(super) id: String,
    pub(super) inbox: Arc<Inbox>,
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
            self.owner.presence_changed.send_replace(());
            if let Some(tabs) = sessions.confirmed_by_player.get_mut(&player.id) {
                tabs.remove(&self.id);
                if tabs.is_empty() {
                    sessions.confirmed_by_player.remove(&player.id);
                    sessions.notices.left(&self.owner.chat, player);
                    self.owner.departure_scheduled.notify_one();
                }
            }
        }
    }
}

impl Sessions {
    fn for_players<'a>(
        &self,
        players: impl IntoIterator<Item = &'a str>,
        mut visit: impl FnMut(&Connection),
    ) {
        for player in players {
            if let Some(tabs) = self.confirmed_by_player.get(player) {
                for id in tabs {
                    if let Some(connection) = self.connections.get(id) {
                        visit(connection);
                    }
                }
            }
        }
    }
}

impl WebChat {
    pub(crate) fn open(chat: Arc<ChatService>) -> io::Result<Arc<Self>> {
        let web = Arc::new(Self {
            chat,
            presence_changed: watch::channel(()).0,
            renew_changed: Notify::new(),
            departure_scheduled: Notify::new(),
            sessions: Mutex::new(Sessions {
                roster: empty_web_roster(),
                ..Sessions::default()
            }),
        });
        let source = Arc::downgrade(&web);
        let publisher = Arc::downgrade(&web);
        web.chat.attach_web(
            move || {
                source
                    .upgrade()
                    .map_or_else(empty_web_roster, |web| web.web_roster())
            },
            move |event| {
                if let Some(web) = publisher.upgrade() {
                    web.publish(event);
                }
            },
        )?;
        Ok(web)
    }

    fn publish(&self, committed: Arc<Committed>) {
        let Some(sessions) = self.lock_sessions() else {
            return;
        };
        if sessions.confirmed_by_player.is_empty() {
            return;
        }
        let event = Arc::new(LiveEvent::new(committed));
        let Some(destination) = event.destination() else {
            return;
        };
        let channel = destination.channel();
        let game = self.chat.game();
        let deliver = |connection: &Connection| {
            let state = connection.state.borrow();
            let State::Authorized(lease) = &*state else {
                return;
            };
            if connection.confirmed.is_none() {
                return;
            }
            // Recheck expiry at delivery so a same-login renewal racing expiry keeps its queued rows.
            let Some(recipient) = event.for_viewer(&lease.grant.player_uuid, lease.staff) else {
                return;
            };
            if game.as_ref().is_none_or(|game| recipient.visible_in(game)) {
                connection.inbox.push(channel, &event);
            }
        };
        match destination {
            Destination::Private { author, recipient } => {
                sessions.for_players([author.id.as_str(), recipient.id.as_str()], deliver);
            }
            _ => {
                if let Some(players) = event.restricted_to()
                    && players.len() < sessions.confirmed_by_player.len()
                {
                    sessions.for_players(players.iter().map(String::as_str), deliver);
                } else {
                    sessions.connections.values().for_each(deliver);
                }
            }
        }
    }

    fn lock_sessions(&self) -> Option<MutexGuard<'_, Sessions>> {
        match self.sessions.lock() {
            Ok(sessions) => Some(sessions),
            Err(poisoned) => {
                // Revocations may be incomplete. Close existing clients and refuse new grants.
                for (_, connection) in poisoned.into_inner().connections.drain() {
                    connection.close("chat_unavailable", &self.presence_changed);
                }
                None
            }
        }
    }

    pub(crate) async fn serve(
        self: Arc<Self>,
        listener: tokio::net::TcpListener,
        identity: transport::Identity,
        origin: String,
        stop: tokio_util::sync::CancellationToken,
    ) -> io::Result<()> {
        let result = transport::serve(self.clone(), listener, identity, origin, stop).await;
        // All connection tasks have stopped. Discard departures caused by listener shutdown.
        if let Some(mut sessions) = self.lock_sessions() {
            sessions.notices = PresenceNotices::default();
        }
        result
    }

    pub(super) async fn announce_departures(&self) {
        loop {
            let deadline = {
                let Some(mut sessions) = self.lock_sessions() else {
                    return;
                };
                sessions.notices.publish_due(&self.chat)
            };
            if let Some(deadline) = deadline {
                // New departures always have a later deadline. A reconnect can cancel this one.
                tokio::time::sleep_until(deadline).await;
            } else {
                self.departure_scheduled.notified().await;
            }
        }
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
        let inbox = Arc::new(Inbox::default());
        sessions.connections.insert(
            id.clone(),
            Connection {
                address,
                inbox: Arc::clone(&inbox),
                state: sender,
                confirmed: None,
                last_activity: 0,
            },
        );
        Some(Registration {
            id,
            inbox,
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
            connection.close("chat_forbidden", &self.presence_changed);
            return outcome(Outcome::Forbidden, 0);
        }
        if let State::Authorized(previous) = &previous
            && !same_client(&previous.grant, &grant)
        {
            connection.close("session_changed", &self.presence_changed);
            return outcome(Outcome::SessionChanged, 0);
        }
        let mut staff = staff;
        let mut total = 0;
        let mut player = 0;
        let mut last_activity = 0;
        for other in sessions.connections.values() {
            if let State::Authorized(lease) = &*other.state.borrow() {
                if lease.grant.session_ref == grant.session_ref {
                    if !same_login(&lease.grant, &grant) {
                        return outcome(Outcome::Forbidden, 0);
                    }
                    if lease.policy_checked_at > queried_at
                        || lease.grant.checked_at > grant.checked_at
                    {
                        return outcome(Outcome::Busy, 0);
                    }
                    if lease.policy_checked_at == queried_at {
                        staff &= lease.staff;
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
                if other.confirmed.is_some()
                    && (lease.grant.player_name != grant.player_name
                        || lease.grant.expires_at <= now)
                {
                    self.presence_changed.send_replace(());
                }
                other.state.send_replace(State::Authorized(Arc::new(
                    lease.renew(&grant, staff, queried_at),
                )));
            }
        }
        if matches!(previous, State::Awaiting) {
            connection
                .state
                .send_replace(State::Authorized(Arc::new(Lease {
                    grant,
                    authority_epoch: 0,
                    staff,
                    policy_checked_at: queried_at,
                })));
        }
        self.renew_changed.notify_one();
        outcome(Outcome::Accepted, last_activity)
    }

    pub(super) fn reject_renewal_proof(&self, attempted: &Grant) {
        let Some(mut sessions) = self.lock_sessions() else {
            return;
        };
        sessions.roster_valid_until = 0;
        for connection in sessions.connections.values() {
            let rejected = matches!(&*connection.state.borrow(), State::Authorized(lease)
                if same_login(&lease.grant, attempted)
                    && lease.grant.checked_at <= attempted.checked_at
                    && lease.grant.renewal_token == attempted.renewal_token);
            if rejected {
                connection.close("authorization_expired", &self.presence_changed);
            }
        }
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
                    connection.close("session_revoked", &self.presence_changed);
                }
            }
            sessions.revoked.insert(session_ref, now + 60_000);
        }
    }

    pub(crate) fn invalidate(&self, id: String) {
        let Some(mut sessions) = self.lock_sessions() else {
            return;
        };
        sessions.roster_valid_until = 0;
        let now = now();
        sessions
            .policy_changed
            .retain(|_, changed| *changed > now - 5_000);
        for connection in sessions.connections.values() {
            let revoke = matches!(&*connection.state.borrow(), State::Authorized(lease) if lease.grant.player_uuid == id);
            if revoke {
                connection.close("chat_forbidden", &self.presence_changed);
            }
        }
        sessions.policy_changed.insert(id, now);
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
        let tabs = sessions
            .confirmed_by_player
            .entry(player.id.clone())
            .or_default();
        let joined = tabs.is_empty();
        if !tabs.insert(id.to_owned()) {
            return;
        }
        if joined {
            sessions.notices.joined(&self.chat, &player);
        }
        if let Some(connection) = sessions.connections.get_mut(id) {
            connection.confirmed = Some(player);
            self.renew_changed.notify_one();
            self.presence_changed.send_replace(());
            sessions.roster_valid_until = 0;
        }
    }

    // Each read already has a 120 KiB page limit. Tabs share the player's page allowance.
    pub(super) fn allow_read(&self, player: &str) -> bool {
        let Some(mut sessions) = self.lock_sessions() else {
            return false;
        };
        sessions
            .chat_reads
            .retain(|_, (start, _)| start.elapsed().as_secs() < 1);
        let (_, pages) = sessions
            .chat_reads
            .entry(player.to_owned())
            .or_insert_with(|| (tokio::time::Instant::now(), 0));
        if *pages >= 8 {
            return false;
        }
        *pages += 1;
        true
    }

    pub(super) fn renewal_leases(&self, at: i64) -> Vec<(Arc<Lease>, i64)> {
        let Some(sessions) = self.lock_sessions() else {
            return Vec::new();
        };
        let mut logins = BTreeMap::new();
        for connection in sessions
            .connections
            .values()
            .filter(|c| c.confirmed.is_some())
        {
            if let State::Authorized(lease) = &*connection.state.borrow()
                && lease.grant.expires_at > at
            {
                let (_, activity) = logins
                    .entry(lease.grant.session_ref.clone())
                    .or_insert_with(|| (Arc::clone(lease), 0));
                *activity = (*activity).max(connection.last_activity);
            }
        }
        logins.into_values().collect()
    }

    pub(super) fn activity(&self, id: &str) {
        if let Some(mut sessions) = self.lock_sessions()
            && let Some(connection) = sessions.connections.get_mut(id)
        {
            connection.last_activity = now();
        }
    }

    pub(super) fn web_roster(&self) -> Arc<ChatWebRoster> {
        let Some(mut sessions) = self.lock_sessions() else {
            return empty_web_roster();
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
        if sessions.roster.players != players {
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
    use super::super::test_support::{grant, open_chat};
    use super::*;
    use std::net::Ipv4Addr;

    #[tokio::test]
    async fn a_rejected_proof_expires_only_its_lease_and_allows_a_fresh_grant() {
        let (_directory, web) = open_chat().await;
        let first = web.register(Ipv4Addr::LOCALHOST.into()).unwrap();
        let at = now() - 2000;
        let old = grant(&first, at);
        let policy = AccountPolicy::default();
        web.authorize(old.clone(), &policy, false, at);
        let mut updated = old.clone();
        updated.checked_at += 1000;
        updated.renewal_token = "replacement".into();
        web.authorize(updated.clone(), &policy, false, at + 1000);
        web.reject_renewal_proof(&old);
        assert!(matches!(*first.state.borrow(), State::Authorized(_)));
        web.reject_renewal_proof(&updated);
        assert!(matches!(
            *first.state.borrow(),
            State::Closed("authorization_expired")
        ));
        let replacement = web.register(Ipv4Addr::LOCALHOST.into()).unwrap();
        updated.connection_id.clone_from(&replacement.id);
        assert!(matches!(
            web.authorize(updated, &policy, false, now()).0,
            Outcome::Accepted
        ));
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
        let (outcome, _) = web.authorize(grant.clone(), &policy, true, started - 1);
        assert!(matches!(outcome, Outcome::Busy));
        let (outcome, _) = web.authorize(grant.clone(), &policy, true, started + 1);
        assert!(matches!(outcome, Outcome::Accepted));
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
    async fn private_and_staff_posts_only_enter_eligible_confirmed_tabs() {
        use crate::domains::chat::{model::Channel, service::WebPost, web::live::Item};
        use futures_util::FutureExt;

        let (_directory, web) = open_chat().await;
        let started = now();
        let login = |staff: bool, confirmed: bool| {
            let connection = web.register(Ipv4Addr::LOCALHOST.into()).unwrap();
            let mut grant = grant(&connection, started);
            grant.session_ref.clone_from(&connection.id);
            assert!(matches!(
                web.authorize(grant.clone(), &AccountPolicy::default(), staff, started)
                    .0,
                Outcome::Accepted
            ));
            if confirmed {
                web.confirm(&connection.id);
            }
            (
                connection,
                ChatPlayer {
                    id: grant.player_uuid,
                    name: grant.player_name,
                },
            )
        };
        let (alice, author) = login(true, true);
        let (bob, target) = login(false, true);
        let (other, _) = login(false, true);
        let (pending, _) = login(true, false);
        web.chat.project_game(Some(Vec::new()));

        for (channel, target_uuid, id) in [
            (Channel::Private, Some(target.id.as_str()), "direct-message"),
            (Channel::Staff, None, "staff-message"),
        ] {
            assert!(
                web.chat
                    .publish_web(
                        WebPost {
                            sender: author.clone(),
                            staff: true,
                            channel,
                            target: target_uuid,
                            text: "hello",
                            request_id: id,
                        },
                        || true
                    )
                    .await
                    .is_ok()
            );
            let Item::Event(sent) = alice.inbox.recv().await.unwrap() else {
                panic!("expected delivery")
            };
            if channel == Channel::Private {
                let Item::Event(received) = bob.inbox.recv().await.unwrap() else {
                    panic!("expected delivery")
                };
                assert!(Arc::ptr_eq(&sent, &received));
            }
            for tab in [&bob, &other, &pending] {
                assert!(tab.inbox.recv().now_or_never().is_none());
            }
        }
        drop((alice, bob, other, pending));
        web.chat.close().await.unwrap();
    }

    #[test]
    fn queued_staff_delivery_survives_one_waiter_and_rechecks_revocation() {
        use crate::domains::chat::{model::Channel, service::WebPost};
        use futures_util::FutureExt;
        use std::sync::mpsc;

        // Keep the only blocking worker occupied until the first viewer disconnects
        // and the second viewer loses staff. This orders the operations without a timer.
        let runtime = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .max_blocking_threads(1)
            .build()
            .unwrap();
        runtime.block_on(async {
            let (_directory, web) = open_chat().await;
            let connection = web.register(Ipv4Addr::LOCALHOST.into()).unwrap();
            let started = now();
            let grant = grant(&connection, started);
            let policy = AccountPolicy::default();
            web.authorize(grant.clone(), &policy, true, started);
            let State::Authorized(lease) = connection.state.borrow().clone() else {
                panic!("staff connection must be authorized");
            };

            web.confirm(&connection.id);
            web.chat.project_game(Some(Vec::new()));
            web.chat
                .publish_web(
                    WebPost {
                        sender: ChatPlayer {
                            id: uuid::Uuid::new_v4().to_string(),
                            name: "Mod".into(),
                        },
                        staff: true,
                        channel: Channel::Staff,
                        target: None,
                        text: "staff broadcast",
                        request_id: "staff-request",
                    },
                    || true,
                )
                .await
                .ok()
                .unwrap();
            let super::super::live::Item::Event(event) = connection.inbox.recv().await.unwrap()
            else {
                panic!("unexpected gap")
            };
            let delivery = event
                .for_viewer(&grant.player_uuid, lease.staff)
                .unwrap()
                .delivery(&[], |_| None)
                .unwrap()
                .unwrap();

            let (release, wait) = mpsc::channel::<()>();
            let occupied = tokio::task::spawn_blocking(move || wait.recv());
            assert!(delivery.prepare(true, || true).now_or_never().is_none());
            let mut pending = Box::pin(delivery.prepare(true, || {
                connection.state.borrow().permits_reply(&lease, now())
            }));
            assert!(pending.as_mut().now_or_never().is_none());
            assert!(matches!(
                web.authorize(grant, &policy, false, started + 1).0,
                Outcome::Accepted
            ));
            release.send(()).unwrap();
            assert!(pending.await.unwrap().is_none());
            occupied.await.unwrap().unwrap();
            assert!(delivery.prepare(true, || true).await.unwrap().is_some());
            drop(connection);
            web.chat.close().await.unwrap();
        });
    }

    async fn answer_account_check(game: &crate::game::GameBridge) {
        use crate::game::GameResult;
        use mik_protocol::wire::GameQueryReply;
        use std::time::Duration;
        let mut pending = None;
        let queue = game.queries(true);
        let job = queue
            .exchange("paper", None, &mut pending, Duration::from_secs(2), true)
            .await
            .job
            .unwrap();
        queue
            .exchange(
                "paper",
                Some(GameQueryReply {
                    id: job.id,
                    result: Some(GameResult::Account(AccountPolicy::default())),
                }),
                &mut pending,
                Duration::ZERO,
                true,
            )
            .await;
    }

    #[tokio::test]
    async fn one_website_check_renews_both_tabs_and_rechecks_paper_then_revocation_closes_both() {
        use crate::{domains::access::Access, game::GameBridge};
        use axum::{Json, Router, http::StatusCode, response::IntoResponse, routing::post};
        use serde_json::{Value, json};
        use std::sync::atomic::{AtomicUsize, Ordering};

        let (_directory, web) = open_chat().await;
        let first = web.register(Ipv4Addr::LOCALHOST.into()).unwrap();
        let second = web.register(Ipv4Addr::LOCALHOST.into()).unwrap();
        let start = now() - 35_000;
        let grant = grant(&first, start);
        for tab in [&first, &second] {
            let mut initial = grant.clone();
            initial.connection_id.clone_from(&tab.id);
            initial.client_public_key.clone_from(&tab.id);
            assert!(matches!(
                web.authorize(initial, &AccountPolicy::default(), true, start)
                    .0,
                Outcome::Accepted
            ));
            web.confirm(&tab.id);
        }
        web.activity(&second.id);
        let mut due = web.renewal_leases(now());
        assert_eq!(due.len(), 1);
        let (lease, activity) = due.pop().unwrap();
        assert!(activity > start);

        let mode = Arc::new(AtomicUsize::new(0));
        let router = Router::new().route("/chat/check", post({
            let mode = mode.clone();
            let grant = grant.clone();
            move |Json(body): Json<Value>| async move {
                assert_eq!(body, json!({"checks":[{"sessionRef":grant.session_ref,"renewalToken":grant.renewal_token,"lastActivityAt":activity}]}));
                match mode.load(Ordering::Relaxed) {
                    0 => StatusCode::SERVICE_UNAVAILABLE.into_response(),
                    1 => Json(json!({"results":[{"sessionRef":grant.session_ref,"status":200,"lease":{"playerUuid":grant.player_uuid,"playerName":"Renamed",
                        "authenticatedAt":grant.authenticated_at,"checkedAt":now(),"expiresAt":now()+55_000}}]})).into_response(),
                    _ => Json(json!({"results":[{"sessionRef":grant.session_ref,"status":401}]})).into_response(),
                }
            }
        }));
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let endpoint = format!("http://{}/chat/check", listener.local_addr().unwrap());
        let server = tokio::spawn(async move {
            axum::serve(listener, router).await.unwrap();
        });
        let game = Arc::new(GameBridge::new());
        let access = Access::new(game.clone(), None);
        let client = crate::http_egress::direct_client(true).build().unwrap();
        let batch = [(lease, activity)];
        let check = || web.check_leases(&client, &endpoint, &access, &batch);
        check().await;
        assert_eq!(web.renewal_leases(now()).len(), 1);
        mode.store(1, Ordering::Relaxed);
        tokio::join!(check(), answer_account_check(&game));
        assert!(
            web.renewal_leases(now())
                .iter()
                .all(|(lease, _)| now() - lease.grant.checked_at < 30_000)
        );
        for tab in [&first, &second] {
            let State::Authorized(current) = tab.state.borrow().clone() else {
                panic!("missing renewal")
            };
            assert!(!current.staff);
            assert_eq!(current.grant.player_name, "Renamed");
            assert!(current.grant.expires_at > grant.expires_at);
            assert_eq!(current.grant.client_public_key, tab.id);
        }
        mode.store(2, Ordering::Relaxed);
        check().await;
        assert!(
            [&first, &second]
                .iter()
                .all(|tab| matches!(*tab.state.borrow(), State::Closed("session_revoked")))
        );
        server.abort();
        drop((first, second));
        web.chat.close().await.unwrap();
    }

    #[tokio::test]
    async fn chat_reads_confirmed_sessions_without_extending_the_web_lifetime() {
        let (_directory, web) = open_chat().await;
        let chat = web.chat.clone();
        chat.project_game(Some(Vec::new()));
        let connection = web.register(Ipv4Addr::LOCALHOST.into()).unwrap();
        let started = now();
        let grant = grant(&connection, started);
        let empty = chat.web_roster();
        assert!(empty.players.is_empty());
        assert!(matches!(
            web.authorize(grant.clone(), &AccountPolicy::default(), true, started)
                .0,
            Outcome::Accepted
        ));
        assert!(Arc::ptr_eq(&chat.web_roster(), &empty));
        web.confirm(&connection.id);
        let available = chat.web_roster();
        assert_eq!(available.players.len(), 1);
        assert!(available.revision > empty.revision);
        assert_eq!(
            available.players[0].player.as_ref().unwrap().id,
            grant.player_uuid
        );

        web.revoke(vec![grant.session_ref]);
        let revoked = chat.web_roster();
        assert!(revoked.players.is_empty());
        assert!(revoked.revision > available.revision);
        drop(connection);
        let weak = Arc::downgrade(&web);
        drop(web);
        assert!(weak.upgrade().is_none());
        assert!(Arc::ptr_eq(&chat.web_roster(), &empty));
        chat.close().await.unwrap();
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
