use super::super::super::{chat, followups::FollowUpKey};
use qqbot_sdk_rs::{InteractionResolved, Keyboard};
use serde_json::json;
use uuid::Uuid;

const NUMBERS: [&str; 2] = ["①", "②"];
const LABEL_LIMIT: usize = 10;
const PREFIX: &str = "mik-ai:";

pub(super) fn numbered(questions: &[String]) -> impl Iterator<Item = (&'static str, &str)> {
    NUMBERS
        .into_iter()
        .zip(questions.iter().map(String::as_str))
}

pub(super) fn supported(questions: &[String]) -> bool {
    (1..=NUMBERS.len()).contains(&questions.len())
        && questions
            .iter()
            .all(|question| chat::copyable_question(question))
}

fn label<'a>(number: &'static str, question: &'a str) -> &'a str {
    // QQ allows 10 characters. A UTF-16 budget conservatively covers both ways of counting.
    if question.encode_utf16().nth(LABEL_LIMIT).is_none() {
        question
    } else {
        number
    }
}

pub(super) fn offer(
    owner: &str,
    questions: &[String],
    unavailable: &str,
) -> Option<(Keyboard, Vec<FollowUpKey>)> {
    if !supported(questions) || owner.is_empty() {
        return None;
    }
    let group_id = Uuid::new_v4().simple().to_string();
    let mut keys = Vec::with_capacity(questions.len());
    let rows: Vec<_> = numbered(questions)
        .map(|(number, question)| {
            let key = Uuid::new_v4();
            let id = key.simple().to_string();
            keys.push(FollowUpKey(key));
            let label = label(number, question);
            json!({
                "buttons": [{
                    "id": id,
                    "group_id": group_id,
                    "render_data": { "label": label, "visited_label": label, "style": 1 },
                    "action": {
                        "type": 1,
                        "permission": { "type": 0, "specify_user_ids": [owner] },
                        "data": format!("{PREFIX}{id}"),
                        "unsupport_tips": unavailable,
                    },
                }],
            })
        })
        .collect();
    let keyboard = Keyboard {
        id: None,
        content: Some(json!({ "rows": rows })),
    };
    Some((keyboard, keys))
}

pub(super) fn key(resolved: &InteractionResolved) -> Option<FollowUpKey> {
    let id = resolved.button_data.as_deref()?.strip_prefix(PREFIX)?;
    // The group callback may omit button_id. When present, it must identify the same button.
    if id.len() != 32
        || resolved
            .button_id
            .as_deref()
            .is_some_and(|button| button != id)
    {
        return None;
    }
    let key = Uuid::try_parse(id).ok()?;
    (key.get_version_num() == 4 && key.simple().encode_lower(&mut Uuid::encode_buffer()) == id)
        .then_some(FollowUpKey(key))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::domains::{
        ai::Conversation,
        social::followups::{QuestionOffer, QuestionOffers},
    };
    use std::time::Instant;

    #[test]
    fn callback_buttons_display_full_questions_and_send_only_opaque_ids() {
        let questions = ["用铁块吗？".into(), "要几个中继器？".into()];
        let (keyboard, keys) = offer("original-member", &questions, "请直接提问").unwrap();
        let content = keyboard.content.unwrap();
        let rows = content["rows"].as_array().unwrap();
        assert_eq!(rows.len(), 2);
        assert!(rows[0]["buttons"][0]["group_id"].is_string());
        assert_eq!(
            rows[0]["buttons"][0]["group_id"],
            rows[1]["buttons"][0]["group_id"]
        );
        for ((row, question), token) in rows.iter().zip(questions).zip(keys) {
            let buttons = row["buttons"].as_array().unwrap();
            assert_eq!(buttons.len(), 1);
            let button = &buttons[0];
            assert_eq!(button["render_data"]["label"], question);
            assert_eq!(button["render_data"]["visited_label"], question);
            assert_eq!(button["action"]["type"], 1);
            assert_eq!(button["action"]["permission"]["type"], 0);
            assert_eq!(
                button["action"]["permission"]["specify_user_ids"],
                json!(["original-member"])
            );
            let mut callback = InteractionResolved {
                button_data: button["action"]["data"].as_str().map(str::to_owned),
                ..Default::default()
            };
            assert!(key(&callback) == Some(token));
            callback.button_id = Some("different-button".into());
            assert!(key(&callback).is_none());
        }
    }

    #[test]
    fn numbered_button_claims_the_full_question_once() {
        let question = "铁块电梯相邻两层要隔多远？";
        let questions = vec!["要用铁块吗？".into(), question.into()];
        let (keyboard, ids) = offer("owner", &questions, "Unavailable").unwrap();
        let payload = keyboard.content.unwrap();
        assert_eq!(
            payload["rows"][0]["buttons"][0]["render_data"]["label"],
            questions[0]
        );
        let button = &payload["rows"][1]["buttons"][0];
        assert_eq!(button["render_data"]["label"], "②");
        assert_eq!(button["render_data"]["visited_label"], "②");
        let callback = key(&InteractionResolved {
            button_data: button["action"]["data"].as_str().map(str::to_owned),
            button_id: button["id"].as_str().map(str::to_owned),
            ..Default::default()
        })
        .unwrap();

        let player = Conversation {
            platform: "qq",
            account: "qq-app".into(),
            room: "group".into(),
            thread: None,
            sender: "owner".into(),
        };
        let now = Instant::now();
        let offers = QuestionOffers::default();
        let generation = offers.begin(player.clone(), now).unwrap();
        let reviewed = QuestionOffer::new(generation, questions).unwrap();
        assert!(offers.offer(&player, &reviewed, ids, now));
        let outsider = Conversation {
            sender: "other-member".into(),
            ..player.clone()
        };
        assert_eq!(offers.claim(&outsider, &callback, now), None);
        assert_eq!(
            offers.claim(&player, &callback, now).as_deref(),
            Some(question)
        );
        assert_eq!(offers.claim(&player, &callback, now), None);
    }

    #[test]
    fn labels_change_to_numbers_at_either_character_limit() {
        assert_eq!(label("①", "这是十个字符的题吗？"), "这是十个字符的题吗？");
        assert_eq!(label("①", "这是超过十个字符的题吗？"), "①");
        assert_eq!(label("①", "𠀀𠀀𠀀𠀀x?"), "𠀀𠀀𠀀𠀀x?");
        assert_eq!(label("①", "𠀀𠀀𠀀𠀀𠀀?"), "①");
        assert!(offer("owner", &["<@member>".into()], "Unavailable").is_none());
    }
}
