package dev.atlasmod.core.recipe;

import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.unlock.UnlockCondition;
import dev.atlasmod.core.visibility.VisibilityPolicy;

import java.util.Objects;
import java.util.Optional;

/**
 * Describes how an item can be acquired beyond crafting recipes.
 * This is central to Atlas's "How do I get this?" philosophy.
 */
public record AcquisitionSource(
        String id,
        String ownerModId,
        EntryKey entry,
        SourceType type,
        String description,
        String detail,
        UnlockCondition unlockCondition,
        VisibilityPolicy visibilityPolicy,
        boolean renewable
) {
    public AcquisitionSource {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(ownerModId, "ownerModId must not be null");
        Objects.requireNonNull(entry, "entry must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(description, "description must not be null");
        visibilityPolicy = visibilityPolicy == null ? VisibilityPolicy.VISIBLE : visibilityPolicy;
    }

    public Optional<String> optionalDetail() {
        return Optional.ofNullable(detail);
    }

    public Optional<UnlockCondition> optionalUnlockCondition() {
        return Optional.ofNullable(unlockCondition);
    }

    public enum SourceType {
        CRAFTING,
        SMELTING,
        BLASTING,
        SMOKING,
        STONECUTTING,
        SMITHING,
        BREWING,
        CAMPFIRE,
        MACHINE,
        MOB_DROP,
        BLOCK_DROP,
        VILLAGER_TRADE,
        LOOT_TABLE,
        WORLDGEN,
        FISHING,
        COMPOSTING,
        FUEL,
        SALVAGE,
        CUSTOM
    }
}
