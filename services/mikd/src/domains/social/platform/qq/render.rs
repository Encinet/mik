use super::super::super::{
    chat::{self, Outbound, OutputText, Platform},
    locale::Locale,
    runtime::Runtime,
};
use crate::domains::chat::link_destination;
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
        let mention = content.and_then(|content| {
            let mut targets = chat::targets(content, bindings, Platform::Qq).filter(|target| {
                let [account, scope, subject] = target.parts();
                scope.is_empty()
                    && account == issuer
                    && !subject.is_empty()
                    && subject
                        .bytes()
                        .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'_' | b'-'))
                    && runtime.membership().qq_joined(issuer, group, subject)
            });
            let target = targets.next()?;
            targets.next().is_none().then_some(target)
        });
        rendered.write(
            visible,
            content,
            style,
            mention.as_ref().map(|target| target.parts()[2]),
        );
    });
    rendered.finish()
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
        mention: Option<&str>,
    ) {
        let link = match content {
            Some(Content::Link(link)) => link_destination(&link.url),
            _ => None,
        };
        self.plain.push(
            &chat::escape_qq(visible),
            link.as_ref().map(|url| chat::escape_qq(url.as_str())),
        );
        if let Some(mention) = mention {
            self.markdown.push_str("<qqbot-at-user id=\"");
            self.markdown.push_str(mention);
            self.markdown.push_str("\" />");
            self.rich = true;
            return;
        }
        let style = style.unwrap_or_default();
        let emphasis = match (
            style.bold,
            style.italic
                || matches!(
                    content,
                    Some(Content::Description(_) | Content::ItemLabel(_))
                ),
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
            markdown_text(&mut self.markdown, visible);
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
    use mik_protocol::wire::ChatLink;

    #[test]
    fn builds_native_rich_content_from_nodes_only() {
        let mut result = Text::default();
        let link = Content::Link(ChatLink {
            label: "Wiki [页]".into(),
            url: "https://minecraft.wiki/w/Java_Edition_(guide)".into(),
            ..Default::default()
        });
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
        result.write(" @小爱", None, None, Some("member_01"));
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
        let oversized = Content::Link(ChatLink {
            label: "link".into(),
            url: format!("https://example.org/{}", "a".repeat(MARKDOWN_BYTES)),
            ..Default::default()
        });
        result.write("link", Some(&oversized), None, None);
        let rendered = result.finish();
        assert!(matches!(rendered.message, MessageSegment::Text(value) if value == "link"));
        assert_eq!(rendered.review, "link");
    }
}
