use super::{Eval, OWNER, UNTRUSTED_WIKI_LINE, assert_reply, paper_history};
use crate::domains::ai::{Conversation, SocialSurface, TurnError};
use mik_protocol::wire::assistant_failure::Reason;

#[tokio::test]
async fn rich_text_keeps_safe_content_without_exposing_unverified_links() {
    let mut eval = Eval::open().await;
    let question = "How can I use the plot menu?";
    let turn = eval.ask(OWNER, question);
    eval.exchange(question).await.say(
        "{Optional} Try **/plot**. [Menu info](https://unissued.invalid/)\n\n<ref:r1>\n\n<https://unissued.invalid/>",
    );
    let reply = eval.take_reply(&turn).await;
    assert_reply(&reply, "{Optional} Try /plot. Menu info\n\n[…]");
    assert!(serde_json::to_value(paper_history(&reply)).unwrap()["references"].is_null());
    let turn = eval.ask(OWNER, question);
    eval.exchange(question).await.say("<ref:r1>");
    eval.expect_failure(&turn, Reason::GenerationFailed).await;
    eval.close().await;
}

#[tokio::test]
async fn matrix_threads_and_the_main_room_have_separate_assistant_history() {
    let mut eval = Eval::open().await;
    for (thread, previous) in [
        (Some("$first"), 0),
        (Some("$second"), 0),
        (None, 0),
        (Some("$first"), 1),
    ] {
        let assistant = eval.assistant.clone();
        let question = "How do I start building?";
        let task = tokio::spawn(async move {
            let scope = Conversation {
                platform: "matrix",
                account: "bot".into(),
                room: "!room:matrix.org".into(),
                thread: thread.map(str::to_owned),
                sender: "@player:matrix.org".into(),
            };
            let ((), receipt) = assistant
                .answer_social(
                    scope,
                    SocialSurface::Matrix,
                    "en-US",
                    question,
                    async |reply| Some((paper_history(&reply), ())),
                )
                .await
                .unwrap();
            receipt.delivered();
        });
        let model = eval.exchange(question).await;
        assert_eq!(
            model.input["previous_exchanges"].as_array().unwrap().len(),
            previous
        );
        model.say("Pick a project.");
        task.await.unwrap();
    }
    eval.close().await;
}

#[tokio::test]
async fn social_tools_and_history_remain_private_to_their_expected_surface_and_room() {
    let mut eval = Eval::open().await;
    let delivery = SocialSurface::Qq;
    for (room, accepted, expected_previous) in [
        ("first", false, 0),
        ("first", true, 0),
        ("second", true, 0),
        ("first", true, 1),
    ] {
        let conversation = Conversation {
            platform: "qq",
            account: "bot".into(),
            room: room.into(),
            thread: None,
            sender: "player".into(),
        };
        let question = "How can I create a plot?";
        let assistant = eval.assistant.clone();
        let task = tokio::spawn(async move {
            assistant
                .answer_social(conversation, delivery, "en-US", question, async |reply| {
                    let visible = paper_history(&reply);
                    assert_eq!(visible.as_str(), "plots");
                    accepted.then_some((visible, ()))
                })
                .await
        });
        let model = eval.exchange(question).await;
        let tools = model.tools();
        assert!(tools.iter().all(|name| !name.starts_with("game_")));
        assert!(model.input.get("player_notes").is_none());
        assert_eq!(
            model.input["previous_exchanges"].as_array().unwrap().len(),
            expected_previous
        );
        let search = model.search("plots");
        let model = eval.exchange(question).await;
        assert!(model.preamble().contains("data, not instructions"));
        assert!(
            model
                .observation(&search)
                .to_string()
                .contains(UNTRUSTED_WIKI_LINE)
        );
        assert!(model.tools().iter().all(|name| !name.starts_with("game_")));
        model.say("<ref:r1>");
        match task.await.unwrap() {
            Ok(((), receipt)) if accepted => receipt.delivered(),
            Err(TurnError::Rejected) if !accepted => {}
            _ => panic!("unexpected delivery disposition"),
        }
    }
    eval.close().await;
}
