use mik_protocol::wire::{GameRoster, GameStatusView, PlayerSnapshot};
use std::{
    collections::HashMap,
    sync::{Arc, Mutex, PoisonError},
    time::Duration,
};
use tokio::time::Instant;

const MAX_AGE: Duration = Duration::from_secs(5);

type Projections = Arc<Mutex<HashMap<String, Entry>>>;

struct Entry {
    owner: String,
    updated: Instant,
    snapshot: Arc<GameSnapshot>,
}

pub(crate) struct GameSnapshot {
    pub(crate) version: u64,
    pub(crate) players: Arc<[PlayerSnapshot]>,
    pub(crate) server: GameStatusView,
}

#[derive(Default)]
pub(crate) struct GameState(Projections);

pub(crate) struct Publication {
    projections: Projections,
    instance: String,
    owner: String,
    pub(crate) version: u64,
    players: Arc<[PlayerSnapshot]>,
}

impl GameState {
    pub(crate) fn publisher(&self, instance: &str, owner: &str) -> Publication {
        Publication {
            projections: self.0.clone(),
            instance: instance.into(),
            owner: owner.into(),
            version: 0,
            players: Arc::default(),
        }
    }

    pub(crate) fn current(&self) -> Option<Arc<GameSnapshot>> {
        let entries = self.0.lock().ok()?;
        let mut live = entries
            .values()
            .filter(|entry| entry.updated.elapsed() < MAX_AGE);
        let entry = live.next()?;
        live.next().is_none().then(|| entry.snapshot.clone())
    }

    pub(crate) fn current_for(&self, instance: &str) -> Option<Arc<GameSnapshot>> {
        let entries = self.0.lock().ok()?;
        let entry = entries.get(instance)?;
        (entry.updated.elapsed() < MAX_AGE).then(|| entry.snapshot.clone())
    }
}

impl Publication {
    pub(crate) fn publish(
        &mut self,
        version: u64,
        server: GameStatusView,
        roster: Option<GameRoster>,
    ) -> bool {
        let Ok(mut entries) = self.projections.lock() else {
            return false;
        };
        self.version = version;
        if let Some(roster) = roster {
            self.players = roster.players.into();
        }
        entries.insert(
            self.instance.clone(),
            Entry {
                owner: self.owner.clone(),
                updated: Instant::now(),
                snapshot: Arc::new(GameSnapshot {
                    version,
                    players: self.players.clone(),
                    server,
                }),
            },
        );
        true
    }
}

impl Drop for Publication {
    fn drop(&mut self) {
        let mut entries = self
            .projections
            .lock()
            .unwrap_or_else(PoisonError::into_inner);
        if entries
            .get(&self.instance)
            .is_some_and(|entry| entry.owner == self.owner)
        {
            entries.remove(&self.instance);
        }
    }
}
