package dev.atlasmod.api.internal;

import dev.atlasmod.core.category.RecipeCategory;
import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.recipe.AcquisitionSource;
import dev.atlasmod.core.recipe.RecipeNode;
import dev.atlasmod.core.registry.AtlasInfoPage;
import dev.atlasmod.core.registry.AtlasRegistrySnapshot;
import dev.atlasmod.core.registry.AtlasRendererBinding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable collector used while Atlas registrations are being built.
 */
public final class AtlasMutableRegistry implements AtlasRegistrationSink {

    private final Map<String, RecipeCategory> categories = new LinkedHashMap<>();
    private final Map<String, RecipeNode> recipes = new LinkedHashMap<>();
    private final Map<EntryKey, List<AcquisitionSource>> sources = new LinkedHashMap<>();
    private final Map<EntryKey, List<AtlasInfoPage>> infoPages = new LinkedHashMap<>();
    private final Map<String, AtlasRendererBinding> renderers = new LinkedHashMap<>();

    @Override
    public void addCategory(RecipeCategory category) {
        putUnique(categories, category.id(), category, "category");
    }

    @Override
    public void addRecipe(RecipeNode recipe) {
        putUnique(recipes, recipe.id(), recipe, "recipe");
    }

    @Override
    public void addSource(AcquisitionSource source) {
        sources.computeIfAbsent(source.entry(), ignored -> new ArrayList<>()).add(source);
    }

    @Override
    public void addInfoPage(AtlasInfoPage infoPage) {
        infoPages.computeIfAbsent(infoPage.entry(), ignored -> new ArrayList<>()).add(infoPage);
    }

    @Override
    public void addRenderer(AtlasRendererBinding rendererBinding) {
        putUnique(renderers, rendererBinding.key(), rendererBinding, "renderer");
    }

    public AtlasRegistrySnapshot snapshot() {
        return new AtlasRegistrySnapshot(categories, recipes, sources, infoPages, renderers);
    }

    private static <K, V> void putUnique(Map<K, V> map, K key, V value, String label) {
        if (map.containsKey(key)) {
            throw new IllegalArgumentException("Duplicate " + label + " registration: " + key);
        }
        map.put(key, value);
    }
}
