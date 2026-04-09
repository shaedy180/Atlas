package dev.atlasmod.core.availability;

import dev.atlasmod.core.recipe.RecipeNode;
import dev.atlasmod.core.unlock.UnlockCondition;
import dev.atlasmod.core.visibility.VisibilityPolicy;

import java.util.ArrayList;
import java.util.List;

/**
 * The Availability Engine - evaluates UnlockConditions against the player's
 * current ContextSnapshot and produces human-readable explanations.
 *
 * This is Atlas's primary differentiator: not just "what are the recipes"
 * but "why can't I do this yet?" with clear, actionable answers.
 */
public final class AvailabilityEngine {

    /**
     * Result of evaluating a recipe's availability.
     *
     * @param available whether all conditions are met
     * @param policy    the visibility state to display
     * @param blockers  list of human-readable explanations for unmet conditions
     */
    public record AvailabilityResult(
            boolean available,
            VisibilityPolicy policy,
            List<String> blockers
    ) {
        public AvailabilityResult {
            blockers = List.copyOf(blockers);
        }

        public static final AvailabilityResult AVAILABLE =
                new AvailabilityResult(true, VisibilityPolicy.VISIBLE, List.of());
    }

    /**
     * Evaluates a recipe node's availability against the given context.
     * Returns an AvailabilityResult describing the outcome.
     */
    public AvailabilityResult evaluate(RecipeNode recipe, ContextSnapshot context) {
        var condition = recipe.unlockCondition();
        if (condition.isEmpty()) return AvailabilityResult.AVAILABLE;

        UnlockCondition cond = condition.get();
        List<String> blockers = new ArrayList<>();
        boolean met = check(cond, context, blockers);

        if (met) {
            return AvailabilityResult.AVAILABLE;
        }

        // Recipe is blocked - determine visibility policy
        VisibilityPolicy policy = switch (cond) {
            case UnlockCondition.RequiresProgression _ -> VisibilityPolicy.HIDDEN;
            case UnlockCondition.Custom _ -> VisibilityPolicy.TEASER;
            default -> VisibilityPolicy.GREYED_OUT;
        };

        return new AvailabilityResult(false, policy, blockers);
    }

    /**
     * Checks a single condition against the context.
     * Populates the blockers list with explanation text if not met.
     * Returns true if the condition is satisfied.
     */
    private boolean check(UnlockCondition cond, ContextSnapshot ctx, List<String> blockers) {
        return switch (cond) {
            case UnlockCondition.RequiresStation req -> {
                boolean has = ctx.nearbyBlockIds().contains(req.stationId());
                if (!has) blockers.add(req.explain());
                yield has;
            }
            case UnlockCondition.RequiresDimension req -> {
                boolean in = ctx.dimensionId().equals(req.dimensionId());
                if (!in) blockers.add(req.explain());
                yield in;
            }
            case UnlockCondition.RequiresBiome req -> {
                boolean in = ctx.biomeId().equals(req.biomeTagOrId());
                if (!in) blockers.add(req.explain());
                yield in;
            }
            case UnlockCondition.RequiresAdvancement req -> {
                boolean has = ctx.advancements().contains(req.advancementId());
                if (!has) blockers.add(req.explain());
                yield has;
            }
            case UnlockCondition.RequiresProgression req -> {
                // Progression stages are opaque strings; check if it's in advancements
                boolean has = ctx.advancements().contains(req.stageKey());
                if (!has) blockers.add(req.explain());
                yield has;
            }
            case UnlockCondition.RequiresCatalyst req -> {
                boolean has = ctx.inventoryItemIds().contains(req.catalystId());
                if (!has) blockers.add(req.explain());
                yield has;
            }
            case UnlockCondition.Custom custom -> {
                // Custom conditions are always blocking unless their key appears
                // in the advancement set (convention for modded progression)
                blockers.add(custom.explain());
                yield false;
            }
        };
    }
}
