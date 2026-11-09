package org.encinet.mik.feature.player.profile.nametag;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;

import static org.encinet.mik.feature.player.profile.nametag.NameTagField.*;
import static org.encinet.mik.feature.player.profile.nametag.NameTagTestData.*;
import static org.junit.jupiter.api.Assertions.*;

class NameTagStoreTest {
    @Test void copyingBothFieldsChecksAllVersionsBeforeReplacingAnything() {
        var prefix = decoration(PREFIX, "Old prefix");
        var suffix = decoration(SUFFIX, "Old suffix");
        var external = decoration(SUFFIX, "External suffix");
        var values = new ArrayList<>(List.of(prefix, external));
        var changes = Map.of(PREFIX, new NameTagStore.Change(Set.of(prefix), "New prefix"),
                SUFFIX, new NameTagStore.Change(Set.of(suffix), "New suffix"));
        var replacements = Map.of(PREFIX, decoration(PREFIX, "New prefix"), SUFFIX, decoration(SUFFIX, "New suffix"));
        assertThrows(NameTagStore.Conflict.class, () -> NameTagStore.replace(nodes(values), changes, replacements));
        assertEquals(List.of(prefix, external), values, "a suffix conflict must not leave the copied prefix applied");
    }

    @Test void aCompleteStyleIsPersistedOnceAndClearsAMissingSourceFieldWithoutTouchingOtherMetadata() throws Exception {
        var prefix = decoration(PREFIX, "Old prefix");
        var suffix = decoration(SUFFIX, "Old suffix");
        var external = decoration(SUFFIX, "Inherited fallback", 100, false, false, true);
        var backend = new Backend(List.of(prefix, suffix, external));
        try (var _ = provider(backend.api); var store = new NameTagStore(backend.api)) {
            var changes = new java.util.EnumMap<NameTagField, NameTagStore.Change>(NameTagField.class);
            changes.put(PREFIX, new NameTagStore.Change(Set.of(prefix), "<blue>Copied "));
            changes.put(SUFFIX, new NameTagStore.Change(Set.of(suffix), ""));
            var save = store.save(backend.playerId, changes);
            changes.clear();
            assertEquals(1, backend.pending.size());
            var pending = backend.pending.getFirst();
            pending.mutation().accept(backend.user);
            pending.saved().complete(null);
            save.join();
            var result = store.read(backend.playerId).orElseThrow();
            assertEquals("<blue>Copied ", customValue(result, PREFIX));
            assertEquals("", customValue(result, SUFFIX));
            assertTrue(backend.values.contains(external));
            assertFalse(backend.values.contains(prefix));
            assertFalse(backend.values.contains(suffix));
        }
    }

    @Test void savingANonEmptySuffixReadsItBackAndNeverReplacesThePrefixOrOtherSuffixNodes() throws Exception {
        var prefix = decoration(PREFIX, "<gold>Prefix ");
        var suffix = decoration(SUFFIX, " <blue>Old suffix");
        var inherited = decoration(SUFFIX, "Role suffix", 100, false, false, true);
        var contextual = decoration(SUFFIX, "World suffix", 10_000, true, false, true);
        var backend = new Backend(List.of(prefix, suffix, inherited, contextual));
        try (var _ = provider(backend.api); var store = new NameTagStore(backend.api)) {
            var current = store.read(backend.playerId).orElseThrow();
            String raw = " <gradient:green:blue>New suffix</gradient> ";
            var save = save(store, backend.playerId, SUFFIX, current.version(SUFFIX), raw);
            assertFalse(save.isDone());
            var request = backend.pending.getFirst();
            request.mutation().accept(backend.user);
            request.saved().complete(null);
            save.join();
            var saved = store.read(backend.playerId).orElseThrow();
            assertEquals(raw, customValue(saved, SUFFIX));
            assertEquals(Set.of(prefix), saved.version(PREFIX));
            assertEquals(1, saved.version(SUFFIX).size());
            assertTrue(backend.values.containsAll(List.of(prefix, inherited, contextual)));
            assertFalse(backend.values.contains(suffix));
        }
    }
    @Test void pendingPersistencePreventsDuplicateWritesAndReleasesTheOwnerAfterFailure() {
        var prefix = decoration(PREFIX, "Prefix");
        var suffix = decoration(SUFFIX, "Suffix");
        var backend = new Backend(List.of(prefix, suffix));
        try (var store = new NameTagStore(backend.api)) {
            var snapshot = store.read(backend.playerId).orElseThrow();
            var save = save(store, backend.playerId, PREFIX, snapshot.version(PREFIX), "");
            assertFalse(save.isDone());
            assertEquals(List.of(prefix, suffix), backend.values, "typing and accepting a request do not run persistence inline");
            assertThrows(CompletionException.class, () -> save(store, backend.playerId, SUFFIX, snapshot.version(SUFFIX), "").join());
            assertEquals(1, backend.pending.size());
            var request = backend.pending.getFirst();
            request.mutation().accept(backend.user);
            assertEquals(List.of(suffix), backend.values);
            request.saved().completeExceptionally(new IllegalStateException("storage unavailable"));
            assertThrows(CompletionException.class, save::join);
            save(store, backend.playerId, SUFFIX, snapshot.version(SUFFIX), "");
            assertEquals(2, backend.pending.size(), "a failed save cannot leave the owner locked forever");
        }
    }

    @Test void disablingBeforeTheLoadedUserCallbackPreventsALateMutation() {
        var prefix = decoration(PREFIX, "Prefix");
        var backend = new Backend(List.of(prefix));
        var store = new NameTagStore(backend.api);
        var baseline = store.read(backend.playerId).orElseThrow();
        var save = save(store, backend.playerId, PREFIX, baseline.version(PREFIX), "");
        store.close();
        var request = backend.pending.getFirst();
        var error = assertThrows(IllegalStateException.class, () -> request.mutation().accept(backend.user));
        request.saved().completeExceptionally(error);
        assertThrows(CompletionException.class, save::join);
        assertEquals(List.of(prefix), backend.values);
        assertTrue(store.read(backend.playerId).isEmpty());
        assertThrows(CompletionException.class, () -> save(store, backend.playerId, PREFIX, baseline.version(PREFIX), "").join());
        assertEquals(1, backend.pending.size());
    }

    @Test void onlyPermanentPositiveUnscopedCustomNodesAreCopiedOrReplaced() {
        var prefix = decoration(PREFIX, "<red>Prefix");
        var suffix = decoration(SUFFIX, "Suffix");
        var role = decoration(PREFIX, "Role", 100, false, false, true);
        var context = decoration(PREFIX, "World", 10_000, true, false, true);
        var temporary = decoration(PREFIX, "Timed", 10_000, false, true, true);
        var negated = decoration(PREFIX, "Negative", 10_000, false, false, false);
        var values = new ArrayList<>(List.of(prefix, suffix, role, context, temporary, negated));
        var current = NameTagStore.snapshot(values);
        assertEquals(Set.of(prefix, suffix), current.nodes());
        assertEquals("<red>Prefix", customValue(current, PREFIX));
        var replacement = decoration(PREFIX, "<blue>New");
        var data = nodes(values);
        replace(data, PREFIX, current.version(PREFIX), replacement);
        assertEquals(Set.of(suffix, role, context, temporary, negated, replacement), Set.copyOf(values));
        replace(data, SUFFIX, current.version(SUFFIX), null);
        assertEquals(Set.of(role, context, temporary, negated, replacement), Set.copyOf(values));
    }

    @Test void versionIncludesDuplicatePriorityValuesEvenWhenTheChosenTextIsUnchanged() {
        var first = decoration(PREFIX, "A");
        var duplicatePriority = decoration(PREFIX, "B");
        var values = new ArrayList<>(List.of(first, duplicatePriority));
        var before = NameTagStore.snapshot(values);
        values.remove(duplicatePriority);
        var now = NameTagStore.snapshot(values);
        assertEquals(customValue(before, PREFIX), customValue(now, PREFIX));
        assertThrows(NameTagStore.Conflict.class, () -> replace(nodes(values), PREFIX,
                before.version(PREFIX), decoration(PREFIX, "Replacement")));
        assertEquals(List.of(first), values);
    }

    @Test void editingOneFieldDoesNotConflictWithChangesToTheOtherFieldOrRoles() {
        var prefix = decoration(PREFIX, "Prefix");
        var values = new ArrayList<>(List.of(prefix));
        var before = NameTagStore.snapshot(values);
        var suffix = decoration(SUFFIX, "Added by another editor");
        var role = decoration(PREFIX, "New role", 100, false, false, true);
        values.addAll(List.of(suffix, role));
        replace(nodes(values), PREFIX, before.version(PREFIX), null);
        assertEquals(Set.of(suffix, role), Set.copyOf(values));
    }
}
