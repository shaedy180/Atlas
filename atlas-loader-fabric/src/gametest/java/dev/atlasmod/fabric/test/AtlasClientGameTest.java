package dev.atlasmod.fabric.test;

import dev.atlasmod.api.AtlasApi;
import dev.atlasmod.client.AtlasClient;
import dev.atlasmod.client.AtlasScreen;
import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.plan.CraftingPlan;
import dev.atlasmod.ui.DeepModeTab;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/**
 * Smoke test in a real client: recipes load, every Deep Mode tab renders, the
 * planner works on vanilla data and the plan tracker shows in the inventory.
 */
public final class AtlasClientGameTest implements FabricClientGameTest {

    private static final EntryKey PICKAXE = new EntryKey("item", "minecraft:iron_pickaxe");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(mc -> mc.level != null && AtlasClient.recipeGraph().size() > 0, 20 * 60);
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runCommand("give @a minecraft:oak_log 2");
            world.getServer().runCommand("give @a minecraft:raw_iron 1");
            world.getServer().runCommand("setblock ~2 ~ ~ minecraft:crafting_table");
            context.waitTicks(40);

            int recipes = context.computeOnClient(mc -> AtlasClient.recipeGraph().size());
            check(recipes > 1000, "expected the vanilla recipes, got " + recipes);
            int items = context.computeOnClient(mc -> AtlasClient.searchIndex().size());
            check(items > 1000, "expected the item registry, got " + items);

            // Mod API: plugin recipe survives the world load, info pages from code and resources
            check(TestAtlasPlugin.worldLoaded, "onWorldLoad was not called");
            check(context.computeOnClient(mc -> AtlasClient.recipeGraph().getNode("atlas_gametest:gravel_from_cobblestone")
                    .map(n -> n.station().isPresent() && n.outputCount() == 2).orElse(false)), "API recipe missing");
            int pages = context.computeOnClient(mc -> AtlasApi.get().infoRegistry().size());
            check(pages >= 2, "expected API and resource pack info pages, got " + pages);
            String blocked = context.computeOnClient(mc -> AtlasClient.insights().availability().evaluateFully(
                    AtlasClient.recipeGraph().getNode("atlas_gametest:gravel_from_cobblestone").orElseThrow(),
                    AtlasClient.context().snapshot()).blockers().toString());
            check(blocked.contains("Nether") && blocked.contains("Grindstone"), "expected dimension and station blockers, got " + blocked);

            CraftingPlan plan = context.computeOnClient(mc -> AtlasClient.insights().plan(PICKAXE, 1));
            check(plan.materials().stream().anyMatch(m -> m.item().id().equals("minecraft:raw_iron")),
                    "iron pickaxe should need raw iron, got " + plan.materials());
            check(!plan.done() && plan.progress() > 0, "plan should be partly covered, progress " + plan.progress());

            for (DeepModeTab tab : DeepModeTab.values()) {
                context.setScreen(() -> new AtlasScreen(PICKAXE, tab));
                context.waitTicks(25);
                context.takeScreenshot("atlas-" + tab.name().toLowerCase());
            }

            context.runOnClient(mc -> {
                AtlasClient.buildPlans().pin(PICKAXE, 1);
                AtlasClient.notes().add(PICKAXE, "Test note from the game test");
            });
            context.setScreen(() -> new AtlasScreen(PICKAXE, DeepModeTab.NOTES));
            context.waitTicks(10);
            context.takeScreenshot("atlas-notes-filled");

            context.setScreen(() -> new AtlasScreen(new EntryKey("item", "minecraft:gravel"), DeepModeTab.CRAFT));
            context.waitTicks(10);
            context.takeScreenshot("atlas-api-recipe");
            context.setScreen(() -> new AtlasScreen(new EntryKey("item", "minecraft:oak_planks"), DeepModeTab.ALTERNATIVES));
            context.waitTicks(10);
            context.takeScreenshot("atlas-planks-alternatives");

            context.setScreen(() -> null);
            context.waitTicks(5);
            var player = context.computeOnClient(mc -> mc.player);
            context.setScreen(() -> new InventoryScreen(player));
            context.waitTicks(30);
            context.takeScreenshot("inventory-quick-mode");

            context.runOnClient(mc -> {
                AtlasClient.buildPlans().unpin(PICKAXE);
                AtlasClient.notes().remove(PICKAXE, 0);
            });
            context.setScreen(() -> null);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
