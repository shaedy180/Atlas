package dev.atlasmod.search;

import dev.atlasmod.core.entry.EntryKey;

import java.util.*;

/**
 * In-memory search index for fast entry lookup. Supports tokenized names,
 * mod ids, tags, and tooltip text.
 */
public final class SearchIndex {

    private record IndexEntry(
            EntryKey key,
            String name,
            String namespace,
            Set<String> tags,
            String tooltip,
            Set<String> sourceTypes,
            Set<String> stations,
            boolean renewable
    ) {}

    private final List<IndexEntry> entries = new ArrayList<>();

    public void add(
            EntryKey key,
            String displayName,
            Set<String> tags,
            String tooltip,
            Set<String> sourceTypes,
            Set<String> stations,
            boolean renewable
    ) {
        entries.add(new IndexEntry(
                key,
                displayName.toLowerCase(Locale.ROOT),
                key.namespace(),
                tags != null ? tags : Set.of(),
                tooltip != null ? tooltip.toLowerCase(Locale.ROOT) : "",
                sourceTypes != null ? Set.copyOf(sourceTypes) : Set.of(),
                stations != null ? Set.copyOf(stations) : Set.of(),
                renewable
        ));
    }

    /**
     * Search the index with a parsed query. Returns matching entry keys.
     */
    public List<EntryKey> search(SearchQuery query) {
        String text = query.textQuery().toLowerCase(Locale.ROOT);
        List<EntryKey> results = new ArrayList<>();

        for (var entry : entries) {
            if (!text.isEmpty() && !entry.name.contains(text)) continue;
            if (query.modFilter() != null && !entry.namespace.equals(query.modFilter())) continue;
            if (query.tagFilter() != null && !entry.tags.contains(query.tagFilter())) continue;
            if (query.tooltipFilter() != null && !entry.tooltip.contains(query.tooltipFilter().toLowerCase(Locale.ROOT))) continue;
            if (query.sourceFilter() != null && !entry.sourceTypes.contains(query.sourceFilter().toLowerCase(Locale.ROOT))) continue;
            if (query.stationFilter() != null && !entry.stations.contains(query.stationFilter())) continue;
            if (query.onlyRenewable() && !entry.renewable) continue;
            results.add(entry.key);
        }

        return results;
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        entries.clear();
    }
}
