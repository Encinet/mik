use super::super::session::{Lease, Registration, WebChat};
use super::{
    Result,
    framing::Output,
    scope::{guard_write, permits_delivery, scope_ended},
};
use crate::domains::chat::history::LiveAnchor;
use serde_json::{Value, json};
use std::{collections::HashMap, sync::Arc, time::Duration};
use tokio::{
    sync::{Mutex, Notify, watch},
    time::Instant,
};

#[derive(Default)]
pub(super) struct Progress {
    pub(super) state: Mutex<LiveProgress>,
    pub(super) changed: Notify,
    pub(super) gaps: watch::Sender<()>,
}

impl Progress {
    pub(super) async fn missed(&self) {
        self.state.lock().await.clear();
        self.gaps.send_replace(());
    }

    pub(super) async fn flush(
        &self,
        output: &Output,
        session: &Registration<'_>,
        lease: &Arc<Lease>,
        web: &WebChat,
    ) -> Result<()> {
        let mut state = self.state.lock().await;
        let now = Instant::now();
        if state.deadline(now).is_none_or(|at| at > now) {
            return Ok(());
        }
        let Some(game) = web.chat.game() else {
            state.clear();
            return Ok(());
        };
        let hidden = game.hidden_from(&lease.grant.player_uuid);
        state.retain_scope(&hidden, lease);
        let updates = state.checkpoints(now)?;
        drop(state);
        for update in updates {
            if !permits_delivery(web, session, lease, &game, &hidden) {
                break;
            }
            guard_write(
                output.json(&update),
                scope_ended(web, session, lease, &game, &hidden),
            )
            .await?;
        }
        Ok(())
    }
}

#[derive(Default)]
pub(super) struct LiveProgress {
    channels: HashMap<&'static str, ChannelProgress>,
    missed: HashMap<&'static str, i64>,
    delivered: HashMap<&'static str, i64>,
    hidden: Vec<String>,
    staff_epoch: u64,
    last_flush: Option<Instant>,
}

struct ChannelProgress {
    exchange_id: u64,
    anchor: LiveAnchor,
    epoch: u64,
    delivered: Option<i64>,
    next_page: Option<String>,
}

/// Held by the history exchange until its completion is written.
pub(super) struct HistoryCommit {
    pub(super) channel: &'static str,
    pub(super) hidden: Vec<String>,
    pub(super) anchor: Option<LiveAnchor>,
    pub(super) continuation: Option<String>,
    pub(super) next_page: Option<String>,
    pub(super) checkpoint: Option<String>,
    pub(super) reset: bool,
}

impl LiveProgress {
    pub(super) fn clear(&mut self) {
        self.channels.clear();
        self.delivered.clear();
    }
    // A history query may finish after its snapshot was overtaken by an inbox overflow.
    // Remember that floor even after clearing the old anchor or receiving a stale continuation.
    pub(super) fn missed_through(&mut self, channel: &'static str, cursor: i64) {
        let floor = self.missed.entry(channel).or_default();
        *floor = (*floor).max(cursor);
        if self
            .channels
            .get(channel)
            .is_some_and(|state| state.anchor.through < *floor)
        {
            self.channels.remove(channel);
        }
    }

    fn continues(&self, page: &HistoryCommit, lease: &Lease) -> bool {
        !page.reset
            && page.continuation.is_some()
            && self.channels.get(page.channel).is_some_and(|current| {
                page.continuation == current.next_page
                    && page.hidden == current.anchor.hidden
                    && (page.channel != "staff"
                        || (lease.staff && lease.authority_epoch == current.epoch))
            })
    }

    pub(super) fn aligned(&self, page: &HistoryCommit, lease: &Lease) -> bool {
        page.next_page.is_none() && page.checkpoint.is_some() && self.continues(page, lease)
    }

    pub(super) fn commit(&mut self, page: HistoryCommit, lease: &Lease, exchange_id: u64) {
        if let Some(anchor) = page.anchor {
            if self
                .missed
                .get(page.channel)
                .is_some_and(|floor| anchor.through < *floor)
                || self
                    .channels
                    .get(page.channel)
                    .is_some_and(|newer| newer.exchange_id > exchange_id)
            {
                return;
            }
            // Live frames can arrive between the SQLite snapshot and the end of the exchange.
            let delivered = self
                .delivered
                .get(anchor.channel)
                .copied()
                .filter(|cursor| *cursor > anchor.through);
            self.channels.insert(
                anchor.channel,
                ChannelProgress {
                    exchange_id,
                    anchor,
                    epoch: lease.authority_epoch,
                    delivered,
                    next_page: page.next_page,
                },
            );
        } else if self.continues(&page, lease)
            && let Some(current) = self.channels.get_mut(page.channel)
        {
            current.exchange_id = exchange_id;
            current.next_page = page.next_page;
            if current.next_page.is_none()
                && let Some(checkpoint) = page.checkpoint
            {
                current.anchor.token = checkpoint;
            }
        }
    }

    pub(super) fn retain_scope(&mut self, hidden: &[String], lease: &Lease) {
        if self.hidden != hidden {
            self.delivered.clear();
            hidden.clone_into(&mut self.hidden);
        }
        if !lease.staff || self.staff_epoch != lease.authority_epoch {
            self.delivered.remove("staff");
        }
        self.staff_epoch = lease.authority_epoch;
        self.channels.retain(|channel, state| {
            state.anchor.hidden == hidden
                && (*channel != "staff" || (lease.staff && state.epoch == lease.authority_epoch))
        });
    }

    pub(super) fn delivered(&mut self, channel: &'static str, cursor: i64) {
        let through = self.delivered.entry(channel).or_default();
        *through = (*through).max(cursor);
        if let Some(state) = self.channels.get_mut(channel)
            && cursor > state.anchor.through
        {
            state.delivered = Some(*through);
        }
    }

    pub(super) fn deadline(&self, now: Instant) -> Option<Instant> {
        self.channels
            .values()
            .any(|state| state.next_page.is_none() && state.delivered.is_some())
            .then(|| {
                self.last_flush
                    .map_or(now, |last| last + Duration::from_secs(5))
            })
    }

    fn checkpoints(&mut self, now: Instant) -> Result<Vec<Value>> {
        self.last_flush = Some(now);
        let mut updates = Vec::new();
        for (channel, state) in &mut self.channels {
            if state.next_page.is_none()
                && let Some(cursor) = state.delivered.take()
            {
                let anchor = &mut state.anchor;
                let from = anchor.advance(cursor)?;
                updates.push(
                    json!({"type":"checkpoint","channel":channel,"from":from,"to":anchor.token}),
                );
            }
        }
        Ok(updates)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::domains::chat::{
        history::{Audience, SyncPage, SyncRequest},
        model::Channel,
        now,
        service::WebPost,
        web::{
            session::{Outcome, State},
            test_support::{grant, open_chat},
        },
    };
    use mik_protocol::wire::{AccountPolicy, ChatPlayer};
    use std::net::Ipv4Addr;

    fn snapshot(page: SyncPage, channel: Channel) -> HistoryCommit {
        let token = page.checkpoint().unwrap();
        HistoryCommit {
            channel: channel.name(),
            anchor: Some(page.into_live_anchor(channel, &[], token.clone())),
            hidden: Vec::new(),
            continuation: None,
            next_page: None,
            checkpoint: Some(token),
            reset: false,
        }
    }

    #[tokio::test]
    async fn a_stale_snapshot_cannot_restore_a_channel_across_a_live_gap() {
        let (_directory, web) = open_chat().await;
        let session = web.register(Ipv4Addr::LOCALHOST.into()).unwrap();
        let at = now();
        let grant = grant(&session, at);
        assert!(matches!(
            web.authorize(grant, &AccountPolicy::default(), false, at).0,
            Outcome::Accepted
        ));
        let State::Authorized(lease) = session.state.borrow().clone() else {
            panic!("missing lease")
        };
        let read = |channel| {
            web.chat.history().sync(SyncRequest {
                audience: Audience {
                    viewer: lease.grant.player_uuid.clone(),
                    staff: false,
                    channel,
                    target: None,
                },
                since: None,
                continuation: None,
                hidden: Vec::new(),
            })
        };
        let mut progress = LiveProgress::default();
        let before_gap = read(Channel::Private).await.unwrap();
        // The public live event is already on the wire before its history exchange completes.
        progress.delivered("public", 1);
        progress.commit(
            snapshot(read(Channel::Public).await.unwrap(), Channel::Public),
            &lease,
            4,
        );
        progress.missed_through("private", 1);
        progress.commit(snapshot(before_gap, Channel::Private), &lease, 8);
        progress.delivered("private", 2);
        let now = Instant::now();
        assert_eq!(progress.deadline(now), Some(now));
        let updates = progress.checkpoints(now).unwrap();
        assert_eq!(updates.len(), 1);
        assert_eq!(updates[0]["channel"], "public");

        web.chat.project_game(Some(Vec::new()));
        assert!(
            web.chat
                .publish_web(
                    WebPost {
                        sender: ChatPlayer {
                            id: lease.grant.player_uuid.clone(),
                            name: "Player".into()
                        },
                        staff: false,
                        channel: Channel::Public,
                        target: None,
                        text: "advance the database",
                        request_id: "progress",
                    },
                    || true
                )
                .await
                .is_ok()
        );
        progress.commit(
            snapshot(read(Channel::Private).await.unwrap(), Channel::Private),
            &lease,
            12,
        );
        progress.delivered("private", 2);
        let updates = progress.checkpoints(Instant::now()).unwrap();
        assert_eq!(updates.len(), 1);
        assert_eq!(updates[0]["channel"], "private");
        drop(session);
        web.chat.close().await.unwrap();
    }
}
