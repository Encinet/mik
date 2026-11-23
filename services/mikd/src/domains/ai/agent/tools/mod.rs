//! Turn-scoped registration and execution shared by all model tools.

mod evidence;
mod knowledge;
mod observation;
mod paper;
mod web;

use super::PaperActor;
use crate::domains::ai::{
    TurnError,
    diagnostics::TurnLog,
    knowledge::{Knowledge, MikWikiLocale, MinecraftWiki, Tavily, WikiEdition},
    model::AiModel,
    reference::Reference,
};
use crate::game::GameBridge;
use evidence::Evidence;
use observation::ToolObservation;
use rig_agent::tool::{DynamicTool, ToolOutput};
use rig_core::message::ToolName;
use serde_json::{Value, json};
use std::{
    sync::{
        Arc, Mutex,
        atomic::{AtomicBool, AtomicUsize, Ordering},
    },
    time::{Duration, Instant},
};
use tokio::sync::Semaphore;

const TOOL_CALLS: usize = 10;
const TOOL_TIME: Duration = Duration::from_secs(15);
const OBSERVATION_WINDOW: Duration = Duration::from_secs(60);
const TOOL_OUTPUT_BYTES: usize = 12 * 1024;

pub(super) struct Tools {
    knowledge: Knowledge,
    minecraft: MinecraftWiki,
    web_search: Option<Tavily>,
    game: Arc<GameBridge>,
    knowledge_slots: Arc<Semaphore>,
}

impl Tools {
    pub(super) fn new(
        knowledge: Knowledge,
        minecraft: MinecraftWiki,
        web_search: Option<Tavily>,
        game: Arc<GameBridge>,
    ) -> Self {
        Self {
            knowledge,
            minecraft,
            web_search,
            game,
            knowledge_slots: Arc::new(Semaphore::new(2)),
        }
    }

    pub(super) async fn refresh_wiki(&self) {
        tokio::join!(self.knowledge.refresh_wiki(), self.minecraft.refresh_due());
    }

    pub(super) const fn web_search_enabled(&self) -> bool {
        self.web_search.is_some()
    }
}

pub(super) struct Observations {
    tools: Arc<Tools>,
    mik_wiki_locale: MikWikiLocale,
    minecraft_edition: WikiEdition,
    actor: Option<PaperActor>,
    started: Instant,
    calls: AtomicUsize,
    web_request: Option<WebRequest>,
    searched_web: AtomicBool,
    evidence: Mutex<Evidence>,
}

pub(super) struct WebRequest {
    pub(super) model: AiModel,
    pub(super) question: String,
    pub(super) log: TurnLog,
}

impl Observations {
    pub(super) fn new(
        tools: Arc<Tools>,
        locale: &str,
        actor: Option<PaperActor>,
        started: Instant,
        web_request: Option<WebRequest>,
    ) -> Self {
        Self {
            tools,
            mik_wiki_locale: MikWikiLocale::from_locale(locale),
            minecraft_edition: WikiEdition::from_locale(locale),
            actor,
            started,
            calls: AtomicUsize::new(0),
            web_request,
            searched_web: AtomicBool::new(false),
            evidence: Mutex::default(),
        }
    }

    pub(super) fn references(&self) -> Vec<Reference> {
        self.evidence
            .lock()
            .map(|evidence| evidence.references().to_vec())
            .unwrap_or_default()
    }

    pub(super) fn limits_reached(&self) -> bool {
        self.calls.load(Ordering::Relaxed) >= TOOL_CALLS
            || self.started.elapsed() >= OBSERVATION_WINDOW
    }

    pub(super) fn available_tools(self: &Arc<Self>) -> Result<Vec<DynamicTool>, TurnError> {
        let mut available = Vec::new();
        knowledge::register(self, &mut available)?;
        web::register(self, &mut available)?;
        paper::register(self, &mut available)?;
        Ok(available)
    }

    fn bind<Invoke, Work>(
        self: &Arc<Self>,
        name: &str,
        description: &str,
        parameters: Value,
        invoke: Invoke,
    ) -> Result<DynamicTool, TurnError>
    where
        Invoke: Fn(Arc<Self>, Value) -> Work + Clone + Send + Sync + 'static,
        Work: Future<Output = ToolObservation> + Send + 'static,
    {
        let run = self.clone();
        let name = ToolName::new(name).map_err(|_| TurnError::Failed)?;
        Ok(DynamicTool::new(
            name,
            description,
            parameters,
            move |args| {
                let run = run.clone();
                let invoke = invoke.clone();
                Box::pin(async move {
                    let observed = run.observe(async { invoke(run.clone(), args).await }).await;
                    Ok(ToolOutput::json(observed))
                })
            },
        ))
    }

    async fn observe(&self, work: impl Future<Output = ToolObservation>) -> Value {
        let remaining = OBSERVATION_WINDOW.saturating_sub(self.started.elapsed());
        let observation = if self.calls.fetch_add(1, Ordering::Relaxed) >= TOOL_CALLS {
            ToolObservation::unavailable("tool budget reached")
        } else if remaining.is_zero() {
            ToolObservation::unavailable("observation time ended")
        } else {
            tokio::time::timeout(TOOL_TIME.min(remaining), work)
                .await
                .unwrap_or_else(|_| ToolObservation::unavailable("tool timed out"))
        };
        observation.for_model(&self.evidence, TOOL_OUTPUT_BYTES)
    }
}

fn empty_schema() -> Value {
    json!({"type": "object", "properties": {}, "additionalProperties": false, "required": []})
}

fn no_arguments(args: &Value) -> bool {
    args.as_object().is_some_and(serde_json::Map::is_empty)
}
