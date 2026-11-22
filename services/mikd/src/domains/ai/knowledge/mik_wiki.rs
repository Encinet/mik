use super::{Builder, CHUNK_SIZE, Fields, KnowledgeIndex};
use crate::http_egress;
use reqwest::{Client, StatusCode, Url, header};
use serde::{Deserialize, Serialize, Serializer};
use sha2::{Digest, Sha256};
use std::{
    io,
    sync::{Arc, Mutex},
    time::{Duration, Instant},
};
use tantivy::doc;
use text_splitter::TextSplitter;

const ORIGIN: &str = "https://mcmik.top";
const FEED_PATH: &str = "/api/wiki/search-index/";
const MAX_FEED_BYTES: usize = 2 * 1024 * 1024;
const MAX_BLOCKS: usize = 1024;
use crate::domains::ai::reference::CITATION_URL_LIMIT;
const MAX_AGE: Duration = Duration::from_hours(24);

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(in crate::domains::ai) enum MikWikiLocale {
    Chinese,
    English,
}

impl MikWikiLocale {
    const ALL: [Self; 2] = [Self::Chinese, Self::English];

    pub(in crate::domains::ai) fn from_locale(locale: &str) -> Self {
        let language = locale.split(['-', '_']).next().unwrap_or_default();
        if language.eq_ignore_ascii_case("zh") || language.eq_ignore_ascii_case("lzh") {
            Self::Chinese
        } else {
            Self::English
        }
    }

    pub(super) const fn other(self) -> Self {
        match self {
            Self::Chinese => Self::English,
            Self::English => Self::Chinese,
        }
    }
}

fn tag(locale: MikWikiLocale) -> &'static str {
    match locale {
        MikWikiLocale::Chinese => "zh-CN",
        MikWikiLocale::English => "en",
    }
}

#[expect(
    clippy::trivially_copy_pass_by_ref,
    reason = "serde serialize_with signature"
)]
fn serialize_locale<S: Serializer>(
    locale: &MikWikiLocale,
    serializer: S,
) -> Result<S::Ok, S::Error> {
    serializer.serialize_str(tag(*locale))
}

fn article(locale: MikWikiLocale, section: &str) -> io::Result<Url> {
    let mut url = Url::parse(ORIGIN).map_err(io::Error::other)?;
    url.path_segments_mut()
        .map_err(|()| io::Error::other("invalid official wiki origin"))?
        .extend([tag(locale), "wiki"]);
    url.query_pairs_mut().append_pair("section", section);
    Ok(url)
}

pub(super) fn is_section_citation(url: &Url) -> bool {
    matches!(url.path(), "/zh-CN/wiki" | "/en/wiki")
        && url
            .query()
            .and_then(|query| query.strip_prefix("section="))
            .is_some_and(valid_section)
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize)]
pub(in crate::domains::ai) struct Target {
    #[serde(serialize_with = "serialize_locale")]
    pub(super) locale: MikWikiLocale,
    pub(super) key: String,
    section: String,
}

impl Target {
    pub(in crate::domains::ai) fn parse(query: &str, preferred: MikWikiLocale) -> Option<Self> {
        if query.len() > 2048 || query.contains('\\') || query.chars().any(char::is_control) {
            return None;
        }
        let url = Url::parse(query).ok()?;
        if url.scheme() != "https"
            || url.host_str() != Some("mcmik.top")
            || !url.username().is_empty()
            || url.password().is_some()
            || url.port().is_some()
        {
            return None;
        }
        let locale = match url.path().trim_end_matches('/') {
            "/wiki" => preferred,
            "/zh-CN/wiki" => MikWikiLocale::Chinese,
            "/en/wiki" => MikWikiLocale::English,
            _ => return None,
        };
        let section = url
            .query_pairs()
            .find_map(|(key, value)| (key == "section").then_some(value));
        let section = section.as_deref().unwrap_or("getting-started");
        if !valid_section(section) {
            return None;
        }
        let key = if let Some(fragment) = url.fragment().filter(|fragment| !fragment.is_empty()) {
            let mut article = article(locale, section).ok()?;
            article.set_fragment(Some(fragment));
            article.into()
        } else {
            section.into()
        };
        Some(Self {
            locale,
            key,
            section: section.into(),
        })
    }
}

pub(in crate::domains::ai) struct MikWiki {
    client: Client,
    feed: Url,
    chinese: Mutex<Option<Snapshot>>,
    english: Mutex<Option<Snapshot>>,
}

struct Snapshot {
    digest: [u8; 32],
    refreshed: Instant,
    index: Arc<KnowledgeIndex>,
}

impl MikWiki {
    pub(in crate::domains::ai) fn new() -> io::Result<Self> {
        // Only the fixed HTTPS endpoint is fetched. Player links select documents locally.
        Self::open(ORIGIN, true)
    }

    #[cfg(test)]
    pub(in crate::domains::ai) fn with_test_origin(origin: &str) -> io::Result<Self> {
        Self::open(origin, false)
    }

    fn open(origin: &str, https_only: bool) -> io::Result<Self> {
        let feed = Url::parse(origin)
            .and_then(|origin| origin.join(FEED_PATH))
            .map_err(io::Error::other)?;
        Ok(Self {
            // Use the machine's DNS (including split DNS) for the official Wiki authority.
            client: http_egress::direct_client(true)
                .https_only(https_only)
                .timeout(Duration::from_secs(8))
                .build()
                .map_err(io::Error::other)?,
            feed,
            chinese: Mutex::default(),
            english: Mutex::default(),
        })
    }

    pub(super) async fn refresh(&self) {
        let refresh = |locale| async move {
            if let Err(error) = self.refresh_locale(locale).await {
                eprintln!("mikd AI official Wiki ({}): {error:?}", tag(locale));
            }
        };
        let [first, second] = MikWikiLocale::ALL;
        tokio::join!(refresh(first), refresh(second));
    }

    pub(super) fn cached(&self, locale: MikWikiLocale) -> Option<Arc<KnowledgeIndex>> {
        let snapshot = self.slot(locale).lock().ok()?;
        snapshot
            .as_ref()
            .filter(|snapshot| snapshot.refreshed.elapsed() < MAX_AGE)
            .map(|snapshot| snapshot.index.clone())
    }

    fn slot(&self, locale: MikWikiLocale) -> &Mutex<Option<Snapshot>> {
        match locale {
            MikWikiLocale::Chinese => &self.chinese,
            MikWikiLocale::English => &self.english,
        }
    }

    async fn refresh_locale(&self, locale: MikWikiLocale) -> io::Result<()> {
        let url = self.feed.join(tag(locale)).map_err(io::Error::other)?;
        let mut response = self
            .client
            .get(url)
            .header(header::ACCEPT, "application/json")
            .header(header::USER_AGENT, "MIK-AI-Wiki/1.0")
            .send()
            .await
            .map_err(io::Error::other)?;
        if response.status() != StatusCode::OK {
            return Err(io::Error::other(format!(
                "Wiki index HTTP {}",
                response.status()
            )));
        }
        if response
            .content_length()
            .is_some_and(|len| len > MAX_FEED_BYTES as u64)
        {
            return Err(invalid_feed("Wiki index exceeds its byte limit"));
        }
        let mut bytes = Vec::new();
        while let Some(chunk) = response.chunk().await.map_err(io::Error::other)? {
            if bytes.len() + chunk.len() > MAX_FEED_BYTES {
                return Err(invalid_feed("Wiki index exceeds its byte limit"));
            }
            bytes.extend_from_slice(&chunk);
        }
        let digest = Sha256::digest(&bytes).into();
        let unavailable = |_| io::Error::other("Wiki snapshot unavailable");
        let slot = self.slot(locale);
        {
            let mut current = slot.lock().map_err(unavailable)?;
            if let Some(snapshot) = current
                .as_mut()
                .filter(|snapshot| snapshot.digest == digest)
            {
                snapshot.refreshed = Instant::now();
                return Ok(());
            }
        }
        let index = tokio::task::spawn_blocking(move || build(locale, &bytes))
            .await
            .map_err(io::Error::other)??;
        let snapshot = Snapshot {
            digest,
            refreshed: Instant::now(),
            index: Arc::new(index),
        };
        // Drop the old index after releasing the lock; answers can keep using their snapshot.
        let old = slot.lock().map_err(unavailable)?.replace(snapshot);
        drop(old);
        Ok(())
    }
}

// Consume the website's public, non-executable excerpts, not the staff MDX in its source tree.
#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct Block {
    section_id: String,
    heading: String,
    slug: String,
    direct_text: String,
    level: u8,
}

struct Heading<'a> {
    level: u8,
    title: &'a str,
    url: Url,
}

fn build(locale: MikWikiLocale, bytes: &[u8]) -> io::Result<KnowledgeIndex> {
    let blocks: Vec<Block> =
        serde_json::from_slice(bytes).map_err(|_| invalid_feed("malformed public Wiki index"))?;
    if blocks.is_empty() || blocks.len() > MAX_BLOCKS {
        return Err(invalid_feed("invalid number of public Wiki headings"));
    }
    let builder = Builder::new()?;
    let Fields {
        source,
        body,
        wiki_url,
        wiki_target,
    } = builder.fields;
    let splitter = TextSplitter::new(CHUNK_SIZE);
    let mut section = "";
    let mut headings = Vec::<Heading>::new();
    let mut has_text = false;
    for block in &blocks {
        if !valid_section(&block.section_id)
            || !(1..=4).contains(&block.level)
            || block.heading.trim().is_empty()
            || block.heading.len() > CITATION_URL_LIMIT
        {
            return Err(invalid_feed("invalid public Wiki heading"));
        }
        if section != block.section_id {
            section = &block.section_id;
            headings.clear();
        }
        headings.retain(|ancestor| ancestor.level < block.level);
        let mut url = article(locale, section)?;
        if !block.slug.is_empty() {
            url.set_fragment(Some(&block.slug));
            if url.as_str().len() > CITATION_URL_LIMIT {
                url.set_fragment(None);
            }
        }
        headings.push(Heading {
            level: block.level,
            title: &block.heading,
            url: url.clone(),
        });
        let title = headings
            .iter()
            .map(|heading| heading.title)
            .collect::<Vec<_>>()
            .join(" › ");
        for chunk in splitter.chunks(&block.direct_text) {
            has_text = true;
            let mut document = doc!(source => title.as_str(), body => format!("{title}\n\n{chunk}"),
                wiki_url => url.as_str(), wiki_target => section);
            // A link to an article or a parent heading also finds the text under its subheadings.
            for heading in &headings {
                if heading.url.fragment().is_some() {
                    document.add_text(wiki_target, heading.url.as_str());
                }
            }
            builder.add(document)?;
        }
    }
    if !has_text {
        return Err(invalid_feed("public Wiki index contains no article text"));
    }
    builder.finish()
}

fn valid_section(section: &str) -> bool {
    (1..=80).contains(&section.len())
        && section
            .bytes()
            .all(|ch| ch.is_ascii_lowercase() || ch.is_ascii_digit() || ch == b'-')
}

fn invalid_feed(message: &'static str) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidData, message)
}

#[cfg(test)]
mod tests {
    use super::{super::Knowledge, *};
    use axum::{Json, Router, extract::Path, http::Uri, routing::get};
    use serde_json::json;
    use std::{
        net::Ipv4Addr,
        sync::atomic::{AtomicBool, Ordering},
    };
    use tokio::{net::TcpListener, sync::mpsc};
    use tokio_util::task::AbortOnDropHandle;

    #[tokio::test]
    async fn indexes_public_locales_and_keeps_the_last_snapshot_on_bad_refresh() {
        let (requests, mut received) = mpsc::unbounded_channel();
        let broken = Arc::new(AtomicBool::new(false));
        let serve_broken = broken.clone();
        let index_response = move |Path(locale): Path<String>, uri: Uri| {
            assert!(uri.query().is_none());
            requests.send(locale.clone()).unwrap();
            let invalid = serve_broken.load(Ordering::Relaxed);
            async move {
                Json(match locale.as_str() {
                    "zh-CN" if !invalid => json!([
                        {"sectionId":"mechanisms", "heading":"铜门", "slug":"铜门", "directText":"", "level":1},
                        {"sectionId":"mechanisms", "heading":"使用钥匙", "slug":"使用钥匙", "directText":"蓝灯亮起后插入铜钥匙。", "level":2,
                         "url":"https://not-the-wiki.example/unsafe", "subtreeText":"Only this heading's direct text is indexed."}
                    ]),
                    "en" if !invalid => json!([
                        {"sectionId":"mechanisms", "heading":"Copper Gate", "slug":"copper-gate", "directText":"Insert your copper key once the blue light is on.", "level":1}
                    ]),
                    _ => json!({"error":"invalid response"}),
                })
            }
        };
        let app = Router::new().route("/api/wiki/search-index/{locale}", get(index_response));
        let listener = TcpListener::bind((Ipv4Addr::LOCALHOST, 0)).await.unwrap();
        let origin = format!("http://{}", listener.local_addr().unwrap());
        let _server = AbortOnDropHandle::new(tokio::spawn(async {
            axum::serve(listener, app).await.unwrap();
        }));
        let knowledge = Knowledge::open(None, MikWiki::with_test_origin(&origin).unwrap()).unwrap();
        let wiki = &knowledge.mik_wiki;
        let zh = MikWikiLocale::from_locale("zh-TW");
        let en = MikWikiLocale::from_locale("en-US");
        let query = "铜门 铜钥匙";
        let offline = knowledge.search(zh, query).unwrap();
        assert_eq!(offline.wiki_status, super::super::LookupStatus::Unavailable);
        assert!(offline.excerpts.is_empty());

        knowledge.refresh_wiki().await;
        let mut locales = [received.try_recv().unwrap(), received.try_recv().unwrap()];
        locales.sort_unstable();
        assert_eq!(locales, ["en", "zh-CN"]);

        let source =
            Url::parse("https://mcmik.top/zh-CN/wiki?section=mechanisms#使用钥匙").unwrap();
        let matches = knowledge.search(zh, query).unwrap();
        assert_eq!(matches.wiki_status, super::super::LookupStatus::Found);
        let excerpt = &matches.excerpts[0];
        assert!(excerpt.text.contains("蓝灯亮起后插入铜钥匙。"));
        assert_eq!(excerpt.wiki_url, source.as_str());
        assert_eq!(excerpt.source, "铜门 › 使用钥匙");

        let linked_from_english = knowledge
            .search(en, "https://mcmik.top/zh-CN/wiki?section=mechanisms#铜门")
            .unwrap();
        assert_eq!(linked_from_english.excerpts[0].wiki_url, source.as_str());
        let english = knowledge.search(en, "copper key").unwrap();
        assert_eq!(
            english.excerpts[0].wiki_url,
            "https://mcmik.top/en/wiki?section=mechanisms#copper-gate"
        );
        let missing = knowledge
            .search(zh, "https://mcmik.top/zh-CN/wiki?section=moderator")
            .unwrap();
        assert_eq!(missing.wiki_status, super::super::LookupStatus::NoMatch);
        assert!(missing.excerpts.is_empty());
        assert!(
            received.try_recv().is_err(),
            "player lookups must never contact the wiki"
        );

        broken.store(true, Ordering::Relaxed);
        assert!(wiki.refresh_locale(zh).await.is_err());
        assert_eq!(
            knowledge.search(zh, source.as_str()).unwrap().excerpts[0].wiki_url,
            source.as_str()
        );
        wiki.chinese.lock().unwrap().as_mut().unwrap().refreshed -= MAX_AGE;
        let expired = knowledge.search(zh, source.as_str()).unwrap();
        assert_eq!(expired.wiki_status, super::super::LookupStatus::Unavailable);
        assert!(expired.excerpts.is_empty());
    }
}
