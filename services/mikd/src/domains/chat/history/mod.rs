mod cursor;
mod query;

pub(super) use query::{Audience, LiveAnchor, PostedReceipt, SyncPage, SyncRequest};

use super::{
    model::{Destination, destination, uuid},
    routing::NativeEvent,
};
use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
use cursor::Cursors;
#[cfg(test)]
use mik_protocol::wire;
use mik_protocol::wire::ChatEvent;
use prost::Message;
use std::{
    fs::{self, File, OpenOptions},
    os::unix::fs::{OpenOptionsExt, PermissionsExt},
    path::Path,
    sync::{
        Arc,
        atomic::{AtomicUsize, Ordering},
    },
    time::{Duration, SystemTime, UNIX_EPOCH},
};
use tokio::sync::{RwLock, Semaphore};
use tokio_rusqlite::{
    Connection as AsyncConnection,
    rusqlite::{self, Connection, OpenFlags, OptionalExtension, TransactionBehavior, params},
};
use uuid::Uuid;

const PAGE_SIZE: usize = 256;
pub(super) const PAGE_BYTES: usize = 120 * 1024;
pub(super) const RETENTION_MS: i64 = 7 * 24 * 60 * 60 * 1000;

#[derive(Clone, Debug, thiserror::Error)]
pub(crate) enum HistoryError {
    #[error("invalid history request: {0}")]
    Invalid(&'static str),
    #[error("web chat authorization expired")]
    AuthorizationExpired,
    #[error("recipient unavailable")]
    RecipientUnavailable,
    #[error("request_conflict")]
    Conflict,
    #[error("history queue full or closed")]
    Busy,
    #[error("history database operation failed")]
    Database(#[source] Arc<rusqlite::Error>),
    #[error("history filesystem operation failed")]
    Io(#[source] Arc<std::io::Error>),
}

impl From<rusqlite::Error> for HistoryError {
    fn from(error: rusqlite::Error) -> Self {
        Self::Database(Arc::new(error))
    }
}

impl From<std::io::Error> for HistoryError {
    fn from(error: std::io::Error) -> Self {
        Self::Io(Arc::new(error))
    }
}

#[derive(Clone)]
pub(crate) struct SendReceipt {
    pub(super) sender: String,
    pub(super) request_id: String,
    pub(super) fingerprint: String,
}

pub(super) struct Append {
    pub(super) entry: ChatEvent,
    pub(super) admission: Admission,
}

// Only the originating ingress can supply the corresponding idempotency key.
pub(super) enum Admission {
    Paper([u8; 32]),
    Web(SendReceipt, Box<dyn Fn() -> bool + Send + Sync>),
    Native(NativeEvent),
    Presence,
}

pub(super) struct Saved {
    pub(super) message_id: String,
    pub(super) delivery: Option<Delivery>,
}

pub(super) struct Delivery {
    pub(super) cursor: i64,
    pub(super) order_key: String,
    pub(super) expires_at: i64,
}

pub(super) type AppendResult = Result<(Saved, Append), HistoryError>;

impl Append {
    pub(super) fn size(&self) -> usize {
        self.entry.encoded_len()
            + match &self.admission {
                Admission::Paper(key) => key.len(),
                Admission::Native(native) => native.size(),
                Admission::Web(receipt, _) => {
                    receipt.sender.len() + receipt.request_id.len() + receipt.fingerprint.len()
                }
                Admission::Presence => 0,
            }
    }
}

pub(crate) struct HistoryStore {
    writer: AsyncConnection,
    readers: [AsyncConnection; 2],
    next_reader: AtomicUsize,
    read_slots: Arc<Semaphore>,
    cursors: Arc<Cursors>,
    closed: RwLock<bool>,
    _ownership: File,
}

impl HistoryStore {
    /// Open the single authoritative mikd history database.
    /// # Errors
    /// Refuses unsafe files, competing writers, unsupported schemas and corrupt metadata.
    pub(crate) async fn open(path: &Path) -> Result<Self, HistoryError> {
        let owned_path = path.to_owned();
        let ownership = tokio::task::spawn_blocking(move || lock_history(&owned_path))
            .await
            .map_err(std::io::Error::other)??;
        let writer = AsyncConnection::open_with_flags(
            path,
            OpenFlags::SQLITE_OPEN_READ_WRITE | OpenFlags::SQLITE_OPEN_NOFOLLOW,
        )
        .await?;
        let cursors = writer
            .call(|connection| {
                let cursors = initialize(connection)?;
                let mode: String =
                    connection.query_row("PRAGMA journal_mode=WAL", [], |row| row.get(0))?;
                if mode != "wal" {
                    return Err(HistoryError::Invalid("history database requires WAL"));
                }
                Ok(cursors)
            })
            .await
            .map_err(unwrap_error)?;
        let readers = [open_reader(path).await?, open_reader(path).await?];
        Ok(Self {
            writer,
            readers,
            next_reader: AtomicUsize::new(0),
            read_slots: Arc::new(Semaphore::new(16)),
            cursors: Arc::new(cursors),
            closed: RwLock::new(false),
            _ownership: ownership,
        })
    }

    pub(super) async fn append_many(
        &self,
        appends: Vec<Append>,
    ) -> Result<Vec<AppendResult>, HistoryError> {
        let closed = self.closed.read().await;
        if *closed {
            return Err(HistoryError::Busy);
        }
        self.writer
            .call(move |connection| {
                let results = append_entries(connection, &appends, now_ms()?)?;
                Ok(results
                    .into_iter()
                    .zip(appends)
                    .map(|(result, append)| result.map(|saved| (saved, append)))
                    .collect())
            })
            .await
            .map_err(unwrap_error)
    }

    pub(super) async fn paper_receipt(
        &self,
        id: String,
        digest: [u8; 32],
    ) -> Result<Option<String>, HistoryError> {
        self.read(move |db, now| saved_paper(db, &id, &digest, now - RETENTION_MS))
            .await
    }

    pub(super) async fn web_receipt(
        &self,
        receipt: SendReceipt,
    ) -> Result<Option<String>, HistoryError> {
        self.read(move |db, now| saved_web(db, &receipt, now - RETENTION_MS))
            .await
    }

    pub(super) async fn posted_receipts(
        &self,
        viewer: String,
        staff: bool,
        ids: Vec<String>,
    ) -> Result<Vec<PostedReceipt>, HistoryError> {
        self.read(move |db, now| query::posted_receipts(db, &viewer, staff, &ids, now))
            .await
    }

    pub(super) async fn sync(&self, request: SyncRequest) -> Result<query::SyncPage, HistoryError> {
        let cursors = self.cursors.clone();
        let (caller, _cancel_on_drop) = tokio::sync::oneshot::channel();
        self.read(move |db, now| {
            cancellable_read(db, caller, |db| {
                let snapshot = db.transaction()?;
                query::sync_page(&snapshot, request, cursors, now)
            })
        })
        .await
    }

    async fn read<T: Send + 'static>(
        &self,
        read: impl FnOnce(&mut Connection, i64) -> Result<T, HistoryError> + Send + 'static,
    ) -> Result<T, HistoryError> {
        let closed = self.closed.read().await;
        if *closed {
            return Err(HistoryError::Busy);
        }
        let permit = self
            .read_slots
            .clone()
            .try_acquire_owned()
            .map_err(|_| HistoryError::Busy)?;
        let i = self.next_reader.fetch_add(1, Ordering::Relaxed) % self.readers.len();
        self.readers[i]
            .call(move |db| {
                let _permit = permit;
                read(db, now_ms()?)
            })
            .await
            .map_err(unwrap_error)
    }

    pub(super) async fn prune(&self) -> Result<(), HistoryError> {
        let closed = self.closed.read().await;
        if *closed {
            return Err(HistoryError::Busy);
        }
        self.writer
            .call(|connection| prune(connection, now_ms()?))
            .await
            .map_err(unwrap_error)
    }

    pub(super) async fn close(&self) -> Result<(), HistoryError> {
        let mut closed = self.closed.write().await;
        if *closed {
            return Ok(());
        }
        *closed = true;
        let [first, second] = &self.readers;
        let (first, second) = tokio::join!(first.clone().close(), second.clone().close());
        self.writer
            .clone()
            .close()
            .await
            .and(first)
            .and(second)
            .map_err(|error| match error {
                tokio_rusqlite::Error::Close((_, cause)) => HistoryError::from(cause),
                _ => HistoryError::Busy,
            })
    }
}

/// tokio-rusqlite keeps queued work after a future drops. Scope SQLite cancellation to this
/// invocation on its reader thread so a late `STOP_SENDING` cannot interrupt the next request.
fn cancellable_read<T>(
    db: &mut Connection,
    caller: tokio::sync::oneshot::Sender<()>,
    read: impl FnOnce(&mut Connection) -> Result<T, HistoryError>,
) -> Result<T, HistoryError> {
    if caller.is_closed() {
        return Err(HistoryError::Busy);
    }
    db.progress_handler(1024, Some(move || caller.is_closed()))?;
    let result = read(db);
    db.progress_handler(0, None::<fn() -> bool>)?;
    result
}

fn lock_history(path: &Path) -> Result<File, HistoryError> {
    let parent = path
        .parent()
        .filter(|path_parent| path.is_absolute() && !path_parent.as_os_str().is_empty())
        .ok_or(HistoryError::Invalid("absolute database path required"))?;
    fs::create_dir_all(parent)?;
    let metadata = fs::symlink_metadata(parent)?;
    if !metadata.is_dir() || metadata.permissions().mode() & 0o077 != 0 {
        return Err(HistoryError::Invalid("history directory must be private"));
    }
    let lock_path = path.with_extension("owner");
    if fs::symlink_metadata(&lock_path).is_ok_and(|metadata| !metadata.is_file()) {
        return Err(HistoryError::Invalid("unsafe ownership lock"));
    }
    let ownership = OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .truncate(false)
        .mode(0o600)
        .open(lock_path)?;
    ownership.try_lock().map_err(std::io::Error::other)?;
    match fs::symlink_metadata(path) {
        Ok(metadata) if metadata.is_file() => (),
        Ok(_) => return Err(HistoryError::Invalid("unsafe database path")),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
            OpenOptions::new()
                .create_new(true)
                .write(true)
                .mode(0o600)
                .open(path)?;
        }
        Err(error) => return Err(error.into()),
    }
    fs::set_permissions(path, fs::Permissions::from_mode(0o600))?;
    Ok(ownership)
}

async fn open_reader(path: &Path) -> Result<AsyncConnection, HistoryError> {
    let reader = AsyncConnection::open_with_flags(
        path,
        OpenFlags::SQLITE_OPEN_READ_ONLY | OpenFlags::SQLITE_OPEN_NOFOLLOW,
    )
    .await?;
    reader
        .call(|connection| {
            connection.execute_batch("PRAGMA trusted_schema=OFF; PRAGMA query_only=ON;")?;
            Ok(())
        })
        .await
        .map_err(unwrap_error)?;
    Ok(reader)
}

fn unwrap_error(error: tokio_rusqlite::Error<HistoryError>) -> HistoryError {
    match error {
        tokio_rusqlite::Error::Error(error) => error,
        _ => HistoryError::Busy,
    }
}

fn now_ms() -> Result<i64, HistoryError> {
    i64::try_from(
        SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map_err(|_| HistoryError::Invalid("clock before epoch"))?
            .as_millis(),
    )
    .map_err(|_| HistoryError::Invalid("clock exhausted"))
}

const SCHEMA: &str = include_str!("schema.sql");
const NATIVE_HEADER: (i32, i32) = (0x4d49_4b48, 6);

fn initialize(connection: &mut Connection) -> Result<Cursors, HistoryError> {
    connection.busy_timeout(Duration::from_secs(3))?;
    connection.execute_batch(
        "PRAGMA trusted_schema=OFF; PRAGMA foreign_keys=ON; PRAGMA synchronous=FULL;",
    )?;
    let transaction = connection.transaction_with_behavior(TransactionBehavior::Immediate)?;
    let header = (
        transaction.pragma_query_value(None, "application_id", |row| row.get(0))?,
        transaction.pragma_query_value(None, "user_version", |row| row.get(0))?,
    );
    match header {
        NATIVE_HEADER => {}
        (0, 0)
            if !transaction
                .prepare("SELECT 1 FROM sqlite_schema LIMIT 1")?
                .exists([])? =>
        {
            transaction.execute_batch(SCHEMA)?;
            transaction.execute(
                "INSERT INTO chat_metadata(name,value) VALUES ('database_id',?1)",
                [Uuid::new_v4().to_string()],
            )?;
            let mut secret = [0; 32];
            getrandom::fill(&mut secret)
                .map_err(|_| HistoryError::Invalid("cursor entropy unavailable"))?;
            let encoded = URL_SAFE_NO_PAD.encode(secret);
            secret.fill(0);
            transaction.execute(
                "INSERT INTO chat_metadata(name,value) VALUES ('cursor_key',?1)",
                [encoded],
            )?;
        }
        _ => {
            return Err(HistoryError::Invalid(
                "unsupported history database; configure an empty database path",
            ));
        }
    }
    let cursors = cursors(&transaction)?;
    transaction.commit()?;
    Ok(cursors)
}

fn cursors(connection: &Connection) -> Result<Cursors, HistoryError> {
    let database: String = connection.query_row(
        "SELECT value FROM chat_metadata WHERE name='database_id'",
        [],
        |r| r.get(0),
    )?;
    let key: String = connection.query_row(
        "SELECT value FROM chat_metadata WHERE name='cursor_key'",
        [],
        |r| r.get(0),
    )?;
    Cursors::new(&key, &database)
}

fn prune(connection: &Connection, now: i64) -> Result<(), HistoryError> {
    connection
        .prepare_cached("DELETE FROM chat_messages WHERE recorded_at <= ?1")?
        .execute([now - RETENTION_MS])?;
    Ok(())
}

fn append_entries(
    connection: &mut Connection,
    appends: &[Append],
    now: i64,
) -> Result<Vec<Result<Saved, HistoryError>>, HistoryError> {
    let mut transaction = connection.transaction_with_behavior(TransactionBehavior::Immediate)?;
    prune(&transaction, now)?;
    let results = if let [append] = appends {
        vec![Ok(insert_entry(&transaction, append, now)?)]
    } else {
        let mut results = Vec::with_capacity(appends.len());
        for append in appends {
            let savepoint = transaction.savepoint()?;
            let result = insert_entry(&savepoint, append, now);
            if result.is_ok() {
                savepoint.commit()?;
            } else {
                savepoint.finish()?;
            }
            results.push(result);
        }
        results
    };
    if results
        .iter()
        .any(|result| result.as_ref().is_ok_and(|saved| saved.delivery.is_some()))
    {
        transaction.commit()?;
    } else {
        transaction.rollback()?;
    }
    Ok(results)
}

fn insert_entry(
    transaction: &Connection,
    append: &Append,
    now: i64,
) -> Result<Saved, HistoryError> {
    let entry = &append.entry;
    let record = entry
        .record
        .as_ref()
        .ok_or(HistoryError::Invalid("history record required"))?;
    if record.kind.is_none() || record.encoded_len() > PAGE_BYTES {
        return Err(HistoryError::Invalid("invalid history record"));
    }
    let (mut source_key, mut paper_key) = (None, None);
    let prior = match &append.admission {
        Admission::Paper(key) => {
            paper_key = Some(key.as_slice());
            saved_paper(transaction, &entry.message_id, key, i64::MIN)?
        }
        Admission::Web(receipt, _) => saved_web(transaction, receipt, i64::MIN)?,
        Admission::Native(event) => {
            let key = event.key(&record.platform)?;
            let prior = transaction
                .query_row(
                    "SELECT message_id FROM chat_messages WHERE source_key=?1",
                    [&key],
                    |row| row.get(0),
                )
                .optional()?;
            source_key = Some(key);
            prior
        }
        Admission::Presence => None,
    };
    if let Some(message_id) = prior {
        return Ok(Saved {
            message_id,
            delivery: None,
        });
    }
    // The writer admits each new send inside its transaction. Revocation before this
    // point wins even if the request spent time queued. A prior durable receipt is immutable.
    if let Admission::Web(_, authorized) = &append.admission
        && !authorized()
    {
        return Err(HistoryError::AuthorizationExpired);
    }
    let expires_at = now
        .checked_add(RETENTION_MS)
        .ok_or(HistoryError::Invalid("invalid recording time"))?;
    let destination = destination(record)?;
    let channel = destination.channel().name();
    let (first, second, conversation) = match destination {
        Destination::Private { author, recipient } => {
            let mut peers = [&author.id, &recipient.id];
            peers.sort_unstable();
            (
                Some(peers[0]),
                Some(peers[1]),
                format!("private:{}:{}", peers[0], peers[1]),
            )
        }
        _ => (None, None, channel.to_owned()),
    };
    let previous: Option<String> = transaction
        .query_row(
            "SELECT order_key FROM chat_conversations WHERE conversation=?1",
            [&conversation],
            |r| r.get(0),
        )
        .optional()?;
    let order_key = next_order(previous.as_deref(), now)?;
    let inserted = transaction.execute("INSERT INTO chat_messages(message_id,source_key,channel,private_first,private_second,restricted,recorded_at,record,order_key,submission_key) VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10) ON CONFLICT(message_id) DO NOTHING",
        params![entry.message_id, source_key, channel, first, second, entry.visibility.is_some(), now, record.encode_to_vec(), order_key, paper_key])?;
    if inserted == 0 {
        return Err(HistoryError::Conflict);
    }
    let cursor = transaction.last_insert_rowid();
    if let Some(visibility) = &entry.visibility {
        for viewer in &visibility.player_ids {
            transaction.execute(
                "INSERT INTO chat_viewers VALUES (?1,?2)",
                params![cursor, viewer],
            )?;
        }
    }
    transaction.execute("INSERT INTO chat_conversations VALUES (?1,?2) ON CONFLICT(conversation) DO UPDATE SET order_key=excluded.order_key", params![conversation, order_key])?;
    if let Admission::Web(receipt, _) = &append.admission {
        transaction.execute(
            "INSERT INTO chat_requests VALUES (?1,?2,?3,?4)",
            params![
                receipt.sender,
                receipt.request_id,
                receipt.fingerprint,
                cursor
            ],
        )?;
    }
    Ok(Saved {
        message_id: entry.message_id.clone(),
        delivery: Some(Delivery {
            cursor,
            order_key,
            expires_at,
        }),
    })
}

fn saved_web(
    db: &Connection,
    receipt: &SendReceipt,
    cutoff: i64,
) -> Result<Option<String>, HistoryError> {
    validate_receipt(receipt)?;
    let existing = db.query_row("SELECT r.fingerprint,m.message_id FROM chat_requests r JOIN chat_messages m ON m.cursor=r.message_cursor WHERE r.sender=?1 AND r.request_id=?2 AND m.recorded_at>?3",
        params![receipt.sender, receipt.request_id, cutoff], |row| Ok((row.get::<_, String>(0)?, row.get(1)?))).optional()?;
    existing
        .map(|(fingerprint, id)| {
            if fingerprint == receipt.fingerprint {
                Ok(id)
            } else {
                Err(HistoryError::Conflict)
            }
        })
        .transpose()
}

fn saved_paper(
    db: &Connection,
    id: &str,
    fingerprint: &[u8; 32],
    cutoff: i64,
) -> Result<Option<String>, HistoryError> {
    let stored = db
        .query_row(
            "SELECT submission_key FROM chat_messages WHERE message_id=?1 AND recorded_at>?2",
            params![id, cutoff],
            |r| r.get::<_, Option<Vec<u8>>>(0),
        )
        .optional()?;
    stored
        .map(|key| {
            if key.as_deref() == Some(fingerprint.as_slice()) {
                Ok(id.to_owned())
            } else {
                Err(HistoryError::Conflict)
            }
        })
        .transpose()
}

fn validate_receipt(receipt: &SendReceipt) -> Result<(), HistoryError> {
    uuid(&receipt.sender)?;
    if receipt.request_id.is_empty()
        || receipt.request_id.len() > 512
        || receipt.fingerprint.is_empty()
        || receipt.fingerprint.len() > 512
    {
        return Err(HistoryError::Invalid("invalid request receipt"));
    }
    Ok(())
}

fn next_order(previous: Option<&str>, now: i64) -> Result<String, HistoryError> {
    let mut millis =
        u64::try_from(now.max(0)).map_err(|_| HistoryError::Invalid("invalid ordering time"))?;
    let mut bytes = [0; 16];
    getrandom::fill(&mut bytes)
        .map_err(|_| HistoryError::Invalid("ordering entropy unavailable"))?;
    let mut random = u128::from_be_bytes(bytes) & ((1_u128 << 74) - 1);
    if let Some(previous) = previous {
        let old = Uuid::parse_str(previous)
            .map_err(|_| HistoryError::Invalid("invalid stored ordering UUID"))?
            .as_u128();
        let old_time =
            u64::try_from(old >> 80).map_err(|_| HistoryError::Invalid("invalid ordering time"))?;
        if millis <= old_time {
            millis = old_time;
            let old_random = ((old >> 64) & 0xfff) << 62 | (old & ((1_u128 << 62) - 1));
            let increment = (random & 0xffff_ffff).max(1);
            let next = old_random + increment;
            if next < (1_u128 << 74) {
                random = next;
            } else {
                millis += 1;
            }
        }
    }
    if millis >= (1_u64 << 48) {
        return Err(HistoryError::Invalid("ordering timestamp exhausted"));
    }
    // UUID owns RFC 9562 version/variant encoding; only the persisted monotonic
    // counter is application-specific because it must survive process restarts.
    let random_bytes = ((random >> 62) << 64 | (random & ((1_u128 << 62) - 1))).to_be_bytes();
    let suffix: [u8; 10] = random_bytes[6..]
        .try_into()
        .map_err(|_| HistoryError::Invalid("ordering counter size"))?;
    Ok(uuid::Builder::from_unix_timestamp_millis(millis, &suffix)
        .into_uuid()
        .to_string())
}

#[cfg(test)]
mod tests {
    use super::super::model::Channel;
    use super::*;
    use query::{SyncPage, head, sync_page};
    const ALICE: &str = "00000000-0000-4000-8000-000000000001";
    const BOB: &str = "00000000-0000-4000-8000-000000000002";
    const OTHER: &str = "00000000-0000-4000-8000-000000000003";

    fn entry(
        id: &str,
        channel: Channel,
        participants: &[&str],
        viewers: Option<&[&str]>,
    ) -> Append {
        use super::super::model::room;
        use wire::{
            chat_destination::{Room, Target},
            chat_record::Kind,
        };
        let player = |id: &str| wire::ChatPlayer {
            id: id.into(),
            name: match id {
                ALICE => "Alice",
                BOB => "Bob",
                _ => "Other",
            }
            .into(),
        };
        let destination = match channel {
            Channel::Private => wire::ChatDestination {
                target: Some(Target::Recipient(player(participants[1]))),
            },
            Channel::Public => room(Room::Public),
            Channel::Staff => room(Room::Staff),
        };
        Append {
            entry: ChatEvent {
                message_id: id.into(),
                visibility: viewers.map(|ids| wire::ChatVisibility {
                    player_ids: ids.iter().map(|id| (*id).into()).collect(),
                }),
                record: Some(wire::ChatRecord {
                    platform: "game".into(),
                    kind: Some(Kind::User(wire::ChatUserRecord {
                        destination: Some(destination),
                        author: Some(super::super::model::player_principal(player(
                            participants.first().copied().unwrap_or(ALICE),
                        ))),
                        body: Some(wire::ChatBody {
                            parts: vec![super::super::text_part(id, None)],
                        }),
                    })),
                    ..Default::default()
                }),
            },
            admission: Admission::Paper([0; 32]),
        }
    }
    fn audience(viewer: &str, channel: Channel) -> Audience {
        Audience {
            viewer: viewer.into(),
            staff: false,
            channel,
            target: None,
        }
    }
    fn save(connection: &mut Connection, entry: &Append, now: i64) -> Result<Saved, HistoryError> {
        save_one(connection, entry, now)
    }
    fn save_one(
        connection: &mut Connection,
        entry: &Append,
        now: i64,
    ) -> Result<Saved, HistoryError> {
        append_entries(connection, std::slice::from_ref(entry), now)?
            .pop()
            .unwrap()
    }
    fn database() -> Connection {
        let mut connection = Connection::open_in_memory().unwrap();
        initialize(&mut connection).unwrap();
        connection
    }
    fn sync(
        connection: &Connection,
        audience: Audience,
        since: Option<String>,
        continuation: Option<String>,
        hidden: Vec<String>,
        now: i64,
    ) -> Result<SyncPage, HistoryError> {
        sync_page(
            connection,
            SyncRequest {
                audience,
                since,
                continuation,
                hidden,
            },
            Arc::new(cursors(connection).unwrap()),
            now,
        )
    }

    #[test]
    fn exact_retention_and_cascades_are_enforced_by_the_storage_owner() {
        let mut connection = database();
        save(
            &mut connection,
            &entry("old", Channel::Public, &[], Some(&[ALICE])),
            1000,
        )
        .unwrap();
        assert_eq!(
            1,
            sync(
                &connection,
                audience(ALICE, Channel::Public),
                None,
                None,
                vec![],
                1000 + RETENTION_MS - 1
            )
            .unwrap()
            .rows
            .len()
        );
        assert!(
            sync(
                &connection,
                audience(ALICE, Channel::Public),
                None,
                None,
                vec![],
                1000 + RETENTION_MS
            )
            .unwrap()
            .rows
            .is_empty()
        );
        prune(&connection, 1000 + RETENTION_MS).unwrap();
        assert_eq!(
            0,
            connection
                .query_row("SELECT count(*) FROM chat_viewers", [], |r| r
                    .get::<_, i64>(0))
                .unwrap()
        );
    }

    #[test]
    fn authorization_preserves_private_and_restricted_audiences() {
        let mut connection = database();
        for append in [
            entry("ab", Channel::Private, &[ALICE, BOB], None),
            entry("bo", Channel::Private, &[BOB, OTHER], None),
            entry("staff", Channel::Staff, &[], None),
            entry("restricted", Channel::Public, &[], Some(&[ALICE])),
            entry("nobody", Channel::Public, &[], Some(&[])),
        ] {
            save(&mut connection, &append, 1000).unwrap();
        }
        let private = sync(
            &connection,
            audience(ALICE, Channel::Private),
            None,
            None,
            vec![],
            1000,
        )
        .unwrap();
        assert_eq!(
            vec!["ab"],
            private
                .rows
                .iter()
                .map(|r| r.message_id.as_str())
                .collect::<Vec<_>>()
        );
        let restricted = sync(
            &connection,
            audience(ALICE, Channel::Public),
            None,
            None,
            vec![],
            1000,
        )
        .unwrap();
        assert_eq!(
            vec!["restricted"],
            restricted
                .rows
                .iter()
                .map(|r| r.message_id.as_str())
                .collect::<Vec<_>>()
        );
        assert!(
            sync(
                &connection,
                audience(BOB, Channel::Public),
                None,
                None,
                vec![],
                1000
            )
            .unwrap()
            .rows
            .is_empty()
        );
        assert!(
            sync(
                &connection,
                audience(ALICE, Channel::Staff),
                None,
                None,
                vec![],
                1000
            )
            .is_err()
        );
    }

    #[test]
    fn cancelling_a_history_read_interrupts_only_its_sqlite_statement() {
        let mut db = database();
        let (caller, cancel) = tokio::sync::oneshot::channel();
        let count = |db: &mut Connection| -> Result<i64, HistoryError> {
            Ok(db.query_row(
                "WITH RECURSIVE n(x) AS (VALUES(1) UNION ALL SELECT x+1 FROM n WHERE x<1000)
                 SELECT count(*) FROM n",
                [],
                |row| row.get(0),
            )?)
        };
        let interrupted = cancellable_read(&mut db, caller, |db| {
            drop(cancel);
            count(db)
        });
        assert!(matches!(interrupted, Err(HistoryError::Database(error))
            if error.sqlite_error_code() == Some(rusqlite::ErrorCode::OperationInterrupted)));
        assert_eq!(count(&mut db).unwrap(), 1000);
    }

    #[test]
    fn receipt_reconciliation_respects_owner_retention_staff_visibility_and_hidden_recipient() {
        let mut db = database();
        for (id, sender, channel, viewers, recorded) in [
            ("private", ALICE, Channel::Private, None, 1000),
            ("staff", ALICE, Channel::Staff, None, 1000),
            ("expired", ALICE, Channel::Public, None, 999),
            ("other", BOB, Channel::Public, None, 1000),
            ("restricted", ALICE, Channel::Public, Some(&[BOB][..]), 1000),
        ] {
            let mut append = entry(id, channel, &[sender, BOB], viewers);
            append.admission = Admission::Web(
                SendReceipt {
                    sender: sender.into(),
                    request_id: id.into(),
                    fingerprint: "body".into(),
                },
                Box::new(|| true),
            );
            save(&mut db, &append, recorded).unwrap();
        }
        let ids = [
            "other",
            "unknown",
            "expired",
            "private",
            "private",
            "restricted",
            "staff",
        ]
        .map(str::to_owned);
        let at = RETENTION_MS + 999;
        let receipts = query::posted_receipts(&db, ALICE, false, &ids, at).unwrap();
        assert_eq!(
            serde_json::to_value(&receipts).unwrap(),
            serde_json::json!([
                {"requestId":"private","messageId":"private"}
            ])
        );
        assert!(!receipts[0].visible_to(&[BOB.into()]));
        assert!(receipts[0].visible_to(&[]));
        let staff = query::posted_receipts(&db, ALICE, true, &ids, at).unwrap();
        assert_eq!(staff.len(), 2);
        assert_eq!(
            serde_json::to_value(query::posted_receipts(&db, BOB, true, &ids, at).unwrap())
                .unwrap(),
            serde_json::json!([{"requestId":"other","messageId":"other"}])
        );
    }

    #[test]
    fn durable_deduplication_and_monotonic_order_survive_restart_and_clock_rollback() {
        let directory = tempfile::tempdir().unwrap();
        let path = directory.path().join("history.db");
        let mut connection = Connection::open(&path).unwrap();
        initialize(&mut connection).unwrap();
        let mut first = entry("first", Channel::Public, &[], None);
        first.admission = Admission::Web(
            SendReceipt {
                sender: ALICE.into(),
                request_id: "request".into(),
                fingerprint: "fingerprint".into(),
            },
            Box::new(|| true),
        );
        let saved = save_one(&mut connection, &first, 2000).unwrap();
        assert!(saved.delivery.is_some());
        let scope = audience(ALICE, Channel::Public);
        let checkpoint = sync(&connection, scope.clone(), None, None, vec![], 2000)
            .unwrap()
            .checkpoint()
            .unwrap();
        drop(connection);
        let mut connection = Connection::open(&path).unwrap();
        initialize(&mut connection).unwrap();
        assert!(
            !sync(&connection, scope, Some(checkpoint), None, vec![], 2000)
                .unwrap()
                .reset
        );
        first.entry.message_id = "regenerated".into();
        let retried = save_one(&mut connection, &first, 1000).unwrap();
        assert!(retried.delivery.is_none());
        assert_eq!(saved.message_id, retried.message_id);
        let next = save(
            &mut connection,
            &entry("second", Channel::Public, &[], None),
            1000,
        )
        .unwrap();
        let next_order = next.delivery.unwrap().order_key;
        assert!(saved.delivery.unwrap().order_key < next_order);
        assert_eq!(
            Some(uuid::Version::SortRand),
            Uuid::parse_str(&next_order).unwrap().get_version()
        );
        let Admission::Web(receipt, _) = &mut first.admission else {
            unreachable!()
        };
        receipt.fingerprint = "different".into();
        assert!(matches!(
            save_one(&mut connection, &first, 1000),
            Err(HistoryError::Conflict)
        ));
        assert_eq!(2, head(&connection).unwrap());
    }

    #[test]
    fn cursor_binding_and_backfill_keep_the_original_head_without_losing_new_arrivals() {
        let mut connection = database();
        for index in 0..270 {
            save(
                &mut connection,
                &entry(&format!("old-{index}"), Channel::Public, &[], None),
                1000,
            )
            .unwrap();
        }
        let query = audience(ALICE, Channel::Public);
        let first = sync(&connection, query.clone(), None, None, vec![], 1000).unwrap();
        let checkpoint = first.checkpoint().unwrap();
        assert_eq!(91, checkpoint.len());
        assert!(first.has_more);
        save(
            &mut connection,
            &entry("new", Channel::Public, &[], None),
            1000,
        )
        .unwrap();
        let token = first
            .continuation(first.rows.last().unwrap().cursor)
            .unwrap();
        // Swapping the two authenticated cursor kinds must never skip the unread portion.
        assert!(
            sync(
                &connection,
                query.clone(),
                Some(token.clone()),
                None,
                vec![],
                1000
            )
            .unwrap()
            .reset
        );
        assert!(
            sync(
                &connection,
                query.clone(),
                None,
                Some(checkpoint.clone()),
                vec![],
                1000
            )
            .unwrap()
            .reset
        );
        assert!(
            sync(
                &connection,
                query.clone(),
                Some(checkpoint.clone()),
                Some(token.clone()),
                vec![],
                1000
            )
            .is_err()
        );
        let next = sync(&connection, query.clone(), None, Some(token), vec![], 1000).unwrap();
        assert_eq!(14, next.rows.len());
        let delta = sync(
            &connection,
            query.clone(),
            Some(next.checkpoint().unwrap()),
            None,
            vec![],
            1000,
        )
        .unwrap();
        assert_eq!("new", delta.rows[0].message_id);
        assert!(
            sync(
                &connection,
                query.clone(),
                Some(checkpoint.clone()),
                None,
                vec![BOB.into()],
                1000
            )
            .unwrap()
            .reset
        );
        let mut other = query;
        other.viewer = OTHER.into();
        assert!(
            sync(&connection, other, Some(checkpoint), None, vec![], 1000)
                .unwrap()
                .reset
        );
    }

    #[test]
    fn live_checkpoint_skips_a_thousand_delivered_rows_and_keeps_the_offline_tail() {
        let mut db = database();
        let audience = audience(ALICE, Channel::Public);
        let page = sync(&db, audience.clone(), None, None, vec![], 1000).unwrap();
        let initial = page.checkpoint().unwrap();
        let mut anchor = page.into_live_anchor(Channel::Public, &[], initial.clone());
        let rows: Vec<_> = (0..1003)
            .map(|n| entry(&n.to_string(), Channel::Public, &[], None))
            .collect();
        let committed = append_entries(&mut db, &rows, 1000).unwrap();
        let delivered = committed[999]
            .as_ref()
            .unwrap()
            .delivery
            .as_ref()
            .unwrap()
            .cursor;
        assert_eq!(anchor.advance(delivered).unwrap(), initial);
        let resume = sync(
            &db,
            audience.clone(),
            Some(anchor.token.clone()),
            None,
            vec![],
            1000,
        )
        .unwrap();
        assert!(!resume.reset && !resume.has_more);
        assert_eq!(
            resume
                .rows
                .iter()
                .map(|row| row.message_id.as_str())
                .collect::<Vec<_>>(),
            ["1002", "1001", "1000"]
        );
        assert!(
            sync(
                &db,
                audience,
                Some(anchor.token),
                None,
                vec![BOB.into()],
                1000
            )
            .unwrap()
            .reset
        );
    }

    #[test]
    fn queued_web_sends_check_authority_at_admission_and_preserve_durable_receipts() {
        use std::sync::atomic::{AtomicBool, Ordering};
        let mut db = database();
        let enabled = Arc::new(AtomicBool::new(true));
        let post = |id, request: &str| {
            let mut append = entry(id, Channel::Public, &[], None);
            let enabled = enabled.clone();
            append.admission = Admission::Web(
                SendReceipt {
                    sender: ALICE.into(),
                    request_id: request.into(),
                    fingerprint: "same".into(),
                },
                Box::new(move || enabled.load(Ordering::Acquire)),
            );
            append
        };
        let accepted = post("first", "first-request");
        save_one(&mut db, &accepted, 1000).unwrap();
        let queued = post("queued", "second-request");
        enabled.store(false, Ordering::Release);
        let batch = [
            accepted,
            queued,
            entry("independent", Channel::Public, &[], None),
        ];
        let results = append_entries(&mut db, &batch, 1000).unwrap();
        assert!(results[0].as_ref().unwrap().delivery.is_none());
        assert!(matches!(
            results[1],
            Err(HistoryError::AuthorizationExpired)
        ));
        assert!(results[2].as_ref().unwrap().delivery.is_some());
        assert_eq!(head(&db).unwrap(), 2);
    }

    #[test]
    fn sqlite_failure_never_returns_a_false_commit_or_partial_receipt() {
        let mut connection = database();
        connection.execute_batch("PRAGMA query_only=ON;").unwrap();
        let mut append = entry("not-saved", Channel::Public, &[], None);
        append.admission = Admission::Web(
            SendReceipt {
                sender: ALICE.into(),
                request_id: "request".into(),
                fingerprint: "fingerprint".into(),
            },
            Box::new(|| true),
        );
        assert!(matches!(
            save_one(&mut connection, &append, 1000),
            Err(HistoryError::Database(_))
        ));
        assert_eq!(0, head(&connection).unwrap());
        assert_eq!(
            0,
            connection
                .query_row("SELECT count(*) FROM chat_requests", [], |r| r
                    .get::<_, i64>(0))
                .unwrap()
        );
    }

    #[test]
    fn batched_appends_preserve_order_and_isolate_conflicts_and_failed_writes() {
        let mut connection = database();
        connection
            .execute_batch(
                "CREATE TEMP TRIGGER fail_receipt BEFORE INSERT ON chat_requests
             WHEN NEW.request_id='fail' BEGIN SELECT RAISE(ABORT, 'fixture failure'); END;",
            )
            .unwrap();
        let with_receipt = |id, request: &str, fingerprint: &str| {
            let mut append = entry(id, Channel::Public, &[], None);
            append.admission = Admission::Web(
                SendReceipt {
                    sender: ALICE.into(),
                    request_id: request.into(),
                    fingerprint: fingerprint.into(),
                },
                Box::new(|| true),
            );
            append
        };
        let entries = [
            with_receipt("first", "request", "same"),
            with_receipt("retry", "request", "same"),
            with_receipt("conflict", "request", "different"),
            with_receipt("rolled-back", "fail", "same"),
            with_receipt("last", "new-request", "same"),
            with_receipt("first", "different-request", "different"),
        ];
        let results = append_entries(&mut connection, &entries, 1000).unwrap();
        let first = results[0].as_ref().unwrap();
        let retry = results[1].as_ref().unwrap();
        let last = results[4].as_ref().unwrap();
        assert!(retry.delivery.is_none());
        assert_eq!(first.message_id, retry.message_id);
        assert!(
            first.delivery.as_ref().unwrap().order_key < last.delivery.as_ref().unwrap().order_key
        );
        assert!(matches!(results[2], Err(HistoryError::Conflict)));
        assert!(matches!(results[3], Err(HistoryError::Database(_))));
        assert!(matches!(results[5], Err(HistoryError::Conflict)));
        let stored = connection
            .prepare("SELECT message_id FROM chat_messages ORDER BY cursor")
            .unwrap()
            .query_map([], |row| row.get::<_, String>(0))
            .unwrap()
            .collect::<Result<Vec<_>, _>>()
            .unwrap();
        assert_eq!(stored, ["first", "last"]);
    }

    #[test]
    #[ignore = "manual disk-dependent WAL benchmark"]
    fn benchmark_wal_history_batches() {
        const MESSAGES: usize = 1024;
        let root = std::env::var_os("MIKD_BENCH_DIR").unwrap_or_else(|| ".".into());
        let appends = (0..MESSAGES)
            .map(|sequence| entry(&format!("msg-{sequence}"), Channel::Public, &[], None))
            .collect::<Vec<_>>();
        for size in [1, 16] {
            let directory = tempfile::tempdir_in(&root).unwrap();
            let mut connection = Connection::open(directory.path().join("chat.db")).unwrap();
            initialize(&mut connection).unwrap();
            connection.execute_batch("PRAGMA journal_mode=WAL").unwrap();
            let start = std::time::Instant::now();
            for group in appends.chunks(size) {
                let results = append_entries(&mut connection, group, 1000).unwrap();
                assert!(
                    results
                        .into_iter()
                        .all(|saved| saved.unwrap().delivery.is_some())
                );
            }
            println!(
                "batch={size} messages={MESSAGES} elapsed={:?}",
                start.elapsed()
            );
        }
    }

    #[tokio::test]
    async fn history_reads_the_committed_snapshot_while_a_write_is_open() {
        let directory = tempfile::Builder::new()
            .permissions(fs::Permissions::from_mode(0o700))
            .tempdir()
            .unwrap();
        let store = HistoryStore::open(&directory.path().join("history.db"))
            .await
            .unwrap();
        let public = Channel::Public;
        store
            .append_many(vec![entry("committed", public, &[], None)])
            .await
            .unwrap()
            .pop()
            .unwrap()
            .unwrap();

        let (started, writing) = tokio::sync::oneshot::channel();
        let (finish, release) = std::sync::mpsc::channel();
        let writer = store.writer.clone();
        let pending = tokio::spawn(async move {
            writer
                .call_raw(move |connection| {
                    let transaction = connection.transaction().unwrap();
                    transaction
                        .execute("DELETE FROM chat_messages", [])
                        .unwrap();
                    started.send(()).unwrap();
                    release.recv_timeout(Duration::from_secs(5)).unwrap();
                    transaction.commit().unwrap();
                })
                .await
                .unwrap();
        });
        writing.await.unwrap();
        let query = || SyncRequest {
            audience: audience(ALICE, public),
            since: None,
            continuation: None,
            hidden: vec![],
        };
        let snapshot = tokio::time::timeout(Duration::from_secs(2), store.sync(query())).await;
        finish.send(()).unwrap();
        pending.await.unwrap();
        assert_eq!("committed", snapshot.unwrap().unwrap().rows[0].message_id);
        assert!(store.sync(query()).await.unwrap().rows.is_empty());
        store.close().await.unwrap();
    }
}
