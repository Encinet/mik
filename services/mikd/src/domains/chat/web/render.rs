use super::super::link_destination;
use mik_protocol::wire::{
    ChatBody, ChatExternalIdentity, ChatNoticeRecord, ChatRecord, chat_notice_record,
    chat_part::Content, chat_presence_notice, chat_record::Kind, chat_user_record::Source,
};
use serde_json::{Map, Value, json};
use sha2::{Digest, Sha256};
use std::collections::HashSet;

pub(super) struct MessageView<'a> {
    pub(super) record: &'a ChatRecord,
    pub(super) message_id: &'a str,
    pub(super) order_key: &'a str,
    pub(super) expires_at: i64,
}

pub(super) fn message(
    row: &MessageView<'_>,
    channel: &str,
    viewer: &str,
    hidden: &HashSet<&str>,
) -> Option<Value> {
    let record = row.record;
    let mut value = json!({
        "id": row.message_id, "channel": channel, "order": row.order_key,
        "createdAt": record.created_at, "expiresAt": row.expires_at,
    });
    match record.kind.as_ref()? {
        Kind::User(user) => {
            let author = user.author.as_ref()?;
            let player = author.player.as_ref();
            if player.is_some_and(|player| hidden.contains(player.id.as_str()))
                || user
                    .target
                    .as_ref()
                    .is_some_and(|target| hidden.contains(target.id.as_str()))
            {
                return None;
            }
            let id = if let Some(external) = &author.external {
                let key = [
                    &external.platform,
                    &external.issuer,
                    &external.scope,
                    &external.subject,
                ];
                format!(
                    "external:{}",
                    hex::encode(Sha256::digest(key.map(String::as_str).join("\0")))
                )
            } else {
                player.map_or_else(
                    || format!("message:{}", row.message_id),
                    |player| player.id.clone(),
                )
            };
            let mut sender = json!({"id": id, "name": author.display_name});
            if let Some(player) = player {
                sender["playerUuid"] = json!(player.id);
                sender["playerName"] = json!(player.name);
            }
            let body = user.body.as_ref()?;
            value["kind"] = json!("user");
            value["sender"] = sender;
            value["content"] = body_parts(body);
            value["mentioned"] = json!(
                player.is_none_or(|player| player.id != viewer)
                    && body.parts.iter().any(|part| match &part.content {
                        Some(Content::Broadcast(_)) => true,
                        Some(Content::PlayerMention(mention)) => mention
                            .player
                            .as_ref()
                            .is_some_and(|player| player.id == viewer),
                        _ => false,
                    })
            );
            value["source"] = match user.source() {
                Source::Game => json!({"kind":"game"}),
                Source::Web => json!({"kind":"web"}),
                Source::Social => {
                    let source = user.social.as_ref()?;
                    json!({"kind":"social", "platformId":source.platform_id, "platformName":source.platform_name})
                }
                Source::Unspecified => return None,
            };
            if let Some(target) = &user.target {
                value["targetUuid"] = json!(target.id);
                value["targetName"] = json!(target.name);
            }
            Some(value)
        }
        Kind::Notice(notice) => render_notice(value, notice, channel, viewer, hidden),
    }
}

fn render_notice(
    mut value: Value,
    notice: &ChatNoticeRecord,
    channel: &str,
    viewer: &str,
    hidden: &HashSet<&str>,
) -> Option<Value> {
    if let Some(subject) = &notice.subject {
        if hidden.contains(subject.id.as_str()) {
            return None;
        }
        value["subject"] = json!({"playerUuid":subject.id, "name":subject.name});
    }
    if channel == "private" {
        if !notice.participants.iter().any(|player| player.id == viewer)
            || notice
                .participants
                .iter()
                .any(|player| hidden.contains(player.id.as_str()))
        {
            return None;
        }
        let peer = notice
            .participants
            .iter()
            .find(|player| player.id != viewer)?;
        value["recipientUuid"] = json!(viewer);
        value["targetUuid"] = json!(peer.id);
        value["targetName"] = json!(peer.name);
    }
    value["kind"] = json!("server");
    value["countsAsUnread"] = json!(notice.counts_as_unread);
    value["content"] = match notice.content.as_ref()? {
        chat_notice_record::Content::Body(body) => body_parts(body),
        chat_notice_record::Content::Presence(presence) => {
            if matches!(
                presence.kind(),
                chat_presence_notice::Kind::WebJoined | chat_presence_notice::Kind::WebLeft
            ) && notice
                .subject
                .as_ref()
                .is_some_and(|subject| subject.id == viewer)
            {
                return None;
            }
            let template = match presence.kind() {
                chat_presence_notice::Kind::GameJoined => "gameJoined",
                chat_presence_notice::Kind::GameLeft => "gameLeft",
                chat_presence_notice::Kind::WebJoined => "webJoined",
                chat_presence_notice::Kind::WebLeft => "webLeft",
                chat_presence_notice::Kind::Unspecified => return None,
            };
            json!({"template":template, "values":{"name":presence.name}})
        }
    };
    Some(value)
}

fn external_name(identity: &ChatExternalIdentity) -> &str {
    if identity.display_name.trim().is_empty() {
        &identity.subject
    } else {
        &identity.display_name
    }
}

fn body_parts(body: &ChatBody) -> Value {
    let mut runs = Vec::new();
    let mut plain = String::new();
    let mut href_bytes = 0;
    let mut units_left = 16_000_usize;
    let mut style_budget = 16 * 1024;
    for part in &body.parts {
        let Some(content) = &part.content else {
            continue;
        };
        let (text, link, mention) = match content {
            Content::Text(text) | Content::ItemLabel(text) => (text.clone(), None, false),
            Content::Link(link) => (
                link.label.clone(),
                link_destination(&link.url).map(String::from),
                false,
            ),
            Content::Broadcast(label) => (label.clone(), None, true),
            Content::ExternalMention(identity) => {
                (format!("@{}", external_name(identity)), None, false)
            }
            Content::PlayerMention(mention) => {
                let Some(player) = &mention.player else {
                    continue;
                };
                let label = mention.source_identity.as_ref().map_or_else(
                    || format!("@{}", player.name),
                    |identity| format!("@{}({})", external_name(identity), player.name),
                );
                (label, None, true)
            }
            Content::Description(description) => (description.fallback.clone(), None, false),
        };
        let mut text_end = 0;
        for ch in text.chars() {
            let Some(left) = units_left.checked_sub(ch.len_utf16()) else {
                break;
            };
            units_left = left;
            text_end += ch.len_utf8();
        }
        if text_end == 0 {
            if text.is_empty() {
                continue;
            }
            break;
        }
        let link = link.filter(|href| href.len() <= 2048 && href_bytes + href.len() <= 4096);
        let mut style = Map::new();
        if let Some(source) = &part.style {
            if let Some(color) = source.color {
                style.insert("color".into(), json!(color));
            }
            if let Some(color) = source.gradient_end_color {
                style.insert("gradientEndColor".into(), json!(color));
            }
            for (name, value) in [
                ("bold", source.bold),
                ("italic", source.italic),
                ("underlined", source.underlined),
                ("strikethrough", source.strikethrough),
            ] {
                if value {
                    style.insert(name.into(), json!(true));
                }
            }
        }
        let extra = 128 + link.as_ref().map_or(0, String::len);
        if (link.is_some() || mention || !style.is_empty())
            && style_budget >= extra
            && runs.len() + usize::from(!plain.is_empty()) < 255
        {
            if !plain.is_empty() {
                runs.push(json!({"text":std::mem::take(&mut plain)}));
            }
            let mut run = json!({"text": &text[..text_end]});
            if let Some(link) = link {
                href_bytes += link.len();
                run["href"] = json!(link);
            }
            if mention {
                run["mention"] = json!(true);
            }
            if !style.is_empty() {
                run["style"] = Value::Object(style);
            }
            style_budget -= extra;
            runs.push(run);
        } else {
            plain.push_str(&text[..text_end]);
        }
        if text_end < text.len() {
            break;
        }
    }
    if !plain.is_empty() {
        runs.push(json!({"text":plain}));
    }
    Value::Array(runs)
}

#[cfg(test)]
mod tests {
    use super::*;
    use mik_protocol::wire::ChatPart;

    #[test]
    fn body_limit_keeps_a_prefix_across_parts() {
        let text_part = |text| ChatPart {
            style: None,
            content: Some(Content::Text(text)),
        };
        let prefix = "x".repeat(15_999);
        let body = ChatBody {
            parts: vec![
                text_part(prefix.clone()),
                text_part("😀".into()),
                text_part("z".into()),
            ],
        };
        assert_eq!(body_parts(&body), json!([{"text": prefix}]));
    }
}
