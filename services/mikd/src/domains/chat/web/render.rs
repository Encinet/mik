use super::super::{
    description_label, external_account, external_name, label_text, link_destination,
    mentioned_person,
    model::{Channel, Destination, destination},
    player_snapshot, social_platform_label,
};
use mik_protocol::wire::{
    ChatBody, ChatIdentity, ChatPresenceNotice, ChatRecord, ChatTextStyle, chat_part::Content,
    chat_presence_notice, chat_record::Kind, chat_reference::Target as ReferenceTarget,
};
use serde_json::{Map, Value, json};
use sha2::{Digest, Sha256};
use uuid::Uuid;

pub(super) struct MessageView<'a> {
    pub(super) record: &'a ChatRecord,
    pub(super) message_id: &'a str,
    pub(super) order_key: &'a str,
    pub(super) expires_at: i64,
}

pub(super) fn visible_to(record: &ChatRecord, viewer: &str, hidden: impl Fn(&str) -> bool) -> bool {
    match &record.kind {
        Some(Kind::User(user)) => {
            let author = user.author.as_ref().and_then(player_snapshot);
            let Ok(dest) = destination(record) else {
                return false;
            };
            author.is_none_or(|player| !hidden(&player.id))
                && !matches!(dest, Destination::Private { recipient, .. } if hidden(&recipient.id))
        }
        Some(Kind::Presence(notice)) => notice.player.as_ref().is_some_and(|player| {
            !(hidden(&player.id) || record.platform == "web" && player.id == viewer)
        }),
        None => false,
    }
}

pub(super) struct ProjectedMessage {
    pub(super) fields: Value,
    pub(super) channel: Channel,
    mentions: Mentions,
}

impl ProjectedMessage {
    pub(super) fn mentioned(
        &self,
        record: &ChatRecord,
        viewer: &str,
        resolve: impl Fn(&ChatIdentity) -> Option<Uuid>,
    ) -> bool {
        let Some(Kind::User(user)) = &record.kind else {
            return false;
        };
        if user
            .author
            .as_ref()
            .and_then(player_snapshot)
            .is_some_and(|p| p.id == viewer)
        {
            return false;
        }
        user.body
            .as_ref()
            .is_some_and(|body| self.mentions.includes(body, viewer, resolve))
    }
}

pub(super) fn project(
    row: &MessageView<'_>,
    page_channel: Option<Channel>,
) -> Option<ProjectedMessage> {
    let record = row.record;
    let destination = destination(record).ok()?;
    let channel = destination.channel();
    if page_channel.is_some_and(|expected| channel != expected) {
        return None;
    }
    let mut value = json!({
        "id": row.message_id, "order": row.order_key,
        "createdAt": record.created_at_ms, "expiresAt": row.expires_at,
    });
    if page_channel.is_none() {
        value["channel"] = json!(channel.name());
    }
    match record.kind.as_ref()? {
        Kind::User(user) => {
            let author = user.author.as_ref()?;
            let player = player_snapshot(author);
            let target = match destination {
                Destination::Private { recipient, .. } => Some(recipient),
                _ => None,
            };
            let platform = &record.platform;
            let (id, name, source) = if let Some(external) = external_account(author) {
                let key = [
                    &external.platform,
                    &external.issuer,
                    &external.scope,
                    &external.subject,
                ];
                let id = format!(
                    "external:{}",
                    hex::encode(Sha256::digest(key.map(String::as_str).join("\0")))
                );
                (
                    id,
                    external_name(external),
                    json!({"kind":"social", "platformId": platform,
                    "platformName": social_platform_label(platform)}),
                )
            } else {
                let player = player?;
                (
                    player.id.clone(),
                    player.name.as_str(),
                    json!({"kind": platform}),
                )
            };
            let sender = match player {
                Some(p) if p.id == id && p.name == name => {
                    json!({"playerUuid":p.id, "playerName":p.name})
                }
                Some(p) => json!({"id":id, "name":name, "playerUuid":p.id, "playerName":p.name}),
                None => json!({"id":id, "name":name}),
            };
            let body = user.body.as_ref()?;
            value["kind"] = json!("user");
            value["sender"] = sender;
            let (parts, mentions) = body_parts(body);
            value["content"] = parts;
            value["source"] = source;
            if let Some(target) = target {
                value["targetUuid"] = json!(target.id);
                value["targetName"] = json!(target.name);
            }
            Some(ProjectedMessage {
                fields: value,
                channel,
                mentions,
            })
        }
        Kind::Presence(notice) => Some(ProjectedMessage {
            fields: render_notice(value, &record.platform, notice)?,
            channel,
            mentions: Mentions::default(),
        }),
    }
}

fn render_notice(mut value: Value, platform: &str, notice: &ChatPresenceNotice) -> Option<Value> {
    use chat_presence_notice::Kind::{Joined, Left};
    let player = notice.player.as_ref()?;
    let template = match (platform, notice.kind()) {
        ("game", Joined) => "gameJoined",
        ("game", Left) => "gameLeft",
        ("web", Joined) => "webJoined",
        ("web", Left) => "webLeft",
        _ => return None,
    };
    value["subject"] = json!({"playerUuid":player.id, "name":player.name});
    value["kind"] = json!("server");
    value["content"] = json!(template);
    Some(value)
}

#[derive(Default)]
struct Mentions {
    everyone: bool,
    // Indices of complete references in the immutable source body.
    people: Vec<usize>,
}

impl Mentions {
    fn includes(
        &self,
        body: &ChatBody,
        viewer: &str,
        resolve: impl Fn(&ChatIdentity) -> Option<Uuid>,
    ) -> bool {
        if self.everyone || self.people.is_empty() {
            return self.everyone;
        }
        let Ok(viewer) = Uuid::parse_str(viewer) else {
            return false;
        };
        self.people
            .iter()
            .filter_map(|&index| mentioned_person(body.parts.get(index)?.content.as_ref()?))
            .any(|person| resolve(person) == Some(viewer))
    }
}

fn body_parts(body: &ChatBody) -> (Value, Mentions) {
    let mut mentions = Mentions::default();
    let mut runs = Vec::new();
    let mut plain = String::new();
    let mut href_bytes = 0;
    let mut units_left = 16_000_usize;
    let mut style_budget = 16 * 1024;
    for (index, part) in body.parts.iter().enumerate() {
        let Some(content) = &part.content else {
            continue;
        };
        let (text, link, mention) = match content {
            Content::Text(run) => (run.text.clone(), None, None),
            Content::Reference(reference) => {
                let Some(target) = &reference.target else {
                    continue;
                };
                let (link, mention) = match target {
                    ReferenceTarget::Link(link) => {
                        (link_destination(&link.uri).map(String::from), None)
                    }
                    ReferenceTarget::Person(_) | ReferenceTarget::Audience(_) => {
                        (None, Some(target))
                    }
                };
                (label_text(&reference.runs), link, mention)
            }
            Content::Description(description) => {
                (description_label(description).into_owned(), None, None)
            }
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
        let complete = text_end == text.len();
        let mention = mention.filter(|_| complete);
        match mention {
            Some(ReferenceTarget::Audience(_)) => mentions.everyone = true,
            Some(ReferenceTarget::Person(_)) => mentions.people.push(index),
            _ => {}
        }
        let link =
            link.filter(|href| complete && href.len() <= 2048 && href_bytes + href.len() <= 4096);
        let style = projected_style(content);
        let extra = 128 + link.as_ref().map_or(0, String::len);
        if (link.is_some() || mention.is_some() || !style.is_empty())
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
            if mention.is_some() {
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
    (Value::Array(runs), mentions)
}

fn projected_style(content: &Content) -> Map<String, Value> {
    let mut style = Map::new();
    let uniform = |runs: &[mik_protocol::wire::ChatTextRun]| {
        let first = runs.first()?;
        first
            .style
            .filter(|_| runs.iter().all(|run| run.style == first.style))
    };
    let source: Option<ChatTextStyle> = match content {
        Content::Text(run) => run.style,
        Content::Description(description) => description.style,
        Content::Reference(reference) => uniform(&reference.runs),
    };
    if let Some(source) = source {
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
    style
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::domains::chat::{external_principal, person_mention, text_part};
    use mik_protocol::wire::{ChatExternalIdentity, ChatPart, ChatTextRun};

    #[test]
    fn body_limit_keeps_a_prefix_across_parts() {
        let prefix = "x".repeat(15_999);
        let body = ChatBody {
            parts: vec![
                text_part(&prefix, None),
                text_part("😀", None),
                text_part("z", None),
            ],
        };
        assert_eq!(body_parts(&body).0, json!([{"text": prefix}]));
    }

    #[test]
    fn mention_appearance_does_not_depend_on_the_notification_target() {
        let viewer = Uuid::new_v4();
        let mention = ChatPart {
            content: Some(person_mention(
                external_principal(ChatExternalIdentity::default(), None),
                vec![ChatTextRun {
                    text: "@Bobby".into(),
                    style: None,
                }],
            )),
        };
        let body = ChatBody {
            parts: vec![mention.clone()],
        };
        let projected = json!([{"text": "@Bobby", "mention": true}]);
        let (parts, mentions) = body_parts(&body);
        assert_eq!(parts, projected);
        assert!(!mentions.includes(&body, &viewer.to_string(), |_| None));
        assert!(mentions.includes(&body, &viewer.to_string(), |_| Some(viewer)));

        let clipped = ChatBody {
            parts: vec![text_part("x".repeat(15_998), None), mention],
        };
        let (parts, mentions) = body_parts(&clipped);
        assert_eq!(
            parts,
            json!([{"text": format!("{}@B", "x".repeat(15_998))}])
        );
        assert!(!mentions.includes(&clipped, &viewer.to_string(), |_| Some(viewer)));
    }

    #[test]
    fn exhausting_the_formatting_budget_does_not_drop_a_visible_notification() {
        let viewer = Uuid::new_v4();
        let style = Some(ChatTextStyle {
            bold: true,
            ..Default::default()
        });
        let mut parts = vec![text_part("x", style); 128];
        parts.push(ChatPart {
            content: Some(person_mention(
                external_principal(ChatExternalIdentity::default(), None),
                vec![ChatTextRun {
                    text: "@Alice".into(),
                    style: None,
                }],
            )),
        });
        let body = ChatBody { parts };
        let (content, mentions) = body_parts(&body);
        assert_eq!(
            content.as_array().unwrap().last(),
            Some(&json!({"text": "@Alice"}))
        );
        assert!(mentions.includes(&body, &viewer.to_string(), |_| Some(viewer)));
    }
}
