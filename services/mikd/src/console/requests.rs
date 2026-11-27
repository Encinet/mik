use std::{
    io,
    sync::{Mutex, MutexGuard, PoisonError},
    time::Duration,
};
use tokio::{
    sync::{Notify, oneshot},
    time::{Instant, timeout},
};
use uuid::Uuid;

const WORK_BUDGET: Duration = Duration::from_secs(3);
pub(crate) const RESULT_BUDGET: Duration = Duration::from_secs(6);
const POLL_BUDGET: Duration = Duration::from_millis(750);

pub(super) struct Requests<W, R> {
    state: Mutex<State<W, R>>,
    ready: Notify,
}

struct State<W, R> {
    closed: bool,
    job: Option<Job<W, R>>,
}

struct Job<W, R> {
    id: String,
    // Taking the work is the point after which delivery may have happened.
    work: Option<W>,
    expires: Instant,
    result: oneshot::Sender<Result<R, Failure>>,
}

pub(super) struct Delivery<W> {
    pub id: String,
    pub work: W,
    pub remaining_ms: u32,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(super) enum Failure {
    Offline,
    Busy,
    Expired,
    Unconfirmed,
}

impl<W, R> Default for Requests<W, R> {
    fn default() -> Self {
        Self {
            state: Mutex::new(State {
                closed: false,
                job: None,
            }),
            ready: Notify::new(),
        }
    }
}

impl<W, R> Requests<W, R> {
    fn state(&self) -> MutexGuard<'_, State<W, R>> {
        self.state.lock().unwrap_or_else(PoisonError::into_inner)
    }

    pub(super) async fn submit(&self, work: W) -> Result<R, Failure> {
        let id = Uuid::new_v4().to_string();
        let (send, mut receive) = oneshot::channel();
        {
            let mut state = self.state();
            if state.closed {
                return Err(Failure::Offline);
            }
            if state.job.as_ref().is_some_and(|job| {
                job.work.is_some() && (job.expires <= Instant::now() || job.result.is_closed())
            }) {
                state.finish(Err(Failure::Expired));
            }
            if state.job.is_some() {
                return Err(Failure::Busy);
            }
            state.job = Some(Job {
                id: id.clone(),
                work: Some(work),
                expires: Instant::now() + WORK_BUDGET,
                result: send,
            });
        }
        self.ready.notify_one();
        if let Ok(Ok(result)) = timeout(RESULT_BUDGET, &mut receive).await {
            return result;
        }
        let mut state = self.state();
        if let Ok(result) = receive.try_recv() {
            return result;
        }
        if state
            .job
            .as_ref()
            .is_some_and(|job| job.id == id && job.work.is_some())
        {
            state.finish(Err(Failure::Expired));
            Err(Failure::Expired)
        } else {
            // Keep delivered work reserved until its receipt or connection closure.
            Err(Failure::Unconfirmed)
        }
    }

    pub(super) async fn poll(
        &self,
        receipt: Option<(String, R)>,
        budget: Duration,
    ) -> io::Result<Option<Delivery<W>>> {
        self.state().acknowledge(receipt)?;
        Ok(timeout(budget.min(POLL_BUDGET), async {
            loop {
                let delivery = self.state().deliver();
                if let Some(delivery) = delivery {
                    return delivery;
                }
                self.ready.notified().await;
            }
        })
        .await
        .ok())
    }

    pub(super) fn close(&self) {
        let mut state = self.state();
        state.closed = true;
        let outcome = if state.delivered().is_some() {
            Failure::Unconfirmed
        } else {
            Failure::Offline
        };
        state.finish(Err(outcome));
    }
}

impl<W, R> State<W, R> {
    fn finish(&mut self, outcome: Result<R, Failure>) {
        if let Some(job) = self.job.take() {
            drop(job.result.send(outcome));
        }
    }

    fn delivered(&self) -> Option<&Job<W, R>> {
        self.job.as_ref().filter(|job| job.work.is_none())
    }

    fn acknowledge(&mut self, receipt: Option<(String, R)>) -> io::Result<()> {
        match (self.delivered(), receipt) {
            (None, None) => Ok(()),
            (Some(job), Some((id, result))) if job.id == id => {
                self.finish(Ok(result));
                Ok(())
            }
            _ => Err(invalid_receipt()),
        }
    }

    fn deliver(&mut self) -> Option<Delivery<W>> {
        let job = self.job.as_mut()?;
        let remaining_ms = u32::try_from(
            job.expires
                .saturating_duration_since(Instant::now())
                .as_millis(),
        )
        .unwrap_or_default();
        if remaining_ms == 0 || job.result.is_closed() {
            self.finish(Err(Failure::Expired));
            return None;
        }
        Some(Delivery {
            id: job.id.clone(),
            work: job.work.take()?,
            remaining_ms,
        })
    }
}

pub(super) fn invalid_receipt() -> io::Error {
    io::Error::new(io::ErrorKind::InvalidData, "Mismatched console receipt")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test(start_paused = true)]
    async fn work_expired_while_queued_is_never_delivered() {
        let requests = Requests::<_, ()>::default();
        assert_eq!(requests.submit("expired").await, Err(Failure::Expired));
        assert!(requests.poll(None, Duration::ZERO).await.unwrap().is_none());
    }
}
