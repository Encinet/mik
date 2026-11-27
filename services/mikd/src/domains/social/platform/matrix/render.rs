use super::super::super::{
    chat::{self, MentionTarget, Outbound, OutputText, Platform},
    locale::Locale,
    runtime::ReplyBody,
};
use crate::domains::{
    chat::{link_destination, link_uri},
    community::BindingIndex,
};
use htmlescape::encode_minimal;
use matrix_sdk::ruma::{
    OwnedUserId, RoomId, UserId,
    events::{
        Mentions,
        room::{
            member::{MembershipState, RoomMemberEventContent},
            message::RoomMessageEventContent,
        },
    },
};
use matrix_sdk::{DynStateStore, StateStoreExt};
use mik_protocol::wire::{ChatTextStyle, chat_part::Content};
use std::{collections::BTreeSet, fmt::Write as _};

pub(super) const RICH_TEXT_BYTES: usize = 24_000;

pub(in crate::domains::social) struct MatrixReply(pub(super) Box<RoomMessageEventContent>);

pub(super) fn command_reply(body: ReplyBody) -> Option<RoomMessageEventContent> {
    Some(match body {
        ReplyBody::Plain(text) => {
            RoomMessageEventContent::text_plain(text).add_mentions(Mentions::new())
        }
        ReplyBody::Matrix(MatrixReply(content)) => *content,
        ReplyBody::QqMarkdown { .. } => return None,
    })
}

pub(super) async fn message(
    room: &RoomId,
    state: &DynStateStore,
    bindings: &BindingIndex,
    locale: &Locale,
    post: &Outbound<'_>,
) -> RoomMessageEventContent {
    let mut parts = Vec::new();
    post.render(Platform::Matrix, locale, |visible, content, style| {
        let mentions: BTreeSet<_> = content
            .into_iter()
            .flat_map(|content| chat::targets(content, bindings, Platform::Matrix, None))
            .filter_map(|target| matrix_id(&target).map(ToOwned::to_owned))
            .collect();
        parts.push((visible.to_owned(), content, style, mentions));
    });

    let absent = {
        let users = parts.iter().flat_map(|(_, _, _, ids)| ids.iter()).collect();
        known_non_members(room, state, &users).await
    };
    let mut result = Text::default();
    for (visible, content, style, mentions) in parts {
        result.write(
            &visible,
            content,
            style,
            mentions.into_iter().filter(|user| !absent.contains(user)),
        );
    }
    result.finish()
}

async fn known_non_members(
    room: &RoomId,
    state: &DynStateStore,
    users: &BTreeSet<&OwnedUserId>,
) -> BTreeSet<OwnedUserId> {
    if users.is_empty() {
        return BTreeSet::new();
    }
    state
        .get_state_events_for_keys_static::<RoomMemberEventContent, _, _>(
            room,
            users.iter().copied(),
        )
        .await
        .unwrap_or_default()
        .into_iter()
        .filter_map(|event| event.deserialize().ok())
        .filter(|event| {
            matches!(
                event.membership(),
                MembershipState::Leave
                    | MembershipState::Ban
                    | MembershipState::Invite
                    | MembershipState::Knock
            )
        })
        .map(|event| event.user_id().to_owned())
        .collect()
}

fn matrix_id<'target>(target: &'target MentionTarget<'_>) -> Option<&'target UserId> {
    let [issuer, scope, subject] = target.parts();
    let user = <&UserId>::try_from(subject).ok()?;
    (scope.is_empty() && user.server_name().as_str() == issuer).then_some(user)
}

#[derive(Default)]
struct Text {
    plain: OutputText,
    html: String,
    mentions: BTreeSet<OwnedUserId>,
    formatted: bool,
}

impl Text {
    #[expect(
        clippy::let_underscore_must_use,
        reason = "Formatting string slices into a String cannot fail"
    )]
    fn write(
        &mut self,
        visible: &str,
        content: Option<&Content>,
        style: Option<ChatTextStyle>,
        mentions: impl IntoIterator<Item = OwnedUserId>,
    ) {
        let link = content
            .and_then(link_uri)
            .and_then(link_destination)
            .map(String::from);
        let mut mentions = mentions.into_iter().peekable();
        let mention_uri = mentions.next().map(|user| {
            let uri = user.matrix_to_uri().to_string();
            self.mentions.insert(user);
            uri
        });
        let anchor = mention_uri.as_deref().or(link.as_deref());
        let style = style.unwrap_or_default();
        let color = style.color.filter(|color| *color <= 0xff_ffff);
        let tags = [
            ("strong", style.bold),
            (
                "em",
                style.italic || matches!(content, Some(Content::Description(_))),
            ),
            ("u", style.underlined),
            ("del", style.strikethrough),
        ];
        self.formatted |= anchor.is_some() || color.is_some() || tags.iter().any(|(_, used)| *used);
        for (tag, _) in tags.iter().filter(|(_, used)| *used) {
            let _ = write!(self.html, "<{tag}>");
        }
        if let Some(url) = anchor {
            let _ = write!(self.html, "<a href=\"{}\">", encode_minimal(url));
        }
        let runs = match content {
            Some(Content::Reference(reference)) => Some(&reference.runs),
            _ => None,
        };
        if let Some(runs) = runs {
            for run in runs {
                let run_style = run.style.unwrap_or_default();
                let tags = [
                    ("strong", run_style.bold),
                    ("em", run_style.italic),
                    ("u", run_style.underlined),
                    ("del", run_style.strikethrough),
                ];
                self.formatted |= run_style.color.is_some() || tags.iter().any(|(_, on)| *on);
                for (tag, _) in tags.iter().filter(|(_, used)| *used) {
                    let _ = write!(self.html, "<{tag}>");
                }
                write_colored(
                    &mut self.html,
                    &run.text,
                    run_style.color,
                    run_style.gradient_end_color,
                );
                for (tag, _) in tags.iter().rev().filter(|(_, used)| *used) {
                    let _ = write!(self.html, "</{tag}>");
                }
            }
        } else {
            write_colored(&mut self.html, visible, color, style.gradient_end_color);
        }
        if anchor.is_some() {
            self.html.push_str("</a>");
        }
        for (tag, _) in tags.iter().rev().filter(|(_, used)| *used) {
            let _ = write!(self.html, "</{tag}>");
        }
        if mentions.peek().is_some() {
            for (index, user) in mentions.enumerate() {
                self.html.push_str(if index == 0 { " (" } else { ", " });
                let _ = write!(
                    self.html,
                    "<a href=\"{}\">{}</a>",
                    encode_minimal(&user.matrix_to_uri().to_string()),
                    encode_minimal(user.as_str())
                );
                self.mentions.insert(user);
            }
            self.html.push(')');
        }
        self.plain.push(visible, link);
    }

    fn finish(self) -> RoomMessageEventContent {
        let plain = self.plain.within(Platform::Matrix.text_limit());
        let message = if self.formatted && plain.len() + self.html.len() <= RICH_TEXT_BYTES {
            RoomMessageEventContent::text_html(plain, self.html)
        } else {
            RoomMessageEventContent::text_plain(plain)
        };
        message.add_mentions(Mentions::with_user_ids(self.mentions))
    }
}

pub(super) fn inline_html(text: &str) -> String {
    encode_minimal(text).replace('\n', "<br>")
}

fn write_colored(html: &mut String, text: &str, color: Option<u32>, gradient: Option<u32>) {
    let Some(color) = color else {
        html.push_str(&inline_html(text));
        return;
    };
    let Some(gradient) = gradient.filter(|end| *end <= 0xff_ffff && *end != color) else {
        color_span(html, text, color);
        return;
    };
    let count = text.chars().count();
    let steps = count.min(8);
    if steps < 2 {
        color_span(html, text, color);
        return;
    }
    let mut boundaries = text
        .char_indices()
        .skip(1)
        .map(|(index, _)| index)
        .chain(std::iter::once(text.len()));
    let mut start = 0;
    let denominator = u32::try_from(steps - 1).unwrap_or(1);
    for step in 0..steps {
        let end = boundaries
            .nth((count + step) / steps - 1)
            .unwrap_or(text.len());
        let position = u32::try_from(step).unwrap_or_default();
        let blend = |shift: u32| {
            let start_channel = (color >> shift) & 0xff;
            let end_channel = (gradient >> shift) & 0xff;
            ((start_channel * (denominator - position) + end_channel * position) / denominator)
                << shift
        };
        color_span(html, &text[start..end], blend(16) | blend(8) | blend(0));
        start = end;
    }
}

#[expect(
    clippy::let_underscore_must_use,
    reason = "Formatting a color and escaped text into a String cannot fail"
)]
fn color_span(html: &mut String, text: &str, color: u32) {
    let _ = write!(
        html,
        "<span data-mx-color=\"#{color:06x}\">{}</span>",
        inline_html(text)
    );
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::domains::chat::link_reference;
    use matrix_sdk::ruma::events::room::message::MessageType;
    use matrix_sdk::{MemoryStore, StateChanges, StateStore, ruma::serde::Raw};

    #[tokio::test]
    async fn a_player_mention_expands_bindings_but_a_native_mention_does_not() {
        use crate::domains::{
            chat::{external_principal, person_mention},
            community::CommunityStore,
            social::chat::fixtures,
        };
        use mik_protocol::wire::{
            ChatExternalIdentity, ChatIdentity, ChatPart, ChatPlayer, ChatTextRun,
            chat_identity::Principal,
        };
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
        let player = ChatPlayer {
            id: id.to_string(),
            name: "Alice".into(),
        };
        let [joined, unknown, left] = [
            "@alice:example.org",
            "@alternate:other.org",
            "@left:third.org",
        ]
        .map(|id| UserId::parse(id).unwrap());
        let account = |user: &UserId| ChatExternalIdentity {
            platform: "matrix".into(),
            issuer: user.server_name().to_string(),
            subject: user.to_string(),
            ..Default::default()
        };
        for user in [&joined, &unknown, &left] {
            fixtures::bind(&store, id, &account(user)).await;
        }

        let room = matrix_sdk::ruma::room_id!("!public:example.org");
        let state = cached_membership(room, [(&joined, "join"), (&left, "leave")]).await;

        for (identity, expected) in [
            (
                ChatIdentity {
                    principal: Some(Principal::Player(player.clone())),
                },
                BTreeSet::from([joined.clone(), unknown.clone()]),
            ),
            (
                external_principal(account(&joined), Some(player)),
                BTreeSet::from([joined.clone()]),
            ),
        ] {
            let parts = [ChatPart {
                content: Some(person_mention(
                    identity,
                    vec![ChatTextRun {
                        text: "@Alice".into(),
                        style: None,
                    }],
                )),
            }];
            let content = message(
                room,
                &state,
                store.bindings(),
                Locale::get("en-US"),
                &fixtures::post(&parts),
            )
            .await;
            assert_eq!(content.body(), "[Minecraft] Sender » @Alice");
            assert_eq!(content.mentions.unwrap().user_ids, expected);
            let MessageType::Text(text) = content.msgtype else {
                panic!("not text")
            };
            let html = text.formatted.unwrap().body;
            for user in [&joined, &unknown, &left] {
                assert_eq!(
                    html.contains(&user.matrix_to_uri().to_string()),
                    expected.contains(user)
                );
            }
        }
        store.close().await.unwrap();
    }

    async fn cached_membership<'a>(
        room: &RoomId,
        members: impl IntoIterator<Item = (&'a OwnedUserId, &'a str)>,
    ) -> MemoryStore {
        let mut changes = StateChanges::default();
        for (index, (user, membership)) in members.into_iter().enumerate() {
            let raw = Raw::from_json_string(
                serde_json::json!({
                    "type": "m.room.member",
                    "event_id": format!("$member{index}"),
                    "state_key": user,
                    "sender": user,
                    "origin_server_ts": 1,
                    "content": { "membership": membership }
                })
                .to_string(),
            )
            .unwrap();
            changes.add_state_event(room, raw.deserialize().unwrap(), raw);
        }
        let state = MemoryStore::new();
        state.save_changes(&changes).await.unwrap();
        state
    }

    #[test]
    fn matrix_renders_escaped_links_styles_and_explicit_mentions() {
        let mut result = Text::default();
        let link = Content::Reference(link_reference(
            "https://github.com/encinet/mik?a=1&b=2",
            vec![mik_protocol::wire::ChatTextRun {
                text: "<MIK>".into(),
                style: Some(ChatTextStyle {
                    bold: true,
                    color: Some(0x00_ff00),
                    ..Default::default()
                }),
            }],
            false,
        ));
        let plain_link = "<MIK> (https://github.com/encinet/mik?a=1&b=2)";
        let alice = UserId::parse("@alice:example.org").unwrap();
        result.write("<raw> ", None, None, None);
        result.write("<MIK>", Some(&link), None, None);
        result.write("\n@Alice", None, None, Some(alice.clone()));
        let content = result.finish();
        assert_eq!(content.body(), format!("<raw> {plain_link}\n@Alice"));
        assert_eq!(
            content.mentions.unwrap().user_ids,
            BTreeSet::from([alice.clone()])
        );
        let MessageType::Text(text) = content.msgtype else {
            panic!("not text")
        };
        assert_eq!(
            text.formatted.unwrap().body,
            format!(
                "&lt;raw&gt; <a href=\"https://github.com/encinet/mik?a=1&amp;b=2\"><strong><span data-mx-color=\"#00ff00\">&lt;MIK&gt;</span></strong></a><a href=\"{}\"><br>@Alice</a>",
                alice.matrix_to_uri(),
            )
        );
    }

    #[test]
    fn gradient_uses_native_color_spans_with_a_readable_mail_fallback() {
        let mut result = Text::default();
        let email = Content::Reference(link_reference(
            "mailto:help@example.org",
            vec![mik_protocol::wire::ChatTextRun {
                text: "联系".into(),
                style: Some(ChatTextStyle {
                    color: Some(0xff_00_00),
                    gradient_end_color: Some(0x00_00_ff),
                    ..Default::default()
                }),
            }],
            false,
        ));
        let plain = "联系 (mailto:help@example.org)";
        result.write("联系", Some(&email), None, None);
        let content = result.finish();
        assert_eq!(content.body(), plain);
        let MessageType::Text(text) = content.msgtype else {
            panic!("not text")
        };
        assert_eq!(
            text.formatted.unwrap().body,
            "<a href=\"mailto:help@example.org\"><span data-mx-color=\"#ff0000\">联</span><span data-mx-color=\"#0000ff\">系</span></a>"
        );
    }
}
