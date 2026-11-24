package dev.atlasmod.client.compat;

import dev.atlasmod.core.entry.IngredientKey;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.crafting.display.SlotDisplay;

/** Minecraft 26.2: screens moved to {@code Minecraft.gui}. */
public final class McCompat {

    private McCompat() {}

    public static void openScreen(Screen screen) {
        Minecraft.getInstance().gui.setScreen(screen);
    }

    public static Screen currentScreen() {
        return Minecraft.getInstance().gui.screen();
    }

    public static IngredientKey tagIngredient(SlotDisplay.TagSlotDisplay slot) {
        return IngredientKey.tag(slot.tag().location().toString());
    }

    public static String advancementTitle(DisplayInfo display) {
        return display.getTitle().getString();
    }
}
