package dev.atlasmod.fabric;

import dev.atlasmod.api.AtlasApi;
import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.entry.IngredientKey;
import dev.atlasmod.core.entry.StationKey;
import dev.atlasmod.core.recipe.RecipeNode;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Registers vanilla recipe categories and loads recipes from the server's
 * RecipeManager into the Atlas graph. Category registration happens at
 * mod init; recipe loading happens on world join when the integrated server
 * (or recipe data) is available.
 */
public final class VanillaRecipeLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(VanillaRecipeLoader.class);

    private VanillaRecipeLoader() {}

    // ── Category registration (called once at mod init) ──────────────────

    public static void registerCategories() {
        AtlasApi.category("minecraft:crafting")
                .name("Crafting").icon("minecraft:crafting_table").order(0).register();
        AtlasApi.category("minecraft:smelting")
                .name("Smelting").icon("minecraft:furnace").order(10).register();
        AtlasApi.category("minecraft:blasting")
                .name("Blasting").icon("minecraft:blast_furnace").order(11).register();
        AtlasApi.category("minecraft:smoking")
                .name("Smoking").icon("minecraft:smoker").order(12).register();
        AtlasApi.category("minecraft:stonecutting")
                .name("Stonecutting").icon("minecraft:stonecutter").order(20).register();
        AtlasApi.category("minecraft:smithing")
                .name("Smithing").icon("minecraft:smithing_table").order(30).register();
        AtlasApi.category("minecraft:brewing")
                .name("Brewing").icon("minecraft:brewing_stand").order(40).register();
        AtlasApi.category("minecraft:campfire")
                .name("Campfire Cooking").icon("minecraft:campfire").order(50).register();
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

    // ── Recipe loading (called on world join) ────────────────────────────

    /**
     * Loads all recipes from the integrated server's RecipeManager into
     * the Atlas graph. Returns the number of recipes successfully loaded.
     */
    public static int loadRecipes() {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            LOGGER.warn("[Atlas] No integrated server available - recipe loading skipped");
            return 0;
        }

        RecipeManager recipeManager = server.getRecipeManager();
        Collection<RecipeHolder<?>> allRecipes = recipeManager.getRecipes();

        // Clear previous data so reloads are idempotent
        AtlasApi.get().recipeGraph().clear();

        int loaded = 0;
        int skipped = 0;

        for (RecipeHolder<?> holder : allRecipes) {
            try {
                List<RecipeNode> nodes = convertRecipe(holder);
                for (RecipeNode node : nodes) {
                    AtlasApi.get().recipeGraph().addNode(node);
                    loaded++;
                }
                if (nodes.isEmpty()) skipped++;
            } catch (Exception e) {
                skipped++;
                LOGGER.debug("[Atlas] Skipped recipe '{}': {}",
                        holder.id().identifier(), e.getMessage());
            }
        }

        LOGGER.info("[Atlas] Loaded {} recipes ({} skipped)", loaded, skipped);
        return loaded;
    }

    // ── Conversion: RecipeHolder -> RecipeNode (via RecipeDisplay) ───────

    /**
     * Converts a single RecipeHolder into one or more RecipeNodes.
     * Uses the display() system to extract inputs and outputs portably
     * (works for vanilla and modded recipe types that implement display()).
     */
    private static List<RecipeNode> convertRecipe(RecipeHolder<?> holder) {
        Recipe<?> recipe = holder.value();
        String categoryId = mapRecipeType(recipe);
        if (categoryId == null) return List.of();

        List<RecipeDisplay> displays = recipe.display();
        if (displays == null || displays.isEmpty()) return List.of();

        String baseId = holder.id().identifier().toString();
        List<RecipeNode> results = new ArrayList<>();

        for (int i = 0; i < displays.size(); i++) {
            RecipeDisplay display = displays.get(i);
            String nodeId = displays.size() == 1 ? baseId : baseId + "#" + i;

            List<IngredientKey> inputs = extractInputs(display);
            List<EntryKey> outputs = extractOutputs(display);
            // Skip recipes where we couldn't resolve any output
            if (outputs.isEmpty()) continue;

            RecipeNode.Builder builder = RecipeNode.builder(nodeId, categoryId)
                    .inputs(inputs)
                    .outputs(outputs);

            StationKey station = stationForCategory(categoryId);
            if (station != null) builder.station(station);

            // Furnace displays carry duration and experience
            if (display instanceof FurnaceRecipeDisplay furnace) {
                builder.processingTime(furnace.duration());
            }

            results.add(builder.build());
        }

        return results;
    }

    /**
     * Extract ingredient keys from a RecipeDisplay.
     * Each display type exposes ingredients differently.
     */
    private static List<IngredientKey> extractInputs(RecipeDisplay display) {
        List<IngredientKey> inputs = new ArrayList<>();

        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            for (SlotDisplay slot : shaped.ingredients()) {
                addSlotAsIngredient(slot, inputs);
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
            TagKey<Item> tagKey = tag.tag();
            out.add(IngredientKey.tag(tagKey.location().toString()));

        } else if (slot instanceof SlotDisplay.Composite composite) {
            // Composite wraps alternatives (e.g. any plank type for a crafting slot).
            // Recurse into the first child that yields a concrete ingredient.
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
     * Extract output EntryKeys from a RecipeDisplay.
     * All RecipeDisplay types expose result() returning a SlotDisplay.
     */
    private static List<EntryKey> extractOutputs(RecipeDisplay display) {
        List<EntryKey> outputs = new ArrayList<>();
        SlotDisplay result = display.result();
        addSlotAsOutput(result, outputs);
        return outputs;
    }

    /**
     * Converts a result SlotDisplay into EntryKey entries.
     */
    private static void addSlotAsOutput(SlotDisplay slot, List<EntryKey> out) {
        if (slot instanceof SlotDisplay.ItemSlotDisplay item) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item.item().value());
            if (id != null) out.add(new EntryKey("item", id.toString()));

        } else if (slot instanceof SlotDisplay.ItemStackSlotDisplay stack) {
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.stack().item().value());
            if (id != null) out.add(new EntryKey("item", id.toString()));
        }
        // Tag and composite outputs are rare; skip for now
    }

    // ── Mapping helpers ──────────────────────────────────────────────────

    private static String mapRecipeType(Recipe<?> recipe) {
        RecipeType<?> type = recipe.getType();
        if (type == RecipeType.CRAFTING)        return "minecraft:crafting";
        if (type == RecipeType.SMELTING)         return "minecraft:smelting";
        if (type == RecipeType.BLASTING)         return "minecraft:blasting";
        if (type == RecipeType.SMOKING)          return "minecraft:smoking";
        if (type == RecipeType.CAMPFIRE_COOKING) return "minecraft:campfire";
        if (type == RecipeType.STONECUTTING)     return "minecraft:stonecutting";
        if (type == RecipeType.SMITHING)         return "minecraft:smithing";
        LOGGER.debug("[Atlas] Unknown recipe type: {}", type);
        return null;
    }

    private static StationKey stationForCategory(String categoryId) {
        return switch (categoryId) {
            case "minecraft:crafting"    -> new StationKey("minecraft:crafting_table");
            case "minecraft:smelting"    -> new StationKey("minecraft:furnace");
            case "minecraft:blasting"    -> new StationKey("minecraft:blast_furnace");
            case "minecraft:smoking"     -> new StationKey("minecraft:smoker");
            case "minecraft:campfire"    -> new StationKey("minecraft:campfire");
            case "minecraft:stonecutting"-> new StationKey("minecraft:stonecutter");
            case "minecraft:smithing"    -> new StationKey("minecraft:smithing_table");
            default -> null;
        };
    }
}
