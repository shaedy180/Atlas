package dev.atlasmod.core.availability;

import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.recipe.RecipeNode;
import dev.atlasmod.core.unlock.UnlockCondition;
import dev.atlasmod.core.visibility.VisibilityPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvailabilityEngineTest {

    @Test
    void marksProgressionAsUnknownWhenNotSynchronized() {
        RecipeNode recipe = RecipeNode.builder("mymod:test_recipe", "mymod", "mymod:machines")
                .outputs(List.of(new EntryKey("item", "mymod:result")))
                .unlockCondition(new UnlockCondition.RequiresAdvancement("mymod:unlock"))
                .visibilityPolicy(VisibilityPolicy.VISIBLE)
                .build();

        ContextSnapshot context = new ContextSnapshot("minecraft:overworld", "minecraft:plains", Set.of(), Set.of(), Set.of(), false);
        AvailabilityEngine.AvailabilityResult result = new AvailabilityEngine().evaluate(recipe, context);

        assertTrue(result.unknown());
        assertEquals(VisibilityPolicy.TEASER, result.policy());
    }
}
