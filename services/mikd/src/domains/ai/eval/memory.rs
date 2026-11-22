use super::{Eval, OTHER_INSTANCE, OWNER, VISITOR, WAIT, assert_reply};
use mik_protocol::wire::assistant_result::Outcome;
use serde_json::json;
use std::time::Duration;
use tokio::time::{sleep, timeout};
use uuid::Uuid;

const QUESTION: &str =
    "I'm building a steampunk harbor this season and want to preserve the coast.";
const NOTE: &str = "Building a steampunk harbor that preserves the coast.";
const ADVICE: &str = "Place piers along the existing coastline.";

#[tokio::test]
async fn memory_review_is_independent_and_only_delivered_changes_persist() {
    let mut eval = Eval::open_script(None, None, None, true).await;
    let turn = eval.ask(OWNER, QUESTION);
    eval.exchange(QUESTION).await.say(ADVICE);
    assert!(matches!(
        eval.poll_done(&turn).await.outcome,
        Some(Outcome::Reply(_))
    ));
    assert!(
        timeout(Duration::from_millis(60), eval.requests.recv())
            .await
            .is_err()
    );
    eval.discard(&turn);

    for approve in [false, true] {
        let turn = eval.ask(OWNER, QUESTION);
        let model = eval.exchange(QUESTION).await;
        assert_eq!(model.input["player_notes"], json!([]));
        model.say(ADVICE);
        assert_reply(&eval.take_reply(&turn).await, ADVICE);
        eval.delivered(&turn);
        let extract = eval.exchange(QUESTION).await;
        assert!(extract.tools().is_empty());
        assert_eq!(extract.input, json!({"request":QUESTION, "notes":[]}));
        extract.json(&json!({"changes":[{"op":"add", "text":NOTE, "ttl_days":30}]}));
        let review = eval.exchange(QUESTION).await;
        assert!(review.tools().is_empty());
        assert_eq!(review.input["proposals"][0]["change"]["text"], NOTE);
        assert!(review.input.get("candidate").is_none());
        if approve {
            review.json(&json!({"approved":[1,99]}));
        } else {
            review.json(&json!({"approved":[]}));
        }
    }
    let player = Uuid::parse_str(OWNER.uuid).unwrap();
    timeout(WAIT, async {
        loop {
            if eval
                .assistant
                .memory_notes(OWNER.instance, player)
                .as_deref()
                == Some(&[NOTE.to_owned()])
            {
                break;
            }
            sleep(Duration::from_millis(10)).await;
        }
    })
    .await
    .expect("approved memory is committed by the worker");
    eval.reopen().await;
    for (player, has_note) in [(VISITOR, false), (OTHER_INSTANCE, false), (OWNER, true)] {
        let question = "Which materials work for my harbor?";
        let turn = eval.ask(player, question);
        let model = eval.exchange(question).await;
        let notes = model.input["player_notes"].as_array().unwrap();
        assert_eq!(notes.len(), usize::from(has_note));
        if has_note {
            assert_eq!(notes[0], NOTE);
        }
        model.say("Try stone and copper for the harbor.");
        assert_reply(
            &eval.take_reply(&turn).await,
            "Try stone and copper for the harbor.",
        );
        eval.exchange(question).await.json(&json!({"changes":[]}));
    }
    eval.close().await;
}

#[tokio::test]
async fn background_learning_yields_to_a_player_with_a_single_model_slot() {
    let mut eval = Eval::open_script(Some(1), None, None, true).await;
    let turn = eval.ask(OWNER, QUESTION);
    eval.exchange(QUESTION).await.say(ADVICE);
    assert_reply(&eval.take_reply(&turn).await, ADVICE);
    let background = eval.exchange(QUESTION).await;
    assert_eq!(background.input["notes"], json!([]));

    let question = "What fuel should I use in a furnace?";
    let next = eval.ask(OWNER, question);
    let model = eval.exchange(question).await;
    assert_eq!(model.questions(), [QUESTION, question]);
    model.say("Coal works in a furnace.");
    drop(background);
    assert_reply(&eval.take_reply(&next).await, "Coal works in a furnace.");
    eval.exchange(question).await.json(&json!({"changes":[]}));
    eval.close().await;
}

#[tokio::test]
async fn unavailable_player_memory_does_not_disable_the_assistant() {
    let mut eval = Eval::open().await;
    eval.assistant.close().await;
    let path = eval.directory.path().join("ai-player-memory.sqlite");
    let data = b"not an AI memory database";
    std::fs::write(&path, data).unwrap();
    eval.reopen().await;
    let question = "What fuel should I use in a furnace?";
    let turn = eval.ask(OWNER, question);
    let model = eval.exchange(question).await;
    assert!(model.input.get("player_notes").is_none());
    model.say("Coal is a convenient early fuel.");
    assert_reply(
        &eval.take_reply(&turn).await,
        "Coal is a convenient early fuel.",
    );
    assert_eq!(std::fs::read(path).unwrap(), data);
    eval.close().await;
}
