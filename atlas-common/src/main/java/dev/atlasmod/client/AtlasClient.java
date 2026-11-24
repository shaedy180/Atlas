package dev.atlasmod.client;

import dev.atlasmod.api.AtlasApi;
import dev.atlasmod.api.plugin.AtlasPlugin;
import dev.atlasmod.client.compat.McCompat;
import dev.atlasmod.core.invalidation.InvalidationEngine;
import dev.atlasmod.core.invalidation.InvalidationEvent;
import dev.atlasmod.core.recipe.RecipeGraph;
import dev.atlasmod.search.SearchIndex;
import dev.atlasmod.ui.NoteStore;
import dev.atlasmod.ui.PinnedPlanManager;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loader independent client state and lifecycle. The Fabric and NeoForge entry
 * points only forward their events here.
 */
public final class AtlasClient {

    public static final String MOD_ID = "atlas";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final String SAVED_FILE = "atlas_pins.txt";
    private static final String PLANS_FILE = "atlas_plans.txt";
    private static final String NOTES_FILE = "atlas_notes.txt";
    private static final String PREFS_FILE = "atlas_prefs.txt";

    /** A plugin found by the loader, with the id of the mod that provides it. */
    public record PluginEntry(String modId, AtlasPlugin plugin) {}

    private static RecipeGraph recipeGraph;
    private static InvalidationEngine invalidationEngine;
    private static SearchIndex searchIndex;
    private static PinnedPlanManager savedItems;
    private static PinnedPlanManager buildPlans;
    private static NoteStore notes;
    private static ClientContext context;
    private static RegistryTags tags = new RegistryTags();
    private static Insights insights;
    private static final List<PluginEntry> plugins = new ArrayList<>();

    // Load recipes once per world join (reset on disconnect)
    private static boolean worldLoaded;
    // Saving is only allowed after the files were read, so a failed connect can not wipe them
    private static boolean dataLoaded;

    private AtlasClient() {}

    public static void init() {
        LOGGER.info("[Atlas] Initializing Atlas - Recipe & Item Intelligence Layer");
        recipeGraph = new RecipeGraph();
        invalidationEngine = new InvalidationEngine();
        searchIndex = new SearchIndex();
        savedItems = new PinnedPlanManager();
        buildPlans = new PinnedPlanManager();
        notes = new NoteStore();
        context = new ClientContext();

        AtlasApi.init(recipeGraph);
        VanillaRecipeLoader.registerCategories();
    }

    /** Calls {@code onAtlasReady()} on every plugin. A broken plugin is logged and skipped. */
    public static void loadPlugins(List<PluginEntry> found) {
        int loaded = 0;
        for (PluginEntry entry : found) {
            try {
                entry.plugin().onAtlasReady();
                plugins.add(entry);
                loaded++;
                LOGGER.info("[Atlas] Loaded plugin from mod '{}'", entry.modId());
            } catch (Throwable t) {
                // Catch everything including LinkageError so one broken plugin
                // does not take down the rest of the mod
                LOGGER.error("[Atlas] Plugin from mod '{}' threw during onAtlasReady(), skipping", entry.modId(), t);
            }
        }
        if (!found.isEmpty()) LOGGER.info("[Atlas] {} of {} third-party plugins loaded", loaded, found.size());
    }

    // -- Lifecycle, called by the loader -------------------------------------

    public static void onJoin() {
        worldLoaded = false;
        try {
            savedItems.load(file(SAVED_FILE));
            buildPlans.load(file(PLANS_FILE));
            notes.load(file(NOTES_FILE));
            dataLoaded = true;
        } catch (Exception e) {
            LOGGER.warn("[Atlas] Failed to load saved data, changes will not be saved this session", e);
        }
        QuickModeOverlay.loadPrefs(file(PREFS_FILE));
    }

    public static void onLeave() {
        saveAll();
        if (dataLoaded) QuickModeOverlay.savePrefs(file(PREFS_FILE));
        worldLoaded = false;
        recipeGraph.clear();
        searchIndex.clear();
        context.clear();
        ItemStacks.clearCache();
        insights = null;
        AtlasApi.get().clearWorldScope();
        invalidationEngine.fire(InvalidationEvent.WORLD_LEAVE);
    }

    public static void onTick(Minecraft mc) {
        if (!worldLoaded && mc.level != null) {
            worldLoaded = true;
            reload();
            invalidationEngine.fire(InvalidationEvent.WORLD_JOIN);
        }
        if (mc.level == null) return;
        if (AtlasKeyBindings.openAtlas.consumeClick()) {
            McCompat.openScreen(new AtlasScreen());
        }
        if (AtlasKeyBindings.toggleQuickMode.consumeClick()) {
            QuickModeOverlay.toggle();
        }
    }

    /** Reads recipes, tags, info pages and the search index again. Returns the recipe count. */
    public static int reload() {
        AtlasApi.get().clearWorldScope();
        tags = RegistryTags.snapshot();
        ItemStacks.clearCache();
        int count = VanillaRecipeLoader.loadRecipes();
        runWorldPlugins();
        AtlasItemIndexer.buildIndex(searchIndex);
        int pages = 0;
        try {
            pages = InfoPageLoader.reload(AtlasApi.get().infoRegistry());
        } catch (RuntimeException e) {
            LOGGER.warn("[Atlas] Could not read info pages", e);
        }
        insights = new Insights(recipeGraph, tags);
        context.invalidate();
        QuickModeOverlay.onReload();
        invalidationEngine.fire(InvalidationEvent.RECIPE_RELOAD);
        LOGGER.info("[Atlas] World ready: {} recipes, {} items indexed, {} tags, {} info pages",
                recipeGraph.size(), searchIndex.size(), tags.tagCount(), pages);
        return count;
    }

    private static void runWorldPlugins() {
        AtlasApi api = AtlasApi.get();
        api.beginWorldScope();
        try {
            for (PluginEntry entry : plugins) {
                try {
                    entry.plugin().onWorldLoad();
                } catch (Throwable t) {
                    LOGGER.error("[Atlas] Plugin from mod '{}' threw during onWorldLoad()", entry.modId(), t);
                }
            }
        } finally {
            api.endWorldScope();
        }
    }

    public static void saveAll() {
        if (!dataLoaded) return;
        try {
            savedItems.save(file(SAVED_FILE));
            buildPlans.save(file(PLANS_FILE));
            notes.save(file(NOTES_FILE));
        } catch (Exception e) {
            LOGGER.warn("[Atlas] Failed to save data", e);
        }
    }

    private static Path file(String name) {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(name);
    }

    // -- Accessors -----------------------------------------------------------

    public static RecipeGraph recipeGraph() { return recipeGraph; }
    public static InvalidationEngine invalidationEngine() { return invalidationEngine; }
    public static SearchIndex searchIndex() { return searchIndex; }
    public static PinnedPlanManager savedItems() { return savedItems; }
    public static PinnedPlanManager buildPlans() { return buildPlans; }
    public static NoteStore notes() { return notes; }
    public static ClientContext context() { return context; }
    public static RegistryTags tags() { return tags; }
    public static List<PluginEntry> plugins() { return List.copyOf(plugins); }

    /** Analysis services. Before the first world load they run on an empty graph. */
    public static Insights insights() {
        if (insights == null) insights = new Insights(recipeGraph, tags);
        return insights;
    }
}
