//! Vanilla Minecraft reference material, separate from MIK server rules and live observations.
mod address;
mod catalog;
mod edition;
mod html;
mod mediawiki;
#[cfg(test)]
mod tests;

pub(super) use address::is_article_citation;
pub(in crate::domains::ai) use edition::WikiEdition;

use self::{
    address::Address,
    catalog::{Catalog, CatalogBuilder},
    mediawiki::Api,
};
use super::{Excerpt, LookupStatus};
use futures_util::{
    StreamExt,
    future::{BoxFuture, FutureExt, Shared, WeakShared},
    stream,
};
use lru::LruCache;
use reqwest::Url;
use serde::Serialize;
use std::{
    collections::HashMap,
    io,
    num::NonZeroUsize,
    path::PathBuf,
    sync::{Arc, Mutex},
    time::{Duration, Instant},
};
use tokio::sync::{OwnedSemaphorePermit, Semaphore};

const CACHE_AGE: Duration = Duration::from_mins(30);
const OUTLINE_BYTES: usize = 3000;
const CATALOG_DOWNLOAD_CONCURRENCY: usize = 3;
const READ_CACHE_CAPACITY: usize = 64;

pub(in crate::domains::ai) struct MinecraftWiki {
    api: Arc<Api>,
    editions: HashMap<WikiEdition, EditionState>,
    refresh: tokio::sync::Mutex<()>,
    catalog_builder: CatalogBuilder,
    reads: Arc<Semaphore>,
}

struct EditionState {
    catalog: Catalog,
    articles: Arc<Mutex<ArticleReads>>,
}

type FetchArticle = BoxFuture<'static, Result<Arc<Article>, Arc<io::Error>>>;

struct ArticleReads {
    cache: LruCache<ReadKey, Cached>,
    pending: HashMap<ReadKey, WeakShared<FetchArticle>>,
}

impl Default for EditionState {
    fn default() -> Self {
        Self {
            catalog: Catalog::default(),
            articles: Arc::new(Mutex::new(ArticleReads {
                cache: LruCache::new(
                    NonZeroUsize::new(READ_CACHE_CAPACITY).unwrap_or(NonZeroUsize::MIN),
                ),
                pending: HashMap::new(),
            })),
        }
    }
}

/// Created only from a public catalog hit, never deserialized from tool arguments.
#[derive(Clone, Eq, PartialEq)]
pub(in crate::domains::ai) struct ArticleRef {
    id: u64,
    target: Address,
}

impl ArticleRef {
    pub(in crate::domains::ai) fn target_for_read(&self, section: Option<&str>) -> Option<Address> {
        let mut target = self.target.clone();
        if let Some(section) = section {
            target.section = Some(address::normalize_fragment(section.trim())?);
        }
        Some(target)
    }
}

#[derive(Clone, Eq, Hash, PartialEq)]
struct ReadKey {
    id: u64,
    section: Option<String>,
}

struct Cached {
    article: Arc<Article>,
    fetched: Instant,
}

pub(in crate::domains::ai) struct Lookup {
    pub(in crate::domains::ai) matches: Vec<Candidate>,
    /// Usable catalogs actually consulted, in lookup order.
    pub(in crate::domains::ai) available_editions: Vec<WikiEdition>,
    /// A catalog not loaded or too old is not evidence that an article is absent.
    pub(in crate::domains::ai) unavailable_editions: Vec<WikiEdition>,
}

#[derive(Serialize)]
pub(in crate::domains::ai) struct Candidate {
    #[serde(skip)]
    pub(in crate::domains::ai) reference: ArticleRef,
    pub(in crate::domains::ai) edition: WikiEdition,
    title: String,
    url: String,
}

#[derive(Serialize)]
pub(in crate::domains::ai) struct Article {
    edition: WikiEdition,
    title: String,
    url: String,
    #[serde(skip)]
    pub(in crate::domains::ai) excerpts: Vec<Excerpt>,
    sections: Vec<Heading>,
    truncated: bool,
    #[serde(skip)]
    pub(in crate::domains::ai) status: LookupStatus,
    #[serde(skip_serializing_if = "Option::is_none")]
    error: Option<&'static str>,
}

#[derive(Serialize)]
struct Heading {
    heading: String,
    section: String,
}

struct Reading {
    index: u32,
    url: Url,
    markup: String,
}

impl MinecraftWiki {
    pub(in crate::domains::ai) fn new(cache: Option<PathBuf>) -> io::Result<Self> {
        Ok(Self {
            api: Arc::new(Api::new()?),
            editions: WikiEdition::all()
                .map(|edition| (edition, EditionState::default()))
                .collect(),
            refresh: tokio::sync::Mutex::new(()),
            catalog_builder: CatalogBuilder::for_host(cache),
            reads: Arc::new(Semaphore::new(4)),
        })
    }

    #[cfg(test)]
    pub(in crate::domains::ai) fn set_test_origin(&mut self, origin: &str) -> io::Result<()> {
        Arc::get_mut(&mut self.api).unwrap().set_test_origin(origin)
    }

    pub(in crate::domains::ai) async fn refresh_due(&self) {
        // A second refresh trigger must not start another pool. Lookups never take this lock.
        let Ok(_refresh) = self.refresh.try_lock() else {
            return;
        };
        let downloads = &Semaphore::new(CATALOG_DOWNLOAD_CONCURRENCY);
        let refresh_concurrency = CATALOG_DOWNLOAD_CONCURRENCY + self.catalog_builder.parallelism();
        let priority = [WikiEdition::ENGLISH, WikiEdition::CHINESE];
        let remaining = WikiEdition::all().filter(|edition| !priority.contains(edition));
        stream::iter(priority.into_iter().chain(remaining))
            .for_each_concurrent(refresh_concurrency, |edition| async move {
                if let Some(state) = self.editions.get(&edition)
                    && let Err(error) = state
                        .catalog
                        .refresh(&self.api, edition, downloads, &self.catalog_builder)
                        .await
                {
                    eprintln!(
                        "mikd AI Minecraft Wiki catalog ({}): {error}",
                        edition.code()
                    );
                }
            })
            .await;
    }

    pub(in crate::domains::ai) fn find(
        &self,
        preferred: WikiEdition,
        language: Option<WikiEdition>,
        query: &str,
    ) -> io::Result<Lookup> {
        let mut lookup = Lookup {
            matches: Vec::new(),
            available_editions: Vec::new(),
            unavailable_editions: Vec::new(),
        };
        if query.contains("://") {
            let address = Address::parse(query)
                .ok_or_else(|| invalid("expected a supported Minecraft Wiki article URL"))?;
            if language.is_some_and(|edition| edition != address.edition) {
                return Err(invalid(
                    "Minecraft Wiki URL conflicts with the requested edition",
                ));
            }
            if let Some(state) = self.editions.get(&address.edition) {
                let entries = state.catalog.exact(&address.title)?;
                lookup.add(address.edition, entries, address.section.as_deref());
            }
            return Ok(lookup);
        }

        for edition in language.unwrap_or(preferred).fallbacks() {
            if let Some(state) = self.editions.get(&edition) {
                lookup.add(edition, state.catalog.search(query)?, None);
            }
            if language.is_some() || !lookup.matches.is_empty() {
                break;
            }
        }
        Ok(lookup)
    }

    pub(in crate::domains::ai) async fn read(
        &self,
        article: ArticleRef,
        section: Option<&str>,
    ) -> io::Result<Arc<Article>> {
        let target = article
            .target_for_read(section)
            .ok_or_else(|| invalid("invalid Minecraft Wiki section"))?;
        let edition = target.edition;
        let state = self
            .editions
            .get(&edition)
            .ok_or_else(|| invalid("unknown Minecraft Wiki edition"))?;
        let key = ReadKey {
            id: article.id,
            section: target.section,
        };
        let fetch = {
            let mut reads = state
                .articles
                .lock()
                .map_err(|_| malformed("Minecraft Wiki article cache unavailable"))?;
            if let Some(cached) = reads
                .cache
                .get(&key)
                .filter(|cached| cached.fetched.elapsed() < CACHE_AGE)
            {
                return Ok(cached.article.clone());
            }
            if let Some(active) = reads.pending.get(&key).and_then(WeakShared::upgrade) {
                active
            } else {
                let permit = self.reads.clone().try_acquire_owned().map_err(|_| {
                    io::Error::new(io::ErrorKind::WouldBlock, "Minecraft Wiki readers are busy")
                })?;
                reads.pending.retain(|_, flight| flight.upgrade().is_some());
                let flight = shared_read(
                    self.api.clone(),
                    state.articles.clone(),
                    edition,
                    key.clone(),
                    permit,
                );
                if let Some(weak) = flight.downgrade() {
                    drop(reads.pending.insert(key, weak));
                }
                flight
            }
        };
        fetch
            .await
            .map_err(|error| io::Error::new(error.kind(), error))
    }
}

fn shared_read(
    api: Arc<Api>,
    state: Arc<Mutex<ArticleReads>>,
    edition: WikiEdition,
    key: ReadKey,
    permit: OwnedSemaphorePermit,
) -> Shared<FetchArticle> {
    async move {
        let fetched = fetch_article(&api, edition, &key, permit).await;
        if let Ok(mut reads) = state.lock() {
            drop(reads.pending.remove(&key));
            if let Ok(article) = &fetched
                && article.status == LookupStatus::Found
            {
                drop(reads.cache.put(
                    key,
                    Cached {
                        article: article.clone(),
                        fetched: Instant::now(),
                    },
                ));
            }
        }
        fetched.map_err(Arc::new)
    }
    .boxed()
    .shared()
}

async fn fetch_article(
    api: &Api,
    edition: WikiEdition,
    key: &ReadKey,
    permit: OwnedSemaphorePermit,
) -> io::Result<Arc<Article>> {
    let page = api.resolve(edition, key.id).await?;
    let url = address::article(edition, &page.title, None)
        .ok_or_else(|| malformed("invalid canonical Minecraft Wiki article"))?;
    let outline = api.outline(edition, &page).await?;
    let requested = key.section.as_deref().or(page.redirect_section.as_deref());
    let selected = match requested {
        None => Some((0, None)),
        Some(fragment) => select(&outline, fragment).map(|(index, section)| (index, Some(section))),
    };
    let reading = if let Some((index, section)) = selected {
        let section_url = match section {
            Some(section) => address::article(edition, &page.title, Some(&section.anchor))
                .ok_or_else(|| malformed("invalid canonical Minecraft Wiki section"))?,
            None => url.clone(),
        };
        Some(Reading {
            index,
            url: section_url,
            markup: api.section(edition, &page, index).await?,
        })
    } else {
        None
    };
    tokio::task::spawn_blocking(move || {
        let _permit = permit;
        render(edition, page.title, url, &outline, reading).map(Arc::new)
    })
    .await
    .map_err(io::Error::other)?
}

fn render(
    edition: WikiEdition,
    title: String,
    url: Url,
    outline: &[mediawiki::Section],
    reading: Option<Reading>,
) -> io::Result<Article> {
    let mut article = Article {
        edition,
        title,
        url: url.into(),
        excerpts: Vec::new(),
        sections: Vec::new(),
        truncated: false,
        status: LookupStatus::Found,
        error: None,
    };
    let Some(reading) = reading else {
        article.sections = headings(outline);
        article.status = LookupStatus::NoMatch;
        article.error = Some("Minecraft Wiki section not found; choose a section from the outline");
        return Ok(article);
    };
    let language = edition
        .site()
        .ok_or_else(|| invalid("unknown Minecraft Wiki edition"))?
        .language;
    let label = format!("Minecraft Wiki ({language})");
    let (text, truncated) = html::excerpt(&reading.markup)?;
    if text.trim().is_empty() {
        return Err(io::Error::new(
            io::ErrorKind::NotFound,
            "Minecraft Wiki has no readable text here",
        ));
    }
    let source = selected_label(outline, reading.index).map_or_else(
        || format!("{label} · {}", article.title),
        |section| format!("{label} · {} — {section}", article.title),
    );
    article.excerpts.push(Excerpt {
        source,
        text,
        wiki_url: reading.url.into(),
    });
    if reading.index == 0 {
        article.sections = headings(outline);
    }
    article.truncated = truncated;
    Ok(article)
}

impl Lookup {
    fn add(
        &mut self,
        edition: WikiEdition,
        entries: Option<Vec<catalog::Entry>>,
        section: Option<&str>,
    ) {
        let Some(entries) = entries else {
            self.unavailable_editions.push(edition);
            return;
        };
        self.available_editions.push(edition);
        self.matches.extend(
            entries
                .into_iter()
                .filter_map(|entry| {
                    let url = address::article(edition, &entry.title, section)?;
                    Some(Candidate {
                        reference: ArticleRef {
                            id: entry.id,
                            target: Address {
                                edition,
                                title: entry.title.clone(),
                                section: section.map(str::to_owned),
                            },
                        },
                        edition,
                        title: entry.title,
                        url: url.into(),
                    })
                })
                .take(4),
        );
    }
}

fn select<'a>(
    outline: &'a [mediawiki::Section],
    fragment: &str,
) -> Option<(u32, &'a mediawiki::Section)> {
    let decoded = address::decode_fragment(fragment);
    outline.iter().find_map(|section| {
        let index = section_index(section)?;
        (section.anchor == fragment || decoded.as_deref() == Some(section.anchor.as_str()))
            .then_some((index, section))
    })
}

fn section_index(section: &mediawiki::Section) -> Option<u32> {
    section.index.parse().ok().filter(|index| *index != 0)
}

fn selected_label(outline: &[mediawiki::Section], index: u32) -> Option<String> {
    outline
        .iter()
        .find(|section| section_index(section) == Some(index))
        .map(|section| html::heading(&section.line))
        .filter(|heading| !heading.is_empty())
}

fn headings(outline: &[mediawiki::Section]) -> Vec<Heading> {
    let mut budget = OUTLINE_BYTES;
    outline
        .iter()
        .filter_map(|section| {
            section_index(section)?;
            let section_name = address::normalize_fragment(&section.anchor)?;
            Some(Heading {
                heading: html::heading(&section.line),
                section: section_name,
            })
        })
        .take(32)
        .take_while(|heading| {
            let size = serde_json::to_vec(heading).map_or(OUTLINE_BYTES, |bytes| bytes.len() + 1);
            if size > budget {
                return false;
            }
            budget -= size;
            true
        })
        .collect()
}

fn invalid(message: &'static str) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidInput, message)
}

fn malformed(message: &'static str) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidData, message)
}
