mod assistant;
mod outbound;

pub(super) use assistant::{
    assistant_layout, assistant_thinking, copyable_question, manual_questions,
};
#[cfg(test)]
pub(super) use outbound::fixtures;
pub(super) use outbound::{MentionTarget, Outbound, OutputText, targets};

use super::config::Conversation;
use crate::domains::ai::Conversation as AssistantConversation;
use crate::domains::chat::{
    BodyInput, ChatService, NativeEvent, external_principal, link_reference, person_mention,
};
use mik_protocol::wire::{
    ChatDescription, ChatExternalIdentity, ChatPart, ChatTextRun, ChatTextStyle, chat_description,
    chat_part::Content,
};
use std::{ops::Range, time::Duration};

#[derive(Clone, Copy, PartialEq, Eq, Hash)]
pub(super) enum Platform {
    Qq,
    Matrix,
}

impl Platform {
    pub(super) const fn id(self) -> &'static str {
        match self {
            Self::Qq => "qq",
            Self::Matrix => "matrix",
        }
    }

    pub(super) const fn text_limit(self) -> usize {
        match self {
            Self::Qq => 1500,
            Self::Matrix => 6000,
        }
    }
}

pub(super) fn escape_qq(text: &str) -> String {
    text.replace('<', "＜").replace('>', "＞")
}

pub(super) fn reply_text(platform: Platform, text: &str) -> String {
    let text = clip(text, platform.text_limit());
    match platform {
        Platform::Qq => escape_qq(text),
        Platform::Matrix => text.into(),
    }
}

pub(super) fn clip(text: &str, limit: usize) -> &str {
    text.char_indices()
        .nth(limit)
        .map_or(text, |(end, _)| &text[..end])
}

pub(super) struct Inbound {
    pub(super) platform: Platform,
    pub(super) account: String,
    pub(super) native: NativeEvent,
    pub(super) sender: String,
    pub(super) name: String,
    pub(super) content: String,
    pub(super) assistant_thread: Option<String>,
    pub(super) inline: Vec<SpannedNode>,
    pub(super) description: Option<chat_description::Kind>,
    pub(super) commands: bool,
}

pub(super) struct SpannedNode<Identity = ChatExternalIdentity> {
    pub(super) range: Range<usize>,
    pub(super) node: Inline<Identity>,
    pub(super) style: Option<ChatTextStyle>,
}

pub(super) enum Inline<Identity = ChatExternalIdentity> {
    Text,
    Verbatim,
    Reference {
        target: ReferenceTarget<Identity>,
        runs: Vec<ChatTextRun>,
    },
    Description(chat_description::Kind),
}

pub(super) enum ReferenceTarget<Identity> {
    Link(String),
    Person(Identity),
}

impl<Identity> Inline<Identity> {
    pub(super) fn person(identity: Identity) -> Self {
        Self::Reference {
            target: ReferenceTarget::Person(identity),
            runs: Vec::new(),
        }
    }
}

impl Inbound {
    pub(super) fn conversation(&self) -> &str {
        &self.native.source.id
    }

    pub(super) fn assistant_scope(&self) -> AssistantConversation {
        AssistantConversation {
            platform: self.platform.id(),
            account: self.account.clone(),
            room: self.native.source.id.clone(),
            thread: self.assistant_thread.clone(),
            sender: self.sender.clone(),
        }
    }

    pub(super) fn mentions(&self) -> impl Iterator<Item = (&Range<usize>, &ChatExternalIdentity)> {
        self.inline.iter().filter_map(|span| match &span.node {
            Inline::Reference {
                target: ReferenceTarget::Person(identity),
                ..
            } => Some((&span.range, identity)),
            _ => None,
        })
    }

    pub(super) fn external(&self) -> ChatExternalIdentity {
        ChatExternalIdentity {
            platform: self.platform.id().into(),
            issuer: self.account.clone(),
            subject: self.sender.clone(),
            display_name: self.name.clone(),
            ..Default::default()
        }
    }
}

pub(super) async fn publish(chat: &ChatService, event: Inbound, route: &Conversation) {
    let Some(direction) = route.inbound.as_ref() else {
        return;
    };
    let leading = event
        .inline
        .iter()
        .find(|span| !matches!(span.node, Inline::Text))
        .map_or(event.content.len(), |span| span.range.start);
    let Some(consumed) = event
        .content
        .get(..leading)
        .and_then(|plain| direction.prefix_bytes([plain]))
    else {
        return;
    };
    let mut start = if direction.strip { consumed } else { 0 };
    if event.content[start..].trim().is_empty() {
        if event.description.is_none() {
            return;
        }
        start = event.content.len();
    }
    let text = clip(&event.content[start..], 4000);
    let author = event.external();
    let mut parts = inline_parts(text, start, event.inline);
    if let Some(kind) = event.description {
        if !text.is_empty() {
            parts.push(BodyInput::Text(ChatTextRun {
                text: " ".into(),
                style: None,
            }));
        }
        parts.push(resolved(Content::Description(description(kind, None))));
    }
    match tokio::time::timeout(
        Duration::from_secs(2),
        chat.publish_native(author, event.native, parts),
    )
    .await
    {
        Ok(Ok(())) => (),
        Ok(Err(error)) => eprintln!("mikd social chat: publication failed: {error}"),
        Err(_) => eprintln!("mikd social chat: publication confirmation timed out"),
    }
}

fn description(kind: chat_description::Kind, style: Option<ChatTextStyle>) -> ChatDescription {
    ChatDescription {
        label: Some(chat_description::Label::Kind(kind.into())),
        style,
    }
}

fn resolved(content: Content) -> BodyInput {
    BodyInput::Resolved(ChatPart {
        content: Some(content),
    })
}

fn inline_parts(text: &str, start: usize, inline: Vec<SpannedNode>) -> Vec<BodyInput> {
    let mut parts = Vec::new();
    let mut head = 0;
    let end = start + text.len();
    for SpannedNode {
        mut range,
        node,
        style,
    } in inline
    {
        let clipped = range.start < start || range.end > end;
        range.start = range.start.max(start);
        range.end = range.end.min(end);
        let node = if clipped && !matches!(node, Inline::Text | Inline::Verbatim) {
            Inline::Verbatim
        } else {
            node
        };
        if range.start < start + head || range.is_empty() {
            continue;
        }
        let offset = range.start - start;
        let tail = range.end - start;
        if offset > head {
            parts.push(BodyInput::Text(ChatTextRun {
                text: text[head..offset].into(),
                style: None,
            }));
        }
        let run = || ChatTextRun {
            text: text[offset..tail].into(),
            style,
        };
        parts.push(match node {
            Inline::Text => BodyInput::Text(run()),
            Inline::Verbatim => resolved(Content::Text(run())),
            Inline::Reference { target, mut runs } => {
                if runs.is_empty() {
                    runs.push(run());
                }
                resolved(match target {
                    ReferenceTarget::Link(uri) => {
                        Content::Reference(link_reference(uri, runs, false))
                    }
                    ReferenceTarget::Person(person) => {
                        person_mention(external_principal(person, None), runs)
                    }
                })
            }
            Inline::Description(kind) => resolved(Content::Description(description(kind, style))),
        });
        head = tail;
    }
    if head < text.len() {
        parts.push(BodyInput::Text(ChatTextRun {
            text: text[head..].into(),
            style: None,
        }));
    }
    parts
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::domains::{
        chat::{ConversationAddress, HistoryStore},
        social::config::Direction,
    };
    use std::{fs::Permissions, os::unix::fs::PermissionsExt};

    #[tokio::test]
    async fn inbound_route_only_matches_ordinary_leading_text() {
        let directory = tempfile::Builder::new()
            .permissions(Permissions::from_mode(0o700))
            .tempdir()
            .unwrap();
        let chat = ChatService::new(
            HistoryStore::open(&directory.path().join("chat.db"))
                .await
                .unwrap(),
            None,
        );
        let mut live = chat.subscribe();
        let route = Conversation {
            inbound: Some(Direction {
                prefix: "#".into(),
                strip: true,
            }),
            ..Default::default()
        };
        let label = " # 钻石";
        let uri = "https://minecraft.wiki/w/Diamond";
        let incoming = |prefix: &str, node| Inbound {
            platform: Platform::Matrix,
            account: "example.org".into(),
            native: NativeEvent {
                source: ConversationAddress {
                    namespace: String::new(),
                    id: "room".into(),
                },
                event_id: "event".into(),
                event_scope: "room".into(),
            },
            sender: "@alice:example.org".into(),
            name: "Alice".into(),
            content: format!("{prefix}{label}"),
            assistant_thread: None,
            inline: vec![SpannedNode {
                range: prefix.len()..prefix.len() + label.len(),
                node,
                style: None,
            }],
            description: None,
            commands: false,
        };

        let link = || Inline::Reference {
            target: ReferenceTarget::Link(uri.into()),
            runs: Vec::new(),
        };
        for node in [link(), Inline::Verbatim] {
            publish(&chat, incoming("", node), &route).await;
            assert!(matches!(
                live.try_recv(),
                Err(tokio::sync::broadcast::error::TryRecvError::Empty)
            ));
        }

        publish(&chat, incoming("  # ", link()), &route).await;
        let delivery = live.try_recv().unwrap();
        let (_, _, user) = delivery.public_post().unwrap();
        assert_eq!(
            user.body.as_ref().unwrap().parts,
            [ChatPart {
                content: Some(Content::Reference(link_reference(
                    uri,
                    vec![ChatTextRun {
                        text: label.into(),
                        style: None,
                    }],
                    false
                )))
            }]
        );
        chat.close().await.unwrap();
    }
}
