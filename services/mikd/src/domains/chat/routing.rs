use super::{
    HistoryError,
    history::SendReceipt,
    model::{Destination, destination, visibility_allows},
};
use mik_protocol::wire::{ChatEvent, ChatUserRecord, chat_record::Kind};

pub(crate) struct Committed {
    pub(super) cursor: i64,
    pub(crate) source: Option<ConversationAddress>,
    pub(super) entry: ChatEvent,
    pub(super) order_key: String,
    pub(super) expires_at: i64,
    pub(super) receipt: Option<SendReceipt>,
}

pub(crate) struct ConversationAddress {
    pub(crate) namespace: String,
    pub(crate) id: String,
}

pub(crate) struct NativeEvent {
    pub(crate) source: ConversationAddress,
    pub(crate) event_scope: String,
    pub(crate) event_id: String,
}

impl NativeEvent {
    pub(super) fn key(&self, platform: &str) -> Result<String, HistoryError> {
        let parts = [platform, &self.event_scope, &self.event_id];
        if parts
            .into_iter()
            .chain([self.source.id.as_str()])
            .any(|s| s.is_empty() || s.len() > 2048 || s.contains('\0'))
            || self.source.namespace.len() > 2048
            || self.source.namespace.contains('\0')
        {
            return Err(HistoryError::Invalid("invalid native chat event"));
        }
        Ok(parts.join("\0"))
    }

    pub(super) fn size(&self) -> usize {
        self.source.namespace.len()
            + self.source.id.len()
            + self.event_scope.len()
            + self.event_id.len()
    }
}

fn public_user(entry: &ChatEvent) -> Option<&ChatUserRecord> {
    let record = entry.record.as_ref()?;
    if entry.visibility.is_some() || !matches!(destination(record), Ok(Destination::Public)) {
        return None;
    }
    match record.kind.as_ref()? {
        Kind::User(user) => Some(user),
        Kind::Presence(_) => None,
    }
}

impl Committed {
    pub(crate) fn public_post(&self) -> Option<(&str, &str, &ChatUserRecord)> {
        Some((
            &self.entry.message_id,
            &self.entry.record.as_ref()?.platform,
            public_user(&self.entry)?,
        ))
    }

    pub(crate) fn paper_event(&self) -> Option<&ChatEvent> {
        (self.entry.record.as_ref()?.platform != "game").then_some(&self.entry)
    }

    pub(super) fn visible_to(&self, viewer: &str, staff: bool) -> bool {
        visibility_allows(self.entry.visibility.as_ref(), viewer)
            && self
                .entry
                .record
                .as_ref()
                .and_then(|record| destination(record).ok())
                .is_some_and(|destination| destination.includes(viewer, staff))
    }
}
