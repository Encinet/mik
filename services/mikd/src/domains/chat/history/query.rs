use super::{HistoryError, PAGE_BYTES, PAGE_SIZE, RETENTION_MS, channel, uuid};
use crate::domains::chat::cursor::{Cursors, Position};
use mik_protocol::wire::{ChatRecord, HistoryChannel};
use prost::Message;
use std::sync::Arc;
use tokio_rusqlite::rusqlite::{Connection, params, types::ValueRef};

#[derive(Clone)]
pub(in crate::domains::chat) struct Audience {
    pub(in crate::domains::chat) viewer: String,
    pub(in crate::domains::chat) staff: bool,
    pub(in crate::domains::chat) channel: HistoryChannel,
    pub(in crate::domains::chat) target: Option<String>,
}

pub(in crate::domains::chat) struct SyncRequest {
    pub(in crate::domains::chat) audience: Audience,
    pub(in crate::domains::chat) since: Option<String>,
    pub(in crate::domains::chat) continuation: Option<String>,
    pub(in crate::domains::chat) hidden: Vec<String>,
}

pub(in crate::domains::chat) struct Row {
    pub(in crate::domains::chat) cursor: i64,
    pub(in crate::domains::chat) expires_at: i64,
    pub(in crate::domains::chat) order_key: String,
    pub(in crate::domains::chat) message_id: String,
    pub(in crate::domains::chat) record: ChatRecord,
}

pub(in crate::domains::chat) struct SyncPage {
    pub(in crate::domains::chat) rows: Vec<Row>,
    pub(in crate::domains::chat) has_more: bool,
    pub(in crate::domains::chat) reset: bool,
    position: Position,
    binding: String,
    cursors: Arc<Cursors>,
}

impl SyncPage {
    pub(in crate::domains::chat) fn checkpoint(&self) -> Result<String, HistoryError> {
        self.cursors.seal(
            Position {
                after: self.position.head,
                head: self.position.head,
                before: i64::MAX,
            },
            &self.binding,
        )
    }

    pub(in crate::domains::chat) fn continuation(
        &self,
        before: i64,
    ) -> Result<String, HistoryError> {
        self.cursors.seal(
            Position {
                before,
                ..self.position
            },
            &self.binding,
        )
    }
}

pub(super) fn head(connection: &Connection) -> Result<i64, HistoryError> {
    Ok(connection.query_row(
        "SELECT COALESCE((SELECT seq FROM sqlite_sequence WHERE name='chat_messages'),0)",
        [],
        |row| row.get(0),
    )?)
}

pub(super) fn sync_page(
    connection: &Connection,
    request: SyncRequest,
    cursors: Arc<Cursors>,
    now: i64,
) -> Result<SyncPage, HistoryError> {
    let SyncRequest {
        audience,
        since,
        continuation,
        mut hidden,
    } = request;
    uuid(&audience.viewer)?;
    let channel = channel(audience.channel.into())?;
    if (audience.channel != HistoryChannel::HistoryPrivate && audience.target.is_some())
        || audience.target.as_ref() == Some(&audience.viewer)
        || (audience.channel == HistoryChannel::HistoryStaff && !audience.staff)
    {
        return Err(HistoryError::Invalid(
            "invalid or unauthorized history query",
        ));
    }
    if let Some(target) = &audience.target {
        uuid(target)?;
    }
    if hidden.len() > 4096 {
        return Err(HistoryError::Invalid("visibility scope too large"));
    }
    for id in &hidden {
        uuid(id)?;
    }
    hidden.sort();
    hidden.dedup();
    let binding = format!(
        "mik-history-cursor\0{}\0{channel}\0{}\0{}",
        audience.viewer,
        audience.target.as_deref().unwrap_or("null"),
        hidden.join("\n"),
    );
    let current_head = head(connection)?;
    let token = continuation.as_deref().or(since.as_deref());
    let position = cursors
        .open(token, &binding)
        .filter(|position| position.head <= current_head);
    let reset = token.is_some() && position.is_none();
    let position = match position {
        Some(position) if continuation.is_some() => position,
        previous => Position {
            after: previous.map_or(0, |previous| previous.head),
            head: current_head,
            before: i64::MAX,
        },
    };
    let (rows, has_more) = read_page(connection, &audience, channel, position, now)?;
    Ok(SyncPage {
        rows,
        has_more,
        reset,
        position,
        binding,
        cursors,
    })
}

fn read_page(
    connection: &Connection,
    audience: &Audience,
    channel: &str,
    position: Position,
    now: i64,
) -> Result<(Vec<Row>, bool), HistoryError> {
    let mut statement = connection.prepare_cached("SELECT cursor,record,recorded_at,order_key,message_id FROM chat_messages
        WHERE channel=?1 AND cursor<?2 AND recorded_at>?3 AND cursor>?4 AND cursor<=?5
        AND (restricted=0 OR EXISTS(SELECT 1 FROM chat_viewers WHERE message_cursor=cursor AND viewer=?6))
        AND (?1!='PRIVATE' OR ((private_first=?6 OR private_second=?6) AND (?7 IS NULL OR private_first=?7 OR private_second=?7)))
        ORDER BY cursor DESC LIMIT 257")?;
    let mut results = statement.query(params![
        channel,
        position.before,
        now - RETENTION_MS,
        position.after,
        position.head,
        audience.viewer,
        audience.target,
    ])?;
    let mut rows = Vec::new();
    let mut bytes = 0;
    while let Some(row) = results.next()? {
        let ValueRef::Blob(encoded) = row.get_ref(1)? else {
            return Err(HistoryError::Invalid("invalid stored history record"));
        };
        if encoded.len() > PAGE_BYTES {
            return Err(HistoryError::Invalid("oversized stored history row"));
        }
        if rows.len() == PAGE_SIZE || (!rows.is_empty() && bytes + encoded.len() > PAGE_BYTES) {
            return Ok((rows, true));
        }
        bytes += encoded.len();
        rows.push(Row {
            cursor: row.get(0)?,
            record: ChatRecord::decode(encoded)
                .map_err(|_| HistoryError::Invalid("invalid stored chat record"))?,
            message_id: row.get(4)?,
            expires_at: row
                .get::<_, i64>(2)?
                .checked_add(RETENTION_MS)
                .ok_or(HistoryError::Invalid("invalid stored recording time"))?,
            order_key: row.get(3)?,
        });
    }
    Ok((rows, false))
}
