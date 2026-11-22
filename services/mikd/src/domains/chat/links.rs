use linkify::{LinkFinder, LinkKind};
use matrix_sdk::ruma::{MatrixToUri, MatrixUri, OwnedRoomAliasId, OwnedRoomId, OwnedUserId};
use mik_protocol::wire::{
    ChatBody, ChatLink, ChatPart, ChatTextStyle, chat_link::Resource, chat_part::Content,
};
use regex::Regex;
use std::sync::LazyLock;
use url::Url;

#[expect(
    clippy::expect_used,
    reason = "The regex is a source literal covered by link parsing tests"
)]
static SHORT_LINKS: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r#"(?i-u:\b(?:bv[a-z0-9]{10}|av[0-9]+|mc-[0-9]+)\b)|\bmatrix:[^\s<>"，。！？、]+|(?i-u:[@#!][a-z0-9._=+/\-]+:(?:[a-z0-9][a-z0-9.\-]*|\[[0-9a-f:.]+\])(?::[0-9]{1,5})?)"#)
        .expect("chat resource short links")
});

pub(super) fn normalize(body: &mut ChatBody) {
    let mut finder = LinkFinder::new();
    finder
        .kinds(&[LinkKind::Url, LinkKind::Email])
        .url_must_have_scheme(false);
    body.parts = std::mem::take(&mut body.parts)
        .into_iter()
        .flat_map(|mut part| {
            if let Some(Content::Link(link)) = &mut part.content {
                if let Ok(url) = Url::parse(&link.url) {
                    link.resource = resource(&url).into();
                }
                return vec![part];
            }
            let Some(Content::Text(text)) = &part.content else {
                return vec![part];
            };
            let mut parts = Vec::new();
            let mut previous = 0;
            for link in finder.links(text) {
                let label = link.as_str();
                let destination = match link.kind() {
                    LinkKind::Url => destination(label),
                    LinkKind::Email => link_destination(&format!("mailto:{label}")),
                    _ => None,
                };
                let Some(url) = destination else {
                    continue;
                };
                append_short_links(&mut parts, part.style, &text[previous..link.start()]);
                parts.push(link_part(part.style, label, url));
                previous = link.end();
            }
            append_short_links(&mut parts, part.style, &text[previous..]);
            parts
        })
        .collect();
}

fn append_short_links(parts: &mut Vec<ChatPart>, style: Option<ChatTextStyle>, text: &str) {
    let mut previous = 0;
    for matched in SHORT_LINKS.find_iter(text) {
        let label = matched.as_str();
        let (start, end) = if label.starts_with("matrix:") || label.starts_with(['@', '#', '!']) {
            if text[..matched.start()]
                .ends_with(|c: char| c.is_ascii_alphanumeric() || "/@:#.!_$".contains(c))
            {
                continue;
            }
            (
                matched.start(),
                matched.start() + label.trim_end_matches(['.', ',', ')', ']']).len(),
            )
        } else {
            let joined = |character: char| matches!(character, '/' | '@' | '-');
            if text[..matched.start()].ends_with(joined)
                || text[matched.end()..].starts_with(joined)
            {
                continue;
            }
            (matched.start(), matched.end())
        };
        let label = &text[start..end];
        if let Some(url) = short_destination(label) {
            append_text(parts, style, &text[previous..start]);
            parts.push(link_part(style, label, url));
            previous = end;
        }
    }
    append_text(parts, style, &text[previous..]);
}

fn short_destination(label: &str) -> Option<Url> {
    if label.starts_with("matrix:") {
        let permalink = MatrixToUri::from(MatrixUri::parse(label).ok()?);
        return http_destination(&permalink.to_string());
    }
    let permalink = match label.as_bytes().first()? {
        b'@' => Some(label.parse::<OwnedUserId>().ok()?.matrix_to_uri()),
        b'#' => Some(label.parse::<OwnedRoomAliasId>().ok()?.matrix_to_uri()),
        b'!' => Some(label.parse::<OwnedRoomId>().ok()?.matrix_to_uri()),
        _ => None,
    };
    if let Some(permalink) = permalink {
        return http_destination(&permalink.to_string());
    }
    let prefix = &label[..2];
    if prefix.eq_ignore_ascii_case("mc") {
        http_destination(&format!("https://mojira.dev/MC{}", &label[2..]))
    } else {
        let prefix = if prefix.eq_ignore_ascii_case("bv") {
            "BV"
        } else {
            "av"
        };
        http_destination(&format!(
            "https://www.bilibili.com/video/{prefix}{}",
            &label[2..]
        ))
    }
}

fn append_text(parts: &mut Vec<ChatPart>, style: Option<ChatTextStyle>, text: &str) {
    if !text.is_empty() {
        parts.push(ChatPart {
            style,
            content: Some(Content::Text(text.into())),
        });
    }
}

fn link_part(style: Option<ChatTextStyle>, label: &str, url: Url) -> ChatPart {
    ChatPart {
        style,
        content: Some(Content::Link(ChatLink {
            label: label.into(),
            resource: resource(&url).into(),
            url: url.into(),
        })),
    }
}

fn destination(label: &str) -> Option<Url> {
    let explicit = label.contains("://");
    let url = if explicit {
        http_destination(label)?
    } else {
        http_destination(&format!("https://{label}"))?
    };
    (explicit || resource(&url) != Resource::Unspecified).then_some(url)
}

fn http_destination(raw: &str) -> Option<Url> {
    Url::parse(raw).ok().filter(is_http_destination)
}

pub(crate) fn link_destination(raw: &str) -> Option<Url> {
    let url = Url::parse(raw).ok()?;
    (is_http_destination(&url)
        || url.scheme() == "mailto"
            && url.cannot_be_a_base()
            && !url.path().is_empty()
            && url.query().is_none()
            && url.fragment().is_none())
    .then_some(url)
}

fn is_http_destination(url: &Url) -> bool {
    matches!(url.scheme(), "http" | "https")
        && url.has_host()
        && url.username().is_empty()
        && url.password().is_none()
}

fn resource(url: &Url) -> Resource {
    match url.host_str().unwrap_or_default().trim_end_matches('.') {
        "bilibili.com" | "www.bilibili.com" | "m.bilibili.com" | "b23.tv" => Resource::Bilibili,
        "github.com" | "gist.github.com" | "www.github.com" => Resource::Github,
        "minecraft.wiki" => Resource::MinecraftWiki,
        "bugs.mojang.com"
        | "bugs-legacy.mojang.com"
        | "report.bugs.mojang.com"
        | "mojira.atlassian.net"
        | "mojira.dev" => Resource::Mojira,
        "matrix.to" => Resource::Matrix,
        host if host.ends_with(".github.io") => Resource::Github,
        host if host.ends_with(".minecraft.wiki") => Resource::MinecraftWiki,
        _ => Resource::Unspecified,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn normalizes_short_links_without_rewriting_user_text_or_nested_tokens() {
        let text = "看bv1xx411c7mu、av123、mc-4、@alice:example.org、matrix:u/alice:example.org。 https://example.org/MC-5 xMC-6";
        let style = Some(ChatTextStyle {
            bold: true,
            ..Default::default()
        });
        let mut normalized = ChatBody {
            parts: vec![ChatPart {
                style,
                content: Some(Content::Text(text.into())),
            }],
        };

        normalize(&mut normalized);

        let mut body = String::new();
        let mut links = Vec::new();
        for part in &normalized.parts {
            assert_eq!(part.style, style);
            match part.content.as_ref().unwrap() {
                Content::Text(value) => body.push_str(value),
                Content::Link(link) => {
                    body.push_str(&link.label);
                    links.push((link.url.as_str(), link.resource()));
                }
                _ => panic!("unexpected chat node"),
            }
        }
        assert_eq!(body, text);
        assert_eq!(
            links,
            [
                (
                    "https://www.bilibili.com/video/BV1xx411c7mu",
                    Resource::Bilibili
                ),
                ("https://www.bilibili.com/video/av123", Resource::Bilibili),
                ("https://mojira.dev/MC-4", Resource::Mojira),
                ("https://matrix.to/#/@alice:example.org", Resource::Matrix),
                ("https://matrix.to/#/@alice:example.org", Resource::Matrix),
                ("https://example.org/MC-5", Resource::Unspecified),
            ]
        );
    }
}
