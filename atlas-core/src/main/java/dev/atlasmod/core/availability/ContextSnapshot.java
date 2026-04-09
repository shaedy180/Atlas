package dev.atlasmod.core.availability;

import java.util.Objects;
import java.util.Set;

/**
 * Immutable snapshot of the player's current context used by the
 * AvailabilityEngine to evaluate UnlockConditions.
 * Captured once per evaluation cycle and shared across all checks.
 */
public record ContextSnapshot(
        String dimensionId,
        String biomeId,
        Set<String> advancements,
        Set<String> inventoryItemIds,
        Set<String> nearbyBlockIds
) {
    public ContextSnapshot {
        Objects.requireNonNull(dimensionId);
        Objects.requireNonNull(biomeId);
        advancements = Set.copyOf(advancements);
        inventoryItemIds = Set.copyOf(inventoryItemIds);
        nearbyBlockIds = Set.copyOf(nearbyBlockIds);
    }

    /**
     * Returns a minimal empty snapshot for when no player context is available.
     */
    public static ContextSnapshot empty() {
        return new ContextSnapshot("", "", Set.of(), Set.of(), Set.of());
    }
}
