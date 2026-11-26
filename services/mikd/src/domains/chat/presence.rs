use super::model::{Destination, destination, player_snapshot};
use mik_protocol::wire::{
    ChatEvent, ChatGamePeer, ChatPlayer, ChatVisibility, ChatWebPeer, chat_record::Kind,
};
use std::{collections::HashMap, sync::Arc, time::Duration};
use tokio::{sync::watch, time::Instant};

const GAME_VIEW_MAX_AGE: Duration = Duration::from_secs(3);

pub(super) struct GamePresence {
    projection: watch::Sender<Option<(Instant, Arc<GameView>)>>,
}

impl GamePresence {
    pub(super) fn new() -> Self {
        let (projection, _) = watch::channel(None);
        Self { projection }
    }

    pub(super) fn project(&self, players: Option<Vec<ChatGamePeer>>) {
        if let Some(players) = players {
            let view = Arc::new(GameView::new(players));
            self.projection.send_replace(Some((Instant::now(), view)));
        } else {
            self.projection.send_modify(|projection| {
                if let Some((observed, _)) = projection {
                    *observed = Instant::now();
                }
            });
        }
    }

    pub(super) fn clear(&self) {
        self.projection.send_replace(None);
    }

    pub(super) fn view(&self) -> Option<Arc<GameView>> {
        self.projection
            .borrow()
            .as_ref()
            .filter(|(at, _)| fresh(*at))
            .map(|(_, view)| Arc::clone(view))
    }

    // Only this observer follows Paper heartbeats. Sockets wake on a new snapshot,
    // disappearance or recovery, including expiry without a final Paper update.
    pub(super) async fn notify_changes(&self, changed: &watch::Sender<()>) {
        let mut samples = self.projection.subscribe();
        let mut previous = None;
        loop {
            let sample = samples.borrow_and_update().clone();
            let current = sample.filter(|(at, _)| fresh(*at));
            let view = current.as_ref().map(|(_, view)| Arc::clone(view));
            if !match (&previous, &view) {
                (None, None) => true,
                (Some(old), Some(new)) => Arc::ptr_eq(old, new),
                _ => false,
            } {
                changed.send_replace(());
            }
            previous = view;
            if let Some((at, _)) = current {
                tokio::select! {
                    () = tokio::time::sleep_until(at + GAME_VIEW_MAX_AGE) => {},
                    result = samples.changed() => if result.is_err() { return; },
                }
            } else if samples.changed().await.is_err() {
                return;
            }
        }
    }

    pub(super) async fn wait_for_view(&self, budget: Duration) -> Option<Arc<GameView>> {
        let mut projection = self.projection.subscribe();
        let view = tokio::time::timeout(
            budget,
            projection.wait_for(|view| view.as_ref().is_some_and(|(at, _)| fresh(*at))),
        )
        .await
        .ok()?
        .ok()?;
        view.as_ref().map(|(_, view)| Arc::clone(view))
    }
}

fn fresh(at: Instant) -> bool {
    at.elapsed() < GAME_VIEW_MAX_AGE
}

pub(super) struct GameView {
    peers: Vec<ChatGamePeer>,
    positions: HashMap<String, usize>,
}

pub(super) struct Peer<'snapshot> {
    pub(super) player: &'snapshot ChatPlayer,
    pub(super) game: bool,
    pub(super) staff: bool,
    pub(super) connections: u32,
}

impl GameView {
    pub(super) fn new(peers: Vec<ChatGamePeer>) -> Self {
        let positions = peers
            .iter()
            .enumerate()
            .filter_map(|(index, peer)| {
                peer.player
                    .as_ref()
                    .map(|player| (player.id.clone(), index))
            })
            .collect();
        Self { peers, positions }
    }

    pub(super) fn iter(&self) -> impl Iterator<Item = &ChatGamePeer> {
        self.peers.iter()
    }

    pub(super) fn has_player(&self, id: &str) -> bool {
        self.positions.contains_key(id)
    }

    pub(super) fn can_see(&self, viewer: &str, subject: &ChatGamePeer) -> bool {
        subject.visible_to.is_none()
            || subject
                .player
                .as_ref()
                .is_some_and(|player| player.id == viewer)
            || self
                .positions
                .get(viewer)
                .is_some_and(|index| visible(subject, *index))
    }

    pub(super) fn viewers<'a>(
        &'a self,
        subject: &'a ChatGamePeer,
    ) -> impl Iterator<Item = &'a String> {
        self.peers.iter().enumerate().filter_map(|(index, viewer)| {
            let player = viewer.player.as_ref()?;
            (visible(subject, index) || subject.player.as_ref() == Some(player))
                .then_some(&player.id)
        })
    }

    pub(super) fn restrict(&self, entry: &mut ChatEvent) {
        let Some(record) = &entry.record else { return };
        let (subject, recipient) = match &record.kind {
            Some(Kind::User(user)) => (
                user.author.as_ref().and_then(player_snapshot),
                match destination(record) {
                    Ok(Destination::Private { recipient, .. }) => Some(recipient),
                    _ => None,
                },
            ),
            Some(Kind::Presence(presence)) => (presence.player.as_ref(), None),
            None => return,
        };
        for peer in subject
            .into_iter()
            .chain(recipient)
            .filter_map(|player| self.peer(&player.id))
            .filter(|peer| peer.visible_to.is_some())
        {
            if let Some(visibility) = &mut entry.visibility {
                visibility
                    .player_ids
                    .retain(|viewer| self.can_see(viewer, peer));
            } else {
                entry.visibility = Some(ChatVisibility {
                    player_ids: self.viewers(peer).cloned().collect(),
                });
            }
        }
    }
    pub(super) fn hidden_from(&self, viewer: &str) -> Vec<String> {
        let mut hidden: Vec<_> = self
            .iter()
            .filter(|peer| !self.can_see(viewer, peer))
            .filter_map(|peer| peer.player.as_ref())
            .map(|player| player.id.clone())
            .collect();
        hidden.sort_unstable();
        hidden
    }

    fn peer(&self, id: &str) -> Option<&ChatGamePeer> {
        self.positions.get(id).map(|index| &self.peers[*index])
    }

    pub(super) fn player<'snapshot>(
        &'snapshot self,
        viewer: &str,
        id: &str,
        web: &'snapshot [ChatWebPeer],
    ) -> Option<&'snapshot ChatPlayer> {
        if let Some(peer) = self.peer(id) {
            if !self.can_see(viewer, peer) {
                return None;
            }
            if peer.eligible {
                return peer.player.as_ref();
            }
        }
        active_web_players(web).find(|player| player.id == id)
    }

    pub(super) fn hidden_player(&self, viewer: &str, id: &str) -> bool {
        self.peer(id)
            .is_some_and(|peer| !self.can_see(viewer, peer))
    }

    pub(super) fn web_player<'snapshot>(
        &self,
        viewer: &str,
        id: &str,
        web: &'snapshot [ChatWebPeer],
    ) -> Option<&'snapshot ChatPlayer> {
        if self.hidden_player(viewer, id) {
            return None;
        }
        active_web_players(web).find(|player| player.id == id)
    }

    pub(super) fn peers<'snapshot>(
        &'snapshot self,
        viewer: &str,
        web: &'snapshot [ChatWebPeer],
    ) -> Vec<Peer<'snapshot>> {
        let mut peers = HashMap::with_capacity(self.peers.len() + web.len());
        for peer in self
            .iter()
            .filter(|peer| peer.eligible && self.can_see(viewer, peer))
        {
            let Some(player) = &peer.player else { continue };
            peers.insert(
                player.id.as_str(),
                Peer {
                    player,
                    game: true,
                    staff: peer.staff,
                    connections: 0,
                },
            );
        }
        for web in web.iter().filter(|peer| peer.connections > 0) {
            let Some(player) = &web.player else { continue };
            if self
                .peer(&player.id)
                .is_some_and(|peer| !self.can_see(viewer, peer))
            {
                continue;
            }
            let peer = peers.entry(player.id.as_str()).or_insert(Peer {
                player,
                game: false,
                staff: false,
                connections: 0,
            });
            peer.connections = web.connections;
            peer.staff |= web.staff;
        }
        peers.into_values().collect()
    }
}

pub(super) fn active_web_players(web: &[ChatWebPeer]) -> impl Iterator<Item = &ChatPlayer> {
    web.iter()
        .filter(|peer| peer.connections > 0)
        .filter_map(|peer| peer.player.as_ref())
}

fn visible(subject: &ChatGamePeer, index: usize) -> bool {
    subject
        .visible_to
        .as_ref()
        .and_then(|bits| bits.get(index / 8))
        .is_some_and(|byte| byte & (1 << (index % 8)) != 0)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test(start_paused = true)]
    async fn unchanged_samples_only_wake_web_sockets_on_expiry_and_recovery() {
        let game = Arc::new(GamePresence::new());
        let (changed, mut events) = watch::channel(());
        let task = tokio::spawn({
            let game = Arc::clone(&game);
            async move {
                game.notify_changes(&changed).await;
            }
        });
        game.project(Some(Vec::new()));
        events.changed().await.unwrap();
        for _ in 0..5 {
            tokio::time::advance(Duration::from_secs(1)).await;
            game.project(None);
            tokio::task::yield_now().await;
            assert!(!events.has_changed().unwrap());
        }
        tokio::time::advance(GAME_VIEW_MAX_AGE).await;
        events.changed().await.unwrap();
        assert!(game.view().is_none());
        game.project(None);
        events.changed().await.unwrap();
        assert!(game.view().is_some());
        game.clear();
        events.changed().await.unwrap();
        task.abort();
    }

    #[tokio::test(start_paused = true)]
    async fn waiters_get_a_fresh_projection_and_expired_views_are_not_reused() {
        let game = GamePresence::new();
        let budget = Duration::from_millis(750);
        let (ready, ()) = tokio::join!(game.wait_for_view(budget), async {
            tokio::time::sleep(Duration::from_millis(100)).await;
            game.project(Some(Vec::new()));
        });
        let ready = ready.unwrap();

        tokio::time::advance(GAME_VIEW_MAX_AGE).await;
        assert!(game.view().is_none());
        assert!(game.wait_for_view(budget).await.is_none());

        game.project(None);
        assert!(Arc::ptr_eq(&ready, &game.view().unwrap()));
        game.clear();
        game.project(None);
        assert!(game.view().is_none());
    }
}
