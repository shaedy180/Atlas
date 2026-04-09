package dev.atlasmod.fabric;

import dev.atlasmod.api.AtlasApi;
import dev.atlasmod.core.recipe.RecipeGraph;
import dev.atlasmod.core.invalidation.InvalidationEngine;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric client entry point for Atlas.
 */
public final class AtlasFabricClient implements ClientModInitializer {

    public static final String MOD_ID = "atlas";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static RecipeGraph recipeGraph;
    private static InvalidationEngine invalidationEngine;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Atlas] Initializing Atlas — Recipe & Item Intelligence Layer");

        recipeGraph = new RecipeGraph();
        invalidationEngine = new InvalidationEngine();

        AtlasApi.init(recipeGraph);

        // Register vanilla recipe categories
        VanillaRecipeLoader.registerCategories();

        // Key bindings
        AtlasKeyBindings.register();

        LOGGER.info("[Atlas] Initialization complete. Graph ready for registration.");
    }

    public static RecipeGraph recipeGraph() { return recipeGraph; }
    public static InvalidationEngine invalidationEngine() { return invalidationEngine; }
}
