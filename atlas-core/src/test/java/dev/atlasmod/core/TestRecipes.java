package dev.atlasmod.core;

import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.entry.IngredientKey;
import dev.atlasmod.core.entry.StationKey;
import dev.atlasmod.core.entry.TagResolver;
import dev.atlasmod.core.recipe.RecipeGraph;
import dev.atlasmod.core.recipe.RecipeNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A tiny slice of vanilla recipes for tests. */
public final class TestRecipes {

    public static final TagResolver TAGS = new TagResolver() {
        final Map<String, List<String>> tags = Map.of(
                "minecraft:planks", List.of("minecraft:oak_planks", "minecraft:birch_planks"),
                "minecraft:logs", List.of("minecraft:oak_log", "minecraft:birch_log"));

        @Override public List<String> members(String tagId) {
            return tags.getOrDefault(tagId, List.of());
        }

        @Override public Set<String> tagsOf(String itemId) {
            var result = new java.util.HashSet<String>();
            tags.forEach((tag, members) -> { if (members.contains(itemId)) result.add(tag); });
            return result;
        }
    };

    private TestRecipes() {}

    public static EntryKey item(String path) {
        return new EntryKey("item", "minecraft:" + path);
    }

    public static IngredientKey in(String path) {
        return IngredientKey.item("minecraft:" + path);
    }

    public static RecipeNode recipe(String id, String output, int count, IngredientKey... inputs) {
        return RecipeNode.builder("minecraft:" + id, "minecraft:crafting")
                .inputs(List.of(inputs))
                .outputs(List.of(item(output)))
                .outputCount(count)
                .station(new StationKey("minecraft:crafting_table"))
                .build();
    }

    public static IngredientKey[] times(IngredientKey key, int n) {
        List<IngredientKey> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(key);
        return list.toArray(IngredientKey[]::new);
    }

    /** Planks, sticks, wooden and iron pickaxes plus the iron and diamond block loops. */
    public static RecipeGraph graph() {
        RecipeGraph g = new RecipeGraph();
        g.addNode(recipe("oak_planks", "oak_planks", 4, in("oak_log")));
        g.addNode(recipe("birch_planks", "birch_planks", 4, in("birch_log")));
        g.addNode(recipe("stick", "stick", 4, IngredientKey.tag("minecraft:planks"), IngredientKey.tag("minecraft:planks")));
        g.addNode(recipe("wooden_pickaxe", "wooden_pickaxe", 1,
                IngredientKey.tag("minecraft:planks"), IngredientKey.tag("minecraft:planks"),
                IngredientKey.tag("minecraft:planks"), in("stick"), in("stick")));
        g.addNode(recipe("iron_pickaxe", "iron_pickaxe", 1,
                in("iron_ingot"), in("iron_ingot"), in("iron_ingot"), in("stick"), in("stick")));
        g.addNode(recipe("iron_block", "iron_block", 1, times(in("iron_ingot"), 9)));
        g.addNode(recipe("iron_ingot_from_block", "iron_ingot", 9, in("iron_block")));
        g.addNode(recipe("iron_ingot_from_nuggets", "iron_ingot", 1, times(in("iron_nugget"), 9)));
        g.addNode(recipe("iron_nugget", "iron_nugget", 9, in("iron_ingot")));
        g.addNode(RecipeNode.builder("minecraft:iron_ingot_from_smelting", "minecraft:smelting")
                .inputs(List.of(in("raw_iron"))).outputs(List.of(item("iron_ingot")))
                .station(new StationKey("minecraft:furnace")).build());
        g.addNode(RecipeNode.builder("minecraft:iron_ingot_from_ore", "minecraft:smelting")
                .inputs(List.of(in("iron_ore"))).outputs(List.of(item("iron_ingot")))
                .station(new StationKey("minecraft:furnace")).build());
        g.addNode(recipe("diamond_block", "diamond_block", 1, times(in("diamond"), 9)));
        g.addNode(recipe("diamond", "diamond", 9, in("diamond_block")));
        return g;
    }
}
