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
    use crate::domains::{
        ai::{AiConfig, Assistant, AssistantState, Conversation},
        social::{
            commands::AssistantCommand,
            followups::{FollowUpKey, QuestionOffer, QuestionOffers},
        },
    };
    use crate::game::GameBridge;
    use std::{
        sync::Arc,
        time::{Duration, Instant},
    };

    async fn assistant_for(endpoint: &str) -> (tempfile::TempDir, Assistant) {
        let directory = tempfile::tempdir().unwrap();
        let path = directory.path().join("ai.yml");
        let settings = serde_json::json!({"provider": {"endpoint": endpoint, "model": "test"}});
        std::fs::write(&path, settings.to_string()).unwrap();
        let config = AiConfig::load(&path, directory.path(), None, None, None).unwrap();
        let assistant = Assistant::open(config, Arc::new(GameBridge::new()))
            .await
            .unwrap();
        (directory, assistant)
    }

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

    #[tokio::test]
    async fn a_rejected_qq_question_preserves_buttons_from_the_admitted_turn() {
        let (_dir, assistant) =
            assistant_for("https://model.example.org/v1/chat/completions").await;
        let scope = Conversation {
            platform: "qq",
            account: "bot".into(),
            room: "group".into(),
            sender: "player".into(),
            thread: None,
        };
        let offers = QuestionOffers::default();
        let begin = |question| {
            let mut generation = None;
            let turn = AssistantCommand::begin(
                AssistantState::Ready(&assistant),
                scope.clone(),
                Locale::get("en-US"),
                question,
                ReplyDelivery::qq(Gate::Unavailable, true),
                "/",
                || {
                    generation = offers.begin(scope.clone(), Instant::now());
                    generation
                },
            );
            (turn, generation)
        };
        let (first, generation) = begin("Where is spawn?");
        let first = first.ok().unwrap();
        let (second, buttons) = begin("Another question");
        assert_eq!(
            second.err().unwrap().text,
            Locale::get("en-US").text("ai-busy")
        );
        assert!(buttons.is_none());
        let follow_up = "Can I walk there?";
        let offer = QuestionOffer::new(generation.unwrap(), vec![follow_up.into()]).unwrap();
        let key = FollowUpKey(Uuid::new_v4());
        assert!(offers.offer(&scope, &offer, vec![key], Instant::now()));
        assert_eq!(
            offers.claim(&scope, &key, Instant::now()).as_deref(),
            Some(follow_up)
        );

        drop(first);
        assistant.close().await;
    }

    #[tokio::test]
    async fn a_stalled_thinking_notice_does_not_delay_the_reviewed_answer() {
        use axum::{Json, Router, routing::post};
        use tokio::net::TcpListener;

        let listener = TcpListener::bind(("127.0.0.1", 0)).await.unwrap();
        let endpoint = format!(
            "http://{}/v1/chat/completions",
            listener.local_addr().unwrap()
        );
        let app = Router::new().route("/v1/chat/completions", post(async || Json(serde_json::json!({
            "id": "chatcmpl-1", "object": "chat.completion", "created": 1, "model": "test",
            "choices": [{"index": 0, "finish_reason": "stop", "message": {"role": "assistant", "content": "Use /spawn."}}]
        }))));
        let server = tokio::spawn(async move {
            axum::serve(listener, app).await.unwrap();
        });
        let (_dir, assistant) = assistant_for(&endpoint).await;
        let scope = Conversation {
            platform: "matrix",
            account: "bot".into(),
            room: "room".into(),
            sender: "player".into(),
            thread: None,
        };
        let turn = AssistantCommand::begin(
            AssistantState::Ready(&assistant),
            scope,
            Locale::get("en-US"),
            "How do I go back?",
            ReplyDelivery::matrix(),
            "/",
            || None,
        )
        .ok()
        .unwrap();
        let mut started = false;
        let answer = turn.answer(|_| async {
            started = true;
            std::future::pending::<()>().await;
        });
        let (body, receipt) = tokio::time::timeout(Duration::from_secs(5), answer)
            .await
            .unwrap()
            .ok()
            .unwrap();
        assert!(started);
        assert!(matches!(body, ReplyBody::Matrix(_)));
        receipt.delivered();
        assistant.close().await;
        server.abort();
        assert!(server.await.unwrap_err().is_cancelled());
    }
}
