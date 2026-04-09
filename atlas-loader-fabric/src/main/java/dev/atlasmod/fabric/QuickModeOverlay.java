package dev.atlasmod.fabric;

import dev.atlasmod.api.AtlasApi;
import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.recipe.RecipeNode;
import dev.atlasmod.search.SearchIndex;
import dev.atlasmod.search.SearchQuery;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Quick Mode: lightweight overlay panel drawn alongside inventory screens.
 * Shows search results and recipe quick-view without leaving the inventory.
 * Toggled via keybind (default: O).
 */
public final class QuickModeOverlay {

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
    private static final int PADDING = 4;
    private static final int HEADER_HEIGHT = 16;
    private static final int SEARCH_HEIGHT = 16;
    private static final int MAX_SEARCH_CHARS = 64;

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

        int panelX = panelX(screen);
        int startY = gridStartY();
        int columns = Math.max(1, (PANEL_WIDTH - PADDING * 2) / ITEM_SIZE);

        if (isPointInSearchBox(screen, mouseX, mouseY)) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;

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
        Font font = Screens.getFont(screen);
        int panelX = panelX(screen);
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

        // Search field
        int searchY = panelY + HEADER_HEIGHT;
        int searchX = panelX + PADDING;
        int searchW = PANEL_WIDTH - PADDING * 2;
        int searchColor = searchFocused ? 0x663A5A8A : 0x55303030;
        gfx.fill(searchX, searchY, searchX + searchW, searchY + SEARCH_HEIGHT, searchColor);
        String shownQuery = searchText.isEmpty() ? "Search (/)..." : searchText;
        int searchTextColor = searchText.isEmpty() ? 0xFF888888 : TEXT_COLOR;
        gfx.text(font, Component.literal(shownQuery), searchX + 3, searchY + 4, searchTextColor);

        int y = gridStartY();

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

                if (mouseX >= ix && mouseX < ix + ITEM_SIZE && mouseY >= iy && mouseY < iy + ITEM_SIZE) {
                    gfx.setTooltipForNextFrame(font, stack, mouseX, mouseY);
                }
            }
        }

        gfx.disableScissor();

        // Recipe preview if an item is selected
        if (selectedEntry != null) {
            int recipeY = y + itemRows * ITEM_SIZE + 4;
            drawRecipePreview(gfx, font, panelX + PADDING, recipeY, panelY + panelH - PADDING);
        }
    }

    private static void drawRecipePreview(GuiGraphicsExtractor gfx, Font font, int x, int y, int bottomY) {
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
            if (y + ITEM_SIZE > bottomY) break;
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
