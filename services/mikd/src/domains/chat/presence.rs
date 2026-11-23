use super::ChatService;
use mik_protocol::wire::{ChatGamePeer, ChatPlayer, ChatWebPeer, HistoryEntry, chat_record::Kind};
use std::collections::HashMap;

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
        !subject.concealed
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

    pub(super) fn restrict(&self, entry: &mut HistoryEntry) {
        let Some(Kind::User(user)) = entry
            .record
            .as_ref()
            .and_then(|record| record.kind.as_ref())
        else {
            return;
        };
        let author = user
            .author
            .as_ref()
            .and_then(|author| author.player.as_ref());
        let subjects = author.into_iter().chain(user.target.as_ref());
        for peer in subjects
            .filter_map(|player| self.positions.get(&player.id))
            .map(|index| &self.peers[*index])
            .filter(|peer| peer.concealed)
        {
            if entry.restricted {
                entry.viewers.retain(|viewer| self.can_see(viewer, peer));
            } else {
                entry.restricted = true;
                entry.viewers = self.viewers(peer).cloned().collect();
            }
        }
    }
}

impl ChatService {
    pub(super) fn hidden_from(&self, viewer: &str) -> Option<Vec<String>> {
        let game = self.game()?;
        let mut hidden: Vec<_> = game
            .iter()
            .filter(|peer| !game.can_see(viewer, peer))
            .filter_map(|peer| peer.player.as_ref())
            .map(|player| player.id.clone())
            .collect();
        hidden.sort_unstable();
        Some(hidden)
    }
}

impl GameView {
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
        web.iter()
            .filter_map(|peer| peer.player.as_ref())
            .find(|player| player.id == id)
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
        for web in web {
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

fn visible(subject: &ChatGamePeer, index: usize) -> bool {
    subject
        .visible_to
        .get(index / 8)
        .is_some_and(|byte| byte & (1 << (index % 8)) != 0)
}
