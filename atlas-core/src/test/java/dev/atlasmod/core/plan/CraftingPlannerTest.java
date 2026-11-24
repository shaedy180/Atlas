package dev.atlasmod.core.plan;

import dev.atlasmod.core.TestRecipes;
import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.plan.CraftingPlan.Material;
import dev.atlasmod.core.plan.CraftingPlan.Status;
import dev.atlasmod.core.recipe.RecipeGraph;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static dev.atlasmod.core.TestRecipes.item;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftingPlannerTest {

    private final RecipeGraph graph = TestRecipes.graph();
    private final CostModel costs = new CostModel(graph, TestRecipes.TAGS);
    private final CraftingPlanner planner = new CraftingPlanner(costs);

    private static Map<String, Integer> missing(CraftingPlan plan) {
        return plan.materials().stream().collect(Collectors.toMap(m -> m.item().id(), Material::missing));
    }

    @Test
    void woodenPickaxeNeedsTwoLogsAndReusesLeftoverPlanks() {
        CraftingPlan plan = planner.plan(item("wooden_pickaxe"), 1, Inventory.empty());

        // 3 planks for the head plus 2 for sticks: two logs give 8 planks.
        assertEquals(Map.of("minecraft:oak_log", 2), missing(plan));
        assertEquals(Status.CRAFT, plan.root().status());
        assertFalse(plan.hasCycle());
        assertFalse(plan.done());
    }

    @Test
    void ironIngotsComeFromSmeltingNotFromBlocksOrNuggets() {
        CraftingPlan plan = planner.plan(item("iron_pickaxe"), 1, Inventory.empty());

        assertEquals(3, missing(plan).getOrDefault("minecraft:raw_iron", 0) + missing(plan).getOrDefault("minecraft:iron_ore", 0));
        assertFalse(missing(plan).containsKey("minecraft:iron_block"));
        assertFalse(missing(plan).containsKey("minecraft:iron_nugget"));
    }

    @Test
    void inventoryIsUsedBeforeCrafting() {
        Inventory inv = new Inventory(Map.of("minecraft:stick", 2, "minecraft:iron_ingot", 1), TestRecipes.TAGS);
        CraftingPlan plan = planner.plan(item("iron_pickaxe"), 1, inv);

        assertEquals(2, missing(plan).getOrDefault("minecraft:raw_iron", 0) + missing(plan).getOrDefault("minecraft:iron_ore", 0));
        assertFalse(missing(plan).containsKey("minecraft:oak_log"));
        assertTrue(plan.progress() > 0 && plan.progress() < 1);
    }

    @Test
    void equallyCheapRecipesPreferWhatThePlayerHas() {
        Inventory withOre = new Inventory(Map.of("minecraft:iron_ore", 3), TestRecipes.TAGS);
        Inventory withRaw = new Inventory(Map.of("minecraft:raw_iron", 3), TestRecipes.TAGS);

        assertTrue(missing(planner.plan(item("iron_pickaxe"), 1, withOre)).getOrDefault("minecraft:iron_ore", 1) == 0);
        assertTrue(missing(planner.plan(item("iron_pickaxe"), 1, withRaw)).getOrDefault("minecraft:raw_iron", 1) == 0);
    }

    @Test
    void ownedTargetIsDone() {
        Inventory inv = new Inventory(Map.of("minecraft:iron_pickaxe", 1), TestRecipes.TAGS);
        CraftingPlan plan = planner.plan(item("iron_pickaxe"), 1, inv);

        assertTrue(plan.done());
        assertEquals(1.0, plan.progress());
    }

    @Test
    void tagIngredientsPreferWhatThePlayerOwns() {
        Inventory inv = new Inventory(Map.of("minecraft:birch_planks", 1), TestRecipes.TAGS);
        CraftingPlan plan = planner.plan(item("stick"), 4, inv);

        var children = plan.root().children();
        assertEquals("minecraft:birch_planks", children.getFirst().item().id());
        assertEquals("minecraft:planks", children.getFirst().tagId());
    }

    @Test
    void diamondHasNoRecipeOnlyALoop() {
        CraftingPlan plan = planner.plan(item("diamond_block"), 1, Inventory.empty());

        assertEquals(Map.of("minecraft:diamond", 9), missing(plan));
        assertEquals(Status.RAW, plan.root().children().getFirst().status());
    }

    @Test
    void stepsAreOrderedDeepestFirst() {
        CraftingPlan plan = planner.plan(item("wooden_pickaxe"), 1, Inventory.empty());
        var order = plan.steps().stream().map(s -> s.item().id()).toList();

        assertEquals("minecraft:wooden_pickaxe", order.getLast());
        assertTrue(order.indexOf("minecraft:oak_planks") < order.indexOf("minecraft:stick"));
    }

    @Test
    void costModelKnowsUnpackingIsNotASource() {
        assertEquals(1.0, costs.cost(new EntryKey("item", "minecraft:diamond")));
        assertEquals(9.0, costs.cost(new EntryKey("item", "minecraft:diamond_block")));
        assertEquals(1.0, costs.cost(new EntryKey("item", "minecraft:iron_ingot")));
        assertEquals(1.0 / 9, costs.cost(new EntryKey("item", "minecraft:iron_nugget")), 1e-9);
    }
}
