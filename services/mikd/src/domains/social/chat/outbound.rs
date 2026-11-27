use super::{Platform, clip};
use crate::domains::{
    chat::{
        Committed, current_player, description_label, external_account, external_name, label_text,
        mentioned_person, player_snapshot, social_platform_label,
    },
    community::{Binding, BindingIndex},
    social::{
        config::{Conversation, Direction},
        locale::Locale,
    },
};
use mik_protocol::wire::{
    ChatExternalIdentity, ChatPart, ChatTextStyle, chat_description, chat_part::Content,
};
use std::{borrow::Cow, sync::Arc};

type StyledText<'a> = (&'a str, Option<ChatTextStyle>);

pub(in crate::domains::social) struct Outbound<'a> {
    name: &'a str,
    platform: &'a str,
    parts: &'a [ChatPart],
    leading: StyledText<'a>,
    pub(in crate::domains::social) message_id: &'a str,
}

impl<'a> Outbound<'a> {
    pub(in crate::domains::social) fn for_conversation(
        delivery: &'a Committed,
        platform: Platform,
        namespace: &str,
        conversation: &str,
        route: &Conversation,
    ) -> Option<Self> {
        let (message_id, source, user) = delivery.public_post()?;
        let author = user.author.as_ref()?;
        if source == platform.id()
            && delivery
                .source
                .as_ref()
                .is_some_and(|source| source.namespace == namespace && source.id == conversation)
        {
            return None;
        }
        let origin_label = match source {
            "game" => "Minecraft",
            "web" => "Web",
            other => social_platform_label(other),
        };
        let direction = route.outbound.as_ref()?;
        let parts = &user.body.as_ref()?.parts;
        let (leading, parts) = if direction.strip || !direction.prefix.is_empty() {
            route_body(parts, direction)?
        } else {
            (StyledText::default(), parts.as_slice())
        };
        if leading.0.is_empty() && parts.is_empty() {
            return None;
        }
        Some(Self {
            name: if let Some(player) = player_snapshot(author) {
                &player.name
            } else {
                external_name(external_account(author)?)
            },
            platform: origin_label,
            parts,
            leading,
            message_id,
        })
    }

    pub(in crate::domains::social) fn render(
        &self,
        platform: Platform,
        locale: &Locale,
        mut write: impl FnMut(&str, Option<&'a Content>, Option<ChatTextStyle>),
    ) {
        let limit = platform.text_limit();
        let header = format!("[{}] {} » ", self.platform, self.name);
        let header = clip(&header, limit);
        write(header, None, None);
        let mut remaining = limit - header.chars().count();
        let (leading, leading_style) = &self.leading;
        if !leading.is_empty() {
            let visible = clip(leading, remaining);
            write(visible, None, *leading_style);
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
                write(visible, None, style(content));
                break;
            }
            write(visible, Some(content), style(content));
            remaining -= visible.chars().count();
        }
    }
}

fn route_body<'a>(
    mut parts: &'a [ChatPart],
    direction: &Direction,
) -> Option<(StyledText<'a>, &'a [ChatPart])> {
    let mut consumed =
        direction.prefix_bytes(parts.iter().map_while(as_text).map(|run| run.text.as_str()))?;
    if direction.strip {
        while consumed > 0 {
            let (part, tail) = parts.split_first()?;
            let run = as_text(part)?;
            if consumed < run.text.len() {
                return Some(((&run.text[consumed..], run.style), tail));
            }
            consumed -= run.text.len();
            parts = tail;
        }
    }
    Some((StyledText::default(), parts))
}

fn as_text(part: &ChatPart) -> Option<&mik_protocol::wire::ChatTextRun> {
    match &part.content {
        Some(Content::Text(run)) => Some(run),
        _ => None,
    }
}

fn style(content: &Content) -> Option<ChatTextStyle> {
    match content {
        Content::Text(run) => run.style,
        Content::Description(description) => description.style,
        Content::Reference(_) => None,
    }
}

fn label(content: &Content) -> Cow<'_, str> {
    match content {
        Content::Text(run) => Cow::Borrowed(&run.text),
        Content::Reference(reference) => Cow::Owned(label_text(&reference.runs)),
        Content::Description(description) => description_label(description),
    }
}

fn localized_label<'a>(content: &'a Content, locale: &Locale) -> Cow<'a, str> {
    if let Content::Description(description) = content
        && let Some(chat_description::Label::Kind(kind)) = description.label
        && let Ok(kind) = chat_description::Kind::try_from(kind)
    {
        let key = format!("chat-content-{}", kind.as_str_name().to_ascii_lowercase());
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
    issuer: Option<&str>,
) -> impl Iterator<Item = MentionTarget<'message>> + use<'message> {
    let person = mentioned_person(content);
    let external = person.and_then(external_account);
    let in_namespace = |platform_id: &str, issuer_id: &str| {
        platform_id == platform.id() && issuer.is_none_or(|issuer| issuer == issuer_id)
    };
    let direct = external.filter(|e| in_namespace(&e.platform, &e.issuer));
    let bound = person
        .filter(|_| direct.is_none())
        .and_then(|person| current_player(person, Some(bindings)))
        .map(|id| {
            let mut accounts = bindings.player(id);
            accounts.retain(|binding| {
                let [platform, issuer, ..] = binding.external.parts();
                in_namespace(platform, issuer)
            });
            accounts
        })
        .unwrap_or_default();
    bound
        .into_iter()
        .map(MentionTarget::Bound)
        .chain(direct.map(MentionTarget::External))
}

#[cfg(test)]
pub(in crate::domains::social) mod fixtures {
    use super::*;
    use crate::domains::community::{CommunityStore, Membership, Redemption};
    use uuid::Uuid;

    pub(in crate::domains::social) fn post(parts: &[ChatPart]) -> Outbound<'_> {
        Outbound {
            name: "Sender",
            platform: "Minecraft",
            parts,
            leading: StyledText::default(),
            message_id: "test",
        }
    }

    pub(in crate::domains::social) async fn bind(
        store: &CommunityStore,
        player: Uuid,
        external: &ChatExternalIdentity,
    ) {
        let code = store
            .issue_link_code(player, "Alice".into(), &external.platform, || true)
            .await
            .unwrap()
            .code;
        let key = crate::domains::chat::binding_key(external).unwrap();
        assert!(matches!(
            store
                .redeem_link_code(Uuid::new_v4(), key, "Same name", "", Some(code), |_| Some(
                    Membership::Joined
                ))
                .await
                .unwrap(),
            Redemption::Applied(_)
        ));
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::domains::chat::link_reference;

    #[test]
    fn routing_consumes_only_leading_text_and_keeps_whole_semantic_spans() {
        use crate::domains::chat::text_part;
        let style = Some(ChatTextStyle {
            bold: true,
            ..Default::default()
        });
        let parts = [
            text_part(" #", None),
            text_part("#  ", style),
            ChatPart {
                content: Some(Content::Reference(link_reference(
                    "https://example.org",
                    vec![mik_protocol::wire::ChatTextRun {
                        text: "  你好".into(),
                        style: None,
                    }],
                    false,
                ))),
            },
        ];
        let rule = |prefix: &str| Direction {
            prefix: prefix.into(),
            strip: true,
        };
        let (leading, rest) = route_body(&parts, &rule("##")).unwrap();
        assert_eq!(leading, StyledText::default());
        assert_eq!(rest, &parts[2..]);
        assert!(route_body(&parts, &rule("##    你好")).is_none());

        let (leading, rest) = route_body(&parts, &rule("#")).unwrap();
        assert_eq!(leading, StyledText::default());
        assert_eq!(rest, &parts[1..]);
        let styled = [text_part(" #", None), text_part("#  你好", style)];
        let (leading, rest) = route_body(&styled, &rule("##")).unwrap();
        assert_eq!(leading, ("你好", style));
        assert!(rest.is_empty());
        assert!(route_body(&parts[2..], &rule("你好")).is_none());
    }

    #[tokio::test]
    async fn native_mention_never_switches_to_another_account_or_a_new_binding() {
        use crate::domains::community::CommunityStore;
        use mik_protocol::wire::{ChatPlayer, ChatTextRun};
        use std::{fs::Permissions, os::unix::fs::PermissionsExt};
        use uuid::Uuid;
        let temp = tempfile::Builder::new()
            .permissions(Permissions::from_mode(0o700))
            .tempdir()
            .unwrap();
        let store = CommunityStore::open(&temp.path().join("community.db"))
            .await
            .unwrap();
        let id = Uuid::new_v4();
        let original = ChatExternalIdentity {
            platform: "matrix".into(),
            issuer: "example.org".into(),
            subject: "@alice:example.org".into(),
            display_name: "Same name".into(),
            ..Default::default()
        };
        for external in [
            original.clone(),
            ChatExternalIdentity {
                subject: "@alternate:other.org".into(),
                issuer: "other.org".into(),
                ..original.clone()
            },
            ChatExternalIdentity {
                platform: "qq".into(),
                issuer: "app".into(),
                subject: "member".into(),
                ..Default::default()
            },
            ChatExternalIdentity {
                platform: "qq".into(),
                issuer: "other-app".into(),
                subject: "other-member".into(),
                ..Default::default()
            },
        ] {
            fixtures::bind(&store, id, &external).await;
        }
        let make = |snapshot: Uuid| {
            crate::domains::chat::person_mention(
                crate::domains::chat::external_principal(
                    original.clone(),
                    Some(ChatPlayer {
                        id: snapshot.to_string(),
                        name: "Alice".into(),
                    }),
                ),
                vec![ChatTextRun {
                    text: "看这里".into(),
                    style: None,
                }],
            )
        };
        let bindings = store.bindings();
        assert_player_targets(id, bindings);

        let current = make(id);
        let direct: Vec<_> = targets(&current, bindings, Platform::Matrix, None).collect();
        assert!(
            matches!(direct.as_slice(),[MentionTarget::External(account)] if **account == original)
        );
        assert_eq!(
            targets(&current, bindings, Platform::Qq, Some("app")).count(),
            1
        );
        let stale = make(Uuid::new_v4());
        assert_eq!(
            targets(&stale, bindings, Platform::Qq, Some("app")).count(),
            0
        );
        assert_eq!(targets(&stale, bindings, Platform::Matrix, None).count(), 1);
        assert_eq!(label(&stale), "看这里");
        store.close().await.unwrap();
    }

    fn assert_player_targets(id: uuid::Uuid, bindings: &BindingIndex) {
        use mik_protocol::wire::{ChatIdentity, ChatPlayer, ChatTextRun, chat_identity::Principal};
        let player = crate::domains::chat::person_mention(
            ChatIdentity {
                principal: Some(Principal::Player(ChatPlayer {
                    id: id.to_string(),
                    name: "Alice".into(),
                })),
            },
            vec![ChatTextRun {
                text: "@Alice".into(),
                style: None,
            }],
        );
        let qq: Vec<_> = targets(&player, bindings, Platform::Qq, Some("app")).collect();
        assert!(matches!(qq.as_slice(), [target] if target.parts() == ["app", "", "member"]));
        let mut matrix: Vec<_> = targets(&player, bindings, Platform::Matrix, None).collect();
        matrix.sort_by(|a, b| a.parts().cmp(&b.parts()));
        assert_eq!(
            matrix.iter().map(MentionTarget::parts).collect::<Vec<_>>(),
            [
                ["example.org", "", "@alice:example.org"],
                ["other.org", "", "@alternate:other.org"],
            ]
        );
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
