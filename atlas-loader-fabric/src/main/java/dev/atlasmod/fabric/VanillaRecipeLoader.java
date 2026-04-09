package dev.atlasmod.fabric;

import dev.atlasmod.api.AtlasApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers vanilla Minecraft recipe categories with Atlas.
 */
public final class VanillaRecipeLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(VanillaRecipeLoader.class);

    private VanillaRecipeLoader() {}

    public static void registerCategories() {
        AtlasApi.category("minecraft:crafting")
                .name("Crafting")
                .icon("minecraft:crafting_table")
                .order(0)
                .register();

        AtlasApi.category("minecraft:smelting")
                .name("Smelting")
                .icon("minecraft:furnace")
                .order(10)
                .register();

        AtlasApi.category("minecraft:blasting")
                .name("Blasting")
                .icon("minecraft:blast_furnace")
                .order(11)
                .register();

        AtlasApi.category("minecraft:smoking")
                .name("Smoking")
                .icon("minecraft:smoker")
                .order(12)
                .register();

        AtlasApi.category("minecraft:stonecutting")
                .name("Stonecutting")
                .icon("minecraft:stonecutter")
                .order(20)
                .register();

        AtlasApi.category("minecraft:smithing")
                .name("Smithing")
                .icon("minecraft:smithing_table")
                .order(30)
                .register();

        AtlasApi.category("minecraft:brewing")
                .name("Brewing")
                .icon("minecraft:brewing_stand")
                .order(40)
                .register();

        AtlasApi.category("minecraft:campfire")
                .name("Campfire Cooking")
                .icon("minecraft:campfire")
                .order(50)
                .register();

        AtlasApi.category("atlas:mob_drops")
                .name("Mob Drops")
                .icon("minecraft:iron_sword")
                .order(60)
                .register();

        AtlasApi.category("atlas:block_drops")
                .name("Block Drops")
                .icon("minecraft:iron_pickaxe")
                .order(61)
                .register();

        AtlasApi.category("atlas:villager_trades")
                .name("Villager Trades")
                .icon("minecraft:emerald")
                .order(70)
                .register();

        AtlasApi.category("atlas:loot_tables")
                .name("Loot Tables")
                .icon("minecraft:chest")
                .order(80)
                .register();

        AtlasApi.category("atlas:worldgen")
                .name("World Generation")
                .icon("minecraft:grass_block")
                .order(90)
                .register();

        AtlasApi.category("atlas:fishing")
                .name("Fishing")
                .icon("minecraft:fishing_rod")
                .order(100)
                .register();

        AtlasApi.category("atlas:composting")
                .name("Composting")
                .icon("minecraft:composter")
                .order(110)
                .register();

        AtlasApi.category("atlas:fuel")
                .name("Fuel")
                .icon("minecraft:coal")
                .order(120)
                .register();

        LOGGER.info("[Atlas] Registered {} vanilla categories", 15);
    }
}
