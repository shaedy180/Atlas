package dev.atlasmod.core.availability;

import dev.atlasmod.core.entry.IngredientKey;
import dev.atlasmod.core.entry.TagResolver;
import dev.atlasmod.core.plan.Inventory;
import dev.atlasmod.core.recipe.RecipeNode;
import dev.atlasmod.core.unlock.UnlockCondition;
import dev.atlasmod.core.visibility.VisibilityPolicy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Evaluates a recipe against the player's current context and explains, in plain
 * words, why it can not be made yet: missing workstation, wrong dimension or biome,
 * locked progression, or missing materials.
 */
public final class AvailabilityEngine {

    /** What kind of thing is in the way. The UI picks colors and ordering from this. */
    public enum BlockerKind { STATION, DIMENSION, BIOME, PROGRESSION, CATALYST, MATERIALS, OTHER }

    /**
     * One reason a recipe can not be made right now.
     *
     * @param subject id of the station, dimension, item or advancement involved (may be empty)
     * @param amount  missing amount for {@link BlockerKind#MATERIALS}, otherwise 0
     */
    public record Blocker(BlockerKind kind, String subject, int amount, String message) {}

    /**
     * Result of evaluating a recipe's availability.
     *
     * @param available whether all conditions are met (materials aside)
     * @param policy    the visibility state to display
     * @param blockers  explanations for unmet conditions
     * @param details   every blocker, including missing materials
     */
    public record AvailabilityResult(
            boolean available,
            VisibilityPolicy policy,
            List<String> blockers,
            List<Blocker> details
    ) {
        public AvailabilityResult {
            blockers = List.copyOf(blockers);
            details = List.copyOf(details);
        }

        public AvailabilityResult(boolean available, VisibilityPolicy policy, List<String> blockers) {
            this(available, policy, blockers, List.of());
        }

        public static final AvailabilityResult AVAILABLE =
                new AvailabilityResult(true, VisibilityPolicy.VISIBLE, List.of(), List.of());

        /** Conditions met and every material is in the inventory. */
        public boolean craftableNow() {
            return available && missingMaterials().isEmpty();
        }

        public List<Blocker> missingMaterials() {
            return details.stream().filter(b -> b.kind() == BlockerKind.MATERIALS).toList();
        }
    }

    private final TagResolver tags;
    private final Function<String, String> names;

    public AvailabilityEngine() {
        this(TagResolver.NONE, Function.identity());
    }

    /**
     * @param tags  resolves tag ingredients against the inventory
     * @param names turns ids into display names for the messages, tags arrive as "#id"
     */
    public AvailabilityEngine(TagResolver tags, Function<String, String> names) {
        this.tags = Objects.requireNonNull(tags);
        this.names = Objects.requireNonNull(names);
    }

    /**
     * Checks only the recipe's unlock condition, like Atlas 0.2 did.
     */
    public AvailabilityResult evaluate(RecipeNode recipe, ContextSnapshot context) {
        var condition = recipe.unlockCondition();
        if (condition.isEmpty()) return AvailabilityResult.AVAILABLE;

        List<Blocker> blockers = new ArrayList<>();
        if (check(condition.get(), context, blockers)) return AvailabilityResult.AVAILABLE;
        return result(blockers, policyFor(condition.get()));
    }

    /**
     * Full check: station, unlock conditions, recipe book and materials.
     */
    public AvailabilityResult evaluateFully(RecipeNode recipe, ContextSnapshot context) {
        List<Blocker> blockers = new ArrayList<>();
        VisibilityPolicy policy = VisibilityPolicy.VISIBLE;

        recipe.station().ifPresent(station -> {
            String id = station.id();
            if (!context.nearbyBlockIds().contains(id)) {
                String hint = context.count(id) > 0 ? " (you have one, place it)" : "";
                blockers.add(new Blocker(BlockerKind.STATION, id, 0,
                        "Needs a " + names.apply(id) + " nearby" + hint));
            }
        });

        var condition = recipe.unlockCondition();
        if (condition.isPresent() && !check(condition.get(), context, blockers)) {
            policy = policyFor(condition.get());
        }

        if (context.limitedCrafting() && context.lockedRecipes().contains(recipe.id())) {
            blockers.add(new Blocker(BlockerKind.PROGRESSION, recipe.id(), 0,
                    "Recipe not unlocked yet (limited crafting is on)"));
            if (policy == VisibilityPolicy.VISIBLE) policy = VisibilityPolicy.GREYED_OUT;
        }

        Inventory inventory = new Inventory(context.inventory(), tags);
        for (Map.Entry<IngredientKey, Integer> entry : recipe.ingredientCounts().entrySet()) {
            IngredientKey ingredient = entry.getKey();
            int need = entry.getValue();
            int have = takeAny(inventory, ingredient, need);
            if (have < need) {
                String id = ingredient.tagBased() ? "#" + ingredient.id() : ingredient.id();
                blockers.add(new Blocker(BlockerKind.MATERIALS, id, need - have,
                        "Missing " + (need - have) + "x " + displayName(ingredient)));
            }
        }

        return result(blockers, policy);
    }

    private AvailabilityResult result(List<Blocker> blockers, VisibilityPolicy policy) {
        boolean available = blockers.stream().allMatch(b -> b.kind() == BlockerKind.MATERIALS);
        if (blockers.isEmpty()) return AvailabilityResult.AVAILABLE;
        List<String> messages = blockers.stream()
                .filter(b -> b.kind() != BlockerKind.MATERIALS)
                .map(Blocker::message)
                .toList();
        return new AvailabilityResult(available, available ? VisibilityPolicy.VISIBLE : policy, messages, blockers);
    }

    private static VisibilityPolicy policyFor(UnlockCondition condition) {
        return switch (condition) {
            case UnlockCondition.AllOf all -> all.conditions().stream()
                    .map(AvailabilityEngine::policyFor)
                    .max(Comparator.comparingInt(AvailabilityEngine::strictness))
                    .orElse(VisibilityPolicy.GREYED_OUT);
            case UnlockCondition.RequiresProgression _ -> VisibilityPolicy.HIDDEN;
            case UnlockCondition.Custom _ -> VisibilityPolicy.TEASER;
            default -> VisibilityPolicy.GREYED_OUT;
        };
    }

    private static int strictness(VisibilityPolicy policy) {
        return switch (policy) {
            case VISIBLE -> 0;
            case GREYED_OUT -> 1;
            case TEASER -> 2;
            case HIDDEN -> 3;
        };
    }

    private String displayName(IngredientKey ingredient) {
        return names.apply(ingredient.tagBased() ? "#" + ingredient.id() : ingredient.id());
    }

    private static int takeAny(Inventory inventory, IngredientKey ingredient, int need) {
        if (!ingredient.tagBased()) return inventory.take(ingredient.id(), need);
        int taken = 0;
        for (String member : inventory.tags().members(ingredient.id())) {
            if (taken >= need) break;
            taken += inventory.take(member, need - taken);
        }
        return taken;
    }

    /**
     * Checks a single condition against the context.
     * Adds an explanation to the blockers list if not met.
     */
    private boolean check(UnlockCondition cond, ContextSnapshot ctx, List<Blocker> blockers) {
        return switch (cond) {
            case UnlockCondition.RequiresStation req -> test(ctx.nearbyBlockIds().contains(req.stationId()),
                    blockers, BlockerKind.STATION, req.stationId(),
                    "Needs a " + names.apply(req.stationId()) + " nearby");
            case UnlockCondition.RequiresDimension req -> test(ctx.dimensionId().equals(req.dimensionId()),
                    blockers, BlockerKind.DIMENSION, req.dimensionId(),
                    "Only works in " + names.apply(req.dimensionId()));
            case UnlockCondition.RequiresBiome req -> {
                String biome = req.biomeTagOrId();
                boolean in = biome.startsWith("#")
                        ? ctx.biomeTags().contains(biome.substring(1))
                        : ctx.biomeId().equals(biome);
                yield test(in, blockers, BlockerKind.BIOME, biome, "Only works in biome " + names.apply(biome));
            }
            case UnlockCondition.RequiresAdvancement req -> test(ctx.advancements().contains(req.advancementId()),
                    blockers, BlockerKind.PROGRESSION, req.advancementId(),
                    "Needs advancement " + names.apply(req.advancementId()));
            case UnlockCondition.RequiresProgression req -> test(ctx.hasStage(req.stageKey()),
                    blockers, BlockerKind.PROGRESSION, req.stageKey(), req.explain());
            case UnlockCondition.RequiresCatalyst req -> test(ctx.count(req.catalystId()) > 0,
                    blockers, BlockerKind.CATALYST, req.catalystId(),
                    "Needs " + names.apply(req.catalystId()) + " in your inventory");
            case UnlockCondition.Custom custom -> test(false, blockers, BlockerKind.OTHER, "", custom.explain());
            case UnlockCondition.Check check -> {
                boolean met;
                try {
                    met = check.test().test(ctx);
                } catch (RuntimeException e) {
                    met = false;
                }
                yield test(met, blockers, BlockerKind.OTHER, "", check.explain());
            }
            case UnlockCondition.AllOf all -> {
                boolean met = true;
                for (UnlockCondition part : all.conditions()) {
                    met &= check(part, ctx, blockers);
                }
                yield met;
            }
        };
    }

    private static boolean test(boolean met, List<Blocker> blockers, BlockerKind kind, String subject, String message) {
        if (!met) blockers.add(new Blocker(kind, subject, 0, message));
        return met;
    }
}
