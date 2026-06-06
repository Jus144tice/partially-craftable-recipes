package com.partiallycraftablerecipes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.partiallycraftablerecipes.RecipeBookSorter.Entry;
import com.partiallycraftablerecipes.RecipeBookSorter.SortMode;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RecipeBookSorterTest {

    // Helper: build entries (index assigned by position) and return the resulting index order.
    private static List<Integer> order(List<Entry> in, SortMode mode, boolean group, boolean search) {
        return RecipeBookSorter.sort(in, mode, group, search).stream()
                .map(Entry::index)
                .collect(Collectors.toList());
    }

    private static Entry e(int index, String name, int rank) {
        return new Entry(index, name, rank);
    }

    @Test
    void defaultNoGroup_isIdentity() {
        List<Entry> in = List.of(e(0, "Zebra", 2), e(1, "Apple", 0), e(2, "Mango", 1));
        assertEquals(List.of(0, 1, 2), order(in, SortMode.DEFAULT, false, false));
    }

    @Test
    void alphabetical_ordersByName() {
        List<Entry> in = List.of(e(0, "Zebra", 2), e(1, "Apple", 0), e(2, "Mango", 1));
        assertEquals(List.of(1, 2, 0), order(in, SortMode.ALPHABETICAL, false, false));
    }

    @Test
    void alphabetical_isCaseInsensitive() {
        List<Entry> in = List.of(e(0, "banana", 0), e(1, "Apple", 0), e(2, "cherry", 0));
        assertEquals(List.of(1, 0, 2), order(in, SortMode.ALPHABETICAL, false, false));
    }

    @Test
    void alphabetical_suppressedWhileSearching() {
        List<Entry> in = List.of(e(0, "Zebra", 2), e(1, "Apple", 0));
        // search active -> keep original (vanilla relevance) order
        assertEquals(List.of(0, 1), order(in, SortMode.ALPHABETICAL, false, true));
    }

    @Test
    void group_ordersCraftableThenPartialThenUncraftable_preservingOriginalWithin() {
        // ranks: 0 craftable, 1 partial, 2 uncraftable
        List<Entry> in =
                List.of(e(0, "u1", 2), e(1, "c1", 0), e(2, "p1", 1), e(3, "c2", 0), e(4, "u2", 2), e(5, "p2", 1));
        // grouped, default sort within group -> original order within each rank
        assertEquals(List.of(1, 3, 2, 5, 0, 4), order(in, SortMode.DEFAULT, true, false));
    }

    @Test
    void groupAndAlphabetical_sortsByNameWithinEachGroup() {
        List<Entry> in = List.of(e(0, "Banana", 0), e(1, "Apple", 0), e(2, "Durian", 2), e(3, "Cherry", 1));
        // groups: craftable {Banana,Apple}->{Apple,Banana}, partial {Cherry}, uncraftable {Durian}
        assertEquals(List.of(1, 0, 3, 2), order(in, SortMode.ALPHABETICAL, true, false));
    }

    @Test
    void groupStillAppliesWhileSearching_onlyAlphabeticalSuppressed() {
        List<Entry> in = List.of(e(0, "Zebra", 1), e(1, "Apple", 0));
        // search active: no alpha, but grouping still puts craftable (Apple) before partial (Zebra)
        assertEquals(List.of(1, 0), order(in, SortMode.ALPHABETICAL, true, true));
    }

    @Test
    void next_togglesBetweenTwoModes() {
        assertEquals(SortMode.ALPHABETICAL, RecipeBookSorter.next(SortMode.DEFAULT));
        assertEquals(SortMode.DEFAULT, RecipeBookSorter.next(SortMode.ALPHABETICAL));
    }
}
