package dev.atlasmod.core.transfer;

import dev.atlasmod.core.entry.IngredientKey;
import dev.atlasmod.core.entry.StationKey;

import java.util.List;

/**
 * Describes how to transfer recipe ingredients to a container/machine.
 *
 * @param station     the target station/container
 * @param ingredients ordered ingredients to place
 * @param slotHints   optional slot mappings (empty list = auto)
 */
public record TransferSpec(
        StationKey station,
        List<IngredientKey> ingredients,
        List<Integer> slotHints
) {
    public TransferSpec {
        ingredients = List.copyOf(ingredients);
        slotHints = slotHints == null ? List.of() : List.copyOf(slotHints);
    }
}
