package dev.atlasmod.ui;

import dev.atlasmod.core.entry.EntryKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NoteStoreTest {

    private static final EntryKey DIAMOND = new EntryKey("item", "minecraft:diamond");

    @Test
    void roundTripKeepsPipesBackslashesAndNewlines(@TempDir Path dir) throws Exception {
        NoteStore store = new NoteStore();
        store.add(DIAMOND, "Mine at y -59 | branch mining");
        store.add(DIAMOND, "path C:\\games\nsecond line");
        store.add(DIAMOND, "   ");
        store.save(dir.resolve("notes.txt"));

        NoteStore loaded = new NoteStore();
        loaded.load(dir.resolve("notes.txt"));
        assertEquals(List.of("Mine at y -59 | branch mining", "path C:\\games\nsecond line"), loaded.notes(DIAMOND));
    }

    @Test
    void removeDropsEmptyEntries() {
        NoteStore store = new NoteStore();
        store.add(DIAMOND, "one");
        store.remove(DIAMOND, 0);
        assertFalse(store.hasNotes(DIAMOND));
    }
}
