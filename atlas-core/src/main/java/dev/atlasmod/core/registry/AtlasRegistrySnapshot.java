package dev.atlasmod.core.registry;

import dev.atlasmod.core.category.RecipeCategory;
import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.recipe.AcquisitionSource;
import dev.atlasmod.core.recipe.RecipeNode;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Immutable runtime snapshot used by Atlas on both server and client.
 */
public record AtlasRegistrySnapshot(
        Map<String, RecipeCategory> categoriesById,
        Map<String, RecipeNode> recipesById,
        Map<EntryKey, List<AcquisitionSource>> sourcesByEntry,
        Map<EntryKey, List<AtlasInfoPage>> infoPagesByEntry,
        Map<String, AtlasRendererBinding> renderersByKey
) {
    public static final AtlasRegistrySnapshot EMPTY = new AtlasRegistrySnapshot(
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of()
    );

    public AtlasRegistrySnapshot {
        categoriesById = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(categoriesById, "categoriesById")));
        recipesById = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(recipesById, "recipesById")));
        sourcesByEntry = copyMultiMap(Objects.requireNonNull(sourcesByEntry, "sourcesByEntry"));
        infoPagesByEntry = copyMultiMap(Objects.requireNonNull(infoPagesByEntry, "infoPagesByEntry"));
        renderersByKey = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(renderersByKey, "renderersByKey")));
    }

    private static <K, V> Map<K, List<V>> copyMultiMap(Map<K, List<V>> input) {
        Map<K, List<V>> copy = new LinkedHashMap<>();
        input.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Map.copyOf(copy);
    }

    public Collection<RecipeCategory> categories() {
        return categoriesById.values();
    }

    public Collection<RecipeNode> recipes() {
        return recipesById.values();
    }

    public List<AcquisitionSource> sourcesFor(EntryKey entry) {
        return sourcesByEntry.getOrDefault(entry, List.of());
    }

    public List<AtlasInfoPage> infoPagesFor(EntryKey entry) {
        return infoPagesByEntry.getOrDefault(entry, List.of());
    }

    public Set<String> ownerModIds() {
        TreeSet<String> owners = new TreeSet<>();
        categoriesById.values().forEach(category -> owners.add(category.ownerModId()));
        recipesById.values().forEach(recipe -> owners.add(recipe.ownerModId()));
        sourcesByEntry.values().forEach(list -> list.forEach(source -> owners.add(source.ownerModId())));
        infoPagesByEntry.values().forEach(list -> list.forEach(page -> owners.add(page.ownerModId())));
        renderersByKey.values().forEach(renderer -> owners.add(renderer.ownerModId()));
        return Set.copyOf(owners);
    }
}
