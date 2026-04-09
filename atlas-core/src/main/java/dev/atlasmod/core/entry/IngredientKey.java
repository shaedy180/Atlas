package dev.atlasmod.core.entry;

import java.util.Objects;

/**
 * Immutable key identifying an ingredient within a recipe.
 * Can be tag-based or exact.
 *
 * @param type      "item", "fluid", "tag", etc.
 * @param id        resource identifier or tag id
 * @param tagBased  whether this ingredient accepts any item within a tag
 */
public record IngredientKey(String type, String id, boolean tagBased) {

    /** Sentinel for empty grid slots in shaped recipes. */
    public static final IngredientKey EMPTY = new IngredientKey("empty", "", false);

    public IngredientKey {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(id, "id must not be null");
    }

    public boolean isEmpty() { return this == EMPTY; }

    public static IngredientKey item(String id) {
        return new IngredientKey("item", id, false);
    }

    public static IngredientKey tag(String tagId) {
        return new IngredientKey("tag", tagId, true);
    }

    public static IngredientKey fluid(String id) {
        return new IngredientKey("fluid", id, false);
    }
}
