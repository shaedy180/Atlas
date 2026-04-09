package dev.atlasmod.fabric;

import dev.atlasmod.api.AtlasApi;
import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.recipe.RecipeNode;
import dev.atlasmod.search.SearchIndex;
import dev.atlasmod.search.SearchQuery;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Quick Mode: lightweight overlay panel drawn alongside inventory screens.
 * Shows search results and recipe quick-view without leaving the inventory.
 * Toggled via keybind (default: O).
 */
public final class QuickModeOverlay {

    private static boolean enabled = false;
    private static String searchText = "";
    private static List<EntryKey> results = List.of();
    private static int scrollOffset = 0;

    // Currently hovered/selected item in the overlay
    private static EntryKey selectedEntry;
    private static List<RecipeNode> selectedRecipes = List.of();

    // Layout
    private static final int PANEL_WIDTH = 130;
    private static final int ITEM_SIZE = 18;
    private static final int PADDING = 4;
    private static final int HEADER_HEIGHT = 14;

    // Colors
    private static final int BG_COLOR = 0xCC101018;
    private static final int BORDER_COLOR = 0xFF404060;
    private static final int TEXT_COLOR = 0xFFCCCCCC;
    private static final int HEADER_COLOR = 0xFFE0E0FF;
    private static final int HIGHLIGHT_COLOR = 0x40FFFFFF;

    private QuickModeOverlay() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggle() {
        enabled = !enabled;
        if (enabled) {
            refreshSearch();
        }
    }

    /**
     * Registers the screen event hooks. Called once during mod init.
     */
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!enabled) return;
            if (!(screen instanceof AbstractContainerScreen<?>)) return;

            // Hook into the screen's render cycle to draw our overlay
            ScreenEvents.afterExtract(screen).register((scr, graphics, mouseX, mouseY, tickDelta) -> {
                if (!enabled) return;
                drawOverlay(graphics, scr, mouseX, mouseY);
            });
        });
    }

    private static void refreshSearch() {
        SearchIndex index = AtlasFabricClient.searchIndex();
        if (index == null) {
            results = List.of();
            return;
        }
        SearchQuery query = SearchQuery.parse(searchText);
        results = index.search(query);
        scrollOffset = 0;
    }

    /**
     * Called from key event handler when search text changes.
     */
    public static void setSearchText(String text) {
        searchText = text;
        refreshSearch();
    }

    /**
     * Handles mouse scroll within the overlay panel area.
     * Returns true if the scroll was consumed.
     */
    public static boolean handleScroll(Screen screen, double mouseX, double mouseY, double scrollY) {
        if (!enabled) return false;
        int panelX = screen.width - PANEL_WIDTH - PADDING;
        if (mouseX >= panelX && mouseX <= screen.width) {
            scrollOffset = Math.max(0, scrollOffset - (int) scrollY);
            return true;
        }
        return false;
    }

    /**
     * Handles mouse click within the overlay panel.
     * Returns true if the click was consumed.
     */
    public static boolean handleClick(Screen screen, double mouseX, double mouseY, int button) {
        if (!enabled || button != 0) return false;

        int panelX = screen.width - PANEL_WIDTH - PADDING;
        int startY = PADDING + HEADER_HEIGHT + 2;
        int columns = Math.max(1, (PANEL_WIDTH - PADDING * 2) / ITEM_SIZE);

        if (mouseX >= panelX && mouseX <= screen.width && mouseY >= startY) {
            int relX = (int) mouseX - panelX - PADDING;
            int relY = (int) mouseY - startY;
            int col = relX / ITEM_SIZE;
            int row = relY / ITEM_SIZE;
            int idx = (row + scrollOffset) * columns + col;

            if (col >= 0 && col < columns && idx >= 0 && idx < results.size()) {
                selectedEntry = results.get(idx);
                selectedRecipes = AtlasApi.get().recipeGraph().recipesFor(selectedEntry);
                return true;
            }
        }
        return false;
    }

    // ── Rendering ────────────────────────────────────────────────────────

    private static void drawOverlay(GuiGraphicsExtractor gfx, Screen screen, int mouseX, int mouseY) {
        Font font = Screens.getFont(screen);
        int panelX = screen.width - PANEL_WIDTH - PADDING;
        int panelY = PADDING;
        int panelH = screen.height - PADDING * 2;

        // Panel background and border
        gfx.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelH, BG_COLOR);
        // Top border
        gfx.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 1, BORDER_COLOR);
        // Bottom border
        gfx.fill(panelX, panelY + panelH - 1, panelX + PANEL_WIDTH, panelY + panelH, BORDER_COLOR);
        // Left border
        gfx.fill(panelX, panelY, panelX + 1, panelY + panelH, BORDER_COLOR);
        // Right border
        gfx.fill(panelX + PANEL_WIDTH - 1, panelY, panelX + PANEL_WIDTH, panelY + panelH, BORDER_COLOR);

        // Header
        gfx.text(font, Component.literal("Atlas Quick"), panelX + PADDING, panelY + 3, HEADER_COLOR);
        int y = panelY + HEADER_HEIGHT + 2;

        // Item grid
        int columns = Math.max(1, (PANEL_WIDTH - PADDING * 2) / ITEM_SIZE);
        int maxRows = (panelH - HEADER_HEIGHT - 4) / ITEM_SIZE;

        // Split: top half for items, bottom half for recipe preview
        int itemRows = selectedEntry != null ? maxRows / 2 : maxRows;
        int visibleItems = columns * itemRows;

        gfx.enableScissor(panelX + PADDING, y, panelX + PANEL_WIDTH - PADDING, y + itemRows * ITEM_SIZE);

        for (int i = 0; i < visibleItems && (i + scrollOffset * columns) < results.size(); i++) {
            int idx = i + scrollOffset * columns;
            EntryKey entry = results.get(idx);

            int col = i % columns;
            int row = i / columns;
            int ix = panelX + PADDING + col * ITEM_SIZE;
            int iy = y + row * ITEM_SIZE;

            // Highlight selected item
            if (entry.equals(selectedEntry)) {
                gfx.fill(ix - 1, iy - 1, ix + ITEM_SIZE - 2, iy + ITEM_SIZE - 2, HIGHLIGHT_COLOR);
            }

            ItemStack stack = entryToStack(entry);
            if (!stack.isEmpty()) {
                gfx.item(stack, ix, iy);
            }
        }

        gfx.disableScissor();

        // Recipe preview if an item is selected
        if (selectedEntry != null) {
            int recipeY = y + itemRows * ITEM_SIZE + 4;
            drawRecipePreview(gfx, font, panelX + PADDING, recipeY, panelH - (recipeY - panelY));
        }
    }

    private static void drawRecipePreview(GuiGraphicsExtractor gfx, Font font, int x, int y, int maxHeight) {
        if (selectedRecipes.isEmpty()) {
            gfx.text(font, Component.literal("No recipes"), x, y, TEXT_COLOR);
            return;
        }

        // Divider
        gfx.fill(x - 2, y - 2, x + PANEL_WIDTH - PADDING * 2, y - 1, BORDER_COLOR);

        // Show item name
        ItemStack stack = entryToStack(selectedEntry);
        if (!stack.isEmpty()) {
            gfx.item(stack, x, y);
            gfx.text(font, stack.getHoverName(), x + 20, y + 4, HEADER_COLOR);
        }
        y += 20;

        // Show first few recipes compactly
        int shown = 0;
        for (RecipeNode recipe : selectedRecipes) {
            if (y + ITEM_SIZE > maxHeight) break;
            if (shown >= 4) break; // cap to avoid overflow

            // Category label
            String cat = recipe.categoryId().replace("minecraft:", "").replace("atlas:", "");
            gfx.text(font, Component.literal(cat), x, y, 0xFF8888FF);
            y += 10;

            // Inputs -> Output in a compact row
            int ix = x;
            for (var input : recipe.inputs()) {
                if (ix + ITEM_SIZE > x + PANEL_WIDTH - PADDING * 2) break;
                ItemStack inputStack = idToStack(input.id());
                if (!inputStack.isEmpty()) {
                    gfx.item(inputStack, ix, y);
                    ix += ITEM_SIZE;
                }
            }
            gfx.text(font, Component.literal(">"), ix + 2, y + 4, TEXT_COLOR);
            ix += 12;
            for (var output : recipe.outputs()) {
                ItemStack outputStack = entryToStack(output);
                if (!outputStack.isEmpty()) {
                    gfx.item(outputStack, ix, y);
                    ix += ITEM_SIZE;
                }
            }
            y += ITEM_SIZE + 2;
            shown++;
        }
    }

    // ── Utility ──────────────────────────────────────────────────────────

    private static ItemStack entryToStack(EntryKey entry) {
        if (!"item".equals(entry.type())) return ItemStack.EMPTY;
        return idToStack(entry.id());
    }

    private static ItemStack idToStack(String id) {
        try {
            Identifier loc = Identifier.parse(id);
            var optHolder = BuiltInRegistries.ITEM.get(loc);
            if (optHolder.isPresent()) {
                Item item = optHolder.get().value();
                return new ItemStack(item);
            }
        } catch (Exception ignored) {
            // Malformed ID
        }
        return ItemStack.EMPTY;
    }
}
