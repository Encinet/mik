use super::{Platform, clip};
use crate::domains::{
    chat::Committed,
    community::{Binding, BindingIndex},
    social::{config::Conversation, locale::Locale},
};
use mik_protocol::wire::{
    ChatExternalIdentity, ChatPart, ChatTextStyle, chat_part::Content, chat_user_record::Source,
};
use std::{borrow::Cow, sync::Arc};
use uuid::Uuid;

type StyledText<'a> = (Cow<'a, str>, Option<ChatTextStyle>);

pub(in crate::domains::social) struct Outbound<'a> {
    name: &'a str,
    platform: &'a str,
    pub(in crate::domains::social) parts: &'a [ChatPart],
    leading: StyledText<'a>,
    pub(in crate::domains::social) message_id: &'a str,
}

impl<'a> Outbound<'a> {
    pub(in crate::domains::social) fn for_conversation(
        delivery: &'a Committed,
        platform: Platform,
        conversation: &str,
        route: &Conversation,
    ) -> Option<Self> {
        let (message_id, user) = delivery.public_post()?;
        if user.social.as_ref().is_some_and(|origin| {
            origin.platform_id == platform.id() && origin.conversation_id == conversation
        }) {
            return None;
        }
        let direction = route.outbound.as_ref()?;
        let author = user.author.as_ref()?;
        let mut parts = user.body.as_ref()?.parts.as_slice();
        let mut leading = StyledText::default();
        if direction.strip || !direction.prefix.is_empty() {
            let (suffix, rest) = after_prefix(parts, &direction.prefix)?;
            if direction.strip {
                leading = suffix;
                parts = rest;
            }
        }
        if leading.0.is_empty() && parts.is_empty() {
            return None;
        }
        Some(Self {
            name: author
                .player
                .as_ref()
                .map_or(&author.display_name, |player| &player.name),
            platform: user.social.as_ref().map_or(
                if user.source() == Source::Web {
                    "Web"
                } else {
                    "Minecraft"
                },
                |source| source.platform_name.as_str(),
            ),
            parts,
            leading,
            message_id,
        })
    }

    pub(in crate::domains::social) fn render(
        &self,
        platform: Platform,
        locale: &Locale,
        mut write: impl FnMut(&str, Option<&Content>, Option<ChatTextStyle>),
    ) {
        let limit = platform.text_limit();
        let header = format!("[{}] {} » ", self.platform, self.name);
        let header = clip(&header, limit);
        write(header, None, None);
        let mut remaining = limit - header.chars().count();
        let (leading, style) = &self.leading;
        if !leading.is_empty() {
            let visible = clip(leading, remaining);
            write(visible, None, *style);
            remaining -= visible.chars().count();
        }
        for part in self.parts {
            if remaining == 0 {
                break;
            }
            let Some(content) = part.content.as_ref() else {
                continue;
            };
            let label = localized_label(content, locale);
            let visible = clip(&label, remaining);
            if visible.len() != label.len() {
                if matches!(content, Content::Text(_)) {
                    write(visible, None, part.style);
                }
                break;
            }
            write(visible, Some(content), part.style);
            remaining -= visible.chars().count();
        }
    }
}

fn after_prefix<'a>(
    parts: &'a [ChatPart],
    mut prefix: &str,
) -> Option<(StyledText<'a>, &'a [ChatPart])> {
    let mut at_start = true;
    for (index, part) in parts.iter().enumerate() {
        let Some(content) = part.content.as_ref() else {
            continue;
        };
        if prefix.is_empty() && !matches!(content, Content::Text(_)) {
            return Some((StyledText::default(), &parts[index..]));
        }
        let label = label(content);
        let candidate = if at_start {
            label.trim_start()
        } else {
            &*label
        };
        if candidate.is_empty() {
            continue;
        }
        if let Some(rest) = candidate.strip_prefix(prefix) {
            let remaining = rest.trim_start().len();
            if remaining == 0 {
                prefix = "";
                continue;
            }
            if prefix.is_empty() && remaining == label.len() {
                return Some((StyledText::default(), &parts[index..]));
            }
            let skip = label.len() - remaining;
            let rest = match label {
                Cow::Borrowed(value) => Cow::Borrowed(&value[skip..]),
                Cow::Owned(mut value) => {
                    value.drain(..skip);
                    Cow::Owned(value)
                }
            };
            return Some(((rest, part.style), &parts[index + 1..]));
        }
        prefix = prefix.strip_prefix(candidate)?;
        at_start = false;
    }
    prefix.is_empty().then_some((StyledText::default(), &[]))
}

fn label(content: &Content) -> Cow<'_, str> {
    match content {
        Content::Text(value) | Content::Broadcast(value) | Content::ItemLabel(value) => {
            Cow::Borrowed(value)
        }
        Content::Link(link) => Cow::Borrowed(if link.label.is_empty() {
            &link.url
        } else {
            &link.label
        }),
        Content::PlayerMention(mention) => mention
            .player
            .as_ref()
            .map(|player| Cow::Owned(format!("@{}", player.name)))
            .unwrap_or_default(),
        Content::ExternalMention(mention) => Cow::Owned(format!("@{}", mention.display_name)),
        Content::Description(description) => Cow::Borrowed(&description.fallback),
    }
}

fn localized_label<'a>(content: &'a Content, locale: &Locale) -> Cow<'a, str> {
    if let Content::Description(description) = content {
        let key = format!(
            "chat-content-{}",
            description.kind().as_str_name().to_ascii_lowercase()
        );
        let localized = locale.text(&key);
        if localized != key {
            return Cow::Owned(localized);
        }
    }
    label(content)
}

#[derive(Default)]
pub(in crate::domains::social) struct OutputText {
    labels: String,
    links: Vec<(usize, String)>,
}

impl OutputText {
    pub(in crate::domains::social) fn push(&mut self, label: &str, destination: Option<String>) {
        self.labels.push_str(label);
        if let Some(destination) = destination.filter(|url| url != label) {
            self.links.push((self.labels.len(), destination));
        }
    }

    pub(in crate::domains::social) fn within(&self, limit: usize) -> String {
        let mut remaining = limit.saturating_sub(self.labels.chars().count());
        if self.links.is_empty() || remaining == 0 {
            return self.labels.clone();
        }
        let mut result = String::with_capacity(self.labels.len());
        let mut offset = 0;
        for (end, url) in &self.links {
            let size = url.chars().count() + 3;
            if size <= remaining {
                result.push_str(&self.labels[offset..*end]);
                result.push_str(" (");
                result.push_str(url);
                result.push(')');
                offset = *end;
                remaining -= size;
            }
        }
        result.push_str(&self.labels[offset..]);
        result
    }

    pub(in crate::domains::social) fn full(&self) -> String {
        self.within(usize::MAX)
    }
}

pub(in crate::domains::social) enum MentionTarget<'message> {
    Bound(Arc<Binding>),
    External(&'message ChatExternalIdentity),
}

impl MentionTarget<'_> {
    pub(in crate::domains::social) fn parts(&self) -> [&str; 3] {
        match self {
            Self::Bound(binding) => {
                let [_, issuer, scope, subject] = binding.external.parts();
                [issuer, scope, subject]
            }
            Self::External(identity) => [&identity.issuer, &identity.scope, &identity.subject],
        }
    }
}

pub(in crate::domains::social) fn targets<'message>(
    content: &'message Content,
    bindings: &BindingIndex,
    platform: Platform,
) -> impl Iterator<Item = MentionTarget<'message>> + use<'message> {
    let (bound, external) = match content {
        Content::PlayerMention(mention) => mention
            .player
            .as_ref()
            .and_then(|player| Uuid::parse_str(&player.id).ok())
            .map(|player| (bindings.player(player), None))
            .unwrap_or_default(),
        Content::ExternalMention(mention) if mention.platform == platform.id() => {
            (Vec::new(), Some(MentionTarget::External(mention)))
        }
        _ => (Vec::new(), None),
    };
    bound
        .into_iter()
        .filter_map(move |binding| {
            (binding.external.parts()[0] == platform.id()).then_some(MentionTarget::Bound(binding))
        })
        .chain(external)
}

#[cfg(test)]
mod tests {
    use super::*;
    use mik_protocol::wire::ChatLink;

    #[test]
    fn strips_by_shared_label_without_erasing_later_semantics() {
        let parts = [
            Content::Text(" #".into()),
            Content::Link(ChatLink {
                label: "# 你好 ".into(),
                url: "https://example.org".into(),
                ..Default::default()
            }),
            Content::ExternalMention(ChatExternalIdentity {
                display_name: "Alice".into(),
                ..Default::default()
            }),
        ]
        .map(|content| ChatPart {
            style: None,
            content: Some(content),
        });
        let (leading, rest) = after_prefix(&parts, "## ").unwrap();
        assert_eq!(leading.0, "你好 ");
        assert_eq!(rest, &parts[2..]);
        let (leading, rest) = after_prefix(&parts, "").unwrap();
        assert_eq!(leading.0, "#");
        assert_eq!(rest, &parts[1..]);
    }

    #[test]
    fn fallback_prefers_visible_labels_to_partial_addresses() {
        let mut text = OutputText::default();
        let url = "https://example.org/wiki";
        text.push("Wiki", Some(url.into()));
        text.push(" @Alice", None);
        assert_eq!(text.within(16), "Wiki @Alice");
        assert_eq!(text.within(usize::MAX), format!("Wiki ({url}) @Alice"));
    }
}
