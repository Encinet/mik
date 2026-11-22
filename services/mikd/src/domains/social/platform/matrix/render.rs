use super::super::super::{
    chat::{self, MentionTarget, Outbound, OutputText, Platform},
    locale::Locale,
    runtime::{ReplyBody, Runtime},
};
use crate::domains::chat::link_destination;
use htmlescape::encode_minimal;
use matrix_sdk::Room;
use matrix_sdk::ruma::{
    OwnedUserId, UserId,
    events::{
        Mentions,
        room::{
            member::{MembershipState, RoomMemberEventContent},
            message::RoomMessageEventContent,
        },
    },
};
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
    room: &Room,
    runtime: &Runtime,
    locale: &Locale,
    post: &Outbound<'_>,
) -> RoomMessageEventContent {
    let bindings = runtime.identity.bindings();
    let targets = post
        .parts
        .iter()
        .filter_map(|part| part.content.as_ref())
        .flat_map(|content| chat::targets(content, bindings, Platform::Matrix));
    let joined = joined_targets(room, targets).await;
    let mut result = Text::default();
    post.render(Platform::Matrix, locale, |visible, content, style| {
        let mention = content.filter(|_| !joined.is_empty()).and_then(|content| {
            let mut targets = chat::targets(content, bindings, Platform::Matrix)
                .filter_map(|target| joined.get(matrix_id(&target)?));
            let target = targets.next()?;
            targets.next().is_none().then(|| target.clone())
        });
        result.write(visible, content, style, mention);
    });
    result.finish()
}

async fn joined_targets<'message>(
    room: &Room,
    targets: impl Iterator<Item = MentionTarget<'message>>,
) -> BTreeSet<OwnedUserId> {
    let mut users = BTreeSet::new();
    for target in targets {
        if let Some(user) = matrix_id(&target)
            && !users.contains(user)
        {
            if users.len() == 32 {
                return BTreeSet::new();
            }
            users.insert(user.to_owned());
        }
    }
    if users.is_empty() {
        return users;
    }
    room.get_state_events_for_keys_static::<RoomMemberEventContent, _, _>(&users)
        .await
        .unwrap_or_default()
        .into_iter()
        .filter_map(|event| event.deserialize().ok())
        .filter(|event| *event.membership() == MembershipState::Join)
        .filter_map(|event| users.take(event.user_id()))
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
        mention: Option<OwnedUserId>,
    ) {
        let link = match content {
            Some(Content::Link(link)) => link_destination(&link.url).map(String::from),
            _ => None,
        };
        let mention_uri = mention.map(|user| {
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
                style.italic
                    || matches!(
                        content,
                        Some(Content::Description(_) | Content::ItemLabel(_))
                    ),
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
        write_colored(&mut self.html, visible, color, style.gradient_end_color);
        if anchor.is_some() {
            self.html.push_str("</a>");
        }
        for (tag, _) in tags.iter().rev().filter(|(_, used)| *used) {
            let _ = write!(self.html, "</{tag}>");
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
    use matrix_sdk::ruma::events::room::message::MessageType;
    use mik_protocol::wire::ChatLink;

    #[test]
    fn matrix_renders_escaped_links_styles_and_explicit_mentions() {
        let mut result = Text::default();
        let link = Content::Link(ChatLink {
            label: "<MIK>".into(),
            url: "https://github.com/encinet/mik?a=1&b=2".into(),
            ..Default::default()
        });
        let plain_link = "<MIK> (https://github.com/encinet/mik?a=1&b=2)";
        let alice = UserId::parse("@alice:example.org").unwrap();
        result.write("<raw> ", None, None, None);
        result.write(
            "<MIK>",
            Some(&link),
            Some(ChatTextStyle {
                bold: true,
                color: Some(0x00_ff00),
                ..Default::default()
            }),
            None,
        );
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
                "&lt;raw&gt; <strong><a href=\"https://github.com/encinet/mik?a=1&amp;b=2\"><span data-mx-color=\"#00ff00\">&lt;MIK&gt;</span></a></strong><a href=\"{}\"><br>@Alice</a>",
                alice.matrix_to_uri(),
            )
        );
    }

    #[test]
    fn gradient_uses_native_color_spans_with_a_readable_mail_fallback() {
        let mut result = Text::default();
        let email = Content::Link(ChatLink {
            label: "联系".into(),
            url: "mailto:help@example.org".into(),
            ..Default::default()
        });
        let plain = "联系 (mailto:help@example.org)";
        result.write(
            "联系",
            Some(&email),
            Some(ChatTextStyle {
                color: Some(0xff_00_00),
                gradient_end_color: Some(0x00_00_ff),
                ..Default::default()
            }),
            None,
        );
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
