package dev.atlasmod.api;

import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.entry.IngredientKey;
import dev.atlasmod.core.recipe.RecipeGraph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtlasApiTest {

    @Test
    void registeredRecipesSurviveAGraphRebuildAndUseTheCategoryStation() {
        RecipeGraph graph = new RecipeGraph();
        AtlasApi.init(graph);
        AtlasApi.category("test:alloy").name("Alloy").station("test:alloy_furnace").recipeType("test:alloy").register();

        AtlasApi.recipes("test:alloy").id("test:bronze")
                .input(IngredientKey.item("test:copper"), 3)
                .display(IngredientKey.item("test:tin"))
                .output(new EntryKey("item", "test:bronze"), 4)
                .register();

        graph.clear();
        AtlasApi.get().restoreRecipes();

        var node = graph.getNode("test:bronze").orElseThrow();
        assertEquals(4, node.outputCount());
        assertEquals("test:alloy_furnace", node.station().orElseThrow().id());
        assertEquals(3, node.ingredientCounts().get(IngredientKey.item("test:copper")));
        assertTrue(AtlasApi.get().categoryForRecipeType("test:alloy").isPresent());
    }

    @Test
    void worldScopedRecipesAreDroppedOnLeave() {
        RecipeGraph graph = new RecipeGraph();
        AtlasApi.init(graph);
        AtlasApi.get().beginWorldScope();
        AtlasApi.recipes("test:alloy").id("test:temp").output(new EntryKey("item", "test:x")).register();
        AtlasApi.get().endWorldScope();

        AtlasApi.get().clearWorldScope();
        graph.clear();
        AtlasApi.get().restoreRecipes();
        assertTrue(graph.getNode("test:temp").isEmpty());
    }

    @Test
    void infoPagesNeedTargets() {
        AtlasApi.init(new RecipeGraph());
        AtlasApi.info("test:tip").title("Tip").line("Hello").forItems("test:x", "#test:tag").register();
        assertEquals(List.of("test:x", "#test:tag"), AtlasApi.get().infoRegistry().all().iterator().next().targets());
    }
}
