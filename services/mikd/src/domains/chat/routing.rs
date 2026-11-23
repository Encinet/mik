use super::service::Committed;
use mik_protocol::wire::{
    ChatUserRecord, HistoryChannel, HistoryEntry, chat_notice_record, chat_presence_notice,
    chat_record::Kind, chat_user_record::Source,
};

impl Committed {
    pub(crate) fn public_post(&self) -> Option<(&str, &ChatUserRecord)> {
        if self.entry.channel() != HistoryChannel::HistoryPublic || self.entry.restricted {
            return None;
        }
        match self.entry.record.as_ref()?.kind.as_ref()? {
            Kind::User(user) => Some((&self.entry.message_id, user)),
            Kind::Notice(_) => None,
        }
    }

    pub(crate) fn paper_delivery(&self) -> Option<HistoryEntry> {
        match self.entry.record.as_ref()?.kind.as_ref()? {
            Kind::User(user) if matches!(user.source(), Source::Web | Source::Social) => {
                Some(self.entry.clone())
            }
            Kind::Notice(notice)
                if matches!(notice.content.as_ref(), Some(chat_notice_record::Content::Presence(presence))
                if matches!(presence.kind(), chat_presence_notice::Kind::WebJoined | chat_presence_notice::Kind::WebLeft)) =>
            {
                Some(self.entry.clone())
            }
            _ => None,
        }
    }

    pub(super) fn visible_to(&self, viewer: &str, staff: bool) -> bool {
        (!self.entry.restricted || self.entry.viewers.iter().any(|id| id == viewer))
            && match self.entry.channel() {
                HistoryChannel::HistoryPublic => true,
                HistoryChannel::HistoryStaff => staff,
                HistoryChannel::HistoryPrivate => {
                    self.entry.participants.iter().any(|id| id == viewer)
                }
                HistoryChannel::Unspecified => false,
            }
    }
}
