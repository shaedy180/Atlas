package dev.atlasmod.client;

import dev.atlasmod.core.alternative.AlternativeResolver;
import dev.atlasmod.core.availability.AvailabilityEngine;
import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.plan.CostModel;
import dev.atlasmod.core.plan.CraftingPlan;
import dev.atlasmod.core.plan.CraftingPlanner;
import dev.atlasmod.core.plan.Inventory;
import dev.atlasmod.core.recipe.RecipeGraph;

/**
 * The analysis services for one loaded world. Rebuilt whenever recipes or tags
 * reload, because the cost model caches its results.
 */
public final class Insights {

    private final CostModel costs;
    private final CraftingPlanner planner;
    private final AlternativeResolver alternatives;
    private final AvailabilityEngine availability;

    Insights(RecipeGraph graph, RegistryTags tags) {
        this.costs = new CostModel(graph, tags);
        this.planner = new CraftingPlanner(costs);
        this.alternatives = new AlternativeResolver(graph, costs);
        this.availability = new AvailabilityEngine(tags, Insights::displayName);
    }

    public CostModel costs() { return costs; }
    public CraftingPlanner planner() { return planner; }
    public AlternativeResolver alternatives() { return alternatives; }
    public AvailabilityEngine availability() { return availability; }

    /** Plan against the player's current inventory. */
    public CraftingPlan plan(EntryKey target, int count) {
        return planner.plan(target, count, inventory());
    }

    public Inventory inventory() {
        return new Inventory(AtlasClient.context().inventoryCounts(), costs.tags());
    }

    /** Items and blocks use their game name, everything else the advancement title or a pretty id. */
    private static String displayName(String id) {
        return ItemStacks.of(id).isEmpty() ? AtlasClient.context().advancementTitle(id) : ItemStacks.name(id);
    }
}
