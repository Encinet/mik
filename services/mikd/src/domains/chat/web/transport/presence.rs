use super::super::{
    application,
    record::{self, Record},
    session::{Lease, Registration, WebChat},
};
use super::{
    Result,
    framing::Output,
    invalid,
    scope::{guard_write, scope_ended},
};
use crate::domains::chat::{now, presence::GameView};
use serde_json::json;
use std::{
    borrow::Cow,
    collections::HashMap,
    sync::{Arc, Weak},
};

#[derive(Default)]
pub(super) struct PresenceUpdates {
    source: Option<(Weak<GameView>, u64, bool)>,
    snapshot: Option<application::Presence>,
    hidden: Option<Vec<String>>,
    missed_view: bool,
    revision: u64,
    sensitive: bool,
}

impl PresenceUpdates {
    pub(super) fn hidden_for(&self, game: &Arc<GameView>, viewer: &str) -> Cow<'_, [String]> {
        if let Some((previous, _, _)) = &self.source
            && std::ptr::eq(previous.as_ptr(), Arc::as_ptr(game))
            && let Some(hidden) = self.hidden.as_deref()
        {
            Cow::Borrowed(hidden)
        } else {
            Cow::Owned(game.hidden_from(viewer))
        }
    }

    pub(super) async fn publish(
        &mut self,
        web: &WebChat,
        session: &Registration<'_>,
        lease: &Arc<Lease>,
        socket: &Output,
    ) -> Result<()> {
        let (record, resync) = self.next(web, lease, socket.deflate).await?;
        if let Some(record) = record {
            let game = web.chat.game();
            if !session.state.borrow().permits_reply(lease, now())
                || !self.current(web, game.as_ref())
            {
                // next() advanced its local delta base. Replace it with a full snapshot on retry.
                self.source = None;
                self.snapshot = None;
                self.missed_view = true;
                return Ok(());
            }
            if let (Some(game), Some(hidden)) = (game, self.hidden.as_deref()) {
                guard_write(
                    socket.record(&record),
                    scope_ended(web, session, lease, &game, hidden),
                )
                .await?;
            } else {
                // The offline roster is empty.
                socket.record(&record).await?;
            }
        }
        if resync {
            socket.json(&json!({"type":"historyChanged"})).await?;
        }
        Ok(())
    }

    pub(super) fn missed(&mut self) {
        self.missed_view = true;
    }

    pub(super) fn needs_publish(&self) -> bool {
        self.snapshot.is_none() || self.missed_view
    }

    fn current(&self, web: &WebChat, game: Option<&Arc<GameView>>) -> bool {
        let roster = web.web_roster();
        match (&self.source, game) {
            (None, None) => true,
            (Some((previous, revision, _)), Some(game)) => {
                *revision == roster.revision && std::ptr::eq(previous.as_ptr(), Arc::as_ptr(game))
            }
            _ => false,
        }
    }

    async fn next(
        &mut self,
        web: &WebChat,
        lease: &Lease,
        deflate: bool,
    ) -> Result<(Option<Record>, bool)> {
        let game = web.chat.game();
        let roster = web.web_roster();
        if let (Some((previous, revision, staff)), Some(game)) = (&self.source, &game)
            && std::ptr::eq(previous.as_ptr(), Arc::as_ptr(game))
            && *revision == roster.revision
            && *staff == lease.staff
            && !self.missed_view
        {
            return Ok((None, false));
        }
        if game.is_none() && self.source.is_none() && self.snapshot.is_some() {
            return Ok((None, false));
        }
        let hidden = game
            .as_ref()
            .map(|view| view.hidden_from(&lease.grant.player_uuid));
        let resync = hidden.is_some()
            && (self.missed_view || (self.snapshot.is_some() && self.hidden.is_none()));
        let scope_changed = self.hidden != hidden;
        self.missed_view = false;
        self.hidden = hidden;
        self.source = game
            .as_ref()
            .map(|view| (Arc::downgrade(view), roster.revision, lease.staff));
        let next = application::presence(game.as_deref(), &roster.players, lease);
        // Names can be chosen by players. Keep individually visible players and staff membership
        // out of a shared dictionary, including removals that refer to the preceding snapshot.
        let sensitive = !next.staff_players.is_empty()
            || game
                .as_ref()
                .is_some_and(|view| view.iter().any(|peer| peer.visible_to.is_some()));
        let previous_sensitive = std::mem::replace(&mut self.sensitive, sensitive);
        let resync = resync || (scope_changed && self.revision > 0 && self.hidden.is_some());
        if self.snapshot.as_ref() == Some(&next) {
            return Ok((None, resync));
        }
        let base = self.revision;
        self.revision += 1;
        let mut full = serde_json::to_value(&next)?;
        full["revision"] = json!(self.revision);
        let full = full.to_string();
        let mut packet = if full.len() <= record::MAX_RESPONSE {
            Some(Record::prepare(full, deflate && !sensitive).await?)
        } else {
            None
        };
        if let Some(previous) = &self.snapshot {
            let mut old: HashMap<_, _> = previous
                .players
                .iter()
                .map(|p| (&p.player_uuid, p))
                .collect();
            let upsert: Vec<_> = next
                .players
                .iter()
                .enumerate()
                .filter(|(_, peer)| old.remove(&peer.player_uuid) != Some(*peer))
                .map(|(index, peer)| json!({"index":index,"player":peer}))
                .collect();
            let mut change = json!({"type":"presenceDelta","base":base,"revision":self.revision,
                "upsert":upsert,"remove":old.into_keys().collect::<Vec<_>>()});
            if previous.staff_players != next.staff_players {
                change["staffPlayers"] = json!(next.staff_players);
            }
            let delta = change.to_string();
            if delta.len() <= record::MAX_RESPONSE {
                let delta =
                    Record::prepare(delta, deflate && !sensitive && !previous_sensitive).await?;
                if packet
                    .as_ref()
                    .is_none_or(|full| delta.payload.len() < full.payload.len())
                {
                    packet = Some(delta);
                }
            }
        }
        let packet = packet.ok_or_else(invalid)?;
        self.snapshot = Some(next);
        Ok((Some(packet), resync))
    }
}
