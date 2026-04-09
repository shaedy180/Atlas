package dev.atlasmod.core.recipe;

/**
 * Describes how an item can be acquired beyond crafting recipes.
 * This is central to Atlas's "How do I get this?" philosophy.
 */
public record AcquisitionSource(SourceType type, String description, String detail) {

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
