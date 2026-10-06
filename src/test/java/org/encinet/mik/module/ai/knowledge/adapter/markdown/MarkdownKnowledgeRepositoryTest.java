package org.encinet.mik.module.ai.knowledge.adapter.markdown;

import org.encinet.mik.module.ai.knowledge.model.KnowledgeDocument;
import org.encinet.mik.module.ai.knowledge.model.KnowledgeScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownKnowledgeRepositoryTest {
    @TempDir
    Path directory;

    @Test
    void appliesRevisionChecksProtectionRollbackAndPrivateForgetting() {
        MarkdownKnowledgeRepository repository =
                new MarkdownKnowledgeRepository(directory, 1_000_000);
        KnowledgeDocument first = document("guide", KnowledgeScope.PUBLIC,
                null, "# Guide\n\nFirst version.");

        KnowledgeDocument revisionOne = repository.save(first, 0);
        KnowledgeDocument revisionTwo = repository.save(copy(
                revisionOne, "# Guide\n\nSecond version."), 1);
        assertEquals(2, revisionTwo.revision());

        KnowledgeDocument protectedDocument = repository.setProtected(
                KnowledgeScope.PUBLIC, Optional.empty(), "guide", true);
        assertTrue(protectedDocument.protectedDocument());
        assertThrows(KnowledgeRepositoryException.class, () -> repository.save(
                copy(protectedDocument, "# Guide\n\nForbidden edit."),
                protectedDocument.revision()));

        KnowledgeDocument unprotected = repository.setProtected(
                KnowledgeScope.PUBLIC, Optional.empty(), "guide", false);
        KnowledgeDocument restored = repository.rollback(KnowledgeScope.PUBLIC,
                Optional.empty(), "guide", 1);
        assertEquals(unprotected.revision() + 1, restored.revision());
        assertTrue(restored.body().contains("First version"));

        UUID owner = UUID.randomUUID();
        repository.save(document("preference", KnowledgeScope.USER, owner,
                "User prefers concise answers."), 0);
        assertEquals(1, repository.listUser(owner).size());
        assertEquals(1, repository.forgetUser(owner, Optional.empty()).size());
        assertFalse(repository.findUser(owner, "preference").isPresent());

        var rejected = repository.enqueueCandidate(document(
                "candidate-private", KnowledgeScope.USER, owner,
                "A standalone private candidate."));
        repository.rejectCandidate(rejected);
        Path privateArchive = directory.resolve("knowledge/.archive/users")
                .resolve(owner.toString());
        assertTrue(Files.isDirectory(privateArchive));
        repository.forgetUser(owner, Optional.empty());
        assertFalse(Files.exists(privateArchive));
    }

    private static KnowledgeDocument document(
            String id,
            KnowledgeScope scope,
            UUID owner,
            String body
    ) {
        return new KnowledgeDocument(id, scope, Optional.ofNullable(owner), id, "en_us",
                List.of(), List.of(), "note", List.of(), false, 1,
                Instant.EPOCH, Instant.EPOCH, Optional.empty(), body);
    }

    private static KnowledgeDocument copy(KnowledgeDocument source, String body) {
        return new KnowledgeDocument(source.id(), source.scope(), source.owner(),
                source.title(), source.language(), source.aliases(), source.tags(),
                source.kind(), source.sources(), source.protectedDocument(),
                source.revision(), source.createdAt(), source.updatedAt(),
                source.expiresAt(), body);
    }
}
