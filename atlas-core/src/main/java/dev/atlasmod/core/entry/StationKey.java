package dev.atlasmod.core.entry;

import java.util.Objects;

/**
 * Identifies a workstation or machine required for a recipe.
 *
 * @param id   resource identifier, e.g. "minecraft:crafting_table"
 */
public record StationKey(String id) {

    public StationKey {
        Objects.requireNonNull(id, "id must not be null");
    }
}
