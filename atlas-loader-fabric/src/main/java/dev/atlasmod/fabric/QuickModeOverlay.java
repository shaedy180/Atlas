package dev.atlasmod.fabric;

import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.entry.IngredientKey;
import dev.atlasmod.core.recipe.RecipeNode;
import dev.atlasmod.ui.PinnedPlanManager;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Quick Mode: lightweight overlay panel drawn alongside inventory screens.
 * Shows search results and recipe quick-view without leaving the inventory.
 * Toggled via keybind (default: O).
 */
public final class QuickModeOverlay {

    private static final Logger LOGGER = LoggerFactory.getLogger("Atlas/QuickMode");

    private static boolean enabled = true;
    private static String searchText = "";
    private static boolean searchFocused = false;
    private static List<EntryKey> results = List.of();
    private static int scrollOffset = 0;

    // Currently hovered/selected item in the overlay
    private static EntryKey selectedEntry;
    private static List<RecipeNode> selectedRecipes = List.of();

    // Layout (panelWidth is user-resizable)
    private static int panelWidth = 130;
    private static final int MIN_PANEL_WIDTH = 80;
    private static final int MAX_PANEL_WIDTH = 300;
    private static final int ITEM_SIZE = 18;
    private static final int GRID_SLOT = 16;
    private static final int PADDING = 4;
    private static final int HEADER_HEIGHT = 16;
    private static final int SEARCH_HEIGHT = 16;
    private static final int MAX_SEARCH_CHARS = 64;

    // Resize drag state
    private static boolean draggingResize = false;
    private static double dragStartX = 0;

    // Filter chips (dynamic: rebuilt when recipes load)
    private static List<String> filterOptions = new ArrayList<>(List.of("", "@minecraft"));
    private static List<String> filterLabels  = new ArrayList<>(List.of("All", "Vanilla"));
    private static int activeFilter = 0;
    private static int filterScrollOffset = 0;
    private static final int FILTER_HEIGHT = 12;
    private static boolean filtersInitialized = false;

    // Actionbar feedback timer
    private static String feedbackMessage = null;
    private static long feedbackExpireTime = 0;

    // Recipe item hit boxes for tooltip and click-through
    private record ItemHitBox(int x, int y, int w, int h, EntryKey entry, ItemStack stack) {}
    private static final List<ItemHitBox> recipeHitBoxes = new ArrayList<>();

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

    // ── Panel width persistence ──────────────────────────────────────────

    public static void savePrefs(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "panelWidth=" + panelWidth + "\n");
        } catch (IOException e) {
            LOGGER.warn("[Atlas] Failed to save quick mode prefs", e);
        }
    }

    public static void loadPrefs(Path file) {
        try {
            if (!Files.exists(file)) return;
            for (String line : Files.readAllLines(file)) {
                if (line.startsWith("panelWidth=")) {
                    int w = Integer.parseInt(line.substring("panelWidth=".length()).trim());
                    panelWidth = Math.max(MIN_PANEL_WIDTH, Math.min(MAX_PANEL_WIDTH, w));
                }
            }
            LOGGER.debug("[Atlas] Loaded quick mode prefs: panelWidth={}", panelWidth);
        } catch (Exception e) {
            LOGGER.warn("[Atlas] Failed to load quick mode prefs", e);
        }
    }

    public static void toggle() {
        enabled = !enabled;
        if (enabled) {
            rebuildFilters();
            refreshSearch();
        } else {
            searchFocused = false;
        }
    }

    /**
     * Rebuilds the dynamic filter list from detected mod namespaces.
     */
    public static void rebuildFilters() {
        filterOptions = new ArrayList<>();
        filterLabels = new ArrayList<>();
        // Fixed entries
        filterOptions.add("");
        filterLabels.add("All");
        filterOptions.add("@minecraft");
        filterLabels.add("Vanilla");
        filterOptions.add("Saved");
        filterLabels.add("Saved");
        TreeSet<String> mods = new TreeSet<>(AtlasRuntimeController.clientSnapshot().ownerModIds());
        mods.remove("minecraft");
        mods.remove(AtlasFabricClient.MOD_ID);
        for (String mod : mods) {
            filterOptions.add("@" + mod);
            filterLabels.add(mod.substring(0, 1).toUpperCase() + mod.substring(1));
        }
        // Reset active filter if out of bounds
        if (activeFilter >= filterLabels.size()) {
            activeFilter = 0;
        }
    }

    private static void showFeedback(String message) {
        feedbackMessage = message;
        feedbackExpireTime = System.currentTimeMillis() + 2000;
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendOverlayMessage(Component.literal(message));
        }
    }

    private static EntryKey stackToEntry(ItemStack stack) {
        if (stack.isEmpty()) return null;
        var key = stack.typeHolder().unwrapKey().orElse(null);
        if (key == null) return null;
        return new EntryKey("item", key.identifier().toString());
    }

    /**
     * Registers the screen event hooks. Called once during mod init.
     */
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;

            // Auto-paste a recipe from Deep Mode's "Send to Grid" button
            if (screen instanceof CraftingScreen craftingScreen && AtlasScreen.hasPendingTransfer()) {
                RecipeNode pending = AtlasScreen.consumePendingTransfer();
                if (pending != null) {
                    // Defer one tick so the screen is fully initialized
                    client.schedule(() -> {
                        pasteRecipeIntoCraftingGrid(craftingScreen);
                        showFeedback("Recipe pasted from Atlas");
                    });
                    // Set the recipe as selected so the overlay shows it
                    selectedEntry = pending.outputs().isEmpty() ? null
                            : new EntryKey("item", pending.outputs().getFirst().id());
                    selectedRecipes = List.of(pending);
                }
            }

            // Hook into the screen's render cycle to draw our overlay
            ScreenEvents.afterExtract(screen).register((scr, graphics, mouseX, mouseY, tickDelta) -> {
                if (!enabled) return;
                // Handle resize dragging during render (tracks mouse position)
                if (draggingResize) {
                    long window = Minecraft.getInstance().getWindow().handle();
                    if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_RELEASE) {
                        draggingResize = false;
                    } else {
                        int newWidth = scr.width - (int) mouseX;
                        panelWidth = Math.max(MIN_PANEL_WIDTH, Math.min(MAX_PANEL_WIDTH, newWidth));
                    }
                }
                drawOverlay(graphics, scr, mouseX, mouseY);
            });

            ScreenMouseEvents.allowMouseClick(screen).register((scr, event) -> !handleClick(scr, event));
            ScreenMouseEvents.allowMouseScroll(screen)
                    .register((scr, mouseX, mouseY, scrollX, scrollY) -> !handleScroll(scr, mouseX, mouseY, scrollY));
            ScreenKeyboardEvents.allowKeyPress(screen).register((scr, event) -> !handleKeyPress(scr, event));
        });
    }

    private static void refreshSearch() {
        boolean savedOnly = activeFilter >= 0
                && activeFilter < filterOptions.size()
                && "Saved".equals(filterOptions.get(activeFilter));
        results = AtlasClientSearch.search(searchText, savedOnly);
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
        int panelX = screen.width - panelWidth - PADDING;
        if (mouseX >= panelX && mouseX <= screen.width) {
            // Horizontal scroll for filter chips row
            int filterY = PADDING + HEADER_HEIGHT + SEARCH_HEIGHT + 1;
            if (mouseY >= filterY && mouseY < filterY + FILTER_HEIGHT) {
                Font font = screen.getFont();
                int totalFilterWidth = 0;
                for (int i = 0; i < filterLabels.size(); i++) {
                    totalFilterWidth += font.width(filterLabels.get(i)) + 6 + 2;
                }
                int maxScroll = Math.max(0, totalFilterWidth - (panelWidth - PADDING * 2));
                filterScrollOffset = Math.max(0, Math.min(maxScroll, filterScrollOffset + (int)(scrollY * -8)));
                return true;
            }
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
            // Check if clicking on left edge for resize drag (within 4px of panel edge)
            int panelLeftEdge = panelX(screen);
            if (mouseX >= panelLeftEdge - 4 && mouseX <= panelLeftEdge + 4
                    && mouseY >= PADDING && mouseY <= screen.height - PADDING) {
                draggingResize = true;
                return true;
            }
            searchFocused = false;
            return false;
        }

        // Any click inside the panel is consumed to prevent inventory interaction.

        // Help toggle: ? button in header
        Font font = screen.getFont();
        int panelX = panelX(screen);
        int helpBtnX = panelX + panelWidth - PADDING - font.width("?") - 4;
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
            int fx = panelX + PADDING - filterScrollOffset;
            for (int i = 0; i < filterLabels.size(); i++) {
                int tw = font.width(filterLabels.get(i)) + 6;
                if (mouseX >= Math.max(fx, panelX + PADDING) && mouseX < Math.min(fx + tw, panelX + panelWidth - PADDING)) {
                    activeFilter = i;
                    applyFilter();
                    return true;
                }
                fx += tw + 2;
            }
        }

        int gridTop = filterY + FILTER_HEIGHT + 2;
        int columns = Math.max(1, (panelWidth - PADDING * 2) / ITEM_SIZE);

        // Dynamic grid bottom
        int panelBottom = screen.height - PADDING;
        int recipeH = computeRecipePreviewHeight(font);
        int gridBottom = recipeH > 0 ? panelBottom - recipeH - 2 : panelBottom;

        // Save button: right side of recipe preview divider area
        if (selectedEntry != null && recipeH > 0) {
            int pinY = panelBottom - recipeH;
            String pinLabel = AtlasFabricClient.pinnedPlanManager().isPinned(selectedEntry) ? "Unsave" : "Save";
            int pinW = font.width(pinLabel) + 6;
            int pinX = panelX + panelWidth - PADDING - pinW;
            if (mouseX >= pinX && mouseX < pinX + pinW && mouseY >= pinY && mouseY < pinY + 12) {
                PinnedPlanManager pm = AtlasFabricClient.pinnedPlanManager();
                if (pm.isPinned(selectedEntry)) {
                    pm.unpin(selectedEntry);
                } else {
                    pm.pin(selectedEntry, 1);
                }
                return true;
            }

            // Paste button (left of pin button, only in crafting screen)
            if (screen instanceof CraftingScreen craftingScreen && hasCraftingRecipe()) {
                String pasteLabel = "Paste";
                int pasteW = font.width(pasteLabel) + 6;
                int pasteX = pinX - pasteW - 2;
                if (mouseX >= pasteX && mouseX < pasteX + pasteW && mouseY >= pinY && mouseY < pinY + 12) {
                    pasteRecipeIntoCraftingGrid(craftingScreen);
                    return true;
                }
            }
        }

        // Recipe item click-through: clicking an ingredient/output navigates to its recipes
        if (mouseY >= gridBottom) {
            for (var hitBox : recipeHitBoxes) {
                if (mouseX >= hitBox.x && mouseX < hitBox.x + hitBox.w
                        && mouseY >= hitBox.y && mouseY < hitBox.y + hitBox.h) {
                    selectedEntry = hitBox.entry;
                    selectedRecipes = AtlasFabricClient.recipeGraph().recipesFor(selectedEntry);
                    LOGGER.info("[Atlas] Recipe click-through: {}", selectedEntry.id());
                    return true;
                }
            }
        }

        if (mouseY >= gridTop && mouseY < gridBottom) {
            int relX = (int) mouseX - panelX - PADDING;
            int relY = (int) mouseY - gridTop;
            int col = relX / ITEM_SIZE;
            int row = relY / ITEM_SIZE;
            int idx = (row + scrollOffset) * columns + col;

            if (col >= 0 && col < columns && idx >= 0 && idx < results.size()) {
                selectedEntry = results.get(idx);
                selectedRecipes = AtlasFabricClient.recipeGraph().recipesFor(selectedEntry);

                // Creative mode: give item to player
                giveItemIfCreative(selectedEntry, event);

                LOGGER.info("[Atlas] Quick select: {} -> {} recipes", selectedEntry.id(), selectedRecipes.size());
            }
        }
        return true;
    }

    private static void applyFilter() {
        String filterValue = activeFilter > 0 ? filterOptions.get(activeFilter) : "";
        // "Saved" filter: show only saved items regardless of search prefix
        if ("Saved".equals(filterValue)) {
            // Strip any existing @filter and refresh with saved items
            String clean = searchText.replaceAll("@\\S+\\s*", "").trim();
            searchText = clean;
            refreshSearch();
            return;
        }
        String filterPrefix = activeFilter > 0 ? filterValue + " " : "";
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

        // Use native GLFW character resolution for keyboard-layout-aware input
        String charName = GLFW.glfwGetKeyName(event.key(), event.scancode());

        // Open search on '/' — works on any keyboard layout
        if (!searchFocused) {
            if (showHelp && key == GLFW.GLFW_KEY_ESCAPE) {
                showHelp = false;
                return true;
            }
            if ("/".equals(charName)) {
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

        // Native character resolution handles all keyboard layouts (DE, FR, etc.)
        if (charName != null && !charName.isEmpty()) {
            boolean shifted = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
            String ch = shifted ? charName.toUpperCase() : charName;
            setSearchText(searchText + ch);
            return true;
        }

        return false;
    }

    // ── Rendering ────────────────────────────────────────────────────────

    private static void drawOverlay(GuiGraphicsExtractor gfx, Screen screen, int mouseX, int mouseY) {
        if (!filtersInitialized) {
            rebuildFilters();
            refreshSearch();
            filtersInitialized = true;
        }
        Font font = screen.getFont();
        int panelX = panelX(screen);
        int panelY = PADDING;
        int panelH = screen.height - PADDING * 2;
        int panelBottom = panelY + panelH;

        // Panel background and border
        gfx.fill(panelX, panelY, panelX + panelWidth, panelBottom, BG_COLOR);
        gfx.fill(panelX, panelY, panelX + panelWidth, panelY + 1, BORDER_COLOR);
        gfx.fill(panelX, panelBottom - 1, panelX + panelWidth, panelBottom, BORDER_COLOR);
        gfx.fill(panelX, panelY, panelX + 1, panelBottom, BORDER_COLOR);
        gfx.fill(panelX + panelWidth - 1, panelY, panelX + panelWidth, panelBottom, BORDER_COLOR);

        // Resize handle indicator (3 small dots on left edge)
        int handleColor = draggingResize ? 0xFFAAAAFF : 0x88888888;
        int hx = panelX - 1;
        int centerY = panelY + panelH / 2;
        gfx.fill(hx, centerY - 6, hx + 3, centerY - 4, handleColor);
        gfx.fill(hx, centerY - 1, hx + 3, centerY + 1, handleColor);
        gfx.fill(hx, centerY + 4, hx + 3, centerY + 6, handleColor);

        // Header + ? button
        gfx.text(font, Component.literal("Atlas Quick"), panelX + PADDING, panelY + 3, HEADER_COLOR);
        int helpBtnX = panelX + panelWidth - PADDING - font.width("?") - 4;
        int helpBtnY = panelY + 2;
        gfx.fill(helpBtnX - 1, helpBtnY - 1, helpBtnX + font.width("?") + 3, helpBtnY + 10, 0x66404060);
        gfx.text(font, Component.literal("?"), helpBtnX + 1, helpBtnY, 0xFFAAAAFF);

        // Search field
        int searchY = panelY + HEADER_HEIGHT;
        int searchX = panelX + PADDING;
        int searchW = panelWidth - PADDING * 2;
        int searchColor = searchFocused ? 0x663A5A8A : 0x55303030;
        gfx.fill(searchX, searchY, searchX + searchW, searchY + SEARCH_HEIGHT, searchColor);
        String shownQuery = searchText.isEmpty() ? "Search..." : searchText;
        int searchTextColor = searchText.isEmpty() ? 0xFF888888 : TEXT_COLOR;
        gfx.text(font, Component.literal(shownQuery), searchX + 3, searchY + 4, searchTextColor);

        // Filter chips row (scrollable)
        int filterY = searchY + SEARCH_HEIGHT + 1;
        int filterAreaLeft = panelX + PADDING;
        int filterAreaRight = panelX + panelWidth - PADDING;
        gfx.enableScissor(filterAreaLeft, filterY, filterAreaRight, filterY + FILTER_HEIGHT);
        int fx = filterAreaLeft - filterScrollOffset;
        int totalFilterWidth = 0;
        for (int i = 0; i < filterLabels.size(); i++) {
            String lbl = filterLabels.get(i);
            int tw = font.width(lbl) + 6;
            int bgColor = (i == activeFilter) ? 0x883A5A8A : 0x44303030;
            gfx.fill(fx, filterY, fx + tw, filterY + FILTER_HEIGHT, bgColor);
            gfx.text(font, Component.literal(lbl), fx + 3, filterY + 2, i == activeFilter ? HEADER_COLOR : 0xFF888888);
            fx += tw + 2;
            totalFilterWidth += tw + 2;
        }
        gfx.disableScissor();

        // Scroll indicators for filter row
        if (filterScrollOffset > 0) {
            gfx.text(font, Component.literal("\u25C0"), filterAreaLeft - 1, filterY + 1, 0xFF888888);
        }
        int maxFilterScroll = Math.max(0, totalFilterWidth - (filterAreaRight - filterAreaLeft));
        if (filterScrollOffset < maxFilterScroll) {
            gfx.text(font, Component.literal("\u25B6"), filterAreaRight - 6, filterY + 1, 0xFF888888);
        }

        int gridTop = filterY + FILTER_HEIGHT + 2;
        int columns = Math.max(1, (panelWidth - PADDING * 2) / ITEM_SIZE);

        // Compute dynamic recipe area height
        int recipeH = computeRecipePreviewHeight(font);
        int recipeAreaTop = panelBottom - PADDING;
        if (recipeH > 0) {
            recipeAreaTop = panelBottom - recipeH;
        }

        int gridBottom = recipeAreaTop - 2;
        int itemRows = Math.max(1, (gridBottom - gridTop) / ITEM_SIZE);
        int visibleItems = columns * itemRows;

        gfx.enableScissor(panelX + PADDING, gridTop, panelX + panelWidth - PADDING, gridTop + itemRows * ITEM_SIZE);

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

            // Pin indicator (small yellow dot, top-right corner)
            if (AtlasFabricClient.pinnedPlanManager().isPinned(entry)) {
                gfx.fill(ix + ITEM_SIZE - 5, iy, ix + ITEM_SIZE - 2, iy + 3, 0xFFFFCC44);
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

        // Feedback text (timed overlay)
        if (feedbackMessage != null && System.currentTimeMillis() < feedbackExpireTime) {
            gfx.text(font, Component.literal(feedbackMessage), panelX + PADDING, panelBottom - 10, 0xFF88FF88);
        } else {
            feedbackMessage = null;
        }

        // Recipe preview at the bottom
        if (selectedEntry != null && !selectedRecipes.isEmpty()) {
            drawRecipePreview(gfx, font, panelX + PADDING, recipeAreaTop, panelBottom - PADDING, mouseX, mouseY);

            // Save button at the recipe divider line
            boolean pinned = AtlasFabricClient.pinnedPlanManager().isPinned(selectedEntry);
            String pinLabel = pinned ? "Unsave" : "Save";
            int pinW = font.width(pinLabel) + 6;
            int pinX = panelX + panelWidth - PADDING - pinW;
            int pinY = recipeAreaTop;
            int pinBg = pinned ? 0x88885533 : 0x66404060;
            gfx.fill(pinX, pinY, pinX + pinW, pinY + 12, pinBg);
            gfx.text(font, Component.literal(pinLabel), pinX + 3, pinY + 2,
                    pinned ? 0xFFFFCC44 : 0xFFAAAAFF);

            // Paste button (only when in crafting screen with a crafting recipe)
            if (screen instanceof CraftingScreen && hasCraftingRecipe()) {
                String pasteLabel = "Paste";
                int pasteW = font.width(pasteLabel) + 6;
                int pasteX = pinX - pasteW - 2;
                gfx.fill(pasteX, pinY, pasteX + pasteW, pinY + 12, 0x66336633);
                gfx.text(font, Component.literal(pasteLabel), pasteX + 3, pinY + 2, 0xFF88FF88);
            }
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

    private static void drawRecipePreview(GuiGraphicsExtractor gfx, Font font, int x, int y, int bottomY, int mouseX, int mouseY) {
        recipeHitBoxes.clear();
        if (selectedRecipes.isEmpty()) {
            gfx.text(font, Component.literal("No recipes"), x, y, TEXT_COLOR);
            return;
        }

        // Divider line
        gfx.fill(x - 2, y, x + panelWidth - PADDING * 2, y + 1, BORDER_COLOR);
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
                                EntryKey entry = stackToEntry(inputStack);
                                if (entry != null) {
                                    recipeHitBoxes.add(new ItemHitBox(sx, sy, GRID_SLOT, GRID_SLOT, entry, inputStack));
                                }
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
                        EntryKey entry = stackToEntry(outputStack);
                        if (entry != null) {
                            recipeHitBoxes.add(new ItemHitBox(outX, outY, GRID_SLOT, GRID_SLOT, entry, outputStack));
                        }
                        outX += GRID_SLOT;
                    }
                }
                y += gridPixelSize + 3;
            } else {
                // ── Non-crafting: compact input → output row ──
                int ix = x;
                for (var input : inputs) {
                    if (ix + GRID_SLOT > x + panelWidth - PADDING * 2) break;
                    if (input.isEmpty()) continue;
                    ItemStack inputStack = ingredientToStack(input);
                    if (!inputStack.isEmpty()) {
                        gfx.item(inputStack, ix, y);
                        EntryKey entry = stackToEntry(inputStack);
                        if (entry != null) {
                            recipeHitBoxes.add(new ItemHitBox(ix, y, GRID_SLOT, GRID_SLOT, entry, inputStack));
                        }
                        ix += GRID_SLOT;
                    }
                }
                gfx.text(font, Component.literal("\u2192"), ix + 2, y + 4, TEXT_COLOR);
                ix += 12;
                for (var output : outputs) {
                    ItemStack outputStack = entryToStack(output);
                    if (!outputStack.isEmpty()) {
                        gfx.item(outputStack, ix, y);
                        EntryKey entry = stackToEntry(outputStack);
                        if (entry != null) {
                            recipeHitBoxes.add(new ItemHitBox(ix, y, GRID_SLOT, GRID_SLOT, entry, outputStack));
                        }
                        ix += GRID_SLOT;
                    }
                }
                y += GRID_SLOT + 3;
            }
            shown++;
        }

        // Tooltip for recipe items on hover
        for (var hitBox : recipeHitBoxes) {
            if (mouseX >= hitBox.x && mouseX < hitBox.x + hitBox.w
                    && mouseY >= hitBox.y && mouseY < hitBox.y + hitBox.h) {
                gfx.setTooltipForNextFrame(font, hitBox.stack, mouseX, mouseY);
                break;
            }
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
        return AtlasCategoryHelper.labelFor(categoryId);
    }

    public static void onAtlasDataUpdated() {
        rebuildFilters();
        refreshSearch();
    }

    // ── Creative Mode ─────────────────────────────────────────────────

    private static void giveItemIfCreative(EntryKey entry, MouseButtonEvent event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.getAbilities().instabuild || mc.gameMode == null) return;

        ItemStack stack = entryToStack(entry);
        if (stack.isEmpty()) return;

        boolean shifted = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        stack = stack.copy();
        stack.setCount(shifted ? stack.getMaxStackSize() : 1);

        // Find next free inventory slot instead of overwriting hotbar
        int freeSlot = mc.player.getInventory().getFreeSlot();
        if (freeSlot == -1) {
            showFeedback("\u00A7c Inventory full!");
            return;
        }
        // Set locally first so the item appears immediately in the player's inventory,
        // then sync to server via creative packet (server sync-back may be delayed/lost
        // when a different container is open)
        mc.player.getInventory().setItem(freeSlot, stack.copy());
        int containerSlot = freeSlot < 9 ? freeSlot + 36 : freeSlot;
        mc.gameMode.handleCreativeModeItemAdd(stack, containerSlot);
        showFeedback("\u00A7a\u2714 " + stack.getHoverName().getString() + " x" + stack.getCount());
        LOGGER.info("[Atlas] Creative give: {} x{} -> slot {}", entry.id(), stack.getCount(), freeSlot);
    }

    // ── Recipe Paste ────────────────────────────────────────────────────

    private static boolean hasCraftingRecipe() {
        if (selectedRecipes.isEmpty()) return false;
        return selectedRecipes.stream().anyMatch(r -> r.categoryId().equals("minecraft:crafting"));
    }

    private static void pasteRecipeIntoCraftingGrid(CraftingScreen craftingScreen) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;

        // Find the first crafting recipe
        RecipeNode recipe = selectedRecipes.stream()
                .filter(r -> r.categoryId().equals("minecraft:crafting"))
                .findFirst().orElse(null);
        if (recipe == null) return;

        CraftingMenu menu = craftingScreen.getMenu();
        List<Slot> gridSlots = menu.getInputGridSlots();
        int containerId = menu.containerId;
        int gw = recipe.gridWidth();
        int gh = recipe.gridHeight();

        int needed = 0;
        int filled = 0;
        for (int gridIdx = 0; gridIdx < 9; gridIdx++) {
            int row = gridIdx / 3;
            int col = gridIdx % 3;
            IngredientKey ingredient = getGridIngredient(recipe.inputs(), gw, gh, row, col);
            if (ingredient == null || ingredient.isEmpty()) continue;
            if (gridIdx >= gridSlots.size()) break;
            needed++;

            Slot gridSlot = gridSlots.get(gridIdx);
            if (!gridSlot.getItem().isEmpty()) { filled++; continue; } // slot already occupied

            // Find a matching item in the player's inventory portion of the container
            // Crafting grid slots are first (0 = result, 1-9 = grid), then player inventory
            int invStart = 10; // first player inventory slot in the container
            boolean found = false;
            for (int invIdx = invStart; invIdx < menu.slots.size(); invIdx++) {
                Slot sourceSlot = menu.getSlot(invIdx);
                if (sourceSlot.getItem().isEmpty()) continue;
                if (matchesIngredient(sourceSlot.getItem(), ingredient)) {
                    // Pick up full stack from inventory
                    mc.gameMode.handleContainerInput(containerId, invIdx, 0, ContainerInput.PICKUP, mc.player);
                    // Place one item in grid slot (right-click)
                    int gridContainerIdx = 1 + gridIdx; // grid slots start at container index 1
                    mc.gameMode.handleContainerInput(containerId, gridContainerIdx, 1, ContainerInput.PICKUP, mc.player);
                    // Put remaining stack back
                    mc.gameMode.handleContainerInput(containerId, invIdx, 0, ContainerInput.PICKUP, mc.player);
                    found = true;
                    break;
                }
            }
            if (found) filled++;
        }

        if (filled < needed) {
            int missing = needed - filled;
            showFeedback("\u00A7e Missing " + missing + " material" + (missing > 1 ? "s" : "") + "!");
        }
        LOGGER.info("[Atlas] Pasted recipe: {} ({}/{})", recipe.id(), filled, needed);
    }

    private static boolean matchesIngredient(ItemStack stack, IngredientKey ingredient) {
        if (ingredient.tagBased()) {
            try {
                Identifier loc = Identifier.parse(ingredient.id());
                TagKey<Item> tagKey = TagKey.create(BuiltInRegistries.ITEM.key(), loc);
                return stack.typeHolder().is(tagKey);
            } catch (Exception e) {
                return false;
            }
        }
        // Item-based match
        try {
            Identifier loc = Identifier.parse(ingredient.id());
            return stack.typeHolder().is(loc);
        } catch (Exception e) {
            return false;
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
        return screen.width - panelWidth - PADDING;
    }

    private static void drawHelpOverlay(GuiGraphicsExtractor gfx, Font font, int panelX, int panelY, int panelBottom) {
        // Semi-transparent background over the whole panel
        gfx.fill(panelX + 2, panelY + 2, panelX + panelWidth - 2, panelBottom - 2, 0xEE101018);

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
        gfx.text(font, Component.literal("to filter by mod/saved."), x, y, TEXT_COLOR); y += lineH + 2;
        gfx.text(font, Component.literal("Scroll to browse items."), x, y, TEXT_COLOR); y += lineH;
        gfx.text(font, Component.literal("Drag left edge to resize."), x, y, TEXT_COLOR); y += lineH;
        gfx.text(font, Component.literal("Press O to toggle panel."), x, y, TEXT_COLOR); y += lineH;
        gfx.text(font, Component.literal("Press Esc to close help."), x, y, TEXT_COLOR); y += lineH + 4;
        gfx.text(font, Component.literal("github.com/shaedy180/Atlas"), x, y, 0xFF666688);
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
        int w = panelWidth - PADDING * 2;
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + SEARCH_HEIGHT;
    }


}
