//! One turn with surface-scoped tools.

mod guards;
mod prompts;
mod run;
mod tools;

use super::{
    TurnError,
    conversation::RecentExchanges,
    diagnostics::TurnLog,
    knowledge::{Knowledge, MinecraftWiki, Tavily},
    model::Interactive,
};
use crate::game::GameBridge;
use mik_protocol::wire::{AssistantReply, AssistantToolDefinition};
use run::Run;
use serde_json::json;
use std::{
    sync::Arc,
    time::{Duration, Instant},
};
use tokio::sync::watch;
use tools::{Observations, Tools, WebRequest};

pub(super) const TURN_BUDGET: Duration = Duration::from_secs(90);
const MODEL_BUDGET: Duration = Duration::from_secs(82);

pub(super) struct PaperActor {
    pub(super) game_instance: String,
    pub(super) player_uuid: String,
    pub(super) turn_id: String,
    pub(super) game_tools: Vec<AssistantToolDefinition>,
}

pub(super) struct Agent {
    model: Interactive,
    tools: Arc<Tools>,
}

pub(super) enum TurnTarget {
    Paper {
        actor: PaperActor,
        memory: Option<Vec<String>>,
    },
    Public,
}

impl Agent {
    pub(super) fn new(
        model: Interactive,
        knowledge: Knowledge,
        minecraft: MinecraftWiki,
        web_search: Option<Tavily>,
        game: Arc<GameBridge>,
    ) -> Self {
        Self {
            model,
            tools: Arc::new(Tools::new(knowledge, minecraft, web_search, game)),
        }
    }

    pub(super) async fn refresh_wiki(&self) {
        self.tools.refresh_wiki().await;
    }

    pub(super) async fn answer(
        &self,
        target: TurnTarget,
        locale: &str,
        question: &str,
        previous: RecentExchanges,
        log: TurnLog,
        summaries: Option<watch::Sender<String>>,
    ) -> Result<AssistantReply, TurnError> {
        let started = Instant::now();
        let preamble = prompts::answer(locale, &target);
        let (actor, memory) = match target {
            TurnTarget::Paper { actor, memory } => (Some(actor), memory),
            TurnTarget::Public => (None, None),
        };
        let mut input = json!({
            "request": question,
            "previous_exchanges": previous,
        });
        if let Some(notes) = &memory {
            input["player_notes"] = json!(notes);
        }
        let web_request = self.tools.web_search_enabled().then(|| WebRequest {
            model: self.model.for_turn(None),
            question: question.to_owned(),
            log,
        });
        let model = self.model.for_turn(summaries);
        let run = Run {
            model: &model,
            log,
            observations: Arc::new(Observations::new(
                self.tools.clone(),
                locale,
                actor,
                started,
                web_request,
            )),
        };
        run.complete(
            preamble,
            input,
            question,
            MODEL_BUDGET.saturating_sub(started.elapsed()),
        )
        .await
    }
}
