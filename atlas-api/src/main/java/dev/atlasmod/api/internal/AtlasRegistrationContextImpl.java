package dev.atlasmod.api.internal;

import dev.atlasmod.api.AtlasRegistrationContext;
import dev.atlasmod.api.registration.CategoryRegistration;
import dev.atlasmod.api.registration.InfoPageRegistration;
import dev.atlasmod.api.registration.RecipeRegistration;
import dev.atlasmod.api.registration.RendererRegistration;
import dev.atlasmod.api.registration.SourceRegistration;

import java.util.Objects;

public final class AtlasRegistrationContextImpl implements AtlasRegistrationContext {

    private final String ownerModId;
    private final AtlasRegistrationSink sink;
    private final CategoryAccess categories;
    private final RecipeAccess recipes;
    private final SourceAccess sources;
    private final InfoPageAccess infoPages;
    private final RendererAccess renderers;

    public AtlasRegistrationContextImpl(String ownerModId, AtlasRegistrationSink sink) {
        this.ownerModId = Objects.requireNonNull(ownerModId, "ownerModId must not be null");
        this.sink = Objects.requireNonNull(sink, "sink must not be null");
        this.categories = id -> new CategoryRegistration(this.sink, this.ownerModId, id);
        this.recipes = new RecipeAccess() {
            @Override
            public RecipeRegistration add(String id, String categoryId) {
                return new RecipeRegistration(AtlasRegistrationContextImpl.this.sink, AtlasRegistrationContextImpl.this.ownerModId, id, categoryId);
            }

            @Override
            public RecipeRegistration legacy(String categoryId) {
                return new RecipeRegistration(AtlasRegistrationContextImpl.this.sink, AtlasRegistrationContextImpl.this.ownerModId, null, categoryId);
            }
        };
        this.sources = id -> new SourceRegistration(this.sink, this.ownerModId, id);
        this.infoPages = id -> new InfoPageRegistration(this.sink, this.ownerModId, id);
        this.renderers = key -> new RendererRegistration(this.sink, this.ownerModId, key);
    }

    @Override
    public String ownerModId() {
        return ownerModId;
    }

    @Override
    public CategoryAccess categories() {
        return categories;
    }

    @Override
    public RecipeAccess recipes() {
        return recipes;
    }

    @Override
    public SourceAccess sources() {
        return sources;
    }

    @Override
    public InfoPageAccess infoPages() {
        return infoPages;
    }

    @Override
    public RendererAccess renderers() {
        return renderers;
    }
}
