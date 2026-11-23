use super::super::{
    super::{
        chat::{self, Platform},
        locale::Locale,
        runtime::ReplyBody,
    },
    RenderedAi,
};
use super::render::{MatrixReply, RICH_TEXT_BYTES, inline_html};
use htmlescape::encode_minimal;
use matrix_sdk::ruma::events::{Mentions, room::message::RoomMessageEventContent};
use mik_protocol::wire::{
    AssistantBlock, AssistantInline, AssistantReply, AssistantRow, assistant_block::Content,
    assistant_part::Target, assistant_span::Style,
};
use std::fmt::Write as _;

pub(in crate::domains::social::platform) fn reply(
    answer: &AssistantReply,
    locale: &Locale,
    prefix: &str,
) -> RenderedAi {
    let mut layout = chat::assistant_layout(answer, Platform::Matrix);
    let mut html = Html::default();
    html.blocks(&answer.blocks);
    let limit = Platform::Matrix.text_limit();
    let raw = layout.visible.as_str();
    let size = raw.len() + html.0.len();
    let (content, visible_answer) = if size <= RICH_TEXT_BYTES {
        let room = RICH_TEXT_BYTES - size;
        let questions = chat::manual_questions(
            &layout.follow_ups(),
            Platform::Matrix,
            locale,
            prefix,
            room.min(limit),
        );
        let questions_html = if questions.is_empty() {
            String::new()
        } else {
            format!("<p>{}</p>", inline_html(questions.trim_start_matches('\n')))
        };
        let extra = questions.len() + questions_html.len();
        let (mut plain, _) = layout
            .visible
            .plain_within(raw.chars().count() + if extra <= room { room - extra } else { room });
        if extra <= room {
            plain.push_str(&questions);
            html.0.push_str(&questions_html);
        }
        (
            RoomMessageEventContent::text_html(plain, html.0),
            layout.visible,
        )
    } else {
        layout.clip(limit);
        let room = limit.saturating_sub(layout.visible.as_str().chars().count());
        let questions =
            chat::manual_questions(&layout.follow_ups(), Platform::Matrix, locale, prefix, room);
        let (mut plain, visible) = layout
            .visible
            .plain_within(limit - questions.chars().count());
        plain.push_str(&questions);
        (RoomMessageEventContent::text_plain(plain), visible)
    };
    RenderedAi {
        body: ReplyBody::Matrix(MatrixReply(Box::new(content.add_mentions(Mentions::new())))),
        visible_answer,
    }
}

#[derive(Default)]
struct Html(String);

#[expect(
    clippy::let_underscore_must_use,
    reason = "Formatting into a String cannot fail"
)]
impl Html {
    fn blocks(&mut self, blocks: &[AssistantBlock]) {
        for block in blocks.iter().filter_map(|block| block.content.as_ref()) {
            match block {
                Content::Paragraph(body) => self.tag("p", |html| html.inline(body)),
                Content::Heading(heading) => {
                    if let Some(body) = &heading.body {
                        let tag = format!("h{}", heading.level.clamp(1, 6));
                        self.tag(&tag, |html| html.inline(body));
                    }
                }
                Content::BlockQuote(quote) => {
                    self.tag("blockquote", |html| html.blocks(&quote.blocks));
                }
                Content::Code(code) => {
                    self.0.push_str("<pre><code");
                    if !code.language.is_empty() {
                        let _ = write!(
                            self.0,
                            " class=\"language-{}\"",
                            encode_minimal(&code.language)
                        );
                    }
                    let _ = write!(self.0, ">{}</code></pre>", encode_minimal(&code.text));
                }
                Content::List(list) => {
                    let tag = if let Some(number) = list.first_number {
                        let _ = write!(self.0, "<ol start=\"{number}\">");
                        "ol"
                    } else {
                        self.0.push_str("<ul>");
                        "ul"
                    };
                    for item in &list.children {
                        self.tag("li", |html| {
                            match item.checked {
                                Some(true) => html.0.push_str("☑ "),
                                Some(false) => html.0.push_str("☐ "),
                                None => (),
                            }
                            html.blocks(&item.blocks);
                        });
                    }
                    let _ = write!(self.0, "</{tag}>");
                }
                Content::Divider(_) => self.0.push_str("<hr>"),
                Content::Table(table) => self.tag("table", |html| {
                    if let Some(header) = &table.header {
                        html.tag("thead", |html| html.row(header, "th"));
                    }
                    html.tag("tbody", |html| {
                        for row in &table.rows {
                            html.row(row, "td");
                        }
                    });
                }),
            }
        }
    }

    fn tag(&mut self, tag: &str, render: impl FnOnce(&mut Self)) {
        let _ = write!(self.0, "<{tag}>");
        render(self);
        let _ = write!(self.0, "</{tag}>");
    }

    fn row(&mut self, row: &AssistantRow, cell_tag: &str) {
        self.tag("tr", |html| {
            for cell in &row.cells {
                html.tag(cell_tag, |html| html.inline(cell));
            }
        });
    }

    fn inline(&mut self, body: &AssistantInline) {
        for part in &body.parts {
            let linked = if let Some(Target::Url(url)) = &part.target {
                let _ = write!(self.0, "<a href=\"{}\">", encode_minimal(url));
                true
            } else {
                false
            };
            for span in &part.spans {
                let tags =
                    span.styles
                        .iter()
                        .filter_map(|&style| match Style::try_from(style).ok()? {
                            Style::Bold => Some("strong"),
                            Style::Italic => Some("em"),
                            Style::Strikethrough => Some("del"),
                            Style::Code => Some("code"),
                            Style::Unspecified => None,
                        });
                for tag in tags.clone() {
                    let _ = write!(self.0, "<{tag}>");
                }
                self.0.push_str(&inline_html(&span.text));
                for tag in tags.rev() {
                    let _ = write!(self.0, "</{tag}>");
                }
            }
            if linked {
                self.0.push_str("</a>");
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::domains::{ai::sample, social::platform::matrix::render::command_reply};
    use serde_json::json;

    #[test]
    fn same_document_renders_html_and_readable_plain_without_commands_or_mentions() {
        let rendered = reply(
            &sample(concat!(
                "> First line.\n> Still the same line.  \n> Hard break.\n\n",
                "| First | Next |\n| --- | --- |\n| [Map](ref:r1) | ~~old~~ |\n\n",
                "- [x] Read the rules\n\n```text\n<mx-reply>@room</mx-reply> &lt;\n```"
            )),
            Locale::get("en-US"),
            "/",
        );
        let content = serde_json::to_value(command_reply(rendered.body)).unwrap();
        let html = content["formatted_body"].as_str().unwrap();
        assert!(html.contains(
            "<blockquote><p>First line. Still the same line.<br>Hard break.</p></blockquote>"
        ));
        assert!(html.contains(concat!(
            "<table><thead><tr><th>First</th><th>Next</th></tr></thead><tbody>",
            "<tr><td><a href=\"https://mcmik.top/wiki\">Map</a></td><td><del>old</del></td></tr></tbody></table>"
        )));
        assert!(html.contains("<pre><code class=\"language-text\">&lt;mx-reply&gt;@room&lt;/mx-reply&gt; &amp;lt;</code></pre>"));
        assert!(
            content["body"]
                .as_str()
                .unwrap()
                .contains("Map (https://mcmik.top/wiki) │ old\n\n• ☑ Read the rules")
        );
        assert_eq!(content["m.mentions"], json!({}));
    }

    #[test]
    fn assistant_records_only_links_that_fit_when_html_is_too_large() {
        let tail = "&".repeat(Platform::Matrix.text_limit() - 3);
        let visible = format!("指南 {tail}");
        let rendered = reply(
            &sample(&format!("[指南](ref:r1) {tail}")),
            Locale::get("en-US"),
            "/",
        );
        assert_eq!(rendered.visible_answer.as_str(), visible);
        assert!(
            serde_json::to_value(&rendered.visible_answer)
                .unwrap()
                .get("references")
                .is_none()
        );
        assert_eq!(
            serde_json::to_value(command_reply(rendered.body)).unwrap(),
            json!({ "msgtype": "m.text", "body": visible, "m.mentions": {} })
        );
    }
}
