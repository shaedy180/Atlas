package dev.atlasmod.core.visibility;

/**
 * Controls how a recipe or entry appears in Atlas based on progression.
 */
public enum VisibilityPolicy {
    /** Always visible with full details. */
    VISIBLE,
    /** Shown but greyed out — requirements displayed. */
    GREYED_OUT,
    /** Completely hidden from results. */
    HIDDEN,
    /** Shown as a teaser (name visible, recipe obscured). */
    TEASER
}
