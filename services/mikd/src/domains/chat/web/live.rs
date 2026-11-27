mod inbox;
pub(super) use inbox::{Inbox, Item};

use super::{
    record::{Record, SharedRecord},
    render::{self, MessageView, ProjectedMessage},
};
use crate::domains::chat::{
    Committed,
    model::{Channel, Destination, destination},
    presence::GameView,
};
use mik_protocol::wire::ChatIdentity;
use serde::Serialize;
use serde_json::Value;
use std::{
    io,
    sync::{Arc, OnceLock},
};
use uuid::Uuid;

/// All eligible inboxes share the committed event and its prepared wire variants.
pub(super) struct LiveEvent {
    committed: Arc<Committed>,
    message: OnceLock<Option<LiveMessage>>,
}

struct LiveMessage {
    projection: ProjectedMessage,
    // Indexed only by the two fields that vary between viewers of this event.
    variants: [[OnceLock<io::Result<SharedRecord>>; 2]; 2],
}

#[derive(Serialize)]
#[serde(tag = "type", rename_all = "camelCase")]
enum Frame<'a> {
    Message { message: Fields<'a> },
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct Fields<'a> {
    #[serde(flatten)]
    message: &'a Value,
    #[serde(skip_serializing_if = "Option::is_none")]
    mentioned: Option<bool>,
    #[serde(skip_serializing_if = "Option::is_none")]
    request_id: Option<&'a str>,
}

pub(super) struct Delivery<'a> {
    pub(super) channel: Channel,
    pub(super) cursor: i64,
    record: &'a SharedRecord,
}

impl Delivery<'_> {
    pub(super) async fn prepare(
        &self,
        compress: bool,
        permits: impl FnOnce() -> bool,
    ) -> io::Result<Option<Arc<Record>>> {
        let record = self.record.select(compress).await?;
        Ok(permits().then_some(record))
    }
}

impl LiveEvent {
    pub(super) fn new(committed: Arc<Committed>) -> Self {
        Self {
            committed,
            message: OnceLock::new(),
        }
    }

    pub(super) fn destination(&self) -> Option<Destination<'_>> {
        destination(self.committed.entry.record.as_ref()?).ok()
    }

    pub(super) fn cursor(&self) -> i64 {
        self.committed.cursor
    }

    pub(super) fn restricted_to(&self) -> Option<&[String]> {
        self.committed
            .entry
            .visibility
            .as_ref()
            .map(|scope| scope.player_ids.as_slice())
    }

    pub(super) fn for_viewer<'event, 'viewer>(
        &'event self,
        viewer: &'viewer str,
        staff: bool,
    ) -> Option<Recipient<'event, 'viewer>> {
        self.committed
            .visible_to(viewer, staff)
            .then_some(Recipient {
                event: self,
                viewer,
            })
    }
}

/// Only subscribers inside the committed audience reach the game visibility check.
pub(super) struct Recipient<'event, 'viewer> {
    event: &'event LiveEvent,
    viewer: &'viewer str,
}

impl<'event> Recipient<'event, '_> {
    pub(super) fn visible_in(&self, game: &GameView) -> bool {
        self.event
            .committed
            .entry
            .record
            .as_ref()
            .is_some_and(|record| {
                render::visible_to(record, self.viewer, |subject| {
                    game.hidden_player(self.viewer, subject)
                })
            })
    }

    pub(super) fn delivery(
        self,
        hidden: &[String],
        resolve: impl Fn(&ChatIdentity) -> Option<Uuid>,
    ) -> io::Result<Option<Delivery<'event>>> {
        let viewer = self.viewer;
        let event = &self.event.committed;
        let Some(record) = &event.entry.record else {
            return Ok(None);
        };
        if !render::visible_to(record, viewer, |id| hidden.iter().any(|p| p == id)) {
            return Ok(None);
        }
        let message = self.event.message.get_or_init(|| {
            let row = MessageView {
                record,
                message_id: &event.entry.message_id,
                order_key: &event.order_key,
                expires_at: event.expires_at,
            };
            Some(LiveMessage {
                projection: render::project(&row, None)?,
                variants: Default::default(),
            })
        });
        let Some(message) = message else {
            return Ok(None);
        };
        let mentioned = message.projection.mentioned(record, viewer, resolve);
        let receipt = event.receipt.as_ref().filter(|r| r.sender == viewer);
        let variant = &message.variants[usize::from(mentioned)][usize::from(receipt.is_some())];
        let record = variant
            .get_or_init(|| {
                let fields = Fields {
                    message: &message.projection.fields,
                    mentioned: mentioned.then_some(true),
                    request_id: receipt.map(|r| r.request_id.as_str()),
                };
                SharedRecord::new(serde_json::to_vec(&Frame::Message { message: fields })?)
            })
            .as_ref()
            .map_err(|err| io::Error::new(err.kind(), err.to_string()))?;
        Ok(Some(Delivery {
            channel: message.projection.channel,
            cursor: event.cursor,
            record,
        }))
    }
}

#[cfg(test)]
mod tests;
