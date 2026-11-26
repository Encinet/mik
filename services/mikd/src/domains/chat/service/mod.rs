mod native;
mod paper;
#[cfg(test)]
mod tests;
mod web_post;

pub(super) use web_post::{SendError, WebPost};

use super::{
    BodyInput, Committed, HistoryError, HistoryStore, body,
    history::{Admission, Append, AppendResult},
    mentions,
    model::{Destination, current_player, destination, validate_event, visibility_allows},
    presence::{GamePresence, GameView},
};
use crate::domains::community::{BindingIndex, CommunityStore};
use mik_protocol::wire::{
    ChatEvent, ChatGamePeer, ChatIdentity, ChatUserRecord, ChatWebPeer, ChatWebRoster,
    chat_record::Kind,
};
use std::{
    io,
    sync::{Arc, LazyLock, Mutex, OnceLock, PoisonError},
};
use tokio::sync::{Semaphore, broadcast, mpsc, oneshot};
use tokio_util::{sync::CancellationToken, task::AbortOnDropHandle};
use uuid::Uuid;
use web_post::SendAllowance;

const QUEUE_ITEMS: usize = 32;
const COMMIT_ITEMS: usize = 16;
pub(super) const LIVE_ITEMS: usize = 32;
const QUEUE_BYTES: u32 = 1024 * 1024;

struct WebPort {
    roster: Box<dyn Fn() -> Arc<ChatWebRoster> + Send + Sync>,
    publish: Box<dyn Fn(Arc<Committed>) + Send + Sync>,
}

struct Publications {
    subscribers: broadcast::Sender<Arc<Committed>>,
    web: OnceLock<WebPort>,
}

pub(crate) fn empty_web_roster() -> Arc<ChatWebRoster> {
    // Revision 1 is reserved for the default projection, including when the Web listener is disabled.
    static EMPTY: LazyLock<Arc<ChatWebRoster>> = LazyLock::new(|| {
        Arc::new(ChatWebRoster {
            revision: 1,
            players: Vec::new(),
        })
    });
    EMPTY.clone()
}

struct Submission {
    append: Append,
    reply: oneshot::Sender<Result<String, HistoryError>>,
    _permit: tokio::sync::OwnedSemaphorePermit,
}

pub(crate) struct ChatService {
    worker: Mutex<Option<AbortOnDropHandle<()>>>,
    history: Arc<HistoryStore>,
    queue: mpsc::Sender<Submission>,
    capacity: Arc<Semaphore>,
    live: Arc<Publications>,
    game: GamePresence,
    community: Option<Arc<CommunityStore>>,
    web_allowance: Mutex<SendAllowance>,
    shutdown: CancellationToken,
}

impl ChatService {
    pub(crate) fn new(history: HistoryStore, community: Option<Arc<CommunityStore>>) -> Self {
        let history = Arc::new(history);
        let live = Arc::new(Publications {
            subscribers: broadcast::channel(LIVE_ITEMS).0,
            web: OnceLock::new(),
        });
        let (queue, mut pending) = mpsc::channel::<Submission>(QUEUE_ITEMS);
        let shutdown = CancellationToken::new();
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
            game: GamePresence::new(),
            community,
            web_allowance: Mutex::default(),
            shutdown,
            worker: Mutex::new(Some(AbortOnDropHandle::new(worker))),
        }
    }

    pub(super) fn attach_web(
        &self,
        roster: impl Fn() -> Arc<ChatWebRoster> + Send + Sync + 'static,
        publish: impl Fn(Arc<Committed>) + Send + Sync + 'static,
    ) -> io::Result<()> {
        self.live
            .web
            .set(WebPort {
                roster: Box::new(roster),
                publish: Box::new(publish),
            })
            .map_err(|_| io::Error::other("Web chat already attached"))
    }

    pub(crate) fn web_roster(&self) -> Arc<ChatWebRoster> {
        self.live
            .web
            .get()
            .map_or_else(empty_web_roster, |web| (web.roster)())
    }

    pub(super) fn history(&self) -> &HistoryStore {
        &self.history
    }

    pub(super) fn current_mention_player(&self, person: &ChatIdentity) -> Option<Uuid> {
        current_player(person, self.bindings())
    }

    fn bindings(&self) -> Option<&BindingIndex> {
        self.community.as_ref().map(|store| store.bindings())
    }

    pub(crate) async fn prune_history(&self) -> Result<(), HistoryError> {
        self.history.prune().await
    }

    pub(crate) fn subscribe(&self) -> broadcast::Receiver<Arc<Committed>> {
        self.live.subscribers.subscribe()
    }

    pub(crate) fn project_game(&self, players: Option<Vec<ChatGamePeer>>) {
        self.game.project(players);
    }

    pub(crate) fn clear_game(&self) {
        self.game.clear();
    }

    pub(super) async fn notify_game_changes(&self, changed: &tokio::sync::watch::Sender<()>) {
        self.game.notify_changes(changed).await;
    }

    pub(super) fn game(&self) -> Option<Arc<GameView>> {
        self.game.view()
    }

    pub(super) fn hidden_from(&self, viewer: &str) -> Option<Vec<String>> {
        Some(self.game()?.hidden_from(viewer))
    }

    /// Apply the audience ceiling before resolving names. All ingress paths use the same order.
    fn prepare_post(
        &self,
        entry: &mut ChatEvent,
        input: impl IntoIterator<Item = BodyInput>,
        game: Option<&GameView>,
        web: &[ChatWebPeer],
    ) -> Result<(), HistoryError> {
        if let Some(game) = game {
            game.restrict(entry);
        }
        let lookup = mentions::Lookup::new(entry, game, web, self.bindings());
        let body = body::assemble(input, lookup)?;
        user_mut(entry)?.body = Some(body);
        Ok(())
    }

    pub(super) fn enqueue_presence(&self, mut entry: ChatEvent) -> Result<(), HistoryError> {
        let game = self.game().ok_or(HistoryError::Busy)?;
        game.restrict(&mut entry);
        // A committed presence event doesn't require a reply to the caller.
        drop(self.enqueue(Append {
            entry,
            admission: Admission::Presence,
        })?);
        Ok(())
    }

    async fn commit(&self, append: Append) -> Result<String, HistoryError> {
        self.enqueue(append)?
            .await
            .map_err(|_| HistoryError::Busy)?
    }

    fn enqueue(
        &self,
        mut append: Append,
    ) -> Result<oneshot::Receiver<Result<String, HistoryError>>, HistoryError> {
        validate_event(&mut append.entry)?;
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

async fn commit_batch(history: &HistoryStore, live: &Publications, batch: &mut Vec<Submission>) {
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

fn publish_committed(live: &Publications, result: AppendResult) -> Result<String, HistoryError> {
    let (saved, append) = result?;
    if let Some(delivery) = saved.delivery {
        let (source, receipt) = match append.admission {
            Admission::Native(native) => (Some(native.source), None),
            Admission::Web(receipt, _) => (None, Some(receipt)),
            Admission::Paper(_) | Admission::Presence => (None, None),
        };
        let event = Arc::new(Committed {
            cursor: delivery.cursor,
            source,
            entry: append.entry,
            order_key: delivery.order_key,
            expires_at: delivery.expires_at,
            receipt,
        });
        if let Some(web) = live.web.get() {
            (web.publish)(event.clone());
        }
        drop(live.subscribers.send(event));
    }
    Ok(saved.message_id)
}

fn respond(
    reply: oneshot::Sender<Result<String, HistoryError>>,
    result: Result<String, HistoryError>,
) {
    if let Err(Err(error)) = reply.send(result) {
        eprintln!("mikd chat append failed: {error}");
    }
}

fn require_readable_recipient(entry: &ChatEvent) -> Result<(), HistoryError> {
    let record = entry
        .record
        .as_ref()
        .ok_or(HistoryError::Invalid("record required"))?;
    if let Destination::Private { recipient, .. } = destination(record)?
        && !visibility_allows(entry.visibility.as_ref(), &recipient.id)
    {
        return Err(HistoryError::RecipientUnavailable);
    }
    Ok(())
}

fn user_mut(event: &mut ChatEvent) -> Result<&mut ChatUserRecord, HistoryError> {
    match event
        .record
        .as_mut()
        .and_then(|record| record.kind.as_mut())
    {
        Some(Kind::User(user)) => Ok(user),
        _ => Err(HistoryError::Invalid("user message required")),
    }
}
