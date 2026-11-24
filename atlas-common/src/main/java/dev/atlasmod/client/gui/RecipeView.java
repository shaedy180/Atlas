package dev.atlasmod.client.gui;

import dev.atlasmod.api.AtlasApi;
import dev.atlasmod.client.ItemStacks;
import dev.atlasmod.core.category.RecipeCategory;
import dev.atlasmod.core.entry.IngredientKey;
import dev.atlasmod.core.recipe.RecipeNode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Draws one recipe: a 3x3 grid for crafting, a compact input row for everything
 * else, followed by an arrow, the result and the station.
 */
public final class RecipeView {

    private static final int SLOT_BG = 0x44FFFFFF;

    private RecipeView() {}

    public static boolean isGrid(RecipeNode recipe) {
        return recipe.categoryId().equals("minecraft:crafting");
    }

    /** Height the recipe needs with the given slot size. */
    public static int height(RecipeNode recipe, int slot) {
        return isGrid(recipe) ? 3 * slot : slot;
    }

    public static String categoryName(String categoryId) {
        return AtlasApi.get().categoryById(categoryId).map(RecipeCategory::name).orElse(ItemStacks.pretty(categoryId));
    }

    /**
     * Draws the recipe and returns its height.
     *
     * @param maxWidth inputs that would not fit are left out and counted in a "+n" label
     */
    public static int draw(Ui ui, GuiGraphicsExtractor gfx, Font font, RecipeNode recipe,
                           int x, int y, int slot, int maxWidth) {
        int left;
        int centerY;
        if (isGrid(recipe)) {
            int gw = recipe.gridWidth();
            int gh = recipe.gridHeight();
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    int sx = x + col * slot;
                    int sy = y + row * slot;
                    gfx.fill(sx, sy, sx + slot - 1, sy + slot - 1, SLOT_BG);
                    IngredientKey ingredient = gridIngredient(recipe.inputs(), gw, gh, row, col);
                    if (!ingredient.isEmpty()) ui.item(gfx, font, ItemStacks.of(ingredient), sx, sy, null);
                }
            }
            left = x + 3 * slot + 3;
            centerY = y + slot;
        } else {
            left = x;
            int shown = 0;
            List<IngredientKey> inputs = recipe.inputs().stream().filter(i -> !i.isEmpty()).toList();
            for (IngredientKey input : inputs) {
                if (left + slot * 3 + 14 > x + maxWidth) break;
                gfx.fill(left, y, left + slot - 1, y + slot - 1, SLOT_BG);
                ui.item(gfx, font, ItemStacks.of(input), left, y, null);
                left += slot;
                shown++;
            }
            if (shown < inputs.size()) {
                String more = "+" + (inputs.size() - shown);
                gfx.text(font, more, left + 1, y + slot / 2 - 4, Ui.MUTED);
                left += font.width(more) + 3;
            }
            left += 2;
            centerY = y;
        }

        gfx.text(font, "→", left, centerY + slot / 2 - 4, Ui.TEXT);
        left += 11;
        for (var output : recipe.outputs()) {
            ItemStack stack = ItemStacks.of(output);
            String count = recipe.outputCount() > 1 && output.equals(recipe.outputs().getFirst())
                    ? String.valueOf(recipe.outputCount()) : null;
            ui.item(gfx, font, stack, left, centerY, count);
            left += slot;
        }

        var station = recipe.station();
        if (station.isPresent()) {
            ItemStack stationStack = ItemStacks.of(station.get().id());
            if (!stationStack.isEmpty() && left + slot + 6 <= x + maxWidth) {
                gfx.text(font, "@", left + 2, centerY + slot / 2 - 4, Ui.MUTED);
                ui.item(gfx, font, stationStack, left + 10, centerY, null);
            }
        } else if (isGrid(recipe)) {
            ui.tooltip(x, y, 3 * slot, 3 * slot, List.of(Component.literal("Fits the 2x2 inventory grid")));
        }
        return height(recipe, slot);
    }

    /**
     * Returns the ingredient for a position in a 3x3 crafting grid.
     * For shaped recipes (gw x gh), the recipe is placed top-left.
     * For shapeless (gw == 0), items fill left-to-right, top-to-bottom.
     */
    public static IngredientKey gridIngredient(List<IngredientKey> inputs, int gw, int gh, int row, int col) {
        if (gw > 0 && gh > 0) {
            if (col < gw && row < gh) {
                int idx = row * gw + col;
                return idx < inputs.size() ? inputs.get(idx) : IngredientKey.EMPTY;
            }
            return IngredientKey.EMPTY;
        }
        int idx = row * 3 + col;
        return idx < inputs.size() ? inputs.get(idx) : IngredientKey.EMPTY;
    }
}
