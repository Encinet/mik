use super::{
    HistoryError, PAGE_BYTES, PAGE_SIZE, RETENTION_MS,
    cursor::{Cursors, Position},
    uuid,
};
use crate::domains::chat::model::Channel;
use mik_protocol::wire::ChatRecord;
use prost::Message;
use serde::Serialize;
use std::sync::Arc;
use tokio_rusqlite::rusqlite::{Connection, params, types::ValueRef};

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub(in crate::domains::chat) struct PostedReceipt {
    request_id: String,
    message_id: String,
    #[serde(skip)]
    private_peer: Option<String>,
}

impl PostedReceipt {
    pub(in crate::domains::chat) fn visible_to(&self, hidden: &[String]) -> bool {
        self.private_peer
            .as_ref()
            .is_none_or(|id| !hidden.contains(id))
    }
}

pub(super) fn posted_receipts(
    db: &Connection,
    viewer: &str,
    staff: bool,
    ids: &[String],
    now: i64,
) -> Result<Vec<PostedReceipt>, HistoryError> {
    let ids = serde_json::to_string(ids).map_err(std::io::Error::other)?;
    let mut statement = db.prepare_cached(
        "SELECT r.request_id, m.message_id,
                CASE WHEN m.private_first=?1 THEN m.private_second ELSE m.private_first END
         FROM chat_requests r JOIN chat_messages m ON m.cursor=r.message_cursor
         WHERE r.sender=?1 AND r.request_id IN (SELECT value FROM json_each(?2))
           AND m.recorded_at>?3 AND (m.channel!='staff' OR ?4)
           AND (m.channel!='private' OR m.private_first=?1 OR m.private_second=?1)
           AND (m.restricted=0 OR EXISTS (SELECT 1 FROM chat_viewers v
                WHERE v.message_cursor=m.cursor AND v.viewer=?1))",
    )?;
    let rows = statement.query_map(params![viewer, ids, now - RETENTION_MS, staff], |row| {
        Ok(PostedReceipt {
            request_id: row.get(0)?,
            message_id: row.get(1)?,
            private_peer: row.get(2)?,
        })
    })?;
    rows.collect::<Result<_, _>>().map_err(Into::into)
}

#[derive(Clone)]
pub(in crate::domains::chat) struct Audience {
    pub(in crate::domains::chat) viewer: String,
    pub(in crate::domains::chat) staff: bool,
    pub(in crate::domains::chat) channel: Channel,
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
    pub(in crate::domains::chat) restricted: bool,
}

pub(in crate::domains::chat) struct SyncPage {
    pub(in crate::domains::chat) rows: Vec<Row>,
    pub(in crate::domains::chat) has_more: bool,
    pub(in crate::domains::chat) reset: bool,
    pub(in crate::domains::chat) from_checkpoint: bool,
    position: Position,
    binding: String,
    cursors: Arc<Cursors>,
}

/// A complete, unfiltered channel snapshot taken while this socket was subscribed.
pub(in crate::domains::chat) struct LiveAnchor {
    pub(in crate::domains::chat) channel: &'static str,
    pub(in crate::domains::chat) hidden: Vec<String>,
    pub(in crate::domains::chat) token: String,
    pub(in crate::domains::chat) through: i64,
    binding: String,
    cursors: Arc<Cursors>,
}

impl LiveAnchor {
    pub(in crate::domains::chat) fn advance(
        &mut self,
        cursor: i64,
    ) -> Result<String, HistoryError> {
        let token = self.cursors.seal(
            Position {
                after: cursor,
                head: cursor,
                before: i64::MAX,
            },
            &self.binding,
        )?;
        self.through = cursor;
        Ok(std::mem::replace(&mut self.token, token))
    }
}

impl SyncPage {
    pub(in crate::domains::chat) fn into_live_anchor(
        self,
        channel: Channel,
        hidden: &[String],
        token: String,
    ) -> LiveAnchor {
        LiveAnchor {
            channel: channel.name(),
            hidden: hidden.to_vec(),
            token,
            through: self.position.head,
            binding: self.binding,
            cursors: self.cursors,
        }
    }
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
    if since.is_some() && continuation.is_some() {
        return Err(HistoryError::Invalid("multiple history cursors"));
    }
    uuid(&audience.viewer)?;
    let channel = audience.channel.name();
    if (audience.channel != Channel::Private && audience.target.is_some())
        || audience.target.as_ref() == Some(&audience.viewer)
        || (audience.channel == Channel::Staff && !audience.staff)
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
    let position = cursors.open(token, &binding).filter(|position| {
        position.head <= current_head
            && if continuation.is_some() {
                position.before != i64::MAX
            } else {
                position.before == i64::MAX && position.after == position.head
            }
    });
    let reset = token.is_some() && position.is_none();
    let from_checkpoint = continuation.is_none() && position.is_some();
    let position = match position {
        Some(position) if continuation.is_some() => position,
        previous => Position {
            after: previous.map_or(0, |previous| previous.head),
            head: current_head,
            before: i64::MAX,
        },
    };
    let (rows, has_more) = if position.after == current_head {
        (Vec::new(), false)
    } else {
        read_page(connection, &audience, channel, position, now)?
    };
    Ok(SyncPage {
        rows,
        has_more,
        reset,
        from_checkpoint,
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
    let mut statement = connection.prepare_cached("SELECT cursor,record,recorded_at,order_key,message_id,restricted FROM chat_messages
        WHERE channel=?1 AND cursor<?2 AND recorded_at>?3 AND cursor>?4 AND cursor<=?5
        AND (restricted=0 OR EXISTS(SELECT 1 FROM chat_viewers WHERE message_cursor=cursor AND viewer=?6))
        AND (?1!='private' OR ((private_first=?6 OR private_second=?6) AND (?7 IS NULL OR private_first=?7 OR private_second=?7)))
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
            restricted: row.get(5)?,
            expires_at: row
                .get::<_, i64>(2)?
                .checked_add(RETENTION_MS)
                .ok_or(HistoryError::Invalid("invalid stored recording time"))?,
            order_key: row.get(3)?,
        });
    }
    Ok((rows, false))
}
