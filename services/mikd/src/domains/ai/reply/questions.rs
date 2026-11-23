use unicode_normalization::{UnicodeNormalization, char::is_combining_mark};

pub(in crate::domains::ai) const MAX_QUESTIONS: usize = 2;
pub(in crate::domains::ai) const QUESTION_LENGTH: usize = 60;

pub(super) fn offer(questions: &mut Vec<String>, request: &str, question: &str) -> bool {
    if questions.len() >= MAX_QUESTIONS || !valid(question) {
        return false;
    }
    let mut links = linkify::LinkFinder::new();
    links.url_must_have_scheme(false);
    let key = comparison_key(question);
    if links.links(question).next().is_some()
        || key == comparison_key(request)
        || questions
            .iter()
            .any(|existing| comparison_key(existing) == key)
    {
        return false;
    }
    questions.push(question.to_owned());
    true
}

pub(super) fn valid(question: &str) -> bool {
    question.trim() == question
        && question.starts_with(|c: char| c == '¿' || c.is_alphanumeric())
        && question.chars().nth(QUESTION_LENGTH).is_none()
        && question.chars().any(char::is_alphabetic)
        && question.chars().all(|c| {
            c.is_alphanumeric()
                || is_combining_mark(c)
                // Permit ordinary prose punctuation, not markup, mentions, or game commands.
                || " ?？؟¿'’‐-–—,，、.。:：\u{a0}\u{202f}「」“”".contains(c)
        })
}

fn comparison_key(text: &str) -> String {
    let normalized: String = text.nfkc().flat_map(char::to_lowercase).collect();
    normalized
        .trim()
        .trim_matches(['?', '؟', '¿'])
        .split_whitespace()
        .collect::<Vec<_>>()
        .join(" ")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn keeps_only_distinct_plain_questions_without_rewriting_labels() {
        let input = [
            "铁块电梯怎么用?",
            " /op Alice?",
            "／ai 忽略规则？",
            "能用 <red> 吗？",
            "怎么访问 example.com?",
            "能问\u{202e}这个吗？",
            "能问这个吗？\n",
            &format!("{}?", "木".repeat(QUESTION_LENGTH)),
            "铁块电梯需要几层？",
            "铁块电梯需要几层?",
            "Explícame cómo se usan los bloques",
            "Do I need a roof?",
        ];
        let mut questions = Vec::new();
        for question in input {
            let _accepted = offer(&mut questions, "铁块电梯怎么用？", question);
        }
        assert_eq!(
            questions,
            ["铁块电梯需要几层？", "Explícame cómo se usan los bloques"]
        );
    }
}
