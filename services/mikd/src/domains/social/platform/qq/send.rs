use super::super::super::{
    chat::Inbound,
    config::Conversation,
    followups::QuestionOffer,
    locale::Locale,
    runtime::{CommandReply, ReplyBody, Runtime},
};
use super::keyboard;
use base64::{Engine, engine::general_purpose::STANDARD};
use qqbot_sdk_rs::{
    Bot, BotEvent, InteractionCreate, Keyboard, MediaUploadRequest, MessageRequest, SdkError,
    api::message::MediaTarget,
};

#[derive(Clone, Copy)]
pub(super) enum Target<'a> {
    Message(&'a BotEvent),
    Interaction(&'a InteractionCreate),
}

impl Target<'_> {
    async fn reply(self, request: MessageRequest) -> qqbot_sdk_rs::Result<()> {
        // InteractionCreate uses the dispatch event_id; do not reply to the bot's old message.
        match self {
            Self::Message(event) => event.reply(request).await,
            Self::Interaction(event) => event.reply(request).await,
        }
        .map(|_| ())
    }
}

pub(super) async fn command(
    target: Target<'_>,
    bot: &Bot,
    runtime: &Runtime,
    inbound: &Inbound,
    route: &Conversation,
    reply: CommandReply,
) {
    let mut actual = None;
    let result = match reply.body {
        ReplyBody::Plain(text) => {
            let media = match reply.avatar {
                Some(png) => bot
                    .api()
                    .messages()
                    .upload_media_with(
                        MediaTarget::Group(&inbound.conversation),
                        &MediaUploadRequest {
                            file_type: Some(1),
                            srv_send_msg: Some(false),
                            file_name: Some("avatar.png".into()),
                            file_data: Some(STANDARD.encode(png)),
                            ..Default::default()
                        },
                    )
                    .await
                    .ok()
                    .filter(|media| !media.file_info.is_empty()),
                None => None,
            };
            let msg_type = if media.is_some() { 7 } else { 0 };
            target
                .reply(MessageRequest {
                    media,
                    msg_type: Some(msg_type),
                    ..text_request(text)
                })
                .await
        }
        ReplyBody::QqMarkdown {
            markdown,
            plain,
            plain_visible,
            offer,
        } => {
            let rich = markdown_request(markdown, offer.as_ref(), |offer| {
                let locale = Locale::get(&route.locale);
                let (keyboard, keys) = keyboard::offer(
                    &inbound.sender,
                    offer.questions(),
                    &locale.text("ai-question-button-unavailable"),
                )?;
                runtime
                    .offer_questions(inbound, offer, keys)
                    .then_some(keyboard)
            });
            send_markdown(rich, plain, |request| target.reply(request))
                .await
                .map(|format| {
                    if format == SentFormat::Plain {
                        actual = Some(plain_visible);
                    }
                })
        }
        ReplyBody::Matrix(_) => return,
    };
    match result {
        Ok(()) => {
            if let Some(receipt) = reply.receipt {
                receipt.delivered_as(actual);
            }
        }
        Err(error) => log_failure(&error),
    }
}

fn markdown_request(
    markdown: serde_json::Value,
    offer: Option<&QuestionOffer>,
    register: impl FnOnce(&QuestionOffer) -> Option<Keyboard>,
) -> MessageRequest {
    MessageRequest {
        msg_type: Some(2),
        markdown: Some(markdown),
        keyboard: offer.and_then(register),
        ..Default::default()
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum SentFormat {
    Markdown,
    Plain,
}

async fn send_markdown<Sent: Future<Output = qqbot_sdk_rs::Result<()>>>(
    rich: MessageRequest,
    plain: String,
    mut reply: impl FnMut(MessageRequest) -> Sent,
) -> qqbot_sdk_rs::Result<SentFormat> {
    let without_keyboard = rich.keyboard.is_some().then(|| MessageRequest {
        keyboard: None,
        ..rich.clone()
    });
    let sent = match (reply(rich).await, without_keyboard) {
        (Err(error), Some(request)) if keyboard_rejected(&error) => reply(request).await,
        (sent, _) => sent,
    };
    match sent {
        Err(error) if format_rejected(&error) => {
            reply(text_request(plain)).await.map(|()| SentFormat::Plain)
        }
        result => result.map(|()| SentFormat::Markdown),
    }
}

fn text_request(text: String) -> MessageRequest {
    MessageRequest {
        content: Some(text),
        msg_type: Some(0),
        ..Default::default()
    }
}

fn format_rejected(error: &SdkError) -> bool {
    matches!(
        rejection_code(error),
        Some(304_036 | 40_034_008..=40_034_011 | 40_034_124 | 40_034_127 | 40_054_007)
    )
}

fn keyboard_rejected(error: &SdkError) -> bool {
    matches!(
        rejection_code(error),
        Some(304_037..=304_040 | 305_007 | 40_034_029)
    )
}

fn rejection_code(error: &SdkError) -> Option<i64> {
    match error {
        SdkError::Api {
            status: 200..=299 | 400 | 403 | 422,
            code,
            ..
        } => Some(*code),
        _ => None,
    }
}

fn log_failure(error: &SdkError) {
    if let SdkError::Api { status, code, .. } = error {
        eprintln!("mikd QQ command reply failed (HTTP {status}, code {code})");
    } else {
        eprintln!("mikd QQ command reply failed");
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::domains::{
        ai::{Conversation as AssistantConversation, sample},
        social::{chat::Platform, followups::QuestionOffers, platform::qq::assistant},
    };
    use std::{future::ready, time::Instant};

    const QUESTION: &str = "铁块电梯相邻两层要隔多远？";

    fn api(status: u16, code: i64) -> SdkError {
        SdkError::Api {
            status,
            code,
            message: "irrelevant".into(),
            trace_id: None,
            data: None,
        }
    }

    fn reviewed_markdown(
        generation: Option<uuid::Uuid>,
    ) -> (serde_json::Value, String, Option<QuestionOffer>) {
        let answer = sample(&format!(
            "**铁块**电梯需要安全的落点。\n\n[{QUESTION}](ask:)"
        ));
        let rendered = assistant::reply(&answer, Locale::get("zh-CN"), generation, "/");
        let ReplyBody::QqMarkdown {
            markdown,
            plain,
            offer,
            ..
        } = rendered.message.body
        else {
            panic!("expected a reviewed Markdown projection");
        };
        assert!(rendered.review.starts_with(&plain));
        assert!(plain.ends_with(&format!("\n/ai {QUESTION}")));
        (markdown, plain, offer)
    }

    #[tokio::test]
    async fn replaced_generation_sends_the_same_reviewed_markdown_without_a_keyboard() {
        let owner = AssistantConversation {
            platform: "qq",
            account: "app".into(),
            room: "group".into(),
            thread: None,
            sender: "owner".into(),
        };
        let now = Instant::now();
        let offers = QuestionOffers::default();
        let generation = offers.begin(owner.clone(), now).unwrap();
        let (markdown, plain, offer) = reviewed_markdown(Some(generation));
        let offer = offer.unwrap();
        assert_ne!(offers.begin(owner.clone(), now).unwrap(), generation);
        let rich = markdown_request(markdown.clone(), Some(&offer), |offer| {
            let (keyboard, keys) =
                keyboard::offer(&owner.sender, offer.questions(), "Unavailable")?;
            offers.offer(&owner, offer, keys, now).then_some(keyboard)
        });
        let mut sent = Vec::new();
        let format = send_markdown(rich, plain, |request| {
            sent.push(request);
            ready(Ok(()))
        })
        .await
        .unwrap();
        assert_eq!(format, SentFormat::Markdown);
        assert_eq!(sent.len(), 1);
        assert_eq!(sent[0].msg_type, Some(2));
        assert_eq!(sent[0].markdown.as_ref(), Some(&markdown));
        assert!(sent[0].keyboard.is_none());
    }

    #[tokio::test]
    async fn rejected_markdown_does_not_record_links_omitted_by_the_fallback() {
        let tail = "a".repeat(Platform::Qq.text_limit() - "Guide ".len());
        let visible = format!("Guide {tail}");
        let answer = sample(&format!("[Guide](ref:r1) {tail}"));
        let rendered = assistant::reply(&answer, Locale::get("en-US"), None, "/");
        let ReplyBody::QqMarkdown {
            markdown,
            plain,
            plain_visible,
            ..
        } = rendered.message.body
        else {
            panic!("expected both Markdown and plain projections");
        };
        let url = "https://mcmik.top/wiki";
        assert!(markdown["content"].as_str().unwrap().contains(url));
        assert!(rendered.review.contains(url));
        assert_eq!(plain, visible);
        assert_eq!(
            serde_json::to_value(rendered.message.visible_answer).unwrap()["references"][0]["url"],
            url
        );

        let mut sent = Vec::new();
        let format = send_markdown(
            markdown_request(markdown, None, |_| None),
            plain,
            |request| {
                let result = if sent.is_empty() {
                    Err(api(403, 40_034_127))
                } else {
                    Ok(())
                };
                sent.push(request);
                ready(result)
            },
        )
        .await
        .unwrap();
        assert_eq!(format, SentFormat::Plain);
        assert_eq!(sent.len(), 2);
        assert_eq!(sent[1].content.as_deref(), Some(visible.as_str()));
        assert_eq!(
            serde_json::to_value(plain_visible).unwrap(),
            serde_json::json!({"reply": visible})
        );
    }

    #[tokio::test]
    async fn only_a_definite_rejection_sends_the_reviewed_fallback_once() {
        for (with_keyboard, first, retry) in [
            (true, Ok(()), None),
            (true, Err(api(400, 304_037)), Some(SentFormat::Markdown)),
            (false, Err(api(403, 40_034_127)), Some(SentFormat::Plain)),
            (true, Err(api(200, 304_024)), None),
            (
                true,
                Err(SdkError::Timeout("response not received".into())),
                None,
            ),
        ] {
            let (markdown, plain, offer) =
                reviewed_markdown(with_keyboard.then(uuid::Uuid::new_v4));
            let rich = markdown_request(markdown.clone(), offer.as_ref(), |offer| {
                keyboard::offer("owner", offer.questions(), "Unavailable")
                    .map(|(keyboard, _)| keyboard)
            });
            let error_expected = first.is_err() && retry.is_none();
            let mut first = Some(first);
            let mut sent = Vec::new();
            let result = send_markdown(rich, plain.clone(), |request| {
                sent.push(request);
                ready(first.take().unwrap_or(Ok(())))
            })
            .await;
            assert_eq!(result.is_err(), error_expected);
            if !error_expected {
                assert_eq!(result.unwrap(), retry.unwrap_or(SentFormat::Markdown));
            }
            assert_eq!(sent.len(), if retry.is_some() { 2 } else { 1 });
            assert_eq!(sent[0].msg_type, Some(2));
            assert_eq!(sent[0].markdown.as_ref(), Some(&markdown));
            assert_eq!(sent[0].keyboard.is_some(), with_keyboard);
            if retry == Some(SentFormat::Plain) {
                assert_eq!(sent[1].msg_type, Some(0));
                assert_eq!(sent[1].content.as_deref(), Some(plain.as_str()));
                assert!(sent[1].keyboard.is_none() && sent[1].markdown.is_none());
            }
            if retry == Some(SentFormat::Markdown) {
                assert_eq!(sent[1].markdown.as_ref(), Some(&markdown));
                assert!(sent[1].keyboard.is_none());
            }
        }
    }

    #[test]
    fn fallback_is_only_for_definite_shape_or_format_permission_rejections() {
        assert!(keyboard_rejected(&api(400, 304_037)));
        assert!(!format_rejected(&api(400, 304_037)));
        assert!(format_rejected(&api(403, 40_034_127)));
        assert!(keyboard_rejected(&api(200, 40_034_029)));
        for error in [
            api(400, -1),
            api(403, -1),
            api(408, 304_037),
            api(429, 304_037),
            api(502, 304_037),
            api(400, 40_034_006),
            api(200, 304_024),
            api(400, 40_034_026),
            SdkError::Timeout("timeout".into()),
        ] {
            assert!(!format_rejected(&error) && !keyboard_rejected(&error));
        }
    }
}
