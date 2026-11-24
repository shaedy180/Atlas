package dev.atlasmod.core.plan;

import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.entry.IngredientKey;
import dev.atlasmod.core.entry.TagResolver;
import dev.atlasmod.core.recipe.RecipeGraph;
import dev.atlasmod.core.recipe.RecipeNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Estimates how many raw materials one unit of an item costs, following the
 * cheapest recipe at every step. Items without a recipe cost 1. Recipes that loop
 * back into the item being priced (ingot to block to ingot) are ignored.
 * <p>
 * Results are cached, so build a new model after the recipe graph changes.
 */
public final class CostModel {

    /** Deeper chains are treated as raw materials. Keeps huge modpacks fast. */
    static final int MAX_DEPTH = 12;

    private final RecipeGraph graph;
    private final TagResolver tags;
    private final Map<EntryKey, Double> cache = new HashMap<>();
    private final Set<EntryKey> inProgress = new HashSet<>();
    private boolean touchedCycle;
    private Map<String, Integer> usage;

    private static final double EPSILON = 1e-9;

    public CostModel(RecipeGraph graph, TagResolver tags) {
        this.graph = graph;
        this.tags = tags == null ? TagResolver.NONE : tags;
    }

    public TagResolver tags() {
        return tags;
    }

    public RecipeGraph graph() {
        return graph;
    }

    /** Raw material cost of one unit of the item. */
    public double cost(EntryKey item) {
        return cost(item, 0);
    }

    /** Raw material cost of one craft of the recipe, divided by its output count. */
    public double recipeCost(RecipeNode recipe) {
        return recipeCost(recipe, 0);
    }

    /** Cost of the cheapest item accepted by the ingredient. */
    public double ingredientCost(IngredientKey ingredient) {
        return ingredientCost(ingredient, 0);
    }

    /** The cheapest recipe for the item that does not loop back into itself. */
    public Optional<RecipeNode> bestRecipe(EntryKey item) {
        return bestRecipe(item, null);
    }

    /**
     * The cheapest recipe for the item. Equally cheap recipes are decided by what
     * the player already has, then by how common the inputs and station are
     * (raw iron beats iron ore, a furnace beats a blast furnace).
     */
    public Optional<RecipeNode> bestRecipe(EntryKey item, Inventory inventory) {
        List<RecipeNode> best = new ArrayList<>();
        double bestCost = Double.POSITIVE_INFINITY;
        inProgress.add(item);
        try {
            for (RecipeNode recipe : graph.recipesFor(item)) {
                double c = recipeCost(recipe, 1);
                if (c == Double.POSITIVE_INFINITY) continue;
                if (c < bestCost - EPSILON) {
                    bestCost = c;
                    best.clear();
                    best.add(recipe);
                } else if (Math.abs(c - bestCost) <= EPSILON) {
                    best.add(recipe);
                }
            }
        } finally {
            inProgress.remove(item);
        }
        if (best.size() <= 1) return best.stream().findFirst();
        Comparator<RecipeNode> order = Comparator.comparingInt((RecipeNode r) -> owned(r, inventory))
                .thenComparingInt(this::popularity);
        return best.stream().max(order);
    }

    /** How many of the recipe's inputs the inventory covers. */
    int owned(RecipeNode recipe, Inventory inventory) {
        if (inventory == null) return 0;
        int owned = 0;
        for (Map.Entry<IngredientKey, Integer> entry : recipe.ingredientCounts().entrySet()) {
            owned += Math.min(entry.getValue(), inventory.count(entry.getKey()));
        }
        return owned;
    }

    /** Rough measure of how common a recipe's inputs and station are. */
    int popularity(RecipeNode recipe) {
        if (usage == null) countUsage();
        int score = 0;
        for (IngredientKey input : recipe.ingredientCounts().keySet()) {
            score += usage.getOrDefault(input.id(), 0);
        }
        if (recipe.station().isPresent()) score += usage.getOrDefault("station:" + recipe.station().get().id(), 0);
        return score;
    }

    private void countUsage() {
        usage = new HashMap<>();
        for (RecipeNode node : graph.allNodes()) {
            for (IngredientKey input : node.ingredientCounts().keySet()) usage.merge(input.id(), 1, Integer::sum);
            node.station().ifPresent(st -> usage.merge("station:" + st.id(), 1, Integer::sum));
        }
    }

    /** The accepted item with the lowest cost, or the first member when all are equal. */
    public Optional<String> cheapestMember(IngredientKey ingredient) {
        if (!ingredient.tagBased()) return Optional.of(ingredient.id());
        String best = null;
        double bestCost = Double.POSITIVE_INFINITY;
        for (String member : tags.members(ingredient.id())) {
            double c = cost(new EntryKey("item", member));
            if (c < bestCost) {
                bestCost = c;
                best = member;
            }
        }
        return Optional.ofNullable(best);
    }

    private double cost(EntryKey item, int depth) {
        return cost(item, depth, false);
    }

    /**
     * @param viaUnpacking true when the caller turns one of this item into several
     *                     of something else (block to ingots)
     */
    private double cost(EntryKey item, int depth, boolean viaUnpacking) {
        Double cached = cache.get(item);
        if (cached != null) return cached;
        if (inProgress.contains(item)) {
            touchedCycle = true;
            return Double.POSITIVE_INFINITY;
        }
        List<RecipeNode> recipes = graph.recipesFor(item);
        if (recipes.isEmpty() || depth >= MAX_DEPTH) return 1.0;

        boolean outerCycle = touchedCycle;
        touchedCycle = false;
        inProgress.add(item);
        double best = Double.POSITIVE_INFINITY;
        try {
            for (RecipeNode recipe : recipes) {
                best = Math.min(best, recipeCost(recipe, depth + 1));
            }
        } finally {
            inProgress.remove(item);
        }
        if (best == Double.POSITIVE_INFINITY) {
            // Every recipe loops back (diamond and diamond block). Something in such a
            // loop has to be found in the world. Unpacking a block is never how you
            // get the first diamond, so that side of the loop is skipped and the
            // other side becomes the raw material.
            best = depth > 0 && viaUnpacking ? Double.POSITIVE_INFINITY : 1.0;
        }
        // Values computed while a caller was still open may be too high, so only
        // cache results that did not depend on an unfinished item.
        if (!touchedCycle) cache.put(item, best);
        touchedCycle |= outerCycle;
        return best;
    }

    private double recipeCost(RecipeNode recipe, int depth) {
        Map<IngredientKey, Integer> counts = recipe.ingredientCounts();
        boolean unpacking = counts.size() == 1 && counts.values().iterator().next() == 1 && recipe.outputCount() > 1;
        double total = 0;
        for (Map.Entry<IngredientKey, Integer> entry : counts.entrySet()) {
            total += entry.getValue() * ingredientCost(entry.getKey(), depth, unpacking);
            if (total == Double.POSITIVE_INFINITY) return total;
        }
        // A recipe with no inputs still has to happen once.
        if (total == 0) total = 1;
        return total / recipe.outputCount();
    }

    private double ingredientCost(IngredientKey ingredient, int depth) {
        return ingredientCost(ingredient, depth, false);
    }

    private double ingredientCost(IngredientKey ingredient, int depth, boolean viaUnpacking) {
        if (ingredient.isEmpty()) return 0;
        if (!ingredient.tagBased()) return cost(entryOf(ingredient), depth, viaUnpacking);
        List<String> members = tags.members(ingredient.id());
        if (members.isEmpty()) return 1.0;
        double best = Double.POSITIVE_INFINITY;
        for (String member : members) {
            best = Math.min(best, cost(new EntryKey("item", member), depth, viaUnpacking));
        }
        return best;
    }

    static EntryKey entryOf(IngredientKey ingredient) {
        return new EntryKey(ingredient.type(), ingredient.id());
    }
}
