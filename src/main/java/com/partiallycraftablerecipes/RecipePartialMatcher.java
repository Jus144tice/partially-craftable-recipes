package com.partiallycraftablerecipes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure ingredient-matching logic, free of any Minecraft types so it can be unit-tested directly (the
 * same split the project already uses for click logic).
 *
 * <p>Inputs are deliberately primitive:
 *
 * <ul>
 *   <li>a recipe is a list of <em>slots</em>, one per non-empty ingredient cell; each slot is an
 *       {@code int[]} of the stacking ids it will accept (one id for a plain item, many for a
 *       tag/alternatives ingredient such as "any plank"), and
 *   <li>the player's items are a map of {@code stackingId -> count}.
 * </ul>
 *
 * <p>The matcher greedily assigns one available item to each slot to estimate how close the recipe is
 * to craftable. It processes the <em>most constrained</em> slots first (fewest distinct available
 * options) so that, for example, a recipe needing one specific item and three "any plank" slots
 * spends the scarce item on the slot that truly needs it. This greedy pass is a heuristic — it is
 * used only for the closeness score and the missing-ingredient list. Whether a recipe is actually
 * <em>fully</em> craftable is decided by the game's own exact check at the call site, so a greedy
 * miscount can never make a craftable recipe look partial or vice-versa.
 *
 * <p>Counts are respected because each slot consumes one unit from the shared pool: two "plank" slots
 * will only both be satisfied if the inventory holds at least two matching planks. Nothing is ever
 * mutated on the caller's side — the available map is copied first, so this never "consumes" items.
 */
public final class RecipePartialMatcher {

    private RecipePartialMatcher() {}

    /**
     * Score {@code slots} against {@code available}.
     *
     * @param slots one entry per required (non-empty) ingredient cell; each is the stacking ids that
     *     cell accepts. Must not contain empty arrays (filter empty ingredients out before calling).
     * @param available {@code stackingId -> count} of what the player has. Not modified.
     * @return the satisfied/total counts and the grouped missing items
     */
    public static PartialCraftingScore match(List<int[]> slots, Map<Integer, Integer> available) {
        int total = slots.size();
        if (total == 0) {
            return new PartialCraftingScore(0, 0, List.of());
        }

        // Work on a private copy so we never consume the caller's items.
        Map<Integer, Integer> pool = new HashMap<>(available);

        // Most-constrained-first: fewest currently-available options gets first pick of the pool.
        Integer[] order = new Integer[total];
        for (int i = 0; i < total; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(
                order,
                (a, b) -> Integer.compare(availableOptions(slots.get(a), pool), availableOptions(slots.get(b), pool)));

        boolean[] satisfied = new boolean[total];
        int[] usedId = new int[total]; // which item id actually covered each satisfied slot
        int satisfiedCount = 0;
        for (int idx : order) {
            for (int id : slots.get(idx)) {
                Integer have = pool.get(id);
                if (have != null && have > 0) {
                    pool.put(id, have - 1);
                    satisfied[idx] = true;
                    usedId[idx] = id;
                    satisfiedCount++;
                    break;
                }
            }
        }

        // Walk slots in original order, grouping satisfied slots into the "present" list (by the item
        // that covered them) and unsatisfied slots into the "missing" list (by the slot's first
        // accepted item, used as a representative).
        Map<Integer, MissingIngredient> present = new LinkedHashMap<>();
        Map<Integer, MissingIngredient> missing = new LinkedHashMap<>();
        for (int i = 0; i < total; i++) {
            if (satisfied[i]) {
                tally(present, usedId[i]);
            } else {
                int[] slot = slots.get(i);
                tally(missing, slot.length > 0 ? slot[0] : 0);
            }
        }

        return new PartialCraftingScore(
                total, satisfiedCount, new ArrayList<>(missing.values()), new ArrayList<>(present.values()));
    }

    /** Add one of {@code itemId} to a (itemId &rarr; tally) map, accumulating counts. */
    private static void tally(Map<Integer, MissingIngredient> into, int itemId) {
        into.merge(itemId, new MissingIngredient(itemId, 1), (existing, add) -> existing.plus(add.count()));
    }

    /** How many of {@code slot}'s accepted ids are currently present (count &gt; 0) in {@code pool}. */
    private static int availableOptions(int[] slot, Map<Integer, Integer> pool) {
        int n = 0;
        for (int id : slot) {
            Integer have = pool.get(id);
            if (have != null && have > 0) {
                n++;
            }
        }
        return n;
    }
}
