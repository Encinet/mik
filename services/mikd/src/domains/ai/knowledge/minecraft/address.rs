use super::{WikiEdition, edition};
use crate::domains::ai::reference::CITATION_URL_LIMIT;
use percent_encoding::{AsciiSet, NON_ALPHANUMERIC, percent_decode_str, utf8_percent_encode};
use reqwest::Url;
use serde::Serialize;
use unicode_normalization::UnicodeNormalization;

const INPUT_URL_LIMIT: usize = 2048;
const FRAGMENT_ESCAPE: &AsciiSet = &NON_ALPHANUMERIC
    .remove(b'-')
    .remove(b'_')
    .remove(b'.')
    .remove(b'~');

const VIRTUAL_NAMESPACES: &str = include_str!(concat!(
    env!("CARGO_MANIFEST_DIR"),
    "/../../src/main/resources/org/encinet/mik/feature/community/ai/minecraft-wiki-virtual-namespaces.txt"
));

#[derive(Clone, Debug, Eq, PartialEq, Serialize)]
pub(in crate::domains::ai) struct Address {
    pub(super) edition: WikiEdition,
    pub(super) title: String,
    pub(super) section: Option<String>,
}

impl Address {
    pub(in crate::domains::ai) fn parse(input: &str) -> Option<Self> {
        if input.len() > INPUT_URL_LIMIT
            || input.contains('\\')
            || input.chars().any(char::is_control)
        {
            return None;
        }
        // Check the raw authority/path before any URL normalization. The input is never fetched.
        let address = input.strip_prefix("https://")?;
        let (host, path) = address.split_at(address.find('/')?);
        let (edition, article) = edition::match_article(host, path)?;
        let (slug, section) = match article.split_once('#') {
            Some((slug, "")) => (slug, None),
            Some((slug, fragment)) => (slug, Some(decode_fragment(fragment)?)),
            None => (article, None),
        };
        let title = percent_decode_str(article_slug(edition, slug)?)
            .decode_utf8()
            .ok()?
            .replace('_', " ");
        valid_title(&title).then_some(Self {
            edition,
            title,
            section,
        })
    }
}

// zh's published language selector uses ?variant=, not a variant article path. This
// is presentation metadata, not an edition or a parameter to send upstream.
fn article_slug(edition: WikiEdition, slug: &str) -> Option<&str> {
    let Some((title, query)) = slug.split_once('?') else {
        return Some(slug);
    };
    (edition == WikiEdition::CHINESE
        && matches!(
            query,
            "variant=zh"
                | "variant=zh-hans"
                | "variant=zh-hant"
                | "variant=zh-cn"
                | "variant=zh-tw"
                | "variant=zh-hk"
        ))
    .then_some(title)
}

pub(in crate::domains::ai::knowledge) fn is_article_citation(raw: &str) -> bool {
    let Some(address) = Address::parse(raw) else {
        return false;
    };
    article(address.edition, &address.title, address.section.as_deref())
        .is_some_and(|canonical| canonical.as_str() == raw)
}

pub(super) fn normalize_fragment(fragment: &str) -> Option<String> {
    (!fragment.is_empty()
        && fragment.len() <= 384
        && !fragment.contains(['#', '\\'])
        && !fragment.chars().any(char::is_control))
    .then(|| fragment.replace(' ', "_"))
}

pub(super) fn decode_fragment(fragment: &str) -> Option<String> {
    normalize_fragment(&percent_decode_str(fragment).decode_utf8().ok()?)
}

pub(super) fn article(edition: WikiEdition, title: &str, fragment: Option<&str>) -> Option<Url> {
    if !valid_title(title) {
        return None;
    }
    let route = edition.site()?.canonical;
    let mut url = Url::parse(&format!("https://{}", route.host)).ok()?;
    url.path_segments_mut()
        .ok()?
        .clear()
        .extend(route.article_prefix.trim_matches('/').split('/'))
        .extend(title.replace(' ', "_").split('/'));
    if let Some(fragment) = fragment {
        let fragment = normalize_fragment(fragment)?;
        let encoded = utf8_percent_encode(&fragment, FRAGMENT_ESCAPE).to_string();
        url.set_fragment(Some(&encoded));
    }
    (url.as_str().len() <= CITATION_URL_LIMIT).then_some(url)
}

fn valid_title(title: &str) -> bool {
    title.len() <= 255
        && !title.contains(['#', '<', '>', '[', ']', '{', '}', '|', '\\'])
        && !title.chars().any(|ch| {
            ch.is_control() || matches!(ch, '\u{200e}' | '\u{200f}' | '\u{202a}'..='\u{202e}')
        })
        && !title.as_bytes().windows(3).any(|part| {
            part[0] == b'%' && part[1].is_ascii_hexdigit() && part[2].is_ascii_hexdigit()
        })
        && title
            .split('/')
            .all(|segment| !matches!(segment.trim_matches([' ', '_']), "" | "." | ".."))
        && !title.split('/').next().is_some_and(|prefix| {
            ["api.php", "index.php", "load.php", "rest.php"]
                .iter()
                .any(|script| prefix.trim_matches([' ', '_']).eq_ignore_ascii_case(script))
        })
        && !title.split_once(':').is_some_and(|(namespace, _)| {
            let namespace = namespace.trim_matches(|ch: char| ch == '_' || ch.is_whitespace());
            let normalized = namespace.to_lowercase().nfc().collect::<String>();
            namespace.is_empty()
                || VIRTUAL_NAMESPACES
                    .lines()
                    .any(|name| !name.starts_with('#') && name == normalized)
        })
}
