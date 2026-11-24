package dev.atlasmod.client;

import dev.atlasmod.api.AtlasApi;
import dev.atlasmod.client.compat.McCompat;
import dev.atlasmod.core.category.RecipeCategory;
import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.entry.IngredientKey;
import dev.atlasmod.core.entry.StationKey;
import dev.atlasmod.core.recipe.RecipeNode;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Registers vanilla recipe categories and loads recipes from the server's
 * RecipeManager into the Atlas graph. Category registration happens at
 * mod init; recipe loading happens on world join when the integrated server
 * (or recipe data) is available.
 * <p>
 * Recipe types from other mods are imported as well. They land in the category
 * that claimed the type through the API, or in a category named after the type.
 */
public final class VanillaRecipeLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(VanillaRecipeLoader.class);

    private static final Set<String> GAME_RECIPE_IDS = new HashSet<>();
    private static final Set<String> AUTO_CATEGORIES = new HashSet<>();

    private VanillaRecipeLoader() {}

    // -- Category registration (called once at mod init) --------------------

    public static void registerCategories() {
        vanilla("minecraft:crafting", "Crafting", "minecraft:crafting_table", 0, "minecraft:crafting");
        vanilla("minecraft:smelting", "Smelting", "minecraft:furnace", 10, "minecraft:smelting");
        vanilla("minecraft:blasting", "Blasting", "minecraft:blast_furnace", 11, "minecraft:blasting");
        vanilla("minecraft:smoking", "Smoking", "minecraft:smoker", 12, "minecraft:smoking");
        vanilla("minecraft:stonecutting", "Stonecutting", "minecraft:stonecutter", 20, "minecraft:stonecutting");
        vanilla("minecraft:smithing", "Smithing", "minecraft:smithing_table", 30, "minecraft:smithing");
        vanilla("minecraft:brewing", "Brewing", "minecraft:brewing_stand", 40, null);
        vanilla("minecraft:campfire", "Campfire Cooking", "minecraft:campfire", 50, "minecraft:campfire_cooking");
        AtlasApi.category("atlas:mob_drops")
                .name("Mob Drops").icon("minecraft:iron_sword").order(60).register();
        AtlasApi.category("atlas:block_drops")
                .name("Block Drops").icon("minecraft:iron_pickaxe").order(61).register();
        AtlasApi.category("atlas:villager_trades")
                .name("Villager Trades").icon("minecraft:emerald").order(70).register();
        AtlasApi.category("atlas:loot_tables")
                .name("Loot Tables").icon("minecraft:chest").order(80).register();
        AtlasApi.category("atlas:worldgen")
                .name("World Generation").icon("minecraft:grass_block").order(90).register();
        AtlasApi.category("atlas:fishing")
                .name("Fishing").icon("minecraft:fishing_rod").order(100).register();
        AtlasApi.category("atlas:composting")
                .name("Composting").icon("minecraft:composter").order(110).register();
        AtlasApi.category("atlas:fuel")
                .name("Fuel").icon("minecraft:coal").order(120).register();

        LOGGER.info("[Atlas] Registered 16 vanilla categories");
    }

    private static void vanilla(String id, String name, String station, int order, String recipeType) {
        var registration = AtlasApi.category(id).name(name).icon(station).order(order).station(station);
        if (recipeType != null) registration.recipeType(recipeType);
        registration.register();
    }

    /** Ids of recipes that came from the game, without the "#n" display suffix. */
    public static Set<String> gameRecipeIds() {
        return Set.copyOf(GAME_RECIPE_IDS);
    }

    // -- Recipe loading (called on world join) ------------------------------

    /**
     * Loads all recipes from the integrated server's RecipeManager into
     * the Atlas graph. Returns the number of recipes successfully loaded.
     */
    public static int loadRecipes() {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        var graph = AtlasApi.get().recipeGraph();

        // Clear previous data so reloads are idempotent
        graph.clear();
        GAME_RECIPE_IDS.clear();

        if (server == null) {
            LOGGER.warn("[Atlas] No integrated server available, only API recipes are shown");
            AtlasApi.get().restoreRecipes();
            return 0;
        }

        int loaded = 0;
        int skipped = 0;
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            try {
                List<RecipeNode> nodes = convertRecipe(holder);
                for (RecipeNode node : nodes) {
                    graph.addNode(node);
                    loaded++;
                }
                if (nodes.isEmpty()) {
                    skipped++;
                } else {
                    GAME_RECIPE_IDS.add(holder.id().identifier().toString());
                }
            } catch (Exception e) {
                skipped++;
                LOGGER.debug("[Atlas] Skipped recipe '{}': {}", holder.id().identifier(), e.getMessage());
            }
        }

        AtlasApi.get().restoreRecipes();
        LOGGER.info("[Atlas] Loaded {} recipes ({} skipped)", loaded, skipped);
        return loaded;
    }

    // -- Conversion: RecipeHolder -> RecipeNode (via RecipeDisplay) ---------

    /**
     * Converts a single RecipeHolder into one or more RecipeNodes.
     * Uses the display() system to extract inputs and outputs portably
     * (works for vanilla and modded recipe types that implement display()).
     */
    private static List<RecipeNode> convertRecipe(RecipeHolder<?> holder) {
        Recipe<?> recipe = holder.value();
        RecipeCategory category = categoryFor(recipe);
        if (category == null) return List.of();

        List<RecipeDisplay> displays = recipe.display();
        if (displays == null || displays.isEmpty()) return List.of();

        String baseId = holder.id().identifier().toString();
        List<RecipeNode> results = new ArrayList<>();

        for (int i = 0; i < displays.size(); i++) {
            RecipeDisplay display = displays.get(i);
            String nodeId = displays.size() == 1 ? baseId : baseId + "#" + i;

            List<IngredientKey> inputs = extractInputs(display);
            List<EntryKey> outputs = new ArrayList<>();
            int outputCount = addSlotAsOutput(display.result(), outputs);
            // Skip recipes where we couldn't resolve any output
            if (outputs.isEmpty()) continue;

            RecipeNode.Builder builder = RecipeNode.builder(nodeId, category.id())
                    .inputs(inputs)
                    .outputs(outputs)
                    .outputCount(outputCount);

            StationKey station = stationFor(category, display, inputs);
            if (station != null) builder.station(station);

            // Shaped crafting carries grid dimensions
            if (display instanceof ShapedCraftingRecipeDisplay shaped) {
                builder.grid(shaped.width(), shaped.height());
            }

            // Furnace displays carry duration and experience
            if (display instanceof FurnaceRecipeDisplay furnace) {
                builder.processingTime(furnace.duration());
            }

            results.add(builder.build());
        }

        return results;
    }

    /** Category for the recipe type. Unknown mod types get a category of their own. */
    private static RecipeCategory categoryFor(Recipe<?> recipe) {
        Identifier typeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        if (typeId == null) return null;
        String type = typeId.toString();
        var api = AtlasApi.get();
        var claimed = api.categoryForRecipeType(type);
        if (claimed.isPresent()) return claimed.get();
        var existing = api.categoryById(type);
        if (existing.isPresent()) return existing.get();
        if (AUTO_CATEGORIES.add(type)) {
            AtlasApi.category(type).name(ItemStacks.pretty(type)).order(500).register();
            LOGGER.debug("[Atlas] Created category for unknown recipe type {}", type);
        }
        return api.categoryById(type).orElse(null);
    }

    /**
     * The category's main station. Crafting recipes that fit the 2x2 inventory
     * grid need no table, so they have no station.
     */
    private static StationKey stationFor(RecipeCategory category, RecipeDisplay display, List<IngredientKey> inputs) {
        if (category.stations().isEmpty()) return null;
        if (category.id().equals("minecraft:crafting")) {
            boolean fitsInventory = display instanceof ShapedCraftingRecipeDisplay shaped
                    ? shaped.width() <= 2 && shaped.height() <= 2
                    : inputs.stream().filter(i -> !i.isEmpty()).count() <= 4;
            if (fitsInventory) return null;
        }
        return new StationKey(category.stations().getFirst());
    }

    /**
     * Extract ingredient keys from a RecipeDisplay.
     * Each display type exposes ingredients differently.
     */
    private static List<IngredientKey> extractInputs(RecipeDisplay display) {
        List<IngredientKey> inputs = new ArrayList<>();

        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            // Preserve empty slots so grid positions stay correct
            for (SlotDisplay slot : shaped.ingredients()) {
                if (slot instanceof SlotDisplay.Empty) {
                    inputs.add(IngredientKey.EMPTY);
                } else {
                    addSlotAsIngredient(slot, inputs);
                }
            }
        } else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            for (SlotDisplay slot : shapeless.ingredients()) {
                addSlotAsIngredient(slot, inputs);
            }
        } else if (display instanceof FurnaceRecipeDisplay furnace) {
            addSlotAsIngredient(furnace.ingredient(), inputs);
        } else if (display instanceof StonecutterRecipeDisplay stonecutter) {
            addSlotAsIngredient(stonecutter.input(), inputs);
        } else if (display instanceof SmithingRecipeDisplay smithing) {
            addSlotAsIngredient(smithing.template(), inputs);
            addSlotAsIngredient(smithing.base(), inputs);
            addSlotAsIngredient(smithing.addition(), inputs);
        }

        return inputs;
    }

    /**
     * Converts a SlotDisplay into IngredientKey entries.
     * Handles item, itemstack, tag, composite, and remainder displays.
     * For composite (alternative) slots, picks the first resolvable child
     * as the representative ingredient.
     */
    private static void addSlotAsIngredient(SlotDisplay slot, List<IngredientKey> out) {
        if (slot instanceof SlotDisplay.ItemSlotDisplay item) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item.item().value());
            if (id != null) out.add(IngredientKey.item(id.toString()));

        } else if (slot instanceof SlotDisplay.ItemStackSlotDisplay stack) {
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.stack().item().value());
            if (id != null) out.add(IngredientKey.item(id.toString()));

        } else if (slot instanceof SlotDisplay.TagSlotDisplay tag) {
            IngredientKey key = McCompat.tagIngredient(tag);
            if (!key.isEmpty()) out.add(key);

        } else if (slot instanceof SlotDisplay.Composite composite) {
            // Composite wraps alternatives (e.g. any plank type for a crafting slot).
            List<SlotDisplay> children = composite.contents();
            if (!children.isEmpty()) {
                addSlotAsIngredient(children.getFirst(), out);
            }

        } else if (slot instanceof SlotDisplay.WithRemainder remainder) {
            // WithRemainder wraps an inner slot plus a leftover (e.g. milk bucket -> bucket).
            addSlotAsIngredient(remainder.input(), out);
        }
        // SlotDisplay.Empty, AnyFuel, and unknown types are silently skipped
    }

    /**
     * Converts a result SlotDisplay into EntryKey entries and returns the stack size.
     */
    private static int addSlotAsOutput(SlotDisplay slot, List<EntryKey> out) {
        if (slot instanceof SlotDisplay.ItemSlotDisplay item) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item.item().value());
            if (id != null) out.add(new EntryKey("item", id.toString()));

        } else if (slot instanceof SlotDisplay.ItemStackSlotDisplay stack) {
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.stack().item().value());
            if (id != null) out.add(new EntryKey("item", id.toString()));
            return Math.max(1, stack.stack().count());
        }
        // Tag and composite outputs are rare; skip for now
        return 1;
    }
}
