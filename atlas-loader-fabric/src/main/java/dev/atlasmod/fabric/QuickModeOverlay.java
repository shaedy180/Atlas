package dev.atlasmod.fabric;

import dev.atlasmod.api.AtlasApi;
import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.entry.IngredientKey;
import dev.atlasmod.core.recipe.RecipeNode;
import dev.atlasmod.search.SearchIndex;
import dev.atlasmod.search.SearchQuery;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Quick Mode: lightweight overlay panel drawn alongside inventory screens.
 * Shows search results and recipe quick-view without leaving the inventory.
 * Toggled via keybind (default: O).
 */
public final class QuickModeOverlay {

    private static final Logger LOGGER = LoggerFactory.getLogger("Atlas/QuickMode");

    private static boolean enabled = false;
    private static String searchText = "";
    private static boolean searchFocused = false;
    private static List<EntryKey> results = List.of();
    private static int scrollOffset = 0;

    // Currently hovered/selected item in the overlay
    private static EntryKey selectedEntry;
    private static List<RecipeNode> selectedRecipes = List.of();

    // Layout
    private static final int PANEL_WIDTH = 130;
    private static final int ITEM_SIZE = 18;
    private static final int GRID_SLOT = 16;
    private static final int PADDING = 4;
    private static final int HEADER_HEIGHT = 16;
    private static final int SEARCH_HEIGHT = 16;
    private static final int MAX_SEARCH_CHARS = 64;
    private static final int RECIPE_PREVIEW_HEIGHT = 80;

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
        } else {
            searchFocused = false;
        }
    }

    /**
     * Registers the screen event hooks. Called once during mod init.
     */
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;

            // Hook into the screen's render cycle to draw our overlay
            ScreenEvents.afterExtract(screen).register((scr, graphics, mouseX, mouseY, tickDelta) -> {
                if (!enabled) return;
                drawOverlay(graphics, scr, mouseX, mouseY);
            });

            ScreenMouseEvents.allowMouseClick(screen).register((scr, event) -> !handleClick(scr, event));
            ScreenMouseEvents.allowMouseScroll(screen)
                    .register((scr, mouseX, mouseY, scrollX, scrollY) -> !handleScroll(scr, mouseX, mouseY, scrollY));
            ScreenKeyboardEvents.allowKeyPress(screen).register((scr, event) -> !handleKeyPress(scr, event));
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
        String normalized = text == null ? "" : text;
        if (normalized.length() > MAX_SEARCH_CHARS) {
            normalized = normalized.substring(0, MAX_SEARCH_CHARS);
        }
        searchText = normalized;
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
    public static boolean handleClick(Screen screen, MouseButtonEvent event) {
        if (!enabled || event.button() != 0) return false;

        double mouseX = event.x();
        double mouseY = event.y();
        if (!isPointInPanel(screen, mouseX, mouseY)) {
            searchFocused = false;
            return false;
        }

        // Any click inside the panel is consumed to prevent inventory interaction
        // beneath the overlay. We still process search/grid hits below.

        if (isPointInSearchBox(screen, mouseX, mouseY)) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;

        int panelX = panelX(screen);
        int startY = gridStartY();
        int columns = Math.max(1, (PANEL_WIDTH - PADDING * 2) / ITEM_SIZE);

        // Compute the bottom boundary of the item grid (exclude recipe preview area)
        int panelBottom = screen.height - PADDING;
        int gridBottom = panelBottom;
        if (selectedEntry != null && !selectedRecipes.isEmpty()) {
            gridBottom = panelBottom - RECIPE_PREVIEW_HEIGHT;
        }

        if (mouseY >= startY && mouseY < gridBottom) {
            int relX = (int) mouseX - panelX - PADDING;
            int relY = (int) mouseY - startY;
            int col = relX / ITEM_SIZE;
            int row = relY / ITEM_SIZE;
            int idx = (row + scrollOffset) * columns + col;

            if (col >= 0 && col < columns && idx >= 0 && idx < results.size()) {
                selectedEntry = results.get(idx);
                selectedRecipes = AtlasApi.get().recipeGraph().recipesFor(selectedEntry);
                LOGGER.info("[Atlas] Quick select: {} -> {} recipes", selectedEntry.id(), selectedRecipes.size());
            }
        }
        return true;
    }

    /**
     * Handles key presses while quick mode is active.
     * Returns true if Atlas consumed the key.
     */
    public static boolean handleKeyPress(Screen screen, KeyEvent event) {
        if (!enabled || !(screen instanceof AbstractContainerScreen<?>)) return false;

        int key = event.key();

        // Open search input with '/' for fast keyboard-driven filtering.
        if (!searchFocused && key == GLFW.GLFW_KEY_SLASH) {
            searchFocused = true;
            return true;
        }

        if (!searchFocused) return false;

        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            searchFocused = false;
            return true;
        }

        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!searchText.isEmpty()) {
                setSearchText(searchText.substring(0, searchText.length() - 1));
            }
            return true;
        }

        if (key == GLFW.GLFW_KEY_SPACE) {
            setSearchText(searchText + " ");
            return true;
        }

        char c = keyToSearchChar(key, event.modifiers());
        if (c != 0) {
            setSearchText(searchText + c);
            return true;
        }

        return false;
    }

    // ── Rendering ────────────────────────────────────────────────────────

    private static void drawOverlay(GuiGraphicsExtractor gfx, Screen screen, int mouseX, int mouseY) {
        Font font = screen.getFont();
        int panelX = panelX(screen);
        int panelY = PADDING;
        int panelH = screen.height - PADDING * 2;
        int panelBottom = panelY + panelH;

        // Panel background and border
        gfx.fill(panelX, panelY, panelX + PANEL_WIDTH, panelBottom, BG_COLOR);
        gfx.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 1, BORDER_COLOR);
        gfx.fill(panelX, panelBottom - 1, panelX + PANEL_WIDTH, panelBottom, BORDER_COLOR);
        gfx.fill(panelX, panelY, panelX + 1, panelBottom, BORDER_COLOR);
        gfx.fill(panelX + PANEL_WIDTH - 1, panelY, panelX + PANEL_WIDTH, panelBottom, BORDER_COLOR);

        // Header
        gfx.text(font, Component.literal("Atlas Quick"), panelX + PADDING, panelY + 3, HEADER_COLOR);

        // Search field
        int searchY = panelY + HEADER_HEIGHT;
        int searchX = panelX + PADDING;
        int searchW = PANEL_WIDTH - PADDING * 2;
        int searchColor = searchFocused ? 0x663A5A8A : 0x55303030;
        gfx.fill(searchX, searchY, searchX + searchW, searchY + SEARCH_HEIGHT, searchColor);
        String shownQuery = searchText.isEmpty() ? "Search (/)..." : searchText;
        int searchTextColor = searchText.isEmpty() ? 0xFF888888 : TEXT_COLOR;
        gfx.text(font, Component.literal(shownQuery), searchX + 3, searchY + 4, searchTextColor);

        int gridTop = gridStartY();
        int columns = Math.max(1, (PANEL_WIDTH - PADDING * 2) / ITEM_SIZE);

        // Reserve space at the bottom for recipe preview when an item is selected
        int recipeAreaTop = panelBottom - PADDING;
        if (selectedEntry != null && !selectedRecipes.isEmpty()) {
            recipeAreaTop = panelBottom - RECIPE_PREVIEW_HEIGHT;
        }

        int gridBottom = recipeAreaTop - 2;
        int itemRows = Math.max(1, (gridBottom - gridTop) / ITEM_SIZE);
        int visibleItems = columns * itemRows;

        gfx.enableScissor(panelX + PADDING, gridTop, panelX + PANEL_WIDTH - PADDING, gridTop + itemRows * ITEM_SIZE);

        for (int i = 0; i < visibleItems && (i + scrollOffset * columns) < results.size(); i++) {
            int idx = i + scrollOffset * columns;
            EntryKey entry = results.get(idx);

            int col = i % columns;
            int row = i / columns;
            int ix = panelX + PADDING + col * ITEM_SIZE;
            int iy = gridTop + row * ITEM_SIZE;

            if (entry.equals(selectedEntry)) {
                gfx.fill(ix - 1, iy - 1, ix + ITEM_SIZE - 2, iy + ITEM_SIZE - 2, HIGHLIGHT_COLOR);
            }

            ItemStack stack = entryToStack(entry);
            if (!stack.isEmpty()) {
                gfx.item(stack, ix, iy);

                if (mouseX >= ix && mouseX < ix + ITEM_SIZE && mouseY >= iy && mouseY < iy + ITEM_SIZE) {
                    gfx.setTooltipForNextFrame(font, stack, mouseX, mouseY);
                }
            }
        }

        gfx.disableScissor();

        // Recipe preview at the bottom
        if (selectedEntry != null && !selectedRecipes.isEmpty()) {
            drawRecipePreview(gfx, font, panelX + PADDING, recipeAreaTop, panelBottom - PADDING);
        }
    }

    private static void drawRecipePreview(GuiGraphicsExtractor gfx, Font font, int x, int y, int bottomY) {
        if (selectedRecipes.isEmpty()) {
            gfx.text(font, Component.literal("No recipes"), x, y, TEXT_COLOR);
            return;
        }

        // Divider line
        gfx.fill(x - 2, y, x + PANEL_WIDTH - PADDING * 2, y + 1, BORDER_COLOR);
        y += 3;

        // Show at most 2 recipes
        int shown = 0;
        for (RecipeNode recipe : selectedRecipes) {
            if (y + GRID_SLOT > bottomY) break;
            if (shown >= 2) break;

            int gw = recipe.gridWidth();
            int gh = recipe.gridHeight();
            var inputs = recipe.inputs();
            var outputs = recipe.outputs();

            if (gw > 0 && gh > 0) {
                // ── Shaped crafting: render as a real grid ──
                int gridPixelW = gw * GRID_SLOT;
                int gridPixelH = gh * GRID_SLOT;

                // Grid on the left
                for (int row = 0; row < gh; row++) {
                    for (int col = 0; col < gw; col++) {
                        int slotIdx = row * gw + col;
                        int sx = x + col * GRID_SLOT;
                        int sy = y + row * GRID_SLOT;
                        // Slot background
                        gfx.fill(sx, sy, sx + GRID_SLOT - 1, sy + GRID_SLOT - 1, 0x44FFFFFF);

                        if (slotIdx < inputs.size() && !inputs.get(slotIdx).isEmpty()) {
                            ItemStack inputStack = ingredientToStack(inputs.get(slotIdx));
                            if (!inputStack.isEmpty()) {
                                gfx.item(inputStack, sx, sy);
                            }
                        }
                    }
                }

                // Arrow and output, centered vertically next to the grid
                int arrowX = x + gridPixelW + 3;
                int centerY = y + gridPixelH / 2 - 4;
                gfx.text(font, Component.literal("\u2192"), arrowX, centerY, TEXT_COLOR);

                int outX = arrowX + 12;
                int outY = y + gridPixelH / 2 - GRID_SLOT / 2;
                for (var output : outputs) {
                    ItemStack outputStack = entryToStack(output);
                    if (!outputStack.isEmpty()) {
                        gfx.item(outputStack, outX, outY);
                        outX += GRID_SLOT;
                    }
                }

                y += gridPixelH + 3;
            } else {
                // ── Shapeless / other: compact horizontal row ──
                int ix = x;
                for (var input : inputs) {
                    if (ix + GRID_SLOT > x + PANEL_WIDTH - PADDING * 2) break;
                    if (input.isEmpty()) continue;
                    ItemStack inputStack = ingredientToStack(input);
                    if (!inputStack.isEmpty()) {
                        gfx.item(inputStack, ix, y);
                        ix += GRID_SLOT;
                    }
                }
                gfx.text(font, Component.literal("\u2192"), ix + 2, y + 4, TEXT_COLOR);
                ix += 12;
                for (var output : outputs) {
                    ItemStack outputStack = entryToStack(output);
                    if (!outputStack.isEmpty()) {
                        gfx.item(outputStack, ix, y);
                        ix += GRID_SLOT;
                    }
                }
                y += GRID_SLOT + 3;
            }
            shown++;
        }
    }

    // ── Utility ──────────────────────────────────────────────────────────

    private static ItemStack entryToStack(EntryKey entry) {
        if (!"item".equals(entry.type())) return ItemStack.EMPTY;
        return idToStack(entry.id());
    }

    /**
     * Resolves an IngredientKey to a representative ItemStack.
     * For tag-based ingredients, picks the first item in the tag.
     */
    private static ItemStack ingredientToStack(IngredientKey ingredient) {
        if (ingredient.tagBased()) {
            return tagToStack(ingredient.id());
        }
        return idToStack(ingredient.id());
    }

    private static ItemStack tagToStack(String tagId) {
        try {
            Identifier loc = Identifier.parse(tagId);
            TagKey<Item> tagKey = TagKey.create(BuiltInRegistries.ITEM.key(), loc);
            for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(tagKey)) {
                return new ItemStack(holder.value());
            }
        } catch (Exception ignored) {
        }
        return ItemStack.EMPTY;
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

    private static int panelX(Screen screen) {
        return screen.width - PANEL_WIDTH - PADDING;
    }

    private static int gridStartY() {
        return PADDING + HEADER_HEIGHT + SEARCH_HEIGHT + 2;
    }

    private static boolean isPointInPanel(Screen screen, double mouseX, double mouseY) {
        int panelX = panelX(screen);
        return mouseX >= panelX
                && mouseX <= screen.width - PADDING
                && mouseY >= PADDING
                && mouseY <= screen.height - PADDING;
    }

    private static boolean isPointInSearchBox(Screen screen, double mouseX, double mouseY) {
        int x = panelX(screen) + PADDING;
        int y = PADDING + HEADER_HEIGHT;
        int w = PANEL_WIDTH - PADDING * 2;
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + SEARCH_HEIGHT;
    }

    private static char keyToSearchChar(int key, int modifiers) {
        boolean shifted = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;

        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            char base = (char) ('a' + (key - GLFW.GLFW_KEY_A));
            return shifted ? Character.toUpperCase(base) : base;
        }

        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            return (char) ('0' + (key - GLFW.GLFW_KEY_0));
        }

        if (key == GLFW.GLFW_KEY_MINUS) return '-';
        if (key == GLFW.GLFW_KEY_PERIOD) return '.';
        if (key == GLFW.GLFW_KEY_SEMICOLON) return ';';
        if (key == GLFW.GLFW_KEY_APOSTROPHE) return '\'';
        if (key == GLFW.GLFW_KEY_COMMA) return ',';

        return 0;
    }
}
