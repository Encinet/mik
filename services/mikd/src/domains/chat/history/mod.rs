mod query;

pub(super) use query::{Audience, SyncRequest};

use super::cursor::Cursors;
use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
use mik_protocol::wire::{self, HistoryChannel, HistoryEntry, HistorySaved};
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

pub(crate) struct SendReceipt {
    pub(super) sender: String,
    pub(super) request_id: String,
    pub(super) fingerprint: String,
}

pub(super) struct Append {
    pub(super) entry: HistoryEntry,
    pub(super) receipt: Option<SendReceipt>,
}

pub(super) type AppendResult = Result<(HistorySaved, Append), HistoryError>;

impl Append {
    pub(super) fn size(&self) -> usize {
        self.entry.encoded_len()
            + self.receipt.as_ref().map_or(0, |receipt| {
                receipt.sender.len() + receipt.request_id.len() + receipt.fingerprint.len()
            })
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
        mut appends: Vec<Append>,
    ) -> Result<Vec<AppendResult>, HistoryError> {
        let closed = self.closed.read().await;
        if *closed {
            return Err(HistoryError::Busy);
        }
        self.writer
            .call(move |connection| {
                let results = append_entries(connection, &mut appends, now_ms()?)?;
                Ok(results
                    .into_iter()
                    .zip(appends)
                    .map(|(result, append)| result.map(|saved| (saved, append)))
                    .collect())
            })
            .await
            .map_err(unwrap_error)
    }

    pub(super) async fn sync(&self, request: SyncRequest) -> Result<query::SyncPage, HistoryError> {
        let closed = self.closed.read().await;
        if *closed {
            return Err(HistoryError::Busy);
        }
        let permit = self
            .read_slots
            .clone()
            .try_acquire_owned()
            .map_err(|_| HistoryError::Busy)?;
        let reader = self.next_reader.fetch_add(1, Ordering::Relaxed) % self.readers.len();
        let cursors = self.cursors.clone();
        self.readers[reader]
            .call(move |connection| {
                let _permit = permit;
                let snapshot = connection.transaction()?;
                query::sync_page(&snapshot, request, cursors, now_ms()?)
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
const NATIVE_HEADER: (i32, i32) = (0x4d49_4b48, 1);

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
    appends: &mut [Append],
    now: i64,
) -> Result<Vec<Result<HistorySaved, HistoryError>>, HistoryError> {
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
        .any(|result| result.as_ref().is_ok_and(|saved| saved.inserted))
    {
        transaction.commit()?;
    } else {
        transaction.rollback()?;
    }
    Ok(results)
}

fn insert_entry(
    transaction: &Connection,
    append: &mut Append,
    now: i64,
) -> Result<HistorySaved, HistoryError> {
    let entry = &mut append.entry;
    validate_entry(entry)?;
    let record = entry
        .record
        .as_ref()
        .ok_or(HistoryError::Invalid("history record required"))?;
    if record.kind.is_none() || record.encoded_len() > PAGE_BYTES {
        return Err(HistoryError::Invalid("invalid history record"));
    }
    let source_key = social_event_key(record)?;
    if let Some(receipt) = &append.receipt {
        validate_receipt(receipt)?;
    }
    if let Some(receipt) = &append.receipt {
        let existing = transaction.query_row("SELECT r.fingerprint,m.message_id,m.order_key FROM chat_requests r JOIN chat_messages m ON m.cursor=r.message_cursor WHERE r.sender=?1 AND r.request_id=?2",
            params![receipt.sender, receipt.request_id], |r| Ok((r.get::<_, String>(0)?, r.get::<_, String>(1)?, r.get::<_, String>(2)?))).optional()?;
        if let Some((fingerprint, message_id, order_key)) = existing {
            if fingerprint != receipt.fingerprint {
                return Err(HistoryError::Conflict);
            }
            return Ok(HistorySaved {
                message_id,
                order_key,
                inserted: false,
            });
        }
    }
    let existing = transaction
        .query_row(
            "SELECT order_key FROM chat_messages WHERE message_id=?1",
            [&entry.message_id],
            |r| r.get(0),
        )
        .optional()?;
    if let Some(order_key) = existing {
        return Ok(HistorySaved {
            message_id: entry.message_id.clone(),
            order_key,
            inserted: false,
        });
    }
    if let Some(source_key) = &source_key {
        let existing = transaction
            .query_row(
                "SELECT message_id,order_key FROM chat_messages WHERE source_key=?1",
                [source_key],
                |row| Ok((row.get(0)?, row.get(1)?)),
            )
            .optional()?;
        if let Some((message_id, order_key)) = existing {
            return Ok(HistorySaved {
                message_id,
                order_key,
                inserted: false,
            });
        }
    }
    let channel = channel(entry.channel)?;
    let conversation = format!("{channel}:{}", entry.participants.join(":"));
    let previous: Option<String> = transaction
        .query_row(
            "SELECT order_key FROM chat_conversations WHERE conversation=?1",
            [&conversation],
            |r| r.get(0),
        )
        .optional()?;
    let order_key = next_order(previous.as_deref(), now)?;
    let first = entry.participants.first();
    let second = entry.participants.last();
    transaction.execute("INSERT INTO chat_messages(message_id,source_key,channel,private_first,private_second,restricted,recorded_at,record,order_key) VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9)",
        params![entry.message_id, source_key, channel, first, second, entry.restricted, now, record.encode_to_vec(), order_key])?;
    let cursor = transaction.last_insert_rowid();
    if entry.restricted {
        for viewer in &entry.viewers {
            transaction.execute(
                "INSERT INTO chat_viewers VALUES (?1,?2)",
                params![cursor, viewer],
            )?;
        }
    }
    transaction.execute("INSERT INTO chat_conversations VALUES (?1,?2) ON CONFLICT(conversation) DO UPDATE SET order_key=excluded.order_key", params![conversation, order_key])?;
    if let Some(receipt) = &append.receipt {
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
    Ok(HistorySaved {
        message_id: entry.message_id.clone(),
        order_key,
        inserted: true,
    })
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

fn validate_entry(entry: &mut HistoryEntry) -> Result<(), HistoryError> {
    channel(entry.channel)?;
    if entry.message_id.trim().is_empty()
        || entry.message_id.len() > 2048
        || entry.viewers.len() > 4096
    {
        return Err(HistoryError::Invalid("invalid history entry"));
    }
    for id in entry.participants.iter().chain(&entry.viewers) {
        uuid(id)?;
    }
    entry.participants.sort();
    entry.participants.dedup();
    entry.viewers.sort();
    entry.viewers.dedup();
    if entry.channel == i32::from(HistoryChannel::HistoryPrivate) {
        if !(1..=2).contains(&entry.participants.len()) {
            return Err(HistoryError::Invalid("private audience required"));
        }
    } else if !entry.participants.is_empty() {
        return Err(HistoryError::Invalid("public audience has participants"));
    }
    if !entry.restricted && !entry.viewers.is_empty() {
        return Err(HistoryError::Invalid("unrestricted audience has viewers"));
    }
    Ok(())
}

fn social_event_key(record: &wire::ChatRecord) -> Result<Option<String>, HistoryError> {
    let Some(wire::chat_record::Kind::User(user)) = &record.kind else {
        return Ok(None);
    };
    let Some(social) = &user.social else {
        return Ok(None);
    };
    source_key(social).map(Some)
}

fn source_key(social: &wire::ChatSocialSource) -> Result<String, HistoryError> {
    let parts = [
        &social.platform_id,
        &social.conversation_id,
        &social.event_id,
    ];
    if parts
        .iter()
        .any(|part| part.is_empty() || part.len() > 2048 || part.contains('\0'))
    {
        return Err(HistoryError::Invalid("invalid social event key"));
    }
    Ok(format!("{}\0{}\0{}", parts[0], parts[1], parts[2]))
}

fn channel(channel: i32) -> Result<&'static str, HistoryError> {
    match HistoryChannel::try_from(channel) {
        Ok(HistoryChannel::HistoryPublic) => Ok("PUBLIC"),
        Ok(HistoryChannel::HistoryStaff) => Ok("STAFF"),
        Ok(HistoryChannel::HistoryPrivate) => Ok("PRIVATE"),
        _ => Err(HistoryError::Invalid("unknown channel")),
    }
}

fn uuid(value: &str) -> Result<(), HistoryError> {
    if Uuid::parse_str(value)
        .map_err(|_| HistoryError::Invalid("invalid UUID"))?
        .to_string()
        != value
    {
        return Err(HistoryError::Invalid("noncanonical UUID"));
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
    use super::*;
    use query::{SyncPage, head, sync_page};
    const ALICE: &str = "00000000-0000-4000-8000-000000000001";
    const BOB: &str = "00000000-0000-4000-8000-000000000002";
    const OTHER: &str = "00000000-0000-4000-8000-000000000003";

    fn entry(
        id: &str,
        channel: HistoryChannel,
        participants: &[&str],
        viewers: Option<&[&str]>,
    ) -> Append {
        Append {
            entry: HistoryEntry {
                message_id: id.into(),
                channel: channel.into(),
                participants: participants.iter().map(|s| (*s).into()).collect(),
                restricted: viewers.is_some(),
                viewers: viewers
                    .unwrap_or_default()
                    .iter()
                    .map(|s| (*s).into())
                    .collect(),
                record: Some(wire::ChatRecord {
                    kind: Some(wire::chat_record::Kind::Notice(wire::ChatNoticeRecord {
                        content: Some(wire::chat_notice_record::Content::Body(wire::ChatBody {
                            parts: vec![wire::ChatPart {
                                content: Some(wire::chat_part::Content::Text(id.into())),
                                ..Default::default()
                            }],
                        })),
                        ..Default::default()
                    })),
                    ..Default::default()
                }),
            },
            receipt: None,
        }
    }
    fn audience(viewer: &str, channel: HistoryChannel) -> Audience {
        Audience {
            viewer: viewer.into(),
            staff: false,
            channel,
            target: None,
        }
    }
    fn save(
        connection: &mut Connection,
        mut entry: Append,
        now: i64,
    ) -> Result<HistorySaved, HistoryError> {
        save_one(connection, &mut entry, now)
    }
    fn save_one(
        connection: &mut Connection,
        entry: &mut Append,
        now: i64,
    ) -> Result<HistorySaved, HistoryError> {
        append_entries(connection, std::slice::from_mut(entry), now)?
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
            entry("old", HistoryChannel::HistoryPublic, &[], Some(&[ALICE])),
            1000,
        )
        .unwrap();
        assert_eq!(
            1,
            sync(
                &connection,
                audience(ALICE, HistoryChannel::HistoryPublic),
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
                audience(ALICE, HistoryChannel::HistoryPublic),
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
            entry("ab", HistoryChannel::HistoryPrivate, &[ALICE, BOB], None),
            entry("bo", HistoryChannel::HistoryPrivate, &[BOB, OTHER], None),
            entry("staff", HistoryChannel::HistoryStaff, &[], None),
            entry(
                "restricted",
                HistoryChannel::HistoryPublic,
                &[],
                Some(&[ALICE]),
            ),
            entry("nobody", HistoryChannel::HistoryPublic, &[], Some(&[])),
        ] {
            save(&mut connection, append, 1000).unwrap();
        }
        let private = sync(
            &connection,
            audience(ALICE, HistoryChannel::HistoryPrivate),
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
            audience(ALICE, HistoryChannel::HistoryPublic),
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
                audience(BOB, HistoryChannel::HistoryPublic),
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
                audience(ALICE, HistoryChannel::HistoryStaff),
                None,
                None,
                vec![],
                1000
            )
            .is_err()
        );
    }

    #[test]
    fn durable_deduplication_and_monotonic_order_survive_restart_and_clock_rollback() {
        let directory = tempfile::tempdir().unwrap();
        let path = directory.path().join("history.db");
        let mut connection = Connection::open(&path).unwrap();
        initialize(&mut connection).unwrap();
        let mut first = entry("first", HistoryChannel::HistoryPublic, &[], None);
        first.receipt = Some(SendReceipt {
            sender: ALICE.into(),
            request_id: "request".into(),
            fingerprint: "fingerprint".into(),
        });
        let saved = save_one(&mut connection, &mut first, 2000).unwrap();
        assert!(saved.inserted);
        let scope = audience(ALICE, HistoryChannel::HistoryPublic);
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
        let retried = save_one(&mut connection, &mut first, 1000).unwrap();
        assert!(!retried.inserted);
        assert_eq!(saved.message_id, retried.message_id);
        assert_eq!(saved.order_key, retried.order_key);
        let next = save(
            &mut connection,
            entry("second", HistoryChannel::HistoryPublic, &[], None),
            1000,
        )
        .unwrap();
        assert!(saved.order_key < next.order_key);
        assert_eq!(
            Some(uuid::Version::SortRand),
            Uuid::parse_str(&next.order_key).unwrap().get_version()
        );
        first.receipt.as_mut().unwrap().fingerprint = "different".into();
        assert!(matches!(
            save_one(&mut connection, &mut first, 1000),
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
                entry(
                    &format!("old-{index}"),
                    HistoryChannel::HistoryPublic,
                    &[],
                    None,
                ),
                1000,
            )
            .unwrap();
        }
        let query = audience(ALICE, HistoryChannel::HistoryPublic);
        let first = sync(&connection, query.clone(), None, None, vec![], 1000).unwrap();
        let checkpoint = first.checkpoint().unwrap();
        assert_eq!(91, checkpoint.len());
        assert!(first.has_more);
        save(
            &mut connection,
            entry("new", HistoryChannel::HistoryPublic, &[], None),
            1000,
        )
        .unwrap();
        let token = first
            .continuation(first.rows.last().unwrap().cursor)
            .unwrap();
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
    fn sqlite_failure_never_returns_a_false_commit_or_partial_receipt() {
        let mut connection = database();
        connection.execute_batch("PRAGMA query_only=ON;").unwrap();
        let mut append = entry("not-saved", HistoryChannel::HistoryPublic, &[], None);
        append.receipt = Some(SendReceipt {
            sender: ALICE.into(),
            request_id: "request".into(),
            fingerprint: "fingerprint".into(),
        });
        assert!(matches!(
            save_one(&mut connection, &mut append, 1000),
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
            let mut append = entry(id, HistoryChannel::HistoryPublic, &[], None);
            append.receipt = Some(SendReceipt {
                sender: ALICE.into(),
                request_id: request.into(),
                fingerprint: fingerprint.into(),
            });
            append
        };
        let mut entries = [
            with_receipt("first", "request", "same"),
            with_receipt("retry", "request", "same"),
            with_receipt("conflict", "request", "different"),
            with_receipt("rolled-back", "fail", "same"),
            with_receipt("last", "new-request", "same"),
        ];
        let results = append_entries(&mut connection, &mut entries, 1000).unwrap();
        let first = results[0].as_ref().unwrap();
        let retry = results[1].as_ref().unwrap();
        let last = results[4].as_ref().unwrap();
        assert!(first.inserted && last.inserted && !retry.inserted);
        assert_eq!(first.message_id, retry.message_id);
        assert_eq!(first.order_key, retry.order_key);
        assert!(first.order_key < last.order_key);
        assert!(matches!(results[2], Err(HistoryError::Conflict)));
        assert!(matches!(results[3], Err(HistoryError::Database(_))));
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
        let mut appends = (0..MESSAGES)
            .map(|sequence| {
                entry(
                    &format!("msg-{sequence}"),
                    HistoryChannel::HistoryPublic,
                    &[],
                    None,
                )
            })
            .collect::<Vec<_>>();
        for size in [1, 16] {
            let directory = tempfile::tempdir_in(&root).unwrap();
            let mut connection = Connection::open(directory.path().join("chat.db")).unwrap();
            initialize(&mut connection).unwrap();
            connection.execute_batch("PRAGMA journal_mode=WAL").unwrap();
            let start = std::time::Instant::now();
            for group in appends.chunks_mut(size) {
                let results = append_entries(&mut connection, group, 1000).unwrap();
                assert!(results.into_iter().all(|saved| saved.unwrap().inserted));
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
        let public = HistoryChannel::HistoryPublic;
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
