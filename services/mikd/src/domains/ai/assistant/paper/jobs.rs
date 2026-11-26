//! Paper generation, pollable outcomes, and delivery confirmations have separate lifetimes.

use crate::domains::ai::{ReasoningSummaries, TurnError, assistant::DeliveryReceipt};
use lru::LruCache;
use mik_protocol::wire::AssistantReply;
use std::{
    collections::HashMap,
    future::Future,
    num::NonZeroUsize,
    sync::{Arc, Mutex},
    time::Duration,
};
use tokio::{sync::watch, time::Instant};
use tokio_util::{
    sync::{CancellationToken, DropGuard},
    task::TaskTracker,
};
use uuid::Uuid;

const RESULT_TTL: Duration = Duration::from_secs(45);
const RECEIPT_TTL: Duration = Duration::from_secs(60);
const CLOSED_TTL: Duration = Duration::from_secs(60);
const RESULT_LIMIT: usize = 128;
const CLOSED_LIMIT: usize = 512;

#[derive(Debug)]
pub(super) enum JobError {
    Invalid,
    Busy,
    Unavailable,
}

#[derive(Clone, Eq, Hash, PartialEq)]
pub(super) struct Scope {
    pub(super) instance: String,
    pub(super) player: Uuid,
}

pub(super) struct Ready {
    pub(super) reply: AssistantReply,
    pub(super) receipt: DeliveryReceipt,
}

pub(super) enum Poll {
    Pending(String),
    Ready(Result<AssistantReply, TurnError>),
}

struct Job {
    id: Uuid,
    summaries: ReasoningSummaries,
    _cancel: DropGuard,
}

struct Finished {
    result: Result<Ready, TurnError>,
    poll_until: Instant,
    receipt_until: Option<Instant>,
}

impl Finished {
    fn expired(&self, now: Instant) -> bool {
        self.receipt_until.unwrap_or(self.poll_until) <= now
    }
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum Disposition {
    Discarded,
    Delivered,
}

struct ClosedTurn {
    disposition: Disposition,
    expires_at: Instant,
}

struct Entries {
    jobs: HashMap<Scope, Job>,
    finished: LruCache<(Scope, Uuid), Finished>,
    closed: LruCache<(Scope, Uuid), ClosedTurn>,
}

impl Entries {
    fn take_active(&mut self, scope: &Scope, id: Uuid) -> Option<Job> {
        if self.jobs.get(scope).is_some_and(|job| job.id == id) {
            self.jobs.remove(scope)
        } else {
            None
        }
    }

    fn finish(&mut self, scope: &Scope, id: Uuid, result: Result<Ready, TurnError>) {
        if self.take_active(scope, id).is_none() {
            return;
        }
        if let Some(((old_scope, old_id), _)) = self.finished.push(
            (scope.clone(), id),
            Finished {
                result,
                poll_until: Instant::now() + RESULT_TTL,
                receipt_until: None,
            },
        ) {
            self.remember_closed(&old_scope, old_id, Disposition::Discarded);
        }
    }

    fn disposition(&self, scope: &Scope, id: Uuid) -> Option<Disposition> {
        self.closed
            .peek(&(scope.clone(), id))
            .filter(|closed| closed.expires_at > Instant::now())
            .map(|closed| closed.disposition)
    }

    fn remember_closed(&mut self, scope: &Scope, id: Uuid, disposition: Disposition) {
        let _evicted = self.closed.push(
            (scope.clone(), id),
            ClosedTurn {
                disposition,
                expires_at: Instant::now() + CLOSED_TTL,
            },
        );
    }

    fn delivered(&mut self, scope: &Scope, id: Uuid) -> Result<(), JobError> {
        if self.jobs.get(scope).is_some_and(|job| job.id == id) {
            return Err(JobError::Invalid);
        }
        if self.disposition(scope, id) == Some(Disposition::Delivered) {
            return Ok(());
        }
        let key = (scope.clone(), id);
        if let Some(finished) = self.finished.peek(&key)
            && finished.result.is_ok()
            && finished.receipt_until.is_none()
            && !finished.expired(Instant::now())
        {
            return Err(JobError::Invalid);
        }
        let finished = self.finished.pop(&key).ok_or(JobError::Unavailable)?;
        let Finished {
            result: Ok(ready),
            receipt_until: Some(until),
            ..
        } = finished
        else {
            self.remember_closed(scope, id, Disposition::Discarded);
            return Err(JobError::Unavailable);
        };
        if until <= Instant::now() {
            self.remember_closed(scope, id, Disposition::Discarded);
            return Err(JobError::Unavailable);
        }
        ready.receipt.delivered();
        self.remember_closed(scope, id, Disposition::Delivered);
        Ok(())
    }

    fn maintain(&mut self) {
        let now = Instant::now();
        let expired: Vec<_> = self
            .finished
            .iter()
            .filter(|(_, result)| result.expired(now))
            .map(|(key, _)| key.clone())
            .collect();
        for (scope, id) in expired {
            drop(self.finished.pop(&(scope.clone(), id)));
            self.remember_closed(&scope, id, Disposition::Discarded);
        }
    }
}

// A worker may unwind or be cancelled after its scope has already admitted a different turn.
struct Cleanup {
    entries: Arc<Mutex<Entries>>,
    scope: Scope,
    id: Uuid,
}

impl Drop for Cleanup {
    fn drop(&mut self) {
        if let Ok(mut entries) = self.entries.lock()
            && entries.take_active(&self.scope, self.id).is_some()
        {
            entries.remember_closed(&self.scope, self.id, Disposition::Discarded);
        }
    }
}

pub(in crate::domains::ai::assistant) struct Jobs {
    entries: Arc<Mutex<Entries>>,
    tasks: TaskTracker,
    stop: CancellationToken,
}

impl Jobs {
    pub(in crate::domains::ai::assistant) fn new() -> Self {
        Self {
            entries: Arc::new(Mutex::new(Entries {
                jobs: HashMap::new(),
                finished: LruCache::new(
                    NonZeroUsize::new(RESULT_LIMIT).unwrap_or(NonZeroUsize::MIN),
                ),
                closed: LruCache::new(NonZeroUsize::new(CLOSED_LIMIT).unwrap_or(NonZeroUsize::MIN)),
            })),
            tasks: TaskTracker::new(),
            stop: CancellationToken::new(),
        }
    }

    pub(super) fn start<F>(
        &self,
        scope: Scope,
        id: Uuid,
        previous_displayed: Option<Uuid>,
        prepare: impl FnOnce(watch::Sender<String>) -> Result<F, JobError>,
    ) -> Result<(), JobError>
    where
        F: Future<Output = Result<Ready, TurnError>> + Send + 'static,
    {
        let mut entries = self.entries.lock().map_err(|_| JobError::Busy)?;
        if entries.disposition(&scope, id).is_some()
            || entries.finished.contains(&(scope.clone(), id))
        {
            return Err(JobError::Unavailable);
        }
        if let Some(previous) = previous_displayed {
            if previous == id {
                return Err(JobError::Invalid);
            }
            match entries.delivered(&scope, previous) {
                Ok(()) | Err(JobError::Unavailable) => {}
                Err(error) => return Err(error),
            }
        }
        if self.stop.is_cancelled() || entries.jobs.contains_key(&scope) {
            return Err(JobError::Busy);
        }
        let (updates, summaries) = watch::channel(String::new());
        let future = prepare(updates)?;
        let cancel = self.stop.child_token();
        entries.jobs.insert(
            scope.clone(),
            Job {
                id,
                summaries,
                _cancel: cancel.clone().drop_guard(),
            },
        );
        let cleanup = Cleanup {
            entries: self.entries.clone(),
            scope,
            id,
        };
        // Register with close before dropping the admission lock.
        let worker = self.tasks.track_future(async move {
            let result = tokio::select! {
                biased;
                () = cancel.cancelled() => return,
                ready = future => ready,
            };
            if let Ok(mut entries) = cleanup.entries.lock() {
                entries.finish(&cleanup.scope, id, result);
            }
        });
        drop(entries);
        tokio::spawn(worker);
        Ok(())
    }

    pub(super) fn poll(&self, scope: &Scope, id: Uuid) -> Result<Poll, JobError> {
        let mut entries = self.entries.lock().map_err(|_| JobError::Busy)?;
        if let Some(job) = entries.jobs.get(scope).filter(|job| job.id == id) {
            return Ok(Poll::Pending(job.summaries.borrow().clone()));
        }
        let now = Instant::now();
        let finished = entries
            .finished
            .get_mut(&(scope.clone(), id))
            .filter(|finished| finished.poll_until > now)
            .ok_or(JobError::Unavailable)?;
        Ok(Poll::Ready(match &finished.result {
            Ok(ready) => {
                let _deadline = finished.receipt_until.get_or_insert(now + RECEIPT_TTL);
                Ok(ready.reply.clone())
            }
            Err(error) => Err(*error),
        }))
    }

    pub(super) fn delivered(&self, scope: &Scope, id: Uuid) -> Result<(), JobError> {
        self.entries
            .lock()
            .map_err(|_| JobError::Busy)?
            .delivered(scope, id)
    }

    pub(super) fn discard(&self, scope: &Scope, id: Uuid) -> Result<(), JobError> {
        let mut entries = self.entries.lock().map_err(|_| JobError::Busy)?;
        if entries.disposition(scope, id) == Some(Disposition::Delivered) {
            return Err(JobError::Unavailable);
        }
        drop(entries.take_active(scope, id));
        drop(entries.finished.pop(&(scope.clone(), id)));
        if entries.disposition(scope, id).is_none() {
            entries.remember_closed(scope, id, Disposition::Discarded);
        }
        Ok(())
    }

    pub(in crate::domains::ai::assistant) fn maintain(&self) {
        if let Ok(mut entries) = self.entries.lock() {
            entries.maintain();
        }
    }

    pub(in crate::domains::ai::assistant) async fn close(&self) {
        self.stop.cancel();
        if let Ok(mut entries) = self.entries.lock() {
            entries.jobs.clear();
            entries.finished.clear();
        }
        self.tasks.close();
        self.tasks.wait().await;
    }
}

impl Drop for Jobs {
    fn drop(&mut self) {
        self.stop.cancel();
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn player() -> Scope {
        Scope {
            instance: "primary".into(),
            player: Uuid::new_v4(),
        }
    }

    #[tokio::test]
    async fn panicking_worker_does_not_strand_a_turn() {
        let jobs = Jobs::new();
        let scope = player();
        let first = Uuid::new_v4();
        jobs.start(scope.clone(), first, None, |_| {
            Ok(async { panic!("worker failed") })
        })
        .unwrap();

        jobs.tasks.close();
        jobs.tasks.wait().await;
        assert!(matches!(
            jobs.poll(&scope, first),
            Err(JobError::Unavailable)
        ));
        let next = Uuid::new_v4();
        jobs.start(scope.clone(), next, None, |_| Ok(std::future::pending()))
            .unwrap();
        assert!(
            matches!(jobs.poll(&scope, next), Ok(Poll::Pending(summary)) if summary.is_empty())
        );
        jobs.close().await;
    }

    #[tokio::test]
    async fn discard_can_overtake_ask_and_stale_operations_cannot_retire_the_next_turn() {
        let jobs = Jobs::new();
        let scope = player();
        let early = Uuid::new_v4();
        jobs.discard(&scope, early).unwrap();
        assert!(matches!(
            jobs.start(
                scope.clone(),
                early,
                None,
                |_| -> Result<std::future::Pending<_>, _> {
                    panic!("discarded ask cannot start work")
                }
            ),
            Err(JobError::Unavailable)
        ));

        let cancelled = Uuid::new_v4();
        let next = Uuid::new_v4();
        jobs.start(scope.clone(), cancelled, None, |_| {
            Ok(std::future::pending())
        })
        .unwrap();
        jobs.discard(&scope, cancelled).unwrap();
        jobs.start(scope.clone(), next, None, |summaries| {
            summaries.send("Check the permissions.".into()).unwrap();
            Ok(std::future::pending())
        })
        .unwrap();
        assert!(matches!(
            jobs.delivered(&scope, cancelled),
            Err(JobError::Unavailable)
        ));
        jobs.discard(&scope, cancelled).unwrap();
        // Model the old worker cleaning up after the new one has registered.
        let old_cleanup = Cleanup {
            entries: jobs.entries.clone(),
            scope: scope.clone(),
            id: cancelled,
        };
        drop(old_cleanup);
        assert!(matches!(
            jobs.delivered(&scope, next),
            Err(JobError::Invalid)
        ));
        assert!(
            matches!(jobs.poll(&scope, next), Ok(Poll::Pending(summary)) if summary == "Check the permissions.")
        );
        jobs.close().await;
    }

    #[tokio::test(start_paused = true)]
    async fn polling_at_the_end_of_retention_opens_one_new_receipt_window() {
        use crate::domains::ai::{Conversation, conversation::Sessions, reply, sample};
        use tokio::time::advance;

        let jobs = Jobs::new();
        let scope = player();
        let id = Uuid::new_v4();
        let conversation = Conversation {
            platform: "paper",
            account: scope.instance.clone(),
            room: "private".into(),
            sender: scope.player.to_string(),
            thread: None,
        };
        let sessions = Sessions::default();
        let ready = |id, question, text| {
            let reply = sample(text);
            let receipt = DeliveryReceipt::new(
                sessions.enter(conversation.clone()).unwrap().finish(),
                question,
                reply::paper_history(&reply),
                None,
            );
            jobs.start(scope.clone(), id, None, |_| {
                Ok(std::future::ready(Ok(Ready { reply, receipt })))
            })
            .unwrap();
        };
        ready(id, "question".into(), "First answer.");
        let expires = Uuid::new_v4();
        jobs.tasks.close();
        jobs.tasks.wait().await;
        ready(expires, "not shown".into(), "Second answer.");
        jobs.tasks.wait().await;

        let second = Duration::from_secs(1);
        advance(RESULT_TTL.saturating_sub(2 * second)).await;
        assert!(matches!(jobs.delivered(&scope, id), Err(JobError::Invalid)));
        assert!(matches!(jobs.poll(&scope, id), Ok(Poll::Ready(Ok(_)))));
        assert!(matches!(jobs.poll(&scope, expires), Ok(Poll::Ready(Ok(_)))));
        advance(second).await;
        assert!(matches!(jobs.poll(&scope, expires), Ok(Poll::Ready(Ok(_)))));
        advance(2 * second).await;
        jobs.maintain();
        assert!(matches!(jobs.poll(&scope, id), Err(JobError::Unavailable)));
        jobs.delivered(&scope, id).unwrap();
        assert!(matches!(
            jobs.discard(&scope, id),
            Err(JobError::Unavailable)
        ));
        jobs.delivered(&scope, id).unwrap();
        advance(RECEIPT_TTL.saturating_sub(3 * second)).await;
        jobs.maintain();
        assert!(matches!(
            jobs.delivered(&scope, expires),
            Err(JobError::Unavailable)
        ));
        jobs.close().await;
    }
}
