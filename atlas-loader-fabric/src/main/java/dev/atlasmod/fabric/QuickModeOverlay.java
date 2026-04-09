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

    // Filter chips
    private static final String[] FILTER_OPTIONS = {"All", "@minecraft"};
    private static final String[] FILTER_LABELS  = {"All", "Vanilla"};
    private static int activeFilter = 0;
    private static final int FILTER_HEIGHT = 12;

    // Help overlay
    private static boolean showHelp = false;

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

        // Any click inside the panel is consumed to prevent inventory interaction.

        // Help toggle: ? button in header
        Font font = screen.getFont();
        int panelX = panelX(screen);
        int helpBtnX = panelX + PANEL_WIDTH - PADDING - font.width("?") - 4;
        int helpBtnY = PADDING + 2;
        if (mouseX >= helpBtnX - 1 && mouseX <= helpBtnX + font.width("?") + 3
                && mouseY >= helpBtnY - 1 && mouseY <= helpBtnY + 10) {
            showHelp = !showHelp;
            return true;
        }

        // Close help on any other click
        if (showHelp) {
            showHelp = false;
            return true;
        }

        if (isPointInSearchBox(screen, mouseX, mouseY)) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;

        // Filter chips
        int filterY = PADDING + HEADER_HEIGHT + SEARCH_HEIGHT + 1;
        if (mouseY >= filterY && mouseY < filterY + FILTER_HEIGHT) {
            int fx = panelX + PADDING;
            for (int i = 0; i < FILTER_LABELS.length; i++) {
                int tw = font.width(FILTER_LABELS[i]) + 6;
                if (mouseX >= fx && mouseX < fx + tw) {
                    activeFilter = i;
                    // Apply filter by updating search text prefix
                    applyFilter();
                    return true;
                }
                fx += tw + 2;
            }
        }

        int gridTop = filterY + FILTER_HEIGHT + 2;
        int columns = Math.max(1, (PANEL_WIDTH - PADDING * 2) / ITEM_SIZE);

        // Dynamic grid bottom
        int panelBottom = screen.height - PADDING;
        int recipeH = computeRecipePreviewHeight(font);
        int gridBottom = recipeH > 0 ? panelBottom - recipeH - 2 : panelBottom;

        if (mouseY >= gridTop && mouseY < gridBottom) {
            int relX = (int) mouseX - panelX - PADDING;
            int relY = (int) mouseY - gridTop;
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

    private static void applyFilter() {
        String filterPrefix = activeFilter > 0 ? FILTER_OPTIONS[activeFilter] + " " : "";
        // Strip any existing @filter from searchText
        String clean = searchText.replaceAll("@\\S+\\s*", "").trim();
        setSearchText(filterPrefix + clean);
    }

    /**
     * Handles key presses while quick mode is active.
     * Returns true if Atlas consumed the key.
     */
    public static boolean handleKeyPress(Screen screen, KeyEvent event) {
        if (!enabled || !(screen instanceof AbstractContainerScreen<?>)) return false;

        int key = event.key();

        // Open search on '/' — both US layout (GLFW_KEY_SLASH) and DE layout (Shift+7)
        if (!searchFocused) {
            if (showHelp && key == GLFW.GLFW_KEY_ESCAPE) {
                showHelp = false;
                return true;
            }
            boolean isSlash = key == GLFW.GLFW_KEY_SLASH
                    || (key == GLFW.GLFW_KEY_7 && (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0);
            if (isSlash) {
                searchFocused = true;
                return true;
            }
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

        // Header + ? button
        gfx.text(font, Component.literal("Atlas Quick"), panelX + PADDING, panelY + 3, HEADER_COLOR);
        int helpBtnX = panelX + PANEL_WIDTH - PADDING - font.width("?") - 4;
        int helpBtnY = panelY + 2;
        gfx.fill(helpBtnX - 1, helpBtnY - 1, helpBtnX + font.width("?") + 3, helpBtnY + 10, 0x66404060);
        gfx.text(font, Component.literal("?"), helpBtnX + 1, helpBtnY, 0xFFAAAAFF);

        // Search field
        int searchY = panelY + HEADER_HEIGHT;
        int searchX = panelX + PADDING;
        int searchW = PANEL_WIDTH - PADDING * 2;
        int searchColor = searchFocused ? 0x663A5A8A : 0x55303030;
        gfx.fill(searchX, searchY, searchX + searchW, searchY + SEARCH_HEIGHT, searchColor);
        String shownQuery = searchText.isEmpty() ? "Search..." : searchText;
        int searchTextColor = searchText.isEmpty() ? 0xFF888888 : TEXT_COLOR;
        gfx.text(font, Component.literal(shownQuery), searchX + 3, searchY + 4, searchTextColor);

        // Filter chips row
        int filterY = searchY + SEARCH_HEIGHT + 1;
        int fx = panelX + PADDING;
        for (int i = 0; i < FILTER_LABELS.length; i++) {
            String lbl = FILTER_LABELS[i];
            int tw = font.width(lbl) + 6;
            int bgColor = (i == activeFilter) ? 0x883A5A8A : 0x44303030;
            gfx.fill(fx, filterY, fx + tw, filterY + FILTER_HEIGHT, bgColor);
            gfx.text(font, Component.literal(lbl), fx + 3, filterY + 2, i == activeFilter ? HEADER_COLOR : 0xFF888888);
            fx += tw + 2;
        }

        int gridTop = filterY + FILTER_HEIGHT + 2;
        int columns = Math.max(1, (PANEL_WIDTH - PADDING * 2) / ITEM_SIZE);

        // Compute dynamic recipe area height
        int recipeH = computeRecipePreviewHeight(font);
        int recipeAreaTop = panelBottom - PADDING;
        if (recipeH > 0) {
            recipeAreaTop = panelBottom - recipeH;
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

        // Help overlay (drawn last, on top)
        if (showHelp) {
            drawHelpOverlay(gfx, font, panelX, panelY, panelBottom);
        }
    }

    private static int computeRecipePreviewHeight(Font font) {
        if (selectedEntry == null || selectedRecipes.isEmpty()) return 0;
        int h = 4; // divider + gap
        int shown = 0;
        for (RecipeNode recipe : selectedRecipes) {
            if (shown >= 3) break;
            h += 10; // label
            if (recipe.categoryId().equals("minecraft:crafting")) {
                h += 3 * GRID_SLOT + 3;
            } else {
                h += GRID_SLOT + 3;
            }
            shown++;
        }
        return h;
    }

    private static void drawRecipePreview(GuiGraphicsExtractor gfx, Font font, int x, int y, int bottomY) {
        if (selectedRecipes.isEmpty()) {
            gfx.text(font, Component.literal("No recipes"), x, y, TEXT_COLOR);
            return;
        }

        // Divider line
        gfx.fill(x - 2, y, x + PANEL_WIDTH - PADDING * 2, y + 1, BORDER_COLOR);
        y += 3;

        // Show at most 3 recipes
        int shown = 0;
        for (RecipeNode recipe : selectedRecipes) {
            if (y + GRID_SLOT > bottomY) break;
            if (shown >= 3) break;

            String label = categoryLabel(recipe.categoryId());
            var inputs = recipe.inputs();
            var outputs = recipe.outputs();
            boolean isCrafting = recipe.categoryId().equals("minecraft:crafting");

            // Category label
            gfx.text(font, Component.literal(label), x, y, 0xFF8888FF);
            y += 10;

            if (isCrafting) {
                // ── Always render a full 3x3 crafting grid ──
                int gw = recipe.gridWidth();
                int gh = recipe.gridHeight();
                int gridPixelSize = 3 * GRID_SLOT;

                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 3; col++) {
                        int sx = x + col * GRID_SLOT;
                        int sy = y + row * GRID_SLOT;
                        gfx.fill(sx, sy, sx + GRID_SLOT - 1, sy + GRID_SLOT - 1, 0x44FFFFFF);

                        IngredientKey ingredient = getGridIngredient(inputs, gw, gh, row, col);
                        if (ingredient != null && !ingredient.isEmpty()) {
                            ItemStack inputStack = ingredientToStack(ingredient);
                            if (!inputStack.isEmpty()) {
                                gfx.item(inputStack, sx, sy);
                            }
                        }
                    }
                }

                // Arrow and output centered vertically next to the 3x3 grid
                int arrowX = x + gridPixelSize + 3;
                int centerY = y + gridPixelSize / 2 - 4;
                gfx.text(font, Component.literal("\u2192"), arrowX, centerY, TEXT_COLOR);

                int outX = arrowX + 12;
                int outY = y + gridPixelSize / 2 - GRID_SLOT / 2;
                for (var output : outputs) {
                    ItemStack outputStack = entryToStack(output);
                    if (!outputStack.isEmpty()) {
                        gfx.item(outputStack, outX, outY);
                        outX += GRID_SLOT;
                    }
                }
                y += gridPixelSize + 3;
            } else {
                // ── Non-crafting: compact input → output row ──
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

    /**
     * Returns the ingredient for a position in a 3x3 crafting grid.
     * For shaped recipes (gw x gh), the recipe is placed top-left.
     * For shapeless (gw == 0), items fill left-to-right, top-to-bottom.
     */
    private static IngredientKey getGridIngredient(List<IngredientKey> inputs, int gw, int gh, int row, int col) {
        if (gw > 0 && gh > 0) {
            // Shaped: only cells within the recipe dimensions have content
            if (col < gw && row < gh) {
                int idx = row * gw + col;
                return idx < inputs.size() ? inputs.get(idx) : IngredientKey.EMPTY;
            }
            return IngredientKey.EMPTY;
        }
        // Shapeless: fill sequentially
        int idx = row * 3 + col;
        return idx < inputs.size() ? inputs.get(idx) : IngredientKey.EMPTY;
    }

    /**
     * Maps a category ID to a short human-readable label.
     */
    private static String categoryLabel(String categoryId) {
        return switch (categoryId) {
            case "minecraft:crafting"     -> "Crafting";
            case "minecraft:smelting"     -> "Smelting";
            case "minecraft:blasting"     -> "Blasting";
            case "minecraft:smoking"      -> "Smoking";
            case "minecraft:campfire"     -> "Campfire";
            case "minecraft:stonecutting" -> "Stonecutting";
            case "minecraft:smithing"     -> "Smithing";
            default -> {
                // Strip namespace, capitalize
                String raw = categoryId.contains(":") ? categoryId.substring(categoryId.indexOf(':') + 1) : categoryId;
                yield raw.substring(0, 1).toUpperCase() + raw.substring(1).replace('_', ' ');
            }
        };
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

    private static void drawHelpOverlay(GuiGraphicsExtractor gfx, Font font, int panelX, int panelY, int panelBottom) {
        // Semi-transparent background over the whole panel
        gfx.fill(panelX + 2, panelY + 2, panelX + PANEL_WIDTH - 2, panelBottom - 2, 0xEE101018);

        int x = panelX + PADDING + 2;
        int y = panelY + 6;
        int lineH = 10;

        gfx.text(font, Component.literal("Atlas Quick Mode"), x, y, HEADER_COLOR); y += lineH + 2;
        gfx.text(font, Component.literal("Click an item to see"), x, y, TEXT_COLOR); y += lineH;
        gfx.text(font, Component.literal("its recipes below."), x, y, TEXT_COLOR); y += lineH + 2;
        gfx.text(font, Component.literal("Click the search bar"), x, y, TEXT_COLOR); y += lineH;
        gfx.text(font, Component.literal("or press any key to"), x, y, TEXT_COLOR); y += lineH;
        gfx.text(font, Component.literal("filter items by name."), x, y, TEXT_COLOR); y += lineH + 2;
        gfx.text(font, Component.literal("Search prefixes:"), x, y, 0xFF8888FF); y += lineH;
        gfx.text(font, Component.literal(" @mod  - filter by mod"), x, y, TEXT_COLOR); y += lineH;
        gfx.text(font, Component.literal(" $tag  - filter by tag"), x, y, TEXT_COLOR); y += lineH;
        gfx.text(font, Component.literal(" #text - search tooltip"), x, y, TEXT_COLOR); y += lineH + 2;
        gfx.text(font, Component.literal("Use the filter buttons"), x, y, TEXT_COLOR); y += lineH;
        gfx.text(font, Component.literal("to switch All/Vanilla."), x, y, TEXT_COLOR); y += lineH + 2;
        gfx.text(font, Component.literal("Scroll to browse items."), x, y, TEXT_COLOR); y += lineH;
        gfx.text(font, Component.literal("Press O to toggle panel."), x, y, TEXT_COLOR); y += lineH;
        gfx.text(font, Component.literal("Press Esc to close help."), x, y, TEXT_COLOR);
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

        // Number row: handle shifted chars for DE layout
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            if (shifted) {
                // DE layout shifted number row: common chars
                return switch (key) {
                    case GLFW.GLFW_KEY_7 -> '/';
                    case GLFW.GLFW_KEY_8 -> '(';
                    case GLFW.GLFW_KEY_9 -> ')';
                    case GLFW.GLFW_KEY_0 -> '=';
                    default -> 0; // skip other shifted numbers
                };
            }
            return (char) ('0' + (key - GLFW.GLFW_KEY_0));
        }

        if (key == GLFW.GLFW_KEY_SLASH) return '/';
        if (key == GLFW.GLFW_KEY_MINUS) return '-';
        if (key == GLFW.GLFW_KEY_PERIOD) return '.';
        if (key == GLFW.GLFW_KEY_SEMICOLON) return shifted ? ':' : ';';
        if (key == GLFW.GLFW_KEY_APOSTROPHE) return '\'';
        if (key == GLFW.GLFW_KEY_COMMA) return ',';

        return 0;
    }
}
