package com.partiallycraftablerecipes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the pure matching algorithm. Item "ids" here are just arbitrary ints standing in for the
 * game's stacking ids; the algorithm never interprets them beyond equality and counts.
 */
class RecipePartialMatcherTest {

    // Readable stand-in ids for the cake example: wheat + egg + milk + sugar.
    private static final int WHEAT = 1;
    private static final int EGG = 2;
    private static final int MILK = 3;
    private static final int SUGAR = 4;

    private static int[] slot(int... ids) {
        return ids;
    }

    @Nested
    class CakeExample {
        // Cake: 3 wheat + 1 egg + 3 milk + 1 sugar = 8 slots (vanilla cake), but the prompt's smaller
        // example uses one of each; model that directly as four single-item slots.
        private final List<int[]> recipe = List.of(slot(WHEAT), slot(EGG), slot(MILK), slot(SUGAR));

        @Test
        void hasWheatAndSugarButNotEggOrMilk_isPartialTwoOfFour() {
            PartialCraftingScore score = RecipePartialMatcher.match(recipe, Map.of(WHEAT, 5, SUGAR, 5));
            assertEquals(4, score.totalSlots());
            assertEquals(2, score.satisfiedSlots());
            assertEquals(2, score.missingSlots());
            assertTrue(score.isPartial(1));
            // missing should be egg and milk, one each
            assertEquals(2, score.missing().size());
            assertTrue(score.missing().stream().anyMatch(m -> m.itemId() == EGG && m.count() == 1));
            assertTrue(score.missing().stream().anyMatch(m -> m.itemId() == MILK && m.count() == 1));
        }

        @Test
        void hasEverything_isFullyMatchedNotPartial() {
            PartialCraftingScore score =
                    RecipePartialMatcher.match(recipe, Map.of(WHEAT, 1, EGG, 1, MILK, 1, SUGAR, 1));
            assertEquals(4, score.satisfiedSlots());
            assertEquals(0, score.missingSlots());
            assertFalse(score.isPartial(1)); // all slots satisfied -> not "partial" by the heuristic
            assertTrue(score.missing().isEmpty());
        }

        @Test
        void hasNothing_zeroSatisfiedNotPartial() {
            PartialCraftingScore score = RecipePartialMatcher.match(recipe, Map.of());
            assertEquals(0, score.satisfiedSlots());
            assertFalse(score.isPartial(1)); // need at least one matched ingredient
            assertEquals(4, score.missing().size());
        }
    }

    @Nested
    class CountsAndDuplicates {
        @Test
        void twoSameSlots_needTwoItems() {
            // Two planks slots, but only one plank in inventory -> only one satisfied.
            List<int[]> recipe = List.of(slot(WHEAT), slot(WHEAT));
            PartialCraftingScore score = RecipePartialMatcher.match(recipe, Map.of(WHEAT, 1));
            assertEquals(2, score.totalSlots());
            assertEquals(1, score.satisfiedSlots());
            // the single missing entry is one more wheat
            assertEquals(1, score.missing().size());
            assertEquals(WHEAT, score.missing().get(0).itemId());
            assertEquals(1, score.missing().get(0).count());
        }

        @Test
        void twoSameSlots_withTwoItems_bothSatisfied() {
            List<int[]> recipe = List.of(slot(WHEAT), slot(WHEAT));
            PartialCraftingScore score = RecipePartialMatcher.match(recipe, Map.of(WHEAT, 2));
            assertEquals(2, score.satisfiedSlots());
            assertTrue(score.missing().isEmpty());
        }
    }

    @Nested
    class TagsAndAlternatives {
        // A "plank" slot that accepts oak(10) or birch(11).
        @Test
        void alternativeIngredient_satisfiedByEitherOption() {
            List<int[]> recipe = List.of(slot(10, 11), slot(10, 11), slot(10, 11));
            // three plank slots, two birch available -> two satisfied, one missing
            PartialCraftingScore score = RecipePartialMatcher.match(recipe, Map.of(11, 2));
            assertEquals(2, score.satisfiedSlots());
            assertEquals(1, score.missingSlots());
        }

        @Test
        void mostConstrainedSlotFirst_spendsScarceItemWell() {
            // One slot needs the rare item (99) only; three slots accept rare(99) or common(98).
            // We have one rare and plenty of common. A naive left-to-right greedy could waste the rare
            // on a flexible slot; most-constrained-first should keep all four slots satisfiable.
            List<int[]> recipe = List.of(slot(98, 99), slot(98, 99), slot(98, 99), slot(99));
            PartialCraftingScore score = RecipePartialMatcher.match(recipe, Map.of(99, 1, 98, 10));
            assertEquals(4, score.satisfiedSlots(), "scarce-item slot should be satisfied, not starved");
        }
    }

    @Test
    void doesNotConsumeCallersMap() {
        Map<Integer, Integer> available = new java.util.HashMap<>(Map.of(WHEAT, 1));
        RecipePartialMatcher.match(List.of(slot(WHEAT)), available);
        assertEquals(1, available.get(WHEAT), "matching must not mutate the caller's availability map");
    }

    @Test
    void emptyRecipe_isEmptyScore() {
        PartialCraftingScore score = RecipePartialMatcher.match(List.of(), Map.of(WHEAT, 1));
        assertEquals(0, score.totalSlots());
        assertEquals(0, score.satisfiedSlots());
        assertTrue(score.missing().isEmpty());
    }
}
