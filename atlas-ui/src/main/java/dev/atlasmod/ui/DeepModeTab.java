package dev.atlasmod.ui;

/**
 * The tabs shown in the fullscreen Atlas screen.
 * Each tab represents a different view of the selected entry.
 */
public enum DeepModeTab {
    CRAFT("Craft"),
    USE("Uses"),
    SOURCES("Sources"),
    TREE("Tree"),
    ALTERNATIVES("Alternatives"),
    UNLOCKS("Unlocks"),
    NOTES("Notes");

    private final String label;

    DeepModeTab(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
