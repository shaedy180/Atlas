package dev.atlasmod.core.invalidation;

/**
 * Events that trigger cache invalidation in Atlas subsystems.
 */
public enum InvalidationEvent {
    DATAPACK_RELOAD,
    RECIPE_RELOAD,
    TAG_RELOAD,
    CONFIG_RELOAD,
    INVENTORY_CHANGE,
    WORLD_JOIN,
    WORLD_LEAVE
}
