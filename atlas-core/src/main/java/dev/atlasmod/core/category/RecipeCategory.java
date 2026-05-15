package dev.atlasmod.core.category;

import java.util.Objects;

/**
 * A recipe category groups related recipe types together (e.g. "Crafting", "Smelting", "Alloy Forge").
 *
 * @param id         unique identifier, e.g. "minecraft:crafting"
 * @param ownerModId mod that owns the registration
 * @param name       display name (translatable key or literal)
 * @param icon       entry key for the icon, e.g. "minecraft:crafting_table"
 * @param order      sort order (lower = higher in list)
 */
public record RecipeCategory(String id, String ownerModId, String name, String icon, int order) {

    public RecipeCategory {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(ownerModId, "ownerModId must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }
}
