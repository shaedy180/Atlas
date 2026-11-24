package dev.atlasmod.core.availability;

import dev.atlasmod.core.TestRecipes;
import dev.atlasmod.core.availability.AvailabilityEngine.BlockerKind;
import dev.atlasmod.core.entry.IngredientKey;
import dev.atlasmod.core.recipe.RecipeNode;
import dev.atlasmod.core.unlock.UnlockCondition;
import dev.atlasmod.core.visibility.VisibilityPolicy;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static dev.atlasmod.core.TestRecipes.in;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvailabilityEngineTest {

    private final AvailabilityEngine engine = new AvailabilityEngine(TestRecipes.TAGS, Function.identity());

    private static ContextSnapshot context(Map<String, Integer> inventory, Set<String> nearby) {
        return new ContextSnapshot("minecraft:overworld", "minecraft:plains", Set.of("minecraft:is_overworld"),
                Set.of(), inventory, nearby, Set.of(), false, Set.of());
    }

    private static RecipeNode stick() {
        return TestRecipes.recipe("stick", "stick", 4,
                IngredientKey.tag("minecraft:planks"), IngredientKey.tag("minecraft:planks"));
    }

    @Test
    void readyWhenStationAndMaterialsArePresent() {
        var ctx = context(Map.of("minecraft:oak_planks", 1, "minecraft:birch_planks", 1),
                Set.of("minecraft:crafting_table"));
        var result = engine.evaluateFully(stick(), ctx);

        assertTrue(result.craftableNow());
        assertEquals(VisibilityPolicy.VISIBLE, result.policy());
    }

    @Test
    void explainsMissingStationAndMaterials() {
        var ctx = context(Map.of("minecraft:oak_planks", 1, "minecraft:crafting_table", 1), Set.of());
        var result = engine.evaluateFully(stick(), ctx);

        assertFalse(result.available());
        assertEquals(BlockerKind.STATION, result.details().getFirst().kind());
        assertTrue(result.blockers().getFirst().contains("place it"));
        assertEquals(1, result.missingMaterials().getFirst().amount());
    }

    @Test
    void materialsAloneDoNotMakeARecipeUnavailable() {
        var result = engine.evaluateFully(stick(), context(Map.of(), Set.of("minecraft:crafting_table")));

        assertTrue(result.available());
        assertFalse(result.craftableNow());
        assertTrue(result.blockers().isEmpty());
    }

    @Test
    void dimensionBiomeTagAndCustomChecks() {
        RecipeNode recipe = RecipeNode.builder("test:portal", "test:ritual")
                .inputs(java.util.List.of(in("obsidian")))
                .unlockCondition(UnlockCondition.and(
                        new UnlockCondition.RequiresDimension("minecraft:the_nether"),
                        UnlockCondition.and(new UnlockCondition.RequiresBiome("#minecraft:is_overworld"),
                                new UnlockCondition.Check("Only at night", ctx -> false))))
                .build();
        var result = engine.evaluateFully(recipe, context(Map.of("minecraft:obsidian", 1), Set.of()));

        assertEquals(2, result.blockers().size());
        assertEquals(BlockerKind.DIMENSION, result.details().get(0).kind());
        assertEquals("Only at night", result.details().get(1).message());
        assertEquals(VisibilityPolicy.GREYED_OUT, result.policy());
    }

    @Test
    void progressionHidesAndStagesUnlock() {
        RecipeNode recipe = RecipeNode.builder("test:late", "test:magic")
                .unlockCondition(new UnlockCondition.RequiresProgression("stage_two")).build();
        var locked = engine.evaluate(recipe, ContextSnapshot.empty());
        var unlocked = engine.evaluate(recipe, new ContextSnapshot("", "", Set.of(), Set.of(), Map.of(),
                Set.of(), Set.of(), false, Set.of("stage_two")));

        assertEquals(VisibilityPolicy.HIDDEN, locked.policy());
        assertTrue(unlocked.available());
    }

    @Test
    void lockedRecipeOnlyBlocksWithLimitedCrafting() {
        var open = new ContextSnapshot("", "", Set.of(), Set.of(), Map.of(), Set.of("minecraft:crafting_table"),
                Set.of("minecraft:stick"), false, Set.of());
        var limited = new ContextSnapshot("", "", Set.of(), Set.of(), Map.of(), Set.of("minecraft:crafting_table"),
                Set.of("minecraft:stick"), true, Set.of());

        assertTrue(engine.evaluateFully(stick(), open).available());
        assertFalse(engine.evaluateFully(stick(), limited).available());
    }
}
