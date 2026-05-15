package dev.atlasmod.api.internal;

import dev.atlasmod.core.category.RecipeCategory;
import dev.atlasmod.core.recipe.AcquisitionSource;
import dev.atlasmod.core.recipe.RecipeNode;
import dev.atlasmod.core.registry.AtlasInfoPage;
import dev.atlasmod.core.registry.AtlasRendererBinding;

public interface AtlasRegistrationSink {

    void addCategory(RecipeCategory category);

    void addRecipe(RecipeNode recipe);

    void addSource(AcquisitionSource source);

    void addInfoPage(AtlasInfoPage infoPage);

    void addRenderer(AtlasRendererBinding rendererBinding);
}
