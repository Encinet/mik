use super::{SendOutcome, send};
use crate::domains::{
    ai::ReasoningSummaries,
    social::{
        chat::{assistant_thinking, clip},
        locale::Locale,
    },
};
use futures_util::{
    FutureExt,
    future::{BoxFuture, Shared},
};
use matrix_sdk::ruma::{
    EventId, OwnedEventId, UserId,
    events::{
        Mentions,
        room::message::{
            MessageType, OriginalSyncRoomMessageEvent, Relation, ReplacementMetadata,
            RoomMessageEventContent, RoomMessageEventContentWithoutRelation,
        },
    },
};
use matrix_sdk::{Room, deserialized_responses::EncryptionInfo};
use std::{future::ready, time::Duration};
use tokio::sync::mpsc;
use tokio::time::{Instant, sleep_until, timeout};

// Generation and review run while we collect the HTTP or sync acknowledgement.
// Only the final delivery waits for this extra window if both are still missing.
const CONFIRMATION_GRACE: Duration = Duration::from_secs(2);
const EDIT_INTERVAL: Duration = Duration::from_secs(2);

pub(super) struct Feedback<'a> {
    room: &'a Room,
    question: &'a EventId,
    confirmation: Option<Shared<BoxFuture<'a, Option<OwnedEventId>>>>,
    edit: Option<Shared<BoxFuture<'a, bool>>>,
}

impl<'a> Feedback<'a> {
    pub(super) fn new(room: &'a Room, question: &'a EventId) -> Self {
        Self {
            room,
            question,
            confirmation: None,
            edit: None,
        }
    }

    pub(super) async fn show(
        &mut self,
        relation: Relation<RoomMessageEventContentWithoutRelation>,
        locale: &'static Locale,
        summaries: Option<ReasoningSummaries>,
    ) {
        let confirmation = post(self.room, self.question, relation, locale)
            .boxed()
            .shared();
        self.confirmation = Some(confirmation.clone());
        let (Some(id), Some(mut summaries)) = (confirmation.await, summaries) else {
            return;
        };
        let mut previous = String::new();
        let mut next_edit = Instant::now();
        let mut sequence = 0_u32;
        loop {
            let latest = summaries.borrow_and_update().clone();
            if !latest.is_empty() && latest != previous {
                sleep_until(next_edit).await;
                let latest = summaries.borrow_and_update().clone();
                let content = summary_edit(&latest, id.clone());
                sequence += 1;
                let transaction = format!("{}:ai-summary:{sequence}", self.question);
                let room = self.room;
                let edit = async move {
                    matches!(
                        timeout(CONFIRMATION_GRACE, send(room, content, &transaction)).await,
                        Ok(SendOutcome::Sent(_) | SendOutcome::NotSent)
                    )
                }
                .boxed()
                .shared();
                self.edit = Some(edit.clone());
                next_edit = Instant::now() + EDIT_INTERVAL;
                if !edit.await {
                    return;
                }
                previous = latest;
            }
            if summaries.changed().await.is_err() {
                return;
            }
        }
    }

    pub(super) async fn finish(
        self,
        content: RoomMessageEventContent,
        relation: Relation<RoomMessageEventContentWithoutRelation>,
    ) -> RoomMessageEventContent {
        finish(content, relation, self.confirmation, self.edit).await
    }
}

async fn post(
    room: &Room,
    question: &EventId,
    relation: Relation<RoomMessageEventContentWithoutRelation>,
    locale: &Locale,
) -> Option<OwnedEventId> {
    let transaction = format!("{}:ai-thinking", question.as_str());
    let (echo, observed) = mpsc::channel(1);
    let handler = room.add_event_handler({
        let bot = room.own_user_id().to_owned();
        let transaction = transaction.clone();
        move |event: OriginalSyncRoomMessageEvent, encryption: Option<EncryptionInfo>| {
            if encryption.is_some() && matches_echo(&event, &bot, &transaction) {
                let _sent = echo.try_send(event.event_id);
            }
            ready(())
        }
    });
    let _guard = room.client().event_handler_drop_guard(handler);
    let mut content = RoomMessageEventContent::text_plain(assistant_thinking(locale));
    content.relates_to = Some(relation);
    confirm(send(room, content, &transaction), observed).await
}

fn matches_echo(event: &OriginalSyncRoomMessageEvent, bot: &UserId, transaction: &str) -> bool {
    event.sender == bot
        && event
            .unsigned
            .transaction_id
            .as_deref()
            .is_some_and(|id| id.as_str() == transaction)
}

async fn confirm(
    posting: impl Future<Output = SendOutcome>,
    mut observed: mpsc::Receiver<OwnedEventId>,
) -> Option<OwnedEventId> {
    tokio::select! {
        biased;
        Some(id) = observed.recv() => Some(id),
        outcome = posting => match outcome {
            SendOutcome::Sent(id) => Some(id),
            SendOutcome::NotSent => None,
            SendOutcome::Unconfirmed => observed.recv().await,
        }
    }
}

async fn finish(
    mut content: RoomMessageEventContent,
    relation: Relation<RoomMessageEventContentWithoutRelation>,
    confirmation: Option<impl Future<Output = Option<OwnedEventId>>>,
    summary_edit: Option<impl Future<Output = bool>>,
) -> RoomMessageEventContent {
    // A summary whose delivery is uncertain could arrive after a final edit.
    // Send the answer as its own reply in that case.
    let may_replace = match summary_edit {
        Some(edit) => edit.await,
        None => true,
    };
    if may_replace
        && let Some(confirmation) = confirmation
        && let Ok(Some(id)) = tokio::time::timeout(CONFIRMATION_GRACE, confirmation).await
    {
        return replace_thinking(content, id);
    }
    content.relates_to = Some(relation);
    content
}

fn summary_edit(summary: &str, thinking: OwnedEventId) -> RoomMessageEventContent {
    let content =
        RoomMessageEventContent::text_plain(format!("◆ {summary}")).add_mentions(Mentions::new());
    replace_thinking(content, thinking)
}

fn replace_thinking(
    content: RoomMessageEventContent,
    thinking: OwnedEventId,
) -> RoomMessageEventContent {
    let body = content.body();
    let preview = clip(body, 512);
    let ellipsis = if preview.len() < body.len() {
        "…"
    } else {
        ""
    };
    // The full rich reply belongs in m.new_content. Keep the legacy fallback small.
    let fallback = MessageType::text_plain(format!("* {preview}{ellipsis}"));
    let mut edited = content.make_replacement(ReplacementMetadata::new(thinking, None));
    edited.msgtype = fallback;
    edited
}

#[cfg(test)]
mod tests {
    use super::super::{assistant_reply, render, reply_relation};
    use super::*;
    use matrix_sdk::ruma::{event_id, user_id};
    use tokio::time::{Instant, sleep};

    #[tokio::test(start_paused = true)]
    async fn a_late_sync_echo_recovers_the_edit_after_an_unknown_send() {
        let thinking = event_id!("$thinking");
        let answer = assistant_reply(
            &crate::domains::ai::sample("Use **iron blocks**."),
            Locale::get("en-US"),
            "/",
        );
        let content = render::command_reply(answer.body).unwrap();
        let question = event_id!("$question");
        let transaction = "$question:ai-thinking";
        let bot = user_id!("@bot:example.org");
        let (echo, observed) = mpsc::channel(1);
        let sync = async {
            sleep(Duration::from_millis(500)).await;
            for (sender, txn, id) in [
                ("@other:example.org", transaction, "$foreign"),
                (bot.as_str(), "$other:ai-thinking", "$unrelated"),
                (bot.as_str(), transaction, thinking.as_str()),
            ] {
                let event = serde_json::from_value(serde_json::json!({
                    "type": "m.room.message", "sender": sender, "event_id": id,
                    "origin_server_ts": 1, "unsigned": {"transaction_id": txn},
                    "content": {"msgtype": "m.text", "body": "◆ Thinking…"}
                }))
                .unwrap();
                if matches_echo(&event, bot, transaction) {
                    echo.try_send(event.event_id).unwrap();
                }
            }
        };
        let result = finish(
            content,
            reply_relation(question.into(), None),
            Some(confirm(ready(SendOutcome::Unconfirmed), observed)),
            None::<std::future::Ready<bool>>,
        );
        let ((), content) = tokio::join!(sync, result);
        let edit = serde_json::to_value(content).unwrap();

        assert_eq!(
            edit["m.relates_to"],
            serde_json::json!({"rel_type": "m.replace", "event_id": thinking.as_str()})
        );
        assert_eq!(edit["body"], "* ◆ Use iron blocks.");
        assert!(edit.get("formatted_body").is_none());
        assert_eq!(edit["m.new_content"]["body"], "◆ Use iron blocks.");
        assert_eq!(
            edit["m.new_content"]["formatted_body"],
            "<p>◆ Use <strong>iron blocks</strong>.</p>"
        );
    }

    #[tokio::test(start_paused = true)]
    async fn a_missing_confirmation_has_a_bounded_fallback_in_the_original_thread() {
        let question = event_id!("$question");
        let root = event_id!("$root");
        for outcome in [SendOutcome::NotSent, SendOutcome::Unconfirmed] {
            let wait = if matches!(outcome, SendOutcome::NotSent) {
                Duration::ZERO
            } else {
                CONFIRMATION_GRACE
            };
            let (_echo, observed) = mpsc::channel(1);
            let started = Instant::now();
            let result = finish(
                RoomMessageEventContent::text_plain("Generation failed"),
                reply_relation(question.into(), Some(root.into())),
                Some(confirm(ready(outcome), observed)),
                None::<std::future::Ready<bool>>,
            )
            .await;
            assert_eq!(started.elapsed(), wait);
            let json = serde_json::to_value(result).unwrap();
            assert_eq!(json["body"], "Generation failed");
            assert_eq!(json["m.relates_to"]["event_id"], root.as_str());
            assert_eq!(
                json["m.relates_to"]["m.in_reply_to"]["event_id"],
                question.as_str()
            );
        }
    }

    #[tokio::test]
    async fn summary_and_confirmed_answer_replace_the_same_event_without_notifying_the_room() {
        let thinking = event_id!("$thinking");
        let question = event_id!("$question");
        let root = event_id!("$root");
        let edit =
            serde_json::to_value(summary_edit("Checking <b>@room</b>.", thinking.into())).unwrap();
        assert_eq!(edit["m.new_content"]["body"], "◆ Checking <b>@room</b>.");
        assert!(edit["m.new_content"].get("formatted_body").is_none());
        assert_eq!(edit["m.new_content"]["m.mentions"], serde_json::json!({}));
        for confirmed in [true, false] {
            let answer = finish(
                RoomMessageEventContent::text_plain("◆ Use iron blocks."),
                reply_relation(question.into(), Some(root.into())),
                Some(ready(Some(thinking.into()))),
                Some(ready(confirmed)),
            )
            .await;
            let answer = serde_json::to_value(answer).unwrap();
            if confirmed {
                assert_eq!(answer["m.relates_to"], edit["m.relates_to"]);
                assert_eq!(answer["m.new_content"]["body"], "◆ Use iron blocks.");
            } else {
                assert_eq!(answer["m.relates_to"]["event_id"], root.as_str());
                assert_eq!(
                    answer["m.relates_to"]["m.in_reply_to"]["event_id"],
                    question.as_str()
                );
                assert_eq!(answer["body"], "◆ Use iron blocks.");
            }
        }
    }
}
