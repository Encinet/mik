mod markdown;

use super::super::{
    super::{
        chat::{self, Platform},
        followups::QuestionOffer,
        locale::Locale,
        runtime::ReplyBody,
    },
    RenderedAi,
};
use super::{
    keyboard,
    render::{MARKDOWN_BYTES, Rendered, markdown_text},
};
use mik_protocol::wire::AssistantReply;
use serde_json::json;
use uuid::Uuid;

pub(super) fn reply(
    answer: &AssistantReply,
    locale: &Locale,
    generation: Option<Uuid>,
    prefix: &str,
) -> Rendered<RenderedAi> {
    let limit = Platform::Qq.text_limit();
    let full = chat::assistant_layout(answer, Platform::Qq);
    let mut fallback = full.clone();
    fallback.clip(limit);
    let manual = chat::manual_questions(
        &fallback.follow_ups(),
        Platform::Qq,
        locale,
        prefix,
        limit.saturating_sub(fallback.visible.as_str().chars().count()),
    );
    let (mut plain, plain_visible) = fallback
        .visible
        .plain_within(limit - manual.chars().count());
    plain.push_str(&manual);
    let (mut markdown, styled) = markdown::answer(answer);
    let questions = full.follow_ups();
    let mut review = plain.clone();
    let body = if markdown.len() <= MARKDOWN_BYTES {
        let room = MARKDOWN_BYTES - markdown.len();
        let numbered = generation.and_then(|id| question_keyboard(id, &questions, locale, prefix));
        let selected = numbered
            .and_then(|(offer, label)| {
                let suffix = markdown_suffix(&label);
                (suffix.len() <= room).then_some((Some(offer), label, suffix))
            })
            .unwrap_or_else(|| {
                let label = chat::manual_questions(&questions, Platform::Qq, locale, prefix, room);
                let suffix = markdown_suffix(&label);
                if suffix.len() <= room {
                    (None, label, suffix)
                } else {
                    (None, String::new(), String::new())
                }
            });
        let (offer, questions, suffix) = selected;
        if styled
            || offer.is_some()
            || full.visible.as_str() != fallback.visible.as_str()
            || questions != manual
        {
            markdown.push_str(&suffix);
            let (mut projection, _) = full.visible.plain_within(usize::MAX);
            projection.push_str(&questions);
            if projection != plain {
                review.push_str("\n\n");
                review.push_str(&projection);
            }
            return Rendered {
                message: RenderedAi {
                    body: ReplyBody::QqMarkdown {
                        markdown: json!({ "content": markdown }),
                        plain,
                        plain_visible,
                        offer,
                    },
                    visible_answer: full.visible,
                },
                review,
            };
        }
        ReplyBody::Plain(plain)
    } else {
        ReplyBody::Plain(plain)
    };
    Rendered {
        message: RenderedAi {
            body,
            visible_answer: plain_visible,
        },
        review,
    }
}

fn markdown_suffix(text: &str) -> String {
    if text.is_empty() {
        return String::new();
    }
    let mut result = "\n\n".to_owned();
    markdown_text(&mut result, text.trim_start_matches('\n'));
    result
}

fn question_keyboard(
    generation: Uuid,
    questions: &[String],
    locale: &Locale,
    prefix: &str,
) -> Option<(QuestionOffer, String)> {
    if !keyboard::supported(questions) {
        return None;
    }
    let heading = chat::escape_qq(&locale.text("ai-suggested-questions"));
    let mut numbered = format!("\n\n{heading}");
    for (number, question) in keyboard::numbered(questions) {
        numbered.push('\n');
        numbered.push_str(number);
        numbered.push('\n');
        numbered.push_str(prefix);
        numbered.push_str("ai ");
        numbered.push_str(question);
    }
    Some((
        QuestionOffer::new(generation, questions.to_vec())?,
        numbered,
    ))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::domains::ai::sample;
    use pulldown_cmark::{Event, Options, Parser, Tag, TagEnd};

    #[test]
    fn reviews_plain_and_markdown_with_their_visible_questions() {
        let question = "铁块电梯相邻两层要隔多远？";
        let answer = sample(&format!(
            "**铁块**电梯需要安全的落点。\n\n[{question}](ask:)"
        ));
        let locale = Locale::get("zh-CN");
        for generation in [None, Some(Uuid::new_v4())] {
            let rendered = reply(&answer, locale, generation, "!");
            let ReplyBody::QqMarkdown {
                markdown,
                plain,
                offer,
                ..
            } = rendered.message.body
            else {
                panic!("expected Markdown")
            };
            let content = markdown["content"].as_str().unwrap();
            assert_eq!(offer.is_some(), generation.is_some());
            assert_eq!(content.contains('①'), generation.is_some());
            assert!(content.ends_with(&format!("\n\\!ai {question}")));
            assert!(plain.ends_with(&format!("\n!ai {question}")) && !plain.contains('①'));
            assert!(rendered.review.starts_with(&plain));
            assert_eq!(rendered.review.contains('①'), generation.is_some());
        }
    }

    #[test]
    fn preserves_nested_styles_inline_links_and_markdown_blocks_when_reparsed() {
        let answer = sample(concat!(
            "## Route\n\n> 7. **a *b*, c** [see **map**](ref:r1)  \n>    tail\n",
            ">    - [x] ~~done~~\n\n>    ````text\n>    <tag> **literal**\n>    ```\n>    ````\n\n",
            "| Comparison | Value |\n| :--- | ---: |\n| `a\\|b` | 3 |"
        ));
        let rendered = reply(&answer, Locale::get("en-US"), None, "/");
        let ReplyBody::QqMarkdown {
            markdown, plain, ..
        } = rendered.message.body
        else {
            panic!("expected Markdown")
        };
        let content = markdown["content"].as_str().unwrap();
        let events = Parser::new_ext(
            content,
            Options::ENABLE_TABLES | Options::ENABLE_TASKLISTS | Options::ENABLE_STRIKETHROUGH,
        )
        .collect::<Vec<_>>();
        assert!(content.contains("**a *b*, c**"), "{content}");
        assert!(
            content.contains("[see **map**](https://mcmik.top/wiki)"),
            "{content}"
        );
        assert!(
            events.contains(&Event::Start(Tag::List(Some(7)))),
            "{events:?}"
        );
        assert!(events.contains(&Event::HardBreak), "{events:?}");
        assert!(events.contains(&Event::TaskListMarker(true)), "{events:?}");
        assert!(events.contains(&Event::End(TagEnd::Strikethrough)));
        assert!(
            events.contains(&Event::End(TagEnd::CodeBlock)),
            "{events:?}"
        );
        assert!(events.contains(&Event::Code("a|b".into())), "{events:?}");
        assert!(content.contains("＜tag＞ **literal**"), "{content}");
        assert!(plain.contains("Comparison │ Value\na|b │ 3"), "{plain}");
    }

    #[test]
    fn rich_body_uses_its_own_budget_and_plain_fallback_marks_the_cut() {
        let answer = sample(&format!(
            "**Intro**\n\n{}\n\nFinal thought.",
            "a".repeat(2000)
        ));
        let rendered = reply(&answer, Locale::get("en-US"), None, "/");
        let ReplyBody::QqMarkdown {
            markdown,
            plain,
            plain_visible,
            ..
        } = rendered.message.body
        else {
            panic!("expected Markdown")
        };
        assert!(
            markdown["content"]
                .as_str()
                .unwrap()
                .ends_with("Final thought\\.")
        );
        assert!(rendered.review.contains("Final thought."));
        assert!(plain.ends_with('…'));
        assert!(plain.chars().count() <= Platform::Qq.text_limit());
        assert_eq!(plain_visible.as_str(), plain);
        assert!(
            rendered
                .message
                .visible_answer
                .as_str()
                .ends_with("Final thought.")
        );
    }
}
