package dev.atlasmod.api;

import dev.atlasmod.api.registration.CategoryRegistration;
import dev.atlasmod.api.registration.InfoPageRegistration;
import dev.atlasmod.api.registration.RecipeRegistration;
import dev.atlasmod.api.registration.RendererRegistration;
import dev.atlasmod.api.registration.SourceRegistration;

import java.util.Objects;

/**
 * Convenience facade for the currently active Atlas registration context.
 */
public final class AtlasApi {

    private static volatile AtlasRegistrationContext ACTIVE_CONTEXT;

    private AtlasApi() {
    }

    public static void attachContext(AtlasRegistrationContext context) {
        ACTIVE_CONTEXT = Objects.requireNonNull(context, "context must not be null");
    }

    public static void clearContext() {
        ACTIVE_CONTEXT = null;
    }

    public static AtlasRegistrationContext context() {
        AtlasRegistrationContext context = ACTIVE_CONTEXT;
        if (context == null) {
            throw new IllegalStateException("Atlas registration context is not active");
        }
        return context;
    }

    public static CategoryRegistration category(String categoryId) {
        return context().categories().add(categoryId);
    }

    public static RecipeRegistration recipe(String id, String categoryId) {
        return context().recipes().add(id, categoryId);
    }

    /**
     * Legacy entrypoint kept for older plugins.
     * The recipe id must still be provided before register() is called.
     */
    @Deprecated(forRemoval = false)
    public static RecipeRegistration recipes(String categoryId) {
        return context().recipes().legacy(categoryId);
    }

    public static SourceRegistration source(String id) {
        return context().sources().add(id);
    }

    public static InfoPageRegistration infoPage(String id) {
        return context().infoPages().add(id);
    }

    public static RendererRegistration renderer(String key) {
        return context().renderers().add(key);
    }
}
