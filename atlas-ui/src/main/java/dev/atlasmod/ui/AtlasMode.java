package dev.atlasmod.ui;

/**
 * Atlas operates in two UI modes to avoid a single overloaded interface.
 */
public enum AtlasMode {
    /**
     * Quick Mode: lightweight overlay in the inventory screen.
     * Search, saved items, recipe preview, recipe paste and the plan tracker.
     */
    QUICK,

    /**
     * Deep Mode: fullscreen Atlas screen with the item list and the tabs
     * Craft, Uses, Sources, Tree, Alternatives, Unlocks and Notes.
     */
    DEEP
}
