package dev.atlasmod.core.entry;

import java.util.List;
import java.util.Set;

/**
 * Resolves item tags. Core code never touches Minecraft registries directly,
 * so the loader passes an implementation backed by the live registry.
 */
public interface TagResolver {

    TagResolver NONE = new TagResolver() {
        @Override public List<String> members(String tagId) { return List.of(); }
        @Override public Set<String> tagsOf(String itemId) { return Set.of(); }
    };

    /** Item ids inside the tag, in registry order. Unknown tags return an empty list. */
    List<String> members(String tagId);

    /** Tags the item belongs to. */
    Set<String> tagsOf(String itemId);

    /** Whether the ingredient accepts the given item id. */
    default boolean matches(IngredientKey ingredient, String itemId) {
        if (ingredient.isEmpty()) return false;
        if (ingredient.tagBased()) return members(ingredient.id()).contains(itemId);
        return ingredient.id().equals(itemId);
    }
}
