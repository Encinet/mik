mod mik_wiki;
mod minecraft;
mod tokenizer;
mod web;

pub(super) use mik_wiki::Target as MikWikiTarget;
pub(super) use mik_wiki::{MikWiki, MikWikiLocale};
pub(super) use minecraft::{ArticleRef, MinecraftWiki, WikiEdition};
pub(super) use web::{SearchError, Tavily, WebQuery};

use super::reference::CITATION_URL_LIMIT;
use reqwest::Url;
use serde::Serialize;
use std::{
    fs::{self, File},
    io::{self, Read},
    path::Path,
};
use tantivy::{
    Index, IndexReader, IndexWriter, ReloadPolicy, TantivyDocument, Term,
    collector::TopDocs,
    doc,
    query::{BooleanQuery, Query, TermQuery},
    schema::{
        Field, IndexRecordOption, STORED, STRING, Schema, TextFieldIndexing, TextOptions, Value,
    },
    tokenizer::{TokenStream, Tokenizer},
};
use text_splitter::{MarkdownSplitter, TextSplitter};
use tokenizer::IcuTokenizer;
use walkdir::WalkDir;

const MAX_BYTES_PER_FILE: u64 = 256 * 1024;
const MAX_CORPUS_BYTES: usize = 16 * 1024 * 1024;
const MAX_FILES: usize = 1024;
const CHUNK_SIZE: usize = 900;
const MAX_EXCERPTS: usize = 4;

/// Click targets are canonical public Wiki pages produced by the knowledge tools, not arbitrary
/// links from an answer or a Wiki excerpt. This check never performs a request.
pub(super) fn is_wiki_citation(raw: &str) -> bool {
    if raw.len() > CITATION_URL_LIMIT || !raw.is_ascii() || raw.contains('\\') {
        return false;
    }
    let Ok(url) = Url::parse(raw) else {
        return false;
    };
    if url.as_str() != raw
        || url.scheme() != "https"
        || !url.username().is_empty()
        || url.password().is_some()
        || url.port().is_some()
    {
        return false;
    }
    match url.host_str() {
        Some("mcmik.top") => mik_wiki::is_section_citation(&url),
        _ => minecraft::is_article_citation(raw),
    }
}

pub(super) struct Knowledge {
    local: Option<KnowledgeIndex>,
    mik_wiki: MikWiki,
}

struct KnowledgeIndex {
    reader: IndexReader,
    fields: Fields,
}

pub(super) struct Matches {
    pub(super) excerpts: Vec<Excerpt>,
    pub(super) wiki_status: LookupStatus,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq, Serialize)]
#[serde(rename_all = "snake_case")]
pub(super) enum LookupStatus {
    Found,
    NoMatch,
    Unavailable,
    AccessUnavailable,
}

impl LookupStatus {
    pub(super) const fn search(found: bool, all_available: bool) -> Self {
        if found {
            Self::Found
        } else if all_available {
            Self::NoMatch
        } else {
            Self::Unavailable
        }
    }
}

#[derive(Serialize)]
pub(super) struct Excerpt {
    pub(super) source: String,
    pub(super) text: String,
    #[serde(skip_serializing_if = "String::is_empty")]
    pub(super) wiki_url: String,
}

#[derive(Clone, Copy)]
struct Fields {
    source: Field,
    body: Field,
    wiki_url: Field,
    wiki_target: Field,
}

struct Builder {
    index: Index,
    writer: IndexWriter<TantivyDocument>,
    fields: Fields,
}

impl Knowledge {
    pub(super) fn open(root: Option<&Path>, mik_wiki: MikWiki) -> io::Result<Self> {
        let local = match root.map(fs::symlink_metadata).transpose() {
            Ok(Some(_)) => root.map(KnowledgeIndex::local).transpose()?,
            Ok(None) => None,
            Err(error) if error.kind() == io::ErrorKind::NotFound => None,
            Err(error) => return Err(error),
        };
        Ok(Self { local, mik_wiki })
    }

    pub(super) async fn refresh_wiki(&self) {
        self.mik_wiki.refresh().await;
    }

    pub(super) fn search(&self, locale: MikWikiLocale, query: &str) -> tantivy::Result<Matches> {
        if let Some(target) = mik_wiki::Target::parse(query, locale) {
            let snapshot = self.mik_wiki.cached(target.locale);
            let excerpts = match &snapshot {
                Some(index) => index.read_wiki(&target.key)?,
                None => Vec::new(),
            };
            return Ok(Matches {
                wiki_status: LookupStatus::search(!excerpts.is_empty(), snapshot.is_some()),
                excerpts,
            });
        }

        let mut all_available = true;
        let mut excerpts = Vec::new();
        for locale in [locale, locale.other()] {
            if let Some(index) = self.mik_wiki.cached(locale) {
                excerpts = index.search(query)?;
                if !excerpts.is_empty() {
                    break;
                }
            } else {
                all_available = false;
            }
        }
        let wiki_status = LookupStatus::search(!excerpts.is_empty(), all_available);
        if let Some(index) = &self.local
            && let Ok(local) = index.search(query)
            && !local.is_empty()
        {
            // Leave one slot for server-provided supplements. They do not prove an official rule.
            excerpts.truncate(MAX_EXCERPTS - 1);
            excerpts.extend(local.into_iter().take(MAX_EXCERPTS - excerpts.len()));
        }
        Ok(Matches {
            excerpts,
            wiki_status,
        })
    }
}

impl KnowledgeIndex {
    fn local(root: &Path) -> io::Result<Self> {
        if !root.is_absolute() || !root.is_dir() {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                format!("{} must be an absolute knowledge directory", root.display()),
            ));
        }
        let builder = Builder::new()?;
        let Fields { source, body, .. } = builder.fields;
        let markdown = MarkdownSplitter::new(CHUNK_SIZE);
        let text = TextSplitter::new(CHUNK_SIZE);
        let mut file_count = 0;
        let mut total_bytes = 0;
        for entry in WalkDir::new(root).follow_links(false).sort_by_file_name() {
            let entry = entry.map_err(io::Error::other)?;
            let extension = entry.path().extension().and_then(|value| value.to_str());
            if !entry.file_type().is_file() || !matches!(extension, Some("md" | "txt")) {
                continue;
            }
            let mut contents = String::new();
            File::open(entry.path())?
                .take(MAX_BYTES_PER_FILE + 1)
                .read_to_string(&mut contents)?;
            file_count += 1;
            total_bytes += contents.len();
            if contents.len() as u64 > MAX_BYTES_PER_FILE
                || total_bytes > MAX_CORPUS_BYTES
                || file_count > MAX_FILES
            {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidData,
                    "knowledge source limits exceeded",
                ));
            }
            let relative = entry
                .path()
                .strip_prefix(root)
                .map_err(io::Error::other)?
                .with_extension("");
            let name = relative.to_string_lossy();
            let chunks: Box<dyn Iterator<Item = &str>> = if extension == Some("md") {
                Box::new(markdown.chunks(&contents))
            } else {
                Box::new(text.chunks(&contents))
            };
            for chunk in chunks {
                builder.add(doc!(source => name.as_ref(), body => chunk))?;
            }
        }
        builder.finish()
    }

    fn search(&self, query: &str) -> tantivy::Result<Vec<Excerpt>> {
        let mut tokenizer = IcuTokenizer;
        let mut tokens = tokenizer.token_stream(query);
        let mut terms = Vec::new();
        while terms.len() < 24 && tokens.advance() {
            terms.push(Term::from_field_text(
                self.fields.body,
                &tokens.token().text,
            ));
        }
        if terms.is_empty() {
            return Ok(Vec::new());
        }
        terms.sort_unstable();
        terms.dedup();
        let query = BooleanQuery::union(
            terms
                .into_iter()
                .map(|term| {
                    Box::new(TermQuery::new(term, IndexRecordOption::WithFreqs)) as Box<dyn Query>
                })
                .collect(),
        );
        self.collect(&query)
    }

    fn read_wiki(&self, target: &str) -> tantivy::Result<Vec<Excerpt>> {
        self.collect(&TermQuery::new(
            Term::from_field_text(self.fields.wiki_target, target),
            IndexRecordOption::Basic,
        ))
    }

    fn collect(&self, query: &dyn Query) -> tantivy::Result<Vec<Excerpt>> {
        let searcher = self.reader.searcher();
        searcher
            .search(query, &TopDocs::with_limit(MAX_EXCERPTS).order_by_score())?
            .into_iter()
            .map(|(_, id)| {
                let doc = searcher.doc::<TantivyDocument>(id)?;
                let field = |field| {
                    doc.get_first(field)
                        .and_then(|value| value.as_str())
                        .unwrap_or_default()
                        .to_owned()
                };
                Ok(Excerpt {
                    source: field(self.fields.source),
                    text: field(self.fields.body),
                    wiki_url: field(self.fields.wiki_url),
                })
            })
            .collect()
    }
}

impl Builder {
    fn new() -> io::Result<Self> {
        let mut schema = Schema::builder();
        let fields = Fields {
            source: schema.add_text_field("source", STORED),
            body: schema.add_text_field(
                "body",
                TextOptions::default().set_stored().set_indexing_options(
                    TextFieldIndexing::default()
                        .set_tokenizer("icu")
                        .set_index_option(IndexRecordOption::WithFreqs),
                ),
            ),
            wiki_url: schema.add_text_field("wiki_url", STORED),
            wiki_target: schema.add_text_field("wiki_target", STRING),
        };
        let index = Index::create_in_ram(schema.build());
        index.tokenizers().register("icu", IcuTokenizer);
        let writer = index
            .writer_with_num_threads::<TantivyDocument>(1, 24 * 1024 * 1024)
            .map_err(io::Error::other)?;
        Ok(Self {
            index,
            writer,
            fields,
        })
    }

    fn add(&self, document: TantivyDocument) -> io::Result<()> {
        self.writer
            .add_document(document)
            .map_err(io::Error::other)?;
        Ok(())
    }

    fn finish(mut self) -> io::Result<KnowledgeIndex> {
        self.writer.commit().map_err(io::Error::other)?;
        Ok(KnowledgeIndex {
            reader: self
                .index
                .reader_builder()
                .reload_policy(ReloadPolicy::Manual)
                .try_into()
                .map_err(io::Error::other)?,
            fields: self.fields,
        })
    }
}
