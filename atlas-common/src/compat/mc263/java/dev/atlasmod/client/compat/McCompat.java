package dev.atlasmod.client.compat;

import dev.atlasmod.core.entry.IngredientKey;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.crafting.display.SlotDisplay;

/** Minecraft 26.3: tag slots hold a holder set and advancement titles are a record field. */
public final class McCompat {

    private McCompat() {}

    public static void openScreen(Screen screen) {
        Minecraft.getInstance().gui.setScreen(screen);
    }

    public static Screen currentScreen() {
        return Minecraft.getInstance().gui.screen();
    }

    public static IngredientKey tagIngredient(SlotDisplay.TagSlotDisplay slot) {
        // Usually a named tag. A plain list of items shows its first entry.
        var set = slot.tag();
        var key = set.unwrapKey();
        if (key.isPresent()) return IngredientKey.tag(key.get().location().toString());
        return set.stream().findFirst()
                .flatMap(holder -> holder.unwrapKey())
                .map(k -> IngredientKey.item(k.identifier().toString()))
                .orElse(IngredientKey.EMPTY);
    }

    public static String advancementTitle(DisplayInfo display) {
        return display.title().getString();
    }
}
