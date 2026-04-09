package dev.atlasmod.core.transfer;

import dev.atlasmod.core.entry.IngredientKey;
import dev.atlasmod.core.entry.StationKey;
import dev.atlasmod.core.recipe.RecipeNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Resolves TransferSpec objects from RecipeNodes.
 * Given a recipe, determines what ingredients go where in the target
 * container/machine. The actual slot interaction is platform-specific
 * and handled by the loader module.
 */
public final class TransferResolver {

    /**
     * Result of resolving a transfer.
     *
     * @param success    whether the transfer can be performed
     * @param spec       the resolved transfer specification (null if not possible)
     * @param missing    list of ingredient IDs the player doesn't have
     */
    public record TransferResult(boolean success, TransferSpec spec, List<String> missing) {
        public TransferResult {
            missing = missing == null ? List.of() : List.copyOf(missing);
        }
    }

    /**
     * Creates a TransferSpec from a recipe node. Does not check inventory;
     * the caller is responsible for checking whether the player has ingredients.
     */
    public TransferSpec resolve(RecipeNode recipe) {
        Objects.requireNonNull(recipe, "recipe must not be null");

        StationKey station = recipe.station().orElse(null);
        if (station == null) {
            // Freeform recipes (like hand crafting 2x2) use crafting table as default
            station = new StationKey("minecraft:crafting_table");
        }

        List<IngredientKey> ingredients = recipe.inputs();

        // If the recipe has a placement pattern, generate slot hints
        // For now, use sequential slot ordering since we don't have
        // container layout info at this layer
        List<Integer> slotHints = new ArrayList<>();
        for (int i = 0; i < ingredients.size(); i++) {
            slotHints.add(i);
        }

        return new TransferSpec(station, ingredients, slotHints);
    }

    /**
     * Creates a TransferSpec and checks which ingredients the player is missing.
     *
     * @param recipe          the recipe to transfer
     * @param inventoryItems  item IDs the player currently has
     * @return a TransferResult containing the spec and any missing ingredients
     */
    public TransferResult resolveWithInventory(RecipeNode recipe, java.util.Set<String> inventoryItems) {
        TransferSpec spec = resolve(recipe);

        List<String> missing = new ArrayList<>();
        for (IngredientKey ingredient : spec.ingredients()) {
            if (!inventoryItems.contains(ingredient.id())) {
                missing.add(ingredient.id());
            }
        }

        return new TransferResult(missing.isEmpty(), spec, missing);
    }
}
