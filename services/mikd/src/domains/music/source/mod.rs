mod discovery_http;
mod lx;
mod metadata;
mod subscriptions;

use super::{
    SourceImport, SourceRefresh, SourceState, http,
    identity::IdentityStore,
    lyrics::LyricsText,
    track::{ResolverInput, TrackRecord, TrackRef, label},
};
use discovery_http::DiscoveryClient;
use futures_util::{StreamExt, stream};
use lx::Script;
use serde::Serialize;
use serde_json::Value;
use std::{
    collections::{BTreeMap, HashSet},
    io,
    path::PathBuf,
    sync::Arc,
    time::Duration,
};
use subscriptions::Sources;
use uuid::Uuid;

const SEARCH_CHANNEL_CONCURRENCY: usize = 4;
const SEARCH_PROVIDER_CONCURRENCY: usize = 2;
const CHANNELS: [&str; 5] = ["kw", "kg", "tx", "wy", "mg"];
const SEARCH_BUDGET: usize = 200;
const DISCOVERY_DEADLINE: Duration = Duration::from_secs(8);
const PROVIDER_SEARCH_DEADLINE: Duration = Duration::from_secs(3);

#[derive(Clone)]
pub(super) struct Source {
    registry: Arc<Sources>,
    discovery_http: Arc<DiscoveryClient>,
    identities: Arc<IdentityStore>,
}

pub(super) struct Resolution {
    pub url: url::Url,
    pub provider_id: String,
}

#[derive(Default)]
pub(super) struct Discovery {
    pub tracks: Vec<TrackRecord>,
    pub partial: bool,
}

pub(super) struct Collection {
    pub title: String,
    pub discovery: Discovery,
}

type Provider = (String, Arc<Script>);

#[derive(Serialize)]
struct SearchInput<'query> {
    keyword: &'query str,
    page: i32,
    limit: i32,
}

#[derive(Serialize)]
struct MediaInput<'track> {
    #[serde(rename = "musicInfo")]
    info: &'track Value,
    #[serde(rename = "type", skip_serializing_if = "Option::is_none")]
    quality: Option<&'track str>,
}

impl Source {
    pub async fn open(path: PathBuf, identities: Arc<IdentityStore>) -> io::Result<Self> {
        let registry = Arc::new(Sources::open(path.clone()).await?);
        Ok(Self {
            registry,
            discovery_http: Arc::new(DiscoveryClient(http::client()?)),
            identities,
        })
    }

    pub async fn close(&self) {
        self.registry.drain().await;
    }

    pub(super) async fn resolver_input(&self, key: &TrackRef) -> io::Result<Option<ResolverInput>> {
        self.identities.resolver_input(key).await
    }

    pub async fn snapshot(&self) -> Vec<SourceState> {
        self.registry.snapshot().await
    }

    pub async fn import(&self, url: &str) -> io::Result<SourceImport> {
        self.registry.import(url).await
    }

    pub async fn remove(&self, id: &str) -> io::Result<bool> {
        self.registry.remove(id).await
    }

    pub async fn refresh(&self) -> SourceRefresh {
        self.registry.refresh().await
    }

    pub async fn discover(&self, keyword: &str) -> Discovery {
        let deadline = tokio::time::Instant::now() + DISCOVERY_DEADLINE;
        let mut channels = BTreeMap::<String, Vec<Provider>>::new();
        let mut discovery = Discovery::default();
        let Ok(active) = tokio::time::timeout_at(deadline, self.registry.active()).await else {
            discovery.partial = true;
            return discovery;
        };
        for (id, script) in active {
            for source in script.channels.keys() {
                let capability = &script.channels[source];
                if !capability.search
                    && (capability.qualities.is_empty() || !DiscoveryClient::supports(source))
                {
                    continue;
                }
                let providers = channels.entry(source.clone()).or_default();
                if providers.len() < 4 {
                    providers.push((id.clone(), script.clone()));
                } else {
                    discovery.partial = true;
                }
            }
        }
        discovery.partial |= channels.len() > 24;
        let mut searches = stream::iter(channels.into_iter().take(24))
            .map(|(source, providers)| async move {
                let outcome = self
                    .search_channel(&source, providers, keyword, 1, 30)
                    .await;
                (source, outcome)
            })
            .buffer_unordered(SEARCH_CHANNEL_CONCURRENCY);
        let mut completed = BTreeMap::new();
        loop {
            match tokio::time::timeout_at(deadline, searches.next()).await {
                Ok(Some((source, result))) => {
                    completed.insert(source, result);
                }
                Ok(None) => break,
                Err(_) => {
                    discovery.partial = true;
                    break;
                }
            }
        }
        // Keep the cap independent of the order in which providers answered.
        for (source, result) in completed {
            if let Ok((items, partial)) = result {
                let capacity = SEARCH_BUDGET.saturating_sub(discovery.tracks.len());
                discovery.partial |= partial || items.len() > capacity;
                discovery.tracks.extend(items.into_iter().take(capacity));
            } else {
                eprintln!("mikd music discovery source={source:?} failed");
                discovery.partial = true;
            }
        }
        discovery
    }

    async fn search_channel(
        &self,
        source: &str,
        providers: Vec<Provider>,
        keyword: &str,
        page: i32,
        limit: i32,
    ) -> io::Result<(Vec<TrackRecord>, bool)> {
        let info = &SearchInput {
            keyword,
            page,
            limit,
        };
        let count = usize::try_from(limit).map_err(io::Error::other)?;
        let preferred = providers
            .iter()
            .find(|(_, script)| !script.channels[source].qualities.is_empty())
            .map(|(id, _)| id.clone());
        let providers = providers
            .into_iter()
            .filter(|(_, script)| script.channels[source].search);
        let mut results = stream::iter(providers)
            .map(|(id, script)| async move {
                let result = tokio::time::timeout(
                    PROVIDER_SEARCH_DEADLINE,
                    script.call(source, "musicSearch", info),
                )
                .await
                .map_err(io::Error::from)
                .and_then(std::convert::identity);
                (id, result)
            })
            .buffered(SEARCH_PROVIDER_CONCURRENCY);
        let mut tracks = Vec::new();
        let mut seen = HashSet::new();
        let mut total = 0;
        let mut capped = false;
        let mut error = None;
        while let Some((id, result)) = results.next().await {
            match result {
                Ok(value) => {
                    let Some((rows, reported_total)) = search_rows(value) else {
                        error = Some(io::Error::other("invalid music search result"));
                        continue;
                    };
                    total = total.max(reported_total);
                    for track in map_tracks(rows, source, &id) {
                        if !seen.insert(track.key.clone()) {
                            continue;
                        }
                        if tracks.len() == count {
                            capped = true;
                            break;
                        }
                        tracks.push(track);
                    }
                }
                Err(failure) => error = Some(failure),
            }
        }
        if tracks.is_empty()
            && DiscoveryClient::supports(source)
            && let Some(provider) = preferred.as_deref()
        {
            let listing = tokio::time::timeout(
                PROVIDER_SEARCH_DEADLINE,
                self.discovery_http.search(source, keyword, page, limit),
            )
            .await??;
            let tracks = map_tracks(listing.tracks, source, provider)
                .filter(|track| seen.insert(track.key.clone()))
                .collect::<Vec<_>>();
            return Ok((
                tracks,
                error.is_some()
                    || usize::try_from(listing.total).is_ok_and(|reported| reported > seen.len()),
            ));
        }
        if tracks.is_empty()
            && let Some(error) = error
        {
            return Err(error);
        }
        let partial = capped
            || error.is_some()
            || usize::try_from(total).is_ok_and(|reported| reported > tracks.len());
        Ok((tracks, partial))
    }

    pub async fn collection(&self, reference: &str, limit: usize) -> io::Result<Collection> {
        if reference.len() > 4096 {
            return Err(super::invalid("expected a supported playlist URL"));
        }
        let reference_url = url::Url::parse(reference)
            .map_err(|_| super::invalid("expected a supported playlist URL"))?;
        let active = self.registry.active().await;
        for (id, script) in &active {
            for (channel, capability) in &script.channels {
                let Some(key) = capability
                    .collection
                    .as_ref()
                    .and_then(|route| route.key(&reference_url))
                else {
                    continue;
                };
                let response = tokio::time::timeout(
                    PROVIDER_SEARCH_DEADLINE,
                    script.call(channel, "musicCollection", serde_json::json!({"key": key})),
                )
                .await?
                .map_err(|error| io::Error::new(error.kind(), "LX collection action failed"))?;
                let title = response["name"].as_str().map_or_else(String::new, label);
                let (rows, total) = search_rows(response)
                    .ok_or_else(|| io::Error::other("invalid LX collection result"))?;
                return Ok(collection_rows(channel, id, title, rows, total, limit));
            }
        }
        let (channel, id) = CHANNELS
            .into_iter()
            .find_map(|channel| {
                discovery_http::playlist_id(channel, reference)
                    .ok()
                    .map(|id| (channel, id))
            })
            .ok_or_else(|| super::invalid("unsupported playlist link"))?;
        let (provider, _) = active
            .into_iter()
            .find(|(_, script)| {
                script
                    .channels
                    .get(channel)
                    .is_some_and(|value| !value.qualities.is_empty())
            })
            .ok_or_else(|| super::failure(mik_protocol::music::music_failure::Code::Unavailable))?;
        let result = self.discovery_http.playlist(channel, &id).await?;
        Ok(collection_rows(
            channel,
            &provider,
            label(&result.name),
            result.tracks,
            result.total,
            limit,
        ))
    }

    async fn candidates(&self, track: &TrackRef, provider: &str) -> Vec<Provider> {
        let mut scripts = self.registry.active().await;
        scripts.retain(|(_, script)| script.channels.contains_key(&track.platform));
        scripts.sort_by_key(|(id, _)| id != provider);
        scripts
    }

    pub(super) async fn resolve(
        &self,
        key: &TrackRef,
        version: Uuid,
        input: &ResolverInput,
        excluded: &[String],
    ) -> io::Result<Resolution> {
        let qualities = metadata::qualities(&input.info);
        let mut error = io::Error::other("no LX source can resolve this track");
        for (provider_id, script) in self
            .candidates(key, &input.provider)
            .await
            .into_iter()
            .filter(|(provider, script)| {
                !excluded.contains(provider) && !script.channels[&key.platform].qualities.is_empty()
            })
            .take(4)
        {
            let Some(quality) =
                metadata::select_quality(&qualities, &script.channels[&key.platform].qualities)
            else {
                continue;
            };
            log_playback_source(&key.platform, version, &provider_id, "resolving");
            match script
                .call(
                    &key.platform,
                    "musicUrl",
                    MediaInput {
                        info: &input.info,
                        quality: Some(quality),
                    },
                )
                .await
                .and_then(|value| {
                    value
                        .as_str()
                        .or_else(|| value["url"].as_str())
                        .ok_or_else(|| io::Error::other("LX source returned no playback URL"))
                        .and_then(http::address)
                }) {
                Ok(url) => {
                    return Ok(Resolution { url, provider_id });
                }
                Err(failure) => {
                    log_playback_source(&key.platform, version, &provider_id, "resolve_failed");
                    let diagnostic = failure.to_string();
                    let safe = match diagnostic.lines().next().unwrap_or_default() {
                        "Error: invalid AES key" => "invalid AES key",
                        "Error: invalid AES key or IV" => "invalid AES key or IV",
                        "LX request timed out" => "LX request timed out",
                        "LX source returned no playback URL" => {
                            "LX source returned no playback URL"
                        }
                        _ => "script failed or returned an invalid URL",
                    };
                    error = playback_source_error(
                        &provider_id,
                        "musicUrl",
                        &io::Error::new(failure.kind(), safe),
                    );
                }
            }
        }
        Err(error)
    }

    async fn lookup(&self, key: &TrackRef, input: &ResolverInput) -> io::Result<LyricsText> {
        let mut transient = false;
        for (_, script) in self
            .candidates(key, &input.provider)
            .await
            .into_iter()
            .filter(|(_, script)| script.channels[&key.platform].lyrics)
            .take(4)
        {
            match script
                .call(
                    &key.platform,
                    "lyric",
                    MediaInput {
                        info: &input.info,
                        quality: None,
                    },
                )
                .await
            {
                Ok(value) => {
                    let lyrics = metadata::lyrics(&value);
                    if lyrics != LyricsText::default() {
                        return Ok(lyrics);
                    }
                }
                Err(_) => transient = true,
            }
        }
        if transient {
            Err(super::failure(
                mik_protocol::music::music_failure::Code::Unavailable,
            ))
        } else {
            Ok(LyricsText::default())
        }
    }

    pub(super) async fn lyrics(
        &self,
        key: &TrackRef,
        input: &ResolverInput,
    ) -> io::Result<LyricsText> {
        tokio::time::timeout(http::TIMEOUT, self.lookup(key, input)).await?
    }
}

pub(super) fn log_playback_source(namespace: &str, version: Uuid, provider_id: &str, stage: &str) {
    eprintln!(
        "mikd LX playback version={} namespace={namespace:?} source={provider_id:?} stage={stage}",
        version.simple()
    );
}

pub(super) fn playback_source_error(
    provider_id: &str,
    action: &str,
    error: &io::Error,
) -> io::Error {
    io::Error::new(
        error.kind(),
        format!("LX source {provider_id:?} ({action}): {error}"),
    )
}

fn search_rows(value: Value) -> Option<(Vec<Value>, i32)> {
    let (items, total) = match value {
        Value::Array(items) => (items, Value::Null),
        Value::Object(mut response) => {
            let items = ["list", "tracks", "data"].into_iter().find_map(|key| {
                match response.remove(key)? {
                    Value::Array(items) => Some(items),
                    _ => None,
                }
            })?;
            (items, response.remove("total").unwrap_or_default())
        }
        _ => return None,
    };
    let count = metadata::total(&total, items.len());
    Some((items, count))
}

fn map_tracks<'source>(
    items: Vec<Value>,
    source: &'source str,
    provider: &'source str,
) -> impl Iterator<Item = TrackRecord> + 'source {
    items
        .into_iter()
        .filter_map(move |item| metadata::from_lx(item, source, provider))
}

fn collection_rows(
    channel: &str,
    provider: &str,
    title: String,
    rows: Vec<Value>,
    total: i32,
    limit: usize,
) -> Collection {
    let row_count = rows.len();
    let mut seen = HashSet::new();
    let mut partial = usize::try_from(total).is_ok_and(|total| total > row_count);
    let mut tracks = Vec::new();
    for row in rows {
        let Some(track) = metadata::from_lx(row, channel, provider) else {
            partial = true;
            continue;
        };
        if !seen.insert(track.key.clone()) {
            continue;
        }
        if tracks.len() == limit {
            partial = true;
            break;
        }
        tracks.push(track);
    }
    Collection {
        title,
        discovery: Discovery { tracks, partial },
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tokio::fs;

    #[tokio::test]
    async fn playback_reports_the_actual_source_after_fallback_or_failure() {
        let root = tempfile::tempdir().unwrap();
        let local = root.path().join("local");
        fs::create_dir(&local).await.unwrap();
        let script = r"
            const { on, send, EVENT_NAMES } = lx;
            on(EVENT_NAMES.request, ({ action }) => {
                if (action !== 'musicUrl') throw new Error('unexpected action');
                __RESOLVE__
            });
            send(EVENT_NAMES.inited, {
                status: true,
                sources: { wy: { type: 'music', actions: ['musicUrl'], qualitys: ['128k'] } },
            });
        ";
        for (name, action) in [
            ("broken.js", "throw new Error('invalid AES key');"),
            ("working.js", "return 'https://example.invalid/audio';"),
        ] {
            fs::write(local.join(name), script.replace("__RESOLVE__", action))
                .await
                .unwrap();
        }
        let identities = Arc::new(
            IdentityStore::open(&root.path().join(super::super::identity::FILE))
                .await
                .unwrap(),
        );
        let source = Source::open(root.path().into(), identities.clone())
            .await
            .unwrap();
        let track = TrackRef::new("wy", "demo".into()).unwrap();
        let input = ResolverInput {
            provider: "local:broken.js".into(),
            info: serde_json::json!({}),
        };
        let version = Uuid::new_v4();
        let selected = source.resolve(&track, version, &input, &[]).await.unwrap();
        assert_eq!(selected.provider_id, "local:working.js");

        let Err(error) = source
            .resolve(&track, version, &input, &[selected.provider_id])
            .await
        else {
            panic!("expected the remaining source to fail");
        };
        assert!(
            error
                .to_string()
                .starts_with("LX source \"local:broken.js\" (musicUrl): invalid AES key"),
            "{error}"
        );
        source.close().await;
        identities.close().await.unwrap();
    }
}
