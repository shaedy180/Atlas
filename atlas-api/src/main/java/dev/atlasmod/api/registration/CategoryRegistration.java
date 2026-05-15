package dev.atlasmod.api.registration;

import dev.atlasmod.api.internal.AtlasRegistrationSink;
import dev.atlasmod.core.category.RecipeCategory;

import java.util.Objects;

/**
 * Fluent builder for registering recipe categories with Atlas.
 */
public final class CategoryRegistration {

    private final AtlasRegistrationSink sink;
    private final String ownerModId;
    private final String id;
    private String name;
    private String icon;
    private int order = 100;

    public CategoryRegistration(AtlasRegistrationSink sink, String ownerModId, String id) {
        this.sink = Objects.requireNonNull(sink, "sink must not be null");
        this.ownerModId = Objects.requireNonNull(ownerModId, "ownerModId must not be null");
        this.id = Objects.requireNonNull(id, "id must not be null");
    }

    public CategoryRegistration name(String name) {
        this.name = name;
        return this;
    }

    public CategoryRegistration icon(String icon) {
        this.icon = icon;
        return this;
    }

    public CategoryRegistration order(int order) {
        this.order = order;
        return this;
    }

    public void register() {
        sink.addCategory(new RecipeCategory(id, ownerModId, name != null ? name : id, icon, order));
    }
}
