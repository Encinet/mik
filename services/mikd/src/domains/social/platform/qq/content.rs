use super::{display_name, reply};
use crate::domains::social::chat::{self, Inline, Platform, SpannedNode};
use mik_protocol::wire::{ChatExternalIdentity, chat_description::Kind};
use qqbot_sdk_rs::{ArkData, MessageAttachment, MessageElement, User};
use regex::Regex;
use std::sync::LazyLock;

const MAX_INLINE: usize = 32;

#[expect(
    clippy::expect_used,
    reason = "The QQ token pattern is a source literal covered by projection tests"
)]
static TOKENS: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(
        r#"(?x)
        <@!?(?<mention>[^<>\s]+)>
        | <qqbot-at-user\s+id="(?<native>[^"<>\s]+)"\s*/>
        | (?<everyone><qqbot-at-everyone\s*/>)
        | <faceType=[0-9]+,faceId="[0-9]*"(?:,ext="[^"<>]*")?>
        | <emoji:[A-Za-z0-9_]+>
        "#,
    )
    .expect("QQ inline tokens")
});

pub(super) struct Projection {
    pub(super) text: String,
    pub(super) inline: Vec<SpannedNode>,
    pub(super) description: Option<Kind>,
    pub(super) commands: bool,
}

pub(super) fn project(
    issuer: &str,
    raw: &str,
    users: &[User],
    kind: Option<u16>,
    attachments: &[MessageAttachment],
    card: Option<&ArkData>,
    elements: &[MessageElement],
) -> Projection {
    let collection = match kind {
        Some(101) => Some(Kind::Multiple),
        Some(102) => Some(Kind::History),
        _ => None,
    };
    if let Some(kind) = collection {
        return Projection {
            text: String::new(),
            inline: Vec::new(),
            description: Some(kind),
            commands: false,
        };
    }
    let mut body = inline(issuer, raw, users);
    body.description = describe_attachments(kind, attachments, card);
    body.commands &= body.description.is_none();
    if body.description.is_none() {
        if elements.iter().any(reply::is_history) {
            body.description = Some(Kind::History);
        } else if kind == Some(reply::REPLY) && body.text.trim().is_empty() {
            body.description = Some(Kind::Reply);
        }
    }
    body
}

fn describe_attachments(
    kind: Option<u16>,
    attachments: &[MessageAttachment],
    card: Option<&ArkData>,
) -> Option<Kind> {
    let card = card
        .map(|card| match card.ark_type.as_deref() {
            Some("map") => Kind::Location,
            Some("contact_card") => Kind::Contact,
            Some("video_share") => Kind::Video,
            Some("music_together") => Kind::Music,
            Some("picture") => Kind::Image,
            _ => Kind::Card,
        })
        .or_else(|| (kind == Some(3)).then_some(Kind::Card));
    match (attachments, card) {
        ([], card) => card,
        ([file], None) => Some(
            match file
                .content_type
                .as_deref()
                .map(|mime| mime.split_once('/').map_or(mime, |(category, _)| category))
            {
                Some("image") => Kind::Image,
                Some("audio" | "voice") => Kind::Audio,
                Some("video") => Kind::Video,
                _ => Kind::File,
            },
        ),
        _ => Some(Kind::Multiple),
    }
}

fn inline(issuer: &str, raw: &str, users: &[User]) -> Projection {
    let mut text = String::new();
    let mut spans = Vec::new();
    let mut commands = true;
    let mut cursor = 0;
    let mut trim_after_bot = false;
    let face = chat::description(Kind::Face).fallback;
    for captures in TOKENS.captures_iter(raw) {
        let token = captures.get_match();
        let plain = &raw[cursor..token.start()];
        text.push_str(if trim_after_bot {
            plain.trim_start()
        } else {
            plain
        });
        cursor = token.end();
        trim_after_bot = false;
        let id = captures.name("mention").or_else(|| captures.name("native"));
        if captures.name("everyone").is_some() || id.is_some_and(|id| id.as_str() == "all") {
            text.push_str("@all");
        } else if let Some(id) = id {
            let id = id.as_str();
            let user = users.iter().take(MAX_INLINE).find(|user| {
                user.id.as_deref() == Some(id) || user.member_openid.as_deref() == Some(id)
            });
            match user {
                Some(user) if user.is_you == Some(true) && text.trim().is_empty() => {
                    text.clear();
                    trim_after_bot = true;
                }
                Some(user)
                    if user.is_you != Some(true)
                        && user.bot != Some(true)
                        && let Some(subject) = user.member_openid.as_ref() =>
                {
                    let start = text.len();
                    let name = display_name(user);
                    text.push('@');
                    text.push_str(&name);
                    if spans.len() < MAX_INLINE {
                        spans.push(SpannedNode {
                            range: start..text.len(),
                            style: None,
                            node: Inline::Mention(ChatExternalIdentity {
                                platform: Platform::Qq.id().into(),
                                issuer: issuer.into(),
                                subject: subject.clone(),
                                display_name: name,
                                ..Default::default()
                            }),
                        });
                    }
                }
                _ => text.push_str(token.as_str()),
            }
        } else {
            commands = false;
            let start = text.len();
            text.push_str(&face);
            if spans.len() < MAX_INLINE {
                spans.push(SpannedNode {
                    range: start..text.len(),
                    style: None,
                    node: Inline::Description(Kind::Face),
                });
            }
        }
    }
    let tail = &raw[cursor..];
    text.push_str(if trim_after_bot {
        tail.trim_start()
    } else {
        tail
    });
    Projection {
        text,
        inline: spans,
        description: None,
        commands,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn projects_qq_tokens_without_bridging_forwarded_content() {
        let members: Vec<User> = serde_json::from_str(
            r#"[{"id":"bot","is_you":true},{"id":"other","member_openid":"alice","member_nickname":"小爱"}]"#,
        ).unwrap();
        let raw = r#"<qqbot-at-user id="bot" /> # 你好 <faceType=1,faceId="32"> <@alice> <qqbot-at-user id="other"/> <qqbot-at-everyone /> 😀"#;
        let text = project("app", raw, &members, None, &[], None, &[]);
        assert_eq!(text.text, "# 你好 [face] @小爱 @小爱 @all 😀");
        assert!(!text.commands);
        assert_eq!(text.inline.len(), 3);
        assert!(matches!(
            text.inline[0].node,
            Inline::Description(Kind::Face)
        ));
        for span in &text.inline[1..] {
            assert_eq!(&text.text[span.range.clone()], "@小爱");
            assert!(matches!(&span.node, Inline::Mention(identity) if identity.subject == "alice"));
        }
        let forwarded = project("app", "/bind leaked", &members, Some(102), &[], None, &[]);
        assert!(forwarded.text.is_empty());
        assert_eq!(forwarded.description, Some(Kind::History));
        assert!(!forwarded.commands);
    }
}
