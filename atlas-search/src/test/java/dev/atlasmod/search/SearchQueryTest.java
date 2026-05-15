package dev.atlasmod.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SearchQueryTest {

    @Test
    void parsesExtendedSyntax() {
        SearchQuery query = SearchQuery.parse("bronze @mymod #heated $c:ingots/bronze >machine =mymod:alloy_forge ~unlocked !hidden *renewable");

        assertEquals("bronze", query.textQuery());
        assertEquals("mymod", query.modFilter());
        assertEquals("heated", query.tooltipFilter());
        assertEquals("c:ingots/bronze", query.tagFilter());
        assertEquals("machine", query.sourceFilter());
        assertEquals("mymod:alloy_forge", query.stationFilter());
        assertTrue(query.onlyUnlocked());
        assertTrue(query.includeHidden());
        assertTrue(query.onlyRenewable());
    }
}
