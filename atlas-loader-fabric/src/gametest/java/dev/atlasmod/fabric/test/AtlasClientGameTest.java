package dev.atlasmod.fabric.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.atlasmod.api.AtlasApi;
import dev.atlasmod.client.AtlasClient;
import dev.atlasmod.client.AtlasScreen;
import dev.atlasmod.client.QuickModeOverlay;
import dev.atlasmod.client.compat.McCompat;
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

            // Real mouse clicks on the hand drawn tab buttons
            context.setScreen(() -> new AtlasScreen(PICKAXE, DeepModeTab.CRAFT));
            context.waitTicks(5);
            clickTab(context, DeepModeTab.TREE);
            check(context.computeOnClient(mc -> ((AtlasScreen) McCompat.currentScreen()).activeTab()) == DeepModeTab.TREE,
                    "clicking the Tree tab did not switch tabs");
            clickTab(context, DeepModeTab.SOURCES);
            check(context.computeOnClient(mc -> ((AtlasScreen) McCompat.currentScreen()).activeTab()) == DeepModeTab.SOURCES,
                    "clicking the Sources tab did not switch tabs");

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

            // A real click into the Quick Mode search field (default panel width 130)
            double[] search = context.computeOnClient(mc -> {
                double scale = mc.getWindow().getGuiScale();
                return new double[]{(mc.getWindow().getGuiScaledWidth() - 69) * scale, 28 * scale};
            });
            context.getInput().setCursorPos(search[0], search[1]);
            context.waitTick();
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(3);
            check(context.computeOnClient(mc -> QuickModeOverlay.isTyping()), "clicking the Quick Mode search did nothing");
            context.getInput().typeChars("stick");
            context.waitTicks(3);
            context.takeScreenshot("inventory-quick-search");

            context.runOnClient(mc -> {
                AtlasClient.buildPlans().unpin(PICKAXE);
                AtlasClient.notes().remove(PICKAXE, 0);
            });
            context.setScreen(() -> null);
        }
    }

    /** Clicks a Deep Mode tab, using the same layout as AtlasScreen. */
    private static void clickTab(ClientGameTestContext context, DeepModeTab target) {
        double[] pos = context.computeOnClient(mc -> {
            var font = mc.font;
            int width = mc.getWindow().getGuiScaledWidth();
            int startX = width * 45 / 100 + 6;
            int right = width - 6;
            int x = startX;
            int y = 32;
            for (DeepModeTab tab : DeepModeTab.values()) {
                int w = font.width(tab.label()) + 8;
                if (x + w > right) {
                    x = startX;
                    y += 14;
                }
                if (tab == target) {
                    double scale = mc.getWindow().getGuiScale();
                    return new double[]{(x + w / 2.0) * scale, (y + 6) * scale};
                }
                x += w + 2;
            }
            throw new IllegalStateException();
        });
        context.getInput().setCursorPos(pos[0], pos[1]);
        context.waitTick();
        // The real left button code: 0 with GLFW, 1 with SDL (26.3)
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(3);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
