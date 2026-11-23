use crate::domains::ai::VisibleReply;
use serde::Serialize;
use std::{
    collections::VecDeque,
    time::{Duration, Instant},
};

const IDLE_TTL: Duration = Duration::from_mins(30);
const RECENT_TURNS: usize = 2;
const QUESTION_CHARS: usize = 500;
const REPLY_CHARS: usize = 2500;

pub(in crate::domains::ai) type RecentExchanges = VecDeque<Exchange>;

pub(super) struct History {
    last_reply: Instant,
    recent: RecentExchanges,
}

#[derive(Clone, Serialize)]
pub(in crate::domains::ai) struct Exchange {
    question: String,
    #[serde(flatten)]
    reply: VisibleReply,
}

impl History {
    pub(super) fn current(&self, now: Instant) -> bool {
        now.duration_since(self.last_reply) < IDLE_TTL
    }

    pub(super) fn snapshot(&self) -> RecentExchanges {
        self.recent.clone()
    }

    pub(super) fn last_reply(&self) -> Instant {
        self.last_reply
    }

    pub(super) fn record(
        context: &mut Option<Self>,
        question: &str,
        reply: VisibleReply,
        now: Instant,
    ) {
        let mut recent = context
            .take()
            .filter(|previous| previous.current(now))
            .map_or_else(VecDeque::new, |previous| previous.recent);
        if recent.len() == RECENT_TURNS {
            recent.pop_front();
        }
        recent.push_back(Exchange {
            question: question.chars().take(QUESTION_CHARS).collect(),
            reply: reply.limit(REPLY_CHARS),
        });
        *context = Some(Self {
            last_reply: now,
            recent,
        });
    }
}
