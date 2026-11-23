mod state;
pub(crate) use state::Publication;

use mik_protocol::wire::{GameQueryJob, GameQueryReply, GameQueryWork};
use std::{collections::VecDeque, time::Duration};
use tokio::{
    sync::{Mutex, Notify, oneshot},
    time::{Instant, timeout, timeout_at},
};
use uuid::Uuid;

pub(crate) use mik_protocol::wire::{
    game_query_job::Operation, game_query_reply::Result as GameResult,
};

const REQUEST_BUDGET: Duration = Duration::from_secs(3);
const POLL_BUDGET: Duration = Duration::from_millis(750);
const MAX_QUEUED: usize = 32;

pub(crate) struct GameBridge {
    game_queries: QueryQueue,
    account_queries: QueryQueue,
    pub(crate) state: state::GameState,
}

#[derive(Default)]
pub(crate) struct QueryQueue {
    pending: Mutex<VecDeque<(Operation, Job)>>,
    available: Notify,
}

pub(crate) struct Job {
    id: String,
    deadline: Instant,
    reply: oneshot::Sender<GameResult>,
    authorized: Box<dyn FnOnce() -> bool + Send>,
    game_instance: Option<String>,
}

impl GameBridge {
    pub(crate) fn new() -> Self {
        Self {
            game_queries: QueryQueue::default(),
            account_queries: QueryQueue::default(),
            state: state::GameState::default(),
        }
    }

    pub(crate) fn queries(&self, accounts: bool) -> &QueryQueue {
        if accounts {
            &self.account_queries
        } else {
            &self.game_queries
        }
    }

    pub(crate) async fn query(&self, operation: Operation) -> Option<GameResult> {
        self.query_guarded(operation, || true).await
    }

    pub(crate) async fn query_for(
        &self,
        game_instance: String,
        operation: Operation,
    ) -> Option<GameResult> {
        self.enqueue(operation, Some(game_instance), || true).await
    }

    pub(crate) async fn query_guarded(
        &self,
        operation: Operation,
        authorized: impl FnOnce() -> bool + Send + 'static,
    ) -> Option<GameResult> {
        self.enqueue(operation, None, authorized).await
    }

    async fn enqueue(
        &self,
        operation: Operation,
        game_instance: Option<String>,
        authorized: impl FnOnce() -> bool + Send + 'static,
    ) -> Option<GameResult> {
        self.queries(matches!(&operation, Operation::Account(_)))
            .enqueue(operation, game_instance, authorized)
            .await
    }
}

impl QueryQueue {
    async fn enqueue(
        &self,
        operation: Operation,
        game_instance: Option<String>,
        authorized: impl FnOnce() -> bool + Send + 'static,
    ) -> Option<GameResult> {
        let (reply, result) = oneshot::channel();
        let deadline = Instant::now() + REQUEST_BUDGET;
        {
            let mut pending = self.pending.lock().await;
            pending.retain(|(_, job)| job.live());
            if pending.len() >= MAX_QUEUED {
                return None;
            }
            pending.push_back((
                operation,
                Job {
                    id: Uuid::new_v4().to_string(),
                    deadline,
                    reply,
                    authorized: Box::new(authorized),
                    game_instance,
                },
            ));
        }
        self.available.notify_waiters();
        timeout_at(deadline, result).await.ok()?.ok()
    }

    pub(crate) async fn exchange(
        &self,
        game_instance: &str,
        reply: Option<GameQueryReply>,
        in_flight: &mut Option<Job>,
        budget: Duration,
        accepts_assistant: bool,
    ) -> GameQueryWork {
        if let (Some(job), Some(reply)) = (in_flight.take(), reply)
            && job.id == reply.id
            && let Some(result) = reply.result
        {
            // The request may have timed out while Paper was handling it.
            drop(job.reply.send(result));
        }
        let job = timeout(budget.min(POLL_BUDGET), async {
            loop {
                let notified = self.available.notified();
                let candidate = {
                    let mut pending = self.pending.lock().await;
                    pending.retain(|(_, job)| job.live());
                    let index = pending.iter().position(|(operation, job)| {
                        (accepts_assistant || !matches!(operation, Operation::AssistantTool(_)))
                            && job
                                .game_instance
                                .as_deref()
                                .is_none_or(|target| target == game_instance)
                    });
                    index.and_then(|index| pending.remove(index))
                };
                let Some((operation, mut job)) = candidate else {
                    notified.await;
                    continue;
                };
                let remaining_ms = u32::try_from(
                    job.deadline
                        .saturating_duration_since(Instant::now())
                        .as_millis(),
                )
                .unwrap_or_default();
                if remaining_ms == 0
                    || !(std::mem::replace(&mut job.authorized, Box::new(|| false)))()
                {
                    continue;
                }
                let work = GameQueryJob {
                    id: job.id.clone(),
                    remaining_ms,
                    operation: Some(operation),
                };
                *in_flight = Some(job);
                return work;
            }
        })
        .await
        .ok();
        GameQueryWork { job }
    }
}

impl Job {
    fn live(&self) -> bool {
        self.deadline > Instant::now() && !self.reply.is_closed()
    }
}

impl Default for GameBridge {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use mik_protocol::wire::{AssistantToolCall, GameQueryEmpty};
    use std::future::ready;

    #[tokio::test]
    async fn targeted_query_skips_other_instances_without_leaving_the_queue() {
        let bridge = GameBridge::new();
        let operation = Operation::AssistantTool(AssistantToolCall {
            player_uuid: "00000000-0000-4000-8000-000000000001".into(),
            turn_id: "turn".into(),
            name: "game_read_weather".into(),
            arguments_json: "{}".into(),
        });
        let query = bridge.query_for("primary".into(), operation.clone());
        tokio::pin!(query);
        tokio::select! {
            biased;
            early = &mut query => panic!("query completed before either server polled: {early:?}"),
            () = ready(()) => {},
        }
        let queue = bridge.queries(false);
        let mut in_flight = None;
        let other = queue
            .exchange("secondary", None, &mut in_flight, Duration::ZERO, true)
            .await;
        assert!(other.job.is_none());
        let old_peer = queue
            .exchange("primary", None, &mut in_flight, Duration::ZERO, false)
            .await;
        assert!(old_peer.job.is_none());
        let work = queue
            .exchange("primary", None, &mut in_flight, Duration::ZERO, true)
            .await;
        let job = work.job.expect("the target instance gets the query");
        assert_eq!(job.operation, Some(operation));
        let result = GameResult::Failed(GameQueryEmpty {});
        queue
            .exchange(
                "primary",
                Some(GameQueryReply {
                    id: job.id,
                    result: Some(result.clone()),
                }),
                &mut in_flight,
                Duration::ZERO,
                true,
            )
            .await;
        assert_eq!(query.await, Some(result));
    }
}
