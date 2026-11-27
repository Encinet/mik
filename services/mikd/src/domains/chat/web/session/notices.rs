use crate::domains::chat::{ChatService, now};
use mik_protocol::wire::{
    ChatEvent, ChatPlayer, ChatPresenceNotice, ChatRecord, chat_presence_notice::Kind, chat_record,
};
use std::{collections::VecDeque, time::Duration};
use tokio::time::Instant;

const RECONNECT_GRACE: Duration = Duration::from_secs(30);

#[derive(Default)]
pub(super) struct PresenceNotices {
    // The last confirmed connection schedules a single departure per UUID, in deadline order.
    departures: VecDeque<Departure>,
}

struct Departure {
    player_id: String,
    deadline: Instant,
    notice: Option<ChatEvent>,
}

impl PresenceNotices {
    pub(super) fn left(&mut self, chat: &ChatService, player: ChatPlayer) {
        let player_id = player.id.clone();
        let deadline = Instant::now() + RECONNECT_GRACE;
        // Keep the visibility ceiling at disconnect. Publication intersects it with the
        // current Paper view, so changes during the grace period cannot reveal a hidden player.
        let notice = chat.game().map(|view| {
            let mut entry = notice(player, Kind::Left);
            view.restrict(&mut entry);
            entry
        });
        self.departures.push_back(Departure {
            player_id,
            deadline,
            notice,
        });
    }

    pub(super) fn joined(&mut self, chat: &ChatService, player: &ChatPlayer) {
        // A reconnect at or after the deadline must see the leave even if the timer is still queued.
        self.publish_due(chat);
        if let Some(index) = self
            .departures
            .iter()
            .position(|departure| departure.player_id == player.id)
        {
            self.departures.remove(index);
        } else {
            publish(chat, notice(player.clone(), Kind::Joined));
        }
    }

    pub(super) fn publish_due(&mut self, chat: &ChatService) -> Option<Instant> {
        let now = Instant::now();
        while let Some(departure) = self.departures.pop_front_if(|next| next.deadline <= now) {
            if let Some(entry) = departure.notice {
                publish(chat, entry);
            }
        }
        self.departures.front().map(|next| next.deadline)
    }
}

fn notice(player: ChatPlayer, kind: Kind) -> ChatEvent {
    ChatEvent {
        message_id: uuid::Uuid::new_v4().to_string(),
        record: Some(ChatRecord {
            created_at_ms: now(),
            platform: "web".into(),
            kind: Some(chat_record::Kind::Presence(ChatPresenceNotice {
                kind: kind.into(),
                player: Some(player),
            })),
        }),
        ..ChatEvent::default()
    }
}

fn publish(chat: &ChatService, entry: ChatEvent) {
    if let Err(error) = chat.enqueue_presence(entry) {
        eprintln!("mikd web chat presence notice dropped: {error}");
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::domains::chat::{
        Committed,
        web::{
            session::{Outcome, Registration, WebChat},
            test_support::{grant, open_chat},
        },
    };
    use mik_protocol::wire::{AccountPolicy, ChatGamePeer};
    use std::{collections::HashSet, net::Ipv4Addr, sync::Arc};
    use tokio::sync::broadcast;

    fn player(name: &str) -> ChatPlayer {
        ChatPlayer {
            id: uuid::Uuid::new_v4().to_string(),
            name: name.to_owned(),
        }
    }

    fn authorized<'a>(web: &'a WebChat, player: &ChatPlayer) -> Registration<'a> {
        let connection = web.register(Ipv4Addr::LOCALHOST.into()).unwrap();
        let mut login = grant(&connection, now());
        login.player_uuid.clone_from(&player.id);
        login.player_name.clone_from(&player.name);
        login.session_ref.clone_from(&connection.id);
        assert!(matches!(
            web.authorize(login, &AccountPolicy::default(), false, now())
                .0,
            Outcome::Accepted
        ));
        connection
    }

    fn committed_notices(
        mut receiver: broadcast::Receiver<Arc<Committed>>,
    ) -> Vec<(Kind, Option<HashSet<String>>)> {
        let mut notices = Vec::new();
        while let Ok(event) = receiver.try_recv() {
            let record = event.entry.record.as_ref().unwrap();
            let Some(chat_record::Kind::Presence(notice)) = &record.kind else {
                panic!("unexpected chat record")
            };
            notices.push((
                notice.kind(),
                event
                    .entry
                    .visibility
                    .as_ref()
                    .map(|v| v.player_ids.iter().cloned().collect()),
            ));
        }
        notices
    }

    #[tokio::test(start_paused = true)]
    async fn short_reconnects_cancel_notices_but_only_proven_connections_count_as_online() {
        let (_directory, web) = open_chat().await;
        let events = web.chat.subscribe();
        let observer = tokio::spawn({
            let web = web.clone();
            async move { web.announce_departures().await }
        });
        tokio::task::yield_now().await;
        web.chat.project_game(Some(Vec::new()));
        let player = player("Player");
        let first = authorized(&web, &player);
        let second = authorized(&web, &player);
        web.confirm(&first.id);
        web.confirm(&second.id);
        assert_eq!(web.web_roster().players[0].connections, 2);
        drop(first);
        assert_eq!(web.web_roster().players[0].connections, 1);
        drop(second);
        assert!(web.web_roster().players.is_empty());

        let tick = Duration::from_secs(1);
        let before_deadline = RECONNECT_GRACE.checked_sub(tick).unwrap();
        tokio::time::advance(before_deadline).await;
        web.chat.project_game(None);
        let replacement = authorized(&web, &player);
        web.confirm(&replacement.id);
        assert_eq!(web.web_roster().players[0].connections, 1);
        tokio::time::advance(tick).await;
        web.revoke(vec![replacement.id.clone()]);
        assert!(web.web_roster().players.is_empty());
        drop(replacement);
        let unconfirmed = authorized(&web, &player);
        tokio::time::advance(before_deadline).await;
        web.chat.project_game(None);
        tokio::time::advance(tick).await;
        tokio::task::yield_now().await;
        assert!(web.web_roster().players.is_empty());
        drop(unconfirmed);
        observer.abort();
        web.chat.close().await.unwrap();
        assert_eq!(
            committed_notices(events),
            [(Kind::Joined, None), (Kind::Left, None)]
        );
    }

    #[tokio::test(start_paused = true)]
    async fn rejoin_at_deadline_publishes_leave_first_with_both_visibility_limits() {
        let (_directory, web) = open_chat().await;
        let events = web.chat.subscribe();
        let players = ["Subject", "Before", "After", "Both"].map(player);
        let view = |mask| {
            players
                .iter()
                .enumerate()
                .map(|(index, player)| ChatGamePeer {
                    player: Some(player.clone()),
                    eligible: true,
                    visible_to: (index == 0).then_some(vec![mask]),
                    ..ChatGamePeer::default()
                })
                .collect()
        };
        web.chat.project_game(Some(view(0b1010)));
        let first = authorized(&web, &players[0]);
        web.confirm(&first.id);
        drop(first);
        // Leave the timer unpolled. The new confirmation itself must settle the elapsed deadline.
        tokio::time::advance(RECONNECT_GRACE).await;
        web.chat.project_game(Some(view(0b1100)));
        let next = authorized(&web, &players[0]);
        web.confirm(&next.id);
        drop(next);
        web.chat.close().await.unwrap();
        let ids =
            |indices: &[usize]| Some(indices.iter().map(|i| players[*i].id.clone()).collect());
        assert_eq!(
            committed_notices(events),
            [
                (Kind::Joined, ids(&[0, 1, 3])),
                (Kind::Left, ids(&[0, 3])),
                (Kind::Joined, ids(&[0, 2, 3])),
            ]
        );
    }
}
