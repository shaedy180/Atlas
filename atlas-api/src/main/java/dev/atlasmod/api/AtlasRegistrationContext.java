package dev.atlasmod.api;

import dev.atlasmod.api.registration.CategoryRegistration;
import dev.atlasmod.api.registration.InfoPageRegistration;
import dev.atlasmod.api.registration.RecipeRegistration;
import dev.atlasmod.api.registration.RendererRegistration;
import dev.atlasmod.api.registration.SourceRegistration;

/**
 * Stable registration surface exposed to Atlas plugins.
 */
public interface AtlasRegistrationContext {

    String ownerModId();

    CategoryAccess categories();

    RecipeAccess recipes();

    SourceAccess sources();

    InfoPageAccess infoPages();

    RendererAccess renderers();

    interface CategoryAccess {
        CategoryRegistration add(String id);
    }

    interface RecipeAccess {
        RecipeRegistration add(String id, String categoryId);

        RecipeRegistration legacy(String categoryId);
    }

    interface SourceAccess {
        SourceRegistration add(String id);
    }

    interface InfoPageAccess {
        InfoPageRegistration add(String id);
    }

    interface RendererAccess {
        RendererRegistration add(String key);
    }
}
