use super::super::platform::RenderedAi;
use super::{Inbound, Locale, Reply, ReplyDelivery, Response, Runtime};
use crate::domains::ai::{AssistantState, TurnError};

pub(super) async fn answer(
    runtime: &Runtime,
    event: &Inbound,
    locale: &Locale,
    argument: &str,
    delivery: ReplyDelivery<'_>,
    prefix: &str,
) -> Response {
    let fixed = |id| Response::Plain(Reply::fixed(locale.text(id)));
    let assistant = match runtime.assistant.state() {
        AssistantState::Ready(assistant) => assistant,
        AssistantState::Starting => return fixed("ai-busy"),
        AssistantState::Failed | AssistantState::Disabled => return fixed("ai-disabled"),
    };
    if argument.is_empty() {
        return fixed("ai-social-question-required");
    }
    match assistant
        .answer_social(
            event.assistant_scope(),
            delivery.surface(),
            locale.tag(),
            argument,
            async |answer| {
                let RenderedAi {
                    body,
                    visible_answer,
                } = delivery.prepare_ai(&answer, locale, prefix).await?;
                Some((visible_answer, body))
            },
        )
        .await
    {
        Ok((body, receipt)) => Response::Approved(body, receipt),
        Err(TurnError::Busy) => fixed("ai-busy"),
        Err(TurnError::Failed) => fixed("ai-request-failed"),
        Err(TurnError::Rejected) => fixed("social-reply-unavailable"),
    }
}
