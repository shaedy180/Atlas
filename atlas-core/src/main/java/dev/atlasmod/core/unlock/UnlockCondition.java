package dev.atlasmod.core.unlock;

import java.util.Objects;

/**
 * Describes a condition that must be met for a recipe to be available/visible.
 * Central to the Availability Engine ("Why can't I do this yet?").
 */
public sealed interface UnlockCondition {

    /** Human-readable explanation of why this blocks availability. */
    String explain();

    record RequiresStation(String stationId) implements UnlockCondition {
        public RequiresStation { Objects.requireNonNull(stationId); }
        @Override public String explain() { return "Missing workstation: " + stationId; }
    }

    record RequiresDimension(String dimensionId) implements UnlockCondition {
        public RequiresDimension { Objects.requireNonNull(dimensionId); }
        @Override public String explain() { return "Requires dimension: " + dimensionId; }
    }

    record RequiresBiome(String biomeTagOrId) implements UnlockCondition {
        public RequiresBiome { Objects.requireNonNull(biomeTagOrId); }
        @Override public String explain() { return "Requires biome: " + biomeTagOrId; }
    }

    record RequiresAdvancement(String advancementId) implements UnlockCondition {
        public RequiresAdvancement { Objects.requireNonNull(advancementId); }
        @Override public String explain() { return "Requires advancement: " + advancementId; }
    }

    record RequiresProgression(String stageKey) implements UnlockCondition {
        public RequiresProgression { Objects.requireNonNull(stageKey); }
        @Override public String explain() { return "Hidden by progression stage: " + stageKey; }
    }

    record RequiresCatalyst(String catalystId) implements UnlockCondition {
        public RequiresCatalyst { Objects.requireNonNull(catalystId); }
        @Override public String explain() { return "Missing catalyst: " + catalystId; }
    }

    record Custom(String reason) implements UnlockCondition {
        public Custom { Objects.requireNonNull(reason); }
        @Override public String explain() { return reason; }
    }
}
