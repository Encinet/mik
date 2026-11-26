use linkify::{LinkFinder, LinkKind};
use matrix_sdk::ruma::{MatrixToUri, MatrixUri, OwnedRoomAliasId, OwnedRoomId, OwnedUserId};
use mik_protocol::wire::ChatTextRun;
use regex::Regex;
use serde::Deserialize;
use std::{ops::Range, sync::LazyLock};
use url::Url;

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct RuleFile {
    absolute_links: String,
    tracking_parameters: Vec<String>,
    short_links: String,
    sites: Vec<SiteRule>,
    short_targets: Vec<ShortRule>,
}

#[derive(Deserialize)]
struct SiteRule {
    resource: String,
    host: String,
    name: String,
    paths: Vec<PathSpec>,
    #[serde(default, rename = "titleQuery")]
    title_query: bool,
}

#[derive(Deserialize)]
struct PathSpec {
    pattern: String,
    label: String,
    target: Option<String>,
}

struct PathRule {
    pattern: Regex,
    label: String,
    target: Option<String>,
}

#[derive(Deserialize)]
struct ShortRule {
    pattern: String,
    target: String,
}

struct Site {
    resource: String,
    host: Regex,
    name: String,
    paths: Vec<PathRule>,
    title_query: bool,
}

struct Rules {
    sites: Vec<Site>,
    absolute: Regex,
    mail_address: Regex,
    tracking: Vec<String>,
    bare: Regex,
    short: Regex,
    targets: Vec<(Regex, String)>,
}

#[expect(
    clippy::expect_used,
    reason = "Embedded repository rules are covered by shared semantic fixtures"
)]
static RULES: LazyLock<Rules> = LazyLock::new(|| {
    let file: RuleFile = serde_json::from_str(include_str!(concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/../../shared/chat/link-rules.json",
    )))
    .expect("chat link rule file");
    let compile = |pattern: &str| Regex::new(pattern).expect("chat link rule pattern");
    let hosts = file
        .sites
        .iter()
        .map(|site| site.host.as_str())
        .collect::<Vec<_>>()
        .join("|");
    Rules {
        absolute: compile(&file.absolute_links),
        mail_address: compile(
            r"(?i-u)^[a-z0-9!#$&'*+/=?^_`{|}~.-]+@[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?$",
        ),
        tracking: file.tracking_parameters,
        bare: compile(&format!(
            r#"(?i:(?:{hosts}))(?::[0-9]{{1,5}})?(?:[/?#][^\s<>"，。！？、；：]*)?"#
        )),
        short: compile(&file.short_links),
        sites: file
            .sites
            .into_iter()
            .map(|site| Site {
                resource: site.resource,
                host: compile(&format!("^(?:{})$", site.host)),
                name: site.name,
                paths: site
                    .paths
                    .into_iter()
                    .map(|path| PathRule {
                        pattern: compile(&path.pattern),
                        label: path.label,
                        target: path.target,
                    })
                    .collect(),
                title_query: site.title_query,
            })
            .collect(),
        targets: file
            .short_targets
            .into_iter()
            .map(|rule| (compile(&rule.pattern), rule.target))
            .collect(),
    }
});

pub(super) struct LinkMatch {
    pub(super) range: Range<usize>,
    pub(super) uri: Url,
    pub(super) label: String,
}

pub(super) fn find(text: &str) -> impl Iterator<Item = LinkMatch> {
    let absolute = RULES.absolute.find_iter(text).filter_map(|matched| {
        let start = matched.start();
        if inside_url(text, start)
            || text[..start].ends_with(|ch: char| ch.is_ascii_alphanumeric() || "+.-".contains(ch))
        {
            return None;
        }
        let end = start + visible_end(matched.as_str());
        let clean = without_tracking(&text[start..end]);
        let url = http_destination(&clean)?;
        Some((start..end, url, clean))
    });
    let mut finder = LinkFinder::new();
    finder.kinds(&[LinkKind::Email]);
    let emails = finder.links(text).filter_map(|link| {
        let start = link.start();
        if inside_url(text, start) {
            return None;
        }
        let end = start + visible_end(link.as_str());
        let token = &text[start..end];
        let address = token.replace('#', "%23").replace('?', "%3F");
        let url = link_destination(&format!("mailto:{address}"))?;
        let start = text[..start]
            .strip_suffix("mailto:")
            .map_or(start, str::len);
        Some((start..end, url, std::borrow::Cow::Borrowed(token)))
    });
    let known = RULES.bare.find_iter(text).filter_map(|matched| {
        let end = matched.start() + visible_end(matched.as_str());
        let token = &text[matched.start()..end];
        if !boundary(text, matched.start(), end, token, true) {
            return None;
        }
        let clean = without_tracking(token);
        let url = http_destination(&format!("https://{clean}"))?;
        site(&url)?;
        Some((matched.start()..end, url, clean))
    });
    let short = RULES.short.find_iter(text).filter_map(|matched| {
        let end = matched.start() + visible_end(matched.as_str());
        let token = &text[matched.start()..end];
        boundary(text, matched.start(), end, token, false)
            .then(|| short_destination(token))
            .flatten()
            .map(|url| (matched.start()..end, url, std::borrow::Cow::Borrowed(token)))
    });
    let mut candidates: Vec<_> = absolute.chain(emails).chain(known).chain(short).collect();
    candidates.sort_by_key(|(span, _, _)| (span.start, std::cmp::Reverse(span.end)));
    let mut head = 0;
    candidates
        .into_iter()
        .filter_map(move |(range, url, display)| {
            if range.start < head {
                return None;
            }
            head = range.end;
            let (label, uri) = match site(&url) {
                Some(site) => (site.label(&url), site.target(url)),
                None if url.scheme() == "mailto" => (format!("Email: {display}"), url),
                None => (display.into_owned(), url),
            };
            Some(LinkMatch { range, uri, label })
        })
}

fn boundary(text: &str, start: usize, end: usize, token: &str, host: bool) -> bool {
    let joined = |ch: char| ch.is_ascii_alphanumeric() || "_/-@#!:".contains(ch);
    let after = &text[end..];
    !inside_url(text, start)
        && !text[..start].ends_with(|ch| joined(ch) || host && ch == '.')
        && (host && token.contains(['/', '?', '#']) || !after.starts_with(joined))
        && (!host
            || !after
                .strip_prefix('.')
                .is_some_and(|tail| tail.starts_with(|c: char| c.is_alphanumeric())))
}

fn without_tracking(raw: &str) -> std::borrow::Cow<'_, str> {
    use std::borrow::Cow;
    let (base, fragment) = raw
        .split_once('#')
        .map_or((raw, ""), |(url, _)| (url, &raw[url.len()..]));
    let Some((prefix, query)) = base.split_once('?') else {
        return Cow::Borrowed(raw);
    };
    let mut removed = false;
    let params = query
        .split('&')
        .filter(|param| {
            let name = param.split_once('=').map_or(*param, |(name, _)| name);
            let drop = RULES
                .tracking
                .iter()
                .any(|key| name.eq_ignore_ascii_case(key));
            removed |= drop;
            !drop && !param.is_empty()
        })
        .collect::<Vec<_>>();
    if !removed {
        Cow::Borrowed(raw)
    } else if params.is_empty() {
        Cow::Owned(format!("{prefix}{fragment}"))
    } else {
        Cow::Owned(format!("{prefix}?{}{fragment}", params.join("&")))
    }
}

pub(super) fn inside_url(text: &str, start: usize) -> bool {
    let prefix = text[..start]
        .rsplit(|ch: char| ch.is_whitespace() || "（【《「『，。！？、；：<>\"".contains(ch))
        .next()
        .unwrap_or_default();
    prefix.contains("://")
}

fn visible_end(text: &str) -> usize {
    let text = text
        .split(['，', '。', '！', '？', '、', '；', '：'])
        .next()
        .unwrap_or(text);
    let mut end = text.len();
    while let Some(last) = text[..end].chars().next_back() {
        let open = match last {
            ')' => Some('('),
            ']' => Some('['),
            '}' => Some('{'),
            '）' => Some('（'),
            '】' => Some('【'),
            '》' => Some('《'),
            '」' => Some('「'),
            '』' => Some('『'),
            _ => None,
        };
        if !".,!?:;'\"…".contains(last)
            && !open.is_some_and(|open| {
                text[..end].matches(last).count() > text[..end].matches(open).count()
            })
        {
            break;
        }
        end -= last.len_utf8();
    }
    end
}

fn short_destination(token: &str) -> Option<Url> {
    for (pattern, target) in &RULES.targets {
        if pattern.is_match(token) {
            return http_destination(&pattern.replace(token, target.as_str()));
        }
    }
    let permalink = if let Some(scheme) = token
        .get(..7)
        .filter(|part| part.eq_ignore_ascii_case("matrix:"))
    {
        let uri = format!("matrix:{}", &token[scheme.len()..]);
        MatrixToUri::from(MatrixUri::parse(&uri).ok()?)
    } else {
        match token.as_bytes().first()? {
            b'@' => token.parse::<OwnedUserId>().ok()?.matrix_to_uri(),
            b'#' => token.parse::<OwnedRoomAliasId>().ok()?.matrix_to_uri(),
            b'!' => token.parse::<OwnedRoomId>().ok()?.matrix_to_uri(),
            _ => return None,
        }
    };
    http_destination(&permalink.to_string())
}

pub(crate) fn label_text(runs: &[ChatTextRun]) -> String {
    runs.iter().map(|run| run.text.as_str()).collect()
}

fn http_destination(raw: &str) -> Option<Url> {
    link_destination(raw).filter(is_http_destination)
}

pub(crate) fn link_destination(raw: &str) -> Option<Url> {
    if raw.len() > 2048
        || raw
            .chars()
            .any(|c| c.is_control() || c.is_whitespace() || c == '\\')
    {
        return None;
    }
    if raw.contains(['{', '}', '|'])
        || raw.as_bytes().iter().enumerate().any(|(i, byte)| {
            *byte == b'%'
                && !raw
                    .as_bytes()
                    .get(i + 1..i + 3)
                    .is_some_and(|pair| pair.iter().all(u8::is_ascii_hexdigit))
        })
    {
        return None;
    }
    let (base, fragment) = raw
        .split_once('#')
        .map_or((raw, None), |(base, fragment)| (base, Some(fragment)));
    let mut url = Url::parse(base).ok()?;
    if let Some(fragment) = fragment {
        url.set_fragment(Some(&fragment.replace('#', "%23")));
    }
    if matches!(url.scheme(), "http" | "https")
        && !raw.split_once("://").is_some_and(|(_, rest)| {
            !rest.starts_with(['/', '?', '#'])
                && !rest.is_empty()
                && !rest
                    .split(['/', '?', '#'])
                    .next()
                    .unwrap_or_default()
                    .contains('@')
        })
    {
        return None;
    }
    (is_http_destination(&url)
        || url.scheme() == "mailto"
            && url.cannot_be_a_base()
            && RULES.mail_address.is_match(&decode(url.path()))
            && url.query().is_none()
            && url.fragment().is_none())
    .then_some(url)
    .filter(|url| url.as_str().len() <= 2048)
}

fn is_http_destination(url: &Url) -> bool {
    matches!(url.scheme(), "http" | "https")
        && url.has_host()
        && url.username().is_empty()
        && url.password().is_none()
}

fn site(url: &Url) -> Option<&'static Site> {
    let host = url.host_str()?.trim_end_matches('.');
    RULES.sites.iter().find(|site| site.host.is_match(host))
}

fn decode(raw: &str) -> String {
    percent_encoding::percent_decode_str(raw)
        .decode_utf8()
        .ok()
        .filter(|value| !value.chars().any(char::is_control))
        .map_or_else(String::new, std::borrow::Cow::into_owned)
}

impl Site {
    fn target(&self, original: Url) -> Url {
        self.paths
            .iter()
            .find_map(|path| {
                let template = path.target.as_deref()?;
                if !path.pattern.is_match(original.path()) {
                    return None;
                }
                let replaced = path.pattern.replace(original.path(), template);
                let mut target = http_destination(&replaced)?;
                target.set_query(original.query());
                target.set_fragment(original.fragment());
                (target.as_str().len() <= 2048).then_some(target)
            })
            .unwrap_or(original)
    }

    fn label(&self, url: &Url) -> String {
        let mut detail = if self.resource == "MATRIX" {
            url.fragment()
                .and_then(|fragment| fragment.strip_prefix('/'))
                .map(|fragment| decode(fragment.split_once('?').map_or(fragment, |(id, _)| id)))
                .unwrap_or_default()
        } else {
            self.paths
                .iter()
                .find_map(|path| {
                    path.pattern
                        .is_match(url.path())
                        .then(|| decode(&path.pattern.replace(url.path(), path.label.as_str())))
                })
                .unwrap_or_default()
        };
        if self.title_query {
            if detail.trim().is_empty() || detail.eq_ignore_ascii_case("index.php") {
                detail = url
                    .query_pairs()
                    .find(|(name, _)| name.eq_ignore_ascii_case("title"))
                    .map(|(_, title)| title.into_owned())
                    .unwrap_or_default();
            }
            detail = detail.replace('_', " ");
        }
        if detail.trim().is_empty() || detail.chars().any(char::is_control) {
            self.name.clone()
        } else {
            format!("{}: {detail}", self.name)
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::domains::chat::body::{BodyInput, assemble};
    use mik_protocol::wire::chat_part::Content;

    #[derive(Deserialize)]
    struct Case {
        text: String,
        label: Option<String>,
        target: Option<String>,
        resource: Option<String>,
    }

    #[test]
    fn validates_native_link_destinations_before_publication() {
        for invalid in [
            "https://@example.org",
            "https://example.org/%GG",
            "mailto:reader",
            "mailto:reader%0D%0A@example.org",
            "mailto:reader@example.org?subject=unexpected",
            "mailto:first@example.org,second@example.org",
        ] {
            assert!(link_destination(invalid).is_none(), "{invalid}");
        }
        assert_eq!(
            link_destination("mailto:reader+chat@example.org")
                .unwrap()
                .as_str(),
            "mailto:reader+chat@example.org"
        );
    }

    #[test]
    fn shared_link_rules() {
        let cases: Vec<Case> = serde_json::from_str(include_str!(concat!(
            env!("CARGO_MANIFEST_DIR"),
            "/../../shared/chat/link-cases.json",
        )))
        .unwrap();
        for case in cases {
            let body = assemble(
                [BodyInput::Text(ChatTextRun {
                    text: case.text.clone(),
                    style: None,
                })],
                None,
            )
            .unwrap();
            let links: Vec<_> = body
                .parts
                .iter()
                .filter_map(|part| match &part.content {
                    Some(Content::Reference(reference)) => {
                        Some((reference, super::super::model::link_target(reference)?))
                    }
                    _ => None,
                })
                .collect();
            let Some(target) = case.target else {
                assert!(links.is_empty(), "{}", case.text);
                continue;
            };
            assert_eq!(links.len(), 1, "{}", case.text);
            let (reference, link) = links[0];
            assert_eq!(
                label_text(&reference.runs),
                case.label.unwrap(),
                "{}",
                case.text
            );
            assert_eq!(link.uri, target, "{}", case.text);
            assert_eq!(
                site(&Url::parse(&link.uri).unwrap())
                    .map_or("GENERIC", |site| site.resource.as_str()),
                case.resource.unwrap(),
                "{}",
                case.text
            );
        }
    }
}
