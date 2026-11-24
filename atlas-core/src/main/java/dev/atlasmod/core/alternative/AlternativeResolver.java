package dev.atlasmod.core.alternative;

import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.entry.IngredientKey;
import dev.atlasmod.core.plan.CostModel;
import dev.atlasmod.core.plan.Inventory;
import dev.atlasmod.core.recipe.RecipeGraph;
import dev.atlasmod.core.recipe.RecipeNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the other ways to get or replace an item: every recipe ranked by cost and
 * effort, every item a tag ingredient accepts, and items that share a tag with it.
 */
public final class AlternativeResolver {

    public enum Mark { CHEAPEST, SIMPLEST, CRAFTABLE_NOW }

    /**
     * A recipe option for the item.
     *
     * @param cost  raw materials per crafted item
     * @param steps crafting steps from raw materials, this recipe included
     */
    public record RankedRecipe(RecipeNode recipe, double cost, int steps, Set<Mark> marks) {}

    /** One item a tag ingredient accepts. */
    public record Option(String itemId, int owned, double cost, boolean preferred) {}

    /** A tag ingredient and everything that can go into that slot. */
    public record Substitution(IngredientKey ingredient, List<Option> options) {}

    private static final int STEP_LIMIT = 8;

    private final RecipeGraph graph;
    private final CostModel costs;

    public AlternativeResolver(RecipeGraph graph, CostModel costs) {
        this.graph = graph;
        this.costs = costs;
    }

    /** All recipes for the item, cheapest first, with the best ones marked. */
    public List<RankedRecipe> rankRecipes(EntryKey item, Inventory inventory) {
        List<RecipeNode> recipes = graph.recipesFor(item);
        List<double[]> scores = new ArrayList<>();
        for (RecipeNode recipe : recipes) {
            scores.add(new double[]{costs.recipeCost(recipe), steps(recipe, 0)});
        }

        double minCost = scores.stream().mapToDouble(s -> s[0]).min().orElse(0);
        double minSteps = scores.stream().mapToDouble(s -> s[1]).min().orElse(0);

        List<RankedRecipe> ranked = new ArrayList<>();
        for (int i = 0; i < recipes.size(); i++) {
            RecipeNode recipe = recipes.get(i);
            double[] score = scores.get(i);
            Set<Mark> marks = EnumSet.noneOf(Mark.class);
            if (recipes.size() > 1 && score[0] <= minCost && score[0] < Double.POSITIVE_INFINITY) {
                marks.add(Mark.CHEAPEST);
            }
            if (recipes.size() > 1 && score[1] <= minSteps) marks.add(Mark.SIMPLEST);
            if (inventory != null && hasAll(recipe, inventory.copy())) marks.add(Mark.CRAFTABLE_NOW);
            ranked.add(new RankedRecipe(recipe, score[0], (int) score[1], marks));
        }
        ranked.sort(Comparator.comparingDouble(RankedRecipe::cost).thenComparingInt(RankedRecipe::steps));
        return ranked;
    }

    /** Every tag ingredient used by the item's recipes, with all accepted items. */
    public List<Substitution> substitutions(EntryKey item, Inventory inventory) {
        Map<IngredientKey, Substitution> result = new LinkedHashMap<>();
        for (RecipeNode recipe : graph.recipesFor(item)) {
            for (IngredientKey ingredient : recipe.ingredientCounts().keySet()) {
                if (ingredient.tagBased() && !result.containsKey(ingredient)) {
                    result.put(ingredient, substitution(ingredient, inventory));
                }
            }
        }
        return List.copyOf(result.values());
    }

    /** Options for one tag ingredient. The preferred one is owned or, failing that, cheapest. */
    public Substitution substitution(IngredientKey ingredient, Inventory inventory) {
        List<Option> options = new ArrayList<>();
        for (String member : costs.tags().members(ingredient.id())) {
            int owned = inventory == null ? 0 : inventory.count(member);
            options.add(new Option(member, owned, costs.cost(new EntryKey("item", member)), false));
        }
        Comparator<Option> byOwned = Comparator.comparingInt(Option::owned);
        Comparator<Option> byCost = Comparator.comparingDouble(Option::cost);
        String preferred = options.stream()
                .max(options.stream().anyMatch(o -> o.owned() > 0) ? byOwned : byCost.reversed())
                .map(Option::itemId)
                .orElse(null);
        options.replaceAll(o -> o.itemId().equals(preferred)
                ? new Option(o.itemId(), o.owned(), o.cost(), true) : o);
        return new Substitution(ingredient, options);
    }

    /**
     * Items that can stand in for this one: they share a tag that some recipe
     * accepts as an ingredient. Oak planks list birch, spruce and the others.
     */
    public List<String> interchangeable(String itemId) {
        Set<String> usedTags = usedIngredientTags();
        Set<String> result = new LinkedHashSet<>();
        for (String tag : costs.tags().tagsOf(itemId)) {
            if (!usedTags.contains(tag)) continue;
            for (String member : costs.tags().members(tag)) {
                if (!member.equals(itemId)) result.add(member);
            }
        }
        return List.copyOf(result);
    }

    /** Tags that the given item belongs to and recipes accept. */
    public List<String> ingredientTagsOf(String itemId) {
        Set<String> usedTags = usedIngredientTags();
        return costs.tags().tagsOf(itemId).stream().filter(usedTags::contains).sorted().toList();
    }

    private Set<String> usedTags;

    private Set<String> usedIngredientTags() {
        if (usedTags == null) {
            Set<String> tags = new LinkedHashSet<>();
            for (RecipeNode node : graph.allNodes()) {
                for (IngredientKey input : node.inputs()) {
                    if (input.tagBased()) tags.add(input.id());
                }
            }
            usedTags = tags;
        }
        return usedTags;
    }

    private final Map<EntryKey, Integer> stepCache = new HashMap<>();

    private int steps(RecipeNode recipe, int depth) {
        if (depth >= STEP_LIMIT) return 1;
        int deepest = 0;
        for (IngredientKey ingredient : recipe.ingredientCounts().keySet()) {
            EntryKey input = ingredient.tagBased()
                    ? costs.cheapestMember(ingredient).map(id -> new EntryKey("item", id)).orElse(null)
                    : new EntryKey(ingredient.type(), ingredient.id());
            if (input != null) deepest = Math.max(deepest, itemSteps(input, depth + 1));
        }
        return 1 + deepest;
    }

    private int itemSteps(EntryKey item, int depth) {
        Integer cached = stepCache.get(item);
        if (cached != null) return cached;
        // Placeholder guards against loops while this item is being measured.
        stepCache.put(item, 0);
        int result = costs.bestRecipe(item).map(r -> steps(r, depth)).orElse(0);
        stepCache.put(item, result);
        return result;
    }

    private static boolean hasAll(RecipeNode recipe, Inventory inventory) {
        for (Map.Entry<IngredientKey, Integer> entry : recipe.ingredientCounts().entrySet()) {
            IngredientKey ingredient = entry.getKey();
            int need = entry.getValue();
            if (ingredient.tagBased()) {
                for (String member : inventory.tags().members(ingredient.id())) {
                    need -= inventory.take(member, need);
                    if (need == 0) break;
                }
            } else {
                need -= inventory.take(ingredient.id(), need);
            }
            if (need > 0) return false;
        }
        return true;
    }
}
