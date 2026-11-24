package dev.atlasmod.api.plugin;

/**
 * Entry point for Atlas integrations provided by other mods.
 * <p>
 * Fabric: list the class under the {@code "atlas"} entrypoint in fabric.mod.json.
 * NeoForge: annotate the class with {@link AtlasEntrypoint}.
 */
public interface AtlasPlugin {

    /**
     * Called once when Atlas is ready to accept registrations.
     * Register your categories, recipes, info pages, etc. here.
     */
    void onAtlasReady();

    /**
     * Called every time a world finished loading and Atlas has read its recipes.
     * Use it for data that depends on the world, like datapack driven recipes.
     * Recipes registered here are removed again when the world is left.
     */
    default void onWorldLoad() {
    }
}
