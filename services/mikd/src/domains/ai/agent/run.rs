//! One bounded model/tool loop with one player-facing reply.

use super::tools::Observations;
use crate::domains::ai::{
    TurnError,
    diagnostics::TurnLog,
    model::{AiModel, MAX_MODEL_OUTPUT},
    reply,
};
use futures_util::FutureExt;
use mik_protocol::wire::AssistantReply;
use rig_agent::{
    AgentBuilder, AgentHook, HookContext,
    agent::{CompletionCallAction, CompletionCallEvent, ModelTurnAction, ModelTurnFinished},
    completion::PromptError,
    run::patch::RequestPatch,
};
use rig_core::{
    completion::FinishReason,
    message::{AssistantContent, ToolChoice},
};
use serde_json::Value;
use std::{
    future::{Future, ready},
    sync::Arc,
    time::Duration,
};

const COMPLETIONS: usize = 6;
const PROVIDER_FILTERED: &str = "provider_filtered";
const TRUNCATED_TOOL_CALL: &str = "truncated_tool_call";

pub(super) struct Run<'a> {
    pub(super) model: &'a AiModel,
    pub(super) log: TurnLog,
    pub(super) observations: Arc<Observations>,
}

impl Run<'_> {
    pub(super) async fn complete(
        self,
        preamble: String,
        input: Value,
        question: &str,
        budget: Duration,
    ) -> Result<AssistantReply, TurnError> {
        let mut builder = AgentBuilder::new(self.model.clone())
            .name("mik_player_assistant")
            .preamble(preamble)
            .max_tokens(MAX_MODEL_OUTPUT)
            .record_content_telemetry(false);
        for tool in self.observations.available_tools()? {
            builder = builder.dynamic_tool(tool);
        }
        let response = tokio::time::timeout(
            budget,
            builder
                .build()
                .prompt(input.to_string())
                .max_turns(COMPLETIONS)
                .max_invalid_tool_call_retries(1)
                .tool_concurrency(1)
                .add_hook(TurnHooks {
                    observations: self.observations.clone(),
                    log: self.log,
                })
                .run()
                .boxed(),
        )
        .await;

        match response {
            Ok(Ok(response)) => {
                let references = self.observations.references();
                if let Some(projection) = reply::project(&response.output(), question, &references)
                {
                    if projection.omitted {
                        self.log.event("completion", "omitted_content");
                    }
                    return Ok(projection.reply);
                }
                self.log.event("completion", "no_valid_content");
                Err(TurnError::Failed)
            }
            Ok(Err(PromptError::Report(report))) => {
                self.log.provider("completion", &report);
                Err(TurnError::Failed)
            }
            Ok(Err(PromptError::Provider(error))) => {
                self.log.provider("completion", &error.report());
                Err(TurnError::Failed)
            }
            outcome => {
                let reason = match outcome {
                    Err(_) => "deadline_exceeded",
                    Ok(Err(PromptError::MaxTurns { .. })) => "turn_limit",
                    Ok(Err(PromptError::UnknownToolCall { .. })) => "unknown_tool_call",
                    Ok(Err(PromptError::Cancelled { reason, .. })) => match reason.as_str() {
                        PROVIDER_FILTERED => PROVIDER_FILTERED,
                        TRUNCATED_TOOL_CALL => TRUNCATED_TOOL_CALL,
                        _ => "agent_cancelled",
                    },
                    _ => "agent_failed",
                };
                self.log.event("completion", reason);
                Err(TurnError::Failed)
            }
        }
    }
}

struct TurnHooks {
    observations: Arc<Observations>,
    log: TurnLog,
}

impl AgentHook for TurnHooks {
    fn on_completion_call(
        &self,
        _: &HookContext,
        event: CompletionCallEvent<'_>,
    ) -> impl Future<Output = CompletionCallAction> + Send {
        if event.turn >= COMPLETIONS || self.observations.limits_reached() {
            return ready(CompletionCallAction::patch(
                RequestPatch::new().tool_choice(ToolChoice::None),
            ));
        }
        ready(CompletionCallAction::continue_run())
    }

    fn on_model_turn_finished(
        &self,
        _: &HookContext,
        event: ModelTurnFinished<'_>,
    ) -> impl Future<Output = ModelTurnAction> + Send {
        let has_calls = event
            .content
            .iter()
            .any(|part| matches!(part, AssistantContent::ToolCall(_)));
        match event.finish_reason {
            Some(FinishReason::ContentFilter) => {
                return ready(ModelTurnAction::stop(PROVIDER_FILTERED));
            }
            Some(FinishReason::Length) if has_calls => {
                return ready(ModelTurnAction::stop(TRUNCATED_TOOL_CALL));
            }
            Some(FinishReason::Length) => self.log.event("completion", "output_limit"),
            _ => {}
        }
        ready(ModelTurnAction::continue_run())
    }
}
