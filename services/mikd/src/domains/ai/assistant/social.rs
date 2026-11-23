use super::{Assistant, DeliveryReceipt, prompt_too_long, safe_locale};
use crate::domains::ai::{
    Conversation, TurnError, VisibleReply,
    agent::{TURN_BUDGET, TurnTarget},
    diagnostics::TurnLog,
};
use mik_protocol::wire::AssistantReply;
use uuid::Uuid;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum SocialSurface {
    Matrix,
    Qq,
}

impl SocialSurface {
    const fn name(self) -> &'static str {
        match self {
            Self::Matrix => "matrix",
            Self::Qq => "qq",
        }
    }
}

impl Assistant {
    pub(crate) async fn answer_social<Message>(
        &self,
        conversation: Conversation,
        surface: SocialSurface,
        locale: &str,
        question: &str,
        review: impl AsyncFnOnce(AssistantReply) -> Option<(VisibleReply, Message)>,
    ) -> Result<(Message, DeliveryReceipt), TurnError> {
        let question = question.trim();
        if conversation.platform != surface.name()
            || question.is_empty()
            || prompt_too_long(question)
        {
            return Err(TurnError::Failed);
        }
        let locale = safe_locale(locale.into());
        let log = TurnLog::new(Uuid::new_v4(), surface.name());
        let turn = self.begin(conversation)?;
        let work = async {
            let (reply, session) = turn
                .answer(TurnTarget::Public, &locale, question, log)
                .await?;
            let (visible, message) = review(reply)
                .await
                .filter(|(visible, _)| !visible.as_str().trim().is_empty())
                .ok_or_else(|| {
                    log.event("delivery", "projection_not_approved");
                    TurnError::Rejected
                })?;
            let receipt = DeliveryReceipt::new(session, question.to_owned(), visible, None);
            Ok((message, receipt))
        };
        tokio::time::timeout(TURN_BUDGET, work).await.map_err(|_| {
            log.event("turn", "deadline_exceeded");
            TurnError::Failed
        })?
    }
}
