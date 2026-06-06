package com.partiallycraftablerecipes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure ordering logic for the recipe book, free of Minecraft types so it can be unit-tested directly.
 *
 * <p>It works on lightweight {@link Entry} records — one per displayed collection — carrying just the
 * three things ordering depends on: the collection's original position, its sort name (the output
 * item's display name, lower-cased comparison), and a craftability rank
 * (0 = craftable, 1 = partial, 2 = uncraftable). The Mixin builds these from the live collections,
 * calls {@link #sort}, and reorders the real list to match.
 *
 * <p>Two independent controls combine here:
 *
 * <ul>
 *   <li><b>{@link SortMode}</b> — {@code DEFAULT} keeps the game's own collection order;
 *       {@code ALPHABETICAL} orders by sort name. Alphabetical is intentionally suppressed while a
 *       search is active so the search results keep their vanilla ordering.
 *   <li><b>group</b> — when on, collections are partitioned by craftability rank (craftable first,
 *       then partial, then the rest), and the chosen {@link SortMode} applies <em>within</em> each
 *       group.
 * </ul>
 *
 * Ties always fall back to the original index, so ordering is stable and {@code DEFAULT} with grouping
 * off is an exact identity (the game's order, untouched).
 */
public final class RecipeBookSorter {

    private RecipeBookSorter() {}

    public enum SortMode {
        DEFAULT,
        ALPHABETICAL
    }

    /**
     * One displayed collection's ordering inputs.
     *
     * @param index original position in the incoming list (stable-sort tiebreaker)
     * @param sortName output item display name, used by {@link SortMode#ALPHABETICAL}
     * @param craftRank 0 = craftable, 1 = partial, 2 = uncraftable (the grouping order)
     */
    public record Entry(int index, String sortName, int craftRank) {}

    /** The next sort mode in the cycle (there are only two, so this toggles). */
    public static SortMode next(SortMode mode) {
        return mode == SortMode.DEFAULT ? SortMode.ALPHABETICAL : SortMode.DEFAULT;
    }

    /**
     * Return {@code entries} reordered for display. Does not mutate the input list.
     *
     * @param mode the active sort mode
     * @param group whether to partition by craftability rank first
     * @param searchActive whether the search box is non-empty (suppresses alphabetical)
     */
    public static List<Entry> sort(List<Entry> entries, SortMode mode, boolean group, boolean searchActive) {
        boolean alphabetical = mode == SortMode.ALPHABETICAL && !searchActive;

        Comparator<Entry> comparator = null;
        if (group) {
            comparator = Comparator.comparingInt(Entry::craftRank);
        }
        if (alphabetical) {
            Comparator<Entry> byName = Comparator.comparing(Entry::sortName, String.CASE_INSENSITIVE_ORDER);
            comparator = comparator == null ? byName : comparator.thenComparing(byName);
        }
        // Stable fallback: keep the game's original order for anything still tied (and identity when
        // neither grouping nor alphabetical is active).
        Comparator<Entry> byIndex = Comparator.comparingInt(Entry::index);
        comparator = comparator == null ? byIndex : comparator.thenComparing(byIndex);

        List<Entry> sorted = new ArrayList<>(entries);
        sorted.sort(comparator);
        return sorted;
    }
}
