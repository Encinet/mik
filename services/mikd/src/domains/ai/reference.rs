use crate::http_egress;
use reqwest::Url;

pub(super) const CITATION_URL_LIMIT: usize = 512;

#[derive(Clone, PartialEq)]
pub(super) struct Reference {
    label: String,
    url: Option<String>,
}

impl Reference {
    pub(super) fn new(label: String, url: Option<&str>) -> Self {
        Self {
            label,
            url: url.and_then(canonical_url).map(String::from),
        }
    }

    pub(super) fn label(&self) -> &str {
        &self.label
    }

    pub(super) fn url(&self) -> Option<&str> {
        self.url.as_deref()
    }
}

pub(super) fn canonical_url(raw: &str) -> Option<Url> {
    let authority = raw
        .strip_prefix("https://")?
        .split(['/', '?', '#'])
        .next()?;
    if raw.len() > CITATION_URL_LIMIT
        || authority.is_empty()
        || authority.contains(['@', ':'])
        || raw.contains('\\')
        || raw
            .chars()
            .any(|character| character.is_control() || character.is_whitespace())
    {
        return None;
    }
    let url = Url::parse(raw).ok()?;
    let url = uri_spelling(&url)?;
    let url::Host::Domain(host) = url.host()? else {
        return None;
    };
    let (_, suffix) = host.rsplit_once('.')?;
    (url.as_str().len() <= CITATION_URL_LIMIT
        && http_egress::allowed_url(&url, false)
        && suffix.len() >= 2
        && (suffix.starts_with("xn--") || suffix.bytes().all(|byte| byte.is_ascii_lowercase()))
        && host.split('.').all(|part| {
            !part.is_empty()
                && part.len() <= 63
                && !part.starts_with('-')
                && !part.ends_with('-')
                && part
                    .bytes()
                    .all(|byte| byte.is_ascii_alphanumeric() || byte == b'-')
        }))
    .then_some(url)
}

fn uri_spelling(url: &Url) -> Option<Url> {
    use std::fmt::Write as _;

    let mut canonical = String::with_capacity(url.as_str().len());
    let bytes = url.as_str().as_bytes();
    let mut fragment = false;
    for (index, &byte) in bytes.iter().enumerate() {
        let keep = match byte {
            b'#' => !std::mem::replace(&mut fragment, true),
            b'%' => bytes
                .get(index + 1..index + 3)
                .is_some_and(|hex| hex.iter().all(u8::is_ascii_hexdigit)),
            byte => byte.is_ascii_alphanumeric() || b"-._~:/?@!$&'()*+,;=".contains(&byte),
        };
        if keep {
            canonical.push(char::from(byte));
        } else {
            write!(canonical, "%{byte:02X}").ok()?;
        }
    }
    Url::parse(&canonical).ok()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn verified_addresses_have_one_spelling_for_web_and_game_clients() {
        let url = canonical_url("https://example.org/[bridge]?view=[east]#floor#2").unwrap();
        assert_eq!(
            url.as_str(),
            "https://example.org/%5Bbridge%5D?view=%5Beast%5D#floor%232"
        );
        assert_eq!(canonical_url(url.as_str()).unwrap(), url);
        assert!(canonical_url("https://name:password@example.org/").is_none());
        assert!(canonical_url("https://example.internal/").is_none());
    }
}
