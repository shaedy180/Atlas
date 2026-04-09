package dev.atlasmod.api;

import dev.atlasmod.api.registration.RecipeRegistration;
import dev.atlasmod.api.registration.CategoryRegistration;
import dev.atlasmod.core.recipe.RecipeGraph;

import java.util.Objects;

/**
 * Main entry point for mod developers integrating with Atlas.
 * <p>
 * Usage (Stufe 2 — Declarative Java Builder API):
 * <pre>{@code
 * AtlasApi.recipes("mymod:alloy_smelting")
 *     .display(inputA, inputB)
 *     .output(result)
 *     .station(machine)
 *     .time(200)
 *     .energy(1200)
 *     .unlock(stageKey)
 *     .register();
 * }</pre>
 */
public final class AtlasApi {

    private static volatile AtlasApi INSTANCE;

    private final RecipeGraph recipeGraph;

    private AtlasApi(RecipeGraph recipeGraph) {
        this.recipeGraph = recipeGraph;
    }

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

    public RecipeGraph recipeGraph() {
        return recipeGraph;
    }
}
