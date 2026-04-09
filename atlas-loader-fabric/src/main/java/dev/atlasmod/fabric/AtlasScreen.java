package dev.atlasmod.fabric;

import dev.atlasmod.api.AtlasApi;
import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.entry.IngredientKey;
import dev.atlasmod.core.recipe.RecipeGraph;
import dev.atlasmod.core.recipe.RecipeNode;
import dev.atlasmod.search.SearchIndex;
import dev.atlasmod.search.SearchQuery;
import dev.atlasmod.ui.DeepModeTab;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

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

    private EditBox searchBox;
    private List<EntryKey> searchResults = List.of();
    private int scrollOffset = 0;
    private int gridColumns = 1;

    // Selected item and its recipes
    private EntryKey selectedEntry;
    private List<RecipeNode> selectedRecipes = List.of();
    private DeepModeTab activeTab = DeepModeTab.CRAFT;

    public AtlasScreen() {
        super(Component.translatable("screen.atlas.title"));
    }

    @Override
    protected void init() {
        super.init();
        LOGGER.info("[Atlas] Deep Mode init: {}x{}", width, height);

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

        // Calculate grid columns based on available width
        gridColumns = Math.max(1, (leftPanelWidth - 12) / ITEM_SIZE);

        // Initial search (show everything)
        onSearchChanged("");
    }

    private int panelDividerX() {
        return width * PANEL_DIVIDER_X_RATIO / 100;
    }

    private void onSearchChanged(String text) {
        SearchIndex index = AtlasFabricClient.searchIndex();
        if (index == null) {
            searchResults = List.of();
            return;
        }
        SearchQuery query = SearchQuery.parse(text);
        searchResults = index.search(query);
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

        // Let widgets (search box, buttons) render themselves
        super.extractRenderState(gfx, mouseX, mouseY, partialTick);
    }

    private void drawItemGrid(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        int startY = HEADER_HEIGHT + SEARCH_HEIGHT + 8;
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
        int startY = HEADER_HEIGHT + TAB_HEIGHT + 8;

        if (selectedEntry == null) {
            gfx.text(font, Component.literal("Select an item to view recipes"), startX, startY, TEXT_COLOR);
            return;
        }

        // Show selected item name
        ItemStack stack = entryToStack(selectedEntry);
        if (!stack.isEmpty()) {
            gfx.item(stack, startX, startY);
            gfx.text(font, stack.getHoverName(), startX + 20, startY + 4, HEADER_COLOR);
        }
        startY += 24;

        // Tab-specific content
        switch (activeTab) {
            case CRAFT -> drawCraftTab(gfx, startX, startY);
            case USE -> drawUseTab(gfx, startX, startY);
            case SOURCES -> drawSourcesTab(gfx, startX, startY);
            default -> gfx.text(font, Component.literal(activeTab.name() + " - Coming soon"),
                    startX, startY, TEXT_COLOR);
        }
    }

    private static final int GRID_SLOT = 18;

    private void drawCraftTab(GuiGraphicsExtractor gfx, int x, int y) {
        if (selectedRecipes.isEmpty()) {
            gfx.text(font, Component.literal("No recipes found"), x, y, TEXT_COLOR);
            return;
        }

        for (RecipeNode recipe : selectedRecipes) {
            if (y > height - 20) break;

            String label = categoryLabel(recipe.categoryId());
            var inputs = recipe.inputs();
            var outputs = recipe.outputs();
            boolean isCrafting = recipe.categoryId().equals("minecraft:crafting");

            // Category label
            gfx.text(font, Component.literal(label), x, y, 0xFF8888FF);
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
                        inputX += GRID_SLOT;
                    }
                }
                gfx.text(font, Component.literal("\u2192"), inputX + 4, y + 4, TEXT_COLOR);
                inputX += 14;
                for (var output : outputs) {
                    ItemStack outputStack = entryToStack(output);
                    if (!outputStack.isEmpty()) {
                        gfx.item(outputStack, inputX, y);
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

            y += 4;
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

    private void drawUseTab(GuiGraphicsExtractor gfx, int x, int y) {
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
                    outX += ITEM_SIZE;
                }
            }
            y += ITEM_SIZE + 4;
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
        }
    }

    // ── Input handling ──────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mouseX = event.x();
        double mouseY = event.y();

        // Check if click is in the item grid
        int startY = HEADER_HEIGHT + SEARCH_HEIGHT + 8;
        int startX = 6;
        int dividerX = panelDividerX();

        if (mouseX >= startX && mouseX < dividerX && mouseY >= startY && mouseY < height) {
            int col = (int) (mouseX - startX) / ITEM_SIZE;
            int row = (int) (mouseY - startY) / ITEM_SIZE;
            int idx = (row + scrollOffset) * gridColumns + col;

            if (col < gridColumns && idx >= 0 && idx < searchResults.size()) {
                selectedEntry = searchResults.get(idx);
                selectedRecipes = AtlasApi.get().recipeGraph().recipesFor(selectedEntry);
                return true;
            }
        }

        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Scroll item grid in the left panel
        if (mouseX < panelDividerX()) {
            scrollOffset = Math.max(0, scrollOffset - (int) scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
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
