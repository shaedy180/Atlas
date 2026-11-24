package dev.atlasmod.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.atlasmod.client.compat.McCompat;
import dev.atlasmod.client.gui.RecipeView;
import dev.atlasmod.client.gui.Ui;
import dev.atlasmod.core.availability.AvailabilityEngine.AvailabilityResult;
import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.entry.IngredientKey;
import dev.atlasmod.core.recipe.RecipeNode;
import dev.atlasmod.search.SearchIndex;
import dev.atlasmod.search.SearchQuery;
import dev.atlasmod.ui.DeepModeTab;
import dev.atlasmod.ui.PinnedPlanManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Quick Mode: lightweight overlay panel drawn alongside inventory screens.
 * Shows search results and recipe quick-view without leaving the inventory.
 * Toggled via keybind (default: O).
 * <p>
 * The loader forwards the screen events of container screens to the static
 * handle methods here.
 */
public final class QuickModeOverlay {

    private static final Logger LOGGER = LoggerFactory.getLogger("Atlas/QuickMode");

    private static boolean enabled = true;
    private static String searchText = "";
    private static boolean searchFocused = false;
    private static List<EntryKey> results = List.of();
    private static int scrollOffset = 0;

    // Currently selected item in the overlay
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
    private static final int FILTER_HEIGHT = 12;
    private static final int PREVIEW_RECIPES = 3;

    // Resize drag state
    private static boolean draggingResize = false;

    // Filter chips (dynamic: rebuilt when recipes load)
    private static List<String> filterOptions = new ArrayList<>(List.of(""));
    private static List<String> filterLabels = new ArrayList<>(List.of("All"));
    private static int activeFilter = 0;
    private static int filterScrollOffset = 0;
    private static boolean filtersInitialized = false;

    // Feedback text timer
    private static String feedbackMessage = null;
    private static long feedbackExpireTime = 0;

    private static final Ui ui = new Ui();
    private static boolean showHelp = false;

    // Colors
    private static final int BG_COLOR = 0xCC101018;
    private static final int BORDER_COLOR = 0xFF404060;
    private static final int HIGHLIGHT_COLOR = 0x40FFFFFF;

    private QuickModeOverlay() {}

    public static boolean isEnabled() {
        return enabled;
    }

    /** True while the search field takes keyboard input. */
    public static boolean isTyping() {
        return enabled && searchFocused;
    }

    // -- Panel width persistence ----------------------------------------------

    public static void savePrefs(Path file) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, "panelWidth=" + panelWidth + "\nenabled=" + enabled + "\n");
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
                } else if (line.startsWith("enabled=")) {
                    enabled = Boolean.parseBoolean(line.substring("enabled=".length()).trim());
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

    /** Called after recipes were reloaded. */
    public static void onReload() {
        filtersInitialized = false;
        if (selectedEntry != null) selectedRecipes = AtlasClient.recipeGraph().recipesFor(selectedEntry);
    }

    /**
     * Rebuilds the dynamic filter list from detected mod namespaces.
     */
    public static void rebuildFilters() {
        filterOptions = new ArrayList<>(List.of("", "@minecraft", "Saved", "Plans"));
        filterLabels = new ArrayList<>(List.of("All", "Vanilla", "Saved", "Plans"));
        TreeSet<String> mods = new TreeSet<>();
        for (var node : AtlasClient.recipeGraph().allNodes()) {
            String id = node.id();
            int colon = id.indexOf(':');
            if (colon > 0 && !"minecraft".equals(id.substring(0, colon))) mods.add(id.substring(0, colon));
        }
        for (String mod : mods) {
            filterOptions.add("@" + mod);
            filterLabels.add(ItemStacks.pretty(mod));
        }
        if (activeFilter >= filterLabels.size()) activeFilter = 0;
    }

    private static void showFeedback(String message) {
        feedbackMessage = message;
        feedbackExpireTime = System.currentTimeMillis() + 2000;
    }

    private static void select(EntryKey entry) {
        selectedEntry = entry;
        selectedRecipes = AtlasClient.recipeGraph().recipesFor(entry);
    }

    private static void refreshSearch() {
        SearchIndex index = AtlasClient.searchIndex();
        if (index == null) {
            results = List.of();
            return;
        }
        List<EntryKey> all = index.search(SearchQuery.parse(searchText));
        String filter = filterOptions.get(Math.min(activeFilter, filterOptions.size() - 1));
        if ("Saved".equals(filter)) {
            all = onlyPinned(all, AtlasClient.savedItems());
        } else if ("Plans".equals(filter)) {
            all = onlyPinned(all, AtlasClient.buildPlans());
        }
        results = all;
        scrollOffset = 0;
    }

    private static List<EntryKey> onlyPinned(List<EntryKey> list, PinnedPlanManager manager) {
        Set<EntryKey> pinned = new HashSet<>();
        manager.plans().forEach(p -> pinned.add(p.target()));
        return list.stream().filter(pinned::contains).toList();
    }

    public static void setSearchText(String text) {
        String normalized = text == null ? "" : text;
        if (normalized.length() > MAX_SEARCH_CHARS) normalized = normalized.substring(0, MAX_SEARCH_CHARS);
        searchText = normalized;
        refreshSearch();
    }

    // -- Event handlers, called by the loader ---------------------------------

    /** Draws the panel and the plan tracker on top of a container screen. */
    public static void render(Screen screen, GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        if (!enabled || !(screen instanceof AbstractContainerScreen<?>)) return;
        if (draggingResize) {
            panelWidth = Math.max(MIN_PANEL_WIDTH, Math.min(MAX_PANEL_WIDTH, screen.width - mouseX));
        }
        ui.begin(QuickModeOverlay::select);
        drawOverlay(gfx, screen, mouseX, mouseY);
        PlanTracker.draw(ui, gfx, screen, mouseX, mouseY, panelX(screen));
        ui.tooltips(gfx, screen.getFont(), mouseX, mouseY);
    }

    /** Ends a resize drag. Always lets the event through. */
    public static void handleRelease(MouseButtonEvent event) {
        if (event.button() == 0) draggingResize = false;
    }

    /**
     * Handles mouse scroll within the overlay panel area.
     * Returns true if the scroll was consumed.
     */
    public static boolean handleScroll(Screen screen, double mouseX, double mouseY, double scrollY) {
        if (!enabled || !(screen instanceof AbstractContainerScreen<?>)) return false;
        int panelX = panelX(screen);
        if (mouseX < panelX || mouseX > screen.width) return false;

        int filterY = PADDING + HEADER_HEIGHT + SEARCH_HEIGHT + 1;
        if (mouseY >= filterY && mouseY < filterY + FILTER_HEIGHT) {
            Font font = screen.getFont();
            int total = 0;
            for (String label : filterLabels) total += font.width(label) + 8;
            int maxScroll = Math.max(0, total - (panelWidth - PADDING * 2));
            filterScrollOffset = Math.max(0, Math.min(maxScroll, filterScrollOffset + (int) (scrollY * -8)));
            return true;
        }
        int columns = Math.max(1, (panelWidth - PADDING * 2) / ITEM_SIZE);
        int maxRows = (results.size() + columns - 1) / columns;
        scrollOffset = Math.max(0, Math.min(Math.max(0, maxRows - 1), scrollOffset - (int) scrollY));
        return true;
    }

    /**
     * Handles mouse click within the overlay panel and the plan tracker.
     * Returns true if the click was consumed.
     */
    public static boolean handleClick(Screen screen, MouseButtonEvent event) {
        if (!enabled || !(screen instanceof AbstractContainerScreen<?>) || event.button() != 0) return false;

        double mouseX = event.x();
        double mouseY = event.y();
        if (showHelp) {
            showHelp = false;
            return isPointInPanel(screen, mouseX, mouseY);
        }

        int panelLeftEdge = panelX(screen);
        if (!isPointInPanel(screen, mouseX, mouseY)) {
            // Left edge of the panel starts a resize drag
            if (mouseX >= panelLeftEdge - 4 && mouseX <= panelLeftEdge + 4
                    && mouseY >= PADDING && mouseY <= screen.height - PADDING) {
                draggingResize = true;
                return true;
            }
            searchFocused = false;
            // The plan tracker sits outside the panel
            return ui.click(mouseX, mouseY);
        }

        // Any click inside the panel is consumed to prevent inventory interaction.
        if (isPointInSearchBox(screen, mouseX, mouseY)) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;
        if (ui.click(mouseX, mouseY)) return true;

        int gridTop = PADDING + HEADER_HEIGHT + SEARCH_HEIGHT + 1 + FILTER_HEIGHT + 2;
        int columns = Math.max(1, (panelWidth - PADDING * 2) / ITEM_SIZE);
        int gridBottom = gridBottom(screen);
        if (mouseY >= gridTop && mouseY < gridBottom) {
            int col = ((int) mouseX - panelLeftEdge - PADDING) / ITEM_SIZE;
            int row = ((int) mouseY - gridTop) / ITEM_SIZE;
            int idx = (row + scrollOffset) * columns + col;
            if (col >= 0 && col < columns && idx >= 0 && idx < results.size()) {
                select(results.get(idx));
                giveItemIfCreative(selectedEntry, event);
                LOGGER.debug("[Atlas] Quick select: {} -> {} recipes", selectedEntry.id(), selectedRecipes.size());
            }
        }
        return true;
    }

    private static void applyFilter() {
        String filterValue = filterOptions.get(activeFilter);
        String clean = searchText.replaceAll("@\\S+\\s*", "").trim();
        if (activeFilter == 0 || "Saved".equals(filterValue) || "Plans".equals(filterValue)) {
            setSearchText(clean);
        } else {
            setSearchText(filterValue + " " + clean);
        }
    }

    /**
     * Handles control keys while quick mode is active. While the search field is
     * focused every key is consumed so inventory shortcuts do not fire.
     * Returns true if Atlas consumed the key.
     */
    public static boolean handleKeyPress(Screen screen, KeyEvent event) {
        if (!enabled || !(screen instanceof AbstractContainerScreen<?>)) return false;
        int key = event.key();

        if (!searchFocused) {
            if (showHelp && key == InputConstants.KEY_ESCAPE) {
                showHelp = false;
                return true;
            }
            if (AtlasKeyBindings.focusSearch != null && AtlasKeyBindings.focusSearch.matches(event)) {
                searchFocused = true;
                return true;
            }
            return false;
        }

        if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            searchFocused = false;
        } else if (key == InputConstants.KEY_BACKSPACE && !searchText.isEmpty()) {
            setSearchText(searchText.substring(0, searchText.length() - 1));
        }
        return true;
    }

    /**
     * Handles typed text. A "/" opens the search field, anything else is
     * appended while it is focused. Returns true if Atlas consumed the text.
     */
    public static boolean handleChar(Screen screen, String text) {
        if (!enabled || !(screen instanceof AbstractContainerScreen<?>) || text == null || text.isEmpty()) return false;
        if (!searchFocused) {
            if ("/".equals(text)) {
                searchFocused = true;
                return true;
            }
            return false;
        }
        setSearchText(searchText + text);
        return true;
    }

    // -- Rendering -------------------------------------------------------------

    private static void drawOverlay(GuiGraphicsExtractor gfx, Screen screen, int mouseX, int mouseY) {
        if (!filtersInitialized) {
            rebuildFilters();
            refreshSearch();
            filtersInitialized = true;
        }
        Font font = screen.getFont();
        int panelX = panelX(screen);
        int panelY = PADDING;
        int panelBottom = screen.height - PADDING;

        gfx.fill(panelX, panelY, panelX + panelWidth, panelBottom, BG_COLOR);
        gfx.outline(panelX, panelY, panelWidth, panelBottom - panelY, BORDER_COLOR);

        // Resize handle indicator (3 small dots on left edge)
        int handleColor = draggingResize ? 0xFFAAAAFF : 0x88888888;
        int hx = panelX - 1;
        int centerY = panelY + (panelBottom - panelY) / 2;
        gfx.fill(hx, centerY - 6, hx + 3, centerY - 4, handleColor);
        gfx.fill(hx, centerY - 1, hx + 3, centerY + 1, handleColor);
        gfx.fill(hx, centerY + 4, hx + 3, centerY + 6, handleColor);

        // Header with Deep Mode and help buttons
        gfx.text(font, "Atlas Quick", panelX + PADDING, panelY + 4, Ui.HEADER);
        int bx = panelX + panelWidth - PADDING - font.width("?") - 8;
        ui.button(gfx, font, "?", bx, panelY + 2, false, mouseX, mouseY, () -> showHelp = !showHelp);
        int deepW = font.width("Deep") + 8;
        if (bx - deepW - 2 > panelX + PADDING + font.width("Atlas Quick") + 2) {
            ui.button(gfx, font, "Deep", bx - deepW - 2, panelY + 2, false, mouseX, mouseY,
                    () -> openDeepMode(selectedEntry, DeepModeTab.CRAFT));
        }

        // Search field
        int searchY = panelY + HEADER_HEIGHT;
        int searchX = panelX + PADDING;
        int searchW = panelWidth - PADDING * 2;
        gfx.fill(searchX, searchY, searchX + searchW, searchY + SEARCH_HEIGHT, searchFocused ? 0x663A5A8A : 0x55303030);
        String shownQuery = searchText.isEmpty() ? (searchFocused ? "" : "Search... ( / )") : searchText;
        boolean blink = searchFocused && (System.currentTimeMillis() / 500) % 2 == 0;
        String visible = Ui.fit(font, shownQuery + (blink ? "_" : ""), searchW - 6);
        gfx.text(font, visible, searchX + 3, searchY + 4, searchText.isEmpty() && !searchFocused ? Ui.MUTED : Ui.TEXT);

        drawFilters(gfx, font, panelX, searchY + SEARCH_HEIGHT + 1, mouseX, mouseY);

        int gridTop = searchY + SEARCH_HEIGHT + 1 + FILTER_HEIGHT + 2;
        int columns = Math.max(1, (panelWidth - PADDING * 2) / ITEM_SIZE);
        int recipeH = previewHeight();
        int recipeAreaTop = recipeH > 0 ? panelBottom - recipeH : panelBottom - PADDING;
        int itemRows = Math.max(1, (recipeAreaTop - 2 - gridTop) / ITEM_SIZE);
        int visibleItems = columns * itemRows;

        gfx.enableScissor(panelX + PADDING, gridTop, panelX + panelWidth - PADDING, gridTop + itemRows * ITEM_SIZE);
        PinnedPlanManager saved = AtlasClient.savedItems();
        PinnedPlanManager plans = AtlasClient.buildPlans();
        for (int i = 0; i < visibleItems && (i + scrollOffset * columns) < results.size(); i++) {
            EntryKey entry = results.get(i + scrollOffset * columns);
            int ix = panelX + PADDING + (i % columns) * ITEM_SIZE;
            int iy = gridTop + (i / columns) * ITEM_SIZE;

            if (entry.equals(selectedEntry)) gfx.fill(ix - 1, iy - 1, ix + ITEM_SIZE - 2, iy + ITEM_SIZE - 2, HIGHLIGHT_COLOR);
            if (saved.isPinned(entry)) gfx.fill(ix + ITEM_SIZE - 5, iy, ix + ITEM_SIZE - 2, iy + 3, 0xFFFFCC44);
            if (plans.isPinned(entry)) gfx.fill(ix, iy, ix + 3, iy + 3, 0xFF44CCFF);

            ItemStack stack = ItemStacks.of(entry);
            if (stack.isEmpty()) continue;
            gfx.item(stack, ix, iy);
            if (!showHelp && mouseX >= ix && mouseX < ix + ITEM_SIZE && mouseY >= iy && mouseY < iy + ITEM_SIZE
                    && mouseY < gridTop + itemRows * ITEM_SIZE) {
                gfx.setTooltipForNextFrame(font, stack, mouseX, mouseY);
            }
        }
        gfx.disableScissor();

        if (feedbackMessage != null && System.currentTimeMillis() < feedbackExpireTime) {
            gfx.text(font, feedbackMessage, panelX + PADDING, recipeAreaTop - 11, 0xFF88FF88);
        } else {
            feedbackMessage = null;
        }

        if (selectedEntry != null && !selectedRecipes.isEmpty()) {
            drawRecipePreview(gfx, screen, font, panelX + PADDING, recipeAreaTop, panelBottom - PADDING, mouseX, mouseY);
        }

        if (showHelp) drawHelpOverlay(gfx, font, panelX, panelY, panelBottom);
    }

    private static void drawFilters(GuiGraphicsExtractor gfx, Font font, int panelX, int filterY, int mouseX, int mouseY) {
        int left = panelX + PADDING;
        int right = panelX + panelWidth - PADDING;
        gfx.enableScissor(left, filterY, right, filterY + FILTER_HEIGHT);
        int fx = left - filterScrollOffset;
        int total = 0;
        for (int i = 0; i < filterLabels.size(); i++) {
            String label = filterLabels.get(i);
            int tw = font.width(label) + 6;
            gfx.fill(fx, filterY, fx + tw, filterY + FILTER_HEIGHT, i == activeFilter ? Ui.CHIP_ACTIVE : 0x44303030);
            gfx.text(font, label, fx + 3, filterY + 2, i == activeFilter ? Ui.HEADER : Ui.MUTED);
            int visLeft = Math.max(left, fx);
            int visRight = Math.min(right, fx + tw);
            if (visRight > visLeft) {
                final int index = i;
                ui.area(visLeft, filterY, visRight - visLeft, FILTER_HEIGHT, () -> {
                    activeFilter = index;
                    applyFilter();
                });
            }
            fx += tw + 2;
            total += tw + 2;
        }
        gfx.disableScissor();
        if (filterScrollOffset > 0) gfx.text(font, "◀", left - 1, filterY + 1, Ui.MUTED);
        if (filterScrollOffset < Math.max(0, total - (right - left))) gfx.text(font, "▶", right - 6, filterY + 1, Ui.MUTED);
    }

    private static int gridBottom(Screen screen) {
        int panelBottom = screen.height - PADDING;
        int recipeH = previewHeight();
        return recipeH > 0 ? panelBottom - recipeH - 2 : panelBottom;
    }

    private static int previewHeight() {
        if (selectedEntry == null || selectedRecipes.isEmpty()) return 0;
        int h = 16; // divider and button row
        int shown = 0;
        for (RecipeNode recipe : selectedRecipes) {
            if (shown++ >= PREVIEW_RECIPES) break;
            h += 10 + RecipeView.height(recipe, GRID_SLOT) + 3;
        }
        return h;
    }

    private static void drawRecipePreview(GuiGraphicsExtractor gfx, Screen screen, Font font, int x, int y, int bottomY,
                                          int mouseX, int mouseY) {
        int innerW = panelWidth - PADDING * 2;
        gfx.fill(x - 2, y, x + innerW, y + 1, BORDER_COLOR);

        // Button row: Paste (crafting table only), Deep, Save
        PinnedPlanManager saved = AtlasClient.savedItems();
        boolean pinned = saved.isPinned(selectedEntry);
        String pinLabel = pinned ? "Unsave" : "Save";
        int bx = x + innerW - font.width(pinLabel) - 8;
        ui.button(gfx, font, pinLabel, bx, y + 2, pinned ? 0x88885533 : Ui.CHIP, pinned ? 0xFFFFCC44 : 0xFFAAAAFF,
                mouseX, mouseY, () -> {
                    if (saved.isPinned(selectedEntry)) saved.unpin(selectedEntry); else saved.pin(selectedEntry, 1);
                    AtlasClient.saveAll();
                });
        int treeW = font.width("Tree") + 8;
        bx -= treeW + 2;
        ui.button(gfx, font, "Tree", bx, y + 2, false, mouseX, mouseY, () -> openDeepMode(selectedEntry, DeepModeTab.TREE));
        if (screen instanceof CraftingScreen craftingScreen && hasCraftingRecipe()) {
            int pasteW = font.width("Paste") + 8;
            bx -= pasteW + 2;
            ui.button(gfx, font, "Paste", bx, y + 2, 0x66336633, 0xFF88FF88, mouseX, mouseY,
                    () -> pasteRecipeIntoCraftingGrid(craftingScreen));
        }
        y += 16;

        var context = AtlasClient.context().snapshot();
        var engine = AtlasClient.insights().availability();
        int shown = 0;
        for (RecipeNode recipe : selectedRecipes) {
            if (shown++ >= PREVIEW_RECIPES || y + GRID_SLOT > bottomY) break;

            // Status dot: green ready, yellow missing materials, red blocked
            AvailabilityResult status = engine.evaluateFully(recipe, context);
            int color = status.craftableNow() ? Ui.GOOD : status.available() ? Ui.WARN : Ui.BAD;
            gfx.fill(x, y + 2, x + 5, y + 7, color);
            List<Component> why = new ArrayList<>();
            if (status.craftableNow()) why.add(Component.literal("Ready to craft"));
            status.details().forEach(b -> why.add(Component.literal(b.message())));
            ui.tooltip(x, y, 8, 9, why);

            gfx.text(font, Ui.fit(font, RecipeView.categoryName(recipe.categoryId()), innerW - 10), x + 8, y, 0xFF8888FF);
            y += 10;
            y += RecipeView.draw(ui, gfx, font, recipe, x, y, GRID_SLOT, innerW) + 3;
        }
    }

    // -- Creative Mode ---------------------------------------------------------

    private static void giveItemIfCreative(EntryKey entry, MouseButtonEvent event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.getAbilities().instabuild || mc.gameMode == null) return;

        ItemStack stack = ItemStacks.of(entry);
        if (stack.isEmpty()) return;
        stack = stack.copy();
        stack.setCount(event.hasShiftDown() ? stack.getMaxStackSize() : 1);

        // Find next free inventory slot instead of overwriting hotbar
        int freeSlot = mc.player.getInventory().getFreeSlot();
        if (freeSlot == -1) {
            showFeedback("Inventory full!");
            return;
        }
        // Set locally first so the item appears immediately in the player's inventory,
        // then sync to server via creative packet (server sync-back may be delayed/lost
        // when a different container is open)
        mc.player.getInventory().setItem(freeSlot, stack.copy());
        int containerSlot = freeSlot < 9 ? freeSlot + 36 : freeSlot;
        mc.gameMode.handleCreativeModeItemAdd(stack, containerSlot);
        showFeedback("Got " + stack.getHoverName().getString() + " x" + stack.getCount());
        LOGGER.info("[Atlas] Creative give: {} x{} -> slot {}", entry.id(), stack.getCount(), freeSlot);
    }

    // -- Recipe Paste ----------------------------------------------------------

    private static boolean hasCraftingRecipe() {
        return selectedRecipes.stream().anyMatch(RecipeView::isGrid);
    }

    private static void pasteRecipeIntoCraftingGrid(CraftingScreen craftingScreen) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;

        RecipeNode recipe = selectedRecipes.stream().filter(RecipeView::isGrid).findFirst().orElse(null);
        if (recipe == null) return;

        CraftingMenu menu = craftingScreen.getMenu();
        List<Slot> gridSlots = menu.getInputGridSlots();
        int containerId = menu.containerId;
        int gw = recipe.gridWidth();
        int gh = recipe.gridHeight();

        int needed = 0;
        int filled = 0;
        for (int gridIdx = 0; gridIdx < 9; gridIdx++) {
            IngredientKey ingredient = RecipeView.gridIngredient(recipe.inputs(), gw, gh, gridIdx / 3, gridIdx % 3);
            if (ingredient.isEmpty()) continue;
            if (gridIdx >= gridSlots.size()) break;
            needed++;

            Slot gridSlot = gridSlots.get(gridIdx);
            if (!gridSlot.getItem().isEmpty()) {
                filled++;
                continue;
            }

            // Crafting grid slots are first (0 = result, 1-9 = grid), then player inventory
            int invStart = 10;
            for (int invIdx = invStart; invIdx < menu.slots.size(); invIdx++) {
                Slot sourceSlot = menu.getSlot(invIdx);
                if (sourceSlot.getItem().isEmpty() || !matchesIngredient(sourceSlot.getItem(), ingredient)) continue;
                // Pick up the stack, drop one into the grid slot (right click), put the rest back
                mc.gameMode.handleContainerInput(containerId, invIdx, 0, ContainerInput.PICKUP, mc.player);
                mc.gameMode.handleContainerInput(containerId, 1 + gridIdx, 1, ContainerInput.PICKUP, mc.player);
                mc.gameMode.handleContainerInput(containerId, invIdx, 0, ContainerInput.PICKUP, mc.player);
                filled++;
                break;
            }
        }

        if (filled < needed) {
            int missing = needed - filled;
            showFeedback("Missing " + missing + " material" + (missing > 1 ? "s" : "") + "!");
        }
        LOGGER.info("[Atlas] Pasted recipe: {} ({}/{})", recipe.id(), filled, needed);
    }

    private static boolean matchesIngredient(ItemStack stack, IngredientKey ingredient) {
        Identifier loc = Identifier.tryParse(ingredient.id());
        if (loc == null) return false;
        if (ingredient.tagBased()) {
            return stack.typeHolder().is(TagKey.create(BuiltInRegistries.ITEM.key(), loc));
        }
        return stack.typeHolder().is(loc);
    }

    // -- Utility ---------------------------------------------------------------

    static void openDeepMode(EntryKey entry, DeepModeTab tab) {
        McCompat.openScreen(new AtlasScreen(entry, tab));
    }

    static int panelX(Screen screen) {
        return enabled ? screen.width - panelWidth - PADDING : screen.width;
    }

    private static void drawHelpOverlay(GuiGraphicsExtractor gfx, Font font, int panelX, int panelY, int panelBottom) {
        gfx.fill(panelX + 2, panelY + 2, panelX + panelWidth - 2, panelBottom - 2, 0xEE101018);
        int x = panelX + PADDING + 2;
        int y = panelY + 6;
        int w = panelWidth - PADDING * 2 - 4;
        gfx.text(font, "Atlas Quick Mode", x, y, Ui.HEADER);
        y += 12;
        String[] lines = {
                "Click an item to see its recipes below. The dot shows if you can craft it now.",
                "Press / or click the search bar to type. Prefixes: @mod, $tag, #tooltip.",
                "Tree opens the crafting tree, Paste fills the crafting table.",
                "Pinned plans show on the left of your inventory.",
                "Drag the left edge to resize. O toggles the panel.",
        };
        for (String line : lines) {
            for (var part : font.split(FormattedText.of(line), w)) {
                gfx.text(font, part, x, y, Ui.TEXT);
                y += 10;
            }
            y += 3;
        }
        gfx.text(font, "github.com/shaedy180/Atlas", x, y + 2, 0xFF666688);
    }

    private static boolean isPointInPanel(Screen screen, double mouseX, double mouseY) {
        int panelX = panelX(screen);
        return mouseX >= panelX && mouseX <= screen.width - PADDING
                && mouseY >= PADDING && mouseY <= screen.height - PADDING;
    }

    private static boolean isPointInSearchBox(Screen screen, double mouseX, double mouseY) {
        int x = panelX(screen) + PADDING;
        int y = PADDING + HEADER_HEIGHT;
        int w = panelWidth - PADDING * 2;
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + SEARCH_HEIGHT;
    }
}
