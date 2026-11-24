package dev.atlasmod.core.plan;

import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.recipe.RecipeNode;

import java.util.List;

/**
 * A resolved crafting tree for one target, with everything needed to show it:
 * the tree itself, the crafting steps in order and the raw material totals.
 *
 * @param root      top of the tree
 * @param steps     crafts in the order they can be done, deepest first
 * @param materials raw materials summed over the whole tree
 * @param hasCycle  whether some branch had to stop because it looped back
 */
public record CraftingPlan(Node root, List<Step> steps, List<Material> materials, boolean hasCycle) {

    public enum Status {
        /** Already in the inventory. */
        HAVE,
        /** Gets crafted from its children. */
        CRAFT,
        /** No recipe, has to be found or farmed. */
        RAW,
        /** Recipe leads back into something already being crafted higher up. */
        CYCLE,
        /** The tree got too deep and stopped here. */
        TOO_DEEP
    }

    /**
     * One item in the tree.
     *
     * @param needed  total amount this branch asks for
     * @param fromInventory part of {@code needed} covered by the inventory
     * @param crafts  how often the recipe runs (0 unless {@link Status#CRAFT})
     * @param tagId   tag the item was picked from, or null
     */
    public record Node(EntryKey item, int needed, int fromInventory, Status status,
                       RecipeNode recipe, int crafts, String tagId, List<Node> children) {
        public Node {
            children = List.copyOf(children);
        }

        public int missing() {
            return status == Status.CRAFT ? 0 : needed - fromInventory;
        }
    }

    /** One crafting action, for example "craft 2x Stick (Crafting, 1 run)". */
    public record Step(EntryKey item, RecipeNode recipe, int crafts, int produced) {}

    /**
     * A raw material total.
     *
     * @param needed how many the whole plan uses
     * @param have   how many of those are already in the inventory
     */
    public record Material(EntryKey item, int needed, int have) {
        public int missing() {
            return Math.max(0, needed - have);
        }
    }

    /** Whether the inventory already holds the full target amount. */
    public boolean done() {
        return root.status() == Status.HAVE;
    }

    /** Share of the raw materials already collected, from 0 to 1. */
    public double progress() {
        if (done()) return 1.0;
        long needed = 0;
        long have = 0;
        for (Material m : materials) {
            needed += m.needed();
            have += Math.min(m.have(), m.needed());
        }
        return needed == 0 ? 0.0 : (double) have / needed;
    }

    public List<Material> missingMaterials() {
        return materials.stream().filter(m -> m.missing() > 0).toList();
    }
}
