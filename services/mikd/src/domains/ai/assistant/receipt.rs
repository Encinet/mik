use crate::domains::ai::{
    conversation::SessionRecord, diagnostics::TurnLog, memory::PlayerMemory, reply::VisibleReply,
};
use std::sync::Arc;
use uuid::Uuid;

pub(super) struct PaperMemory {
    pub(super) store: Arc<PlayerMemory>,
    pub(super) instance: String,
    pub(super) player: Uuid,
    pub(super) log: TurnLog,
}

struct Update {
    question: String,
    visible: VisibleReply,
    memory: Option<PaperMemory>,
}

#[must_use]
pub(crate) struct DeliveryReceipt {
    session: SessionRecord,
    update: Option<Box<Update>>,
}

impl DeliveryReceipt {
    pub(super) fn new(
        session: SessionRecord,
        question: String,
        visible: VisibleReply,
        memory: Option<PaperMemory>,
    ) -> Self {
        Self {
            session,
            update: (!visible.as_str().trim().is_empty()).then(|| {
                Box::new(Update {
                    question,
                    visible,
                    memory,
                })
            }),
        }
    }

    pub(crate) fn delivered(self) {
        self.delivered_as(None);
    }

    pub(crate) fn delivered_as(self, actual: Option<VisibleReply>) {
        let Some(update) = self.update else { return };
        let recorded = self
            .session
            .record(&update.question, actual.unwrap_or(update.visible));
        if let Some(memory) = update.memory
            && !matches!(recorded, Ok(false))
        {
            if recorded.is_err() {
                memory.log.event("context", "history_unavailable");
            }
            memory
                .store
                .delivered(memory.instance, memory.player, update.question, memory.log);
        }
    }
}
