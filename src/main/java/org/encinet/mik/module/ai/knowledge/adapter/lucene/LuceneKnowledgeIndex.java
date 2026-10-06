package org.encinet.mik.module.ai.knowledge.adapter.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.encinet.mik.module.ai.knowledge.adapter.markdown.MarkdownChunker;
import org.encinet.mik.module.ai.knowledge.model.KnowledgeChunk;
import org.encinet.mik.module.ai.knowledge.model.KnowledgeDocument;
import org.encinet.mik.module.ai.knowledge.model.KnowledgeHit;
import org.encinet.mik.module.ai.knowledge.model.KnowledgeScope;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Separate disk-backed public and private BM25 indexes with request-time owner filtering. */
public final class LuceneKnowledgeIndex implements AutoCloseable {
    private static final String FIELD_KEY = "document_key";
    private static final String FIELD_DOCUMENT_ID = "document_id";
    private static final String FIELD_OWNER = "owner";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_ALIASES = "aliases";
    private static final String FIELD_TAGS = "tags";
    private static final String FIELD_TAG_EXACT = "tag_exact";
    private static final String FIELD_HEADING = "heading";
    private static final String FIELD_BODY = "body";
    private static final String FIELD_LANGUAGE = "language";
    private static final String FIELD_URI = "uri";
    private static final String TAG_SEPARATOR = "\u001f";
    private static final int RRF_CONSTANT = 60;
    private static final String CURRENT_FILE = "CURRENT";

    private final Analyzer analyzer = new IcuKnowledgeAnalyzer();
    private final MarkdownChunker chunker;
    private final Path root;
    private IndexStore publicStore;
    private IndexStore privateStore;
    private String currentGeneration;

    public LuceneKnowledgeIndex(Path pluginDataDirectory, MarkdownChunker chunker) {
        this.chunker = Objects.requireNonNull(chunker, "chunker");
        root = Objects.requireNonNull(pluginDataDirectory, "pluginDataDirectory")
                .toAbsolutePath().normalize().resolve("cache").resolve("ai-knowledge");
        try {
            Files.createDirectories(root.resolve("generations"));
            String selected = readCurrentGeneration().orElse(null);
            if (selected != null) {
                try {
                    openGeneration(selected);
                    return;
                } catch (IOException | RuntimeException ignored) {
                    closeStoresQuietly(publicStore, privateStore);
                    publicStore = null;
                    privateStore = null;
                }
            }
            createEmptyGeneration();
        } catch (IOException error) {
            throw new IllegalStateException("Could not open knowledge indexes", error);
        }
    }

    private Optional<String> readCurrentGeneration() throws IOException {
        Path pointer = root.resolve(CURRENT_FILE);
        if (!Files.isRegularFile(pointer, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(pointer)) {
            return Optional.empty();
        }
        String generation = Files.readString(pointer, StandardCharsets.UTF_8).strip();
        if (!generation.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            return Optional.empty();
        }
        Path selected = generationPath(generation);
        return Files.isDirectory(selected.resolve("public"), LinkOption.NOFOLLOW_LINKS)
                && Files.isDirectory(selected.resolve("private"), LinkOption.NOFOLLOW_LINKS)
                ? Optional.of(generation) : Optional.empty();
    }

    private void openGeneration(String generation) throws IOException {
        Path selected = generationPath(generation);
        publicStore = new IndexStore(selected.resolve("public"));
        privateStore = new IndexStore(selected.resolve("private"));
        currentGeneration = generation;
    }

    private void createEmptyGeneration() throws IOException {
        String generation = UUID.randomUUID().toString();
        Path selected = generationPath(generation);
        IndexStore createdPublic = null;
        IndexStore createdPrivate = null;
        try {
            createdPublic = new IndexStore(selected.resolve("public"));
            createdPrivate = new IndexStore(selected.resolve("private"));
            commitAndRefresh(createdPublic);
            commitAndRefresh(createdPrivate);
            writeCurrentGeneration(generation);
            publicStore = createdPublic;
            privateStore = createdPrivate;
            currentGeneration = generation;
        } catch (IOException | RuntimeException error) {
            closeStoresQuietly(createdPublic, createdPrivate);
            deleteGenerationQuietly(selected);
            throw error;
        }
    }

    private Path generationPath(String generation) {
        Path generations = root.resolve("generations").toAbsolutePath().normalize();
        Path selected = generations.resolve(generation).normalize();
        if (!selected.startsWith(generations)) {
            throw new IllegalArgumentException("Knowledge index generation escapes its root");
        }
        return selected;
    }

    private void writeCurrentGeneration(String generation) throws IOException {
        Path temporary = Files.createTempFile(root, ".current-", ".tmp");
        try {
            Files.writeString(temporary, generation + '\n', StandardCharsets.UTF_8);
            try {
                Files.move(temporary, root.resolve(CURRENT_FILE),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, root.resolve(CURRENT_FILE),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public synchronized void replaceAll(List<KnowledgeDocument> documents) {
        Objects.requireNonNull(documents, "documents");
        String nextGeneration = UUID.randomUUID().toString();
        Path nextRoot = generationPath(nextGeneration);
        IndexStore nextPublic = null;
        IndexStore nextPrivate = null;
        try {
            nextPublic = new IndexStore(nextRoot.resolve("public"));
            nextPrivate = new IndexStore(nextRoot.resolve("private"));
            Instant now = Instant.now();
            for (KnowledgeDocument document : documents) {
                if (!document.expiredAt(now)) {
                    (document.scope() == KnowledgeScope.PUBLIC
                            ? nextPublic : nextPrivate).add(document);
                }
            }
            commitAndRefresh(nextPublic);
            commitAndRefresh(nextPrivate);
            writeCurrentGeneration(nextGeneration);
        } catch (IOException | RuntimeException error) {
            closeStoresQuietly(nextPublic, nextPrivate);
            deleteGenerationQuietly(nextRoot);
            throw new IllegalStateException("Could not rebuild knowledge indexes", error);
        }

        IndexStore oldPublic = publicStore;
        IndexStore oldPrivate = privateStore;
        String oldGeneration = currentGeneration;
        publicStore = nextPublic;
        privateStore = nextPrivate;
        currentGeneration = nextGeneration;
        closeStoresQuietly(oldPublic, oldPrivate);
        if (oldGeneration != null && !oldGeneration.equals(nextGeneration)) {
            deleteGenerationQuietly(generationPath(oldGeneration));
        }
        pruneUnusedGenerations();
    }

    public synchronized void update(KnowledgeDocument document) {
        Objects.requireNonNull(document, "document");
        IndexStore store = store(document);
        try {
            store.writer.deleteDocuments(new Term(FIELD_KEY, document.subjectKey()));
            if (!document.expiredAt(Instant.now())) {
                store.add(document);
            }
            commitAndRefresh(store);
        } catch (IOException error) {
            throw new IllegalStateException("Could not update knowledge index", error);
        }
    }

    public synchronized void remove(KnowledgeDocument document) {
        IndexStore store = store(Objects.requireNonNull(document));
        try {
            store.writer.deleteDocuments(new Term(FIELD_KEY, document.subjectKey()));
            commitAndRefresh(store);
        } catch (IOException error) {
            throw new IllegalStateException("Could not remove knowledge from index", error);
        }
    }

    public List<KnowledgeHit> searchPublic(
            List<String> queries,
            List<String> tags,
            int limit
    ) {
        return search(publicStore, Optional.empty(), queries, tags, limit);
    }

    public List<KnowledgeHit> searchUser(
            UUID owner,
            List<String> queries,
            int limit
    ) {
        return search(privateStore, Optional.of(Objects.requireNonNull(owner)),
                queries, List.of(), limit);
    }

    public synchronized int publicChunkCount() {
        return publicStore.writer.getDocStats().numDocs;
    }

    public synchronized int privateChunkCount() {
        return privateStore.writer.getDocStats().numDocs;
    }

    private synchronized List<KnowledgeHit> search(
            IndexStore store,
            Optional<UUID> owner,
            List<String> rawQueries,
            List<String> rawTags,
            int limit
    ) {
        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException("Knowledge result limit must be between 1 and 50");
        }
        List<String> queries = normalized(rawQueries, 4, 512);
        if (queries.isEmpty()) {
            return List.of();
        }
        List<String> tags = normalized(rawTags, 8, 64).stream()
                .map(value -> value.toLowerCase(Locale.ROOT)).toList();
        Map<String, RankedHit> fused = new HashMap<>();
        try {
            store.searchers.maybeRefresh();
            IndexSearcher searcher = store.searchers.acquire();
            try {
                searcher.setSimilarity(new BM25Similarity());
                int perQuery = Math.max(20, limit * 4);
                for (String raw : queries) {
                    Query query = query(raw, owner, tags);
                    TopDocs found = searcher.search(query, perQuery);
                    for (int rank = 0; rank < found.scoreDocs.length; rank++) {
                        ScoreDoc scoreDoc = found.scoreDocs[rank];
                        Document stored = searcher.storedFields().document(scoreDoc.doc);
                        String uri = stored.get(FIELD_URI);
                        double contribution = 1.0d / (RRF_CONSTANT + rank + 1.0d);
                        RankedHit existing = fused.get(uri);
                        if (existing == null) {
                            fused.put(uri, new RankedHit(hit(stored, raw, contribution),
                                    contribution));
                        } else {
                            fused.put(uri, new RankedHit(existing.hit(),
                                    existing.score() + contribution));
                        }
                    }
                }
            } finally {
                store.searchers.release(searcher);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Could not search knowledge index", error);
        }
        return fused.values().stream()
                .sorted(Comparator.comparingDouble(RankedHit::score).reversed()
                        .thenComparing(value -> value.hit().uri().toString()))
                .limit(limit)
                .map(value -> new KnowledgeHit(value.hit().documentId(), value.hit().title(),
                        value.hit().language(), value.hit().tags(), value.hit().heading(),
                        value.hit().excerpt(), value.hit().uri(), value.score()))
                .toList();
    }

    private Query query(String raw, Optional<UUID> owner, List<String> tags)
            throws IOException {
        List<String> tokens = tokens(raw);
        BooleanQuery.Builder root = new BooleanQuery.Builder();
        BooleanQuery.Builder text = new BooleanQuery.Builder();
        for (String token : tokens) {
            BooleanQuery.Builder fields = new BooleanQuery.Builder();
            fields.add(boost(FIELD_TITLE, token, 4f), BooleanClause.Occur.SHOULD);
            fields.add(boost(FIELD_ALIASES, token, 3f), BooleanClause.Occur.SHOULD);
            fields.add(boost(FIELD_HEADING, token, 2f), BooleanClause.Occur.SHOULD);
            fields.add(boost(FIELD_TAGS, token, 2f), BooleanClause.Occur.SHOULD);
            fields.add(boost(FIELD_BODY, token, 1f), BooleanClause.Occur.SHOULD);
            fields.setMinimumNumberShouldMatch(1);
            text.add(fields.build(), BooleanClause.Occur.MUST);
        }
        root.add(text.build(), BooleanClause.Occur.MUST);
        owner.ifPresent(value -> root.add(new TermQuery(
                new Term(FIELD_OWNER, value.toString())), BooleanClause.Occur.FILTER));
        for (String tag : tags) {
            root.add(new TermQuery(new Term(FIELD_TAG_EXACT, tag)),
                    BooleanClause.Occur.FILTER);
        }
        return root.build();
    }

    private static Query boost(String field, String token, float factor) {
        return new BoostQuery(new TermQuery(new Term(field, token)), factor);
    }

    private List<String> tokens(String value) throws IOException {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        try (TokenStream stream = analyzer.tokenStream(FIELD_BODY, value)) {
            CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken() && result.size() < 32) {
                if (!term.toString().isBlank()) {
                    result.add(term.toString());
                }
            }
            stream.end();
        }
        return List.copyOf(result);
    }

    private KnowledgeHit hit(Document document, String query, double score) {
        return new KnowledgeHit(
                document.get(FIELD_DOCUMENT_ID),
                document.get(FIELD_TITLE),
                document.get(FIELD_LANGUAGE),
                splitTags(document.get(FIELD_TAGS)),
                Objects.requireNonNullElse(document.get(FIELD_HEADING), ""),
                excerpt(document.get(FIELD_BODY), query, 900),
                URI.create(document.get(FIELD_URI)),
                score);
    }

    private IndexStore store(KnowledgeDocument document) {
        return document.scope() == KnowledgeScope.PUBLIC ? publicStore : privateStore;
    }

    private void commitAndRefresh(IndexStore store) throws IOException {
        store.writer.commit();
        store.searchers.maybeRefreshBlocking();
    }

    private static List<String> normalized(List<String> values, int maximum, int maxCharacters) {
        return Objects.requireNonNullElse(values, List.<String>of()).stream()
                .filter(Objects::nonNull).map(String::strip).filter(value -> !value.isEmpty())
                .map(value -> value.length() <= maxCharacters
                        ? value : value.substring(0, maxCharacters))
                .distinct().limit(maximum).toList();
    }

    private static String excerpt(String text, String query, int maximum) {
        String source = Objects.requireNonNullElse(text, "").strip();
        if (source.length() <= maximum) {
            return source;
        }
        String needle = query.strip().toLowerCase(Locale.ROOT);
        int match = needle.isEmpty() ? -1 : source.toLowerCase(Locale.ROOT).indexOf(needle);
        int start = match < 0 ? 0 : Math.max(0, match - maximum / 3);
        int end = Math.min(source.length(), start + maximum);
        if (end - start < maximum && start > 0) {
            start = Math.max(0, end - maximum);
        }
        return (start > 0 ? "…" : "") + source.substring(start, end).strip()
                + (end < source.length() ? "…" : "");
    }

    private static List<String> splitTags(String value) {
        String tags = Objects.requireNonNullElse(value, "");
        return tags.isEmpty() ? List.of() : List.of(tags.split(TAG_SEPARATOR, -1));
    }

    private void pruneUnusedGenerations() {
        Path generations = root.resolve("generations").toAbsolutePath().normalize();
        try (var paths = Files.list(generations)) {
            for (Path path : paths.toList()) {
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isSymbolicLink(path)
                        && !path.getFileName().toString().equals(currentGeneration)) {
                    deleteGenerationQuietly(path);
                }
            }
        } catch (IOException ignored) {
            // Old generations are disposable. A later successful rebuild retries cleanup.
        }
    }

    private void deleteGenerationQuietly(Path generation) {
        Path generations = root.resolve("generations").toAbsolutePath().normalize();
        Path selected = generation.toAbsolutePath().normalize();
        if (!selected.startsWith(generations) || selected.equals(generations)) {
            return;
        }
        try {
            if (!Files.exists(selected, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            try (var paths = Files.walk(selected)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        } catch (IOException ignored) {
            // Cache cleanup is best effort and never affects the active generation.
        }
    }

    private static void closeStoresQuietly(IndexStore... stores) {
        for (IndexStore store : stores) {
            if (store == null) {
                continue;
            }
            try {
                store.close();
            } catch (IOException ignored) {
                // Used only while recovering or replacing disposable index generations.
            }
        }
    }

    @Override
    public synchronized void close() {
        RuntimeException failure = null;
        try {
            publicStore.close();
        } catch (IOException error) {
            failure = new IllegalStateException("Could not close public knowledge index", error);
        }
        try {
            privateStore.close();
        } catch (IOException error) {
            if (failure == null) {
                failure = new IllegalStateException("Could not close private knowledge index", error);
            } else {
                failure.addSuppressed(error);
            }
        }
        analyzer.close();
        if (failure != null) {
            throw failure;
        }
    }

    private final class IndexStore implements AutoCloseable {
        private final Directory directory;
        private final IndexWriter writer;
        private final SearcherManager searchers;

        private IndexStore(Path path) throws IOException {
            directory = FSDirectory.open(path);
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setSimilarity(new BM25Similarity());
            writer = new IndexWriter(directory, config);
            searchers = new SearcherManager(writer, new SearcherFactory());
        }

        private void add(KnowledgeDocument document) throws IOException {
            for (KnowledgeChunk chunk : chunker.chunk(document)) {
                Document indexed = new Document();
                indexed.add(new StringField(FIELD_KEY, chunk.documentKey(), Field.Store.NO));
                indexed.add(new StringField(FIELD_DOCUMENT_ID,
                        chunk.documentId(), Field.Store.YES));
                chunk.owner().ifPresent(owner -> indexed.add(new StringField(
                        FIELD_OWNER, owner.toString(), Field.Store.NO)));
                indexed.add(new TextField(FIELD_TITLE, chunk.title(), Field.Store.YES));
                indexed.add(new TextField(FIELD_ALIASES,
                        String.join("\n", chunk.aliases()), Field.Store.NO));
                indexed.add(new TextField(FIELD_TAGS,
                        String.join(TAG_SEPARATOR, chunk.tags()), Field.Store.YES));
                for (String tag : chunk.tags()) {
                    indexed.add(new StringField(FIELD_TAG_EXACT,
                            tag.toLowerCase(Locale.ROOT), Field.Store.NO));
                }
                indexed.add(new TextField(FIELD_HEADING, chunk.heading(), Field.Store.YES));
                indexed.add(new TextField(FIELD_BODY, chunk.text(), Field.Store.YES));
                indexed.add(new StringField(FIELD_LANGUAGE,
                        chunk.language(), Field.Store.YES));
                indexed.add(new StoredField(FIELD_URI, chunk.uri().toASCIIString()));
                writer.addDocument(indexed);
            }
        }

        @Override
        public void close() throws IOException {
            searchers.close();
            writer.close();
            directory.close();
        }
    }

    private record RankedHit(KnowledgeHit hit, double score) {
    }
}
