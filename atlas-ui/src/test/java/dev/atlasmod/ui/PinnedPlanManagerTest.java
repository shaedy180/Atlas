package dev.atlasmod.ui;

import dev.atlasmod.core.entry.EntryKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PinnedPlanManagerTest {

    @Test
    void readsTheExistingFileFormat(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("atlas_pins.txt");
        Files.writeString(file, "# saved\nitem|minecraft:diamond|3\nbroken line\nitem|minecraft:stick|0\n");

        PinnedPlanManager manager = new PinnedPlanManager();
        manager.load(file);

        assertEquals(1, manager.plans().size());
        assertEquals(3, manager.count(new EntryKey("item", "minecraft:diamond")));
        manager.pin(new EntryKey("item", "minecraft:diamond"), 5);
        manager.save(file);
        assertEquals("item|minecraft:diamond|5", Files.readString(file).strip());
    }
}
