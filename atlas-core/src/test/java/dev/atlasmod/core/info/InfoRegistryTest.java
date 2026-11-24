package dev.atlasmod.core.info;

import dev.atlasmod.core.TestRecipes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.atlasmod.core.TestRecipes.item;
import static org.junit.jupiter.api.Assertions.assertEquals;

class InfoRegistryTest {

    @Test
    void matchesItemsAndTagsAndReloadsBySource() {
        InfoRegistry registry = new InfoRegistry();
        registry.register(new InfoPage("pack:wood", "Wood", List.of("Plant trees."), List.of("#minecraft:planks"), "resourcepack"));
        registry.register(new InfoPage("mod:iron", "Iron", List.of("Rare here."), List.of("minecraft:iron_ingot"), "api"));

        assertEquals(1, registry.pagesFor(item("birch_planks"), TestRecipes.TAGS).size());
        assertEquals(1, registry.pagesFor(item("iron_ingot"), TestRecipes.TAGS).size());
        assertEquals(0, registry.pagesFor(item("stick"), TestRecipes.TAGS).size());

        registry.clearSource("resourcepack");
        assertEquals(1, registry.size());
    }
}
