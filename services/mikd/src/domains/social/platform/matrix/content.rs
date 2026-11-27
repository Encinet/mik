use crate::domains::{
    chat::link_destination,
    social::chat::{Inline, ReferenceTarget, SpannedNode},
};
use matrix_sdk::deserialized_responses::{TimelineEvent, TimelineEventKind};
use matrix_sdk::ruma::{
    EventId, MatrixToUri, MatrixUri, OwnedEventId, OwnedUserId, UserId,
    events::{
        AnySyncMessageLikeEvent, AnySyncTimelineEvent, SyncMessageLikeEvent,
        room::message::{
            MessageFormat, MessageType, OriginalSyncRoomMessageEvent, Relation,
            RoomMessageEventContent,
        },
        sticker::OriginalSyncStickerEvent,
    },
    matrix_uri::MatrixId,
};
use mik_protocol::wire::{ChatTextRun, ChatTextStyle, chat_description::Kind};
use ruma_html::{ElementData, Html, ListBehavior, NodeRef, PropertiesNames, SanitizerConfig};
use std::{collections::BTreeSet, ops::Range};

const MAX_INLINE: usize = 32;
const MAX_STYLE_RUNS: usize = 128;

pub(super) enum Message {
    Room(OriginalSyncRoomMessageEvent),
    Sticker(OriginalSyncStickerEvent),
}

impl Message {
    pub(super) fn sender(&self) -> &OwnedUserId {
        match self {
            Self::Room(event) => &event.sender,
            Self::Sticker(event) => &event.sender,
        }
    }

    pub(super) fn id(&self) -> &OwnedEventId {
        match self {
            Self::Room(event) => &event.event_id,
            Self::Sticker(event) => &event.event_id,
        }
    }

    pub(super) fn project(&self, bot: &UserId) -> Option<ProjectedEvent> {
        let (mut body, reply_to, thread_root) = match self {
            Self::Room(event) => {
                let (reply_to, thread_root) = relations(event.content.relates_to.as_ref())?;
                let body = project(&event.content, reply_to.is_some())?;
                (body, reply_to, thread_root)
            }
            Self::Sticker(event) => {
                let (reply_to, thread_root) = relations(event.content.relates_to.as_ref())?;
                let body = Projection {
                    text: without_quote(&event.content.body, reply_to.is_some()).into(),
                    description: Some(Kind::Sticker),
                    ..Default::default()
                };
                (body, reply_to, thread_root)
            }
        };
        body.remove_bot_address(bot);
        Some(ProjectedEvent {
            sender: self.sender().clone(),
            id: self.id().clone(),
            body,
            reply_to: reply_to.map(ToOwned::to_owned),
            thread_root: thread_root.map(ToOwned::to_owned),
        })
    }
}

pub(super) fn decrypted_message(event: &TimelineEvent) -> Option<Message> {
    if !matches!(event.kind, TimelineEventKind::Decrypted(_)) {
        return None;
    }
    match event.raw().deserialize().ok()? {
        AnySyncTimelineEvent::MessageLike(AnySyncMessageLikeEvent::RoomMessage(
            SyncMessageLikeEvent::Original(message),
        )) => Some(Message::Room(message)),
        AnySyncTimelineEvent::MessageLike(AnySyncMessageLikeEvent::Sticker(
            SyncMessageLikeEvent::Original(message),
        )) => Some(Message::Sticker(message)),
        _ => None,
    }
}

pub(super) struct ProjectedEvent {
    pub(super) sender: OwnedUserId,
    pub(super) id: OwnedEventId,
    pub(super) body: Projection,
    pub(super) reply_to: Option<OwnedEventId>,
    pub(super) thread_root: Option<OwnedEventId>,
}

fn relations<Body>(
    relation: Option<&Relation<Body>>,
) -> Option<(Option<&EventId>, Option<&EventId>)> {
    match relation {
        Some(Relation::Replacement(_)) => None,
        Some(Relation::Reply(reply)) => Some((Some(&reply.in_reply_to.event_id), None)),
        Some(Relation::Thread(thread)) => Some((
            thread
                .in_reply_to
                .as_ref()
                .filter(|_| !thread.is_falling_back)
                .map(|reply| &*reply.event_id),
            Some(&thread.event_id),
        )),
        _ => Some((None, None)),
    }
}

#[derive(Default)]
pub(super) struct Projection {
    pub(super) text: String,
    pub(super) inline: Vec<SpannedNode<OwnedUserId>>,
    pub(super) description: Option<Kind>,
    pub(super) commands: bool,
    structured: usize,
    overflowed: bool,
}

impl Projection {
    fn mark(&mut self, range: Range<usize>, node: Inline<OwnedUserId>, style: ChatTextStyle) {
        if range.is_empty() {
            return;
        }
        if matches!(node, Inline::Text | Inline::Verbatim) {
            if self.inline.len() - self.structured >= MAX_STYLE_RUNS {
                self.overflowed = true;
                return;
            }
        } else {
            if self.structured >= MAX_INLINE {
                self.overflowed = true;
                return;
            }
            self.structured += 1;
        }
        self.inline.push(SpannedNode {
            range,
            node,
            style: (style != ChatTextStyle::default()).then_some(style),
        });
    }

    fn finish_anchor(
        &mut self,
        target: Option<ReferenceTarget<OwnedUserId>>,
        start: usize,
        inline_before: usize,
        style: ChatTextStyle,
    ) {
        if self.overflowed || start == self.text.len() {
            return;
        }
        let range = start..self.text.len();
        let spans = &self.inline[inline_before..];
        let target = target.filter(|_| {
            !self.text[range.clone()].trim().is_empty()
                && spans.iter().all(|span| matches!(span.node, Inline::Text))
        });
        let runs = label_runs(&self.text, spans, range.clone(), style);
        self.structured -= spans
            .iter()
            .filter(|span| !matches!(span.node, Inline::Text | Inline::Verbatim))
            .count();
        self.inline.truncate(inline_before);
        self.commands &= matches!(target, Some(ReferenceTarget::Person(_)));
        if let Some(target) = target {
            self.mark(range, Inline::Reference { target, runs }, style);
        } else {
            let mut cursor = start;
            for run in runs {
                let end = cursor + run.text.len();
                self.mark(cursor..end, Inline::Verbatim, run.style.unwrap_or_default());
                cursor = end;
            }
        }
    }

    fn remove_bot_address(&mut self, bot: &UserId) {
        let Some((
            index,
            SpannedNode {
                range,
                node:
                    Inline::Reference {
                        target: ReferenceTarget::Person(user),
                        ..
                    },
                ..
            },
        )) = self
            .inline
            .iter()
            .enumerate()
            .find(|(_, span)| !self.text[span.range.clone()].trim().is_empty())
        else {
            return;
        };
        if user.as_str() != bot.as_str() || !self.text[..range.start].trim().is_empty() {
            return;
        }
        let next = self
            .inline
            .get(index + 1)
            .map_or(self.text.len(), |span| span.range.start);
        let offset = next - self.text[range.end..next].trim_start().len();
        self.text.replace_range(..offset, "");
        self.inline.drain(..=index);
        self.structured -= 1;
        for SpannedNode { range, .. } in &mut self.inline {
            range.start -= offset;
            range.end -= offset;
        }
    }
}

fn project(event: &RoomMessageEventContent, reply: bool) -> Option<Projection> {
    let (plain, formatted, description) = match &event.msgtype {
        MessageType::Text(message) => (
            Some(message.body.as_str()),
            message.formatted.as_ref(),
            None,
        ),
        MessageType::Emote(message) => (
            Some(message.body.as_str()),
            message.formatted.as_ref(),
            Some(Kind::Emote),
        ),
        MessageType::Notice(message) => (
            Some(message.body.as_str()),
            message.formatted.as_ref(),
            Some(Kind::Notice),
        ),
        MessageType::Image(message) => (
            message.caption(),
            message.formatted_caption(),
            Some(Kind::Image),
        ),
        MessageType::Audio(message) => (
            message.caption(),
            message.formatted_caption(),
            Some(Kind::Audio),
        ),
        MessageType::Video(message) => (
            message.caption(),
            message.formatted_caption(),
            Some(Kind::Video),
        ),
        MessageType::File(message) => (
            message.caption(),
            message.formatted_caption(),
            Some(Kind::File),
        ),
        MessageType::Location(_) => (None, None, Some(Kind::Location)),
        _ => return None,
    };
    let text = without_quote(plain.unwrap_or_default(), reply);
    let mut projected = Projection {
        commands: description.is_none(),
        description,
        ..Default::default()
    };
    let targets = event.mentions.as_ref().map(|mentions| &mentions.user_ids);
    if let Some(html) = formatted.filter(|body| body.format == MessageFormat::Html) {
        if html.body.len() <= 64 * 1024 {
            let html = Html::parse(&html.body);
            let sanitizer = SanitizerConfig::compat()
                .remove_reply_fallback()
                .allow_attributes(
                    [PropertiesNames {
                        parent: "img",
                        properties: &["data-mx-emoticon"],
                    }],
                    ListBehavior::Add,
                );
            html.sanitize_with(&sanitizer);
            for node in html.children() {
                append(
                    &mut projected,
                    &node,
                    targets,
                    ChatTextStyle::default(),
                    false,
                    0,
                );
            }
            projected.commands &= projected
                .text
                .split_whitespace()
                .eq(text.split_whitespace());
            return (!projected.overflowed).then_some(projected);
        }
        return None;
    }
    text.clone_into(&mut projected.text);
    if let Some(targets) = targets {
        let mut offset = 0;
        for token in text.split_inclusive(char::is_whitespace) {
            let label = token.trim_end();
            if let Ok(user_id) = <&UserId>::try_from(label)
                && let Some(user) = targets.get(user_id)
            {
                let node = if projected.structured < MAX_INLINE {
                    Inline::person(user.clone())
                } else {
                    Inline::Verbatim
                };
                projected.mark(offset..offset + label.len(), node, ChatTextStyle::default());
                if projected.overflowed {
                    return None;
                }
            }
            offset += token.len();
        }
    }
    Some(projected)
}

fn without_quote(mut text: &str, reply: bool) -> &str {
    if reply && text.starts_with("> ") {
        while let Some(line) = text.strip_prefix("> ") {
            text = line.split_once('\n').map_or("", |(_, rest)| rest);
        }
        text = text.trim_start_matches('\n');
    }
    text
}

fn append(
    projected: &mut Projection,
    node: &NodeRef,
    targets: Option<&BTreeSet<OwnedUserId>>,
    style: ChatTextStyle,
    literal: bool,
    depth: usize,
) {
    projected.overflowed |= depth > 64;
    if projected.overflowed {
        return;
    }
    if let Some(text) = node.as_text() {
        let start = projected.text.len();
        projected.text.push_str(&text.borrow());
        if literal {
            projected.mark(start..projected.text.len(), Inline::Verbatim, style);
        } else if style != ChatTextStyle::default() {
            projected.mark(start..projected.text.len(), Inline::Text, style);
        }
        return;
    }
    let Some(element) = node.as_element() else {
        return;
    };
    let tag = element.name.local.as_ref();
    if tag == "span"
        && element
            .attrs
            .borrow()
            .iter()
            .any(|attr| attr.name.local.as_ref() == "data-mx-spoiler")
    {
        let start = projected.text.len();
        projected.text.push_str("[…]");
        projected.mark(start..projected.text.len(), Inline::Verbatim, style);
        projected.commands = false;
        return;
    }
    let style = inline_style(style, tag, element);
    if tag == "img" {
        append_image(projected, element, style);
        return;
    }
    let quoted = literal || matches!(tag, "blockquote" | "code" | "pre");
    let block = matches!(
        tag,
        "div" | "p" | "li" | "blockquote" | "pre" | "ul" | "ol" | "tr"
    );
    if quoted {
        projected.commands = false;
    }
    if tag == "br" || block && !projected.text.is_empty() && !projected.text.ends_with('\n') {
        projected.text.push('\n');
    }
    let is_anchor = !quoted && tag == "a";
    let anchor = if is_anchor {
        element
            .attrs
            .borrow()
            .iter()
            .find(|attr| attr.name.local.as_ref() == "href")
            .and_then(|href| anchor(&href.value, targets))
    } else {
        None
    };
    let start = projected.text.len();
    let inline_before = projected.inline.len();
    for child in node.children() {
        append(
            projected,
            &child,
            if quoted || is_anchor { None } else { targets },
            style,
            quoted,
            depth + 1,
        );
    }
    if is_anchor {
        projected.finish_anchor(anchor, start, inline_before, style);
    }
    if block && !projected.text.ends_with('\n') {
        projected.text.push('\n');
    }
}

fn label_runs(
    text: &str,
    spans: &[SpannedNode<OwnedUserId>],
    range: Range<usize>,
    style: ChatTextStyle,
) -> Vec<ChatTextRun> {
    let mut cursor = range.start;
    let mut runs = Vec::new();
    for span in spans {
        if cursor < span.range.start {
            runs.push(ChatTextRun {
                text: text[cursor..span.range.start].into(),
                style: Some(style),
            });
        }
        runs.push(ChatTextRun {
            text: text[span.range.clone()].into(),
            style: span.style,
        });
        cursor = span.range.end;
    }
    if cursor < range.end {
        runs.push(ChatTextRun {
            text: text[cursor..range.end].into(),
            style: Some(style),
        });
    }
    runs
}

fn append_image(projected: &mut Projection, element: &ElementData, style: ChatTextStyle) {
    let attributes = element.attrs.borrow();
    let alt = attributes
        .iter()
        .find(|attr| attr.name.local.as_ref() == "alt")
        .map(|attr| attr.value.as_ref())
        .filter(|alt: &&str| !alt.trim().is_empty());
    if let Some(alt) = alt {
        let start = projected.text.len();
        projected.text.push_str(alt);
        projected.mark(start..projected.text.len(), Inline::Verbatim, style);
    } else {
        let kind = if attributes
            .iter()
            .any(|attr| attr.name.local.as_ref() == "data-mx-emoticon")
        {
            Kind::Face
        } else {
            Kind::Image
        };
        let start = projected.text.len();
        projected
            .text
            .push_str(&crate::domains::chat::description_kind_label(kind));
        projected.mark(
            start..projected.text.len(),
            Inline::Description(kind),
            style,
        );
    }
    projected.commands = false;
}

fn inline_style(mut style: ChatTextStyle, tag: &str, element: &ElementData) -> ChatTextStyle {
    match tag {
        "b" | "strong" => style.bold = true,
        "i" | "em" => style.italic = true,
        "u" => style.underlined = true,
        "s" | "del" | "strike" => style.strikethrough = true,
        "span" | "font" => {
            if let Some(color) = element
                .attrs
                .borrow()
                .iter()
                .find(|attr| matches!(attr.name.local.as_ref(), "data-mx-color" | "color"))
                .and_then(|attr| hex_color(&attr.value))
            {
                style.color = Some(color);
            }
        }
        _ => {}
    }
    style
}

fn hex_color(value: &str) -> Option<u32> {
    let value = value.strip_prefix('#')?;
    let color = u32::from_str_radix(value, 16).ok()?;
    match value.len() {
        3 => Some((((color >> 8) & 0xf) << 16 | ((color >> 4) & 0xf) << 8 | (color & 0xf)) * 17),
        6 => Some(color),
        _ => None,
    }
}

fn anchor(
    href: &str,
    targets: Option<&BTreeSet<OwnedUserId>>,
) -> Option<ReferenceTarget<OwnedUserId>> {
    if let Some(user) =
        user_id(href).filter(|user| targets.is_some_and(|targets| targets.contains(user)))
    {
        return Some(ReferenceTarget::Person(user));
    }
    link_destination(href)
        .filter(|url| url.as_str().len() <= 2048)
        .map(|url| ReferenceTarget::Link(url.into()))
}

fn user_id(uri: &str) -> Option<OwnedUserId> {
    if let Ok(uri) = MatrixToUri::parse(uri)
        && let MatrixId::User(user) = uri.id()
    {
        return Some(user.clone());
    }
    let uri = MatrixUri::parse(uri).ok()?;
    match uri.id() {
        MatrixId::User(user) => Some(user.clone()),
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use matrix_sdk::deserialized_responses::{DecryptedRoomEvent, VerificationState};
    use matrix_sdk::ruma::{UserId, events::Mentions};
    use serde_json::json;

    #[test]
    fn native_mentions_keep_their_positions_and_ignore_quoted_users() {
        let first = UserId::parse("@first:example.org").unwrap();
        let second = UserId::parse("@second:example.org").unwrap();
        let mut event = RoomMessageEventContent::text_html(
            "> quoted\n\n@Same Alex Alex",
            "<mx-reply><a href=\"https://matrix.to/#/@second:example.org\">Alex</a></mx-reply>\
             @Same <a href=\"https://matrix.to/#/@first:example.org\">Alex</a> \
             <a href=\"matrix:u/second:example.org\">Alex</a>",
        );
        event.mentions = Some(Mentions::with_user_ids([first.clone(), second.clone()]));
        let projected = project(&event, true).unwrap();
        assert_eq!(projected.text, "@Same Alex Alex");
        assert!(matches!(projected.inline.as_slice(),
            [SpannedNode { range: first_span, node: Inline::Reference { target: ReferenceTarget::Person(first_user), .. }, .. },
             SpannedNode { range: second_span, node: Inline::Reference { target: ReferenceTarget::Person(second_user), .. }, .. }]
            if first_span == &(6..10) && first_user == &first
                && second_span == &(11..15) && second_user == &second));
        assert!(projected.commands);

        let different_body = RoomMessageEventContent::text_html("/bind 123456", "<p>Hello</p>");
        assert!(!project(&different_body, false).unwrap().commands);

        let bot = UserId::parse("@bot:example.org").unwrap();
        let mut addressed =
            RoomMessageEventContent::text_plain("@bot:example.org /profile @first:example.org");
        addressed.mentions = Some(Mentions::with_user_ids([
            bot.clone(),
            UserId::parse("@first:example.org").unwrap(),
        ]));
        let mut addressed = project(&addressed, false).unwrap();
        addressed.remove_bot_address(&bot);
        assert_eq!(addressed.text, "/profile @first:example.org");
        assert_eq!(addressed.inline[0].range, 9..27);
        assert!(addressed.commands);
    }

    #[test]
    fn labeled_links_and_inline_emoji_keep_their_meaning() {
        let event = RoomMessageEventContent::text_html(
            "插件 备用 [face] 好",
            r#"<a href="https://github.com/encinet/mik">插<strong>件</strong></a> <a href="https://user@example.org/">备用</a> <img src="mxc://example.org/emoji" data-mx-emoticon> <img src="mxc://example.org/icon" alt="好">"#,
        );
        let projected = project(&event, false).unwrap();
        assert_eq!(projected.text, "插件 备用 [face] 好");
        assert!(!projected.commands);
        let [
            SpannedNode {
                range: link,
                node:
                    Inline::Reference {
                        target: ReferenceTarget::Link(uri),
                        runs,
                    },
                ..
            },
            SpannedNode {
                node: Inline::Verbatim,
                ..
            },
            SpannedNode {
                range: face,
                node: Inline::Description(Kind::Face),
                ..
            },
            SpannedNode {
                node: Inline::Verbatim,
                ..
            },
        ] = projected.inline.as_slice()
        else {
            panic!("expected the public link, inline emoji and literal alt text");
        };
        assert_eq!(&projected.text[link.clone()], "插件");
        assert_eq!(uri, "https://github.com/encinet/mik");
        assert_eq!(&projected.text[face.clone()], "[face]");
        assert_eq!(runs.len(), 2);
        assert!(runs[1].style.unwrap().bold);
    }

    #[test]
    fn formatting_survives_projection_without_revealing_spoilers() {
        let event = RoomMessageEventContent::text_html(
            "彩色 链接 […]",
            r##"<strong><span data-mx-color="#abc">彩色</span></strong> <a href="https://example.org/"><em>链接</em></a> <span data-mx-spoiler>secret</span>"##,
        );
        let projected = project(&event, false).unwrap();
        assert_eq!(projected.text, "彩色 链接 […]");
        assert!(!projected.commands);
        let [styled, link, spoiler] = projected.inline.as_slice() else {
            panic!("expected style, link and spoiler spans")
        };
        assert_eq!(&projected.text[styled.range.clone()], "彩色");
        assert_eq!(
            styled.style,
            Some(ChatTextStyle {
                bold: true,
                color: Some(0xaa_bb_cc),
                ..Default::default()
            })
        );
        assert!(
            matches!(&link.node, Inline::Reference { runs, .. } if runs[0].style.unwrap().italic)
        );
        assert!(matches!(spoiler.node, Inline::Verbatim));
    }

    #[test]
    fn a_user_permalink_needs_native_mention_evidence() {
        let mut event = RoomMessageEventContent::text_html(
            "@Alice",
            r#"<a href="https://matrix.to/#/@alice:example.org">@<b>Alice</b></a>"#,
        );
        let body = project(&event, false).unwrap();
        assert!(matches!(body.inline.as_slice(),
                [SpannedNode { node: Inline::Reference { target: ReferenceTarget::Link(_), runs }, .. }]
                if runs.len() == 2));
        event.mentions = Some(Mentions::with_user_ids([UserId::parse(
            "@alice:example.org",
        )
        .unwrap()]));
        assert!(matches!(project(&event, false).unwrap().inline.as_slice(),
            [SpannedNode { node: Inline::Reference { target: ReferenceTarget::Person(user), runs }, .. }]
            if user.as_str() == "@alice:example.org" && runs.len() == 2 && runs[1].style.unwrap().bold));
    }

    #[test]
    fn unsupported_or_nontext_link_labels_cannot_be_reparsed_as_mentions() {
        let event = RoomMessageEventContent::text_html(
            "@Alice @Bob code @Carol",
            r#"<a href="https://user@example.org/">@<b>Alice</b></a> <a href="https://example.org/">@Bob <code>code</code></a> @Carol"#,
        );
        let projected = project(&event, false).unwrap();
        assert_eq!(projected.text, "@Alice @Bob code @Carol");
        assert!(!projected.commands);
        assert!(
            projected
                .inline
                .iter()
                .all(|span| matches!(span.node, Inline::Verbatim))
        );
        assert_eq!(projected.inline[0].range.start, 0);
        assert!(projected.inline[1].style.unwrap().bold);
        assert_eq!(
            projected.inline.last().unwrap().range.end,
            "@Alice @Bob code".len()
        );
    }

    #[test]
    fn plain_matrix_targets_are_matched_by_id_not_by_their_order_in_metadata() {
        let mentioned = UserId::parse("@zed:example.org").unwrap();
        let others =
            (0..MAX_INLINE).map(|i| UserId::parse(format!("@alice{i}:example.org")).unwrap());
        let mut event = RoomMessageEventContent::text_plain("@zed:example.org hi");
        event.mentions = Some(Mentions::with_user_ids(others.chain([mentioned.clone()])));
        assert!(matches!(
            project(&event, false).unwrap().inline.as_slice(),
            [SpannedNode {
                node: Inline::Reference { target: ReferenceTarget::Person(user), .. },
                ..
            }] if user == &mentioned
        ));
    }

    #[test]
    fn plain_matrix_mentions_over_the_limit_do_not_become_links() {
        let user = UserId::parse("@alice:example.org").unwrap();
        let mut event = RoomMessageEventContent::text_plain(format!(
            "{}@Bob",
            "@alice:example.org ".repeat(MAX_INLINE + 1)
        ));
        event.mentions = Some(Mentions::with_user_ids([user]));
        let projected = project(&event, false).unwrap();
        let last = projected.inline.last().unwrap();
        assert_eq!(projected.structured, MAX_INLINE);
        assert!(matches!(last.node, Inline::Verbatim));
        assert_eq!(&projected.text[last.range.clone()], "@alice:example.org");
        assert_eq!(&projected.text[last.range.end..], " @Bob");
    }

    #[test]
    fn an_unprojectable_html_body_does_not_reuse_hidden_plain_text() {
        let html = format!(
            "{}<span data-mx-spoiler>secret</span>",
            "<b>text</b>".repeat(MAX_STYLE_RUNS + 1)
        );
        let event = RoomMessageEventContent::text_html("secret", html);
        assert!(project(&event, false).is_none());
    }

    #[test]
    fn decrypted_sticker_is_a_description_not_a_command() {
        let decrypted: DecryptedRoomEvent = serde_json::from_value(json!({
            "event": {
                "type": "m.sticker", "sender": "@al:example.org", "event_id": "$sticker",
                "origin_server_ts": 1, "room_id": "!room:example.org",
                "content": {
                    "body": "> quoted\n\n/profile", "info": {}, "url": "mxc://example.org/sticker",
                    "m.relates_to": { "m.in_reply_to": { "event_id": "$original" } }
                }
            },
            "encryption_info": {
                "sender": "@al:example.org", "verification_state": VerificationState::Verified,
                "algorithm_info": { "MegolmV1AesSha2": {
                    "curve25519_key": "fixture", "sender_claimed_keys": {}
                } }
            }
        }))
        .unwrap();
        assert!(
            decrypted_message(&TimelineEvent::from_plaintext(
                decrypted.event.clone().cast()
            ))
            .is_none()
        );
        let event = TimelineEvent::from_decrypted(decrypted, None);
        let message = decrypted_message(&event).unwrap();
        let bot = UserId::parse("@bot:example.org").unwrap();
        let projected = message.project(&bot).unwrap();
        assert_eq!(projected.id.as_str(), "$sticker");
        assert_eq!(
            projected.reply_to.as_deref().map(EventId::as_str),
            Some("$original")
        );
        assert_eq!(projected.body.text, "/profile");
        assert_eq!(projected.body.description, Some(Kind::Sticker));
        assert!(!projected.body.commands);
    }
}
