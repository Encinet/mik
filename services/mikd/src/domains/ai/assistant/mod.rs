//! Turn lifecycle and context commit, shared by the Paper and social entry points.

mod paper;
mod receipt;
mod social;

pub(crate) use receipt::DeliveryReceipt;
pub(crate) use social::SocialSurface;

use super::{
    AiConfig, Conversation, TurnError,
    agent::{Agent, TurnTarget},
    conversation::{ActiveSession, RecentExchanges, SessionRecord, Sessions},
    diagnostics::TurnLog,
    knowledge::Knowledge,
    memory::PlayerMemory,
    model::TurnCapacity,
};
use crate::game::GameBridge;
use mik_protocol::wire::AssistantReply;
use paper::Jobs;
use std::{io, sync::Arc};
use tokio::sync::OwnedSemaphorePermit;

const PROMPT_LIMIT: u32 = 3000;

pub(crate) struct Assistant {
    agent: Arc<Agent>,
    sessions: Sessions,
    turns: TurnCapacity,
    paper: Jobs,
    memory: Arc<PlayerMemory>,
}

struct ActiveTurn {
    agent: Arc<Agent>,
    session: ActiveSession,
    slot: OwnedSemaphorePermit,
}

impl ActiveTurn {
    async fn answer(
        self,
        target: TurnTarget,
        locale: &str,
        question: &str,
        log: TurnLog,
    ) -> Result<(AssistantReply, SessionRecord), TurnError> {
        let previous = self.session.snapshot().unwrap_or_else(|_| {
            log.event("context", "snapshot_unavailable");
            RecentExchanges::new()
        });
        let answer = self
            .agent
            .answer(target, locale, question, previous, log)
            .await?;
        drop(self.slot);
        Ok((answer, self.session.finish()))
    }
}

impl Assistant {
    pub(crate) async fn open(config: AiConfig, game: Arc<GameBridge>) -> io::Result<Self> {
        let knowledge = tokio::task::spawn_blocking(move || {
            Knowledge::open(Some(&config.knowledge_directory), config.mik_wiki)
        })
        .await
        .map_err(io::Error::other)??;
        Ok(Self {
            agent: Arc::new(Agent::new(
                config.models.interactive,
                knowledge,
                config.minecraft,
                config.web_search,
                game,
            )),
            sessions: Sessions::default(),
            paper: Jobs::new(),
            turns: config.models.capacity,
            memory: Arc::new(
                PlayerMemory::open(config.memory_database, config.models.background).await,
            ),
        })
    }

    pub(crate) async fn close(&self) {
        self.turns.close();
        self.paper.close().await;
        self.memory.close().await;
    }

    pub(crate) fn maintain(&self) {
        self.paper.maintain();
        self.sessions.maintain();
    }

    #[cfg(test)]
    pub(in crate::domains::ai) fn memory_notes(
        &self,
        instance: &str,
        player: uuid::Uuid,
    ) -> Option<Vec<String>> {
        self.memory.recalled(instance, player)
    }

    pub(crate) async fn refresh_wiki(&self) {
        self.agent.refresh_wiki().await;
    }

    fn begin(&self, conversation: Conversation) -> Result<ActiveTurn, TurnError> {
        let slot = self.turns.enter().ok_or(TurnError::Busy)?;
        let session = self.sessions.enter(conversation)?;
        Ok(ActiveTurn {
            agent: self.agent.clone(),
            session,
            slot,
        })
    }
}

fn prompt_too_long(question: &str) -> bool {
    question.chars().nth(PROMPT_LIMIT as usize).is_some()
}

fn safe_locale(locale: String) -> String {
    if (1..=35).contains(&locale.len())
        && locale
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || byte == b'-')
    {
        locale
    } else {
        "en-US".into()
    }
}
