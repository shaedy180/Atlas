package dev.atlasmod.core.entry;

import java.util.Objects;

/**
 * Immutable key identifying any entry (item, block, fluid, etc.) within Atlas.
 *
 * @param type  the entry type, e.g. "item", "block", "fluid"
 * @param id    the full resource identifier, e.g. "minecraft:diamond"
 */
public record EntryKey(String type, String id) {

    public EntryKey {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(id, "id must not be null");
    }

    public String namespace() {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(0, colon) : "minecraft";
    }

    public String path() {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }
}
