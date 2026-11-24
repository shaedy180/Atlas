package dev.atlasmod.client;

import dev.atlasmod.client.gui.Ui;
import dev.atlasmod.core.plan.CraftingPlan;
import dev.atlasmod.ui.DeepModeTab;
import dev.atlasmod.ui.PinnedPlanManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/**
 * The pinned build plans, drawn in the free space left of an open inventory.
 * Shows progress and the materials still missing, updated as items come in.
 */
final class PlanTracker {

    private static final int MAX_PLANS = 4;
    private static final int MAX_MATERIALS = 3;
    private static final long REFRESH_MS = 1000;

    private record Entry(PinnedPlanManager.PinnedPlan pinned, CraftingPlan plan) {}

    private static List<Entry> cache = List.of();
    private static long lastRefresh;
    private static List<PinnedPlanManager.PinnedPlan> lastPlans = List.of();

    private PlanTracker() {}

    static void draw(Ui ui, GuiGraphicsExtractor gfx, Screen screen, int mouseX, int mouseY, int panelLeft) {
        PinnedPlanManager plans = AtlasClient.buildPlans();
        if (plans.plans().isEmpty()) return;

        // Container screens are centered and usually 176 wide. Use the space left of them.
        int free = screen.width / 2 - 92;
        int width = Math.min(150, free - 8);
        if (width < 90) return;

        Font font = screen.getFont();
        List<Entry> entries = entries(plans);
        int x = 4;
        int y = 4;
        int height = 14;
        for (Entry e : entries) height += 20 + Math.min(MAX_MATERIALS, e.plan().missingMaterials().size()) * 17;
        gfx.fill(x, y, x + width, y + height, 0xBB101018);
        gfx.outline(x, y, width, height, 0xFF404060);
        gfx.text(font, "Plans", x + 4, y + 3, Ui.HEADER);
        y += 14;

        for (Entry e : entries) {
            var pinned = e.pinned();
            CraftingPlan plan = e.plan();
            ui.item(gfx, font, ItemStacks.of(pinned.target()), x + 3, y, pinned.count() > 1 ? String.valueOf(pinned.count()) : null);
            String state = plan.done() ? "ready" : Math.round(plan.progress() * 100) + "%";
            int stateW = font.width(state);
            String name = Ui.fit(font, ItemStacks.name(pinned.target().id()), width - stateW - 30);
            gfx.text(font, name, x + 22, y + 1, Ui.TEXT);
            gfx.text(font, state, x + width - stateW - 4, y + 1, plan.done() ? Ui.GOOD : Ui.WARN);
            Ui.progress(gfx, x + 22, y + 11, width - 26, plan.progress());
            ui.area(x + 22, y, width - 26, 16, () -> QuickModeOverlay.openDeepMode(pinned.target(), DeepModeTab.TREE));
            y += 20;

            int shown = 0;
            for (var material : plan.missingMaterials()) {
                if (shown++ >= MAX_MATERIALS) break;
                ui.item(gfx, font, ItemStacks.of(material.item()), x + 12, y, null);
                String text = material.have() + "/" + material.needed() + " " + ItemStacks.name(material.item().id());
                gfx.text(font, Ui.fit(font, text, width - 36), x + 31, y + 4, Ui.MUTED);
                y += 17;
            }
        }
    }

    private static List<Entry> entries(PinnedPlanManager plans) {
        long now = System.currentTimeMillis();
        if (now - lastRefresh < REFRESH_MS && plans.plans().equals(lastPlans)) return cache;
        lastRefresh = now;
        lastPlans = List.copyOf(plans.plans());
        List<Entry> result = new ArrayList<>();
        for (var pinned : plans.plans()) {
            if (result.size() >= MAX_PLANS) break;
            result.add(new Entry(pinned, AtlasClient.insights().plan(pinned.target(), pinned.count())));
        }
        cache = result;
        return result;
    }
}
