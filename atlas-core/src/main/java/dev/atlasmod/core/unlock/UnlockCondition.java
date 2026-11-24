package dev.atlasmod.core.unlock;

import dev.atlasmod.core.availability.ContextSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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

    /** Biome id, or a biome tag when prefixed with {@code #}. */
    record RequiresBiome(String biomeTagOrId) implements UnlockCondition {
        public RequiresBiome { Objects.requireNonNull(biomeTagOrId); }
        @Override public String explain() { return "Requires biome: " + biomeTagOrId; }
    }

    record RequiresAdvancement(String advancementId) implements UnlockCondition {
        public RequiresAdvancement { Objects.requireNonNull(advancementId); }
        @Override public String explain() { return "Requires advancement: " + advancementId; }
    }

    /** A progression stage. Met when the key is a completed advancement or an active stage. */
    record RequiresProgression(String stageKey) implements UnlockCondition {
        public RequiresProgression { Objects.requireNonNull(stageKey); }
        @Override public String explain() { return "Hidden by progression stage: " + stageKey; }
    }

    record RequiresCatalyst(String catalystId) implements UnlockCondition {
        public RequiresCatalyst { Objects.requireNonNull(catalystId); }
        @Override public String explain() { return "Missing catalyst: " + catalystId; }
    }

    /** Always blocking. Use {@link Check} when the condition can be tested. */
    record Custom(String reason) implements UnlockCondition {
        public Custom { Objects.requireNonNull(reason); }
        @Override public String explain() { return reason; }
    }

    /** A condition decided by code, for example a mod's own progression system. */
    record Check(String reason, Predicate<ContextSnapshot> test) implements UnlockCondition {
        public Check {
            Objects.requireNonNull(reason);
            Objects.requireNonNull(test);
        }
        @Override public String explain() { return reason; }
    }

    /** Every listed condition must be met. */
    record AllOf(List<UnlockCondition> conditions) implements UnlockCondition {
        public AllOf { conditions = List.copyOf(conditions); }
        @Override public String explain() {
            return conditions.stream().map(UnlockCondition::explain).collect(Collectors.joining(", "));
        }
    }

    /** Combines two conditions, flattening nested {@link AllOf}s. Either side may be null. */
    static UnlockCondition and(UnlockCondition a, UnlockCondition b) {
        if (a == null) return b;
        if (b == null) return a;
        var list = new ArrayList<UnlockCondition>();
        for (UnlockCondition c : List.of(a, b)) {
            if (c instanceof AllOf all) list.addAll(all.conditions());
            else list.add(c);
        }
        return new AllOf(list);
    }
}
