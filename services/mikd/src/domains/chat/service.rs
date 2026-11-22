use super::{
    HistoryError, HistoryStore,
    history::{Append, AppendResult, SendReceipt},
    links,
    players::{SendAllowance, resolve_mentions},
    presence::GameView,
};
use crate::domains::community::{BindingIndex, CommunityStore};
use mik_protocol::wire::{
    ChatGamePeer, ChatPlayer, HistoryChannel, HistoryEntry, HistorySaved, chat_record::Kind,
    chat_user_record::Source,
};
use std::{
    io,
    sync::{Arc, Mutex, PoisonError},
    time::Duration,
};
use tokio::{
    sync::{Semaphore, broadcast, mpsc, oneshot, watch},
    time::Instant,
};
use tokio_util::{sync::CancellationToken, task::AbortOnDropHandle};

const QUEUE_ITEMS: usize = 32;
const COMMIT_ITEMS: usize = 16;
const LIVE_ITEMS: usize = 32;
const QUEUE_BYTES: u32 = 1024 * 1024;
const GAME_VIEW_MAX_AGE: Duration = Duration::from_secs(3);

pub(crate) struct Committed {
    pub(super) entry: HistoryEntry,
    pub(super) order_key: String,
    pub(super) receipt: Option<SendReceipt>,
}

struct Submission {
    append: Append,
    reply: oneshot::Sender<Result<HistorySaved, HistoryError>>,
    _permit: tokio::sync::OwnedSemaphorePermit,
}

pub(crate) struct ChatService {
    worker: Mutex<Option<AbortOnDropHandle<()>>>,
    history: Arc<HistoryStore>,
    queue: mpsc::Sender<Submission>,
    capacity: Arc<Semaphore>,
    live: broadcast::Sender<Arc<Committed>>,
    game: watch::Sender<Option<(Instant, Arc<GameView>)>>,
    community: Option<Arc<CommunityStore>>,
    pub(super) allowance: Mutex<SendAllowance>,
    shutdown: CancellationToken,
}

impl ChatService {
    pub(crate) fn new(history: HistoryStore, community: Option<Arc<CommunityStore>>) -> Self {
        let history = Arc::new(history);
        let (live, _) = broadcast::channel(LIVE_ITEMS);
        let (queue, mut pending) = mpsc::channel::<Submission>(QUEUE_ITEMS);
        let shutdown = CancellationToken::new();
        let (game, _) = watch::channel(None);
        let worker = tokio::spawn({
            let history = history.clone();
            let live = live.clone();
            let shutdown = shutdown.clone();
            async move {
                let mut batch = Vec::with_capacity(COMMIT_ITEMS);
                loop {
                    let count = tokio::select! {
                        biased;
                        () = shutdown.cancelled(), if !pending.is_closed() => { pending.close(); continue; }
                        count = pending.recv_many(&mut batch, COMMIT_ITEMS) => count,
                    };
                    if count == 0 {
                        break;
                    }
                    commit_batch(&history, &live, &mut batch).await;
                }
            }
        });
        Self {
            history,
            queue,
            capacity: Arc::new(Semaphore::new(QUEUE_BYTES as usize)),
            live,
            game,
            community,
            allowance: Mutex::default(),
            shutdown,
            worker: Mutex::new(Some(AbortOnDropHandle::new(worker))),
        }
    }

    pub(super) fn history(&self) -> &HistoryStore {
        &self.history
    }

    pub(crate) async fn prune_history(&self) -> Result<(), HistoryError> {
        self.history.prune().await
    }

    pub(crate) fn subscribe(&self) -> broadcast::Receiver<Arc<Committed>> {
        self.live.subscribe()
    }

    pub(crate) fn project_game(&self, players: Option<Vec<ChatGamePeer>>) {
        if let Some(players) = players {
            let view = Arc::new(GameView::new(players));
            self.game.send_replace(Some((Instant::now(), view)));
        } else {
            self.game.send_modify(|projection| {
                if let Some((observed, _)) = projection {
                    *observed = Instant::now();
                }
            });
        }
    }

    pub(crate) fn clear_game(&self) {
        self.game.send_replace(None);
    }

    pub(super) fn game(&self) -> Option<Arc<GameView>> {
        self.game
            .borrow()
            .as_ref()
            .filter(|(at, _)| at.elapsed() < GAME_VIEW_MAX_AGE)
            .map(|(_, game)| Arc::clone(game))
    }

    pub(crate) async fn wait_for_game(&self, budget: Duration) -> bool {
        let mut game = self.game.subscribe();
        matches!(
            tokio::time::timeout(
                budget,
                game.wait_for(|view| {
                    view.as_ref()
                        .is_some_and(|(at, _)| at.elapsed() < GAME_VIEW_MAX_AGE)
                }),
            )
            .await,
            Ok(Ok(_))
        )
    }

    pub(crate) async fn publish(&self, entry: HistoryEntry) -> Result<HistorySaved, HistoryError> {
        self.publish_user(entry, None, |_| None).await
    }

    pub(super) async fn publish_user(
        &self,
        mut entry: HistoryEntry,
        receipt: Option<SendReceipt>,
        web_mention: impl FnMut(&str) -> Option<ChatPlayer>,
    ) -> Result<HistorySaved, HistoryError> {
        let Some(Kind::User(user)) = entry
            .record
            .as_mut()
            .and_then(|record| record.kind.as_mut())
        else {
            return Err(HistoryError::Invalid("user message required"));
        };
        let game = self.game();
        let source = user.source();
        if game.is_none()
            && source != Source::Game
            && user
                .author
                .as_ref()
                .is_some_and(|author| author.player.is_some())
        {
            return Err(HistoryError::Busy);
        }
        if let Some(body) = user.body.as_mut() {
            links::normalize(body);
            if source == Source::Web {
                resolve_mentions(body, web_mention);
            }
        }
        if let Some(game) = game.as_deref() {
            game.restrict(&mut entry);
            if let Some(store) = &self.community {
                enrich_bound_mentions(&mut entry, store.bindings(), game);
            }
        }
        self.enqueue(Append { entry, receipt })?
            .await
            .map_err(|_| HistoryError::Busy)?
    }

    pub(crate) async fn submit_notice(
        &self,
        entry: HistoryEntry,
    ) -> Result<HistorySaved, HistoryError> {
        self.enqueue_notice(entry)?
            .await
            .map_err(|_| HistoryError::Busy)?
    }

    pub(super) fn enqueue_notice(
        &self,
        entry: HistoryEntry,
    ) -> Result<oneshot::Receiver<Result<HistorySaved, HistoryError>>, HistoryError> {
        if !matches!(
            entry
                .record
                .as_ref()
                .and_then(|record| record.kind.as_ref()),
            Some(Kind::Notice(_))
        ) {
            return Err(HistoryError::Invalid("notice required"));
        }
        self.enqueue(Append {
            entry,
            receipt: None,
        })
    }

    fn enqueue(
        &self,
        append: Append,
    ) -> Result<oneshot::Receiver<Result<HistorySaved, HistoryError>>, HistoryError> {
        let size = u32::try_from(append.size()).map_err(|_| HistoryError::Busy)?;
        let permit = self
            .capacity
            .clone()
            .try_acquire_many_owned(size)
            .map_err(|_| HistoryError::Busy)?;
        let (reply, result) = oneshot::channel();
        self.queue
            .try_send(Submission {
                append,
                reply,
                _permit: permit,
            })
            .map_err(|_| HistoryError::Busy)?;
        Ok(result)
    }

    pub(crate) async fn close(&self) -> io::Result<()> {
        self.shutdown.cancel();
        // This lock only owns the handle; recover it so cleanup still runs.
        let worker = self
            .worker
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
            .take();
        let drained = match worker {
            Some(worker) => worker
                .await
                .map_err(|_| io::Error::other("chat history worker exited before draining")),
            None => Ok(()),
        };
        let closed = self.history.close().await.map_err(io::Error::other);
        drained.and(closed)
    }
}

async fn commit_batch(
    history: &HistoryStore,
    live: &broadcast::Sender<Arc<Committed>>,
    batch: &mut Vec<Submission>,
) {
    let (appends, replies): (Vec<_>, Vec<_>) = batch
        .drain(..)
        .map(
            |Submission {
                 append,
                 reply,
                 _permit: permit,
             }| (append, (reply, permit)),
        )
        .unzip();
    match history.append_many(appends).await {
        Ok(results) => {
            for ((reply, _permit), result) in replies.into_iter().zip(results) {
                respond(reply, publish_committed(live, result));
            }
        }
        Err(error) => {
            for (reply, _permit) in replies {
                respond(reply, Err(error.clone()));
            }
        }
    }
}

fn publish_committed(
    live: &broadcast::Sender<Arc<Committed>>,
    result: AppendResult,
) -> Result<HistorySaved, HistoryError> {
    let (saved, append) = result?;
    if saved.inserted {
        drop(live.send(Arc::new(Committed {
            entry: append.entry,
            order_key: saved.order_key.clone(),
            receipt: append.receipt,
        })));
    }
    Ok(saved)
}

fn respond(
    reply: oneshot::Sender<Result<HistorySaved, HistoryError>>,
    result: Result<HistorySaved, HistoryError>,
) {
    if let Err(Err(error)) = reply.send(result) {
        eprintln!("mikd chat append failed: {error}");
    }
}

fn enrich_bound_mentions(entry: &mut HistoryEntry, bindings: &BindingIndex, game: &GameView) {
    if entry.channel() != HistoryChannel::HistoryPublic || entry.restricted {
        return;
    }
    let Some(Kind::User(user)) = entry
        .record
        .as_mut()
        .and_then(|record| record.kind.as_mut())
    else {
        return;
    };
    if user.source() != Source::Game {
        return;
    }
    let Some(author) = user
        .author
        .as_ref()
        .and_then(|author| author.player.as_ref())
    else {
        return;
    };
    let Some(body) = &mut user.body else {
        return;
    };
    resolve_mentions(body, |name| {
        let binding = bindings.named(name)?;
        let id = binding.account.to_string();
        if id == author.id || game.has_player(&id) {
            return None;
        }
        Some(ChatPlayer {
            id,
            name: binding.player_name.clone(),
        })
    });
}

#[cfg(test)]
mod tests {
    use super::*;
    use mik_protocol::wire::{
        ChatBody, ChatNoticeRecord, ChatPart, ChatRecord, chat_notice_record, chat_part,
    };
    use std::{fs::Permissions, os::unix::fs::PermissionsExt};
    use tokio_rusqlite::rusqlite::Connection;

    async fn chat_fixture() -> (tempfile::TempDir, ChatService) {
        let directory = tempfile::Builder::new()
            .permissions(Permissions::from_mode(0o700))
            .tempdir()
            .unwrap();
        let history = HistoryStore::open(&directory.path().join("chat.db"))
            .await
            .unwrap();
        (directory, ChatService::new(history, None))
    }

    fn notice(id: &str) -> HistoryEntry {
        HistoryEntry {
            message_id: id.into(),
            channel: HistoryChannel::HistoryPublic.into(),
            record: Some(ChatRecord {
                kind: Some(Kind::Notice(ChatNoticeRecord {
                    content: Some(chat_notice_record::Content::Body(ChatBody {
                        parts: vec![ChatPart {
                            content: Some(chat_part::Content::Text(id.into())),
                            ..Default::default()
                        }],
                    })),
                    ..Default::default()
                })),
                ..Default::default()
            }),
            ..Default::default()
        }
    }

    #[tokio::test(start_paused = true)]
    async fn social_chat_waits_for_fresh_paper_visibility() {
        let (_directory, chat) = chat_fixture().await;
        let budget = Duration::from_millis(750);
        let (ready, ()) = tokio::join!(chat.wait_for_game(budget), async {
            tokio::time::sleep(Duration::from_millis(100)).await;
            chat.project_game(Some(Vec::new()));
        });
        assert!(ready);

        tokio::time::advance(GAME_VIEW_MAX_AGE).await;
        assert!(!chat.wait_for_game(budget).await);
        chat.close().await.unwrap();
    }

    #[tokio::test]
    async fn shutdown_drains_already_queued_messages_in_order() {
        let (_directory, chat) = chat_fixture().await;
        let mut live = chat.subscribe();
        let first = chat.enqueue_notice(notice("first")).unwrap();
        let second = chat.enqueue_notice(notice("second")).unwrap();

        chat.close().await.unwrap();

        let first = first.await.unwrap().unwrap();
        let second = second.await.unwrap().unwrap();
        assert!(first.inserted && second.inserted && first.order_key < second.order_key);
        assert_eq!(live.try_recv().unwrap().entry.message_id, first.message_id);
        assert_eq!(live.try_recv().unwrap().entry.message_id, second.message_id);
    }

    #[tokio::test]
    async fn commit_failure_is_reported_to_every_sender_without_broadcast() {
        let (directory, chat) = chat_fixture().await;
        let database = Connection::open(directory.path().join("chat.db")).unwrap();
        database
            .execute_batch(
                "CREATE TABLE fixture_parent(id INTEGER PRIMARY KEY);
                 CREATE TABLE fixture_child(id INTEGER REFERENCES fixture_parent(id)
                    DEFERRABLE INITIALLY DEFERRED);
                 CREATE TRIGGER fail_commit AFTER INSERT ON chat_messages
                 WHEN NEW.message_id = 'invalid' BEGIN INSERT INTO fixture_child VALUES(1); END;",
            )
            .unwrap();
        let mut live = chat.subscribe();
        let first = chat.enqueue_notice(notice("valid")).unwrap();
        let second = chat.enqueue_notice(notice("invalid")).unwrap();

        chat.close().await.unwrap();

        for receipt in [first, second] {
            assert!(matches!(
                receipt.await.unwrap(),
                Err(HistoryError::Database(_))
            ));
        }
        assert!(matches!(
            live.try_recv(),
            Err(broadcast::error::TryRecvError::Empty)
        ));
        assert_eq!(
            database
                .query_row("SELECT count(*) FROM chat_messages", [], |row| row
                    .get::<_, i64>(0))
                .unwrap(),
            0
        );
    }

    #[tokio::test]
    async fn undrained_worker_is_reported_without_skipping_history_close() {
        let (_directory, chat) = chat_fixture().await;
        chat.worker.lock().unwrap().as_ref().unwrap().abort();

        assert!(chat.close().await.is_err());
        assert!(matches!(
            chat.prune_history().await,
            Err(HistoryError::Busy)
        ));
    }
}
