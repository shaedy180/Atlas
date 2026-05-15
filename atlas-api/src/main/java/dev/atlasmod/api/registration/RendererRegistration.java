package dev.atlasmod.api.registration;

import dev.atlasmod.api.internal.AtlasRegistrationSink;
import dev.atlasmod.core.registry.AtlasRendererBinding;

import java.util.Objects;

public final class RendererRegistration {

    private final AtlasRegistrationSink sink;
    private final String ownerModId;
    private final String key;
    private String description = "";
    private String categoryId;
    private String recipeId;

    public RendererRegistration(AtlasRegistrationSink sink, String ownerModId, String key) {
        this.sink = Objects.requireNonNull(sink, "sink must not be null");
        this.ownerModId = Objects.requireNonNull(ownerModId, "ownerModId must not be null");
        this.key = Objects.requireNonNull(key, "key must not be null");
    }

    public RendererRegistration description(String description) {
        this.description = Objects.requireNonNull(description, "description must not be null");
        return this;
    }

    public RendererRegistration category(String categoryId) {
        this.categoryId = categoryId;
        return this;
    }

    public RendererRegistration recipe(String recipeId) {
        this.recipeId = recipeId;
        return this;
    }

    public void register() {
        sink.addRenderer(new AtlasRendererBinding(key, ownerModId, description, categoryId, recipeId));
    }
}
