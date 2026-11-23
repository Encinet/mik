//! The fixed independent Minecraft Wiki network. Shared with Paper's citation allowlist.
use serde::{Deserialize, Deserializer, Serialize, Serializer, de};
use std::{collections::HashSet, io, sync::LazyLock};

const REGISTRY: &str = include_str!(concat!(
    env!("CARGO_MANIFEST_DIR"),
    "/../../src/main/resources/org/encinet/mik/feature/community/ai/minecraft-wiki-editions.tsv"
));
static SITES: LazyLock<Result<Vec<Site>, &'static str>> = LazyLock::new(load);

/// An edition code from the network registry, not a player locale or an arbitrary host.
#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub(in crate::domains::ai) struct WikiEdition(&'static str);

impl WikiEdition {
    pub(super) const ENGLISH: Self = Self("en");
    pub(super) const CHINESE: Self = Self("zh");
    const CLASSICAL_CHINESE: Self = Self("lzh");

    /// Parses exactly the canonical code used by the tool schema and on the wire.
    pub(in crate::domains::ai) fn parse(code: &str) -> Option<Self> {
        Self::all().find(|edition| edition.code() == code)
    }

    /// Maps regional player locales to their edition, with English for unsupported languages.
    pub(in crate::domains::ai) fn from_locale(locale: &str) -> Self {
        let language = locale.split(['-', '_']).next().unwrap_or_default();
        // Some Java locales still use the former ISO code for Hebrew.
        let code = if language.eq_ignore_ascii_case("iw") {
            "he"
        } else {
            language
        };
        Self::all()
            .find(|edition| edition.code().eq_ignore_ascii_case(code))
            .unwrap_or(Self::ENGLISH)
    }

    pub(in crate::domains::ai) fn codes() -> impl ExactSizeIterator<Item = &'static str> {
        Self::all().map(Self::code)
    }

    pub(in crate::domains::ai) const fn code(self) -> &'static str {
        self.0
    }

    pub(super) fn all() -> impl ExactSizeIterator<Item = Self> {
        sites().iter().map(|site| site.edition)
    }

    pub(super) fn fallbacks(self) -> impl Iterator<Item = Self> {
        [
            Some(self),
            (self == Self::CLASSICAL_CHINESE).then_some(Self::CHINESE),
            (self != Self::ENGLISH).then_some(Self::ENGLISH),
        ]
        .into_iter()
        .flatten()
    }

    pub(super) fn site(self) -> Option<&'static Site> {
        sites().iter().find(|site| site.edition == self)
    }
}

impl Serialize for WikiEdition {
    fn serialize<S: Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        serializer.serialize_str(self.code())
    }
}

impl<'de> Deserialize<'de> for WikiEdition {
    fn deserialize<D: Deserializer<'de>>(deserializer: D) -> Result<Self, D::Error> {
        let code = String::deserialize(deserializer)?;
        Self::parse(&code).ok_or_else(|| de::Error::custom("unsupported Minecraft Wiki edition"))
    }
}

#[derive(Clone, Copy, Eq, Hash, PartialEq)]
pub(super) struct Route {
    pub(super) host: &'static str,
    pub(super) article_prefix: &'static str,
}

pub(super) struct Site {
    pub(super) edition: WikiEdition,
    pub(super) language: &'static str,
    pub(super) canonical: Route,
    pub(super) api_path: &'static str,
    aliases: Vec<Route>,
}

impl Site {
    fn routes(&self) -> impl Iterator<Item = Route> + '_ {
        std::iter::once(self.canonical).chain(self.aliases.iter().copied())
    }
}

pub(super) fn registered() -> io::Result<&'static [Site]> {
    SITES
        .as_deref()
        .map_err(|message| io::Error::other(*message))
}

pub(super) fn match_article<'a>(host: &str, path: &'a str) -> Option<(WikiEdition, &'a str)> {
    sites().iter().find_map(|site| {
        let title = site
            .routes()
            .filter(|route| route.host == host)
            .find_map(|route| path.strip_prefix(route.article_prefix))?;
        Some((site.edition, title))
    })
}

fn sites() -> &'static [Site] {
    SITES.as_deref().unwrap_or_default()
}

fn load() -> Result<Vec<Site>, &'static str> {
    let mut sites = Vec::new();
    let mut codes = HashSet::new();
    let mut routes = HashSet::new();
    for line in REGISTRY.lines().map(str::trim) {
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        let [code, host, article_prefix, api_path, aliases, language] = line
            .splitn(6, '\t')
            .collect::<Vec<_>>()
            .try_into()
            .map_err(|_| "invalid Minecraft Wiki registry columns")?;
        if !(2..=3).contains(&code.len())
            || !code.bytes().all(|byte| byte.is_ascii_lowercase())
            || !codes.insert(code)
            || language.is_empty()
            || language.chars().any(char::is_control)
            || !valid_path(api_path)
        {
            return Err("invalid Minecraft Wiki registry edition");
        }
        let canonical = route(host, article_prefix)?;
        let aliases = if aliases == "-" {
            Vec::new()
        } else {
            aliases
                .split(',')
                .map(|alias| {
                    let (host, prefix) = alias
                        .split_once(':')
                        .ok_or("invalid Minecraft Wiki registry alias")?;
                    route(host, prefix)
                })
                .collect::<Result<_, _>>()?
        };
        let site = Site {
            edition: WikiEdition(code),
            language,
            canonical,
            api_path,
            aliases,
        };
        if site.routes().any(|route| !routes.insert(route)) {
            return Err("duplicate Minecraft Wiki registry route");
        }
        sites.push(site);
    }
    if ![
        WikiEdition::ENGLISH,
        WikiEdition::CHINESE,
        WikiEdition::CLASSICAL_CHINESE,
    ]
    .into_iter()
    .all(|edition| codes.contains(edition.code()))
    {
        return Err("missing Minecraft Wiki fallback editions");
    }
    Ok(sites)
}

fn route(host: &'static str, article_prefix: &'static str) -> Result<Route, &'static str> {
    let valid_host = host.contains('.')
        && host.split('.').all(|label| {
            !label.is_empty()
                && !label.starts_with('-')
                && !label.ends_with('-')
                && label
                    .bytes()
                    .all(|byte| byte.is_ascii_lowercase() || byte.is_ascii_digit() || byte == b'-')
        });
    if !valid_host || !article_prefix.strip_suffix('/').is_some_and(valid_path) {
        return Err("invalid Minecraft Wiki registry article route");
    }
    Ok(Route {
        host,
        article_prefix,
    })
}

fn valid_path(path: &str) -> bool {
    path.strip_prefix('/').is_some_and(|path| {
        path.split('/').all(|part| {
            !matches!(part, "" | "." | "..")
                && part
                    .bytes()
                    .all(|byte| byte.is_ascii_alphanumeric() || b"._-".contains(&byte))
        })
    })
}
