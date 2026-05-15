package dev.atlasmod.core.registry;

import dev.atlasmod.core.entry.EntryKey;

import java.util.List;
import java.util.Objects;

/**
 * Supplemental info attached to an entry.
 */
public record AtlasInfoPage(
        String id,
        String ownerModId,
        EntryKey entry,
        String title,
        String body,
        List<String> references
) {
    public AtlasInfoPage {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(ownerModId, "ownerModId must not be null");
        Objects.requireNonNull(entry, "entry must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(body, "body must not be null");
        references = List.copyOf(references);
    }
}
