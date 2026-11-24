package dev.atlasmod.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.atlasmod.api.AtlasApi;
import dev.atlasmod.client.compat.McCompat;
import dev.atlasmod.core.category.RecipeCategory;
import dev.atlasmod.core.plan.CraftingPlan;
import dev.atlasmod.core.recipe.RecipeGraph;
import dev.atlasmod.core.recipe.RecipeNode;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Client-side /atlas command tree for diagnostics and debugging.
 * All subcommands answer in the local chat, nothing is sent to the server.
 * Built with plain Brigadier so both loaders can register the same tree.
 */
public final class AtlasCommands {

    private AtlasCommands() {}

    /**
     * Builds the /atlas tree for a loader's command source type.
     *
     * @param feedback sends a chat line to the player for the given source
     */
    public static <S> LiteralArgumentBuilder<S> build(BiConsumer<S, Component> feedback) {
        return LiteralArgumentBuilder.<S>literal("atlas")
                .then(LiteralArgumentBuilder.<S>literal("open").executes(ctx -> run(ctx, feedback, AtlasCommands::open)))
                .then(LiteralArgumentBuilder.<S>literal("quick").executes(ctx -> run(ctx, feedback, AtlasCommands::quick)))
                .then(LiteralArgumentBuilder.<S>literal("debug").executes(ctx -> run(ctx, feedback, AtlasCommands::debug)))
                .then(LiteralArgumentBuilder.<S>literal("stats").executes(ctx -> run(ctx, feedback, AtlasCommands::stats)))
                .then(LiteralArgumentBuilder.<S>literal("plans").executes(ctx -> run(ctx, feedback, AtlasCommands::plans)))
                .then(LiteralArgumentBuilder.<S>literal("reload").executes(ctx -> run(ctx, feedback, AtlasCommands::reload)));
    }

    private interface Action {
        void run(List<Component> out);
    }

    private static <S> int run(CommandContext<S> ctx, BiConsumer<S, Component> feedback, Action action) {
        List<Component> out = new ArrayList<>();
        action.run(out);
        out.forEach(line -> feedback.accept(ctx.getSource(), line));
        return 1;
    }

    private static Component line(String text) {
        return Component.literal(text);
    }

    private static void open(List<Component> out) {
        // The chat screen closes after the command runs, so open on the next frame
        Minecraft.getInstance().execute(() -> McCompat.openScreen(new AtlasScreen()));
        out.add(line("§a[Atlas] Opened Deep Mode."));
    }

    private static void quick(List<Component> out) {
        QuickModeOverlay.toggle();
        out.add(line("§a[Atlas] Quick Mode " + (QuickModeOverlay.isEnabled() ? "enabled" : "disabled") + "."));
    }

    // -- /atlas debug ---------------------------------------------------------

    /**
     * Runs a full diagnostic sweep over the Atlas graph and reports issues.
     * Checks: orphaned categories, empty outputs, unknown categories, cycles.
     */
    private static void debug(List<Component> out) {
        RecipeGraph graph = AtlasApi.get().recipeGraph();
        Set<String> categoryIds = AtlasApi.get().categories().keySet();
        out.add(line("§6[Atlas Debug] Running diagnostics..."));

        List<String> issues = new ArrayList<>();
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
            if (node.inputs().isEmpty()) emptyInputs++;
            if (!categoryIds.contains(node.categoryId())) {
                missingCategory++;
                issues.add("Unknown category '" + node.categoryId() + "' in: " + node.id());
            }
        }

        // Atlas' own source categories are filled by integrations, so an empty one is normal
        for (String catId : categoryIds) {
            if (!usedCategories.contains(catId) && !catId.startsWith("atlas:")) {
                issues.add("Category without recipes: " + catId);
            }
        }

        out.add(line("§a[Atlas Debug] Total recipes: " + graph.size()));
        out.add(line("§a[Atlas Debug] Categories used: " + usedCategories.size() + " of " + categoryIds.size()));
        out.add(line("§a[Atlas Debug] Search index: " + AtlasClient.searchIndex().size() + " items"));
        out.add(line("§a[Atlas Debug] Tags: " + AtlasClient.tags().tagCount()
                + ", info pages: " + AtlasApi.get().infoRegistry().size()
                + ", plugins: " + AtlasClient.plugins().size()));
        if (emptyOutputs > 0) out.add(line("§e[Atlas Debug] Recipes with empty outputs: " + emptyOutputs));
        if (emptyInputs > 0) out.add(line("§e[Atlas Debug] Recipes with no inputs: " + emptyInputs));
        if (missingCategory > 0) out.add(line("§c[Atlas Debug] Recipes with unknown category: " + missingCategory));

        if (issues.isEmpty()) {
            out.add(line("§a[Atlas Debug] No issues found, the graph is healthy."));
        } else {
            int shown = Math.min(issues.size(), 10);
            for (int i = 0; i < shown; i++) out.add(line("§c  - " + issues.get(i)));
            if (issues.size() > shown) out.add(line("§c  ... and " + (issues.size() - shown) + " more issues"));
        }
    }

    // -- /atlas stats ---------------------------------------------------------

    private static void stats(List<Component> out) {
        RecipeGraph graph = AtlasApi.get().recipeGraph();
        Map<String, RecipeCategory> categories = AtlasApi.get().categories();
        Map<String, Integer> perCategory = new LinkedHashMap<>();
        for (RecipeNode node : graph.allNodes()) perCategory.merge(node.categoryId(), 1, Integer::sum);

        out.add(line("§6[Atlas Stats]"));
        out.add(line("  Recipes: " + graph.size()));
        out.add(line("  Search index: " + AtlasClient.searchIndex().size() + " items"));
        out.add(line("  Saved items: " + AtlasClient.savedItems().plans().size()
                + ", build plans: " + AtlasClient.buildPlans().plans().size()
                + ", notes: " + AtlasClient.notes().size()));
        out.add(line("  Quick Mode: " + (QuickModeOverlay.isEnabled() ? "ON" : "OFF")));
        if (!perCategory.isEmpty()) {
            out.add(line("  §7Per category:"));
            perCategory.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(8)
                    .forEach(e -> {
                        var cat = categories.get(e.getKey());
                        out.add(line("    " + (cat != null ? cat.name() : e.getKey()) + ": " + e.getValue()));
                    });
        }
    }

    // -- /atlas plans ---------------------------------------------------------

    private static void plans(List<Component> out) {
        var plans = AtlasClient.buildPlans().plans();
        if (plans.isEmpty()) {
            out.add(line("§7[Atlas] No build plans. Pin one from the Tree tab in Deep Mode."));
            return;
        }
        out.add(line("§6[Atlas Plans]"));
        for (var pinned : plans) {
            CraftingPlan plan = AtlasClient.insights().plan(pinned.target(), pinned.count());
            String name = ItemStacks.name(pinned.target().id());
            if (plan.done()) {
                out.add(line("  §a" + pinned.count() + "x " + name + ": ready"));
                continue;
            }
            out.add(line("  §e" + pinned.count() + "x " + name + ": " + Math.round(plan.progress() * 100) + "%"));
            plan.missingMaterials().stream().limit(5).forEach(m ->
                    out.add(line("    §7need " + m.missing() + "x " + ItemStacks.name(m.item().id()))));
        }
    }

    // -- /atlas reload --------------------------------------------------------

    private static void reload(List<Component> out) {
        try {
            int count = AtlasClient.reload();
            out.add(line("§a[Atlas] Reloaded " + count + " recipes, "
                    + AtlasClient.searchIndex().size() + " items indexed."));
        } catch (Exception e) {
            out.add(line("§c[Atlas] Reload failed: " + e.getMessage()));
        }
    }
}
