package com.partiallycraftablerecipes;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * The bridge between Minecraft recipe types and the pure {@link RecipePartialMatcher}.
 *
 * <p>It turns a {@link RecipeHolder} into the matcher's primitive form — a list of ingredient slots
 * (each the stacking ids it accepts) — using the exact same stacking-id space the game uses for
 * {@link StackedContents}, and snapshots the player's available items from a {@code StackedContents}
 * instance (the one the recipe book already maintains, which counts the inventory plus the loaded
 * crafting grid).
 *
 * <p><strong>Conservative by design.</strong> Anything we cannot reason about safely is skipped
 * ({@link #score} returns {@code null}) rather than guessed at, so the recipe simply won't appear in
 * the partial filter and the book never crashes on an exotic recipe:
 *
 * <ul>
 *   <li>special recipes ({@code Recipe#isSpecial()}, e.g. firework/shield-decoration) — no fixed
 *       ingredient list;
 *   <li>custom (non-vanilla) ingredients and empty-tag ingredients — their stacking ids aren't a
 *       reliable "do you have one" signal;
 *   <li>anything that throws — caught and treated as "skip".
 * </ul>
 *
 * Container items (e.g. a milk bucket) need no special handling here: matching only ever reads
 * availability, it never simulates consuming or returning items.
 */
public final class PartialRecipeAnalyzer {

    private PartialRecipeAnalyzer() {}

    /**
     * Snapshot a {@code stackingId -> count} availability map from a {@link StackedContents}, keeping
     * only positive counts. Build this once per filtering pass and reuse it across recipes.
     */
    public static Map<Integer, Integer> availabilityOf(StackedContents contents) {
        Map<Integer, Integer> map = new HashMap<>();
        for (Int2IntMap.Entry e : contents.contents.int2IntEntrySet()) {
            int count = e.getIntValue();
            if (count > 0) {
                map.put(e.getIntKey(), count);
            }
        }
        return map;
    }

    /**
     * Score {@code recipe} against {@code availability}, or return {@code null} if the recipe should be
     * skipped (special / custom / unsupported / errored — see the class doc). A non-null result is only
     * a closeness estimate; callers must still gate "is this partial?" on the game's own
     * fully-craftable check so a craftable recipe is never shown as partial.
     */
    public static PartialCraftingScore score(RecipeHolder<?> recipe, Map<Integer, Integer> availability) {
        try {
            Recipe<?> value = recipe.value();
            if (value.isSpecial()) {
                return null;
            }
            List<Ingredient> ingredients = value.getIngredients();
            if (ingredients.isEmpty()) {
                return null;
            }
            List<int[]> slots = new ArrayList<>(ingredients.size());
            for (Ingredient ingredient : ingredients) {
                if (ingredient.isEmpty()) {
                    continue; // a blank cell in a shaped recipe is not a required slot
                }
                if (ingredient.isCustom() || ingredient.hasNoItems()) {
                    return null; // can't reason about it safely -> skip the whole recipe
                }
                IntList ids = ingredient.getStackingIds();
                if (ids.isEmpty()) {
                    return null;
                }
                slots.add(ids.toIntArray());
            }
            if (slots.isEmpty()) {
                return null;
            }
            return RecipePartialMatcher.match(slots, availability);
        } catch (Throwable t) {
            // A broken/custom recipe must never take down the recipe book — just skip it.
            return null;
        }
    }
}
