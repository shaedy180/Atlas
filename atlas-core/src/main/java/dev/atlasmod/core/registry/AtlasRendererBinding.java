package dev.atlasmod.core.registry;

import java.util.Objects;
import java.util.Optional;

/**
 * Renderer metadata registered by Atlas integrations.
 *
 * Atlas still falls back to default rendering when no matching binding is found.
 */
public record AtlasRendererBinding(
        String key,
        String ownerModId,
        String description,
        String categoryId,
        String recipeId
) {
    public AtlasRendererBinding {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(ownerModId, "ownerModId must not be null");
        Objects.requireNonNull(description, "description must not be null");
    }

    public Optional<String> optionalCategoryId() {
        return Optional.ofNullable(categoryId);
    }

    public Optional<String> optionalRecipeId() {
        return Optional.ofNullable(recipeId);
    }
}
