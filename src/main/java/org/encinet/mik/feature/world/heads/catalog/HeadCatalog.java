package org.encinet.mik.feature.world.heads.catalog;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** A complete searchable snapshot, constructed and queried on feature workers. */
public final class HeadCatalog {
    public enum Facet { CATEGORY, TAG, COLLECTION }
    public enum Sort { RELEVANCE, NAME, ID, CATEGORY }

    public record Label(String id, String name, String description) {
        public Label {
            HeadEntry.requireFacet(id);
            if (name == null || name.isBlank() || name.length() > 160 || name.chars().anyMatch(Character::isISOControl)
                    || (description != null && description.length() > 512))
                throw new IllegalArgumentException("Invalid head catalog label: " + id);
        }
    }

    public record Filter(String query, Map<Facet, Set<String>> facets, Sort sort, boolean descending) {
        public Filter {
            Objects.requireNonNull(query);
            Objects.requireNonNull(sort);
            var copy = new EnumMap<Facet, Set<String>>(Facet.class);
            facets.forEach((facet, values) -> { if (!values.isEmpty()) copy.put(facet, Set.copyOf(values)); });
            facets = Map.copyOf(copy);
        }
    }

    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}\\p{M}]+");
    private static final Comparator<HeadEntry> BY_ID = Comparator.comparingInt((HeadEntry head) ->
            Character.isDigit(head.id().charAt(0)) ? head.id().length() : Integer.MAX_VALUE)
            .thenComparing(HeadEntry::id);
    private static final Comparator<HeadEntry> BY_NAME = Comparator.comparing(HeadEntry::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(BY_ID);
    private static final Comparator<HeadEntry> BY_CATEGORY = Comparator.comparing(HeadEntry::category,
            Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(BY_NAME);

    private final Map<String, HeadEntry> byId;
    private final Map<Facet, Map<String, Label>> labels;
    private final Map<Facet, List<Label>> directories;
    private final List<HeadEntry> ids;
    private final List<HeadEntry> names;
    private final List<HeadEntry> categories;
    private final Map<String, String> searchText;

    HeadCatalog(List<HeadEntry> entries, Map<Facet, List<Label>> taxonomy) {
        var index = new HashMap<String, HeadEntry>();
        var text = new HashMap<String, String>();
        var labelIndex = new EnumMap<Facet, Map<String, Label>>(Facet.class);
        var directory = new EnumMap<Facet, List<Label>>(Facet.class);
        for (Facet facet : Facet.values()) {
            var group = new LinkedHashMap<String, Label>();
            for (Label label : taxonomy.getOrDefault(facet, List.of())) {
                if (group.putIfAbsent(label.id(), label) != null)
                    throw new IllegalArgumentException("Duplicate head catalog label: " + label.id());
            }
            labelIndex.put(facet, Map.copyOf(group));
            directory.put(facet, group.values().stream().sorted(Comparator.comparing(Label::name,
                    String.CASE_INSENSITIVE_ORDER).thenComparing(Label::id)).toList());
        }
        for (HeadEntry entry : entries) {
            if (index.putIfAbsent(entry.id(), entry) != null)
                throw new IllegalArgumentException("Duplicate head ID: " + entry.id());
            var terms = new StringBuilder(entry.name());
            if (entry.category() != null) appendLabel(terms, labelIndex.get(Facet.CATEGORY), entry.category());
            entry.tags().forEach(tag -> appendLabel(terms, labelIndex.get(Facet.TAG), tag));
            entry.collections().forEach(collection -> appendLabel(terms, labelIndex.get(Facet.COLLECTION), collection));
            text.put(entry.id(), folded(terms.toString()));
        }
        byId = Map.copyOf(index);
        labels = Map.copyOf(labelIndex);
        directories = Map.copyOf(directory);
        searchText = Map.copyOf(text);
        ids = entries.stream().sorted(BY_ID).toList();
        names = entries.stream().sorted(BY_NAME).toList();
        categories = entries.stream().sorted(BY_CATEGORY).toList();
    }

    public HeadEntry find(String id) { return byId.get(id); }
    public int size() { return ids.size(); }
    public List<Label> labels(Facet facet) { return directories.get(facet); }
    public String label(Facet facet, String id) {
        Label label = labels.get(facet).get(id);
        return label == null ? id : label.name();
    }

    public List<HeadEntry> search(Filter filter) {
        String raw = filter.query().strip().toLowerCase(Locale.ROOT);
        String id = raw.startsWith("#") ? raw.substring(1) : raw;
        if (HeadEntry.validId(id) && (raw.startsWith("#") || Character.isDigit(id.charAt(0)) || id.contains(":"))) {
            HeadEntry entry = find(id);
            return entry != null && matchesFacets(entry, filter.facets()) ? List.of(entry) : List.of();
        }
        String query = folded(raw);
        List<HeadEntry> sorted = switch (filter.sort()) {
            case RELEVANCE -> query.isEmpty() ? ids : names;
            case ID -> ids;
            case NAME -> names;
            case CATEGORY -> categories;
        };
        if (filter.sort() != Sort.RELEVANCE && filter.descending()) sorted = sorted.reversed();
        if (query.isEmpty() && filter.facets().isEmpty()) return sorted;

        String[] tokens = query.split(" ");
        Predicate<HeadEntry> matches = entry -> matchesFacets(entry, filter.facets())
                && containsAll(searchText.get(entry.id()), tokens);
        if (filter.sort() != Sort.RELEVANCE || query.isEmpty()) return sorted.stream().filter(matches).toList();

        var exact = new ArrayList<HeadEntry>();
        var prefix = new ArrayList<HeadEntry>();
        var other = new ArrayList<HeadEntry>();
        for (HeadEntry entry : sorted) {
            if (!matches.test(entry)) continue;
            String name = folded(entry.name());
            if (name.equals(query)) exact.add(entry);
            else if (name.startsWith(query)) prefix.add(entry);
            else other.add(entry);
        }
        exact.addAll(prefix);
        exact.addAll(other);
        return List.copyOf(exact);
    }

    public static boolean matches(String text, String query) { return folded(text).contains(folded(query)); }

    private static boolean matchesFacets(HeadEntry entry, Map<Facet, Set<String>> facets) {
        Set<String> categories = facets.get(Facet.CATEGORY);
        return (categories == null || (entry.category() != null && categories.contains(entry.category())))
                && entry.tags().containsAll(facets.getOrDefault(Facet.TAG, Set.of()))
                && entry.collections().containsAll(facets.getOrDefault(Facet.COLLECTION, Set.of()));
    }

    private static void appendLabel(StringBuilder terms, Map<String, Label> labels, String id) {
        Label label = labels.get(id);
        if (label == null) throw new IllegalArgumentException("Unknown head catalog label: " + id);
        terms.append(' ').append(id).append(' ').append(label.name());
    }

    private static boolean containsAll(String text, String[] tokens) {
        for (String token : tokens) if (!text.contains(token)) return false;
        return true;
    }

    private static String folded(String text) {
        return SEPARATORS.matcher(Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT))
                .replaceAll(" ").strip();
    }
}
