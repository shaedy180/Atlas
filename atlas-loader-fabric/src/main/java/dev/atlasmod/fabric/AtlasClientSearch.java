package dev.atlasmod.fabric;

import dev.atlasmod.core.availability.AvailabilityEngine;
import dev.atlasmod.core.availability.ContextSnapshot;
import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.recipe.RecipeGraph;
import dev.atlasmod.core.recipe.RecipeNode;
import dev.atlasmod.core.visibility.VisibilityPolicy;
import dev.atlasmod.search.SearchIndex;
import dev.atlasmod.search.SearchQuery;
import dev.atlasmod.ui.PinnedPlanManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class AtlasClientSearch {

    private AtlasClientSearch() {
    }

    static List<EntryKey> search(String text, boolean savedOnly) {
        SearchIndex index = AtlasFabricClient.searchIndex();
        if (index == null) {
            return List.of();
        }

        SearchQuery query = SearchQuery.parse(text);
        List<EntryKey> raw = index.search(query);

        if (savedOnly) {
            PinnedPlanManager manager = AtlasFabricClient.pinnedPlanManager();
            Set<EntryKey> savedTargets = new HashSet<>();
            manager.plans().forEach(plan -> savedTargets.add(plan.target()));
            raw = raw.stream().filter(savedTargets::contains).toList();
        }

        if (query.onlyUnlocked() || !query.includeHidden()) {
            RecipeGraph graph = AtlasFabricClient.recipeGraph();
            ContextSnapshot context = ContextSnapshotBuilder.capture();
            AvailabilityEngine engine = new AvailabilityEngine();

            raw = raw.stream().filter(entry -> includeEntry(entry, query, graph, context, engine)).toList();
        }

        return raw;
    }

    private static boolean includeEntry(
            EntryKey entry,
            SearchQuery query,
            RecipeGraph graph,
            ContextSnapshot context,
            AvailabilityEngine engine
    ) {
        List<RecipeNode> recipes = graph.recipesFor(entry);
        if (recipes.isEmpty()) {
            return true;
        }

        boolean sawUnknown = false;
        boolean sawVisible = false;

        for (RecipeNode recipe : recipes) {
            AvailabilityEngine.AvailabilityResult result = engine.evaluate(recipe, context);

            if (result.unknown()) {
                sawUnknown = true;
            }
            if (result.policy() != VisibilityPolicy.HIDDEN) {
                sawVisible = true;
            }
            if (query.onlyUnlocked() && result.available()) {
                return true;
            }
            if (!query.onlyUnlocked() && result.policy() != VisibilityPolicy.HIDDEN) {
                return true;
            }
        }

        if (query.onlyUnlocked()) {
            return sawUnknown;
        }
        return query.includeHidden() || sawVisible || sawUnknown;
    }
}
