//! Local title catalogs. Player input is used only by local index queries.
mod disk;

use self::disk::DiskCache;
use super::{super::tokenizer::IcuTokenizer, WikiEdition, mediawiki::Api};
use rayon::{ThreadPool, ThreadPoolBuilder};
use serde::{Deserialize, Deserializer, de::IgnoredAny};
use sha2::{Digest, Sha256};
use std::{
    collections::HashSet,
    io,
    num::{NonZeroU64, NonZeroUsize},
    path::{Path, PathBuf},
    sync::{Arc, Mutex, OnceLock},
    thread::available_parallelism,
    time::{Duration, Instant},
};
use tantivy::{
    Index, IndexReader, ReloadPolicy, TantivyDocument, Term,
    collector::TopDocs,
    directory::MmapDirectory,
    indexer::IndexWriterOptions,
    query::{BooleanQuery, BoostQuery, PhraseQuery, Query, TermQuery},
    schema::{
        Field, IndexRecordOption, STORED, STRING, Schema, TextFieldIndexing, TextOptions, Value,
    },
    tokenizer::{TokenStream, Tokenizer},
};
use tokio::sync::{OwnedSemaphorePermit, Semaphore, oneshot};
use unicode_normalization::UnicodeNormalization;

const REFRESH_INTERVAL: Duration = Duration::from_hours(24);
const MAX_AGE: Duration = Duration::from_hours(7 * 24);
const RETRY_INTERVAL: Duration = Duration::from_hours(1);
const DOWNLOAD_TIMEOUT: Duration = Duration::from_mins(4);
const MAX_BATCHES: usize = 240;
const MAX_ENTRIES: usize = 100_000;
const MAX_BATCH_BYTES: usize = 512 * 1024;
const MAX_CURSOR_BYTES: usize = 1024;
const MAX_TITLE_BYTES: usize = 255;
const MAX_QUERY_BYTES: usize = 512;
const MAX_TERMS: usize = 24;
const MAX_MATCHES: usize = 8;
const TITLE_DOCUMENT_OVERHEAD: usize = size_of::<u64>() + 2 * size_of::<u32>();
const INDEX_MEMORY: usize = 24 * 1024 * 1024;
const TITLES_PER_WRITER: usize = 20_000;

#[cfg_attr(test, derive(Debug, Eq, PartialEq))]
pub(super) struct Entry {
    pub(super) id: u64,
    pub(super) title: String,
}

#[derive(Default)]
pub(super) struct Catalog {
    state: Mutex<State>,
}

#[derive(Default)]
struct State {
    snapshot: Option<Snapshot>,
    last_attempt: Option<Instant>,
}

struct Snapshot {
    refreshed: Instant,
    index: Arc<TitleIndex>,
}

impl Catalog {
    pub(super) fn search(&self, terms: &str) -> io::Result<Option<Vec<Entry>>> {
        self.index()?.map(|index| index.search(terms)).transpose()
    }

    pub(super) fn exact(&self, title: &str) -> io::Result<Option<Vec<Entry>>> {
        self.index()?.map(|index| index.exact(title)).transpose()
    }

    fn index(&self) -> io::Result<Option<Arc<TitleIndex>>> {
        let (current, expired) = {
            let mut state = self.state.lock().map_err(|_| state_error())?;
            let expired = state
                .snapshot
                .take_if(|snapshot| snapshot.refreshed.elapsed() >= MAX_AGE);
            let current = state
                .snapshot
                .as_ref()
                .map(|snapshot| snapshot.index.clone());
            (current, expired)
        };
        drop(expired);
        Ok(current)
    }

    fn begin_refresh(&self) -> io::Result<bool> {
        let mut state = self.state.lock().map_err(|_| state_error())?;
        if state
            .snapshot
            .as_ref()
            .is_some_and(|snapshot| snapshot.refreshed.elapsed() < REFRESH_INTERVAL)
            || state
                .last_attempt
                .is_some_and(|attempt| attempt.elapsed() < RETRY_INTERVAL)
        {
            return Ok(false);
        }
        state.last_attempt = Some(Instant::now());
        Ok(true)
    }

    pub(super) async fn refresh(
        &self,
        api: &Api,
        edition: WikiEdition,
        downloads: &Semaphore,
        builder: &CatalogBuilder,
    ) -> io::Result<()> {
        if !self.begin_refresh()? {
            return Ok(());
        }
        if self.index()?.is_none()
            && let Some(snapshot) = builder.restore(edition).await
        {
            let fresh = snapshot.refreshed.elapsed() < REFRESH_INTERVAL;
            self.install(snapshot)?;
            if fresh {
                return Ok(());
            }
        }
        let entries = {
            let _download = downloads.acquire().await.map_err(io::Error::other)?;
            tokio::time::timeout(DOWNLOAD_TIMEOUT, download(api, edition))
                .await
                .map_err(|_| {
                    io::Error::new(
                        io::ErrorKind::TimedOut,
                        "Minecraft Wiki catalog scan timed out",
                    )
                })??
        };
        let index = builder.build(edition, entries, self.index()?).await?;
        self.install(Snapshot {
            refreshed: Instant::now(),
            index,
        })
    }

    fn install(&self, snapshot: Snapshot) -> io::Result<()> {
        let previous = self
            .state
            .lock()
            .map_err(|_| state_error())?
            .snapshot
            .replace(snapshot);
        drop(previous);
        Ok(())
    }
}

pub(super) struct CatalogBuilder {
    build_slots: Arc<Semaphore>,
    workers: OnceLock<Option<ThreadPool>>,
    parallelism: usize,
    disk: Option<Arc<DiskCache>>,
}

impl CatalogBuilder {
    pub(super) fn for_host(cache: Option<PathBuf>) -> Self {
        let cpus = available_parallelism().map_or(1, NonZeroUsize::get);
        let mut builder = Self::new(cpus);
        builder.disk = cache.map(DiskCache::new).map(Arc::new);
        builder
    }

    fn new(parallelism: usize) -> Self {
        Self {
            build_slots: Arc::new(Semaphore::new(parallelism)),
            workers: OnceLock::new(),
            parallelism,
            disk: None,
        }
    }

    pub(super) fn parallelism(&self) -> usize {
        self.parallelism.min(WikiEdition::all().len().max(1))
    }

    async fn restore(&self, edition: WikiEdition) -> Option<Snapshot> {
        let disk = self.disk.clone()?;
        let result = tokio::task::spawn_blocking(move || {
            disk.path(edition)
                .map_or(Ok(None), |path| TitleIndex::restore(&path))
        })
        .await;
        match result {
            Ok(Ok(snapshot)) => snapshot,
            Ok(Err(error)) if error.kind() == io::ErrorKind::NotFound => None,
            Ok(Err(error)) => {
                eprintln!(
                    "mikd Minecraft Wiki ({}): cannot restore catalog: {error}",
                    edition.code()
                );
                None
            }
            Err(error) => {
                eprintln!(
                    "mikd Minecraft Wiki ({}): restore interrupted: {error}",
                    edition.code()
                );
                None
            }
        }
    }

    async fn build(
        &self,
        edition: WikiEdition,
        entries: Vec<Entry>,
        current: Option<Arc<TitleIndex>>,
    ) -> io::Result<Arc<TitleIndex>> {
        let slots = self.build_slots.clone();
        let permit = slots
            .clone()
            .acquire_owned()
            .await
            .map_err(io::Error::other)?;
        let disk = self.disk.clone();
        let build = move || {
            let path = disk.as_ref().and_then(|cache| cache.path(edition));
            let digest = catalog_digest(&entries);
            let unchanged = current.as_ref().filter(|index| index.digest == digest);
            if let Some(index) = unchanged
                && (path.is_none() || index.persistent.is_some())
            {
                if let Some(path) = path.as_deref().filter(|_| index.persistent.is_some())
                    && let Err(error) = disk::revalidate(path)
                {
                    eprintln!(
                        "mikd Minecraft Wiki ({}): cannot update catalog freshness: {error}",
                        edition.code()
                    );
                }
                return Ok(index.clone());
            }
            let permits = reserve_index_workers(&slots, permit, entries.len());
            let workers = permits.num_permits();
            if let Some(path) = path {
                let previous = current.as_ref().and_then(|index| index.persistent.as_ref());
                match TitleIndex::build_persistent(&path, previous, &entries, digest, workers) {
                    Ok(index) => return Ok(Arc::new(index)),
                    Err(error) => eprintln!(
                        "mikd Minecraft Wiki ({}): cannot store catalog: {error}",
                        edition.code()
                    ),
                }
            }
            unchanged.map_or_else(
                || TitleIndex::build(&entries, digest, workers).map(Arc::new),
                |index| Ok(index.clone()),
            )
        };
        if let Some(workers) = self
            .workers
            .get_or_init(|| catalog_workers(self.parallelism()))
        {
            let (result, wait) = oneshot::channel();
            workers.spawn(move || {
                if !result.is_closed() {
                    drop(result.send(build()));
                }
            });
            wait.await.map_err(|_| build_interrupted())?
        } else {
            tokio::task::spawn_blocking(build)
                .await
                .map_err(|_| build_interrupted())?
        }
    }
}

fn reserve_index_workers(
    slots: &Arc<Semaphore>,
    mut permits: OwnedSemaphorePermit,
    titles: usize,
) -> OwnedSemaphorePermit {
    let desired = titles.div_ceil(TITLES_PER_WRITER);
    while permits.num_permits() < desired {
        let Ok(extra) = slots.clone().try_acquire_owned() else {
            break;
        };
        permits.merge(extra);
    }
    permits
}

fn catalog_workers(parallelism: usize) -> Option<ThreadPool> {
    ThreadPoolBuilder::new()
        .num_threads(parallelism)
        .thread_name(|index| format!("mikd-wiki-catalog-{index}"))
        .panic_handler(drop)
        .build()
        .ok()
}

fn build_interrupted() -> io::Error {
    io::Error::other("Minecraft Wiki catalog build did not complete")
}

fn catalog_digest(entries: &[Entry]) -> [u8; 32] {
    let mut digest = Sha256::new();
    for entry in entries {
        digest.update(entry.id.to_le_bytes());
        digest.update((entry.title.len() as u64).to_le_bytes());
        digest.update(entry.title.as_bytes());
    }
    digest.finalize().into()
}

#[derive(Deserialize)]
struct Batch {
    query: AllPages,
    #[serde(default, rename = "continue", deserialize_with = "present_cursor")]
    cursor: Option<Cursor>,
    error: Option<IgnoredAny>,
}

#[derive(Deserialize)]
struct AllPages {
    allpages: Vec<PublicPage>,
}

#[derive(Deserialize)]
struct PublicPage {
    ns: u32,
    pageid: NonZeroU64,
    title: String,
}

#[derive(Deserialize)]
struct Cursor {
    apcontinue: String,
    #[serde(rename = "continue")]
    general: String,
}

fn present_cursor<'de, D: Deserializer<'de>>(deserializer: D) -> Result<Option<Cursor>, D::Error> {
    Cursor::deserialize(deserializer).map(Some)
}

impl Batch {
    fn append(self, entries: &mut Vec<Entry>) -> io::Result<Option<Cursor>> {
        if self.error.is_some() {
            return Err(invalid("Minecraft Wiki catalog API reported an error"));
        }
        if self.query.allpages.len() > MAX_ENTRIES - entries.len() {
            return Err(invalid("Minecraft Wiki catalog exceeds its title limit"));
        }
        for page in self.query.allpages {
            if page.ns != 0
                || page.title.len() > MAX_TITLE_BYTES
                || page
                    .title
                    .trim_matches(|ch: char| ch == '_' || ch.is_whitespace())
                    .is_empty()
                || page.title.chars().any(char::is_control)
            {
                return Err(invalid("invalid Minecraft Wiki catalog entry"));
            }
            entries.push(Entry {
                id: page.pageid.get(),
                title: page.title,
            });
        }
        Ok(self.cursor)
    }
}

async fn download(api: &Api, edition: WikiEdition) -> io::Result<Vec<Entry>> {
    let mut entries = Vec::new();
    let mut cursors = HashSet::new();
    let mut cursor: Option<Cursor> = None;
    for _ in 0..MAX_BATCHES {
        // Redirect pages have their own IDs and are also valid local title matches.
        let mut query = vec![
            ("action", "query"),
            ("list", "allpages"),
            ("apnamespace", "0"),
            ("aplimit", "max"),
            ("apfilterredir", "all"),
            ("maxlag", "5"),
        ];
        if let Some(next) = &cursor {
            query.extend([
                ("apcontinue", next.apcontinue.as_str()),
                ("continue", next.general.as_str()),
            ]);
        }
        let batch = download_batch(api, edition, &query).await?;
        let Some(next) = batch.append(&mut entries)? else {
            return if entries.is_empty() {
                Err(invalid("empty Minecraft Wiki catalog"))
            } else {
                Ok(entries)
            };
        };
        if [&next.apcontinue, &next.general]
            .into_iter()
            .any(|value| value.is_empty() || value.len() > MAX_CURSOR_BYTES)
            || !cursors.insert(next.apcontinue.clone())
        {
            return Err(invalid("invalid Minecraft Wiki catalog continuation"));
        }
        cursor = Some(next);
    }
    Err(invalid("Minecraft Wiki catalog exceeds its batch limit"))
}

async fn download_batch(
    api: &Api,
    edition: WikiEdition,
    query: &[(&str, &str)],
) -> io::Result<Batch> {
    for delay in [Duration::from_secs(5), Duration::from_secs(10)] {
        match api.get(edition, query, MAX_BATCH_BYTES).await {
            Err(error)
                if matches!(
                    error.kind(),
                    io::ErrorKind::TimedOut
                        | io::ErrorKind::ConnectionAborted
                        | io::ErrorKind::WouldBlock
                ) =>
            {
                tokio::time::sleep(delay).await;
            }
            result => return result,
        }
    }
    api.get(edition, query, MAX_BATCH_BYTES).await
}

struct TitleIndex {
    digest: [u8; 32],
    reader: IndexReader,
    persistent: Option<Index>,
    id: Field,
    title: Field,
    key: Field,
}

impl TitleIndex {
    fn build(entries: &[Entry], digest: [u8; 32], workers: usize) -> io::Result<Self> {
        let (schema, fields) = Self::layout();
        let index = Index::create_in_ram(schema);
        Self::populate(&index, fields, entries, workers, None)?;
        Self::open(index, fields, digest, false)
    }

    fn restore(path: &Path) -> io::Result<Option<Snapshot>> {
        let age = disk::age(path)?;
        if age >= MAX_AGE {
            return Ok(None);
        }
        let index = Index::open_in_dir(path).map_err(index_error)?;
        let (schema, fields) = Self::layout();
        if index.schema() != schema {
            return Err(invalid("incompatible Minecraft Wiki cache schema"));
        }
        let Some(payload) = index.load_metas().map_err(index_error)?.payload else {
            return Ok(None);
        };
        let mut digest = [0; 32];
        hex::decode_to_slice(payload, &mut digest).map_err(io::Error::other)?;
        let refreshed = Instant::now()
            .checked_sub(age)
            .ok_or_else(|| invalid("invalid Wiki cache age"))?;
        Ok(Some(Snapshot {
            refreshed,
            index: Arc::new(Self::open(index, fields, digest, true)?),
        }))
    }

    fn build_persistent(
        path: &Path,
        previous: Option<&Index>,
        entries: &[Entry],
        digest: [u8; 32],
        workers: usize,
    ) -> io::Result<Self> {
        let (schema, fields) = Self::layout();
        let index = if let Some(previous) = previous {
            previous.clone()
        } else {
            disk::ensure_private_dir(path)?;
            let directory = MmapDirectory::open(path).map_err(io::Error::other)?;
            Index::open_or_create(directory, schema).map_err(index_error)?
        };
        Self::populate(&index, fields, entries, workers, Some(digest))?;
        Self::open(index, fields, digest, true)
    }

    fn layout() -> (Schema, (Field, Field, Field)) {
        let mut schema = Schema::builder();
        let id = schema.add_u64_field("id", STORED);
        let title = schema.add_text_field(
            "title",
            TextOptions::default().set_stored().set_indexing_options(
                TextFieldIndexing::default()
                    .set_tokenizer("icu")
                    .set_index_option(IndexRecordOption::WithFreqsAndPositions),
            ),
        );
        let key = schema.add_text_field("key", STRING);
        (schema.build(), (id, title, key))
    }

    fn populate(
        index: &Index,
        (id, title, key): (Field, Field, Field),
        entries: &[Entry],
        workers: usize,
        persistent_digest: Option<[u8; 32]>,
    ) -> io::Result<()> {
        index.tokenizers().register("icu", IcuTokenizer);
        let mut writer = index
            .writer_with_options::<TantivyDocument>(
                IndexWriterOptions::builder()
                    .num_worker_threads(workers)
                    .num_merge_threads(1)
                    .memory_budget_per_thread(INDEX_MEMORY)
                    .build(),
            )
            .map_err(index_error)?;
        if persistent_digest.is_some() {
            writer.delete_all_documents().map_err(index_error)?;
        }
        for entry in entries {
            let normalized = title_key(&entry.title);
            let mut document = TantivyDocument::with_capacity(
                entry.title.len() + normalized.len() + TITLE_DOCUMENT_OVERHEAD,
            );
            document.add_u64(id, entry.id);
            document.add_text(title, entry.title.as_str());
            document.add_text(key, normalized);
            writer.add_document(document).map_err(index_error)?;
        }
        if let Some(digest) = persistent_digest {
            let mut commit = writer.prepare_commit().map_err(index_error)?;
            commit.set_payload(&hex::encode(digest));
            commit.commit().map_err(index_error)?;
            writer.wait_merging_threads().map_err(index_error)
        } else {
            writer.commit().map_err(index_error)?;
            Ok(())
        }
    }

    fn open(
        index: Index,
        (id, title, key): (Field, Field, Field),
        digest: [u8; 32],
        persistent: bool,
    ) -> io::Result<Self> {
        let reader = index
            .reader_builder()
            .reload_policy(ReloadPolicy::Manual)
            .try_into()
            .map_err(index_error)?;
        Ok(Self {
            digest,
            reader,
            persistent: persistent.then_some(index),
            id,
            title,
            key,
        })
    }

    fn exact(&self, title: &str) -> io::Result<Vec<Entry>> {
        if title.len() > MAX_QUERY_BYTES {
            return Ok(Vec::new());
        }
        self.collect(&self.key_query(&title_key(title)))
    }

    fn search(&self, terms: &str) -> io::Result<Vec<Entry>> {
        if terms.len() > MAX_QUERY_BYTES {
            return Ok(Vec::new());
        }
        let key = title_key(terms);
        let mut tokenizer = IcuTokenizer;
        let mut stream = tokenizer.token_stream(&key);
        let mut terms = Vec::new();
        while terms.len() < MAX_TERMS && stream.advance() {
            terms.push(Term::from_field_text(self.title, &stream.token().text));
        }
        let mut queries: Vec<Box<dyn Query>> = vec![Box::new(BoostQuery::new(
            Box::new(self.key_query(&key)),
            8.0,
        ))];
        if terms.len() > 1 {
            queries.push(Box::new(BoostQuery::new(
                Box::new(PhraseQuery::new(terms.clone())),
                2.0,
            )));
        }
        terms.sort_unstable();
        terms.dedup();
        queries.extend(terms.into_iter().map(|term| {
            Box::new(TermQuery::new(term, IndexRecordOption::WithFreqs)) as Box<dyn Query>
        }));
        self.collect(&BooleanQuery::union(queries))
    }

    fn key_query(&self, key: &str) -> TermQuery {
        TermQuery::new(
            Term::from_field_text(self.key, key),
            IndexRecordOption::Basic,
        )
    }

    fn collect(&self, query: &dyn Query) -> io::Result<Vec<Entry>> {
        let searcher = self.reader.searcher();
        searcher
            .search(query, &TopDocs::with_limit(MAX_MATCHES).order_by_score())
            .map_err(index_error)?
            .into_iter()
            .map(|(_, address)| {
                let doc = searcher
                    .doc::<TantivyDocument>(address)
                    .map_err(index_error)?;
                let id = doc
                    .get_first(self.id)
                    .and_then(|value| value.as_u64())
                    .filter(|&id| id != 0);
                let title = doc.get_first(self.title).and_then(|value| value.as_str());
                let (Some(id), Some(title)) = (id, title) else {
                    return Err(invalid("invalid stored Minecraft Wiki catalog entry"));
                };
                Ok(Entry {
                    id,
                    title: title.to_owned(),
                })
            })
            .collect()
    }
}

pub(super) fn title_key(title: &str) -> String {
    // The registered editions use first-letter titles, not case-insensitive titles.
    if title.is_ascii() {
        return normalize_key(title.chars(), title.len());
    }
    normalize_key(title.nfc(), title.len())
}

fn normalize_key(chars: impl Iterator<Item = char>, capacity: usize) -> String {
    let mut key = String::with_capacity(capacity);
    let mut space = false;
    for ch in chars {
        if ch == '_' || ch.is_whitespace() {
            space = !key.is_empty();
        } else if key.is_empty() {
            key.extend(ch.to_uppercase());
        } else {
            if space {
                key.push(' ');
                space = false;
            }
            key.push(ch);
        }
    }
    key
}

fn invalid(message: &'static str) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidData, message)
}

fn state_error() -> io::Error {
    io::Error::other("Minecraft Wiki catalog state is unavailable")
}

fn index_error(_: tantivy::TantivyError) -> io::Error {
    io::Error::other("Minecraft Wiki title index failed")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_short_batch_can_still_have_more_pages() {
        let batch: Batch = serde_json::from_str(
            r#"{"continue":{"apcontinue":"Redstone","continue":"-||"},
                "query":{"allpages":[{"pageid":42,"ns":0,"title":"Comparator"}]}}"#,
        )
        .unwrap();
        let mut entries = Vec::new();
        let cursor = batch.append(&mut entries).unwrap().unwrap();
        assert_eq!(cursor.apcontinue, "Redstone");
        assert_eq!(cursor.general, "-||");
        assert_eq!(entries[0].id, 42);

        for invalid in [
            r#"{"continue":null,"query":{"allpages":[]}}"#,
            r#"{"continue":{"continue":"-||"},"query":{"allpages":[]}}"#,
            r#"{"query":{"allpages":[{"pageid":0,"ns":0,"title":"Invalid"}]}}"#,
        ] {
            assert!(serde_json::from_str::<Batch>(invalid).is_err());
        }
        for invalid in [
            r#"{"error":{"code":"maxlag"},"query":{"allpages":[]}}"#,
            r#"{"query":{"allpages":[{"pageid":99,"ns":1,"title":"Talk"}]}}"#,
        ] {
            let batch: Batch = serde_json::from_str(invalid).unwrap();
            assert!(batch.append(&mut entries).is_err());
        }
    }

    #[tokio::test]
    async fn title_search_favors_the_topic_and_exact_lookup_preserves_title_case() {
        let make_entries = || {
            [
                (1, "Redstone"),
                (2, "Redstone Comparator"),
                (3, "Comparator (redstone)"),
                (4, "红石"),
                (5, "红石比较器"),
                (6, "Redstone comparator"),
                (7, "Café"),
            ]
            .into_iter()
            .map(|(id, title)| Entry {
                id,
                title: title.into(),
            })
            .collect::<Vec<_>>()
        };
        let builder = CatalogBuilder::new(2);
        let index = TitleIndex::build(&make_entries(), [0; 32], 1).unwrap();
        let (parallel, other) = tokio::join!(
            builder.build(WikiEdition::ENGLISH, make_entries(), None),
            builder.build(
                WikiEdition::CHINESE,
                vec![Entry {
                    id: 8,
                    title: "剣".into(),
                }],
                None,
            ),
        );
        let parallel = parallel.unwrap();
        assert_eq!(other.unwrap().exact("剣").unwrap()[0].id, 8);
        for query in [
            "Redstone Comparator",
            "redstone__Comparator",
            "红石比较器 配方",
            "cafe\u{301}",
        ] {
            assert_eq!(
                index.search(query).unwrap(),
                parallel.search(query).unwrap()
            );
            assert_eq!(index.exact(query).unwrap(), parallel.exact(query).unwrap());
        }
        assert_eq!(index.search("Redstone Comparator").unwrap()[0].id, 2);
        assert!(matches!(
            index.search("REDSTONE COMPARATOR").unwrap()[0].id,
            2 | 6
        ));
        assert_eq!(index.search("红石比较器 配方").unwrap()[0].id, 5);
        let exact = index.exact("redstone__Comparator").unwrap();
        assert_eq!(exact.len(), 1);
        assert_eq!(exact[0].id, 2);
        assert_eq!(index.exact("cafe\u{301}").unwrap()[0].id, 7);
        assert!(index.exact("redstone comparison").unwrap().is_empty());
    }

    #[tokio::test]
    async fn identical_entries_reuse_the_index_but_changed_titles_or_ids_rebuild() {
        let builder = CatalogBuilder::new(2);
        let entries = |id, title: &str| {
            vec![Entry {
                id,
                title: title.into(),
            }]
        };
        let current = builder
            .build(WikiEdition::ENGLISH, entries(42, "Café"), None)
            .await
            .unwrap();
        let same = builder
            .build(
                WikiEdition::ENGLISH,
                entries(42, "Café"),
                Some(current.clone()),
            )
            .await
            .unwrap();
        assert!(Arc::ptr_eq(&current, &same));

        for (id, title) in [(42, "Étoile"), (43, "Café")] {
            let updated = builder
                .build(
                    WikiEdition::ENGLISH,
                    entries(id, title),
                    Some(current.clone()),
                )
                .await
                .unwrap();
            assert!(!Arc::ptr_eq(&current, &updated));
            assert_eq!(updated.exact(title).unwrap()[0].id, id);
        }
        assert_eq!(current.exact("Café").unwrap()[0].id, 42);
    }

    #[tokio::test]
    async fn disk_catalog_reopens_without_download_and_old_readers_survive_replacement() {
        let temp = tempfile::tempdir().unwrap();
        let cache_builder = || {
            let mut builder = CatalogBuilder::new(2);
            builder.disk = Some(Arc::new(DiskCache::new(temp.path().join("catalogs"))));
            builder
        };
        let edition = WikiEdition::ENGLISH;
        let entries = |id, title: &str| {
            vec![Entry {
                id,
                title: title.into(),
            }]
        };
        {
            let builder = cache_builder();
            let first = builder
                .build(edition, entries(42, "Café"), None)
                .await
                .unwrap();
            assert!(first.persistent.is_some());
        }
        let builder = cache_builder();
        let catalog = Catalog::default();
        catalog
            .refresh(&Api::new().unwrap(), edition, &Semaphore::new(0), &builder)
            .await
            .unwrap();
        let original = catalog.index().unwrap().unwrap();
        assert_eq!(original.exact("Café").unwrap()[0].id, 42);
        let same = builder
            .build(edition, entries(42, "Café"), Some(original.clone()))
            .await
            .unwrap();
        assert!(Arc::ptr_eq(&original, &same));
        let replacement = builder
            .build(edition, entries(43, "Étoile"), Some(original.clone()))
            .await
            .unwrap();
        assert!(replacement.persistent.is_some());
        assert!(replacement.exact("Café").unwrap().is_empty());
        assert_eq!(original.exact("Café").unwrap()[0].id, 42);
        assert_eq!(
            builder
                .restore(edition)
                .await
                .unwrap()
                .index
                .exact("Étoile")
                .unwrap()[0]
                .id,
            43
        );
    }

    #[test]
    fn large_catalogs_can_use_spare_cores_without_blocking_other_builds() {
        let slots = Arc::new(Semaphore::new(4));
        let small = reserve_index_workers(&slots, slots.clone().try_acquire_owned().unwrap(), 100);
        let large =
            reserve_index_workers(&slots, slots.clone().try_acquire_owned().unwrap(), 80_000);
        assert_eq!(small.num_permits(), 1);
        assert_eq!(large.num_permits(), 3);
        drop(small);
        drop(large);
        let alone =
            reserve_index_workers(&slots, slots.clone().try_acquire_owned().unwrap(), 80_000);
        assert_eq!(alone.num_permits(), 4);
    }

    #[tokio::test]
    async fn stale_catalog_remains_searchable_when_refresh_fails() {
        let temp = tempfile::tempdir().unwrap();
        let disk = Arc::new(DiskCache::new(temp.path().join("catalogs")));
        let mut builder = CatalogBuilder::new(1);
        builder.disk = Some(disk.clone());
        let edition = WikiEdition::ENGLISH;
        let index = builder
            .build(
                edition,
                vec![Entry {
                    id: 42,
                    title: "Lantern".into(),
                }],
                None,
            )
            .await
            .unwrap();
        assert!(index.persistent.is_some());
        let manifest = disk.path(edition).unwrap().join("meta.json");
        let downloads = Semaphore::new(0);
        downloads.close();
        let catalog = Catalog::default();
        let api = Api::new().unwrap();
        let manifest = std::fs::File::open(manifest).unwrap();
        manifest
            .set_modified(std::time::SystemTime::now() - REFRESH_INTERVAL - Duration::from_hours(1))
            .unwrap();

        assert!(
            catalog
                .refresh(&api, edition, &downloads, &builder)
                .await
                .is_err()
        );
        assert_eq!(catalog.exact("Lantern").unwrap().unwrap()[0].id, 42);

        manifest
            .set_modified(std::time::SystemTime::now() - MAX_AGE - Duration::from_hours(1))
            .unwrap();
        assert!(builder.restore(edition).await.is_none());
    }

    #[tokio::test]
    async fn corrupt_disk_catalog_falls_back_to_ram_and_can_be_persisted_again() {
        let temp = tempfile::tempdir().unwrap();
        let disk = Arc::new(DiskCache::new(temp.path().join("catalogs")));
        let mut builder = CatalogBuilder::new(1);
        builder.disk = Some(disk.clone());
        let edition = WikiEdition::ENGLISH;
        let entries = || {
            vec![Entry {
                id: 42,
                title: "Lantern".into(),
            }]
        };
        let index = builder.build(edition, entries(), None).await.unwrap();
        assert!(index.persistent.is_some());
        drop(index);
        let manifest = disk.path(edition).unwrap().join("meta.json");
        let valid_manifest = std::fs::read(&manifest).unwrap();
        std::fs::write(&manifest, b"broken manifest").unwrap();

        assert!(builder.restore(edition).await.is_none());
        let index = builder.build(edition, entries(), None).await.unwrap();
        assert!(index.persistent.is_none());
        assert_eq!(index.exact("Lantern").unwrap()[0].id, 42);

        std::fs::write(&manifest, valid_manifest).unwrap();
        let restored = builder
            .build(edition, entries(), Some(index))
            .await
            .unwrap();
        assert!(restored.persistent.is_some());
    }

    #[tokio::test]
    #[ignore = "manual CPU-dependent catalog benchmark"]
    async fn benchmark_catalog_build_workers() {
        for parallelism in [1, available_parallelism().unwrap().get()] {
            let builder = CatalogBuilder::new(parallelism);
            let entries = (1..=80_000)
                .map(|id| {
                    let topic = match id % 5 {
                        0 => "Redstone Comparator",
                        1 => "红石比较器",
                        2 => "ダイヤモンド",
                        3 => "Rezept Gegenstände",
                        _ => "Café et Potions",
                    };
                    Entry {
                        id,
                        title: format!("{topic} {id}"),
                    }
                })
                .collect();
            let start = Instant::now();
            let index = builder
                .build(WikiEdition::ENGLISH, entries, None)
                .await
                .unwrap();
            println!(
                "budget={parallelism} titles=80000 elapsed={:?}",
                start.elapsed()
            );
            assert_eq!(
                index.exact("Redstone Comparator 80000").unwrap()[0].id,
                80_000
            );
        }
    }
}
