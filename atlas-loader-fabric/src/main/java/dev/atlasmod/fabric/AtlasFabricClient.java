package dev.atlasmod.fabric;

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
    private static final String PREFS_FILE = "atlas_prefs.txt";

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Atlas] Initializing Atlas - Recipe & Item Intelligence Layer");

        recipeGraph = new RecipeGraph();
        invalidationEngine = new InvalidationEngine();
        searchIndex = new SearchIndex();
        pinnedPlanManager = new PinnedPlanManager();

        AtlasNetworking.registerClient();

        // Key bindings
        AtlasKeyBindings.register();

        // Client commands (/atlas debug, /atlas stats, /atlas reload)
        AtlasCommands.register();

        // Quick Mode overlay on inventory screens
        QuickModeOverlay.register();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            // Load pinned plans
            try {
                pinnedPlanManager.load(pinsPath());
                LOGGER.debug("[Atlas] Loaded {} pinned plans", pinnedPlanManager.plans().size());
            } catch (Exception e) {
                LOGGER.warn("[Atlas] Failed to load pinned plans", e);
            }
            // Load quick mode preferences
            QuickModeOverlay.loadPrefs(prefsPath());
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            // Save pinned plans before clearing state
            try {
                pinnedPlanManager.save(pinsPath());
                LOGGER.debug("[Atlas] Saved {} pinned plans", pinnedPlanManager.plans().size());
            } catch (Exception e) {
                LOGGER.warn("[Atlas] Failed to save pinned plans", e);
            }
            // Save quick mode preferences
            QuickModeOverlay.savePrefs(prefsPath());
            AtlasRuntimeController.clearClientSnapshot();
            invalidationEngine.fire(InvalidationEvent.WORLD_LEAVE);
            LOGGER.debug("[Atlas] Disconnected, cleared recipe graph and search index");
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

    private static Path prefsPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(PREFS_FILE);
    }
}
