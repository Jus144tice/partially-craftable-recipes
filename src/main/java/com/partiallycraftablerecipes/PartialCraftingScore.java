package com.partiallycraftablerecipes;

import java.util.Comparator;
import java.util.List;

/**
 * The result of scoring a single recipe against the player's available items: how many of its
 * ingredient slots can be satisfied, how many there are in total, and which items are missing.
 *
 * <p>Pure data + arithmetic, with no Minecraft dependencies, so it can be unit-tested directly. It is
 * produced by {@link RecipePartialMatcher} and consumed by the UI layer to decide whether a recipe is
 * "partially craftable", to order the partial list, and to build the missing-ingredients tooltip.
 *
 * <p><strong>"Slot" semantics.</strong> A crafting recipe's ingredient list has one entry per grid
 * cell that needs an item — a recipe that needs four planks has four (non-empty) ingredient slots.
 * Counting satisfied <em>slots</em> therefore naturally accounts for ingredient counts without any
 * special casing.
 */
public final class PartialCraftingScore {

    /**
     * Order used when {@code sortPartialRecipesByClosest} is on: closest-to-craftable first.
     *
     * <ol>
     *   <li>more satisfied slots first ({@link #satisfiedSlots()} descending), then
     *   <li>fewer missing slots first ({@link #missingSlots()} ascending) to break ties.
     * </ol>
     */
    public static final Comparator<PartialCraftingScore> CLOSEST_FIRST = Comparator.comparingInt(
                    PartialCraftingScore::satisfiedSlots)
            .reversed()
            .thenComparingInt(PartialCraftingScore::missingSlots);

    private final int totalSlots;
    private final int satisfiedSlots;
    private final List<MissingIngredient> missing;

    public PartialCraftingScore(int totalSlots, int satisfiedSlots, List<MissingIngredient> missing) {
        if (totalSlots < 0 || satisfiedSlots < 0 || satisfiedSlots > totalSlots) {
            throw new IllegalArgumentException("invalid score: satisfied=" + satisfiedSlots + " total=" + totalSlots);
        }
        this.totalSlots = totalSlots;
        this.satisfiedSlots = satisfiedSlots;
        this.missing = List.copyOf(missing);
    }

    public int totalSlots() {
        return this.totalSlots;
    }

    public int satisfiedSlots() {
        return this.satisfiedSlots;
    }

    /** Required ingredient slots that the inventory could not cover. */
    public int missingSlots() {
        return this.totalSlots - this.satisfiedSlots;
    }

    /** The missing items, grouped by item with their required counts. Never null; may be empty. */
    public List<MissingIngredient> missing() {
        return this.missing;
    }

    /** Fraction of slots covered, in {@code [0,1]} — drives the "2/4 ingredients available" text. */
    public double fraction() {
        return this.totalSlots == 0 ? 0.0 : (double) this.satisfiedSlots / this.totalSlots;
    }

    /**
     * Whether this score qualifies as "partially craftable" on its own terms: at least
     * {@code minMatched} slots satisfied, but not every slot (so it is genuinely partial and not a
     * full match). Callers additionally gate on the game's authoritative "fully craftable" check, so a
     * recipe the game can actually craft is never shown as partial even if this heuristic disagrees.
     *
     * @param minMatched the minimum satisfied-slot count to qualify ({@code PartialConfig.minMatchedIngredients})
     */
    public boolean isPartial(int minMatched) {
        return this.satisfiedSlots >= Math.max(1, minMatched) && this.satisfiedSlots < this.totalSlots;
    }

    @Override
    public String toString() {
        return "PartialCraftingScore[" + this.satisfiedSlots + "/" + this.totalSlots + ", missing=" + this.missing
                + "]";
    }
}
