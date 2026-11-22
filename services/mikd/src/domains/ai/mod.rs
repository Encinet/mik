//! Player assistant boundary for daemon startup, Paper IPC, and social commands.

mod agent;
mod assistant;
mod config;
mod conversation;
mod diagnostics;
mod knowledge;
mod memory;
mod model;
mod reference;
mod reply;

#[cfg(test)]
mod eval;

pub(crate) use assistant::{Assistant, DeliveryReceipt, SocialSurface};
pub use config::AiConfig;
pub(crate) use conversation::Conversation;
#[cfg(test)]
pub(in crate::domains) use reply::sample;
pub(crate) use reply::{Layout, VisibleReply};

use std::sync::{Arc, OnceLock};

#[derive(Clone, Default)]
pub(crate) struct AssistantSlot {
    configured: bool,
    ready: Arc<OnceLock<Option<Arc<Assistant>>>>,
}

#[derive(Clone, Copy)]
pub(crate) enum AssistantState<'a> {
    Disabled,
    Starting,
    Failed,
    Ready(&'a Assistant),
}

impl AssistantSlot {
    pub(crate) fn new(configured: bool) -> Self {
        Self {
            configured,
            ..Self::default()
        }
    }

    pub(crate) fn state(&self) -> AssistantState<'_> {
        if !self.configured {
            return AssistantState::Disabled;
        }
        match self.ready.get() {
            None => AssistantState::Starting,
            Some(None) => AssistantState::Failed,
            Some(Some(assistant)) => AssistantState::Ready(assistant),
        }
    }

    pub(crate) const fn configured(&self) -> bool {
        self.configured
    }

    pub(crate) fn finish(&self, assistant: Option<Arc<Assistant>>) {
        drop(self.ready.set(assistant));
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum TurnError {
    Busy,
    Failed,
    Rejected,
}
