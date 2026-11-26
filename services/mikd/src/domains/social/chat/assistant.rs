use super::super::locale::Locale;
use super::{Platform, reply_text};
use crate::domains::ai::Layout;
use mik_protocol::wire::AssistantReply;

pub(in crate::domains::social) fn assistant_thinking(locale: &Locale) -> String {
    format!("◆ {}", locale.text("ai-thinking"))
}

pub(in crate::domains::social) fn assistant_layout(
    reply: &AssistantReply,
    platform: Platform,
) -> Layout {
    let display = match platform {
        Platform::Qq => super::escape_qq,
        Platform::Matrix => str::to_owned,
    };
    Layout::new(reply, display)
}

pub(in crate::domains::social) fn manual_questions(
    questions: &[String],
    platform: Platform,
    locale: &Locale,
    prefix: &str,
    max_chars: usize,
) -> String {
    if questions.is_empty() {
        return String::new();
    }
    let command = format!("\n{prefix}ai ");
    let command_length = command.chars().count();
    let heading = reply_text(platform, &locale.text("ai-suggested-questions"));
    let mut list = format!("\n\n{heading}");
    let heading_end = list.len();
    let mut remaining = max_chars.saturating_sub(list.chars().count());
    for question in questions
        .iter()
        .filter(|question| copyable_question(question))
    {
        let length = command_length + question.chars().count();
        if length > remaining {
            continue;
        }
        list.push_str(&command);
        list.push_str(question);
        remaining -= length;
    }
    if list.len() == heading_end {
        list.clear();
    }
    list
}

pub(in crate::domains::social) fn copyable_question(question: &str) -> bool {
    !question.is_empty()
        && question.trim() == question
        && !question.contains(['<', '>', '\u{2028}', '\u{2029}'])
        && !question.chars().any(char::is_control)
}
