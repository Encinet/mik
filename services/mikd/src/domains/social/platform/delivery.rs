use super::super::{locale::Locale, runtime::ReplyBody};
use super::{
    matrix,
    qq::{self, moderation::Gate},
};
use crate::domains::ai::{SocialSurface, VisibleReply};
use mik_protocol::wire::AssistantReply;
use uuid::Uuid;

#[derive(Clone, Copy)]
pub(in crate::domains::social) struct ReplyDelivery<'review>(Target<'review>);

#[derive(Clone, Copy)]
enum Target<'review> {
    Matrix,
    Qq {
        gate: Gate<'review>,
        buttons: bool,
        generation: Option<Uuid>,
    },
}

pub(in crate::domains::social) struct RenderedAi {
    pub(in crate::domains::social) body: ReplyBody,
    pub(in crate::domains::social) visible_answer: VisibleReply,
}

impl<'review> ReplyDelivery<'review> {
    pub(super) const fn matrix() -> Self {
        Self(Target::Matrix)
    }

    pub(super) const fn qq(gate: Gate<'review>, buttons: bool) -> Self {
        Self(Target::Qq {
            gate,
            buttons,
            generation: None,
        })
    }

    pub(in crate::domains::social) fn with_generation(
        mut self,
        begin: impl FnOnce() -> Option<Uuid>,
    ) -> Self {
        if let Target::Qq {
            buttons: true,
            generation,
            ..
        } = &mut self.0
        {
            *generation = begin();
        }
        self
    }

    pub(in crate::domains::social) const fn surface(self) -> SocialSurface {
        match self.0 {
            Target::Matrix => SocialSurface::Matrix,
            Target::Qq { .. } => SocialSurface::Qq,
        }
    }

    pub(in crate::domains::social) async fn allows_text(self, text: &str) -> bool {
        match self.0 {
            Target::Matrix => true,
            Target::Qq { gate, .. } => gate.reply(text).await,
        }
    }

    pub(in crate::domains::social) async fn prepare_ai(
        self,
        answer: &AssistantReply,
        locale: &Locale,
        prefix: &str,
    ) -> Option<RenderedAi> {
        match self.0 {
            Target::Matrix => Some(matrix::assistant_reply(answer, locale, prefix)),
            Target::Qq {
                gate, generation, ..
            } => qq::prepare_assistant(answer, locale, generation, gate, prefix).await,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn matrix_keeps_delivering_when_qq_moderation_is_unavailable() {
        let answer = crate::domains::ai::sample("Use iron blocks.");
        let locale = Locale::get("en-US");
        let qq = ReplyDelivery::qq(Gate::Unavailable, false);
        assert!(!qq.allows_text("Use iron blocks.").await);
        assert!(qq.prepare_ai(&answer, locale, "/").await.is_none());

        let matrix =
            ReplyDelivery::matrix().with_generation(|| panic!("Matrix uses no QQ buttons"));
        assert_eq!(matrix.surface(), SocialSurface::Matrix);
        assert!(matrix.allows_text("Use iron blocks.").await);
        assert!(matches!(
            matrix.prepare_ai(&answer, locale, "/").await.unwrap().body,
            ReplyBody::Matrix(_)
        ));
    }

    #[test]
    fn disabled_buttons_do_not_allocate_a_question_offer() {
        let delivery = ReplyDelivery::qq(Gate::Unavailable, false)
            .with_generation(|| panic!("disabled buttons still allocate an offer"));
        assert!(matches!(
            delivery.0,
            Target::Qq {
                generation: None,
                ..
            }
        ));
    }
}
