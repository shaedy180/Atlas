package dev.atlasmod.client.gui;

import dev.atlasmod.client.ItemStacks;
import dev.atlasmod.core.entry.EntryKey;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Small immediate mode helper: things are drawn and registered as clickable in
 * the same call, and clicks are matched against what was drawn last frame.
 */
public final class Ui {

    public static final int TEXT = 0xFFCCCCCC;
    public static final int HEADER = 0xFFE0E0FF;
    public static final int ACCENT = 0xFF8888FF;
    public static final int MUTED = 0xFF888888;
    public static final int GOOD = 0xFF7CDB7C;
    public static final int WARN = 0xFFE8C060;
    public static final int BAD = 0xFFE07070;
    public static final int CHIP = 0x55303040;
    public static final int CHIP_HOVER = 0x88505070;
    public static final int CHIP_ACTIVE = 0x883A5A8A;

    private record Hit(int x, int y, int w, int h, Runnable onClick, ItemStack stack, List<Component> tooltip) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private Consumer<EntryKey> navigator = e -> {};
    private int clipTop = Integer.MIN_VALUE;
    private int clipBottom = Integer.MAX_VALUE;

    /** Starts a frame. Item clicks call the navigator. */
    public void begin(Consumer<EntryKey> navigator) {
        hits.clear();
        this.navigator = navigator;
        unclip();
    }

    /** Only things inside this vertical band can be clicked, used for scrolled areas. */
    public void clip(int top, int bottom) {
        clipTop = top;
        clipBottom = bottom;
    }

    public void unclip() {
        clipTop = Integer.MIN_VALUE;
        clipBottom = Integer.MAX_VALUE;
    }

    private void add(Hit hit) {
        if (hit.y + hit.h <= clipTop || hit.y >= clipBottom) return;
        hits.add(hit);
    }

    /** A flat text button. Returns its width. */
    public int button(GuiGraphicsExtractor gfx, Font font, String label, int x, int y, boolean active,
                      int mouseX, int mouseY, Runnable action) {
        return button(gfx, font, label, x, y, active ? CHIP_ACTIVE : CHIP, active ? HEADER : TEXT,
                mouseX, mouseY, action);
    }

    public int button(GuiGraphicsExtractor gfx, Font font, String label, int x, int y, int bg, int fg,
                      int mouseX, int mouseY, Runnable action) {
        int w = font.width(label) + 8;
        int h = 12;
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h
                && mouseY >= clipTop && mouseY < clipBottom;
        gfx.fill(x, y, x + w, y + h, hover ? CHIP_HOVER : bg);
        gfx.text(font, label, x + 4, y + 2, fg);
        add(new Hit(x, y, w, h, action, ItemStack.EMPTY, List.of()));
        return w;
    }

    /** Draws an item that opens its own page when clicked. */
    public void item(GuiGraphicsExtractor gfx, Font font, ItemStack stack, int x, int y, String countLabel) {
        if (stack.isEmpty()) return;
        gfx.item(stack, x, y);
        if (countLabel != null) gfx.itemDecorations(font, stack, x, y, countLabel);
        EntryKey entry = ItemStacks.entryOf(stack);
        add(new Hit(x, y, 16, 16, entry == null ? null : () -> navigator.accept(entry), stack, List.of()));
    }

    /** A plain hover tooltip for an area. */
    public void tooltip(int x, int y, int w, int h, List<Component> lines) {
        add(new Hit(x, y, w, h, null, ItemStack.EMPTY, lines));
    }

    /** A clickable area without drawing anything. */
    public void area(int x, int y, int w, int h, Runnable action) {
        add(new Hit(x, y, w, h, action, ItemStack.EMPTY, List.of()));
    }

    /** Runs the topmost click action under the mouse. */
    public boolean click(double mouseX, double mouseY) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.onClick != null && hit.contains(mouseX, mouseY)) {
                hit.onClick.run();
                return true;
            }
        }
        return false;
    }

    /** Shows the tooltip of the topmost hovered entry. */
    public void tooltips(GuiGraphicsExtractor gfx, Font font, int mouseX, int mouseY) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!hit.contains(mouseX, mouseY)) continue;
            if (!hit.stack.isEmpty()) {
                gfx.setTooltipForNextFrame(font, hit.stack, mouseX, mouseY);
                return;
            }
            if (!hit.tooltip.isEmpty()) {
                gfx.setComponentTooltipForNextFrame(font, hit.tooltip, mouseX, mouseY);
                return;
            }
        }
    }

    /** Draws a thin progress bar. */
    public static void progress(GuiGraphicsExtractor gfx, int x, int y, int w, double value) {
        gfx.fill(x, y, x + w, y + 3, 0x66000000);
        int filled = (int) Math.round(w * Math.max(0, Math.min(1, value)));
        gfx.fill(x, y, x + filled, y + 3, value >= 1 ? GOOD : WARN);
    }

    /** Cuts text to fit the width, adding "..." when shortened. */
    public static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        String dots = "...";
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + dots) > width) end--;
        return text.substring(0, end) + dots;
    }
}
