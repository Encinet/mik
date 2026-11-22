use super::{
    Change, Scope, Snapshot,
    store::{Database, Notes, Update, now},
};
use std::{
    io,
    path::PathBuf,
    sync::{Arc, Mutex, PoisonError, RwLock},
    time::Duration,
};
use tokio::{
    sync::{mpsc, oneshot},
    time::{Instant, MissedTickBehavior, interval_at, sleep_until, timeout},
};
use tokio_util::{sync::CancellationToken, task::AbortOnDropHandle};
use uuid::Uuid;

const QUEUE_LIMIT: usize = 64;
const BATCH_LIMIT: usize = 32;
const BATCH_WINDOW: Duration = Duration::from_secs(1);
const READ_BUDGET: Duration = Duration::from_millis(250);
const MAINTENANCE: Duration = Duration::from_mins(1);

enum Request {
    Read(Scope, oneshot::Sender<Option<Snapshot>>),
    Record { generation: Uuid, update: Update },
}

type ReadModel = RwLock<Option<Notes>>;

pub(super) struct Store {
    view: Arc<ReadModel>,
    queue: mpsc::Sender<Request>,
    worker: Mutex<Option<AbortOnDropHandle<()>>>,
    stop: CancellationToken,
}

impl Store {
    pub(in crate::domains::ai) async fn open(path: PathBuf) -> Self {
        let view = Arc::new(ReadModel::default());
        let database = match open_database(path.clone(), &view).await {
            Ok(database) => Some(database),
            Err(error) => {
                report_unavailable(&error);
                None
            }
        };
        let (queue, pending) = mpsc::channel(QUEUE_LIMIT);
        let stop = CancellationToken::new();
        let worker = tokio::spawn(run(path, pending, database, view.clone(), stop.clone()));
        Self {
            view,
            queue,
            worker: Mutex::new(Some(AbortOnDropHandle::new(worker))),
            stop,
        }
    }

    pub(super) fn recalled(&self, instance: &str, player: Uuid) -> Option<Vec<String>> {
        let view = self.view.try_read().ok()?;
        let now = now();
        Some(
            view.as_ref()?
                .get(&Scope {
                    instance: instance.into(),
                    player,
                })
                .into_iter()
                .flatten()
                .filter(|note| note.current(now))
                .map(|note| note.text.clone())
                .collect(),
        )
    }

    pub(in crate::domains::ai) async fn read(
        &self,
        instance: &str,
        player: Uuid,
    ) -> Option<Snapshot> {
        let (reply, result) = oneshot::channel();
        let scope = Scope {
            instance: instance.into(),
            player,
        };
        self.queue.try_send(Request::Read(scope, reply)).ok()?;
        timeout(READ_BUDGET, result).await.ok()?.ok()?
    }

    pub(in crate::domains::ai) fn record(
        &self,
        instance: String,
        player: Uuid,
        generation: Uuid,
        changes: Vec<Change>,
    ) {
        if !changes.is_empty() {
            let update = Update {
                scope: Scope { instance, player },
                changes,
            };
            if self
                .queue
                .try_send(Request::Record { generation, update })
                .is_err()
            {
                eprintln!("mikd AI player memory update skipped: worker unavailable or busy");
            }
        }
    }

    pub(in crate::domains::ai) async fn close(&self) {
        self.stop.cancel();
        let worker = self
            .worker
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
            .take();
        if let Some(worker) = worker
            && worker.await.is_err()
        {
            eprintln!("mikd AI player memory worker could not drain");
        }
    }
}

async fn run(
    path: PathBuf,
    mut queue: mpsc::Receiver<Request>,
    mut database: Option<Database>,
    view: Arc<ReadModel>,
    stop: CancellationToken,
) {
    let mut maintenance = interval_at(Instant::now() + MAINTENANCE, MAINTENANCE);
    maintenance.set_missed_tick_behavior(MissedTickBehavior::Delay);
    let mut pending = Vec::new();
    let mut flush_at = Instant::now();
    loop {
        tokio::select! {
            biased;
            () = stop.cancelled() => break,
            () = sleep_until(flush_at), if !pending.is_empty() => flush(&mut database, &mut pending, &view).await,
            _ = maintenance.tick() => {
                if database.is_some() {
                    flush(&mut database, &mut pending, &view).await;
                } else if let Ok(opened) = open_database(path.clone(), &view).await {
                    database = Some(opened);
                    eprintln!("mikd AI player memory resumed");
                }
            }
            request = queue.recv() => match request {
                Some(Request::Record { generation, update }) => {
                    if pending.is_empty() {
                        flush_at = Instant::now() + BATCH_WINDOW;
                    }
                    stage(database.as_ref(), &mut pending, generation, update);
                    if pending.len() >= BATCH_LIMIT {
                        flush(&mut database, &mut pending, &view).await;
                    }
                }
                Some(Request::Read(scope, reply)) if !reply.is_closed() => {
                    if pending.iter().any(|update| update.scope == scope) {
                        flush(&mut database, &mut pending, &view).await;
                    }
                    let snapshot = database.as_ref().and_then(|active| read(&view, &scope, active.generation));
                    let _sent = reply.send(snapshot);
                }
                Some(Request::Read(..)) => {},
                None => break,
            },
        }
    }
    queue.close();
    while let Some(request) = queue.recv().await {
        if let Request::Record { generation, update } = request {
            stage(database.as_ref(), &mut pending, generation, update);
        }
    }
    if !pending.is_empty() {
        flush(&mut database, &mut pending, &view).await;
    }
    if let Some(database) = database {
        database.close().await;
    }
    *view.write().unwrap_or_else(PoisonError::into_inner) = None;
}

fn stage(database: Option<&Database>, pending: &mut Vec<Update>, generation: Uuid, update: Update) {
    if database.is_some_and(|database| database.generation == generation) {
        pending.push(update);
    }
}

async fn open_database(path: PathBuf, view: &ReadModel) -> io::Result<Database> {
    let database = Database::open(path).await?;
    match database.notes().await {
        Ok(notes) => {
            *view.write().unwrap_or_else(PoisonError::into_inner) = Some(notes);
            Ok(database)
        }
        Err(error) => {
            database.close().await;
            Err(error)
        }
    }
}

async fn flush(database: &mut Option<Database>, pending: &mut Vec<Update>, view: &ReadModel) {
    let updates = std::mem::take(pending);
    let Some(active) = database.as_mut() else {
        return;
    };
    match active.save(updates).await {
        Ok(changed) => {
            if let Some(notes) = view
                .write()
                .unwrap_or_else(PoisonError::into_inner)
                .as_mut()
            {
                for (scope, current) in changed {
                    if current.is_empty() {
                        drop(notes.remove(&scope));
                    } else {
                        drop(notes.insert(scope, current));
                    }
                }
                let now = now();
                notes.retain(|_, entries| {
                    entries.retain(|note| note.current(now));
                    !entries.is_empty()
                });
            }
        }
        Err(error) => {
            *view.write().unwrap_or_else(PoisonError::into_inner) = None;
            report_unavailable(&error);
            if let Some(active) = database.take() {
                active.close().await;
            }
        }
    }
}

fn read(view: &ReadModel, scope: &Scope, generation: Uuid) -> Option<Snapshot> {
    let view = view.read().ok()?;
    let now = now();
    let notes = view
        .as_ref()?
        .get(scope)
        .into_iter()
        .flatten()
        .filter(|note| note.current(now))
        .cloned()
        .collect();
    Some(Snapshot { generation, notes })
}

fn report_unavailable(error: &io::Error) {
    eprintln!(
        "mikd AI player memory temporarily unavailable: {:?}",
        error.kind()
    );
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::{fs::Permissions, os::unix::fs::PermissionsExt as _};

    #[tokio::test]
    async fn recall_reads_only_committed_unexpired_notes_without_waiting() {
        let directory = tempfile::Builder::new()
            .permissions(Permissions::from_mode(0o700))
            .tempdir()
            .unwrap();
        let player = Uuid::from_u128(1);
        let memory = Store::open(directory.path().join("memory.sqlite")).await;
        let snapshot = memory.read("paper", player).await.unwrap();
        let text = "Keeps natural coastlines around builds.";
        memory.record(
            "paper".into(),
            player,
            snapshot.generation,
            vec![Change::Add {
                text: text.into(),
                ttl_days: 1,
            }],
        );
        assert!(memory.recalled("paper", player).unwrap().is_empty());
        let committed = memory.read("paper", player).await.unwrap();
        assert_eq!(committed.notes[0].text, text);
        assert_eq!(memory.recalled("paper", player).unwrap(), [text]);
        assert!(memory.recalled("other-paper", player).unwrap().is_empty());

        std::thread::scope(|threads| {
            let mut view = memory.view.write().unwrap();
            let (send, result) = std::sync::mpsc::channel();
            let memory = &memory;
            threads.spawn(move || send.send(memory.recalled("paper", player)).unwrap());
            let recalled = result.recv_timeout(Duration::from_secs(1));
            let scope = Scope {
                instance: "paper".into(),
                player,
            };
            view.as_mut().unwrap().get_mut(&scope).unwrap()[0].expires_at = Some(now());
            drop(view);
            assert!(recalled.unwrap().is_none());
        });

        assert!(memory.recalled("paper", player).unwrap().is_empty());
        memory.close().await;
        assert!(memory.recalled("paper", player).is_none());
    }

    #[tokio::test]
    async fn retries_unavailable_storage_and_rejects_edits_from_an_old_generation() {
        let directory = tempfile::Builder::new()
            .permissions(Permissions::from_mode(0o700))
            .tempdir()
            .unwrap();
        let path = directory.path().join("memory.sqlite");
        let previous = Database::open(path.clone()).await.unwrap();
        let previous_generation = previous.generation;
        let memory = Store::open(path).await;
        let player = Uuid::from_u128(1);
        assert!(memory.recalled("paper", player).is_none());
        assert!(memory.read("paper", player).await.is_none());
        previous.close().await;

        tokio::time::pause();
        tokio::time::advance(MAINTENANCE).await;
        tokio::time::resume();
        let reopened = timeout(Duration::from_secs(5), async {
            loop {
                if let Some(snapshot) = memory.read("paper", player).await {
                    break snapshot;
                }
                tokio::time::sleep(Duration::from_millis(10)).await;
            }
        })
        .await
        .unwrap();
        let valid = "Prefers open walkways between builds.";
        for (generation, text) in [
            (previous_generation, "Stale update."),
            (reopened.generation, valid),
        ] {
            memory.record(
                "paper".into(),
                player,
                generation,
                vec![Change::Add {
                    text: text.into(),
                    ttl_days: 0,
                }],
            );
        }
        let snapshot = memory.read("paper", player).await.unwrap();
        assert_eq!(snapshot.notes.len(), 1);
        assert_eq!(snapshot.notes[0].text, valid);
        assert_eq!(memory.recalled("paper", player).unwrap(), [valid]);
        memory.close().await;
    }
}
