//! Translate the Paper polling protocol into the shared assistant lifecycle.

mod jobs;

pub(super) use jobs::Jobs;

use super::{
    Assistant, DeliveryReceipt, PROMPT_LIMIT, prompt_too_long, receipt::PaperMemory, safe_locale,
};
use crate::domains::ai::{
    AssistantState, Conversation, TurnError,
    agent::{PaperActor, TURN_BUDGET, TurnTarget},
    diagnostics::TurnLog,
    reply::paper_history,
};
use jobs::{JobError, Poll, Ready, Scope};
use mik_protocol::wire::{
    AssistantAsk, AssistantCommand, AssistantFailure, AssistantResult, AssistantSignal,
    assistant_command::Operation,
    assistant_failure::{Cause, Reason},
    assistant_result::Outcome,
};
use uuid::Uuid;

impl AssistantState<'_> {
    pub(crate) fn paper(self, game_instance: &str, command: AssistantCommand) -> AssistantResult {
        let outcome = match self {
            Self::Ready(service) => service
                .paper_operation(game_instance, command)
                .unwrap_or_else(|error| {
                    failure(match error {
                        JobError::Busy => Reason::Busy,
                        JobError::Invalid => Reason::InvalidRequest,
                        JobError::Unavailable => Reason::TurnUnavailable,
                    })
                }),
            Self::Starting => failure(Reason::Busy),
            Self::Failed => failure(Reason::ServiceUnavailable),
            Self::Disabled => failure(Reason::Disabled),
        };
        AssistantResult {
            outcome: Some(outcome),
        }
    }
}

impl Assistant {
    fn paper_operation(
        &self,
        game_instance: &str,
        command: AssistantCommand,
    ) -> Result<Outcome, JobError> {
        let scope = Scope {
            instance: game_instance.into(),
            player: parse_id(&command.player_uuid)?,
        };
        let id = parse_id(&command.turn_id)?;
        Ok(match command.operation.ok_or(JobError::Invalid)? {
            Operation::Ask(ask) => self.ask(scope, id, ask)?,
            Operation::PollResult(_) => match self.paper.poll(&scope, id)? {
                Poll::Pending => Outcome::Pending(AssistantSignal {}),
                Poll::Ready(Ok(reply)) => Outcome::Reply(reply),
                Poll::Ready(Err(TurnError::Busy)) => failure(Reason::Busy),
                Poll::Ready(Err(TurnError::Failed | TurnError::Rejected)) => {
                    failure(Reason::GenerationFailed)
                }
            },
            Operation::ConfirmDelivery(_) => {
                self.paper.delivered(&scope, id)?;
                Outcome::DeliveryConfirmed(AssistantSignal {})
            }
            Operation::CancelTurn(_) => {
                self.paper.discard(&scope, id)?;
                Outcome::Cancelled(AssistantSignal {})
            }
        })
    }

    fn ask(&self, scope: Scope, id: Uuid, ask: AssistantAsk) -> Result<Outcome, JobError> {
        let question = ask.prompt.trim();
        if question.is_empty() {
            return Err(JobError::Invalid);
        }
        if prompt_too_long(question) {
            return Ok(Outcome::Failure(AssistantFailure {
                cause: Some(Cause::MaxPromptCodePoints(PROMPT_LIMIT)),
            }));
        }
        let locale = safe_locale(ask.locale);
        let question = question.to_owned();
        let previous_displayed = ask
            .previous_displayed_turn
            .as_deref()
            .map(parse_id)
            .transpose()?;
        let log = TurnLog::new(id, "paper");
        self.paper
            .start(scope.clone(), id, previous_displayed, || {
                let player = scope.player.to_string();
                let turn = self
                    .begin(Conversation {
                        platform: "paper",
                        account: scope.instance.clone(),
                        room: "private".into(),
                        thread: None,
                        sender: player.clone(),
                    })
                    .map_err(|_| JobError::Busy)?;
                let actor = PaperActor {
                    game_instance: scope.instance.clone(),
                    player_uuid: player,
                    turn_id: id.to_string(),
                    game_tools: ask.game_tools,
                };
                let store = self.memory.clone();
                Ok(async move {
                    let target = TurnTarget::Paper {
                        memory: store.recalled(&scope.instance, scope.player),
                        actor,
                    };
                    let (reply, session) = tokio::time::timeout(
                        TURN_BUDGET,
                        turn.answer(target, &locale, &question, log),
                    )
                    .await
                    .map_err(|_| {
                        log.event("turn", "deadline_exceeded");
                        TurnError::Failed
                    })??;
                    let receipt = DeliveryReceipt::new(
                        session,
                        question,
                        paper_history(&reply),
                        Some(PaperMemory {
                            store,
                            instance: scope.instance,
                            player: scope.player,
                            log,
                        }),
                    );
                    Ok(Ready { reply, receipt })
                })
            })?;
        Ok(Outcome::Accepted(AssistantSignal {}))
    }
}

fn parse_id(value: &str) -> Result<Uuid, JobError> {
    Uuid::parse_str(value).map_err(|_| JobError::Invalid)
}

fn failure(reason: Reason) -> Outcome {
    Outcome::Failure(AssistantFailure {
        cause: Some(Cause::Reason(reason.into())),
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::domains::ai::AssistantSlot;

    #[test]
    fn starting_failed_and_disabled_assistants_report_distinct_failures() {
        let starting = AssistantSlot::new(true);
        let check = |state: AssistantState<'_>, reason: Reason| {
            let result = state.paper("paper", AssistantCommand::default());
            assert_eq!(result.outcome, Some(failure(reason)));
        };
        check(starting.state(), Reason::Busy);
        starting.finish(None);
        check(starting.state(), Reason::ServiceUnavailable);
        check(AssistantSlot::new(false).state(), Reason::Disabled);
    }
}
