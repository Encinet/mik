use super::super::super::{
    chat::{self, MentionTarget, Outbound, OutputText, Platform},
    locale::Locale,
    membership::Directory,
    runtime::Runtime,
};
use crate::domains::chat::{link_destination, link_uri};
use mik_protocol::wire::{ChatTextStyle, chat_part::Content};
use qqbot_sdk_rs::{MessageSegment, segment};

pub(super) const MARKDOWN_BYTES: usize = 8000;

pub(super) struct Rendered<Message> {
    pub(super) message: Message,
    pub(super) review: String,
}

pub(super) fn message(
    issuer: &str,
    group: &str,
    runtime: &Runtime,
    locale: &Locale,
    post: &Outbound<'_>,
) -> Rendered<MessageSegment> {
    let bindings = runtime.identity.bindings();
    let mut rendered = Text::default();
    post.render(Platform::Qq, locale, |visible, content, style| {
        let targets = content
            .into_iter()
            .flat_map(|content| chat::targets(content, bindings, Platform::Qq, Some(issuer)));
        let mentions =
            targets.filter_map(|target| qq_id(&target, runtime.membership(), issuer, group));
        rendered.write(visible, content, style, mentions);
    });
    rendered.finish()
}

fn qq_id(
    target: &MentionTarget<'_>,
    membership: &Directory,
    issuer: &str,
    group: &str,
) -> Option<String> {
    let [_, scope, subject] = target.parts();
    (scope.is_empty()
        && !subject.is_empty()
        && subject
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'_' | b'-'))
        && !membership.qq_known_absent(issuer, group, subject))
    .then(|| subject.to_owned())
}

#[derive(Default)]
struct Text {
    plain: OutputText,
    markdown: String,
    rich: bool,
}

impl Text {
    fn write(
        &mut self,
        visible: &str,
        content: Option<&Content>,
        style: Option<ChatTextStyle>,
        mentions: impl IntoIterator<Item = String>,
    ) {
        let link = content.and_then(link_uri).and_then(link_destination);
        self.plain.push(
            &chat::escape_qq(visible),
            link.as_ref().map(|url| chat::escape_qq(url.as_str())),
        );
        let mut has_mentions = false;
        for mention in mentions {
            if has_mentions {
                self.markdown.push(' ');
            }
            self.markdown.push_str("<qqbot-at-user id=\"");
            self.markdown.push_str(&mention);
            self.markdown.push_str("\" />");
            has_mentions = true;
        }
        if has_mentions {
            self.rich = true;
            return;
        }
        let style = style.unwrap_or_default();
        let emphasis = match (
            style.bold,
            style.italic || matches!(content, Some(Content::Description(_))),
        ) {
            (true, true) => "***",
            (true, false) => "**",
            (false, true) => "*",
            (false, false) => "",
        };
        let strike = if style.strikethrough { "~~" } else { "" };
        if let Some(url) = link.as_ref().filter(|url| {
            matches!(url.scheme(), "http" | "https") && !visible.contains(['\n', '\r'])
        }) {
            self.rich = true;
            self.markdown.push_str(strike);
            self.markdown.push_str(emphasis);
            self.markdown.push('[');
            if let Some(Content::Reference(reference)) = content {
                for run in &reference.runs {
                    let style = run.style.unwrap_or_default();
                    let marks = match (style.bold, style.italic) {
                        (true, true) => "***",
                        (true, false) => "**",
                        (false, true) => "*",
                        (false, false) => "",
                    };
                    let strike = if style.strikethrough { "~~" } else { "" };
                    let label = run.text.trim();
                    let leading = run.text.len() - run.text.trim_start().len();
                    if label.is_empty() || marks.is_empty() && strike.is_empty() {
                        markdown_text(&mut self.markdown, &run.text);
                        continue;
                    }
                    markdown_text(&mut self.markdown, &run.text[..leading]);
                    self.markdown.push_str(strike);
                    self.markdown.push_str(marks);
                    markdown_text(&mut self.markdown, label);
                    self.markdown.push_str(marks);
                    self.markdown.push_str(strike);
                    markdown_text(&mut self.markdown, &run.text[leading + label.len()..]);
                }
            }
            self.markdown.push_str("](");
            markdown_url(&mut self.markdown, url.as_str());
            self.markdown.push(')');
            self.markdown.push_str(emphasis);
            self.markdown.push_str(strike);
            return;
        } else if emphasis.is_empty() && strike.is_empty() {
            markdown_text(&mut self.markdown, visible);
        } else {
            for line in visible.split_inclusive('\n') {
                let trimmed = line.trim();
                if trimmed.is_empty() {
                    markdown_text(&mut self.markdown, line);
                    continue;
                }
                self.rich = true;
                let leading = line.len() - line.trim_start().len();
                markdown_text(&mut self.markdown, &line[..leading]);
                self.markdown.push_str(strike);
                self.markdown.push_str(emphasis);
                markdown_text(&mut self.markdown, trimmed);
                self.markdown.push_str(emphasis);
                self.markdown.push_str(strike);
                markdown_text(&mut self.markdown, &line[leading + trimmed.len()..]);
            }
        }
        if let Some(url) = link.filter(|url| url.as_str() != visible) {
            markdown_text(&mut self.markdown, " (");
            markdown_text(&mut self.markdown, url.as_str());
            markdown_text(&mut self.markdown, ")");
        }
    }

    fn finish(self) -> Rendered<MessageSegment> {
        if self.rich && self.markdown.len() <= MARKDOWN_BYTES {
            Rendered {
                message: segment::markdown(self.markdown),
                review: self.plain.full(),
            }
        } else {
            let review = self.plain.within(Platform::Qq.text_limit());
            Rendered {
                message: segment::text(review.clone()),
                review,
            }
        }
    }
}

pub(super) fn markdown_url(markdown: &mut String, url: &str) {
    for character in url.chars() {
        if matches!(character, '\\' | '(' | ')' | '&') {
            markdown.push('\\');
        }
        markdown.push(character);
    }
}

pub(super) fn markdown_text(markdown: &mut String, text: &str) {
    for character in text.chars() {
        match character {
            '<' => markdown.push('＜'),
            '>' => markdown.push('＞'),
            '\n' => markdown.push_str("  \n"),
            '\r' => {}
            '\\' | '`' | '*' | '_' | '~' | '[' | ']' | '(' | ')' | '#' | '+' | '-' | '.' | '!'
            | '|' | '&' => {
                markdown.push('\\');
                markdown.push(character);
            }
            _ => markdown.push(character),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::domains::chat::link_reference;

    #[test]
    fn builds_native_rich_content_from_nodes_only() {
        let mut result = Text::default();
        let link = Content::Reference(link_reference(
            "https://minecraft.wiki/w/Java_Edition_(guide)",
            vec![mik_protocol::wire::ChatTextRun {
                text: "Wiki [页]".into(),
                style: None,
            }],
            false,
        ));
        let fallback = "Wiki [页] (https://minecraft.wiki/w/Java_Edition_(guide))";
        result.write("<qqbot-at-everyone /> **原文** ", None, None, None);
        result.write(
            "Wiki [页]",
            Some(&link),
            Some(ChatTextStyle {
                bold: true,
                ..Default::default()
            }),
            None,
        );
        result.write(" @小爱", None, None, Some("member_01".into()));
        let rendered = result.finish();
        assert_eq!(
            rendered.review,
            format!("＜qqbot-at-everyone /＞ **原文** {fallback} @小爱")
        );
        let MessageSegment::Markdown(payload) = rendered.message else {
            panic!("expected QQ Markdown")
        };
        assert_eq!(
            payload["content"],
            r#"＜qqbot\-at\-everyone /＞ \*\*原文\*\* **[Wiki \[页\]](https://minecraft.wiki/w/Java_Edition_\(guide\))**<qqbot-at-user id="member_01" />"#
        );
    }

    #[test]
    fn marks_every_candidate_except_known_absences() {
        let config = serde_yaml_ng::from_str("qq: { app_id: app, groups: { group: {} } }").unwrap();
        let membership = Directory::new(&config);
        membership.set_qq_ready(true);
        membership.observe_qq("group", "joined", true);
        membership.observe_qq("group", "left", false);
        let candidates =
            ["joined", "unknown", "left"].map(|subject| mik_protocol::wire::ChatExternalIdentity {
                platform: "qq".into(),
                issuer: "app".into(),
                subject: subject.into(),
                ..Default::default()
            });
        let mentions = candidates.iter().filter_map(|account| {
            qq_id(
                &MentionTarget::External(account),
                &membership,
                "app",
                "group",
            )
        });
        let mut result = Text::default();
        result.write("@Alice", None, None, mentions);
        let rendered = result.finish();
        let MessageSegment::Markdown(payload) = rendered.message else {
            panic!("expected QQ Markdown")
        };
        assert_eq!(
            payload["content"],
            r#"<qqbot-at-user id="joined" /> <qqbot-at-user id="unknown" />"#
        );
        assert_eq!(rendered.review, "@Alice");
    }

    #[test]
    fn keeps_plain_text_for_unsupported_or_oversized_formatting() {
        let mut result = Text::default();
        result.write(
            "plain <text>",
            None,
            Some(ChatTextStyle {
                underlined: true,
                ..Default::default()
            }),
            None,
        );
        assert!(
            matches!(result.finish().message, MessageSegment::Text(value) if value == "plain ＜text＞")
        );

        let mut result = Text::default();
        let oversized = Content::Reference(link_reference(
            format!("https://example.org/{}", "a".repeat(MARKDOWN_BYTES)),
            vec![mik_protocol::wire::ChatTextRun {
                text: "link".into(),
                style: None,
            }],
            false,
        ));
        result.write("link", Some(&oversized), None, None);
        let rendered = result.finish();
        assert!(matches!(rendered.message, MessageSegment::Text(value) if value == "link"));
        assert_eq!(rendered.review, "link");
    }
}
