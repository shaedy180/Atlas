package dev.atlasmod.core.category;

import java.util.List;
import java.util.Objects;

/**
 * A recipe category groups related recipe types together (e.g. "Crafting", "Smelting", "Alloy Forge").
 *
 * @param id          unique identifier, e.g. "minecraft:crafting"
 * @param name        display name (translatable key or literal)
 * @param icon        entry key for the icon, e.g. "minecraft:crafting_table"
 * @param order       sort order (lower = higher in list)
 * @param stations    blocks or machines that process this category
 * @param recipeTypes game recipe type ids imported into this category
 */
public record RecipeCategory(String id, String name, String icon, int order,
                             List<String> stations, List<String> recipeTypes) {

    public RecipeCategory {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        stations = stations == null ? List.of() : List.copyOf(stations);
        recipeTypes = recipeTypes == null ? List.of() : List.copyOf(recipeTypes);
    }

    public RecipeCategory(String id, String name, String icon, int order) {
        this(id, name, icon, order, List.of(), List.of());
    }
}
