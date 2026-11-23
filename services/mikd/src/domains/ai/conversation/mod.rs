//! Scoped generation leases and short-lived confirmed conversation history.

mod context;

pub(super) use context::RecentExchanges;

use super::{TurnError, VisibleReply};
use context::History;
use std::{
    collections::HashMap,
    sync::{
        Arc, Mutex,
        atomic::{AtomicU64, Ordering},
    },
    time::Instant,
};
use tokio::sync::{OwnedSemaphorePermit, Semaphore};

const SESSION_LIMIT: usize = 256;

#[derive(Clone, Eq, Hash, PartialEq)]
pub(crate) struct Conversation {
    pub(crate) platform: &'static str,
    pub(crate) account: String,
    pub(crate) room: String,
    pub(crate) thread: Option<String>,
    pub(crate) sender: String,
}

struct Session {
    context: Mutex<Confirmed>,
    active: Arc<Semaphore>,
    sequence: AtomicU64,
}

#[derive(Default)]
struct Confirmed {
    through: u64,
    history: Option<History>,
}

impl Session {
    fn new() -> Self {
        Self {
            context: Mutex::default(),
            active: Arc::new(Semaphore::new(1)),
            sequence: AtomicU64::new(0),
        }
    }
}

pub(super) struct ActiveSession {
    _permit: OwnedSemaphorePermit,
    record: SessionRecord,
}

pub(super) struct SessionRecord {
    session: Arc<Session>,
    sequence: u64,
}

impl ActiveSession {
    pub(super) fn snapshot(&self) -> Result<RecentExchanges, TurnError> {
        let context = self
            .record
            .session
            .context
            .lock()
            .map_err(|_| TurnError::Failed)?;
        Ok(context
            .history
            .as_ref()
            .filter(|task| task.current(Instant::now()))
            .map_or_else(RecentExchanges::new, History::snapshot))
    }

    pub(super) fn finish(self) -> SessionRecord {
        self.record
    }
}

impl SessionRecord {
    pub(super) fn record(self, question: &str, reply: VisibleReply) -> Result<bool, TurnError> {
        let mut context = self.session.context.lock().map_err(|_| TurnError::Failed)?;
        if self.sequence <= context.through {
            return Ok(false);
        }
        context.through = self.sequence;
        History::record(&mut context.history, question, reply, Instant::now());
        Ok(true)
    }
}

#[derive(Default)]
pub(super) struct Sessions {
    entries: Mutex<SessionMap>,
}

type SessionMap = HashMap<Conversation, Arc<Session>>;

impl Sessions {
    pub(super) fn maintain(&self) {
        if let Ok(mut sessions) = self.entries.lock() {
            prune_sessions(&mut sessions, Instant::now());
        }
    }

    pub(super) fn enter(&self, conversation: Conversation) -> Result<ActiveSession, TurnError> {
        let mut sessions = self.entries.lock().map_err(|_| TurnError::Busy)?;
        prune_sessions(&mut sessions, Instant::now());
        if sessions.len() >= SESSION_LIMIT && !sessions.contains_key(&conversation) {
            evict_idle(&mut sessions)?;
        }
        let session = sessions
            .entry(conversation)
            .or_insert_with(|| Arc::new(Session::new()))
            .clone();
        let permit = session
            .active
            .clone()
            .try_acquire_owned()
            .map_err(|_| TurnError::Busy)?;
        let sequence = session.sequence.fetch_add(1, Ordering::Relaxed) + 1;
        Ok(ActiveSession {
            _permit: permit,
            record: SessionRecord { session, sequence },
        })
    }
}

fn prune_sessions(sessions: &mut SessionMap, now: Instant) {
    sessions.retain(|_, session| {
        // A running turn or an unclaimed reply may still populate this session.
        Arc::get_mut(session).is_none_or(|idle| {
            idle.context.get_mut().is_ok_and(|context| {
                context
                    .history
                    .as_ref()
                    .is_some_and(|history| history.current(now))
            })
        })
    });
}

fn evict_idle(sessions: &mut SessionMap) -> Result<(), TurnError> {
    let key = sessions
        .iter_mut()
        .filter_map(|(key, session)| {
            let context = Arc::get_mut(session)?.context.get_mut().ok()?;
            Some((key, context.history.as_ref()?.last_reply()))
        })
        .min_by_key(|(_, used)| *used)
        .map(|(key, _)| key.clone())
        .ok_or(TurnError::Busy)?;
    sessions.remove(&key);
    Ok(())
}
