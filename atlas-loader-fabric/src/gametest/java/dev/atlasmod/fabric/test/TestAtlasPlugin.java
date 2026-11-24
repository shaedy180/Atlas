package dev.atlasmod.fabric.test;

import dev.atlasmod.api.AtlasApi;
import dev.atlasmod.api.plugin.AtlasPlugin;
import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.entry.IngredientKey;
import dev.atlasmod.core.recipe.AcquisitionSource;
import dev.atlasmod.core.unlock.UnlockCondition;

/** Uses the public API the way an integrating mod would. */
public final class TestAtlasPlugin implements AtlasPlugin {

    static boolean worldLoaded;

    @Override
    public void onAtlasReady() {
        AtlasApi.category("atlas_gametest:crusher")
                .name("Crusher")
                .icon("minecraft:grindstone")
                .station("minecraft:grindstone")
                .register();

        AtlasApi.recipes("atlas_gametest:crusher")
                .id("atlas_gametest:gravel_from_cobblestone")
                .display(IngredientKey.item("minecraft:cobblestone"))
                .output(new EntryKey("item", "minecraft:gravel"), 2)
                .time(100)
                .require(new UnlockCondition.RequiresDimension("minecraft:the_nether"))
                .register();

        AtlasApi.info("atlas_gametest:gravel")
                .title("Crushing")
                .line("The test crusher turns cobblestone into gravel.")
                .forItems("minecraft:gravel")
                .source("Atlas game test")
                .register();

        AtlasApi.addSource("minecraft:flint",
                new AcquisitionSource(AcquisitionSource.SourceType.BLOCK_DROP, "Gravel", "10% chance when broken"));
    }

    @Override
    public void onWorldLoad() {
        worldLoaded = true;
    }
}
