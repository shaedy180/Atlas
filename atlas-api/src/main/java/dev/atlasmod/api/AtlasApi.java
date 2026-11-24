package dev.atlasmod.api;

import dev.atlasmod.api.registration.CategoryRegistration;
import dev.atlasmod.api.registration.InfoRegistration;
import dev.atlasmod.api.registration.RecipeRegistration;
import dev.atlasmod.core.category.RecipeCategory;
import dev.atlasmod.core.info.InfoRegistry;
import dev.atlasmod.core.recipe.AcquisitionSource;
import dev.atlasmod.core.recipe.RecipeGraph;
import dev.atlasmod.core.recipe.RecipeNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Main entry point for mod developers integrating with Atlas.
 * <pre>{@code
 * AtlasApi.category("mymod:alloy_smelting")
 *     .name("Alloy Smelting")
 *     .icon("mymod:alloy_furnace")
 *     .station("mymod:alloy_furnace")
 *     .recipeType("mymod:alloy_smelting")
 *     .register();
 *
 * AtlasApi.recipes("mymod:alloy_smelting")
 *     .display(inputA, inputB)
 *     .output(result, 2)
 *     .time(200)
 *     .energy(1200)
 *     .register();
 * }</pre>
 * See the "Mod API" section of the README for the full guide.
 */
public final class AtlasApi {

    private static volatile AtlasApi INSTANCE;

    private final RecipeGraph recipeGraph;
    private final InfoRegistry infoRegistry = new InfoRegistry();
    private final Map<String, RecipeCategory> categories = new LinkedHashMap<>();
    private final List<RecipeNode> registeredRecipes = new ArrayList<>();
    private final List<RecipeNode> worldRecipes = new ArrayList<>();
    private final Map<String, List<AcquisitionSource>> sources = new LinkedHashMap<>();
    private final List<Supplier<? extends Collection<String>>> stageProviders = new CopyOnWriteArrayList<>();
    private boolean worldScope;

    private AtlasApi(RecipeGraph recipeGraph) {
        this.recipeGraph = recipeGraph;
    }

    /** Called once by Atlas during client start. */
    public static void init(RecipeGraph graph) {
        Objects.requireNonNull(graph, "graph must not be null");
        INSTANCE = new AtlasApi(graph);
    }

    public static AtlasApi get() {
        if (INSTANCE == null) {
            throw new IllegalStateException("Atlas API has not been initialized yet");
        }
        return INSTANCE;
    }

    /** Whether Atlas is installed and started. Useful for optional integrations. */
    public static boolean isReady() {
        return INSTANCE != null;
    }

    /**
     * Begin building recipes for a category.
     */
    public static RecipeRegistration recipes(String categoryId) {
        Objects.requireNonNull(categoryId, "categoryId must not be null");
        return new RecipeRegistration(get().recipeGraph, categoryId);
    }

    /**
     * Begin registering a new recipe category.
     */
    public static CategoryRegistration category(String categoryId) {
        Objects.requireNonNull(categoryId, "categoryId must not be null");
        return new CategoryRegistration(categoryId);
    }

    /**
     * Begin an info page. Shown in the Notes tab of every target item.
     */
    public static InfoRegistration info(String pageId) {
        Objects.requireNonNull(pageId, "pageId must not be null");
        return new InfoRegistration(get().infoRegistry, pageId);
    }

    /**
     * Adds a way to obtain an item that is not a recipe, for example a mob drop
     * or a machine output. Shown in the Sources tab.
     */
    public static void addSource(String itemId, AcquisitionSource source) {
        Objects.requireNonNull(itemId, "itemId must not be null");
        Objects.requireNonNull(source, "source must not be null");
        AtlasApi api = get();
        synchronized (api.sources) {
            api.sources.computeIfAbsent(itemId, k -> new ArrayList<>()).add(source);
        }
    }

    /**
     * Reports extra progression stages (for example from a stage mod). They satisfy
     * {@code UnlockCondition.RequiresProgression} conditions with the same key.
     */
    public static void stageProvider(Supplier<? extends Collection<String>> provider) {
        get().stageProviders.add(Objects.requireNonNull(provider, "provider must not be null"));
    }

    public RecipeGraph recipeGraph() {
        return recipeGraph;
    }

    public InfoRegistry infoRegistry() {
        return infoRegistry;
    }

    /** Every registered category by id. */
    public Map<String, RecipeCategory> categories() {
        synchronized (categories) {
            for (RecipeCategory category : CategoryRegistration.drainPending()) {
                categories.put(category.id(), category);
            }
            return Map.copyOf(categories);
        }
    }

    public Optional<RecipeCategory> categoryById(String id) {
        return Optional.ofNullable(categories().get(id));
    }

    /** The category that imports recipes of the given recipe type, if any. */
    public Optional<RecipeCategory> categoryForRecipeType(String recipeTypeId) {
        return categories().values().stream()
                .filter(c -> c.recipeTypes().contains(recipeTypeId))
                .findFirst();
    }

    public List<AcquisitionSource> sourcesFor(String itemId) {
        synchronized (sources) {
            return List.copyOf(sources.getOrDefault(itemId, List.of()));
        }
    }

    /** Stages from all providers. Broken providers are skipped. */
    public Set<String> stages() {
        Set<String> result = new HashSet<>();
        for (var provider : stageProviders) {
            try {
                Collection<String> stages = provider.get();
                if (stages != null) result.addAll(stages);
            } catch (RuntimeException ignored) {
                // A misbehaving integration must not break the UI
            }
        }
        return result;
    }

    /** Adds a recipe built through {@link RecipeRegistration}. */
    public synchronized void addRecipe(RecipeNode node) {
        (worldScope ? worldRecipes : registeredRecipes).add(node);
        recipeGraph.addNode(node);
    }

    /**
     * Re-adds recipes from the API after Atlas rebuilt its graph.
     * Called by Atlas, not meant for integrations.
     */
    public synchronized void restoreRecipes() {
        registeredRecipes.forEach(recipeGraph::addNode);
        worldRecipes.forEach(recipeGraph::addNode);
    }

    /**
     * Marks registrations made until {@link #endWorldScope()} as belonging to the
     * current world. Called by Atlas around {@code AtlasPlugin.onWorldLoad()}.
     */
    public synchronized void beginWorldScope() {
        worldScope = true;
    }

    public synchronized void endWorldScope() {
        worldScope = false;
    }

    /** Drops recipes registered for the world that was left. */
    public synchronized void clearWorldScope() {
        worldRecipes.clear();
    }
}
