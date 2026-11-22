use qqbot_sdk_rs::MessageElement;

pub(super) const REPLY: u16 = 103;

pub(super) enum Author {
    Member(String),
    Unknown,
}

pub(super) fn collection(kind: Option<u16>) -> bool {
    matches!(kind, Some(101 | 102))
}

pub(super) fn author(
    kind: Option<u16>,
    elements: &[MessageElement],
    has_reference: bool,
) -> Option<Author> {
    if collection(kind) {
        return has_reference.then_some(Author::Unknown);
    }
    let mut quotes = elements
        .iter()
        .filter(|element| kind == Some(REPLY) || element.message_type == Some(REPLY));
    let Some(quote) = quotes.next() else {
        return (has_reference || kind == Some(REPLY)).then_some(Author::Unknown);
    };
    if quotes.next().is_some()
        || collection(quote.message_type)
        || is_history(quote)
        || !quote.msg_elements.is_empty()
    {
        return Some(Author::Unknown);
    }
    let member = quote
        .author
        .as_ref()
        .filter(|user| user.bot != Some(true) && user.is_you != Some(true))
        .and_then(|user| user.member_openid.as_ref())
        .filter(|id| !id.trim().is_empty());
    Some(member.map_or(Author::Unknown, |id| Author::Member(id.clone())))
}

pub(super) fn is_history(element: &MessageElement) -> bool {
    element.message_type == Some(102) || element.content.as_deref().is_some_and(serialized_history)
}

fn serialized_history(content: &str) -> bool {
    content
        .trim_start()
        .lines()
        .next()
        .and_then(|line| line.trim_end().strip_prefix("=== 消息 "))
        .and_then(|header| header.strip_suffix(" ==="))
        .is_some_and(|index| !index.is_empty() && index.bytes().all(|digit| digit.is_ascii_digit()))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn profile_reply_ignores_forwarded_and_bot_authors() {
        let decode = |json| serde_json::from_str::<Vec<MessageElement>>(json).unwrap();
        let quote = decode(r#"[{"author":{"member_openid":"quoted"},"content":"hello"}]"#);
        assert!(
            matches!(author(Some(103), &quote, false), Some(Author::Member(id)) if id == "quoted")
        );
        assert!(author(Some(102), &quote, false).is_none());
        for element in [
            r#"[{"message_type":102,"author":{"member_openid":"forwarded"}}]"#,
            r#"[{"author":{"member_openid":"forwarded"},"content":"=== 消息 1 ===\n[发送者] example"}]"#,
            r#"[{"author":{"member_openid":"bot","is_you":true}}]"#,
        ] {
            assert!(matches!(
                author(Some(103), &decode(element), false),
                Some(Author::Unknown)
            ));
        }
        assert!(matches!(author(None, &[], true), Some(Author::Unknown)));
    }
}
