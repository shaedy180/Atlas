package dev.atlasmod.fabric;

import dev.atlasmod.api.AtlasRegistrationContext;
import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.entry.IngredientKey;
import dev.atlasmod.core.entry.StationKey;
import dev.atlasmod.core.recipe.RecipeNode;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
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
import java.util.Collection;
import java.util.List;

/**
 * Registers vanilla Atlas categories and converts server recipes into RecipeNodes.
 */
public final class VanillaRecipeLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(VanillaRecipeLoader.class);

    private VanillaRecipeLoader() {
    }

    public static void registerCategories(AtlasRegistrationContext context) {
        context.categories().add("minecraft:crafting")
                .name("Crafting").icon("minecraft:crafting_table").order(0).register();
        context.categories().add("minecraft:smelting")
                .name("Smelting").icon("minecraft:furnace").order(10).register();
        context.categories().add("minecraft:blasting")
                .name("Blasting").icon("minecraft:blast_furnace").order(11).register();
        context.categories().add("minecraft:smoking")
                .name("Smoking").icon("minecraft:smoker").order(12).register();
        context.categories().add("minecraft:stonecutting")
                .name("Stonecutting").icon("minecraft:stonecutter").order(20).register();
        context.categories().add("minecraft:smithing")
                .name("Smithing").icon("minecraft:smithing_table").order(30).register();
        context.categories().add("minecraft:brewing")
                .name("Brewing").icon("minecraft:brewing_stand").order(40).register();
        context.categories().add("minecraft:campfire")
                .name("Campfire Cooking").icon("minecraft:campfire").order(50).register();
        context.categories().add("atlas:mob_drops")
                .name("Mob Drops").icon("minecraft:iron_sword").order(60).register();
        context.categories().add("atlas:block_drops")
                .name("Block Drops").icon("minecraft:iron_pickaxe").order(61).register();
        context.categories().add("atlas:villager_trades")
                .name("Villager Trades").icon("minecraft:emerald").order(70).register();
        context.categories().add("atlas:loot_tables")
                .name("Loot Tables").icon("minecraft:chest").order(80).register();
        context.categories().add("atlas:worldgen")
                .name("World Generation").icon("minecraft:grass_block").order(90).register();
        context.categories().add("atlas:fishing")
                .name("Fishing").icon("minecraft:fishing_rod").order(100).register();
        context.categories().add("atlas:composting")
                .name("Composting").icon("minecraft:composter").order(110).register();
        context.categories().add("atlas:fuel")
                .name("Fuel").icon("minecraft:coal").order(120).register();
    }

    public static List<RecipeNode> loadRecipes(MinecraftServer server) {
        RecipeManager recipeManager = server.getRecipeManager();
        Collection<RecipeHolder<?>> allRecipes = recipeManager.getRecipes();

        List<RecipeNode> result = new ArrayList<>();
        int skipped = 0;

        for (RecipeHolder<?> holder : allRecipes) {
            try {
                List<RecipeNode> nodes = convertRecipe(holder);
                result.addAll(nodes);
                if (nodes.isEmpty()) {
                    skipped++;
                }
            } catch (Exception e) {
                skipped++;
                LOGGER.debug("[Atlas] Skipped recipe '{}': {}", holder.id().identifier(), e.getMessage());
            }
        }

        LOGGER.info("[Atlas] Converted {} recipes ({} skipped)", result.size(), skipped);
        return result;
    }

    private static List<RecipeNode> convertRecipe(RecipeHolder<?> holder) {
        Recipe<?> recipe = holder.value();
        String categoryId = mapRecipeType(recipe);
        if (categoryId == null) {
            return List.of();
        }

        List<RecipeDisplay> displays = recipe.display();
        if (displays == null || displays.isEmpty()) {
            return List.of();
        }

        String ownerModId = holder.id().identifier().getNamespace();
        String baseId = holder.id().identifier().toString();
        List<RecipeNode> results = new ArrayList<>();

        for (int i = 0; i < displays.size(); i++) {
            RecipeDisplay display = displays.get(i);
            String nodeId = displays.size() == 1 ? baseId : baseId + "#" + i;

            List<IngredientKey> inputs = extractInputs(display);
            List<EntryKey> outputs = extractOutputs(display);
            if (outputs.isEmpty()) {
                continue;
            }

            RecipeNode.Builder builder = RecipeNode.builder(nodeId, ownerModId, categoryId)
                    .inputs(inputs)
                    .outputs(outputs)
                    .searchAliases(List.of(baseId.replace(':', ' '), holder.id().identifier().getPath().replace('_', ' ')));

            StationKey station = stationForCategory(categoryId);
            if (station != null) {
                builder.station(station);
            }

            if (display instanceof ShapedCraftingRecipeDisplay shaped) {
                builder.grid(shaped.width(), shaped.height());
            }

            if (display instanceof FurnaceRecipeDisplay furnace) {
                builder.processingTime(furnace.duration());
            }

            results.add(builder.build());
        }

        return results;
    }

    private static List<IngredientKey> extractInputs(RecipeDisplay display) {
        List<IngredientKey> inputs = new ArrayList<>();

        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
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

    private static void addSlotAsIngredient(SlotDisplay slot, List<IngredientKey> out) {
        if (slot instanceof SlotDisplay.ItemSlotDisplay item) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item.item().value());
            if (id != null) {
                out.add(IngredientKey.item(id.toString()));
            }
        } else if (slot instanceof SlotDisplay.ItemStackSlotDisplay stack) {
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.stack().item().value());
            if (id != null) {
                out.add(IngredientKey.item(id.toString()));
            }
        } else if (slot instanceof SlotDisplay.TagSlotDisplay tag) {
            TagKey<Item> tagKey = tag.tag();
            out.add(IngredientKey.tag(tagKey.location().toString()));
        } else if (slot instanceof SlotDisplay.Composite composite) {
            List<SlotDisplay> children = composite.contents();
            if (!children.isEmpty()) {
                addSlotAsIngredient(children.getFirst(), out);
            }
        } else if (slot instanceof SlotDisplay.WithRemainder remainder) {
            addSlotAsIngredient(remainder.input(), out);
        }
    }

    private static List<EntryKey> extractOutputs(RecipeDisplay display) {
        List<EntryKey> outputs = new ArrayList<>();
        addSlotAsOutput(display.result(), outputs);
        return outputs;
    }

    private static void addSlotAsOutput(SlotDisplay slot, List<EntryKey> out) {
        if (slot instanceof SlotDisplay.ItemSlotDisplay item) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item.item().value());
            if (id != null) {
                out.add(new EntryKey("item", id.toString()));
            }
        } else if (slot instanceof SlotDisplay.ItemStackSlotDisplay stack) {
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.stack().item().value());
            if (id != null) {
                out.add(new EntryKey("item", id.toString()));
            }
        }
    }

    private static String mapRecipeType(Recipe<?> recipe) {
        RecipeType<?> type = recipe.getType();
        if (type == RecipeType.CRAFTING) return "minecraft:crafting";
        if (type == RecipeType.SMELTING) return "minecraft:smelting";
        if (type == RecipeType.BLASTING) return "minecraft:blasting";
        if (type == RecipeType.SMOKING) return "minecraft:smoking";
        if (type == RecipeType.CAMPFIRE_COOKING) return "minecraft:campfire";
        if (type == RecipeType.STONECUTTING) return "minecraft:stonecutting";
        if (type == RecipeType.SMITHING) return "minecraft:smithing";
        LOGGER.debug("[Atlas] Unknown recipe type: {}", type);
        return null;
    }

    private static StationKey stationForCategory(String categoryId) {
        return switch (categoryId) {
            case "minecraft:crafting" -> new StationKey("minecraft:crafting_table");
            case "minecraft:smelting" -> new StationKey("minecraft:furnace");
            case "minecraft:blasting" -> new StationKey("minecraft:blast_furnace");
            case "minecraft:smoking" -> new StationKey("minecraft:smoker");
            case "minecraft:campfire" -> new StationKey("minecraft:campfire");
            case "minecraft:stonecutting" -> new StationKey("minecraft:stonecutter");
            case "minecraft:smithing" -> new StationKey("minecraft:smithing_table");
            default -> null;
        };
    }
}
