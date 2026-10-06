package org.encinet.mik.module.ai.knowledge.adapter.markdown;

import org.encinet.mik.module.ai.knowledge.model.KnowledgeDocument;
import org.encinet.mik.module.ai.knowledge.model.KnowledgeScope;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Canonical, path-confined Markdown repository with revisions and recoverable archives. */
public final class MarkdownKnowledgeRepository {
    private final Path root;
    private final Path publicRoot;
    private final Path usersRoot;
    private final Path inboxRoot;
    private final Path archiveRoot;
    private final Path revisionsRoot;
    private final int maximumFileBytes;
    private final MarkdownKnowledgeCodec codec;
    private final Map<String, Path> locations = new LinkedHashMap<>();

    public MarkdownKnowledgeRepository(Path pluginDataDirectory, int maximumFileBytes) {
        this(pluginDataDirectory, maximumFileBytes, new MarkdownKnowledgeCodec());
    }

    MarkdownKnowledgeRepository(
            Path pluginDataDirectory,
            int maximumFileBytes,
            MarkdownKnowledgeCodec codec
    ) {
        if (maximumFileBytes < 4_096) {
            throw new IllegalArgumentException("maximumFileBytes must be at least 4096");
        }
        Path data = Objects.requireNonNull(pluginDataDirectory, "pluginDataDirectory")
                .toAbsolutePath().normalize();
        root = data.resolve("knowledge").normalize();
        publicRoot = root.resolve("public");
        usersRoot = root.resolve("users");
        inboxRoot = root.resolve(".inbox");
        archiveRoot = root.resolve(".archive");
        revisionsRoot = root.resolve(".revisions");
        this.maximumFileBytes = maximumFileBytes;
        this.codec = Objects.requireNonNull(codec, "codec");
        requireInside(data, root);
    }

    public synchronized void initialize() {
        try {
            Files.createDirectories(publicRoot);
            Files.createDirectories(usersRoot);
            Files.createDirectories(inboxRoot.resolve("public"));
            Files.createDirectories(inboxRoot.resolve("users"));
            Files.createDirectories(archiveRoot);
            Files.createDirectories(revisionsRoot);
        } catch (IOException error) {
            throw new KnowledgeRepositoryException("Could not initialize knowledge storage", error);
        }
    }

    public synchronized List<KnowledgeDocument> scan() {
        initialize();
        LinkedHashMap<String, Path> discovered = new LinkedHashMap<>();
        List<KnowledgeDocument> result = new ArrayList<>();
        scanDirectory(publicRoot, KnowledgeScope.PUBLIC, Optional.empty(), discovered, result);
        try (var directories = Files.list(usersRoot)) {
            for (Path directory : directories.sorted().toList()) {
                if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(directory)) {
                    continue;
                }
                UUID owner;
                try {
                    owner = UUID.fromString(directory.getFileName().toString());
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                scanDirectory(directory, KnowledgeScope.USER, Optional.of(owner),
                        discovered, result);
            }
        } catch (IOException error) {
            throw new KnowledgeRepositoryException("Could not scan user knowledge", error);
        }
        locations.clear();
        locations.putAll(discovered);
        return List.copyOf(result);
    }

    public synchronized Optional<KnowledgeDocument> findPublic(String id) {
        return find(KnowledgeScope.PUBLIC, Optional.empty(), id);
    }

    public synchronized Optional<KnowledgeDocument> findUser(UUID owner, String id) {
        return find(KnowledgeScope.USER, Optional.of(Objects.requireNonNull(owner)), id);
    }

    public synchronized List<KnowledgeDocument> listUser(UUID owner) {
        UUID checked = Objects.requireNonNull(owner, "owner");
        return scan().stream()
                .filter(value -> value.scope() == KnowledgeScope.USER)
                .filter(value -> value.owner().filter(checked::equals).isPresent())
                .toList();
    }

    public synchronized Optional<KnowledgeDocument> openPublic(URI uri) {
        Objects.requireNonNull(uri, "uri");
        if (!"knowledge".equalsIgnoreCase(uri.getScheme())
                || !"public".equalsIgnoreCase(uri.getHost())) {
            return Optional.empty();
        }
        String path = Objects.requireNonNullElse(uri.getRawPath(), "");
        if (!path.startsWith("/") || path.length() < 2 || path.substring(1).contains("/")) {
            return Optional.empty();
        }
        String id = URLDecoder.decode(path.substring(1), StandardCharsets.UTF_8);
        return findPublic(id);
    }

    public synchronized KnowledgeDocument save(
            KnowledgeDocument requested,
            long expectedRevision
    ) {
        return saveInternal(requested, expectedRevision, false);
    }

    public synchronized KnowledgeDocument setProtected(
            KnowledgeScope scope,
            Optional<UUID> owner,
            String id,
            boolean protectedDocument
    ) {
        KnowledgeDocument existing = find(scope, owner, id)
                .orElseThrow(() -> new KnowledgeRepositoryException(
                        "Unknown knowledge document: " + id));
        KnowledgeDocument requested = copy(existing, existing.body(), protectedDocument,
                existing.revision(), existing.createdAt(), Instant.now());
        return saveInternal(requested, existing.revision(), true);
    }

    public synchronized KnowledgeDocument rollback(
            KnowledgeScope scope,
            Optional<UUID> owner,
            String id,
            long revision
    ) {
        KnowledgeDocument current = find(scope, owner, id)
                .orElseThrow(() -> new KnowledgeRepositoryException(
                        "Unknown knowledge document: " + id));
        Path snapshot = revisionDirectory(current).resolve(revision + ".md");
        if (!Files.isRegularFile(snapshot, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(snapshot)) {
            throw new KnowledgeRepositoryException("Unknown knowledge revision: " + revision);
        }
        KnowledgeDocument previous = read(snapshot, scope, owner, snapshot.getFileName());
        KnowledgeDocument requested = new KnowledgeDocument(
                current.id(), current.scope(), current.owner(), previous.title(),
                previous.language(), previous.aliases(), previous.tags(), previous.kind(),
                previous.sources(), previous.protectedDocument(), current.revision(),
                current.createdAt(), Instant.now(), previous.expiresAt(), previous.body());
        return saveInternal(requested, current.revision(), true);
    }

    public synchronized void archive(
            KnowledgeScope scope,
            Optional<UUID> owner,
            String id,
            long expectedRevision
    ) {
        KnowledgeDocument current = find(scope, owner, id)
                .orElseThrow(() -> new KnowledgeRepositoryException(
                        "Unknown knowledge document: " + id));
        if (current.protectedDocument()) {
            throw new KnowledgeRepositoryException("Knowledge document is protected: " + id);
        }
        if (current.revision() != expectedRevision) {
            throw new KnowledgeRepositoryException("Knowledge document changed concurrently: " + id);
        }
        Path source = locations.get(current.subjectKey());
        snapshot(source, current);
        Path scopeDirectory = current.scope() == KnowledgeScope.PUBLIC
                ? archiveRoot.resolve("public")
                : archiveRoot.resolve("users").resolve(current.owner().orElseThrow().toString());
        Path target = safeResolve(scopeDirectory,
                current.id() + '-' + DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                        .replace(':', '-') + ".md");
        try {
            Files.createDirectories(target.getParent());
            moveAtomically(source, target);
            locations.remove(current.subjectKey());
        } catch (IOException error) {
            throw new KnowledgeRepositoryException("Could not archive knowledge " + id, error);
        }
    }

    /** Permanently removes private memory after an explicit owner-management command. */
    public synchronized List<KnowledgeDocument> forgetUser(
            UUID owner,
            Optional<String> documentId
    ) {
        UUID checkedOwner = Objects.requireNonNull(owner, "owner");
        Optional<String> checkedId = (documentId == null ? Optional.<String>empty() : documentId)
                .map(String::strip).filter(value -> !value.isEmpty());
        List<KnowledgeDocument> selected = listUser(checkedOwner).stream()
                .filter(value -> checkedId.map(id -> value.id().equalsIgnoreCase(id))
                        .orElse(true)).toList();
        for (KnowledgeDocument document : selected) {
            Path path = locations.remove(document.subjectKey());
            try {
                if (path != null) {
                    Files.deleteIfExists(path);
                }
                deleteTree(revisionDirectory(document));
                deleteArchivedPrivate(checkedOwner, document.id());
            } catch (IOException error) {
                throw new KnowledgeRepositoryException(
                        "Could not forget private memory " + document.id(), error);
            }
        }
        if (checkedId.isEmpty()) {
            try {
                deleteTree(inboxRoot.resolve("users").resolve(checkedOwner.toString()));
                deleteTree(revisionsRoot.resolve("users").resolve(checkedOwner.toString()));
                deleteTree(archiveRoot.resolve("users").resolve(checkedOwner.toString()));
            } catch (IOException error) {
                throw new KnowledgeRepositoryException(
                        "Could not forget all private memory artifacts", error);
            }
        }
        return selected;
    }

    public Path inboxRoot() {
        return inboxRoot;
    }

    public Path root() {
        return root;
    }

    public synchronized CandidateEntry enqueueCandidate(KnowledgeDocument candidate) {
        Objects.requireNonNull(candidate, "candidate");
        Path directory = candidate.scope() == KnowledgeScope.PUBLIC
                ? inboxRoot.resolve("public")
                : inboxRoot.resolve("users").resolve(
                candidate.owner().orElseThrow().toString());
        Path target = safeResolve(directory, candidate.id() + ".md");
        writeAtomically(target, codec.render(candidate));
        return new CandidateEntry(target, candidate);
    }

    public synchronized List<CandidateEntry> candidates(int limit) {
        if (limit < 1) {
            return List.of();
        }
        initialize();
        List<CandidateEntry> result = new ArrayList<>();
        collectCandidates(inboxRoot.resolve("public"), KnowledgeScope.PUBLIC,
                Optional.empty(), result, limit);
        Path userInbox = inboxRoot.resolve("users");
        try (var directories = Files.list(userInbox)) {
            for (Path directory : directories.sorted().toList()) {
                if (result.size() >= limit) {
                    break;
                }
                if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(directory)) {
                    continue;
                }
                try {
                    collectCandidates(directory, KnowledgeScope.USER,
                            Optional.of(UUID.fromString(directory.getFileName().toString())),
                            result, limit);
                } catch (IllegalArgumentException ignored) {
                    // Ignore unknown private inbox directories.
                }
            }
        } catch (IOException error) {
            throw new KnowledgeRepositoryException("Could not list knowledge candidates", error);
        }
        return List.copyOf(result);
    }

    public synchronized int candidateCount() {
        try (var files = Files.walk(inboxRoot)) {
                    return Math.toIntExact(files.filter(path -> Files.isRegularFile(
                            path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString()
                            .toLowerCase(Locale.ROOT).endsWith(".md"))
                    .count());
        } catch (IOException error) {
            throw new KnowledgeRepositoryException("Could not count knowledge candidates", error);
        }
    }

    public synchronized void completeCandidate(CandidateEntry candidate) {
        Objects.requireNonNull(candidate, "candidate");
        Path path = candidate.path().toAbsolutePath().normalize();
        requireInside(inboxRoot, path);
        try {
            Files.deleteIfExists(path);
        } catch (IOException error) {
            throw new KnowledgeRepositoryException("Could not remove processed candidate", error);
        }
    }

    /** Moves a repeatedly failing standalone candidate out of the active queue for inspection. */
    public synchronized void rejectCandidate(CandidateEntry candidate) {
        Objects.requireNonNull(candidate, "candidate");
        Path source = candidate.path().toAbsolutePath().normalize();
        requireInside(inboxRoot, source);
        KnowledgeDocument document = candidate.document();
        Path directory = document.scope() == KnowledgeScope.PUBLIC
                ? archiveRoot.resolve("candidates").resolve("public")
                : archiveRoot.resolve("users").resolve(
                document.owner().orElseThrow().toString()).resolve("candidates");
        Path target = safeResolve(directory, document.id() + '-'
                + DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(':', '-') + ".md");
        try {
            Files.createDirectories(directory);
            moveAtomically(source, target);
        } catch (IOException error) {
            throw new KnowledgeRepositoryException(
                    "Could not quarantine knowledge candidate " + document.id(), error);
        }
    }

    private KnowledgeDocument saveInternal(
            KnowledgeDocument requested,
            long expectedRevision,
            boolean managementOverride
    ) {
        Optional<KnowledgeDocument> current = find(
                requested.scope(), requested.owner(), requested.id());
        if (current.isPresent()) {
            KnowledgeDocument existing = current.orElseThrow();
            if (existing.revision() != expectedRevision) {
                throw new KnowledgeRepositoryException(
                        "Knowledge document changed concurrently: " + requested.id());
            }
            if (existing.protectedDocument() && !managementOverride) {
                throw new KnowledgeRepositoryException(
                        "Knowledge document is protected: " + requested.id());
            }
        } else if (expectedRevision != 0) {
            throw new KnowledgeRepositoryException(
                    "Knowledge document does not exist at revision " + expectedRevision);
        }

        Instant now = Instant.now();
        long revision = current.map(value -> value.revision() + 1).orElse(1L);
        Instant created = current.map(KnowledgeDocument::createdAt).orElse(now);
        KnowledgeDocument stored = new KnowledgeDocument(
                requested.id(), requested.scope(), requested.owner(), requested.title(),
                requested.language(), requested.aliases(), requested.tags(), requested.kind(),
                requested.sources(), requested.protectedDocument(), revision, created, now,
                requested.expiresAt(), requested.body());
        Path target = current.map(value -> locations.get(value.subjectKey()))
                .orElseGet(() -> defaultPath(stored));
        current.ifPresent(existing -> snapshot(target, existing));
        writeAtomically(target, codec.render(stored));
        locations.put(stored.subjectKey(), target);
        return stored;
    }

    private Optional<KnowledgeDocument> find(
            KnowledgeScope scope,
            Optional<UUID> owner,
            String id
    ) {
        String key = scope == KnowledgeScope.PUBLIC
                ? "public:" + id.toLowerCase(Locale.ROOT)
                : "user:" + owner.orElseThrow() + ':' + id.toLowerCase(Locale.ROOT);
        Path path = locations.get(key);
        if (path == null) {
            scan();
            path = locations.get(key);
        }
        return path == null ? Optional.empty()
                : Optional.of(read(path, scope, owner, path.getFileName()));
    }

    private void scanDirectory(
            Path directory,
            KnowledgeScope scope,
            Optional<UUID> owner,
            Map<String, Path> discovered,
            List<KnowledgeDocument> documents
    ) {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.filter(value -> Files.isRegularFile(
                            value, LinkOption.NOFOLLOW_LINKS))
                    .filter(value -> value.getFileName().toString()
                            .toLowerCase(Locale.ROOT).endsWith(".md"))
                    .sorted(Comparator.comparing(Path::toString)).toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new KnowledgeRepositoryException(
                            "Symbolic links are not allowed in knowledge storage: " + path);
                }
                Path relative = directory.relativize(path);
                KnowledgeDocument document = read(path, scope, owner, relative);
                Path previous = discovered.putIfAbsent(document.subjectKey(), path);
                if (previous != null) {
                    throw new KnowledgeRepositoryException("Duplicate knowledge id "
                            + document.id() + " in " + previous + " and " + path);
                }
                documents.add(document);
            }
        } catch (IOException error) {
            throw new KnowledgeRepositoryException("Could not scan knowledge directory "
                    + directory, error);
        }
    }

    private void collectCandidates(
            Path directory,
            KnowledgeScope scope,
            Optional<UUID> owner,
            List<CandidateEntry> result,
            int limit
    ) {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(value -> Files.isRegularFile(
                            value, LinkOption.NOFOLLOW_LINKS))
                    .filter(value -> value.getFileName().toString()
                            .toLowerCase(Locale.ROOT).endsWith(".md"))
                    .sorted().toList()) {
                if (result.size() >= limit) {
                    return;
                }
                if (Files.isSymbolicLink(path)) {
                    continue;
                }
                result.add(new CandidateEntry(path,
                        read(path, scope, owner, path.getFileName())));
            }
        } catch (IOException error) {
            throw new KnowledgeRepositoryException(
                    "Could not read knowledge candidate directory", error);
        }
    }

    private KnowledgeDocument read(
            Path path,
            KnowledgeScope scope,
            Optional<UUID> owner,
            Path relative
    ) {
        Path normalized = path.toAbsolutePath().normalize();
        requireInside(root, normalized);
        try {
            long size = Files.size(normalized);
            if (size > maximumFileBytes) {
                throw new KnowledgeRepositoryException(
                        "Knowledge file exceeds maximum size: " + relative);
            }
            FileTime modified = Files.getLastModifiedTime(normalized, LinkOption.NOFOLLOW_LINKS);
            return codec.parse(relative, scope, owner,
                    Files.readString(normalized, StandardCharsets.UTF_8),
                    modified.toInstant());
        } catch (IOException error) {
            throw new KnowledgeRepositoryException("Could not read knowledge " + relative, error);
        }
    }

    private Path defaultPath(KnowledgeDocument document) {
        Path directory = document.scope() == KnowledgeScope.PUBLIC
                ? publicRoot : usersRoot.resolve(document.owner().orElseThrow().toString());
        return safeResolve(directory, document.id() + ".md");
    }

    private Path revisionDirectory(KnowledgeDocument document) {
        Path directory = document.scope() == KnowledgeScope.PUBLIC
                ? revisionsRoot.resolve("public")
                : revisionsRoot.resolve("users").resolve(
                document.owner().orElseThrow().toString());
        return safeResolve(directory, document.id());
    }

    private void snapshot(Path source, KnowledgeDocument document) {
        Path directory = revisionDirectory(document);
        Path target = safeResolve(directory, document.revision() + ".md");
        try {
            Files.createDirectories(directory);
            if (!Files.exists(target)) {
                Files.copy(source, target);
            }
        } catch (IOException error) {
            throw new KnowledgeRepositoryException(
                    "Could not save knowledge revision " + document.id(), error);
        }
    }

    private void writeAtomically(Path target, String content) {
        requireInside(root, target);
        if (content.getBytes(StandardCharsets.UTF_8).length > maximumFileBytes) {
            throw new KnowledgeRepositoryException("Knowledge document exceeds maximum size");
        }
        try {
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), ".knowledge-", ".tmp");
            try {
                Files.writeString(temporary, content, StandardCharsets.UTF_8);
                moveAtomically(temporary, target);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException error) {
            throw new KnowledgeRepositoryException("Could not write knowledge " + target, error);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteArchivedPrivate(UUID owner, String id) throws IOException {
        Path directory = archiveRoot.resolve("users").resolve(owner.toString()).normalize();
        requireInside(archiveRoot, directory);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.filter(value -> value.getFileName().toString()
                    .startsWith(id + '-')).toList()) {
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isSymbolicLink(path)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private void deleteTree(Path directory) throws IOException {
        Path normalized = directory.toAbsolutePath().normalize();
        requireInside(root, normalized);
        if (!Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(normalized)) {
            Files.delete(normalized);
            return;
        }
        try (var paths = Files.walk(normalized)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (Files.isSymbolicLink(path)
                        || Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        || Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static Path safeResolve(Path directory, String child) {
        Path result = directory.resolve(child).toAbsolutePath().normalize();
        requireInside(directory.toAbsolutePath().normalize(), result);
        return result;
    }

    private static void requireInside(Path root, Path target) {
        if (!target.startsWith(root)) {
            throw new KnowledgeRepositoryException("Knowledge path escapes its storage root");
        }
    }

    private static KnowledgeDocument copy(
            KnowledgeDocument source,
            String body,
            boolean protectedDocument,
            long revision,
            Instant created,
            Instant updated
    ) {
        return new KnowledgeDocument(source.id(), source.scope(), source.owner(),
                source.title(), source.language(), source.aliases(), source.tags(),
                source.kind(), source.sources(), protectedDocument, revision, created,
                updated, source.expiresAt(), body);
    }

    public record CandidateEntry(Path path, KnowledgeDocument document) {
        public CandidateEntry {
            path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
            document = Objects.requireNonNull(document, "document");
        }
    }
}
