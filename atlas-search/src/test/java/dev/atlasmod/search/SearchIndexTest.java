package dev.atlasmod.search;

import dev.atlasmod.core.entry.EntryKey;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchIndexTest {

    @Test
    void filtersBySourceStationAndRenewable() {
        SearchIndex index = new SearchIndex();
        EntryKey entry = new EntryKey("item", "mymod:bronze_ingot");
        index.add(
                entry,
                "Bronze Ingot",
                Set.of("c:ingots/bronze"),
                "Heated alloy",
                Set.of("machine"),
                Set.of("mymod:alloy_forge"),
                true
        );

        assertEquals(
                Set.of(entry),
                Set.copyOf(index.search(SearchQuery.parse("bronze >machine =mymod:alloy_forge *renewable")))
        );
    }
}
