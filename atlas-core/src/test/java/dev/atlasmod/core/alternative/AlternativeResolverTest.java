package dev.atlasmod.core.alternative;

import dev.atlasmod.core.TestRecipes;
import dev.atlasmod.core.alternative.AlternativeResolver.Mark;
import dev.atlasmod.core.plan.CostModel;
import dev.atlasmod.core.plan.Inventory;
import dev.atlasmod.core.recipe.RecipeGraph;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static dev.atlasmod.core.TestRecipes.item;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlternativeResolverTest {

    private final RecipeGraph graph = TestRecipes.graph();
    private final AlternativeResolver resolver = new AlternativeResolver(graph, new CostModel(graph, TestRecipes.TAGS));

    @Test
    void smeltingIsTheCheapestWayToIron() {
        var ranked = resolver.rankRecipes(item("iron_ingot"), Inventory.empty());

        assertTrue(ranked.getFirst().recipe().categoryId().equals("minecraft:smelting"));
        assertTrue(ranked.getFirst().marks().contains(Mark.CHEAPEST));
        assertEquals(4, ranked.size());
    }

    @Test
    void ownedPlanksArePreferredSubstitution() {
        var inv = new Inventory(Map.of("minecraft:birch_planks", 3), TestRecipes.TAGS);
        var subs = resolver.substitutions(item("stick"), inv);

        assertEquals(1, subs.size());
        var preferred = subs.getFirst().options().stream().filter(AlternativeResolver.Option::preferred).toList();
        assertEquals("minecraft:birch_planks", preferred.getFirst().itemId());
    }

    @Test
    void planksAreInterchangeable() {
        assertEquals(java.util.List.of("minecraft:birch_planks"), resolver.interchangeable("minecraft:oak_planks"));
        assertEquals(java.util.List.of("minecraft:planks"), resolver.ingredientTagsOf("minecraft:oak_planks"));
    }

    @Test
    void craftableNowMarkUsesInventory() {
        var inv = new Inventory(Map.of("minecraft:raw_iron", 1), TestRecipes.TAGS);
        var ranked = resolver.rankRecipes(item("iron_ingot"), inv);

        assertTrue(ranked.getFirst().marks().contains(Mark.CRAFTABLE_NOW));
    }
}
