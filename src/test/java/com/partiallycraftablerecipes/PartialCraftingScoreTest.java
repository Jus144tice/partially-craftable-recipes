package com.partiallycraftablerecipes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PartialCraftingScoreTest {

    private static PartialCraftingScore score(int satisfied, int total) {
        return new PartialCraftingScore(total, satisfied, List.of());
    }

    @Test
    void isPartial_respectsMinMatchedAndNotFull() {
        assertTrue(score(2, 4).isPartial(1));
        assertTrue(score(2, 4).isPartial(2));
        assertFalse(score(2, 4).isPartial(3), "below the minimum matched count");
        assertFalse(score(4, 4).isPartial(1), "fully matched is not partial");
        assertFalse(score(0, 4).isPartial(1), "nothing matched is not partial");
    }

    @Test
    void fraction() {
        assertEquals(0.5, score(2, 4).fraction(), 1e-9);
        assertEquals(0.0, score(0, 0).fraction(), 1e-9);
    }

    @Test
    void closestFirst_ordersBySatisfiedThenMissing() {
        List<PartialCraftingScore> list = new ArrayList<>(List.of(
                score(1, 4), // 1 satisfied, 3 missing
                score(3, 4), // 3 satisfied, 1 missing  -> should be first
                score(2, 3), // 2 satisfied, 1 missing
                score(2, 5) // 2 satisfied, 3 missing
                ));
        list.sort(PartialCraftingScore.CLOSEST_FIRST);

        // 3 satisfied wins outright.
        assertEquals(3, list.get(0).satisfiedSlots());
        // Then the two 2-satisfied entries, fewer-missing first: (2,3) before (2,5).
        assertEquals(2, list.get(1).satisfiedSlots());
        assertEquals(1, list.get(1).missingSlots());
        assertEquals(2, list.get(2).satisfiedSlots());
        assertEquals(3, list.get(2).missingSlots());
        // 1 satisfied last.
        assertEquals(1, list.get(3).satisfiedSlots());
    }

    @Test
    void rejectsInvalidCounts() {
        assertThrows(IllegalArgumentException.class, () -> new PartialCraftingScore(2, 3, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new PartialCraftingScore(-1, 0, List.of()));
    }
}
