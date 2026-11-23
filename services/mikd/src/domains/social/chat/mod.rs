mod assistant;
mod outbound;

pub(super) use assistant::{assistant_layout, copyable_question, manual_questions};
pub(super) use outbound::{MentionTarget, Outbound, OutputText, targets};

use super::config::Conversation;
use crate::domains::ai::Conversation as AssistantConversation;
use crate::domains::chat::ChatService;
use crate::domains::community::{Binding, BindingIndex, ExternalKey};
use mik_protocol::wire::{
    ChatAuthor, ChatBody, ChatDescription, ChatExternalIdentity, ChatLink, ChatPart, ChatPlayer,
    ChatPlayerMention, ChatRecord, ChatSocialSource, ChatTextStyle, ChatUserRecord, HistoryChannel,
    HistoryEntry, chat_description, chat_part::Content, chat_record::Kind,
    chat_user_record::Source,
};
use std::{ops::Range, time::Duration};
use uuid::Uuid;

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

    pub(super) const fn label(self) -> &'static str {
        match self {
            Self::Qq => "QQ",
            Self::Matrix => "Matrix",
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
    pub(super) conversation: String,
    pub(super) event: String,
    pub(super) sender: String,
    pub(super) name: String,
    pub(super) content: String,
    pub(super) reply_to: Option<String>,
    pub(super) thread_root: Option<String>,
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
    Mention(Identity),
    Link(url::Url),
    Description(chat_description::Kind),
}

impl Inbound {
    pub(super) fn assistant_scope(&self) -> AssistantConversation {
        AssistantConversation {
            platform: self.platform.id(),
            account: self.account.clone(),
            room: self.conversation.clone(),
            thread: self.thread_root.clone(),
            sender: self.sender.clone(),
        }
    }

    pub(super) fn mentions(&self) -> impl Iterator<Item = (&Range<usize>, &ChatExternalIdentity)> {
        self.inline.iter().filter_map(|span| match &span.node {
            Inline::Mention(identity) => Some((&span.range, identity)),
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

pub(super) async fn publish(
    chat: &ChatService,
    event: Inbound,
    route: &Conversation,
    bindings: &BindingIndex,
) {
    let Some(direction) = route.inbound.as_ref() else {
        return;
    };
    let Some(text) = direction.accept(&event.content, event.description.is_some()) else {
        return;
    };
    let start = event.content.len() - text.len();
    let text = clip(text, 4000);
    let id = Uuid::new_v4().to_string();
    let external = event.external();
    let player = binding_key(&external)
        .and_then(|key| bindings.find(&key))
        .map(|binding| player(&binding));
    if player.is_some() && !chat.wait_for_game(Duration::from_millis(750)).await {
        eprintln!("mikd social chat: Paper visibility roster unavailable for a bound sender");
        return;
    }
    let origin = ChatSocialSource {
        platform_id: event.platform.id().into(),
        platform_name: event.platform.label().into(),
        color: match event.platform {
            Platform::Qq => 0x12_b7_f5,
            Platform::Matrix => 0x0d_bd_8b,
        },
        conversation_id: event.conversation,
        event_id: event.event,
        reply_to_event_id: event.reply_to,
        thread_root_event_id: event.thread_root,
    };
    let mut parts = inline_parts(text, start, event.inline, bindings);
    if let Some(kind) = event.description {
        let description = description(kind);
        if !text.is_empty() {
            parts.push(ChatPart {
                style: None,
                content: Some(Content::Text(" ".into())),
            });
        }
        parts.push(ChatPart {
            style: None,
            content: Some(Content::Description(description)),
        });
    }
    let record = ChatRecord {
        created_at: crate::domains::chat::now(),
        kind: Some(Kind::User(ChatUserRecord {
            source: Source::Social.into(),
            author: Some(ChatAuthor {
                display_name: event.name,
                external: Some(external),
                player,
            }),
            social: Some(origin),
            body: Some(ChatBody { parts }),
            ..Default::default()
        })),
    };
    match tokio::time::timeout(
        Duration::from_secs(2),
        chat.publish(HistoryEntry {
            message_id: id,
            channel: HistoryChannel::HistoryPublic.into(),
            record: Some(record),
            ..Default::default()
        }),
    )
    .await
    {
        Ok(Ok(_)) => (),
        Ok(Err(error)) => eprintln!("mikd social chat: publication failed: {error}"),
        Err(_) => eprintln!("mikd social chat: publication confirmation timed out"),
    }
}

pub(super) fn description(kind: chat_description::Kind) -> ChatDescription {
    ChatDescription {
        kind: kind.into(),
        fallback: format!("[{}]", kind.as_str_name().to_ascii_lowercase()),
    }
}

fn inline_parts(
    text: &str,
    start: usize,
    inline: Vec<SpannedNode>,
    bindings: &BindingIndex,
) -> Vec<ChatPart> {
    let mut parts = Vec::new();
    let mut head = 0;
    let end = start + text.len();
    for SpannedNode {
        mut range,
        node,
        style,
    } in inline
    {
        if matches!(node, Inline::Text) {
            range.start = range.start.max(start);
            range.end = range.end.min(end);
        }
        if range.start < start + head || range.end > end || range.is_empty() {
            continue;
        }
        let offset = range.start - start;
        let tail = range.end - start;
        if offset > head {
            parts.push(ChatPart {
                style: None,
                content: Some(Content::Text(text[head..offset].into())),
            });
        }
        parts.push(ChatPart {
            style,
            content: Some(match node {
                Inline::Text => Content::Text(text[offset..tail].into()),
                Inline::Mention(identity) => match binding_key(&identity)
                    .and_then(|key| bindings.find(&key))
                    .map(|binding| player(&binding))
                {
                    Some(player) => Content::PlayerMention(ChatPlayerMention {
                        player: Some(player),
                        source_identity: Some(identity),
                    }),
                    None => Content::ExternalMention(identity),
                },
                Inline::Link(url) => Content::Link(ChatLink {
                    label: text[offset..tail].into(),
                    url: url.into(),
                    ..Default::default()
                }),
                Inline::Description(kind) => Content::Description(description(kind)),
            }),
        });
        head = tail;
    }
    if head < text.len() {
        parts.push(ChatPart {
            style: None,
            content: Some(Content::Text(text[head..].into())),
        });
    }
    parts
}

pub(super) fn binding_key(identity: &ChatExternalIdentity) -> Option<ExternalKey> {
    ExternalKey::new(
        &identity.platform,
        identity.issuer.clone(),
        identity.scope.clone(),
        identity.subject.clone(),
    )
    .ok()
}

pub(super) fn player(binding: &Binding) -> ChatPlayer {
    ChatPlayer {
        id: binding.account.to_string(),
        name: binding.player_name.clone(),
    }
}
