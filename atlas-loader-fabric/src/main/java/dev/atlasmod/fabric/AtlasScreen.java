package dev.atlasmod.fabric;

import dev.atlasmod.api.AtlasApi;
import dev.atlasmod.core.availability.AvailabilityEngine;
import dev.atlasmod.core.availability.ContextSnapshot;
import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.entry.IngredientKey;
import dev.atlasmod.core.recipe.AcquisitionSource;
import dev.atlasmod.core.recipe.RecipeGraph;
import dev.atlasmod.core.recipe.RecipeNode;
import dev.atlasmod.core.visibility.VisibilityPolicy;
import dev.atlasmod.search.SearchIndex;
import dev.atlasmod.search.SearchQuery;
import dev.atlasmod.ui.DeepModeTab;
import dev.atlasmod.ui.PinnedPlanManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Deep Mode: fullscreen Atlas screen with search, item grid, and recipe panel.
 * Opened via keybinding (default: U).
 */
public class AtlasScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("Atlas/DeepMode");

    // Layout constants
    private static final int SEARCH_HEIGHT = 20;
    private static final int ITEM_SIZE = 18;
    private static final int GRID_PADDING = 4;
    private static final int PANEL_DIVIDER_X_RATIO = 45; // left panel uses 45% of width
    private static final int HEADER_HEIGHT = 30;
    private static final int TAB_HEIGHT = 20;

    // Colors
    private static final int BG_COLOR = 0xCC101010;
    private static final int PANEL_BG = 0xCC1A1A2E;
    private static final int DIVIDER_COLOR = 0xFF404060;
    private static final int HEADER_COLOR = 0xFFE0E0FF;
    private static final int TEXT_COLOR = 0xFFCCCCCC;
    private static final int HIGHLIGHT_COLOR = 0x40FFFFFF;

    private static final String ISSUES_URL = "https://github.com/shaedy180/Atlas/issues";

    // Filter chips (dynamic: rebuilt on init)
    private List<String> filterOptions = new ArrayList<>(List.of("", "@minecraft"));
    private List<String> filterLabels  = new ArrayList<>(List.of("All", "Vanilla"));
    private int activeFilter = 0;
    private int filterScrollOffset = 0;
    private int totalFilterWidth = 0;

    private boolean showHelp = false;

    private EditBox searchBox;
    private List<EntryKey> searchResults = List.of();
    private int scrollOffset = 0;
    private int gridColumns = 1;

    // Selected item and its recipes
    private EntryKey selectedEntry;
    private List<RecipeNode> selectedRecipes = List.of();
    private DeepModeTab activeTab = DeepModeTab.CRAFT;

    // Recipe item hit boxes for tooltip and click-through
    private record ItemHitBox(int x, int y, int w, int h, EntryKey entry, ItemStack stack) {}
    private final List<ItemHitBox> recipeHitBoxes = new ArrayList<>();

    // In-UI feedback (visible even when full-screen)
    private String feedbackText = null;
    private long feedbackExpireTime = 0;

    // Pending recipe for transfer to crafting grid after closing the screen
    private static RecipeNode pendingTransferRecipe = null;

    public AtlasScreen() {
        super(Component.translatable("screen.atlas.title"));
    }

    @Override
    protected void init() {
        super.init();
        LOGGER.info("[Atlas] Deep Mode init: {}x{}", width, height);

        // Rebuild dynamic filters
        rebuildFilters();

        int leftPanelWidth = panelDividerX();

        // Search box at top of left panel
        searchBox = new EditBox(font, 6, HEADER_HEIGHT + 2, leftPanelWidth - 12, SEARCH_HEIGHT,
                Component.translatable("screen.atlas.search"));
        searchBox.setMaxLength(128);
        searchBox.setHint(Component.literal("Search items..."));
        searchBox.setResponder(this::onSearchChanged);
        addRenderableWidget(searchBox);

        // Tab buttons across the top of the right panel
        int tabX = leftPanelWidth + 4;
        int tabY = HEADER_HEIGHT + 2;
        for (DeepModeTab tab : DeepModeTab.values()) {
            String label = tab.name().substring(0, 1) + tab.name().substring(1).toLowerCase();
            int tabWidth = font.width(label) + 12;
            final DeepModeTab thisTab = tab;
            Button tabBtn = Button.builder(Component.literal(label), btn -> {
                activeTab = thisTab;
            }).bounds(tabX, tabY, tabWidth, TAB_HEIGHT).build();
            addRenderableWidget(tabBtn);
            tabX += tabWidth + 2;
        }

        // Calculate total filter width for manual rendering (filters are drawn manually with scissor clipping)
        totalFilterWidth = 0;
        for (int i = 0; i < filterLabels.size(); i++) {
            int fw = font.width(filterLabels.get(i)) + 12;
            totalFilterWidth += fw + 2;
        }

        // ? Help button (top-right corner)
        Button helpBtn = Button.builder(Component.literal("?"), btn -> {
            showHelp = !showHelp;
        }).bounds(width - 22, 4, 18, 14).build();
        addRenderableWidget(helpBtn);

        // Bug report / Mod support button (bottom-right corner)
        String reportLabel = "Report Bug / Request Mod";
        int reportW = font.width(reportLabel) + 12;
        Button reportBtn = Button.builder(Component.literal(reportLabel), btn -> {
            Util.getPlatform().openUri(ISSUES_URL);
        }).bounds(width - reportW - 4, height - 20, reportW, 16).build();
        addRenderableWidget(reportBtn);

        // Calculate grid columns based on available width
        gridColumns = Math.max(1, (leftPanelWidth - 12) / ITEM_SIZE);

        // Initial search (show everything)
        onSearchChanged("");
    }

    private int panelDividerX() {
        return width * PANEL_DIVIDER_X_RATIO / 100;
    }

    private void rebuildFilters() {
        filterOptions = new ArrayList<>();
        filterLabels = new ArrayList<>();
        filterOptions.add("");
        filterLabels.add("All");
        filterOptions.add("@minecraft");
        filterLabels.add("Vanilla");
        filterOptions.add("Saved");
        filterLabels.add("Saved");
        var graph = AtlasFabricClient.recipeGraph();
        if (graph != null) {
            TreeSet<String> mods = new TreeSet<>();
            for (var node : graph.allNodes()) {
                String id = node.id();
                int colon = id.indexOf(':');
                if (colon > 0) {
                    String ns = id.substring(0, colon);
                    if (!"minecraft".equals(ns)) {
                        mods.add(ns);
                    }
                }
            }
            for (String mod : mods) {
                filterOptions.add("@" + mod);
                filterLabels.add(mod.substring(0, 1).toUpperCase() + mod.substring(1));
            }
        }
        if (activeFilter >= filterLabels.size()) {
            activeFilter = 0;
        }
    }

    private void showFeedback(String message) {
        feedbackText = message;
        feedbackExpireTime = System.currentTimeMillis() + 2000;
    }

    private static EntryKey stackToEntry(ItemStack stack) {
        if (stack.isEmpty()) return null;
        var key = stack.typeHolder().unwrapKey().orElse(null);
        if (key == null) return null;
        return new EntryKey("item", key.identifier().toString());
    }

    private void onSearchChanged(String text) {
        SearchIndex index = AtlasFabricClient.searchIndex();
        if (index == null) {
            searchResults = List.of();
            return;
        }
        // If "Saved" filter is active, filter to saved items only
        if (activeFilter >= 0 && activeFilter < filterOptions.size() && "Saved".equals(filterOptions.get(activeFilter))) {
            PinnedPlanManager pm = AtlasFabricClient.pinnedPlanManager();
            var savedTargets = new java.util.HashSet<EntryKey>();
            for (var plan : pm.plans()) {
                savedTargets.add(plan.target());
            }
            SearchQuery query = SearchQuery.parse(text);
            var allResults = index.search(query);
            searchResults = allResults.stream().filter(savedTargets::contains).toList();
        } else {
            SearchQuery query = SearchQuery.parse(text);
            List<EntryKey> raw = index.search(query);

            // Apply availability-based filters when requested
            if (query.onlyUnlocked() || !query.includeHidden()) {
                RecipeGraph graph = AtlasApi.get().recipeGraph();
                ContextSnapshot ctx = ContextSnapshotBuilder.capture();
                AvailabilityEngine engine = new AvailabilityEngine();

                raw = raw.stream().filter(entry -> {
                    List<RecipeNode> recipes = graph.recipesFor(entry);
                    if (recipes.isEmpty()) return true; // no recipes means no restrictions

                    for (RecipeNode recipe : recipes) {
                        var result = engine.evaluate(recipe, ctx);
                        // ~unlocked: only show items where at least one recipe is available
                        if (query.onlyUnlocked() && result.available()) return true;
                        // Default (no !hidden): skip items where all recipes are hidden
                        if (!query.includeHidden() && result.policy() == VisibilityPolicy.HIDDEN) continue;
                        if (!query.onlyUnlocked()) return true;
                    }
                    return false;
                }).toList();
            }

            searchResults = raw;
        }
        scrollOffset = 0;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        int dividerX = panelDividerX();

        // Left panel background
        gfx.fill(0, 0, dividerX, height, PANEL_BG);
        // Divider line
        gfx.fill(dividerX, 0, dividerX + 1, height, DIVIDER_COLOR);
        // Right panel background
        gfx.fill(dividerX + 1, 0, width, height, PANEL_BG);

        // Header
        gfx.centeredText(font, Component.literal("Atlas"), width / 2, 8, HEADER_COLOR);

        // Draw item grid in left panel
        drawItemGrid(gfx, mouseX, mouseY);

        // Draw recipe panel on the right
        drawRecipePanel(gfx, dividerX + 6, mouseX, mouseY);

        // Feedback toast (visible in-UI)
        if (feedbackText != null && System.currentTimeMillis() < feedbackExpireTime) {
            int fbW = font.width(feedbackText) + 12;
            int fbX = (width - fbW) / 2;
            int fbY = height - 24;
            gfx.fill(fbX, fbY, fbX + fbW, fbY + 14, 0xCC222222);
            gfx.text(font, Component.literal(feedbackText), fbX + 6, fbY + 3, 0xFF88FF88);
        } else {
            feedbackText = null;
        }

        // Help overlay (drawn on top of everything except widgets)
        if (showHelp) {
            drawHelpOverlay(gfx);
        }

        // Let widgets (search box, buttons) render themselves
        super.extractRenderState(gfx, mouseX, mouseY, partialTick);

        // Filter chips (rendered manually with scissor clipping)
        int filterY = HEADER_HEIGHT + SEARCH_HEIGHT + 6;
        int filterAreaLeft = 6;
        int filterAreaRight = dividerX - 6;
        gfx.enableScissor(filterAreaLeft, filterY, filterAreaRight, filterY + 14);
        int fx = filterAreaLeft - filterScrollOffset;
        for (int i = 0; i < filterLabels.size(); i++) {
            String lbl = filterLabels.get(i);
            int fw = font.width(lbl) + 12;
            int bgColor = (i == activeFilter) ? 0x883A5A8A : 0x44303030;
            gfx.fill(fx, filterY, fx + fw, filterY + 14, bgColor);
            gfx.centeredText(font, Component.literal(lbl), fx + fw / 2, filterY + 3, i == activeFilter ? HEADER_COLOR : 0xFFCCCCCC);
            fx += fw + 2;
        }
        gfx.disableScissor();

        // Scroll indicators for filter row
        if (filterScrollOffset > 0) {
            gfx.text(font, Component.literal("\u25C0"), filterAreaLeft - 1, filterY + 3, 0xFF888888);
        }
        int maxFilterScroll = Math.max(0, totalFilterWidth - (filterAreaRight - filterAreaLeft));
        if (filterScrollOffset < maxFilterScroll) {
            gfx.text(font, Component.literal("\u25B6"), filterAreaRight - 5, filterY + 3, 0xFF888888);
        }
    }

    private void drawItemGrid(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        int startY = HEADER_HEIGHT + SEARCH_HEIGHT + 24; // below search + filter row
        int startX = 6;
        int maxRows = (height - startY - 4) / ITEM_SIZE;
        int visibleItems = gridColumns * maxRows;

        // Scissor to the left panel
        gfx.enableScissor(0, startY, panelDividerX(), height);

        for (int i = 0; i < visibleItems && (i + scrollOffset * gridColumns) < searchResults.size(); i++) {
            int idx = i + scrollOffset * gridColumns;
            EntryKey entry = searchResults.get(idx);

            int col = i % gridColumns;
            int row = i / gridColumns;
            int x = startX + col * ITEM_SIZE;
            int y = startY + row * ITEM_SIZE;

            // Highlight selected
            if (entry.equals(selectedEntry)) {
                gfx.fill(x - 1, y - 1, x + ITEM_SIZE - 1, y + ITEM_SIZE - 1, HIGHLIGHT_COLOR);
            }

            // Pin indicator (small yellow dot, top-right corner)
            if (AtlasFabricClient.pinnedPlanManager().isPinned(entry)) {
                gfx.fill(x + ITEM_SIZE - 5, y, x + ITEM_SIZE - 2, y + 3, 0xFFFFCC44);
            }

            // Render item icon
            ItemStack stack = entryToStack(entry);
            if (!stack.isEmpty()) {
                gfx.item(stack, x, y);
            }

            // Tooltip on hover
            if (mouseX >= x && mouseX < x + ITEM_SIZE && mouseY >= y && mouseY < y + ITEM_SIZE) {
                if (!stack.isEmpty()) {
                    gfx.setTooltipForNextFrame(font, stack, mouseX, mouseY);
                }
            }
        }

        gfx.disableScissor();
    }

    private void drawRecipePanel(GuiGraphicsExtractor gfx, int startX, int mouseX, int mouseY) {
        recipeHitBoxes.clear();
        int startY = HEADER_HEIGHT + TAB_HEIGHT + 8;

        if (selectedEntry == null) {
            gfx.text(font, Component.literal("Select an item to view recipes"), startX, startY, TEXT_COLOR);
            return;
        }

        // Show selected item name + pin button
        ItemStack stack = entryToStack(selectedEntry);
        if (!stack.isEmpty()) {
            gfx.item(stack, startX, startY);
            gfx.text(font, stack.getHoverName(), startX + 20, startY + 4, HEADER_COLOR);
        }

        // Save button next to item name
        PinnedPlanManager pm = AtlasFabricClient.pinnedPlanManager();
        boolean pinned = pm.isPinned(selectedEntry);
        String pinLabel = pinned ? "Unsave" : "Save";
        int pinW = font.width(pinLabel) + 8;
        int pinX = width - pinW - 8;
        int pinY = startY + 2;
        int pinBg = pinned ? 0x88885533 : 0x66404060;
        gfx.fill(pinX, pinY, pinX + pinW, pinY + 14, pinBg);
        gfx.text(font, Component.literal(pinLabel), pinX + 4, pinY + 3,
                pinned ? 0xFFFFCC44 : 0xFFAAAAFF);

        // "Send to Grid" button for crafting recipes (left of save)
        boolean hasCrafting = selectedRecipes.stream()
                .anyMatch(r -> r.categoryId().equals("minecraft:crafting"));
        if (hasCrafting) {
            String sendLabel = "Send to Grid";
            int sendW = font.width(sendLabel) + 8;
            int sendX = pinX - sendW - 4;
            int sendBg = pendingTransferRecipe != null ? 0x88336655 : 0x66404060;
            gfx.fill(sendX, pinY, sendX + sendW, pinY + 14, sendBg);
            gfx.text(font, Component.literal(sendLabel), sendX + 4, pinY + 3, 0xFF88FFAA);
        }

        startY += 24;

        // Tab-specific content
        switch (activeTab) {
            case CRAFT -> drawCraftTab(gfx, startX, startY, mouseX, mouseY);
            case USE -> drawUseTab(gfx, startX, startY, mouseX, mouseY);
            case SOURCES -> drawSourcesTab(gfx, startX, startY);
            case ALTERNATIVES -> drawAlternativesTab(gfx, startX, startY, mouseX, mouseY);
            case UNLOCKS -> drawUnlocksTab(gfx, startX, startY);
            case NOTES -> drawNotesTab(gfx, startX, startY);
        }
    }

    private static final int GRID_SLOT = 18;

    private void drawCraftTab(GuiGraphicsExtractor gfx, int x, int y, int mouseX, int mouseY) {
        if (selectedRecipes.isEmpty()) {
            gfx.text(font, Component.literal("No recipes found"), x, y, TEXT_COLOR);
            return;
        }

        ContextSnapshot context = ContextSnapshotBuilder.capture();
        AvailabilityEngine engine = new AvailabilityEngine();

        for (RecipeNode recipe : selectedRecipes) {
            if (y > height - 20) break;

            String label = categoryLabel(recipe.categoryId());
            var inputs = recipe.inputs();
            var outputs = recipe.outputs();
            boolean isCrafting = recipe.categoryId().equals("minecraft:crafting");

            // Availability badge
            AvailabilityEngine.AvailabilityResult availability = engine.evaluate(recipe, context);
            int labelColor = availability.available() ? 0xFF8888FF : 0xFFAA8844;
            String badge = availability.available() ? "" : " \u26A0";

            // Category label with availability indicator
            gfx.text(font, Component.literal(label + badge), x, y, labelColor);
            y += 12;

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
                int arrowX = x + gridPixelSize + 4;
                int centerY = y + gridPixelSize / 2 - 4;
                gfx.text(font, Component.literal("\u2192"), arrowX, centerY, TEXT_COLOR);

                int outX = arrowX + 14;
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
                y += gridPixelSize + 4;
            } else {
                // ── Non-crafting: compact input → output row ──
                int inputX = x;
                for (var input : inputs) {
                    if (input.isEmpty()) continue;
                    ItemStack inputStack = ingredientToStack(input);
                    if (!inputStack.isEmpty()) {
                        gfx.item(inputStack, inputX, y);
                        EntryKey entry = stackToEntry(inputStack);
                        if (entry != null) {
                            recipeHitBoxes.add(new ItemHitBox(inputX, y, GRID_SLOT, GRID_SLOT, entry, inputStack));
                        }
                        inputX += GRID_SLOT;
                    }
                }
                gfx.text(font, Component.literal("\u2192"), inputX + 4, y + 4, TEXT_COLOR);
                inputX += 14;
                for (var output : outputs) {
                    ItemStack outputStack = entryToStack(output);
                    if (!outputStack.isEmpty()) {
                        gfx.item(outputStack, inputX, y);
                        EntryKey entry = stackToEntry(outputStack);
                        if (entry != null) {
                            recipeHitBoxes.add(new ItemHitBox(inputX, y, GRID_SLOT, GRID_SLOT, entry, outputStack));
                        }
                        inputX += GRID_SLOT;
                    }
                }
                y += GRID_SLOT + 4;
            }

            // Processing time if present
            if (recipe.processingTime() > 0) {
                gfx.text(font, Component.literal("  Time: " + recipe.processingTime() + " ticks"),
                        x, y, 0xFF888888);
                y += 10;
            }

            // Availability blockers shown inline (compact)
            if (!availability.blockers().isEmpty()) {
                for (String blocker : availability.blockers()) {
                    if (y > height - 20) break;
                    gfx.text(font, Component.literal("  \u26A0 " + blocker), x, y, 0xFFCC8844);
                    y += 10;
                }
            }

            y += 4;
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
            if (col < gw && row < gh) {
                int idx = row * gw + col;
                return idx < inputs.size() ? inputs.get(idx) : IngredientKey.EMPTY;
            }
            return IngredientKey.EMPTY;
        }
        int idx = row * 3 + col;
        return idx < inputs.size() ? inputs.get(idx) : IngredientKey.EMPTY;
    }

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
                String raw = categoryId.contains(":") ? categoryId.substring(categoryId.indexOf(':') + 1) : categoryId;
                yield raw.substring(0, 1).toUpperCase() + raw.substring(1).replace('_', ' ');
            }
        };
    }

    private void drawUseTab(GuiGraphicsExtractor gfx, int x, int y, int mouseX, int mouseY) {
        if (selectedEntry == null) return;

        // Find recipes that use this item as an input
        RecipeGraph graph = AtlasApi.get().recipeGraph();
        List<RecipeNode> uses = new ArrayList<>();
        for (RecipeNode node : graph.allNodes()) {
            for (var input : node.inputs()) {
                if (input.id().equals(selectedEntry.id())) {
                    uses.add(node);
                    break;
                }
            }
        }

        if (uses.isEmpty()) {
            gfx.text(font, Component.literal("No uses found"), x, y, TEXT_COLOR);
            return;
        }

        gfx.text(font, Component.literal("Used in " + uses.size() + " recipes:"), x, y, TEXT_COLOR);
        y += 14;

        for (RecipeNode recipe : uses) {
            if (y > height - 20) break; // stop if we run out of space

            gfx.text(font, Component.literal("[" + recipe.categoryId() + "] " + recipe.id()),
                    x, y, 0xFF8888FF);
            y += 12;

            // Show outputs
            int outX = x + 10;
            for (var output : recipe.outputs()) {
                ItemStack outStack = entryToStack(output);
                if (!outStack.isEmpty()) {
                    gfx.item(outStack, outX, y);
                    EntryKey entry = stackToEntry(outStack);
                    if (entry != null) {
                        recipeHitBoxes.add(new ItemHitBox(outX, y, ITEM_SIZE, ITEM_SIZE, entry, outStack));
                    }
                    outX += ITEM_SIZE;
                }
            }
            y += ITEM_SIZE + 4;
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

    private void drawSourcesTab(GuiGraphicsExtractor gfx, int x, int y) {
        if (selectedRecipes.isEmpty()) {
            gfx.text(font, Component.literal("No sources found"), x, y, TEXT_COLOR);
            return;
        }

        gfx.text(font, Component.literal("All sources (" + selectedRecipes.size() + "):"), x, y, TEXT_COLOR);
        y += 14;

        for (RecipeNode recipe : selectedRecipes) {
            if (y > height - 20) break;
            String source = recipe.categoryId().replace("minecraft:", "").replace("atlas:", "");
            gfx.text(font, Component.literal("  " + source + " - " + recipe.id()),
                    x, y, 0xFFAAAAFF);
            y += 12;

            // Show acquisition sources if present
            for (AcquisitionSource src : recipe.sources()) {
                if (y > height - 20) break;
                gfx.text(font, Component.literal("    " + src.type().name() + ": " + src.description()),
                        x, y, 0xFF888888);
                y += 10;
            }
        }
    }

    // ── Alternatives tab ────────────────────────────────────────────────

    private void drawAlternativesTab(GuiGraphicsExtractor gfx, int x, int y, int mouseX, int mouseY) {
        if (selectedEntry == null) {
            gfx.text(font, Component.literal("Select an item to see alternatives"), x, y, TEXT_COLOR);
            return;
        }

        gfx.text(font, Component.literal("Tag-based alternatives for inputs:"), x, y, 0xFF8888FF);
        y += 14;

        // Collect all tag-based ingredients from recipes that produce this item
        boolean foundAny = false;
        for (RecipeNode recipe : selectedRecipes) {
            for (IngredientKey input : recipe.inputs()) {
                if (!input.tagBased() || input.isEmpty()) continue;

                foundAny = true;
                gfx.text(font, Component.literal("Tag: #" + input.id()), x, y, 0xFFAAAAFF);
                y += 12;

                // Resolve all items in this tag
                List<ItemStack> tagItems = resolveTag(input.id());
                if (tagItems.isEmpty()) {
                    gfx.text(font, Component.literal("  (no items found)"), x + 8, y, 0xFF666666);
                    y += 10;
                } else {
                    int itemX = x + 8;
                    for (ItemStack tagStack : tagItems) {
                        if (itemX + GRID_SLOT > width - 10) {
                            itemX = x + 8;
                            y += GRID_SLOT + 2;
                        }
                        if (y > height - 30) break;

                        gfx.item(tagStack, itemX, y);
                        EntryKey entry = stackToEntry(tagStack);
                        if (entry != null) {
                            recipeHitBoxes.add(new ItemHitBox(itemX, y, GRID_SLOT, GRID_SLOT, entry, tagStack));
                        }
                        itemX += GRID_SLOT;
                    }
                    y += GRID_SLOT + 4;
                }

                if (y > height - 30) break;
            }
            if (y > height - 30) break;
        }

        if (!foundAny) {
            gfx.text(font, Component.literal("No tag-based alternatives in these recipes."), x, y, 0xFF888888);
            y += 12;
            gfx.text(font, Component.literal("All ingredients use exact item matches."), x, y, 0xFF666666);
        }

        // Tooltip on hover
        for (var hitBox : recipeHitBoxes) {
            if (mouseX >= hitBox.x && mouseX < hitBox.x + hitBox.w
                    && mouseY >= hitBox.y && mouseY < hitBox.y + hitBox.h) {
                gfx.setTooltipForNextFrame(font, hitBox.stack, mouseX, mouseY);
                break;
            }
        }
    }

    private static List<ItemStack> resolveTag(String tagId) {
        List<ItemStack> items = new ArrayList<>();
        try {
            Identifier loc = Identifier.parse(tagId);
            TagKey<Item> tagKey = TagKey.create(BuiltInRegistries.ITEM.key(), loc);
            for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tagKey)) {
                items.add(new ItemStack(holder.value()));
                if (items.size() >= 36) break; // cap to avoid rendering explosion
            }
        } catch (Exception ignored) {
        }
        return items;
    }

    // ── Unlocks tab ─────────────────────────────────────────────────────

    private void drawUnlocksTab(GuiGraphicsExtractor gfx, int x, int y) {
        if (selectedRecipes.isEmpty()) {
            gfx.text(font, Component.literal("No recipes to evaluate"), x, y, TEXT_COLOR);
            return;
        }

        ContextSnapshot context = ContextSnapshotBuilder.capture();
        AvailabilityEngine engine = new AvailabilityEngine();

        gfx.text(font, Component.literal("Availability for " + selectedEntry.path() + ":"), x, y, 0xFF8888FF);
        y += 14;

        for (RecipeNode recipe : selectedRecipes) {
            if (y > height - 30) break;

            String label = categoryLabel(recipe.categoryId());
            AvailabilityEngine.AvailabilityResult result = engine.evaluate(recipe, context);

            // Status icon and color
            int statusColor;
            String statusIcon;
            if (result.available()) {
                statusColor = 0xFF44CC44;
                statusIcon = "\u2714"; // checkmark
            } else {
                statusColor = switch (result.policy()) {
                    case HIDDEN -> 0xFFCC4444;
                    case TEASER -> 0xFFCC8844;
                    case GREYED_OUT -> 0xFFAAAA44;
                    default -> 0xFF888888;
                };
                statusIcon = switch (result.policy()) {
                    case HIDDEN -> "\u2716"; // X mark
                    case TEASER -> "\uD83D\uDD12"; // lock (fallback to text)
                    default -> "\u26A0"; // warning
                };
            }

            gfx.text(font, Component.literal(statusIcon + " " + label + " - " + recipe.id()),
                    x, y, statusColor);
            y += 12;

            if (!result.blockers().isEmpty()) {
                for (String blocker : result.blockers()) {
                    if (y > height - 20) break;
                    gfx.text(font, Component.literal("  " + blocker), x + 8, y, 0xFFCC8844);
                    y += 10;
                }
            } else if (result.available()) {
                gfx.text(font, Component.literal("  All conditions met"), x + 8, y, 0xFF44CC44);
                y += 10;
            }

            y += 4;
        }

        // Context info at the bottom
        if (y < height - 50) {
            y += 6;
            gfx.text(font, Component.literal("Current context:"), x, y, 0xFF666688);
            y += 11;
            gfx.text(font, Component.literal("  Dim: " + context.dimensionId()), x, y, 0xFF555555);
            y += 10;
            gfx.text(font, Component.literal("  Biome: " + context.biomeId()), x, y, 0xFF555555);
            y += 10;
            gfx.text(font, Component.literal("  Inventory items: " + context.inventoryItemIds().size()), x, y, 0xFF555555);
            y += 10;
            gfx.text(font, Component.literal("  Nearby blocks: " + context.nearbyBlockIds().size()), x, y, 0xFF555555);
        }
    }

    // ── Notes tab ───────────────────────────────────────────────────────

    private void drawNotesTab(GuiGraphicsExtractor gfx, int x, int y) {
        if (selectedEntry == null) {
            gfx.text(font, Component.literal("Select an item to see notes"), x, y, TEXT_COLOR);
            return;
        }

        gfx.text(font, Component.literal("Notes for " + selectedEntry.path()), x, y, 0xFF8888FF);
        y += 16;

        // Info pages are loaded from datapacks (data/<ns>/atlas/info_pages/).
        // Until the datapack loader is wired, show a placeholder with useful
        // static information about the selected item.
        gfx.text(font, Component.literal("Item ID: " + selectedEntry.id()), x, y, 0xFF888888);
        y += 12;
        gfx.text(font, Component.literal("Type: " + selectedEntry.type()), x, y, 0xFF888888);
        y += 12;
        gfx.text(font, Component.literal("Namespace: " + selectedEntry.namespace()), x, y, 0xFF888888);
        y += 16;

        // Show tags for this item
        ItemStack stack = entryToStack(selectedEntry);
        if (!stack.isEmpty()) {
            gfx.text(font, Component.literal("Tags:"), x, y, 0xFFAAAAFF);
            y += 12;
            var tags = stack.typeHolder().tags().toList();
            if (tags.isEmpty()) {
                gfx.text(font, Component.literal("  (none)"), x, y, 0xFF666666);
                y += 10;
            } else {
                for (var tag : tags) {
                    if (y > height - 20) break;
                    gfx.text(font, Component.literal("  #" + tag.location()), x, y, 0xFF888888);
                    y += 10;
                }
            }
        }

        y += 10;
        gfx.text(font, Component.literal("Custom info pages via datapacks"), x, y, 0xFF555555);
        y += 10;
        gfx.text(font, Component.literal("will be loaded from:"), x, y, 0xFF555555);
        y += 10;
        gfx.text(font, Component.literal("data/<ns>/atlas/info_pages/"), x, y, 0xFF666688);
    }

    // ── Filter + Help ──────────────────────────────────────────────────

    private void applyFilter() {
        String filterValue = activeFilter > 0 ? filterOptions.get(activeFilter) : "";
        if ("Saved".equals(filterValue)) {
            String current = searchBox.getValue().replaceAll("@\\S+\\s*", "").trim();
            searchBox.setValue(current);
            return;
        }
        String prefix = activeFilter > 0 ? filterValue + " " : "";
        String current = searchBox.getValue().replaceAll("@\\S+\\s*", "").trim();
        searchBox.setValue(prefix + current);
    }

    private void drawHelpOverlay(GuiGraphicsExtractor gfx) {
        int ox = width / 4;
        int oy = height / 4;
        int ow = width / 2;
        int oh = height / 2;
        gfx.fill(ox, oy, ox + ow, oy + oh, 0xEE101018);
        gfx.fill(ox, oy, ox + ow, oy + 1, DIVIDER_COLOR);
        gfx.fill(ox, oy + oh - 1, ox + ow, oy + oh, DIVIDER_COLOR);
        gfx.fill(ox, oy, ox + 1, oy + oh, DIVIDER_COLOR);
        gfx.fill(ox + ow - 1, oy, ox + ow, oy + oh, DIVIDER_COLOR);

        int x = ox + 8;
        int y = oy + 8;
        int lineH = 11;

        gfx.text(font, Component.literal("Atlas Deep Mode"), x, y, HEADER_COLOR); y += lineH + 2;
        gfx.text(font, Component.literal("Browse items on the left, click to select."), x, y, TEXT_COLOR); y += lineH;
        gfx.text(font, Component.literal("Recipes and details appear on the right."), x, y, TEXT_COLOR); y += lineH + 2;
        gfx.text(font, Component.literal("Tabs:"), x, y, 0xFF8888FF); y += lineH;
        gfx.text(font, Component.literal("  Craft - recipes to make the item"), x, y, TEXT_COLOR); y += lineH;
        gfx.text(font, Component.literal("  Use - recipes that use the item"), x, y, TEXT_COLOR); y += lineH;
        gfx.text(font, Component.literal("  Sources - all ways to obtain"), x, y, TEXT_COLOR); y += lineH;
        gfx.text(font, Component.literal("  Alternatives - tag-based substitutes"), x, y, TEXT_COLOR); y += lineH;
        gfx.text(font, Component.literal("  Unlocks - availability & blockers"), x, y, TEXT_COLOR); y += lineH;
        gfx.text(font, Component.literal("  Notes - item info & tags"), x, y, TEXT_COLOR); y += lineH + 2;
        gfx.text(font, Component.literal("Search prefixes:"), x, y, 0xFF8888FF); y += lineH;
        gfx.text(font, Component.literal("  @mod    - show items from a specific mod"), x, y, TEXT_COLOR); y += lineH;
        gfx.text(font, Component.literal("  $tag    - filter by item tag"), x, y, TEXT_COLOR); y += lineH;
        gfx.text(font, Component.literal("  #text   - search in tooltip text"), x, y, TEXT_COLOR); y += lineH + 2;
        gfx.text(font, Component.literal("Use the filter buttons (All / Vanilla / Saved)"), x, y, TEXT_COLOR); y += lineH;
        gfx.text(font, Component.literal("to narrow results. Mod filters auto-detected."), x, y, TEXT_COLOR); y += lineH + 2;
        gfx.text(font, Component.literal("Keybinds: U to open, Esc to close."), x, y, TEXT_COLOR); y += lineH;
        gfx.text(font, Component.literal("Click ? again to close this help."), x, y, TEXT_COLOR); y += lineH + 4;
        gfx.text(font, Component.literal("github.com/shaedy180/Atlas"), x, y, 0xFF666688);
    }

    // ── Input handling ──────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mouseX = event.x();
        double mouseY = event.y();

        // Save button click detection (right side of recipe panel header)
        if (selectedEntry != null) {
            int pinHeaderY = HEADER_HEIGHT + TAB_HEIGHT + 8 + 2;
            String pinLabel = AtlasFabricClient.pinnedPlanManager().isPinned(selectedEntry) ? "Unsave" : "Save";
            int pinW = font.width(pinLabel) + 8;
            int pinX = width - pinW - 8;
            if (mouseX >= pinX && mouseX < pinX + pinW && mouseY >= pinHeaderY && mouseY < pinHeaderY + 14) {
                PinnedPlanManager pm = AtlasFabricClient.pinnedPlanManager();
                if (pm.isPinned(selectedEntry)) {
                    pm.unpin(selectedEntry);
                } else {
                    pm.pin(selectedEntry, 1);
                }
                return true;
            }

            // "Send to Grid" button click (left of save button)
            boolean hasCrafting = selectedRecipes.stream()
                    .anyMatch(r -> r.categoryId().equals("minecraft:crafting"));
            if (hasCrafting) {
                String sendLabel = "Send to Grid";
                int sendW = font.width(sendLabel) + 8;
                int sendX = pinX - sendW - 4;
                if (mouseX >= sendX && mouseX < sendX + sendW
                        && mouseY >= pinHeaderY && mouseY < pinHeaderY + 14) {
                    pendingTransferRecipe = selectedRecipes.stream()
                            .filter(r -> r.categoryId().equals("minecraft:crafting"))
                            .findFirst().orElse(null);
                    if (pendingTransferRecipe != null) {
                        showFeedback("Recipe queued. Open a crafting table to paste.");
                    }
                    return true;
                }
            }
        }

        // Recipe item click-through: clicking an ingredient/output navigates to its recipes
        int dividerX = panelDividerX();
        if (mouseX > dividerX) {
            for (var hitBox : recipeHitBoxes) {
                if (mouseX >= hitBox.x && mouseX < hitBox.x + hitBox.w
                        && mouseY >= hitBox.y && mouseY < hitBox.y + hitBox.h) {
                    selectedEntry = hitBox.entry;
                    selectedRecipes = AtlasApi.get().recipeGraph().recipesFor(selectedEntry);
                    LOGGER.info("[Atlas] Recipe click-through: {}", selectedEntry.id());
                    return true;
                }
            }
        }

        // Filter chip click handling (manual, matching scissor-rendered chips)
        int filterY = HEADER_HEIGHT + SEARCH_HEIGHT + 6;
        int filterAreaLeft = 6;
        int filterAreaRight = panelDividerX() - 6;
        if (mouseY >= filterY && mouseY < filterY + 14 && mouseX >= filterAreaLeft && mouseX < filterAreaRight) {
            int fx = filterAreaLeft - filterScrollOffset;
            for (int i = 0; i < filterLabels.size(); i++) {
                int fw = font.width(filterLabels.get(i)) + 12;
                if (mouseX >= Math.max(filterAreaLeft, fx) && mouseX < Math.min(filterAreaRight, fx + fw)) {
                    activeFilter = i;
                    applyFilter();
                    return true;
                }
                fx += fw + 2;
            }
        }

        // Check if click is in the item grid
        int startY = HEADER_HEIGHT + SEARCH_HEIGHT + 24;
        int startX = 6;

        if (mouseX >= startX && mouseX < dividerX && mouseY >= startY && mouseY < height) {
            int col = (int) (mouseX - startX) / ITEM_SIZE;
            int row = (int) (mouseY - startY) / ITEM_SIZE;
            int idx = (row + scrollOffset) * gridColumns + col;

            if (col < gridColumns && idx >= 0 && idx < searchResults.size()) {
                selectedEntry = searchResults.get(idx);
                selectedRecipes = AtlasApi.get().recipeGraph().recipesFor(selectedEntry);

                // Creative mode: give item to player
                giveItemIfCreative(selectedEntry, event);

                return true;
            }
        }

        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Horizontal scroll for filter row
        int filterY = HEADER_HEIGHT + SEARCH_HEIGHT + 6;
        if (mouseX < panelDividerX() && mouseY >= filterY && mouseY < filterY + 18) {
            int maxScroll = Math.max(0, totalFilterWidth - panelDividerX() + 12);
            filterScrollOffset = Math.max(0, Math.min(maxScroll, filterScrollOffset + (int)(scrollY * -8)));
            return true;
        }
        // Scroll item grid in the left panel
        if (mouseX < panelDividerX()) {
            scrollOffset = Math.max(0, scrollOffset - (int) scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * Returns and clears a pending recipe stored for transfer into a crafting grid.
     * Called by QuickModeOverlay when the player opens a CraftingScreen.
     */
    public static RecipeNode consumePendingTransfer() {
        RecipeNode r = pendingTransferRecipe;
        pendingTransferRecipe = null;
        return r;
    }

    public static boolean hasPendingTransfer() {
        return pendingTransferRecipe != null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    // ── Creative Mode ─────────────────────────────────────────────────

    private void giveItemIfCreative(EntryKey entry, MouseButtonEvent event) {
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
        // Set locally first so the item appears immediately, then sync to server
        mc.player.getInventory().setItem(freeSlot, stack.copy());
        int containerSlot = freeSlot < 9 ? freeSlot + 36 : freeSlot;
        mc.gameMode.handleCreativeModeItemAdd(stack, containerSlot);
        showFeedback("\u00A7a\u2714 " + stack.getHoverName().getString() + " x" + stack.getCount());
        LOGGER.info("[Atlas] Creative give: {} x{} -> slot {}", entry.id(), stack.getCount(), freeSlot);
    }

    // ── Utility: convert entry/ingredient IDs to ItemStack ──────────────

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
            // Malformed ID, just return empty
        }
        return ItemStack.EMPTY;
    }
}
