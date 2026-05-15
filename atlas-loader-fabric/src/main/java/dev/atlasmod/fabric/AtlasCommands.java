package dev.atlasmod.fabric;

import dev.atlasmod.core.recipe.RecipeGraph;
import dev.atlasmod.core.recipe.RecipeNode;
import dev.atlasmod.core.registry.AtlasRegistrySnapshot;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client-side /atlas command tree for diagnostics and debugging.
 */
public final class AtlasCommands {

    private AtlasCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    ClientCommands.literal("atlas")
                            .then(ClientCommands.literal("open").executes(ctx -> runOpen(ctx.getSource())))
                            .then(ClientCommands.literal("quick").executes(ctx -> runQuickToggle(ctx.getSource())))
                            .then(ClientCommands.literal("debug").executes(ctx -> runDebug(ctx.getSource())))
                            .then(ClientCommands.literal("stats").executes(ctx -> runStats(ctx.getSource())))
                            .then(ClientCommands.literal("reload").executes(ctx -> runReload(ctx.getSource())))
            );
        });
    }

    private static int runOpen(FabricClientCommandSource source) {
        Minecraft client = source.getClient();
        client.setScreen(new AtlasScreen());
        source.sendFeedback(Component.literal("Atlas: opened Deep Mode."));
        return 1;
    }

    private static int runQuickToggle(FabricClientCommandSource source) {
        QuickModeOverlay.toggle();
        source.sendFeedback(Component.literal("Atlas Quick Mode " + (QuickModeOverlay.isEnabled() ? "enabled" : "disabled") + "."));
        return 1;
    }

    private static int runDebug(FabricClientCommandSource source) {
        RecipeGraph graph = AtlasFabricClient.recipeGraph();
        AtlasRegistrySnapshot snapshot = AtlasRuntimeController.clientSnapshot();

        List<String> issues = new ArrayList<>();
        Set<String> categoryIds = new HashSet<>(snapshot.categoriesById().keySet());
        Set<String> usedCategories = new HashSet<>();

        int emptyOutputs = 0;
        int emptyInputs = 0;
        int missingCategory = 0;

        for (RecipeNode node : graph.allNodes()) {
            usedCategories.add(node.categoryId());
            if (node.outputs().isEmpty()) {
                emptyOutputs++;
                issues.add("Empty outputs: " + node.id());
            }
            if (node.inputs().isEmpty()) {
                emptyInputs++;
            }
            if (!categoryIds.isEmpty() && !categoryIds.contains(node.categoryId())) {
                missingCategory++;
                issues.add("Unknown category '" + node.categoryId() + "' in: " + node.id());
            }
        }

        for (String categoryId : categoryIds) {
            if (!usedCategories.contains(categoryId)) {
                issues.add("Orphaned category: " + categoryId);
            }
        }

        int cyclesFound = 0;
        Set<String> checkedOutputs = new HashSet<>();
        for (RecipeNode node : graph.allNodes()) {
            for (var output : node.outputs()) {
                if (checkedOutputs.add(output.id()) && graph.hasCycle(output)) {
                    cyclesFound++;
                    if (cyclesFound <= 5) {
                        issues.add("Cyclic recipe chain from: " + output.id());
                    }
                }
            }
        }

        source.sendFeedback(Component.literal("Atlas Debug"));
        source.sendFeedback(Component.literal("  Recipes: " + graph.size()));
        source.sendFeedback(Component.literal("  Categories: " + snapshot.categories().size()));
        source.sendFeedback(Component.literal("  Search index: " + AtlasFabricClient.searchIndex().size() + " items"));
        source.sendFeedback(Component.literal("  Owners: " + snapshot.ownerModIds().size()));

        if (emptyOutputs > 0) {
            source.sendFeedback(Component.literal("  Recipes with empty outputs: " + emptyOutputs));
        }
        if (emptyInputs > 0) {
            source.sendFeedback(Component.literal("  Recipes with no inputs: " + emptyInputs));
        }
        if (missingCategory > 0) {
            source.sendFeedback(Component.literal("  Recipes with unknown category: " + missingCategory));
        }
        if (cyclesFound > 0) {
            source.sendFeedback(Component.literal("  Cyclic recipe chains: " + cyclesFound));
        }

        if (issues.isEmpty()) {
            source.sendFeedback(Component.literal("  No issues found."));
        } else {
            for (int i = 0; i < Math.min(issues.size(), 10); i++) {
                source.sendFeedback(Component.literal("  - " + issues.get(i)));
            }
            if (issues.size() > 10) {
                source.sendFeedback(Component.literal("  ... and " + (issues.size() - 10) + " more"));
            }
        }

        return 1;
    }

    private static int runStats(FabricClientCommandSource source) {
        RecipeGraph graph = AtlasFabricClient.recipeGraph();
        AtlasRegistrySnapshot snapshot = AtlasRuntimeController.clientSnapshot();

        Map<String, Integer> perCategory = new LinkedHashMap<>();
        for (RecipeNode node : graph.allNodes()) {
            perCategory.merge(node.categoryId(), 1, Integer::sum);
        }

        source.sendFeedback(Component.literal("Atlas Stats"));
        source.sendFeedback(Component.literal("  Recipes: " + graph.size()));
        source.sendFeedback(Component.literal("  Categories: " + snapshot.categories().size()));
        source.sendFeedback(Component.literal("  Search index: " + AtlasFabricClient.searchIndex().size() + " items"));
        source.sendFeedback(Component.literal("  Owners: " + snapshot.ownerModIds().size()));
        source.sendFeedback(Component.literal("  Quick Mode: " + (QuickModeOverlay.isEnabled() ? "ON" : "OFF")));

        perCategory.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(8)
                .forEach(entry -> source.sendFeedback(Component.literal("  " + entry.getKey() + ": " + entry.getValue())));

        return 1;
    }

    private static int runReload(FabricClientCommandSource source) {
        try {
            AtlasRuntimeController.requestReloadFromClient();
            source.sendFeedback(Component.literal("Atlas runtime rebuild requested."));
        } catch (Exception e) {
            source.sendFeedback(Component.literal("Atlas reload failed: " + e.getMessage()));
        }
        return 1;
    }
}
