package dev.atlasmod.fabric;

import dev.atlasmod.api.AtlasApi;
import dev.atlasmod.core.invalidation.InvalidationEngine;
import dev.atlasmod.core.invalidation.InvalidationEvent;
import dev.atlasmod.core.recipe.RecipeGraph;
import dev.atlasmod.search.SearchIndex;
import dev.atlasmod.ui.PinnedPlanManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Fabric client entry point for Atlas.
 * Registers events, keybindings, categories, and hooks into world join
 * for recipe loading and search index population.
 */
public final class AtlasFabricClient implements ClientModInitializer {

    public static final String MOD_ID = "atlas";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static RecipeGraph recipeGraph;
    private static InvalidationEngine invalidationEngine;
    private static SearchIndex searchIndex;
    private static PinnedPlanManager pinnedPlanManager;

    private static final String PINS_FILE = "atlas_pins.txt";

    // Guard: load recipes once per world join (reset on disconnect)
    private static boolean recipesLoaded = false;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Atlas] Initializing Atlas - Recipe & Item Intelligence Layer");

        recipeGraph = new RecipeGraph();
        invalidationEngine = new InvalidationEngine();
        searchIndex = new SearchIndex();
        pinnedPlanManager = new PinnedPlanManager();

        AtlasApi.init(recipeGraph);

        // Register vanilla recipe categories
        VanillaRecipeLoader.registerCategories();

        // Key bindings
        AtlasKeyBindings.register();

        // Client commands (/atlas debug, /atlas stats, /atlas reload)
        AtlasCommands.register();

        // Discover and invoke third-party Atlas plugins
        AtlasPluginLoader.loadAll();

        // Quick Mode overlay on inventory screens
        QuickModeOverlay.register();

        // Hook: load recipes when we join a world.
        // The integrated server needs a tick to fully initialize recipes,
        // so we defer to the first client tick after play connection is ready.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            recipesLoaded = false;
            LOGGER.debug("[Atlas] Play connection joined, scheduling recipe load");
            // Load pinned plans
            try {
                pinnedPlanManager.load(pinsPath());
                LOGGER.debug("[Atlas] Loaded {} pinned plans", pinnedPlanManager.plans().size());
            } catch (Exception e) {
                LOGGER.warn("[Atlas] Failed to load pinned plans", e);
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            // Save pinned plans before clearing state
            try {
                pinnedPlanManager.save(pinsPath());
                LOGGER.debug("[Atlas] Saved {} pinned plans", pinnedPlanManager.plans().size());
            } catch (Exception e) {
                LOGGER.warn("[Atlas] Failed to save pinned plans", e);
            }
            recipesLoaded = false;
            recipeGraph.clear();
            searchIndex.clear();
            invalidationEngine.fire(InvalidationEvent.WORLD_LEAVE);
            LOGGER.debug("[Atlas] Disconnected, cleared recipe graph and search index");
        });

        // On the first tick after join, load recipes from the integrated server
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (!recipesLoaded && mc.level != null) {
                recipesLoaded = true;
                int count = VanillaRecipeLoader.loadRecipes();
                AtlasItemIndexer.buildIndex(searchIndex);
                invalidationEngine.fire(InvalidationEvent.WORLD_JOIN);
                LOGGER.info("[Atlas] World ready: {} recipes, {} items indexed",
                        count, searchIndex.size());
            }
        });

        // Key binding tick handler for opening screens
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (AtlasKeyBindings.openAtlas.consumeClick()) {
                mc.setScreen(new AtlasScreen());
            }

            // Quick mode only makes sense in-world on container screens.
            if (mc.level != null && AtlasKeyBindings.toggleQuickMode.consumeClick()) {
                QuickModeOverlay.toggle();
            }
        });

        LOGGER.info("[Atlas] Initialization complete. Waiting for world join.");
    }

    public static RecipeGraph recipeGraph() { return recipeGraph; }
    public static InvalidationEngine invalidationEngine() { return invalidationEngine; }
    public static SearchIndex searchIndex() { return searchIndex; }
    public static PinnedPlanManager pinnedPlanManager() { return pinnedPlanManager; }

    private static Path pinsPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(PINS_FILE);
    }
}
