package dev.atlasmod.client;

import dev.atlasmod.api.AtlasApi;
import dev.atlasmod.client.gui.RecipeView;
import dev.atlasmod.client.gui.Ui;
import dev.atlasmod.core.alternative.AlternativeResolver;
import dev.atlasmod.core.availability.AvailabilityEngine.AvailabilityResult;
import dev.atlasmod.core.availability.AvailabilityEngine.Blocker;
import dev.atlasmod.core.availability.AvailabilityEngine.BlockerKind;
import dev.atlasmod.core.availability.ContextSnapshot;
import dev.atlasmod.core.category.RecipeCategory;
import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.entry.IngredientKey;
import dev.atlasmod.core.info.InfoPage;
import dev.atlasmod.core.plan.CraftingPlan;
import dev.atlasmod.core.recipe.AcquisitionSource;
import dev.atlasmod.core.recipe.RecipeNode;
import dev.atlasmod.search.SearchIndex;
import dev.atlasmod.search.SearchQuery;
import dev.atlasmod.ui.DeepModeTab;
import dev.atlasmod.ui.NoteStore;
import dev.atlasmod.ui.PinnedPlanManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.InputConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Deep Mode: fullscreen Atlas screen with search, item grid and a detail panel
 * with one tab per question (how to craft, what it is used for, where it comes
 * from, the full crafting tree, alternatives, unlock requirements and notes).
 * Opened via keybinding (default: U).
 */
public class AtlasScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("Atlas/DeepMode");

    // Layout constants
    private static final int SEARCH_HEIGHT = 20;
    private static final int ITEM_SIZE = 18;
    private static final int PANEL_DIVIDER_X_RATIO = 45; // left panel uses 45% of width
    private static final int HEADER_HEIGHT = 30;
    private static final int TAB_HEIGHT = 12;
    private static final int LINE = 11;
    private static final int MAX_USES = 60;

    // Colors
    private static final int PANEL_BG = 0xCC1A1A2E;
    private static final int DIVIDER_COLOR = 0xFF404060;
    private static final int HIGHLIGHT_COLOR = 0x40FFFFFF;

    private static final String ISSUES_URL = "https://github.com/shaedy180/Atlas/issues";

    // Kept between openings so the screen comes back the way it was left
    private static String lastSearch = "";
    private static EntryKey lastEntry;
    private static DeepModeTab lastTab = DeepModeTab.CRAFT;

    private final Ui ui = new Ui();

    // Filter chips (dynamic: rebuilt on init)
    private List<String> filterOptions = new ArrayList<>();
    private List<String> filterLabels = new ArrayList<>();
    private int activeFilter = 0;
    private int filterScrollOffset = 0;
    private int totalFilterWidth = 0;

    private boolean showHelp = false;
    private boolean showPlans = false;

    private EditBox searchBox;
    private EditBox noteBox;
    private List<EntryKey> searchResults = List.of();
    private int scrollOffset = 0;
    private int gridColumns = 1;

    // Selected item, its recipes and navigation history
    private EntryKey selectedEntry;
    private List<RecipeNode> selectedRecipes = List.of();
    private List<RecipeNode> selectedUses = List.of();
    private DeepModeTab activeTab;
    private final Deque<EntryKey> history = new ArrayDeque<>();

    // Right panel scrolling
    private int rightScroll = 0;
    private int contentHeight = 0;
    private int contentTop = 0;
    private int contentBottom = 0;

    // Tree tab
    private int treeCount = 1;
    private final Set<String> collapsed = new HashSet<>();
    private CraftingPlan cachedPlan;
    private String cachedPlanKey = "";
    private long cachedPlanTime;

    // In-UI feedback (visible even when full-screen)
    private String feedbackText = null;
    private long feedbackExpireTime = 0;

    public AtlasScreen() {
        this(lastEntry, lastTab);
    }

    /** Opens Deep Mode directly on an item and tab. */
    public AtlasScreen(EntryKey entry, DeepModeTab tab) {
        super(Component.translatable("screen.atlas.title"));
        this.activeTab = tab == null ? DeepModeTab.CRAFT : tab;
        if (entry != null) select(entry, false);
    }

    @Override
    protected void init() {
        super.init();
        rebuildFilters();

        int leftPanelWidth = panelDividerX();
        searchBox = new EditBox(font, 6, HEADER_HEIGHT + 2, leftPanelWidth - 12, SEARCH_HEIGHT,
                Component.translatable("screen.atlas.search"));
        searchBox.setMaxLength(128);
        searchBox.setHint(Component.literal("Search items, @mod, $tag..."));
        searchBox.setValue(lastSearch);
        searchBox.setResponder(this::onSearchChanged);
        addRenderableWidget(searchBox);

        noteBox = new EditBox(font, leftPanelWidth + 6, height - 40, width - leftPanelWidth - 12, 16,
                Component.literal("Note"));
        noteBox.setMaxLength(NoteStore.MAX_NOTE_LENGTH);
        noteBox.setHint(Component.literal("Write a note and press Enter"));
        addRenderableWidget(noteBox);

        totalFilterWidth = 0;
        for (String label : filterLabels) totalFilterWidth += font.width(label) + 14;

        addRenderableWidget(Button.builder(Component.literal("?"), btn -> showHelp = !showHelp)
                .bounds(width - 22, 4, 18, 14).build());

        String reportLabel = "Report Bug / Request Mod";
        int reportW = font.width(reportLabel) + 12;
        addRenderableWidget(Button.builder(Component.literal(reportLabel),
                        btn -> ConfirmLinkScreen.confirmLinkNow(this, URI.create(ISSUES_URL)))
                .bounds(width - reportW - 4, height - 20, reportW, 16).build());

        gridColumns = Math.max(1, (leftPanelWidth - 12) / ITEM_SIZE);
        onSearchChanged(searchBox.getValue());
    }

    @Override
    public void removed() {
        lastSearch = searchBox != null ? searchBox.getValue() : lastSearch;
        lastEntry = selectedEntry;
        lastTab = activeTab;
        AtlasClient.saveAll();
        super.removed();
    }

    public DeepModeTab activeTab() {
        return activeTab;
    }

    private int panelDividerX() {
        return width * PANEL_DIVIDER_X_RATIO / 100;
    }

    private void rebuildFilters() {
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

    private void showFeedback(String message) {
        feedbackText = message;
        feedbackExpireTime = System.currentTimeMillis() + 2000;
    }

    private void onSearchChanged(String text) {
        SearchIndex index = AtlasClient.searchIndex();
        SearchQuery query = SearchQuery.parse(text);
        List<EntryKey> all = index.search(query);
        String filter = filterOptions.isEmpty() ? "" : filterOptions.get(activeFilter);
        if ("Saved".equals(filter)) {
            searchResults = onlyPinned(all, AtlasClient.savedItems());
        } else if ("Plans".equals(filter)) {
            searchResults = onlyPinned(all, AtlasClient.buildPlans());
        } else {
            searchResults = all;
        }
        scrollOffset = 0;
    }

    private static List<EntryKey> onlyPinned(List<EntryKey> results, PinnedPlanManager manager) {
        Set<EntryKey> pinned = new HashSet<>();
        manager.plans().forEach(p -> pinned.add(p.target()));
        return results.stream().filter(pinned::contains).toList();
    }

    // -- Selection -------------------------------------------------------------

    private void select(EntryKey entry, boolean remember) {
        if (entry == null) return;
        if (remember && selectedEntry != null && !selectedEntry.equals(entry)) {
            history.push(selectedEntry);
            while (history.size() > 50) history.removeLast();
        }
        selectedEntry = entry;
        selectedRecipes = AtlasClient.recipeGraph().recipesFor(entry);
        selectedUses = findUses(entry);
        rightScroll = 0;
        collapsed.clear();
        cachedPlan = null;
        int pinnedCount = AtlasClient.buildPlans().count(entry);
        treeCount = pinnedCount > 0 ? pinnedCount : 1;
    }

    private void navigate(EntryKey entry) {
        select(entry, true);
        LOGGER.debug("[Atlas] Navigated to {}", entry.id());
    }

    private void goBack() {
        if (history.isEmpty()) return;
        EntryKey previous = history.pop();
        select(previous, false);
    }

    private static List<RecipeNode> findUses(EntryKey entry) {
        var tags = AtlasClient.tags();
        List<RecipeNode> uses = new ArrayList<>();
        for (RecipeNode node : AtlasClient.recipeGraph().allNodes()) {
            for (IngredientKey input : node.ingredientCounts().keySet()) {
                if (tags.matches(input, entry.id())) {
                    uses.add(node);
                    break;
                }
            }
        }
        return uses;
    }

    // -- Rendering -------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        int dividerX = panelDividerX();
        ui.begin(this::navigate);

        gfx.fill(0, 0, dividerX, height, PANEL_BG);
        gfx.fill(dividerX, 0, dividerX + 1, height, DIVIDER_COLOR);
        gfx.fill(dividerX + 1, 0, width, height, PANEL_BG);
        gfx.centeredText(font, Component.literal("Atlas"), width / 2, 8, Ui.HEADER);

        int plansCount = AtlasClient.buildPlans().plans().size();
        String plansLabel = "Plans" + (plansCount > 0 ? " (" + plansCount + ")" : "");
        ui.button(gfx, font, plansLabel, width - 30 - font.width(plansLabel) - 8, 5, showPlans, mouseX, mouseY,
                () -> {
                    showPlans = !showPlans;
                    rightScroll = 0;
                });

        drawItemGrid(gfx, mouseX, mouseY);

        noteBox.setVisible(!showPlans && selectedEntry != null && activeTab == DeepModeTab.NOTES);
        if (showPlans) {
            drawPlansPanel(gfx, dividerX + 6, mouseX, mouseY);
        } else {
            drawRecipePanel(gfx, dividerX + 6, mouseX, mouseY);
        }

        if (feedbackText != null && System.currentTimeMillis() < feedbackExpireTime) {
            int fbW = font.width(feedbackText) + 12;
            int fbX = (width - fbW) / 2;
            int fbY = height - 24;
            gfx.fill(fbX, fbY, fbX + fbW, fbY + 14, 0xCC222222);
            gfx.text(font, feedbackText, fbX + 6, fbY + 3, 0xFF88FF88);
        } else {
            feedbackText = null;
        }

        super.extractRenderState(gfx, mouseX, mouseY, partialTick);
        drawFilterChips(gfx, dividerX);
        ui.end();

        if (showHelp) {
            drawHelpOverlay(gfx);
        } else {
            ui.tooltips(gfx, font, mouseX, mouseY);
        }
    }

    private void drawFilterChips(GuiGraphicsExtractor gfx, int dividerX) {
        int filterY = HEADER_HEIGHT + SEARCH_HEIGHT + 6;
        int left = 6;
        int right = dividerX - 6;
        gfx.enableScissor(left, filterY, right, filterY + 14);
        int fx = left - filterScrollOffset;
        for (int i = 0; i < filterLabels.size(); i++) {
            String label = filterLabels.get(i);
            int fw = font.width(label) + 12;
            int bg = (i == activeFilter) ? Ui.CHIP_ACTIVE : 0x44303030;
            gfx.fill(fx, filterY, fx + fw, filterY + 14, bg);
            gfx.centeredText(font, Component.literal(label), fx + fw / 2, filterY + 3,
                    i == activeFilter ? Ui.HEADER : Ui.TEXT);
            int visibleLeft = Math.max(left, fx);
            int visibleRight = Math.min(right, fx + fw);
            if (visibleRight > visibleLeft) {
                final int index = i;
                ui.area(visibleLeft, filterY, visibleRight - visibleLeft, 14, () -> {
                    activeFilter = index;
                    applyFilter();
                });
            }
            fx += fw + 2;
        }
        gfx.disableScissor();

        if (filterScrollOffset > 0) {
            gfx.text(font, "◀", left - 1, filterY + 3, Ui.MUTED);
        }
        int maxFilterScroll = Math.max(0, totalFilterWidth - (right - left));
        if (filterScrollOffset < maxFilterScroll) {
            gfx.text(font, "▶", right - 5, filterY + 3, Ui.MUTED);
        }
    }

    private int gridTop() {
        return HEADER_HEIGHT + SEARCH_HEIGHT + 24;
    }

    private void drawItemGrid(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        int startY = gridTop();
        int startX = 6;
        int maxRows = (height - startY - 4) / ITEM_SIZE;
        int visibleItems = gridColumns * maxRows;
        PinnedPlanManager saved = AtlasClient.savedItems();
        PinnedPlanManager plans = AtlasClient.buildPlans();

        gfx.enableScissor(0, startY, panelDividerX(), height);
        for (int i = 0; i < visibleItems && (i + scrollOffset * gridColumns) < searchResults.size(); i++) {
            EntryKey entry = searchResults.get(i + scrollOffset * gridColumns);
            int x = startX + (i % gridColumns) * ITEM_SIZE;
            int y = startY + (i / gridColumns) * ITEM_SIZE;

            if (entry.equals(selectedEntry)) {
                gfx.fill(x - 1, y - 1, x + ITEM_SIZE - 1, y + ITEM_SIZE - 1, HIGHLIGHT_COLOR);
            }
            // Saved items get a yellow corner, build plans a cyan one
            if (saved.isPinned(entry)) gfx.fill(x + ITEM_SIZE - 5, y, x + ITEM_SIZE - 2, y + 3, 0xFFFFCC44);
            if (plans.isPinned(entry)) gfx.fill(x, y, x + 3, y + 3, 0xFF44CCFF);

            ItemStack stack = ItemStacks.of(entry);
            if (!stack.isEmpty()) {
                gfx.item(stack, x, y);
                if (mouseX >= x && mouseX < x + ITEM_SIZE && mouseY >= y && mouseY < y + ITEM_SIZE && !showHelp) {
                    gfx.setTooltipForNextFrame(font, stack, mouseX, mouseY);
                }
            }
        }
        gfx.disableScissor();

        if (searchResults.isEmpty()) {
            gfx.text(font, AtlasClient.searchIndex().size() == 0 ? "Join a world to load items" : "No items match",
                    startX, startY + 4, Ui.MUTED);
        }
    }

    private void drawRecipePanel(GuiGraphicsExtractor gfx, int startX, int mouseX, int mouseY) {
        int right = width - 6;
        int tabY = HEADER_HEIGHT + 2;
        int tabX = startX;
        for (DeepModeTab tab : DeepModeTab.values()) {
            int w = font.width(tab.label()) + 8;
            if (tabX + w > right) {
                tabX = startX;
                tabY += TAB_HEIGHT + 2;
            }
            ui.button(gfx, font, tab.label(), tabX, tabY, tab == activeTab, mouseX, mouseY, () -> {
                activeTab = tab;
                rightScroll = 0;
            });
            tabX += w + 2;
        }
        int y = tabY + TAB_HEIGHT + 6;

        if (selectedEntry == null) {
            gfx.text(font, "Select an item to view recipes", startX, y, Ui.TEXT);
            return;
        }

        // Item header: back button, icon, name and the save/plan buttons
        int headerX = startX;
        if (!history.isEmpty()) {
            headerX += ui.button(gfx, font, "<", startX, y + 2, false, mouseX, mouseY, this::goBack) + 4;
        }
        ItemStack stack = ItemStacks.of(selectedEntry);
        if (!stack.isEmpty()) {
            gfx.item(stack, headerX, y);
            ui.tooltip(headerX, y, 16, 16, List.of(stack.getHoverName()));
        }
        PinnedPlanManager saved = AtlasClient.savedItems();
        boolean isSaved = saved.isPinned(selectedEntry);
        String saveLabel = isSaved ? "Unsave" : "Save";
        int saveX = right - font.width(saveLabel) - 8;
        ui.button(gfx, font, saveLabel, saveX, y + 2, isSaved ? 0x88885533 : Ui.CHIP,
                isSaved ? 0xFFFFCC44 : 0xFFAAAAFF, mouseX, mouseY, () -> toggle(saved, selectedEntry, 1));
        String name = stack.isEmpty() ? selectedEntry.id() : stack.getHoverName().getString();
        gfx.text(font, Ui.fit(font, name, saveX - headerX - 26), headerX + 20, y + 4, Ui.HEADER);
        y += 22;

        contentTop = y;
        contentBottom = activeTab == DeepModeTab.NOTES ? height - 44 : height - 24;
        gfx.enableScissor(startX - 2, contentTop, width, contentBottom);
        ui.clip(contentTop, contentBottom);
        int contentStart = contentTop - rightScroll;
        int end = switch (activeTab) {
            case CRAFT -> drawCraftTab(gfx, startX, contentStart, right, mouseX, mouseY);
            case USE -> drawUseTab(gfx, startX, contentStart, right, mouseX, mouseY);
            case SOURCES -> drawSourcesTab(gfx, startX, contentStart, right, mouseX, mouseY);
            case TREE -> drawTreeTab(gfx, startX, contentStart, right, mouseX, mouseY);
            case ALTERNATIVES -> drawAlternativesTab(gfx, startX, contentStart, right, mouseX, mouseY);
            case UNLOCKS -> drawUnlocksTab(gfx, startX, contentStart, right);
            case NOTES -> drawNotesTab(gfx, startX, contentStart, right, mouseX, mouseY);
        };
        ui.unclip();
        gfx.disableScissor();
        contentHeight = end - contentStart;
        drawScrollBar(gfx);
    }

    private void drawScrollBar(GuiGraphicsExtractor gfx) {
        int view = contentBottom - contentTop;
        if (contentHeight <= view) {
            rightScroll = 0;
            return;
        }
        rightScroll = Math.min(rightScroll, contentHeight - view);
        int barH = Math.max(10, view * view / contentHeight);
        int barY = contentTop + (view - barH) * rightScroll / (contentHeight - view);
        gfx.fill(width - 3, contentTop, width - 1, contentBottom, 0x33FFFFFF);
        gfx.fill(width - 3, barY, width - 1, barY + barH, 0xAAAAAACC);
    }

    private static void toggle(PinnedPlanManager manager, EntryKey entry, int count) {
        if (manager.isPinned(entry)) {
            manager.unpin(entry);
        } else {
            manager.pin(entry, count);
        }
        AtlasClient.saveAll();
    }

    // -- Text helpers ----------------------------------------------------------

    private int heading(GuiGraphicsExtractor gfx, String text, int x, int y) {
        gfx.text(font, text, x, y, Ui.ACCENT);
        return y + LINE + 1;
    }

    private int wrapped(GuiGraphicsExtractor gfx, String text, int x, int y, int width, int color) {
        for (var line : font.split(FormattedText.of(text), Math.max(20, width))) {
            gfx.text(font, line, x, y, color);
            y += LINE - 1;
        }
        return y;
    }

    /** A small colored square used as a status marker. */
    private static void dot(GuiGraphicsExtractor gfx, int x, int y, int color) {
        gfx.fill(x, y + 2, x + 5, y + 7, color);
    }

    // -- Craft tab -------------------------------------------------------------

    private int drawCraftTab(GuiGraphicsExtractor gfx, int x, int y, int right, int mouseX, int mouseY) {
        if (selectedRecipes.isEmpty()) {
            y = wrapped(gfx, "No recipe makes this item. Check the Sources tab for other ways to get it.",
                    x, y, right - x, Ui.TEXT);
            return y;
        }
        ContextSnapshot ctx = AtlasClient.context().snapshot();
        var insights = AtlasClient.insights();
        Map<String, AlternativeResolver.RankedRecipe> ranking = new LinkedHashMap<>();
        for (var ranked : insights.alternatives().rankRecipes(selectedEntry, null)) {
            ranking.put(ranked.recipe().id(), ranked);
        }

        for (RecipeNode recipe : selectedRecipes) {
            int lx = x;
            gfx.text(font, RecipeView.categoryName(recipe.categoryId()), lx, y, 0xFF8888FF);
            lx += font.width(RecipeView.categoryName(recipe.categoryId())) + 6;
            var ranked = ranking.get(recipe.id());
            if (ranked != null) {
                if (ranked.marks().contains(AlternativeResolver.Mark.CHEAPEST)) {
                    gfx.text(font, "cheapest", lx, y, Ui.GOOD);
                    lx += font.width("cheapest") + 6;
                }
                if (ranked.marks().contains(AlternativeResolver.Mark.SIMPLEST)) {
                    gfx.text(font, "simplest", lx, y, 0xFF88CCFF);
                }
            }
            y += LINE + 1;
            y += RecipeView.draw(ui, gfx, font, recipe, x, y, 18, right - x) + 3;

            if (recipe.processingTime() > 0) {
                gfx.text(font, "Time: " + recipe.processingTime() / 20.0 + "s", x, y, Ui.MUTED);
                y += LINE - 1;
            }
            y = drawStatus(gfx, insights.availability().evaluateFully(recipe, ctx), x, y, right - x);
            y += 8;
        }
        return y;
    }

    /** One line per problem, or a single green "Ready to craft". */
    private int drawStatus(GuiGraphicsExtractor gfx, AvailabilityResult result, int x, int y, int width) {
        if (result.craftableNow()) {
            dot(gfx, x, y, Ui.GOOD);
            gfx.text(font, "Ready to craft", x + 8, y, Ui.GOOD);
            return y + LINE;
        }
        for (Blocker blocker : result.details()) {
            if (blocker.kind() == BlockerKind.MATERIALS) continue;
            dot(gfx, x, y, Ui.BAD);
            y = wrapped(gfx, blocker.message(), x + 8, y, width - 8, Ui.BAD);
        }
        var missing = result.missingMaterials();
        if (!missing.isEmpty()) {
            StringBuilder text = new StringBuilder("Missing: ");
            for (int i = 0; i < missing.size(); i++) {
                Blocker m = missing.get(i);
                if (i > 0) text.append(", ");
                text.append(m.amount()).append("x ").append(ItemStacks.name(m.subject()));
            }
            dot(gfx, x, y, Ui.WARN);
            y = wrapped(gfx, text.toString(), x + 8, y, width - 8, Ui.WARN);
        }
        return y;
    }

    // -- Uses tab --------------------------------------------------------------

    private int drawUseTab(GuiGraphicsExtractor gfx, int x, int y, int right, int mouseX, int mouseY) {
        // Machines list the categories they run
        List<RecipeCategory> runs = AtlasApi.get().categories().values().stream()
                .filter(c -> c.stations().contains(selectedEntry.id()))
                .sorted(Comparator.comparingInt(RecipeCategory::order))
                .toList();
        if (!runs.isEmpty()) {
            y = heading(gfx, "Workstation for", x, y);
            for (RecipeCategory category : runs) {
                long count = AtlasClient.recipeGraph().allNodes().stream()
                        .filter(n -> n.categoryId().equals(category.id())).count();
                gfx.text(font, category.name() + " (" + count + " recipes)", x + 4, y, Ui.TEXT);
                y += LINE;
            }
            y += 6;
        }

        if (selectedUses.isEmpty()) {
            gfx.text(font, "Not used in any recipe", x, y, Ui.TEXT);
            return y + LINE;
        }

        y = heading(gfx, "Used in " + selectedUses.size() + " recipes", x, y);
        int shown = 0;
        for (RecipeNode recipe : selectedUses) {
            if (shown++ >= MAX_USES) {
                gfx.text(font, "and " + (selectedUses.size() - MAX_USES) + " more", x, y, Ui.MUTED);
                y += LINE;
                break;
            }
            gfx.text(font, RecipeView.categoryName(recipe.categoryId()), x, y, Ui.MUTED);
            y += LINE - 1;
            y += RecipeView.draw(ui, gfx, font, recipe, x, y, 16, right - x) + 6;
        }
        return y;
    }

    // -- Sources tab -----------------------------------------------------------

    private int drawSourcesTab(GuiGraphicsExtractor gfx, int x, int y, int right, int mouseX, int mouseY) {
        y = heading(gfx, "How to get it", x, y);

        Map<String, Integer> perCategory = new LinkedHashMap<>();
        for (RecipeNode recipe : selectedRecipes) perCategory.merge(recipe.categoryId(), 1, Integer::sum);
        for (var entry : perCategory.entrySet()) {
            var category = AtlasApi.get().categoryById(entry.getKey());
            String icon = category.map(RecipeCategory::icon).orElse(null);
            if (icon != null) ui.item(gfx, font, ItemStacks.of(icon), x, y, null);
            String label = RecipeView.categoryName(entry.getKey()) + ": " + entry.getValue()
                    + (entry.getValue() == 1 ? " recipe" : " recipes");
            gfx.text(font, label, x + 20, y + 4, Ui.TEXT);
            ui.area(x + 20, y, font.width(label), 16, () -> activeTab = DeepModeTab.CRAFT);
            y += 18;
        }

        List<AcquisitionSource> sources = new ArrayList<>(AtlasApi.get().sourcesFor(selectedEntry.id()));
        selectedRecipes.forEach(r -> sources.addAll(r.sources()));
        for (AcquisitionSource source : sources) {
            gfx.text(font, ItemStacks.pretty(source.type().name().toLowerCase(Locale.ROOT)), x, y, Ui.ACCENT);
            y += LINE;
            if (source.description() != null && !source.description().isEmpty()) {
                y = wrapped(gfx, source.description(), x + 6, y, right - x - 6, Ui.TEXT);
            }
            if (source.detail() != null && !source.detail().isEmpty()) {
                y = wrapped(gfx, source.detail(), x + 6, y, right - x - 6, Ui.MUTED);
            }
            y += 3;
        }

        if (perCategory.isEmpty() && sources.isEmpty()) {
            y = wrapped(gfx, "No recipe makes this. It has to be found in the world, dropped by a mob, "
                    + "traded or fished.", x, y, right - x, Ui.TEXT);
        }

        y += 6;
        if (!selectedUses.isEmpty()) {
            String uses = "Used in " + selectedUses.size() + " recipes";
            ui.button(gfx, font, uses, x, y, false, mouseX, mouseY, () -> activeTab = DeepModeTab.USE);
            y += 16;
        }
        return y;
    }

    // -- Tree tab --------------------------------------------------------------

    private CraftingPlan currentPlan() {
        String key = selectedEntry.id() + "|" + treeCount;
        long now = System.currentTimeMillis();
        if (cachedPlan == null || !key.equals(cachedPlanKey) || now - cachedPlanTime > 1000) {
            cachedPlan = AtlasClient.insights().plan(selectedEntry, treeCount);
            cachedPlanKey = key;
            cachedPlanTime = now;
        }
        return cachedPlan;
    }

    private int drawTreeTab(GuiGraphicsExtractor gfx, int x, int y, int right, int mouseX, int mouseY) {
        gfx.text(font, "Amount", x, y + 2, Ui.TEXT);
        int bx = x + font.width("Amount") + 6;
        bx += ui.button(gfx, font, "-", bx, y, false, mouseX, mouseY, () -> treeCount = Math.max(1, treeCount - 1)) + 2;
        String count = String.valueOf(treeCount);
        gfx.text(font, count, bx + 2, y + 2, Ui.HEADER);
        bx += font.width(count) + 6;
        bx += ui.button(gfx, font, "+", bx, y, false, mouseX, mouseY, () -> treeCount = Math.min(999, treeCount + 1)) + 2;
        bx += ui.button(gfx, font, "x8", bx, y, false, mouseX, mouseY, () -> treeCount = Math.min(999, treeCount * 8)) + 8;

        PinnedPlanManager plans = AtlasClient.buildPlans();
        boolean pinned = plans.isPinned(selectedEntry);
        boolean sameCount = plans.count(selectedEntry) == treeCount;
        String pinLabel = !pinned ? "Pin plan" : sameCount ? "Unpin plan" : "Update plan";
        ui.button(gfx, font, pinLabel, bx, y, pinned, mouseX, mouseY, () -> {
            if (pinned && sameCount) {
                plans.unpin(selectedEntry);
                showFeedback("Plan removed");
            } else {
                plans.pin(selectedEntry, treeCount);
                showFeedback("Plan pinned, it shows up next to your inventory");
            }
            AtlasClient.saveAll();
        });
        y += 18;

        CraftingPlan plan = currentPlan();
        if (plan.done()) {
            dot(gfx, x, y, Ui.GOOD);
            gfx.text(font, "You already have " + treeCount + ".", x + 8, y, Ui.GOOD);
            y += LINE + 4;
        }
        if (plan.root().status() == CraftingPlan.Status.RAW) {
            return wrapped(gfx, "No recipe. This has to be found or farmed.", x, y, right - x, Ui.TEXT);
        }

        y = drawTreeNode(gfx, plan.root(), x, y, right, 0, "", mouseX, mouseY);
        if (plan.hasCycle()) {
            y = wrapped(gfx, "Some branches loop back into themselves (like ingots and blocks) "
                    + "and stop there.", x, y + 2, right - x, Ui.MUTED);
        }

        y += 8;
        y = heading(gfx, "Materials", x, y);
        List<CraftingPlan.Material> materials = new ArrayList<>(plan.materials());
        materials.sort(Comparator.comparingInt(CraftingPlan.Material::missing).reversed());
        for (var material : materials) {
            ui.item(gfx, font, ItemStacks.of(material.item()), x, y, null);
            String text = material.needed() + "x " + ItemStacks.name(material.item().id());
            gfx.text(font, Ui.fit(font, text, right - x - 90), x + 20, y + 4, Ui.TEXT);
            String state = material.missing() == 0 ? "have all" : "have " + material.have() + ", need " + material.missing();
            int stateW = font.width(state);
            gfx.text(font, state, right - stateW - 4, y + 4, material.missing() == 0 ? Ui.GOOD : Ui.WARN);
            y += 18;
        }

        if (!plan.steps().isEmpty()) {
            y += 6;
            y = heading(gfx, "Steps", x, y);
            int n = 1;
            for (var step : plan.steps()) {
                ui.item(gfx, font, ItemStacks.of(step.item()), x + 14, y, null);
                gfx.text(font, n++ + ".", x, y + 4, Ui.MUTED);
                String text = step.produced() + "x " + ItemStacks.name(step.item().id()) + "  "
                        + RecipeView.categoryName(step.recipe().categoryId())
                        + (step.crafts() > 1 ? ", " + step.crafts() + " times" : "");
                gfx.text(font, Ui.fit(font, text, right - x - 40), x + 34, y + 4, Ui.TEXT);
                y += 18;
            }
        }
        return y;
    }

    private int drawTreeNode(GuiGraphicsExtractor gfx, CraftingPlan.Node node, int left, int y, int right,
                             int depth, String path, int mouseX, int mouseY) {
        int x = left + depth * 12;
        String key = path + "/" + node.item().id();
        // Far below the view: only measure, drawing hundreds of hidden items is wasted work
        if (y > contentBottom + 200) return y + treeHeight(node, key);
        boolean hasChildren = !node.children().isEmpty();
        boolean isCollapsed = collapsed.contains(key);

        if (depth > 0) gfx.fill(x - 7, y + 8, x - 2, y + 9, 0x66AAAAAA);
        if (hasChildren) {
            String arrow = isCollapsed ? "+" : "-";
            gfx.text(font, arrow, x, y + 4, Ui.MUTED);
            ui.area(x - 1, y, 8, 16, () -> {
                if (!collapsed.remove(key)) collapsed.add(key);
            });
        }
        ui.item(gfx, font, ItemStacks.of(node.item()), x + 8, y, null);

        String name = node.needed() + "x " + ItemStacks.name(node.item().id());
        String state;
        int color;
        switch (node.status()) {
            case HAVE -> { state = "have"; color = Ui.GOOD; }
            case RAW -> {
                state = node.missing() == 0 ? "have" : "gather " + node.missing();
                color = node.missing() == 0 ? Ui.GOOD : Ui.WARN;
            }
            case CRAFT -> {
                state = "craft " + node.crafts() + "x, " + RecipeView.categoryName(node.recipe().categoryId())
                        + (node.fromInventory() > 0 ? ", have " + node.fromInventory() : "");
                color = 0xFF88CCFF;
            }
            case CYCLE -> { state = "loops back"; color = Ui.BAD; }
            default -> { state = "too deep"; color = Ui.MUTED; }
        }
        int textX = x + 28;
        gfx.text(font, Ui.fit(font, name, right - textX - 10), textX, y + 4, Ui.TEXT);
        int stateX = textX + Math.min(font.width(name), right - textX - 10) + 6;
        if (stateX < right - 20) gfx.text(font, Ui.fit(font, state, right - stateX - 4), stateX, y + 4, color);
        if (node.tagId() != null) {
            ui.tooltip(x + 8, y, 16, 16, List.of(Component.literal("Any #" + node.tagId())));
        }
        y += 18;

        if (hasChildren && !isCollapsed) {
            for (var child : node.children()) {
                y = drawTreeNode(gfx, child, left, y, right, depth + 1, key, mouseX, mouseY);
            }
        }
        return y;
    }

    private int treeHeight(CraftingPlan.Node node, String key) {
        int h = 18;
        if (!collapsed.contains(key)) {
            for (var child : node.children()) h += treeHeight(child, key + "/" + child.item().id());
        }
        return h;
    }

    // -- Alternatives tab ------------------------------------------------------

    private int drawAlternativesTab(GuiGraphicsExtractor gfx, int x, int y, int right, int mouseX, int mouseY) {
        var insights = AtlasClient.insights();
        var resolver = insights.alternatives();
        var inventory = insights.inventory();
        boolean any = false;

        var ranked = resolver.rankRecipes(selectedEntry, inventory);
        if (ranked.size() > 1) {
            any = true;
            y = heading(gfx, "Ways to make it, cheapest first", x, y);
            for (var option : ranked) {
                int lx = x;
                String label = RecipeView.categoryName(option.recipe().categoryId());
                gfx.text(font, label, lx, y, Ui.TEXT);
                lx += font.width(label) + 6;
                for (var mark : option.marks()) {
                    String text = switch (mark) {
                        case CHEAPEST -> "cheapest";
                        case SIMPLEST -> "simplest";
                        case CRAFTABLE_NOW -> "can craft now";
                    };
                    int color = mark == AlternativeResolver.Mark.SIMPLEST ? 0xFF88CCFF : Ui.GOOD;
                    gfx.text(font, text, lx, y, color);
                    lx += font.width(text) + 6;
                }
                String cost = option.cost() == Double.POSITIVE_INFINITY ? "loops"
                        : String.format(Locale.ROOT, "%.1f raw, %d steps", option.cost(), option.steps());
                gfx.text(font, cost, right - font.width(cost) - 4, y, Ui.MUTED);
                y += LINE;
                y += RecipeView.draw(ui, gfx, font, option.recipe(), x, y, 16, right - x) + 6;
            }
            y += 4;
        }

        var substitutions = resolver.substitutions(selectedEntry, inventory);
        if (!substitutions.isEmpty()) {
            any = true;
            y = heading(gfx, "Accepted substitutes", x, y);
            for (var sub : substitutions) {
                String label = "Any " + ItemStacks.tagName(sub.ingredient().id());
                gfx.text(font, label, x, y, Ui.TEXT);
                gfx.text(font, Ui.fit(font, "#" + sub.ingredient().id(), right - x - font.width(label) - 8),
                        x + font.width(label) + 6, y, Ui.MUTED);
                y += LINE;
                y = drawOptionRow(gfx, sub.options(), x, y, right);
                y += 4;
            }
        }

        var swaps = resolver.interchangeable(selectedEntry.id());
        if (!swaps.isEmpty()) {
            any = true;
            y = heading(gfx, "Can be swapped with", x, y);
            String tags = String.join(", ", resolver.ingredientTagsOf(selectedEntry.id()).stream()
                    .map(t -> "#" + t).toList());
            y = wrapped(gfx, "Recipes asking for " + tags + " take any of these:", x, y, right - x, Ui.MUTED);
            int ix = x;
            for (String id : swaps) {
                if (ix + 18 > right) {
                    ix = x;
                    y += 18;
                }
                ui.item(gfx, font, ItemStacks.of(id), ix, y, null);
                ix += 18;
            }
            y += 20;
        }

        if (!any) {
            y = wrapped(gfx, "No alternatives known. There is only one way to make this and it uses no tags.",
                    x, y, right - x, Ui.TEXT);
        }
        return y;
    }

    private int drawOptionRow(GuiGraphicsExtractor gfx, List<AlternativeResolver.Option> options, int x, int y, int right) {
        int ix = x;
        for (var option : options) {
            if (ix + 18 > right) {
                ix = x;
                y += 18;
            }
            if (option.preferred()) {
                gfx.fill(ix - 1, y - 1, ix + 17, y + 17, option.owned() > 0 ? 0x887CDB7C : 0x668888FF);
            }
            ui.item(gfx, font, ItemStacks.of(option.itemId()), ix, y, option.owned() > 0 ? String.valueOf(option.owned()) : null);
            ix += 18;
        }
        return y + 18;
    }

    // -- Unlocks tab -----------------------------------------------------------

    private int drawUnlocksTab(GuiGraphicsExtractor gfx, int x, int y, int right) {
        ClientContext context = AtlasClient.context();
        ContextSnapshot ctx = context.snapshot();
        var engine = AtlasClient.insights().availability();
        int width = right - x;

        gfx.text(font, "You are in " + ItemStacks.pretty(ctx.dimensionId())
                + (ctx.biomeId().isEmpty() ? "" : ", " + ItemStacks.pretty(ctx.biomeId())), x, y, Ui.MUTED);
        y += LINE + 2;
        if (!context.progressKnown()) {
            y = wrapped(gfx, "Recipe book and advancement details are only known in singleplayer.",
                    x, y, width, Ui.WARN);
            y += 4;
        }
        if (selectedRecipes.isEmpty()) {
            return wrapped(gfx, "Nothing to unlock: no recipe makes this item.", x, y, width, Ui.TEXT);
        }

        Set<String> gameRecipes = VanillaRecipeLoader.gameRecipeIds();
        int shown = 0;
        for (RecipeNode recipe : selectedRecipes) {
            if (shown++ >= 12) break;
            String title = RecipeView.categoryName(recipe.categoryId()) + "  " + recipe.id();
            gfx.text(font, Ui.fit(font, title, width), x, y, Ui.ACCENT);
            y += LINE + 1;

            AvailabilityResult result = engine.evaluateFully(recipe, ctx);
            if (recipe.station().isPresent()) {
                String station = ItemStacks.name(recipe.station().get().id());
                boolean near = ctx.nearbyBlockIds().contains(recipe.station().get().id());
                y = check(gfx, near ? Ui.GOOD : Ui.BAD, near ? station + " nearby" : "Needs a " + station + " nearby", x, y, width);
            } else {
                y = check(gfx, Ui.GOOD, "No workstation needed", x, y, width);
            }

            String base = ClientContext.baseRecipeId(recipe.id());
            if (context.progressKnown() && gameRecipes.contains(base)) {
                boolean locked = context.isRecipeLocked(base);
                String text = locked
                        ? "Not in your recipe book yet" + (ctx.limitedCrafting() ? ", crafting is blocked until then" : "")
                        : "In your recipe book";
                y = check(gfx, !locked ? Ui.GOOD : ctx.limitedCrafting() ? Ui.BAD : Ui.WARN, text, x, y, width);
                if (locked) {
                    for (String advancement : context.advancementsUnlocking(base)) {
                        boolean done = ctx.advancements().contains(advancement);
                        y = wrapped(gfx, "Unlocked by: " + context.advancementTitle(advancement)
                                + (done ? " (done)" : ""), x + 8, y, width - 8, Ui.MUTED);
                    }
                }
            }

            for (Blocker blocker : result.details()) {
                if (blocker.kind() == BlockerKind.MATERIALS || blocker.kind() == BlockerKind.STATION) continue;
                y = check(gfx, Ui.BAD, blocker.message(), x, y, width);
            }
            var missing = result.missingMaterials();
            y = check(gfx, missing.isEmpty() ? Ui.GOOD : Ui.WARN, missing.isEmpty() ? "You have all materials"
                    : "Missing " + missing.size() + (missing.size() == 1 ? " material" : " materials"), x, y, width);
            y += 8;
        }
        return y;
    }

    /** A checklist line. Green means fine, yellow is worth knowing, red blocks crafting. */
    private int check(GuiGraphicsExtractor gfx, int color, String text, int x, int y, int width) {
        dot(gfx, x, y, color);
        return wrapped(gfx, text, x + 8, y, width - 8, color == Ui.GOOD ? Ui.TEXT : color);
    }

    // -- Notes tab -------------------------------------------------------------

    private int drawNotesTab(GuiGraphicsExtractor gfx, int x, int y, int right, int mouseX, int mouseY) {
        int width = right - x;
        ItemStack stack = ItemStacks.of(selectedEntry);

        List<InfoPage> pages = AtlasApi.get().infoRegistry().pagesFor(selectedEntry, AtlasClient.tags());
        for (InfoPage page : pages) {
            y = heading(gfx, page.title().isEmpty() ? "Info" : page.title(), x, y);
            for (String line : page.lines()) {
                y = wrapped(gfx, line, x, y, width, Ui.TEXT);
            }
            if (!page.source().isEmpty()) {
                gfx.text(font, "from " + page.source().replace("resourcepack:", "pack "), x, y, Ui.MUTED);
                y += LINE;
            }
            y += 6;
        }

        y = heading(gfx, "Your notes", x, y);
        NoteStore notes = AtlasClient.notes();
        List<String> list = notes.notes(selectedEntry);
        if (list.isEmpty()) {
            y = wrapped(gfx, "No notes yet. Type below and press Enter.", x, y, width, Ui.MUTED);
        }
        for (int i = 0; i < list.size(); i++) {
            final int index = i;
            ui.button(gfx, font, "x", right - 12, y - 1, 0x55603030, Ui.BAD, mouseX, mouseY, () -> {
                notes.remove(selectedEntry, index);
                AtlasClient.saveAll();
            });
            y = wrapped(gfx, "- " + list.get(i), x, y, width - 18, Ui.TEXT) + 2;
        }
        y += 6;

        y = heading(gfx, "Details", x, y);
        gfx.text(font, selectedEntry.id(), x, y, Ui.MUTED);
        y += LINE;
        gfx.text(font, "From: " + ItemStacks.pretty(selectedEntry.namespace()), x, y, Ui.TEXT);
        y += LINE;
        if (!stack.isEmpty()) {
            gfx.text(font, "Stack size: " + stack.getMaxStackSize(), x, y, Ui.TEXT);
            y += LINE;
            if (stack.getMaxDamage() > 0) {
                gfx.text(font, "Durability: " + stack.getMaxDamage(), x, y, Ui.TEXT);
                y += LINE;
            }
            gfx.text(font, "Rarity: " + ItemStacks.pretty(stack.getRarity().name().toLowerCase(Locale.ROOT)), x, y, Ui.TEXT);
            y += LINE;
        }
        var tags = new TreeSet<>(AtlasClient.tags().tagsOf(selectedEntry.id()));
        if (!tags.isEmpty()) {
            y = wrapped(gfx, "Tags: " + String.join(", ", tags.stream().map(t -> "#" + t).toList()),
                    x, y, width, Ui.MUTED);
        }
        y += 6;
        return y;
    }

    private void addNote() {
        if (selectedEntry == null || noteBox.getValue().isBlank()) return;
        AtlasClient.notes().add(selectedEntry, noteBox.getValue());
        AtlasClient.saveAll();
        noteBox.setValue("");
        showFeedback("Note saved");
    }

    // -- Plans panel -----------------------------------------------------------

    private void drawPlansPanel(GuiGraphicsExtractor gfx, int x, int mouseX, int mouseY) {
        int right = width - 6;
        int y = HEADER_HEIGHT + 4;
        gfx.text(font, "Build plans", x, y, Ui.HEADER);
        y += LINE + 4;

        PinnedPlanManager plans = AtlasClient.buildPlans();
        if (plans.plans().isEmpty()) {
            wrapped(gfx, "No plans yet. Open an item, go to the Tree tab and press \"Pin plan\". "
                    + "Pinned plans track what you still need and appear next to your inventory.",
                    x, y, right - x, Ui.TEXT);
            return;
        }

        contentTop = y;
        contentBottom = height - 24;
        gfx.enableScissor(x - 2, contentTop, width, contentBottom);
        ui.clip(contentTop, contentBottom);
        int start = y - rightScroll;
        y = start;
        for (var pinned : List.copyOf(plans.plans())) {
            CraftingPlan plan = AtlasClient.insights().plan(pinned.target(), pinned.count());
            ui.item(gfx, font, ItemStacks.of(pinned.target()), x, y, null);
            String name = pinned.count() + "x " + ItemStacks.name(pinned.target().id());
            gfx.text(font, Ui.fit(font, name, right - x - 70), x + 20, y + 1, Ui.TEXT);
            String state = plan.done() ? "ready" : Math.round(plan.progress() * 100) + "%";
            gfx.text(font, state, right - font.width(state) - 4, y + 1, plan.done() ? Ui.GOOD : Ui.WARN);
            Ui.progress(gfx, x + 20, y + 12, right - x - 24, plan.progress());
            y += 18;

            int ix = x + 20;
            for (var material : plan.missingMaterials()) {
                if (ix + 18 > right) break;
                ui.item(gfx, font, ItemStacks.of(material.item()), ix, y, String.valueOf(material.missing()));
                ix += 18;
            }
            if (!plan.missingMaterials().isEmpty()) y += 18;

            int bx = x + 20;
            bx += ui.button(gfx, font, "Open", bx, y, false, mouseX, mouseY, () -> {
                showPlans = false;
                activeTab = DeepModeTab.TREE;
                navigate(pinned.target());
            }) + 2;
            bx += ui.button(gfx, font, "-", bx, y, false, mouseX, mouseY, () -> {
                if (pinned.count() > 1) plans.pin(pinned.target(), pinned.count() - 1);
            }) + 2;
            bx += ui.button(gfx, font, "+", bx, y, false, mouseX, mouseY,
                    () -> plans.pin(pinned.target(), pinned.count() + 1)) + 2;
            ui.button(gfx, font, "Remove", bx, y, 0x55603030, Ui.BAD, mouseX, mouseY, () -> {
                plans.unpin(pinned.target());
                AtlasClient.saveAll();
            });
            y += 20;
        }
        ui.unclip();
        gfx.disableScissor();
        contentHeight = y - start;
        drawScrollBar(gfx);
    }

    // -- Filter + Help ---------------------------------------------------------

    private void applyFilter() {
        String filterValue = filterOptions.get(activeFilter);
        String current = searchBox.getValue().replaceAll("@\\S+\\s*", "").trim();
        if (activeFilter == 0 || "Saved".equals(filterValue) || "Plans".equals(filterValue)) {
            searchBox.setValue(current);
            onSearchChanged(current);
            return;
        }
        searchBox.setValue(filterValue + " " + current);
    }

    private void drawHelpOverlay(GuiGraphicsExtractor gfx) {
        int ow = Math.min(width - 40, 300);
        int oh = Math.min(height - 40, 210);
        int ox = (width - ow) / 2;
        int oy = (height - oh) / 2;
        gfx.fill(ox, oy, ox + ow, oy + oh, 0xEE101018);
        gfx.outline(ox, oy, ow, oh, DIVIDER_COLOR);

        int x = ox + 8;
        int y = oy + 8;
        int w = ow - 16;
        gfx.text(font, "Atlas Deep Mode", x, y, Ui.HEADER);
        y += LINE + 3;
        y = wrapped(gfx, "Pick an item on the left. The tabs on the right answer one question each:", x, y, w, Ui.TEXT);
        y = wrapped(gfx, "Craft: how to make it and what is missing. Uses: what it goes into. "
                + "Sources: every way to get it. Tree: the full crafting tree with material totals. "
                + "Alternatives: other recipes and substitutes. Unlocks: why you can not make it yet. "
                + "Notes: details, pack tips and your own notes.", x, y, w, Ui.TEXT);
        y += 3;
        y = wrapped(gfx, "Search: @mod, $tag, #tooltip. Click any item in a recipe to open it, "
                + "Backspace goes back. Pin a plan in the Tree tab to track it next to your inventory.",
                x, y, w, Ui.TEXT);
        y += 3;
        wrapped(gfx, "Keys: U opens Atlas, O toggles Quick Mode, Esc closes. Click to close this help.",
                x, y, w, Ui.MUTED);
    }

    // -- Input handling --------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (showHelp) {
            showHelp = false;
            return true;
        }
        double mouseX = event.x();
        double mouseY = event.y();
        // Atlas' own buttons first, so widgets added by other mods can not swallow them
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && ui.click(mouseX, mouseY)) return true;
        if (super.mouseClicked(event, doubleClick)) return true;

        int dividerX = panelDividerX();
        int startY = gridTop();
        int startX = 6;
        if (mouseX >= startX && mouseX < dividerX && mouseY >= startY && mouseY < height) {
            int col = (int) (mouseX - startX) / ITEM_SIZE;
            int row = (int) (mouseY - startY) / ITEM_SIZE;
            int idx = (row + scrollOffset) * gridColumns + col;
            if (col < gridColumns && idx >= 0 && idx < searchResults.size()) {
                EntryKey entry = searchResults.get(idx);
                if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                    // Right click goes straight to the uses
                    navigate(entry);
                    activeTab = DeepModeTab.USE;
                    return true;
                }
                navigate(entry);
                showPlans = false;
                giveItemIfCreative(entry, event);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (noteBox.isVisible() && noteBox.isFocused() && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)) {
            addNote();
            return true;
        }
        boolean typing = searchBox.isFocused() || noteBox.isFocused();
        if (!typing && event.key() == InputConstants.KEY_BACKSPACE) {
            goBack();
            return true;
        }
        if (!typing && AtlasKeyBindings.focusSearch != null && AtlasKeyBindings.focusSearch.matches(event)) {
            setFocused(searchBox);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int filterY = HEADER_HEIGHT + SEARCH_HEIGHT + 6;
        int dividerX = panelDividerX();
        if (mouseX < dividerX && mouseY >= filterY && mouseY < filterY + 18) {
            int maxScroll = Math.max(0, totalFilterWidth - dividerX + 12);
            filterScrollOffset = Math.max(0, Math.min(maxScroll, filterScrollOffset + (int) (scrollY * -8)));
            return true;
        }
        if (mouseX < dividerX) {
            int rows = (searchResults.size() + gridColumns - 1) / gridColumns;
            int visibleRows = (height - gridTop() - 4) / ITEM_SIZE;
            scrollOffset = Math.max(0, Math.min(Math.max(0, rows - visibleRows), scrollOffset - (int) scrollY));
            return true;
        }
        rightScroll = Math.max(0, rightScroll - (int) (scrollY * 18));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    // -- Creative Mode ---------------------------------------------------------

    private void giveItemIfCreative(EntryKey entry, MouseButtonEvent event) {
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
        // Set locally first so the item appears immediately, then sync to server
        mc.player.getInventory().setItem(freeSlot, stack.copy());
        int containerSlot = freeSlot < 9 ? freeSlot + 36 : freeSlot;
        mc.gameMode.handleCreativeModeItemAdd(stack, containerSlot);
        showFeedback("Got " + stack.getHoverName().getString() + " x" + stack.getCount());
        LOGGER.info("[Atlas] Creative give: {} x{} -> slot {}", entry.id(), stack.getCount(), freeSlot);
    }
}
