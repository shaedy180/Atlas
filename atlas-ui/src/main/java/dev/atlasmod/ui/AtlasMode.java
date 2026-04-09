package dev.atlasmod.ui;

/**
 * Atlas operates in two UI modes to avoid a single overloaded interface.
 */
public enum AtlasMode {
    /**
     * Quick Mode: lightweight overlay in the inventory screen.
     * Search, favorites, recipe/uses, transfer buttons.
     */
    QUICK,

    /**
     * Deep Mode: fullscreen Atlas screen with navigation, recipe graph,
     * context sidebar, and tabs (Craft, Use, Sources, Alternatives, Unlocks, Notes).
     */
    DEEP
}
