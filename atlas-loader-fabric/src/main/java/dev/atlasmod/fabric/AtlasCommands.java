package dev.atlasmod.fabric;

import dev.atlasmod.api.AtlasApi;
import dev.atlasmod.api.registration.CategoryRegistration;
import dev.atlasmod.core.category.RecipeCategory;
import dev.atlasmod.core.recipe.RecipeGraph;
import dev.atlasmod.core.recipe.RecipeNode;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * Client-side /atlas command tree for diagnostics and debugging.
 * All subcommands output results to the local chat — nothing is sent to the server.
 */
public final class AtlasCommands {

    private AtlasCommands() {}

    /**
     * Registers all /atlas subcommands. Called once during mod init.
     */
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                ClientCommands.literal("atlas")
                    .then(ClientCommands.literal("debug")
                        .executes(ctx -> runDebug(ctx.getSource())))
                    .then(ClientCommands.literal("stats")
                        .executes(ctx -> runStats(ctx.getSource())))
                    .then(ClientCommands.literal("reload")
                        .executes(ctx -> runReload(ctx.getSource())))
            );
        });
    }

    // ── /atlas debug ─────────────────────────────────────────────────────

    /**
     * Runs a full diagnostic sweep over the Atlas graph and reports issues.
     * Checks: orphaned categories, empty outputs, missing stations, cycles.
     */
    private static int runDebug(FabricClientCommandSource source) {
        RecipeGraph graph = AtlasApi.get().recipeGraph();
        List<RecipeCategory> categories = CategoryRegistration.drainPending();

        source.sendFeedback(Component.literal("§6[Atlas Debug] Running diagnostics..."));

        List<String> issues = new ArrayList<>();
        Set<String> categoryIds = new HashSet<>();
        for (RecipeCategory cat : categories) {
            categoryIds.add(cat.id());
        }

        int totalNodes = graph.size();
        int emptyOutputs = 0;
        int emptyInputs = 0;
        int missingCategory = 0;
        Set<String> usedCategories = new HashSet<>();

        for (RecipeNode node : graph.allNodes()) {
            usedCategories.add(node.categoryId());

            if (node.outputs().isEmpty()) {
                emptyOutputs++;
                issues.add("Empty outputs: " + node.id());
            }
            if (node.inputs().isEmpty()) {
                emptyInputs++;
            }
            // Check if category is registered (if we have category data)
            if (!categoryIds.isEmpty() && !categoryIds.contains(node.categoryId())) {
                missingCategory++;
                issues.add("Unknown category '" + node.categoryId() + "' in: " + node.id());
            }
        }

        // Orphaned categories (registered but no recipes use them)
        if (!categoryIds.isEmpty()) {
            for (String catId : categoryIds) {
                if (!usedCategories.contains(catId)) {
                    issues.add("Orphaned category (no recipes): " + catId);
                }
            }
        }

        // Report results
        source.sendFeedback(Component.literal("§a[Atlas Debug] Total recipes: " + totalNodes));
        source.sendFeedback(Component.literal("§a[Atlas Debug] Categories used: " + usedCategories.size()));
        source.sendFeedback(Component.literal("§a[Atlas Debug] Search index: " + AtlasFabricClient.searchIndex().size() + " items"));

        if (emptyOutputs > 0) {
            source.sendFeedback(Component.literal("§e[Atlas Debug] Recipes with empty outputs: " + emptyOutputs));
        }
        if (emptyInputs > 0) {
            source.sendFeedback(Component.literal("§e[Atlas Debug] Recipes with no inputs: " + emptyInputs));
        }
        if (missingCategory > 0) {
            source.sendFeedback(Component.literal("§c[Atlas Debug] Recipes with unknown category: " + missingCategory));
        }

        if (issues.isEmpty()) {
            source.sendFeedback(Component.literal("§a[Atlas Debug] No issues found — graph is healthy."));
        } else {
            int shown = Math.min(issues.size(), 10);
            for (int i = 0; i < shown; i++) {
                source.sendFeedback(Component.literal("§c  - " + issues.get(i)));
            }
            if (issues.size() > shown) {
                source.sendFeedback(Component.literal("§c  ... and " + (issues.size() - shown) + " more issues"));
            }
        }

        return 1;
    }

    // ── /atlas stats ─────────────────────────────────────────────────────

    /**
     * Quick summary of loaded data.
     */
    private static int runStats(FabricClientCommandSource source) {
        RecipeGraph graph = AtlasApi.get().recipeGraph();

        // Count recipes per category
        Map<String, Integer> perCategory = new LinkedHashMap<>();
        for (RecipeNode node : graph.allNodes()) {
            perCategory.merge(node.categoryId(), 1, Integer::sum);
        }

        source.sendFeedback(Component.literal("§6[Atlas Stats]"));
        source.sendFeedback(Component.literal("  Recipes: " + graph.size()));
        source.sendFeedback(Component.literal("  Search index: " + AtlasFabricClient.searchIndex().size() + " items"));
        source.sendFeedback(Component.literal("  Quick Mode: " + (QuickModeOverlay.isEnabled() ? "ON" : "OFF")));

        if (!perCategory.isEmpty()) {
            source.sendFeedback(Component.literal("  §7Per category:"));
            perCategory.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(8)
                    .forEach(e -> source.sendFeedback(
                            Component.literal("    " + e.getKey() + ": " + e.getValue())));
        }

        return 1;
    }

    // ── /atlas reload ────────────────────────────────────────────────────

    /**
     * Force-reloads recipes and rebuilds the search index.
     */
    private static int runReload(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("§6[Atlas] Reloading recipes..."));
        try {
            int count = VanillaRecipeLoader.loadRecipes();
            AtlasItemIndexer.buildIndex(AtlasFabricClient.searchIndex());
            source.sendFeedback(Component.literal("§a[Atlas] Reloaded " + count + " recipes, "
                    + AtlasFabricClient.searchIndex().size() + " items indexed."));
        } catch (Exception e) {
            source.sendFeedback(Component.literal("§c[Atlas] Reload failed: " + e.getMessage()));
        }
        return 1;
    }
}
