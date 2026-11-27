use super::super::platform::RenderedAi;
use super::{Locale, Reply, ReplyBody, ReplyDelivery};
use crate::domains::ai::{
    AssistantState, Conversation, DeliveryReceipt, ReasoningSummaries, SocialTurn, TurnError,
};
use uuid::Uuid;

pub(in crate::domains::social) struct AssistantCommand<'review> {
    turn: SocialTurn,
    delivery: ReplyDelivery<'review>,
    locale: &'static Locale,
    prefix: &'static str,
}

impl<'review> AssistantCommand<'review> {
    pub(in crate::domains::social) fn begin(
        state: AssistantState<'_>,
        scope: Conversation,
        locale: &'static Locale,
        argument: &str,
        delivery: ReplyDelivery<'review>,
        prefix: &'static str,
        begin_questions: impl FnOnce() -> Option<Uuid>,
    ) -> Result<Self, Reply> {
        let fixed = |id| Reply::fixed(locale.text(id));
        let assistant = match state {
            AssistantState::Ready(assistant) => assistant,
            AssistantState::Starting => return Err(fixed("ai-busy")),
            AssistantState::Failed | AssistantState::Disabled => return Err(fixed("ai-disabled")),
        };
        if argument.is_empty() {
            return Err(fixed("ai-social-question-required"));
        }
        let turn = assistant
            .begin_social(scope, delivery.surface(), locale.tag(), argument)
            .map_err(|error| failure(locale, error))?;
        Ok(Self {
            turn,
            delivery: delivery.with_generation(begin_questions),
            locale,
            prefix,
        })
    }

    pub(in crate::domains::social) async fn answer<F: Future<Output = ()>>(
        self,
        feedback: impl FnOnce(Option<ReasoningSummaries>) -> F,
    ) -> Result<(ReplyBody, DeliveryReceipt), Reply> {
        let Self {
            turn,
            delivery,
            locale,
            prefix,
        } = self;
        let feedback = feedback(turn.summaries());
        let answer = turn.answer(async |answer| {
            let RenderedAi {
                body,
                visible_answer,
            } = delivery.prepare_ai(&answer, locale, prefix).await?;
            Some((visible_answer, body))
        });
        tokio::pin!(answer);
        tokio::select! {
            biased;
            () = feedback => answer.await,
            result = &mut answer => result,
        }
        .map_err(|error| failure(locale, error))
    }
}

fn failure(locale: &Locale, error: TurnError) -> Reply {
    let id = match error {
        TurnError::Busy => "ai-busy",
        TurnError::Failed => "ai-request-failed",
        TurnError::Rejected => "social-reply-unavailable",
    };
    Reply::fixed(locale.text(id))
}
