use super::{
    Observations, ToolObservation,
    observation::{ObservedRecord, label},
};
use crate::{
    domains::ai::{TurnError, agent::PaperActor, knowledge::LookupStatus, reference::Reference},
    game::{GameBridge, GameResult, Operation},
};
use mik_protocol::wire::{
    AssistantToolCall, AssistantToolDefinition, AssistantToolObservation, AssistantToolResult,
    assistant_tool_failure::Reason, assistant_tool_result::Outcome,
};
use prost::Message as _;
use rig_agent::tool::DynamicTool;
use serde_json::{Value, json};
use std::sync::Arc;

const MAX_TOOLS: usize = 24;
const MAX_CATALOG_BYTES: usize = 24 * 1024;
const MAX_DESCRIPTION_BYTES: usize = 1024;
const MAX_SCHEMA_BYTES: usize = 4096;
const MAX_ARGUMENT_BYTES: usize = 4096;
const MAX_LINKS: usize = 16;
const UNAVAILABLE: &str = "live game data is not available";

pub(super) fn register(
    run: &Arc<Observations>,
    tools: &mut Vec<DynamicTool>,
) -> Result<(), TurnError> {
    let Some(actor) = &run.actor else {
        return Ok(());
    };
    let catalog = &actor.game_tools;
    if catalog.len() > MAX_TOOLS
        || catalog
            .iter()
            .map(prost::Message::encoded_len)
            .sum::<usize>()
            > MAX_CATALOG_BYTES
    {
        return Ok(());
    }
    for definition in catalog {
        let Some(schema) = parameters(definition) else {
            continue;
        };
        if tools
            .iter()
            .any(|tool| tool.name().as_str() == definition.name)
        {
            continue;
        }
        let name = definition.name.clone();
        tools.push(run.bind(
            &definition.name,
            &definition.description,
            schema,
            move |run, arguments| {
                let name = name.clone();
                async move {
                    let Some(actor) = &run.actor else {
                        return ToolObservation::unavailable(UNAVAILABLE);
                    };
                    call(&run.tools.game, actor, name, arguments).await
                }
            },
        )?);
    }
    Ok(())
}

fn parameters(tool: &AssistantToolDefinition) -> Option<Value> {
    let name = tool.name.strip_prefix("game_")?.as_bytes();
    if name.len() > 59
        || !name.first().is_some_and(u8::is_ascii_lowercase)
        || !name
            .iter()
            .all(|byte| byte.is_ascii_lowercase() || byte.is_ascii_digit() || *byte == b'_')
        || tool.description.trim().is_empty()
        || tool.description.len() > MAX_DESCRIPTION_BYTES
        || tool.input_schema_json.len() > MAX_SCHEMA_BYTES
    {
        return None;
    }
    let schema: Value = serde_json::from_str(&tool.input_schema_json).ok()?;
    (schema.get("type").and_then(Value::as_str) == Some("object")).then_some(schema)
}

async fn call(
    bridge: &GameBridge,
    actor: &PaperActor,
    name: String,
    arguments: Value,
) -> ToolObservation {
    if !arguments.is_object() {
        return ToolObservation::invalid("tool arguments must be an object");
    }
    let arguments_json = arguments.to_string();
    if arguments_json.len() > MAX_ARGUMENT_BYTES {
        return ToolObservation::invalid("tool arguments are too large");
    }
    let reply = bridge
        .query_for(
            actor.game_instance.clone(),
            Operation::AssistantTool(AssistantToolCall {
                player_uuid: actor.player_uuid.clone(),
                turn_id: actor.turn_id.clone(),
                name,
                arguments_json,
            }),
        )
        .await;
    match reply {
        Some(GameResult::AssistantTool(result)) => observe(result),
        _ => ToolObservation::unavailable(UNAVAILABLE),
    }
}

fn observe(result: AssistantToolResult) -> ToolObservation {
    match result.outcome {
        Some(Outcome::Observation(observation)) => recorded(observation),
        Some(Outcome::Failure(failure)) => {
            let detail = label(&failure.detail);
            let detail = if detail.is_empty() {
                UNAVAILABLE
            } else {
                &detail
            };
            match failure.reason() {
                Reason::InvalidArguments => ToolObservation::invalid(detail),
                Reason::AccessUnavailable => {
                    ToolObservation::error(LookupStatus::AccessUnavailable, detail)
                }
                Reason::Unavailable | Reason::Unspecified => ToolObservation::unavailable(detail),
            }
        }
        None => ToolObservation::unavailable(UNAVAILABLE),
    }
}

fn recorded(observation: AssistantToolObservation) -> ToolObservation {
    if observation.links.len() > MAX_LINKS || observation.encoded_len() > super::TOOL_OUTPUT_BYTES {
        return ToolObservation::unavailable("observation is too large; use a narrower query");
    }
    let Ok(data) = serde_json::from_str(&observation.data_json) else {
        return ToolObservation::unavailable("game tool returned invalid data");
    };
    let observed = ToolObservation::data(data);
    if observation.links.is_empty() {
        return observed;
    }
    let references = observation.links.into_iter().filter_map(|link| {
        let label = label(&link.label);
        if label.is_empty() {
            return None;
        }
        let reference = Reference::new(label, Some(&link.url));
        let url = reference.url()?;
        let record = json!({"label": reference.label(), "url": url});
        Some(ObservedRecord::citable(record, Some(reference)))
    });
    observed.with_records("references", references)
}
