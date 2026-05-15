package dev.atlasmod.fabric;

import dev.atlasmod.core.category.RecipeCategory;

import java.util.Locale;

final class AtlasCategoryHelper {

    private AtlasCategoryHelper() {
    }

    static String labelFor(String categoryId) {
        RecipeCategory category = AtlasRuntimeController.clientSnapshot().categoriesById().get(categoryId);
        if (category != null) {
            return category.name();
        }

        String raw = categoryId.contains(":") ? categoryId.substring(categoryId.indexOf(':') + 1) : categoryId;
        if (raw.isEmpty()) {
            return categoryId;
        }
        return raw.substring(0, 1).toUpperCase(Locale.ROOT) + raw.substring(1).replace('_', ' ');
    }
}
