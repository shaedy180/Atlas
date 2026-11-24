package dev.atlasmod.core.plan;

import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.entry.IngredientKey;
import dev.atlasmod.core.plan.CraftingPlan.Material;
import dev.atlasmod.core.plan.CraftingPlan.Node;
import dev.atlasmod.core.plan.CraftingPlan.Status;
import dev.atlasmod.core.plan.CraftingPlan.Step;
import dev.atlasmod.core.recipe.RecipeNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Expands a target into a full crafting tree. Uses what the player already has,
 * keeps leftovers from earlier crafts (four planks from one log) for later steps,
 * stops on loops and sums up the raw materials.
 */
public final class CraftingPlanner {

    static final int MAX_DEPTH = 10;
    static final int MAX_NODES = 600;

    private final CostModel costs;

    public CraftingPlanner(CostModel costs) {
        this.costs = Objects.requireNonNull(costs);
    }

    public CraftingPlan plan(EntryKey target, int count, Inventory inventory) {
        Run run = new Run(inventory.copy());
        Node root = run.expand(target, Math.max(1, count), null, 0, new HashSet<>());

        Map<EntryKey, int[]> totals = new LinkedHashMap<>();
        collectMaterials(root, totals);
        List<Material> materials = new ArrayList<>();
        totals.forEach((item, v) -> materials.add(new Material(item, v[0], v[1])));

        return new CraftingPlan(root, mergeSteps(run.steps), materials, run.cycle);
    }

    private static void collectMaterials(Node node, Map<EntryKey, int[]> totals) {
        if (node.status() == Status.CRAFT) {
            for (Node child : node.children()) collectMaterials(child, totals);
            return;
        }
        int[] v = totals.computeIfAbsent(node.item(), k -> new int[2]);
        v[0] += node.needed();
        v[1] += node.fromInventory();
    }

    /** Same item and recipe crafted in several branches becomes one step. */
    private static List<Step> mergeSteps(List<Step> steps) {
        Map<String, Step> merged = new LinkedHashMap<>();
        for (Step step : steps) {
            String key = step.item().id() + "|" + step.recipe().id();
            merged.merge(key, step, (a, b) ->
                    new Step(a.item(), a.recipe(), a.crafts() + b.crafts(), a.produced() + b.produced()));
        }
        return List.copyOf(merged.values());
    }

    private final class Run {
        final Inventory inventory;
        final List<Step> steps = new ArrayList<>();
        boolean cycle;
        int nodes;

        Run(Inventory inventory) {
            this.inventory = inventory;
        }

        Node expand(EntryKey item, int needed, String tagId, int depth, Set<EntryKey> path) {
            nodes++;
            int have = inventory.take(item.id(), needed);
            int remaining = needed - have;
            if (remaining == 0) {
                return new Node(item, needed, have, Status.HAVE, null, 0, tagId, List.of());
            }
            if (path.contains(item)) {
                cycle = true;
                return new Node(item, needed, have, Status.CYCLE, null, 0, tagId, List.of());
            }
            if (depth >= MAX_DEPTH || nodes >= MAX_NODES) {
                return new Node(item, needed, have, Status.TOO_DEEP, null, 0, tagId, List.of());
            }
            RecipeNode recipe = pickRecipe(item, path);
            if (recipe == null) {
                return new Node(item, needed, have, Status.RAW, null, 0, tagId, List.of());
            }

            int crafts = ceilDiv(remaining, recipe.outputCount());
            path.add(item);
            List<Node> children = new ArrayList<>();
            for (Map.Entry<IngredientKey, Integer> entry : recipe.ingredientCounts().entrySet()) {
                IngredientKey ingredient = entry.getKey();
                int amount = entry.getValue() * crafts;
                EntryKey child = pick(ingredient);
                children.add(expand(child, amount, ingredient.tagBased() ? ingredient.id() : null,
                        depth + 1, path));
            }
            path.remove(item);

            int produced = crafts * recipe.outputCount();
            inventory.add(item.id(), produced - remaining);
            steps.add(new Step(item, recipe, crafts, produced));
            return new Node(item, needed, have, Status.CRAFT, recipe, crafts, tagId, children);
        }

        /** Cheapest recipe whose direct inputs do not loop back into the current branch. */
        RecipeNode pickRecipe(EntryKey item, Set<EntryKey> path) {
            RecipeNode best = costs.bestRecipe(item, inventory).orElse(null);
            if (best == null || !loopsBack(best, path)) return best;
            // The cheapest one loops in this branch, try the others by cost.
            RecipeNode fallback = null;
            double fallbackCost = Double.POSITIVE_INFINITY;
            for (RecipeNode recipe : costs.graph().recipesFor(item)) {
                if (recipe == best || loopsBack(recipe, path)) continue;
                double c = costs.recipeCost(recipe);
                if (c < fallbackCost) {
                    fallbackCost = c;
                    fallback = recipe;
                }
            }
            if (fallback == null) cycle = true;
            return fallback;
        }

        boolean loopsBack(RecipeNode recipe, Set<EntryKey> path) {
            for (IngredientKey input : recipe.ingredientCounts().keySet()) {
                if (!input.tagBased() && path.contains(CostModel.entryOf(input))) return true;
            }
            return false;
        }

        /**
         * Tag ingredients take the member the player has most of. Otherwise the
         * cheapest member, preferring one whose own inputs are at hand (oak
         * planks when you carry oak logs).
         */
        EntryKey pick(IngredientKey ingredient) {
            if (!ingredient.tagBased()) return CostModel.entryOf(ingredient);
            List<String> members = costs.tags().members(ingredient.id());
            if (members.isEmpty()) return new EntryKey("item", "#" + ingredient.id());

            String best = null;
            long bestScore = Long.MIN_VALUE;
            for (String member : members) {
                EntryKey key = new EntryKey("item", member);
                long score = inventory.count(member) * 1_000_000L;
                RecipeNode recipe = costs.bestRecipe(key, inventory).orElse(null);
                if (recipe != null) score += costs.owned(recipe, inventory) * 1_000L + costs.popularity(recipe);
                // Cheaper members win ties, the cost is scaled so it only breaks ties
                score -= Math.round(costs.cost(key) * 10);
                if (score > bestScore) {
                    bestScore = score;
                    best = member;
                }
            }
            return new EntryKey("item", best);
        }
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }
}
