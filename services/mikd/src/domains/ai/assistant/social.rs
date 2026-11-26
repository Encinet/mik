use super::{ActiveTurn, Assistant, DeliveryReceipt, prompt_too_long, safe_locale};
use crate::domains::ai::{
    Conversation, ReasoningSummaries, TurnError, VisibleReply,
    agent::{TURN_BUDGET, TurnTarget},
    diagnostics::TurnLog,
};
use mik_protocol::wire::AssistantReply;
use tokio::{
    sync::watch,
    time::{Instant, timeout_at},
};
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

pub(crate) struct SocialTurn {
    active: ActiveTurn,
    locale: String,
    question: String,
    log: TurnLog,
    deadline: Instant,
    summaries: Option<watch::Sender<String>>,
}

impl Assistant {
    pub(crate) fn begin_social(
        &self,
        conversation: Conversation,
        surface: SocialSurface,
        locale: &str,
        question: &str,
    ) -> Result<SocialTurn, TurnError> {
        let question = question.trim();
        if conversation.platform != surface.name()
            || question.is_empty()
            || prompt_too_long(question)
        {
            return Err(TurnError::Failed);
        }
        Ok(SocialTurn {
            active: self.begin(conversation)?,
            locale: safe_locale(locale.into()),
            question: question.to_owned(),
            log: TurnLog::new(Uuid::new_v4(), surface.name()),
            deadline: Instant::now() + TURN_BUDGET,
            summaries: (surface == SocialSurface::Matrix)
                .then(|| watch::Sender::new(String::new())),
        })
    }
}

impl SocialTurn {
    pub(crate) fn summaries(&self) -> Option<ReasoningSummaries> {
        self.summaries.as_ref().map(watch::Sender::subscribe)
    }

    pub(crate) async fn answer<Message>(
        self,
        review: impl AsyncFnOnce(AssistantReply) -> Option<(VisibleReply, Message)>,
    ) -> Result<(Message, DeliveryReceipt), TurnError> {
        let Self {
            active,
            locale,
            question,
            log,
            deadline,
            summaries,
        } = self;
        let work = async {
            let (reply, session) = active
                .answer(TurnTarget::Public, &locale, &question, log, summaries)
                .await?;
            let (visible, message) = review(reply)
                .await
                .filter(|(visible, _)| !visible.as_str().trim().is_empty())
                .ok_or_else(|| {
                    log.event("delivery", "projection_not_approved");
                    TurnError::Rejected
                })?;
            Ok((
                message,
                DeliveryReceipt::new(session, question, visible, None),
            ))
        };
        timeout_at(deadline, work).await.map_err(|_| {
            log.event("turn", "deadline_exceeded");
            TurnError::Failed
        })?
    }
}
